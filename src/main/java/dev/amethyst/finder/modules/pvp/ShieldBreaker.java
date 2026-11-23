package dev.amethyst.finder.modules.pvp;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.Friends;
import dev.amethyst.finder.core.ClickActionGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/** One physical use-click, one ordinary axe attack, immediate restoration. No delayed target chase. */
public final class ShieldBreaker {
    public static final String ID="auto_shield_breaker";
    private final FinderConfig config;
    private final ClickActionGate clicks=new ClickActionGate();
    private boolean gestureOwned,acting,useDown;
    private final java.util.ArrayDeque<ClickActionGate.Ticket> pendingUses=new java.util.ArrayDeque<>();
    private int ownedMace=-1,ownedAxe=-1,requests;
    private String status="Hold a mace and right-click a raised enemy shield";
    public ShieldBreaker(FinderConfig config){this.config=config;}
    public boolean busy(){return acting;}
    public boolean reserves(int slot){return acting&&(slot==ownedMace||slot==ownedAxe);}
    public boolean interceptUse(){return gestureOwned||acting;}
    private boolean enabled(){return dev.amethyst.finder.integration.BetterMaceModules.replaces(ID)?dev.amethyst.finder.integration.BetterMaceModules.enabled(ID):config.isModuleEnabled(ID);}
    public String status(){return enabled()?status:"Disabled · turn on Auto Shield Breaker in Arsenal";}
    private String unavailable(Minecraft c){
        if(!enabled())return "Disabled · turn on Auto Shield Breaker in Arsenal";
        if(config.streamerMode)return "Paused: Streamer Mode";
        if(c.player==null||c.level==null||c.getConnection()==null||c.gameMode==null)return "Join a world first";
        if(!c.player.isAlive()||c.player.isSpectator())return "Paused: player unavailable";
        if(c.gui.screen()!=null||c.gui.overlay()!=null||c.isPaused())return "Paused: close the game menu";
        if(!c.isWindowActive()||!c.mouseHandler.isMouseGrabbed())return "Paused: return focus to Minecraft";
        if(c.player.isUsingItem()||c.player.isHandsBusy())return "Paused: finish your other item use";
        if(ChunkFinderClient.MODULES!=null&&(ChunkFinderClient.MODULES.cameraActive()||ChunkFinderClient.MODULES.totemSwapPending()))return "Paused: camera or inventory transaction owns the player";
        if(ChunkFinderClient.BOT!=null&&ChunkFinderClient.BOT.enabled())return "Paused: Combat Bot owns combat";
        if(ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.ownsInventory())return "Paused: Mace PvP is moving inventory";
        if(ChunkFinderClient.PVP!=null&&ChunkFinderClient.PVP.hasPendingExplosiveAssist())return "Paused: explosive placement is still pending";
        if(ChunkFinderClient.HARD_CRYSTAL!=null&&ChunkFinderClient.HARD_CRYSTAL.busy())return "Paused: Hard Crystal is pending";
        return null;
    }
    /** Capture the physical edge, but let vanilla apply hotbar keys before evaluating the held item. */
    public void mouse(Minecraft c,int button,int action){
        if(!c.options.keyUse.matches(InputConstants.Type.MOUSE.getOrCreate(button)))return;
        if(action==0){useDown=false;clicks.mouse(0,false);gestureOwned=false;return;}
        if(action!=1)return;
        useDown=true;
        String reason=unavailable(c);
        clicks.mouse(1,reason==null&&!acting);
        var ticket=clicks.consume();
        if(ticket!=null&&pendingUses.size()<8)pendingUses.addLast(ticket);
        else if(reason!=null)status=reason;
    }
    /** Called at Minecraft.startUseItem, after vanilla has consumed the selected-hotbar key. */
    public boolean use(Minecraft c){
        if(acting)return true;
        var ticket=pendingUses.pollFirst();
        if(ticket==null)return gestureOwned;
        String reason=unavailable(c);
        if(reason!=null){status=reason;return false;}
        if(!c.player.getMainHandItem().is(Items.MACE)){
            status="Use held "+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.player.getMainHandItem().getItem())+" · hotbar "+(c.player.getInventory().getSelectedSlot()+1)+" · select a mace";
            return false;
        }
        if(!(c.hitResult instanceof EntityHitResult hit)||!(hit.getEntity() instanceof Player enemy)){status="No crosshair player · no weapon switch";return false;}
        LocalPlayer self=c.player;
        if(enemy==self||!enemy.isAlive()||enemy.isSpectator()||Friends.listed(config,enemy)){status="Target excluded · no weapon switch";return false;}
        if(!enemy.isBlocking()){status="Opponent is not blocking · mace stays selected";return false;}
        if(!self.hasLineOfSight(enemy)||!self.isWithinEntityInteractionRange(enemy,0)){status="Opponent outside normal visible reach";return false;}
        int axeSlot=-1;
        for(int i=0;i<9;i++){
            ItemStack item=self.getInventory().getItem(i);
            if(item.is(ItemTags.AXES)&&!item.isEmpty()&&(!item.isDamageableItem()||item.getDamageValue()<item.getMaxDamage()-1)){axeSlot=i;break;}
        }
        if(axeSlot<0){status="Put a usable axe in your hotbar";return false;}
        if(!self.isWithinAttackRange(self.getInventory().getItem(axeSlot),enemy.getBoundingBox(),0)){status="Axe cannot reach this player · no weapon switch";return false;}
        int original=self.getInventory().getSelectedSlot();ItemStack mace=self.getMainHandItem().copy();
        var world=c.level;var connection=c.getConnection();
        gestureOwned=useDown;acting=true;ownedMace=original;ownedAxe=axeSlot;
        try{
            self.getInventory().setSelectedSlot(axeSlot);
            if(ticket.claimAttack(true)){
                // Vanilla synchronizes the axe slot before the attack packet. Do not wait for a
                // post-swap cooldown here: during descent that would discard the click/target.
                Runnable attack=()->{c.gameMode.attack(self,enemy);self.swing(InteractionHand.MAIN_HAND,self.getMainHandItem().getAttackAnimation(),false);};
                if(dev.amethyst.finder.integration.BetterMaceModules.replaces(ID)){
                    dev.amethyst.finder.integration.BetterMaceModules.manualStun(c,enemy,original,attack);
                    requests++;status="Right-click #"+requests+" · axe sent · native mace follow-up queued";
                }else{attack.run();requests++;status="Axe attack #"+requests+" sent · mace restored · next hit is yours";}
            }
        }catch(RuntimeException|LinkageError error){status="Axe request failed · "+error.getClass().getSimpleName();}
        finally{
            try{
                if(c.player==self&&c.level==world&&c.getConnection()==connection&&self.getInventory().getSelectedSlot()==axeSlot
                    &&self.getInventory().getItem(original).is(Items.MACE)&&self.getInventory().getItem(original).getEnchantments().equals(mace.getEnchantments())){
                    self.getInventory().setSelectedSlot(original);
                    connection.send(new ServerboundSetCarriedItemPacket(original));
                }
            }catch(RuntimeException|LinkageError restoreError){status="Mace restoration interrupted · "+restoreError.getClass().getSimpleName();}
            finally{acting=false;ownedMace=ownedAxe=-1;}
        }
        return true;
    }
    public void tick(Minecraft c){
        if(c.player==null||c.level==null||c.gui.screen()!=null||!c.isWindowActive()||!enabled()){
            clicks.reset();pendingUses.clear();useDown=false;gestureOwned=false;
        }
    }
    public void reset(Minecraft c){clicks.reset();pendingUses.clear();useDown=false;gestureOwned=false;status="Hold a mace and right-click a raised enemy shield";}
}
