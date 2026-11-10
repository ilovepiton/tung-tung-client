package qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import dev.amethyst.finder.*;
import dev.amethyst.finder.modules.pvp.LootProtection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.player.*;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import java.nio.file.*;
import java.util.*;

/** Actual client/server entities and outgoing packets in an isolated offline world. */
public final class LootQa implements ClientModInitializer {
    private static final Path OUT=Path.of(System.getProperty("tung.qa.output"));
    public static int attacks,uses;
    private final List<Map<String,Object>> checks=new ArrayList<>();
    private final List<Integer> serverDrops=new ArrayList<>();
    private int stage,ticks,since;
    private volatile boolean fixtureReady,removed;
    private volatile boolean anchorsConfirmed;
    private volatile String error="";
    private boolean finished;
    private static final class Still extends ClientInput {@Override public void tick(){keyPresses=Input.EMPTY;}}
    private static final class Sneaking extends ClientInput {
        Sneaking(){keyPresses=new Input(false,false,false,false,false,true,false);}
        @Override public void tick(){}
    }
    private static final class Opponent extends RemotePlayer {
        boolean spectator;
        Opponent(Minecraft client,int id,String name,double x){super(client.level,new GameProfile(UUID.randomUUID(),name));setId(id);setPos(x,1,2);setHealth(20);client.level.addEntity(this);}
        @Override public boolean isSpectator(){return spectator;}
    }
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(Minecraft c){
        ticks++;
        if(finished)return;
        try {
            if(!error.isEmpty())throw new AssertionError(error);
            if(ticks-since>700)throw new AssertionError("Timeout stage="+stage);
            if(c.player!=null){c.player.input=new Still();c.player.setYRot(0);c.player.setXRot(10);c.gui.setScreen(null);for(var key:c.options.keyMappings){key.setDown(false);while(key.consumeClick()){}}}
            switch(stage){
                case 0->{
                    if(!c.isGameLoadFinished())return;
                    var config=ChunkFinderClient.CONFIG;config.modules.replaceAll((id,on)->false);config.enabled=false;config.notifications=false;
                    config.itemFinderTargets=ValuableItems.sanitize(null);config.itemFinderRange=128;config.itemFinderLimit=96;config.itemFinderChunks=true;
                    config.pvpLootProtection=true;config.pvpLootProtectionRange=32;config.pvpFriends="";config.pvpManualAssist=true;config.setModuleEnabled("night_vision",true);config.save();
                    check("Exactly 50 unique catalog types",ValuableItems.ENTRIES.size()==50&&ValuableItems.ENTRIES.stream().map(ValuableItems.Entry::id).distinct().count()==50);
                    for(var entry:ValuableItems.ENTRIES)check("Real item exists: "+entry.id(),BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:"+entry.id()))!=Items.AIR);
                    check("Unknown item keys are removed on sanitize",!ValuableItems.sanitize(Map.of("unknown",false)).containsKey("unknown"));
                    check("Loot protection is on by default",new FinderConfig().pvpLootProtection);
                    c.options.pauseOnLostFocus=false;c.options.renderDistance().set(3);c.getTutorial().setStep(TutorialSteps.NONE);
                    c.createWorldOpenFlows().createFreshLevel("LootQA",new LevelSettings("LootQA",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(93,false,false),lookup->lookup.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),c.gui.screen());next();
                }
                case 1->{
                    if(c.player==null||c.getSingleplayerServer()==null)return;
                    c.getSingleplayerServer().execute(()->{
                        try {
                            var server=c.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();
                            level.getGameRules().set(GameRules.SPAWN_MOBS,false,server);
                            for(int x=-8;x<=24;x++)for(int z=-8;z<=18;z++){level.setBlock(new BlockPos(x,0,z),Blocks.STONE.defaultBlockState(),3,512);for(int y=1;y<5;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3,512);}
                            player.teleportTo(level,.5,1,-3.5,Set.of(),0,10,true);
                            int i=0;
                            for(var entry:ValuableItems.ENTRIES){
                                var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:"+entry.id())));
                                if(entry.id().equals("end_crystal"))stack.setCount(12);
                                if(entry.id().equals("netherite_helmet"))stack.setDamageValue(stack.getMaxDamage()-1);
                                var drop=new ItemEntity(level,2+(i%10)*.7,1.5,3+(i/10)*.7,stack,0,0,0);drop.setNoGravity(true);drop.setPickUpDelay(32767);level.addFreshEntity(drop);serverDrops.add(drop.getId());i++;
                            }
                            for(String id:List.of("blue_shulker_box","dirt")){var drop=new ItemEntity(level,3,1.5,7,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:"+id))),0,0,0);drop.setNoGravity(true);drop.setPickUpDelay(32767);level.addFreshEntity(drop);serverDrops.add(drop.getId());}
                        }catch(Throwable failure){error=failure.toString();}
                        fixtureReady=true;
                    });next();
                }
                case 2->{
                    if(!fixtureReady||ticks-since<60)return;
                    var config=ChunkFinderClient.CONFIG;ModuleControls.setEnabled(c,"item_finder",true);ChunkFinderClient.ITEMS.tick(c);
                    var marks=ChunkFinderClient.ITEMS.marks(c);if(marks.size()<51)return;
                    check("All 50 types plus a colored shulker are found as received drops",marks.size()==51);
                    check("Unselected dirt is excluded",marks.stream().noneMatch(m->m.itemId().equals("dirt")));
                    check("Damaged netherite armor is included",marks.stream().anyMatch(m->m.itemId().equals("netherite_helmet")));
                    check("Actual stack counts are retained",marks.stream().anyMatch(m->m.itemId().equals("end_crystal")&&m.count()==12));
                    check("All colored shulkers share one filter",marks.stream().filter(m->m.itemId().equals("shulker_box")).count()==2);
                    check("Drops produce current chunk markers",!ChunkFinderClient.ITEMS.chunks(c).isEmpty());
                    config.itemFinderTargets.replaceAll((id,on)->false);
                    check("Disabling all filters clears marks immediately",ChunkFinderClient.ITEMS.marks(c).isEmpty()&&ChunkFinderClient.ITEMS.chunks(c).isEmpty());
                    config.itemFinderTargets=ValuableItems.sanitize(null);config.itemFinderTargets.put("shulker_box",false);
                    check("Disabling shulkers removes every color",ChunkFinderClient.ITEMS.marks(c).stream().noneMatch(m->m.itemId().equals("shulker_box")));
                    config.itemFinderTargets=ValuableItems.sanitize(null);
                    config.itemFinderRange=1;check("Current range filters cached entities",ChunkFinderClient.ITEMS.marks(c).isEmpty());config.itemFinderRange=128;
                    next();
                }
                case 3->{
                    if(ticks-since<20)return;
                    shot(c,"item-finder-drops");guardChecks(c);next();
                }
                case 4->{
                    fixtureReady=false;
                    c.getSingleplayerServer().execute(()->{
                        try {
                            var level=c.getSingleplayerServer().overworld();var player=c.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                            for(var pos:List.of(new BlockPos(0,1,-2),new BlockPos(-1,1,-2)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3,512);
                            player.getInventory().setItem(0,new ItemStack(Items.RESPAWN_ANCHOR,64));
                            player.getInventory().setItem(40,new ItemStack(Items.RESPAWN_ANCHOR,64));
                            player.getInventory().setSelectedSlot(0);player.inventoryMenu.sendAllDataToRemote();
                        }catch(Throwable failure){error=failure.toString();}fixtureReady=true;
                    });next();
                }
                case 5->{
                    if(!fixtureReady||ticks-since<30)return;
                    check("Server anchor inventory reached both client hands",c.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)&&c.player.getOffhandItem().is(Items.RESPAWN_ANCHOR));
                    var config=ChunkFinderClient.CONFIG;config.setModuleEnabled("auto_anchor",true);
                    check("Main-hand anchor can be placed with Auto Anchor and loot protection enabled",c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,groundHit(0,-2)).consumesAction());
                    check("Offhand anchor can be placed with Auto Anchor and loot protection enabled",c.gameMode.useItemOn(c.player,InteractionHand.OFF_HAND,groundHit(-1,-2)).consumesAction());
                    check("Safe building does not start an automated combo",!ChunkFinderClient.PVP.ownsInventory());
                    next();
                }
                case 6->{
                    if(ticks-since<30)return;
                    fixtureReady=false;
                    c.getSingleplayerServer().execute(()->{
                        try {
                            var level=c.getSingleplayerServer().overworld();
                            anchorsConfirmed=List.of(new BlockPos(0,1,-2),new BlockPos(-1,1,-2)).stream().allMatch(pos->{var state=level.getBlockState(pos);return state.is(Blocks.RESPAWN_ANCHOR)&&state.getValue(RespawnAnchorBlock.CHARGE)==0;});
                        }catch(Throwable failure){error=failure.toString();}fixtureReady=true;
                    });next();
                }
                case 7->{
                    if(!fixtureReady||ticks-since<15)return;
                    check("Integrated server confirms both anchors with no automatic charge or blast",anchorsConfirmed);
                    ChunkFinderClient.CONFIG.setModuleEnabled("auto_anchor",false);
                    check("Disabling module clears current item and chunk state",disableFinder(c));
                    ModuleControls.setEnabled(c,"item_finder",true);ChunkFinderClient.ITEMS.tick(c);
                    check("Re-enabling rescans the same received drops",ChunkFinderClient.ITEMS.marks(c).size()==51);
                    c.getSingleplayerServer().execute(()->{try{var level=c.getSingleplayerServer().overworld();for(int id:serverDrops){var entity=level.getEntity(id);if(entity!=null)entity.discard();}}catch(Throwable failure){error=failure.toString();}removed=true;});next();
                }
                case 8->{
                    if(!removed||ticks-since<15)return;
                    check("Server pickup/despawn removes all live markers",ChunkFinderClient.ITEMS.marks(c).isEmpty());
                    check("Empty item chunks disappear without history",ChunkFinderClient.ITEMS.chunks(c).isEmpty());
                    c.disconnectWithSavingScreen();next();
                }
                case 9->{check("World disconnect clears item state",ChunkFinderClient.ITEMS.marks(c).isEmpty()&&ChunkFinderClient.ITEMS.chunks(c).isEmpty());check("No stale-world loot guard after disconnect",!LootProtection.blocked(c));finished=true;stage=10;report();c.stop();}
            }
        }catch(Throwable failure){error=failure.toString();failure.printStackTrace();finished=true;report();c.stop();}
    }
    private boolean disableFinder(Minecraft c){ModuleControls.setEnabled(c,"item_finder",false);return ChunkFinderClient.ITEMS.marks(c).isEmpty()&&ChunkFinderClient.ITEMS.chunks(c).isEmpty();}
    private void guardChecks(Minecraft c)throws Exception{
        var config=ChunkFinderClient.CONFIG;
        check("Alone means loot protected",LootProtection.blocked(c));
        var hit=new BlockHitResult(new Vec3(.5,1,.5),Direction.UP,new BlockPos(0,0,0),false);
        var crystal=new EndCrystal(EntityTypes.END_CRYSTAL,c.level);crystal.setId(90001);crystal.setPos(.5,1,.5);c.level.addEntity(crystal);
        int beforeAttacks=attacks,beforeUses=uses;
        c.player.getInventory().setItem(0,new ItemStack(Items.END_CRYSTAL,64));c.player.getInventory().setSelectedSlot(0);
        var result=c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,hit);
        check("Manual crystal placement is rejected before prediction",result==InteractionResult.FAIL);
        c.player.getInventory().setItem(0,ItemStack.EMPTY);
        c.level.setBlock(new BlockPos(-2,1,1),Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE,4),3,512);
        var anchorHit=new BlockHitResult(new Vec3(-1.5,1.5,1),Direction.NORTH,new BlockPos(-2,1,1),false);
        check("Charged anchor detonation with an empty hand is blocked",c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,anchorHit)==InteractionResult.FAIL);
        c.gameMode.attack(c.player,crystal);
        check("Manual attacks send no crystal explosion packet",attacks==beforeAttacks);
        ChunkFinderClient.PVP.assistedAttack(c,crystal);
        ChunkFinderClient.PVP.assistedClick(c,anchorHit,InteractionHand.MAIN_HAND);
        check("Automated attacks and clicks share the same guard",attacks==beforeAttacks&&uses==beforeUses);
        var safeHit=groundHit(-2,-2);
        c.player.getInventory().setItem(40,new ItemStack(Items.RESPAWN_ANCHOR));
        check("Offhand anchor placement remains available while loot is protected",c.gameMode.useItemOn(c.player,InteractionHand.OFF_HAND,safeHit).consumesAction());
        check("Safe offhand anchor placement reaches vanilla prediction and sends a packet",uses==beforeUses+1&&c.level.getBlockState(safeHit.getBlockPos().above()).is(Blocks.RESPAWN_ANCHOR));
        for(boolean enabled:new boolean[]{false,true}){
            config.setModuleEnabled("auto_anchor",enabled);
            var mainHit=groundHit(enabled?0:-1,-2);int count=uses;
            c.player.getInventory().setItem(0,new ItemStack(Items.RESPAWN_ANCHOR));
            check("Main-hand anchor placement works with Auto Anchor "+enabled,c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,mainHit).consumesAction()&&uses==count+1);
            check("Loot-protected placement does not capture an explosive combo "+enabled,!ChunkFinderClient.PVP.ownsInventory());
        }
        config.setModuleEnabled("auto_anchor",false);
        var anchorPos=anchorHit.getBlockPos();
        c.level.setBlock(anchorPos,Blocks.RESPAWN_ANCHOR.defaultBlockState(),3,512);
        c.player.getInventory().setItem(0,new ItemStack(Items.GLOWSTONE));
        int beforeCharge=uses;
        check("Charging an empty anchor is safe and allowed",c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,anchorHit).consumesAction()&&uses==beforeCharge+1);
        check("Charging reaches vanilla prediction",c.level.getBlockState(anchorPos).getValue(RespawnAnchorBlock.CHARGE)==1);
        c.player.getInventory().setItem(0,ItemStack.EMPTY);
        int beforeBlast=uses;
        check("The next empty-hand click cannot detonate the charged anchor",c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,anchorHit)==InteractionResult.FAIL&&uses==beforeBlast);
        c.player.getInventory().setItem(0,new ItemStack(Items.GLOWSTONE));
        c.level.setBlock(anchorPos,Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE,4),3,512);
        check("Glowstone cannot detonate a fully charged anchor",c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,anchorHit)==InteractionResult.FAIL&&uses==beforeBlast);
        c.level.setBlock(anchorPos,Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE,1),3,512);
        c.player.getInventory().setItem(0,new ItemStack(Items.STONE));c.player.getInventory().setItem(40,new ItemStack(Items.GLOWSTONE));
        check("Main-hand click may pass safely to offhand charging",!LootProtection.blocksUse(c,InteractionHand.MAIN_HAND,anchorHit));
        check("Offhand glowstone may charge a non-full anchor",!LootProtection.blocksUse(c,InteractionHand.OFF_HAND,anchorHit));
        c.level.setBlock(anchorPos,Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE,4),3,512);
        check("Offhand glowstone cannot bypass the full-anchor blast guard",LootProtection.blocksUse(c,InteractionHand.MAIN_HAND,anchorHit)&&LootProtection.blocksUse(c,InteractionHand.OFF_HAND,anchorHit));
        c.player.getInventory().setItem(40,ItemStack.EMPTY);
        c.player.getInventory().setItem(0,new ItemStack(Items.RESPAWN_ANCHOR));
        check("Holding an anchor does not bypass charged-anchor blast protection",LootProtection.blocksUse(c,InteractionHand.MAIN_HAND,anchorHit));
        c.player.input=new Sneaking();
        check("Sneak placement against a charged anchor stays available",c.player.isSecondaryUseActive()&&!LootProtection.blocksUse(c,InteractionHand.MAIN_HAND,anchorHit));
        c.player.getInventory().setItem(0,ItemStack.EMPTY);
        check("Sneaking with empty hands still blocks detonation",LootProtection.blocksUse(c,InteractionHand.MAIN_HAND,anchorHit));
        c.player.input=new Still();
        c.player.getInventory().setItem(0,new ItemStack(Items.STONE));
        beforeUses=uses;
        c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,hit);
        check("Ordinary block interactions remain available",uses>beforeUses);
        var friend=new Opponent(c,90002,"LootFriend",3);config.pvpFriends="LootFriend";
        check("A friend alone does not unlock explosions",LootProtection.blocked(c));
        var enemy=new Opponent(c,90003,"LootOpponent",4);
        check("A living opponent unlocks explosions",!LootProtection.blocked(c));
        beforeAttacks=attacks;c.gameMode.attack(c.player,crystal);
        check("Crystal attack packets are allowed with an opponent",attacks==beforeAttacks+1);
        c.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(enemy,(byte)35));
        check("A totem pop is not treated as a death",!LootProtection.blocked(c));
        c.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(enemy,(byte)3));
        check("The last opponent death relocks explosions immediately",LootProtection.blocked(c));
        beforeAttacks=attacks;ChunkFinderClient.PVP.assistedAttack(c,crystal);
        check("A queued follow-up cannot send another attack after death",attacks==beforeAttacks);
        var next=new Opponent(c,90004,"NextOpponent",4);
        check("A new opponent can resume combat",!LootProtection.blocked(c));
        next.setPos(200,1,2);check("Distant players do not unlock local explosions",LootProtection.blocked(c));
        next.setPos(4,1,2);next.spectator=true;check("Spectators do not count as opponents",LootProtection.blocked(c));
        config.pvpLootProtection=false;check("Explicitly disabling loot protection permits practice",!LootProtection.blocked(c));config.pvpLootProtection=true;
        for(var entity:List.of(crystal,friend,enemy,next))c.level.removeEntity(entity.getId(),Entity.RemovalReason.DISCARDED);
        config.pvpFriends="";config.save();
    }
    private BlockHitResult groundHit(int x,int z){return new BlockHitResult(new Vec3(x+.5,1,z+.5),Direction.UP,new BlockPos(x,0,z),false);}
    private void shot(Minecraft c,String name){Screenshot.takeScreenshot(c.gameRenderer.mainRenderTarget(),image->{try{image.writeToFile(OUT.resolve(name+".png"));}catch(Exception failure){error=failure.toString();}finally{image.close();}});}
    private void next(){stage++;since=ticks;report();}
    private void check(String label,boolean passed){checks.add(Map.of("check",label,"passed",passed));report();if(!passed)throw new AssertionError(label);}
    private void report(){try{Files.writeString(OUT.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("stage",stage,"error",error,"checks",checks,"attackPackets",attacks,"usePackets",uses,"userProfileTouched",false)));}catch(Exception failure){throw new RuntimeException(failure);}}
}
