package dev.amethyst.finder;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.vault.*;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;

/** One normal, aimed vault interaction. The preview never determines the server reward. */
public final class VaultAssistant {
    public static final String MODULE="auto_vault";
    private final FinderConfig config;
    private ClientLevel level;private LocalPlayer player;
    private BlockPos locked;private Move pending;private int clock,ownedSlot=-1;
    private String status="Choose a vault type and preview, then arm once";
    private long interactions;
    private record Move(int source,int target,ItemStack sourceBefore,ItemStack targetBefore,int deadline){}
    public VaultAssistant(FinderConfig config){this.config=config;config.setModuleEnabled(MODULE,false);}
    public void setEnabled(Minecraft c,boolean enabled){
        if(enabled&&(c.level==null||c.player==null))throw new IllegalStateException("Join a world before arming the vault helper");
        if(enabled&&pending!=null)throw new IllegalStateException("Wait for the inventory move to finish");
        config.setModuleEnabled(MODULE,enabled);locked=null;ownedSlot=-1;
        if(enabled){level=c.level;player=c.player;status="Armed once · aim at an unopened "+config.vaultType+" vault";}
        else status="Vault helper off";
    }
    private void stop(String reason){config.setModuleEnabled(MODULE,false);status=reason;locked=null;ownedSlot=-1;config.save();}
    public String status(){return status;}
    public static Item key(String type){return "ominous".equals(type)?Items.OMINOUS_TRIAL_KEY:Items.TRIAL_KEY;}
    public static int count(LocalPlayer p,Item item){if(p==null)return 0;int total=p.getOffhandItem().is(item)?p.getOffhandItem().getCount():0;for(int i=0;i<36;i++){var s=p.getInventory().getItem(i);if(s.is(item))total+=s.getCount();}return total;}
    public static String type(VaultBlockEntity vault){return vault.getBlockState().getValue(VaultBlock.OMINOUS)?"ominous":"normal";}
    public static VaultBlockEntity aimed(Minecraft c){
        if(c.player==null||c.level==null||!(c.hitResult instanceof BlockHitResult hit)||!c.player.isWithinBlockInteractionRange(hit.getBlockPos(),0))return null;
        return c.level.getBlockEntity(hit.getBlockPos()) instanceof VaultBlockEntity v?v:null;
    }
    public void tick(Minecraft c){
        clock++;
        if(c.player!=player||c.level!=level){pending=null;level=c.level;player=c.player;if(config.isModuleEnabled(MODULE))stop("World changed · arm again");return;}
        if(pending!=null){
            var m=pending;var inv=player.getInventory();
            if(ItemStack.matches(inv.getItem(m.target),m.sourceBefore)&&ItemStack.matches(inv.getItem(m.source),m.targetBefore)){pending=null;ownedSlot=m.target;}
            else if(clock>=m.deadline){pending=null;stop("Key move was not confirmed by the server · arm again");return;}
            else {status="Waiting for the server to move the key";return;}
        }
        if(!config.isModuleEnabled(MODULE))return;
        if(c.player==null||c.level==null||c.gameMode==null||c.getConnection()==null){stop("Join a world and arm again");return;}
        if(c.gui.screen()!=null||c.gui.overlay()!=null||!c.getWindow().isFocused()||!c.mouseHandler.isMouseGrabbed()||c.isPaused()){status="Armed · return to Minecraft";return;}
        if(player.isDeadOrDying()){stop("Player died · arm again");return;}
        if(player.containerMenu!=player.inventoryMenu||!player.inventoryMenu.getCarried().isEmpty()||player.isUsingItem()||c.options.keyUse.isDown()
                ||ChunkFinderClient.PVP.ownsInventory()||ChunkFinderClient.PVP.ownsCombat()){status="Waiting for manual use or inventory/combat activity to finish";return;}
        var vault=aimed(c);
        if(vault==null){status="Aim directly at a vault within normal reach";return;}
        if(locked!=null&&!locked.equals(vault.getBlockPos())){status="Aim back at the armed vault, or switch off and arm again";return;}
        if(!type(vault).equals(config.vaultType)){status="Wrong vault type · selected "+config.vaultType;return;}
        var state=vault.getBlockState().getValue(VaultBlock.STATE);
        if(state!=VaultState.ACTIVE){status=state==VaultState.INACTIVE?"Vault inactive or already claimed · server decides eligibility":"Vault is dispensing rewards";return;}
        Item required=key(config.vaultType);
        if(count(player,required)==0){stop("No "+("ominous".equals(config.vaultType)?"Ominous Trial Key":"Trial Key")+" in your inventory · nothing opened");return;}
        locked=vault.getBlockPos().immutable();
        InteractionHand hand;
        if(player.getOffhandItem().is(required))hand=InteractionHand.OFF_HAND;
        else {
            var inv=player.getInventory();
            if(ownedSlot>=0&&inv.getSelectedSlot()!=ownedSlot){stop("Hotbar selection changed · arm again");return;}
            if(!player.getMainHandItem().is(required)){
                int slot=-1;for(int i=0;i<36;i++)if(inv.getItem(i).is(required)){slot=i;break;}
                if(slot<9){inv.setSelectedSlot(slot);ownedSlot=slot;}
                else {
                    int target=inv.getSelectedSlot();for(int i=0;i<9;i++)if(inv.getItem(i).isEmpty()){target=i;break;}
                    inv.setSelectedSlot(target);ownedSlot=target;
                    pending=new Move(slot,target,inv.getItem(slot).copy(),inv.getItem(target).copy(),clock+40);
                    player.connection.send(new ServerboundContainerClickPacket(player.inventoryMenu.containerId,player.inventoryMenu.getStateId(),(short)slot,(byte)target,ContainerInput.SWAP,new Int2ObjectOpenHashMap<>(),HashedStack.create(player.inventoryMenu.getCarried(),player.connection.decoratedHashOpsGenenerator())));
                    status="Moving the matching key into your hotbar";return;
                }
            }
            hand=InteractionHand.MAIN_HAND;
        }
        var target=VaultRewards.find(config.vaultType,config.vaultReward());
        if(target==null){stop("Choose a valid reward preview first");return;}
        if(!target.matches(vault.getSharedData().getDisplayItem())){status="Key ready · waiting for "+target.name()+" preview · loot is random";return;}
        // No hidden data, inventory prediction, repeated requests or fabricated block hit.
        var result=c.gameMode.useItemOn(player,hand,(BlockHitResult)c.hitResult);interactions++;
        stop(result.consumesAction()?"One opening request sent · reward is random · helper off":"Server did not accept the opening · helper off");
    }
    public Map<String,Object> state(Minecraft c){
        var out=new LinkedHashMap<String,Object>();out.put("armed",config.isModuleEnabled(MODULE));out.put("type",config.vaultType);out.put("reward",config.vaultReward());out.put("normalReward",config.vaultNormalReward);out.put("ominousReward",config.vaultOminousReward);out.put("status",status);out.put("trialKeys",count(c.player,Items.TRIAL_KEY));out.put("ominousKeys",count(c.player,Items.OMINOUS_TRIAL_KEY));out.put("interactions",interactions);out.put("movingKey",pending!=null);
        var v=aimed(c);if(v!=null){var p=v.getBlockPos();var preview=v.getSharedData().getDisplayItem();out.put("aimed",Map.of("x",p.getX(),"y",p.getY(),"z",p.getZ(),"type",type(v),"state",v.getBlockState().getValue(VaultBlock.STATE).getSerializedName(),"preview",preview.isEmpty()?"":BuiltInRegistries.ITEM.getKey(preview.getItem()).toString(),"previewName",preview.isEmpty()?"No preview":preview.getHoverName().getString()));}
        return out;
    }
}
