package dev.amethyst.finder.modules.pvp;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.core.ClickActionGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;

/** A distinct physical use click may place one crystal and request one attack on its received spawn. */
public final class HardCrystal {
    public record Placement(LocalPlayer player,ClientLevel world,ClientPacketListener connection,BlockPos position,
                            Set<Integer> oldIds,ClickActionGate.Ticket ticket) {}
    private final FinderConfig config;
    private final ClickActionGate clicks=new ClickActionGate();
    private ClickActionGate.Ticket currentClick;
    private Placement pending;
    private int entityId=-1;
    private boolean attacked,advancing;
    private long deadline,removalRevision;
    private String status="One click places one crystal; no held repeat";
    public HardCrystal(FinderConfig config){this.config=config;}
    public boolean enabled(){return config.isModuleEnabled("hard_crystal");}
    public boolean busy(){return pending!=null;}
    public String status(){return !enabled()?"Disabled":config.streamerMode?"Paused: Streamer Mode":status;}
    public void setEnabled(Minecraft client,boolean value) {
        reset();
        if(value)ChunkFinderClient.PVP.setEnabled("auto_crystal",false);
        config.setModuleEnabled("hard_crystal",value);
        status=value?"Ready · release, then click once with an end crystal":"Disabled";
    }
    public void reset(){clicks.reset();currentClick=null;pending=null;entityId=-1;attacked=false;}
    private boolean eligible(Minecraft c) {
        return enabled()&&!config.streamerMode&&c.player!=null&&c.level!=null&&c.getConnection()!=null&&c.gameMode!=null
                &&c.player.isAlive()&&!c.player.isSpectator()&&!c.player.isUsingItem()&&!c.player.isHandsBusy()
                &&c.gui.screen()==null&&c.gui.overlay()==null&&c.isWindowActive()&&c.mouseHandler.isMouseGrabbed()
                &&!LootProtection.blocked(c)
                &&(ChunkFinderClient.PVP==null||!ChunkFinderClient.PVP.hasPendingExplosiveAssist())
                &&(ChunkFinderClient.BOT==null||!ChunkFinderClient.BOT.enabled())
                &&(ChunkFinderClient.MACE==null||!ChunkFinderClient.MACE.enabled())
                &&(ChunkFinderClient.MODULES==null||!ChunkFinderClient.MODULES.cameraActive()&&!ChunkFinderClient.MODULES.totemSwapPending());
    }
    public void mouse(Minecraft client,int button,int action) {
        if(client.options.keyUse.matches(InputConstants.Type.MOUSE.getOrCreate(button)))clicks.mouse(action,eligible(client));
    }
    public boolean applies(Minecraft client,InteractionHand hand) {
        return enabled()&&client.player!=null&&client.options.keyUse.saveString().startsWith("key.mouse.")&&client.player.getItemInHand(hand).is(Items.END_CRYSTAL);
    }
    public boolean interceptsUse(Minecraft client) {
        return enabled()&&client.player!=null&&(applies(client,InteractionHand.MAIN_HAND)||applies(client,InteractionHand.OFF_HAND));
    }
    /** Consume before vanilla begins any block/entity/air-use attempt. */
    public boolean beginGesture(Minecraft client) {
        currentClick=clicks.consume();
        if(currentClick==null)return false;
        if(!eligible(client)||pending!=null) {currentClick=null;return false;}
        return true;
    }
    public void endGesture(){currentClick=null;}
    public boolean beginUse(Minecraft client){return currentClick!=null&&pending==null&&eligible(client);}
    public Placement capture(Minecraft client,BlockHitResult hit) {
        var ticket=currentClick;currentClick=null;
        if(ticket==null)return null;
        var base=client.level.getBlockState(hit.getBlockPos());
        if(!base.is(Blocks.OBSIDIAN)&&!base.is(Blocks.BEDROCK))return null;
        var pos=hit.getBlockPos().above().immutable();var old=new HashSet<Integer>();
        for(var e:client.level.entitiesForRendering())
            if(e.getType()==EntityTypes.END_CRYSTAL&&e.position().distanceToSqr(Vec3.atBottomCenterOf(pos))<.25)old.add(e.getId());
        return new Placement(client.player,client.level,client.getConnection(),pos,Set.copyOf(old),ticket);
    }
    public void observed(Minecraft client,Placement placed,boolean accepted) {
        if(!accepted||placed==null||pending!=null||!eligible(client)||placed.player()!=client.player||placed.world()!=client.level||placed.connection()!=client.getConnection())return;
        pending=placed;entityId=-1;attacked=false;deadline=System.nanoTime()+3_000_000_000L;
        status="Waiting for your placed crystal from the server";
    }
    public void tick(Minecraft client) {
        if(!eligible(client)) {if(busy())status="Cancelled: world, focus or combat state changed";reset();return;}
        received(client);
    }
    public void received(Minecraft client) {
        if(advancing||pending==null)return;
        if(!eligible(client)||pending.player()!=client.player||pending.world()!=client.level||pending.connection()!=client.getConnection()) {reset();status="Cancelled: session or focus changed";return;}
        if(System.nanoTime()>deadline) {reset();status="No server confirmation · click again; no automatic retry";return;}
        advancing=true;
        try {
            if(attacked) {
                if(PvpAcknowledgements.entityRemovalRevision(client,entityId)>removalRevision) {
                    pending=null;entityId=-1;attacked=false;status="Crystal removal confirmed · ready for a new click";
                }
                return;
            }
            for(var e:client.level.entitiesForRendering()) {
                if(e.getType()!=EntityTypes.END_CRYSTAL||e.isRemoved()||!e.isAlive()||pending.oldIds().contains(e.getId())
                        ||e.position().distanceToSqr(Vec3.atBottomCenterOf(pending.position()))>=.25)continue;
                if(!client.player.hasLineOfSight(e)||!client.player.isWithinEntityInteractionRange(e,0)
                        ||!client.player.isWithinAttackRange(client.player.getMainHandItem(),e.getBoundingBox(),0)) {
                    status="Waiting: placed crystal must be visible and within normal attack reach";return;
                }
                if(ChunkFinderClient.PVP.manualFriendAtRisk(client,e.position(),6,null)!=null) {status="Paused: protected friend in crystal blast";return;}
                if(config.pvpManualBlastChecks&&ChunkFinderClient.PVP.manualSelfDamage(client,e.position(),6,null)>config.pvpMaxSelfDamage) {status="Paused: configured self-damage limit";return;}
                if(!pending.ticket().claimAttack(true))return;
                attacked=true;entityId=e.getId();removalRevision=PvpAcknowledgements.entityRemovalRevision(client,entityId);deadline=System.nanoTime()+3_000_000_000L;
                client.gameMode.attack(client.player,e);
                client.player.swing(InteractionHand.MAIN_HAND,client.player.getMainHandItem().getAttackAnimation(),false);
                client.player.connection.send(ServerboundPunchPacket.INSTANCE);
                status="One attack requested · waiting for server removal";
                return;
            }
        } catch(ReflectiveOperationException|RuntimeException error) {reset();status="Stopped: crystal action could not finish";}
        finally {advancing=false;}
    }
}
