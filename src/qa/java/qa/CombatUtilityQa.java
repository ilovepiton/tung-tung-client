package qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import dev.amethyst.finder.*;
import dev.amethyst.finder.modules.InvisMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.player.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.util.*;

/** Isolated integrated-server checks for real slots, actual pops, attacks and rendering. */
public final class CombatUtilityQa implements ClientModInitializer {
    private static final Path OUT=Path.of(System.getProperty("tung.qa.output"));
    public static int swaps,attacks,handsSubmitted,invisibleLabels;
    public static volatile int serverSwaps,rejections;
    public static volatile boolean rejectNext;
    public static long emptyReceiptAt;
    public static long popReceiptAt;
    public static final List<Double> popRefillMs=new ArrayList<>();
    public static final List<Double> refillMs=new ArrayList<>();
    private static volatile ServerPlayer npc;
    private static volatile ServerPlayer chainedPopPlayer;
    private volatile boolean ready,serverReady;
    private volatile String error="";
    private final List<Map<String,Object>> checks=new ArrayList<>();
    private int ticks,since,stage,beforeSwaps,beforeAttacks,beforeHands,beforeLabels;
    private boolean finished,keepScreen,lookForward;
    private double initialAbsorption;
    private net.minecraft.world.phys.Vec3 initialPosition;
    private final SimpleContainer chest=new SimpleContainer(27);
    private static final class Still extends ClientInput {@Override public void tick(){keyPresses=Input.EMPTY;}}
    @Override public void onInitializeClient(){
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ServerTickEvents.END_SERVER_TICK.register(server->{
            TotemPacketDelay.flush();
            var p=chainedPopPlayer;
            if(p!=null&&p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                chainedPopPlayer=null;p.setInvulnerableTime(0);
                p.hurtServer(p.level(),p.damageSources().generic(),1000);p.inventoryMenu.broadcastChanges();
            }
            if(npc!=null&&!npc.isRemoved())npc.doTick();
        });
    }
    private void tick(Minecraft c){
        ticks++;
        if(finished)return;
        try {
            if(!error.isEmpty())throw new AssertionError(error);
            if(ticks-since>700)throw new AssertionError("Timeout stage="+stage);
            if(c.player!=null){
                c.player.input=new Still();c.player.setYRot(lookForward?0:180);c.player.setXRot(0);
                if(!keepScreen)c.gui.setScreen(null);
                for(var key:c.options.keyMappings){key.setDown(false);while(key.consumeClick()){}}
            }
            if(c.getSingleplayerServer()!=null&&ticks%5==0)c.getSingleplayerServer().execute(()->{var players=c.getSingleplayerServer().getPlayerList().getPlayers();serverReady=!players.isEmpty()&&players.getFirst().connection.hasClientLoaded();});
            var cfg=ChunkFinderClient.CONFIG;
            switch(stage){
                case 0->{
                    if(!c.isGameLoadFinished())return;
                    cfg.modules.replaceAll((id,on)->false);cfg.enabled=false;cfg.notifications=false;
                    cfg.pvpTotemHealth=1;cfg.pvpLootProtection=false;cfg.pvpFriends="";cfg.pvpFastAttack=false;cfg.pvpDelayTicks=2;cfg.pvpRange=6;cfg.pvpTargetPlayers=true;cfg.pvpTargetHostiles=false;cfg.save();
                    c.options.pauseOnLostFocus=false;c.options.renderDistance().set(2);c.getTutorial().setStep(TutorialSteps.NONE);
                    c.createWorldOpenFlows().createFreshLevel("UtilityQA",new LevelSettings("UtilityQA",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.NORMAL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(93,false,false),lookup->lookup.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),c.gui.screen());next();
                }
                case 1->{
                    if(c.player==null||c.getSingleplayerServer()==null)return;
                    fixture(c,(l,p)->{
                        l.getGameRules().set(GameRules.SPAWN_MOBS,false,l.getServer());
                        for(int x=-6;x<=6;x++)for(int z=-6;z<=8;z++){l.setBlock(new BlockPos(x,0,z),Blocks.BEDROCK.defaultBlockState(),3,512);for(int y=1;y<6;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3,512);}
                        p.setGameMode(GameType.SURVIVAL);p.teleportTo(l,.5,1,-3.5,Set.of(),180,0,true);kit(p,9);
                        p.getInventory().setItem(10,new ItemStack(Items.TOTEM_OF_UNDYING));p.inventoryMenu.sendAllDataToRemote();
                    });next();
                }
                case 2->{
                    if(!ready||!serverReady||age()<30)return;
                    beforeSwaps=swaps;int selected=c.player.getInventory().getSelectedSlot();
                    ModuleControls.setEnabled(c,"auto_totem",true);
                    check("Enable immediately replaces an offhand shield at full health with the legacy threshold set to 1",c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&c.player.getHealth()==20&&cfg.pvpTotemHealth==1);
                    check("First refill sends one ordinary inventory swap without waiting for a tick",swaps==beforeSwaps+1);
                    check("Displaced shield is preserved in the totem source slot",c.player.getInventory().getItem(9).is(Items.SHIELD));
                    check("Auto Totem leaves the selected weapon slot unchanged",c.player.getInventory().getSelectedSlot()==selected&&c.player.getMainHandItem().is(Items.DIAMOND_SWORD));next();
                }
                case 3->{
                    if(age()<40)return;
                    fixture(c,(l,p)->{
                        require(p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&p.getInventory().getItem(9).is(Items.SHIELD),"Initial server swap missing");
                        TotemPacketDelay.arm(p,Integer.getInteger("tung.qa.delayInventoryMs",0));
                        p.hurtServer(l,p.damageSources().generic(),1000);
                        require(p.isAlive()&&!p.getOffhandItem().is(Items.TOTEM_OF_UNDYING),"Actual server totem pop did not occur");p.inventoryMenu.broadcastChanges();
                    });next();
                }
                case 4->{
                    if(!ready||age()<20)return;
                    check("Actual server totem pop refills from another inventory slot",c.player.isAlive()&&c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&c.player.getInventory().getItem(10).isEmpty());
                    check("Refill is sent within one client tick of the actual pop, including delayed inventory echoes",!popRefillMs.isEmpty()&&popRefillMs.stream().mapToDouble(Double::doubleValue).max().orElse(999)<50);
                    if(Integer.getInteger("tung.qa.delayInventoryMs",0)>0)
                        check("The latency test genuinely held server inventory packets",TotemPacketDelay.heldPackets>=2);
                    check("Equipped totem causes no inverse swaps or duplicate clicks",swaps==beforeSwaps+2&&!ChunkFinderClient.MODULES.totemSwapPending());
                    ModuleControls.setEnabled(c,"auto_totem",false);
                    fixture(c,(l,p)->{
                        kit(p,11);p.getInventory().setItem(0,new ItemStack(Items.TOTEM_OF_UNDYING));
                        p.getInventory().setItem(40,new ItemStack(Items.TOTEM_OF_UNDYING));p.inventoryMenu.sendAllDataToRemote();
                    });go(31);
                }
                case 31->{
                    if(!ready||age()<25)return;
                    beforeSwaps=swaps;ModuleControls.setEnabled(c,"auto_totem",true);
                    fixture(c,(l,p)->{
                        TotemPacketDelay.arm(p,Math.max(400,Integer.getInteger("tung.qa.delayInventoryMs",0)));
                        p.setInvulnerableTime(0);p.hurtServer(l,p.damageSources().generic(),1000);
                        require(p.isAlive()&&p.getMainHandItem().isEmpty()&&p.getOffhandItem().is(Items.TOTEM_OF_UNDYING),"Main-hand fixture did not consume the main-hand totem first");
                        p.inventoryMenu.broadcastChanges();
                    });go(32);
                }
                case 32->{
                    if(!ready||age()<25)return;
                    check("Main-hand activation preserves the intact offhand without an extra swap",swaps==beforeSwaps&&c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&c.player.getInventory().getItem(11).is(Items.TOTEM_OF_UNDYING));
                    ModuleControls.setEnabled(c,"auto_totem",false);
                    fixture(c,(l,p)->{
                        kit(p,11);p.getInventory().setItem(12,new ItemStack(Items.TOTEM_OF_UNDYING));
                        p.getInventory().setItem(40,new ItemStack(Items.TOTEM_OF_UNDYING));p.inventoryMenu.sendAllDataToRemote();
                    });go(33);
                }
                case 33->{
                    if(!ready||age()<25)return;
                    beforeSwaps=swaps;ModuleControls.setEnabled(c,"auto_totem",true);
                    fixture(c,(l,p)->{
                        TotemPacketDelay.arm(p,Math.max(600,Integer.getInteger("tung.qa.delayInventoryMs",0)));
                        p.setInvulnerableTime(0);p.hurtServer(l,p.damageSources().generic(),1000);p.inventoryMenu.broadcastChanges();
                        chainedPopPlayer=p;
                    });go(34);
                }
                case 34->{
                    if(!ready||age()<40)return;
                    check("Two real pops before the delayed slot echoes use two distinct spare slots",swaps==beforeSwaps+2&&c.player.getInventory().getItem(11).isEmpty()&&c.player.getInventory().getItem(12).isEmpty());
                    check("Late echoes do not undo the final refill or leave a pending transaction",c.player.isAlive()&&c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&!ChunkFinderClient.MODULES.totemSwapPending());
                    check("Every offhand-pop request is sent within one client tick",popRefillMs.size()>=3&&popRefillMs.stream().mapToDouble(Double::doubleValue).max().orElse(999)<50);
                    ModuleControls.setEnabled(c,"auto_totem",false);
                    fixture(c,(l,p)->{
                        require(p.isAlive()&&p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&p.getInventory().getItem(11).isEmpty()&&p.getInventory().getItem(12).isEmpty(),"Rapid-pop server state missing");
                        kit(p,11);p.inventoryMenu.sendAllDataToRemote();chest.setItem(0,new ItemStack(Items.TOTEM_OF_UNDYING));
                        p.openMenu(new SimpleMenuProvider((id,inv,who)->ChestMenu.threeRows(id,inv,chest),Component.literal("Utility QA chest")));
                        p.containerMenu.setCarried(new ItemStack(Items.DIAMOND,7));p.containerMenu.sendAllDataToRemote();
                    });keepScreen=true;go(5);
                }
                case 5->{
                    if(!ready||age()<25)return;
                    check("Test fixture uses a genuine open chest menu, occupied cursor and unequipped totem",c.player.containerMenu!=c.player.inventoryMenu&&c.player.containerMenu.getCarried().getCount()==7&&c.player.getOffhandItem().is(Items.SHIELD));
                    beforeSwaps=swaps;ModuleControls.setEnabled(c,"auto_totem",true);
                    check("Refill works from player inventory while a chest is open",c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&swaps==beforeSwaps+1);
                    check("Swap preserves the carried cursor stack",c.player.containerMenu.getCarried().is(Items.DIAMOND)&&c.player.containerMenu.getCarried().getCount()==7);next();
                }
                case 6->{
                    if(age()<20)return;
                    fixture(c,(l,p)->{
                        require(p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&p.getInventory().getItem(11).is(Items.SHIELD),"Chest-menu server swap missing");
                        require(chest.getItem(0).is(Items.TOTEM_OF_UNDYING)&&p.containerMenu.getCarried().getCount()==7,"Chest loot or cursor was changed");
                    });next();
                }
                case 7->{
                    if(!ready)return;check("Server confirms chest-menu refill without taking chest loot",true);
                    ModuleControls.setEnabled(c,"auto_totem",false);keepScreen=false;
                    fixture(c,(l,p)->{p.closeContainer();kit(p,12);p.inventoryMenu.sendAllDataToRemote();});next();
                }
                case 8->{
                    if(!ready||age()<25)return;beforeSwaps=swaps;rejectNext=true;ModuleControls.setEnabled(c,"auto_totem",true);next();
                }
                case 9->{
                    if(age()<25)return;
                    check("A server-rejected swap recovers without a long fixed delay",rejections==1&&c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&swaps==beforeSwaps+2);
                    fixture(c,(l,p)->require(p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)&&p.getInventory().getItem(12).is(Items.SHIELD),"Rejected swap recovery missing on server"));next();
                }
                case 10->{
                    if(!ready)return;check("Recovered totem is present on the actual server",true);ModuleControls.setEnabled(c,"auto_totem",false);
                    fixture(c,(l,p)->{
                        kit(p,14);p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2000);p.setAbsorptionAmount(1000);
                        p.getInventory().setItem(0,new ItemStack(Items.RESPAWN_ANCHOR,64));p.getInventory().setItem(10,new ItemStack(Items.GLOWSTONE,64));p.inventoryMenu.sendAllDataToRemote();
                    });next();
                }
                case 11->{
                    if(!ready||age()<25)return;
                    cfg.pvpManualAssist=true;cfg.pvpAnchorCover=false;ModuleControls.setEnabled(c,"auto_anchor",true);
                    var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(.5,1,.5),Direction.UP,new BlockPos(0,0,0),false);
                    check("Fixture starts a real manual Anchor combo",c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,hit).consumesAction()&&ChunkFinderClient.PVP.ownsInventory());
                    ModuleControls.setEnabled(c,"auto_totem",true);
                    check("An unrelated Anchor inventory transaction does not delay the totem",c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING));next();
                }
                case 12->{
                    if(age()<35)return;ModuleControls.setEnabled(c,"auto_anchor",false);ModuleControls.setEnabled(c,"auto_totem",false);
                    fixture(c,(l,p)->{kit(p,15);p.inventoryMenu.sendAllDataToRemote();});next();
                }
                case 13->{
                    if(!ready||age()<25)return;ModuleControls.setEnabled(c,"mace_bot",true);ModuleControls.setEnabled(c,"auto_totem",true);
                    check("Mace PvP does not override the user's enabled Auto Totem",c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING));
                    ModuleControls.setEnabled(c,"mace_bot",false);ModuleControls.setEnabled(c,"auto_totem",false);
                    fixture(c,(l,p)->{
                        p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.DIAMOND_SWORD));p.getInventory().setSelectedSlot(0);p.setHealth(20);p.setAbsorptionAmount(1000);p.teleportTo(l,.5,1,.5,Set.of(),180,0,true);p.inventoryMenu.sendAllDataToRemote();
                        for(int x=-2;x<=2;x++)for(int z=-2;z<=5;z++)for(int y=1;y<=4;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3,512);
                        var profile=new GameProfile(UUID.fromString("aabbccdd-aabb-ccdd-aabb-ccddaabbccdd"),"AuraOpponent");
                        npc=new ServerPlayer(l.getServer(),l,profile,ClientInformation.createDefault());npc.connection=new ServerGamePacketListenerImpl(l.getServer(),new Connection(PacketFlow.SERVERBOUND),npc,CommonListenerCookie.createInitial(profile,false));
                        npc.setPos(.5,1,2.5);npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);npc.setHealth(200);npc.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2000);npc.setAbsorptionAmount(1000);npc.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                        p.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(npc)));l.addNewPlayer(npc);
                        npc.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                    });next();
                }
                case 14->{
                    if(!ready||age()<30)return;
                    check("Server opponent completed the normal player-loaded acknowledgement",npc.connection.hasClientLoaded());
                    if(!(c.level.getEntity(npc.getId()) instanceof net.minecraft.world.entity.player.Player)){var remote=new RemotePlayer(c.level,npc.getGameProfile());remote.setId(npc.getId());remote.setPos(npc.position());c.level.addEntity(remote);}
                    beforeAttacks=attacks;initialPosition=c.player.position();initialAbsorption=npc.getHealth()+npc.getAbsorptionAmount();ModuleControls.setEnabled(c,"killaura",true);next();
                }
                case 15->{
                    if(age()<35)return;
                    check("Kill Aura attacks a nearby player behind the current view",attacks>beforeAttacks&&c.player.getYRot()==180);
                    check("Normal melee attacks actually damage the server opponent",npc.getHealth()+npc.getAbsorptionAmount()<initialAbsorption);
                    check("Kill Aura does not move the player's body",c.player.position().distanceToSqr(initialPosition)<.01);
                    cfg.pvpFriends="AuraOpponent";beforeAttacks=attacks;next();
                }
                case 16->{
                    if(age()<15)return;check("Friend filtering stops automatic melee attacks",attacks==beforeAttacks);cfg.pvpFriends="";
                    fixture(c,(l,p)->npc.teleportTo(l,.5,1,8.5,Set.of(),0,0,true));next();
                }
                case 17->{
                    if(!ready||age()<20)return;beforeAttacks=attacks;next();
                }
                case 18->{
                    if(age()<15)return;check("Increasing target range does not extend normal weapon reach",attacks==beforeAttacks);
                    fixture(c,(l,p)->{npc.teleportTo(l,.5,1,2.5,Set.of(),0,0,true);for(int y=1;y<=3;y++)l.setBlock(new BlockPos(0,y,1),Blocks.OBSIDIAN.defaultBlockState(),3,512);});next();
                }
                case 19->{if(!ready||age()<20)return;beforeAttacks=attacks;next();}
                case 20->{
                    if(age()<15)return;check("Kill Aura skips targets behind a solid wall",attacks==beforeAttacks);cfg.pvpTargetPlayers=false;
                    fixture(c,(l,p)->{for(int y=1;y<=3;y++)l.setBlock(new BlockPos(0,y,1),Blocks.AIR.defaultBlockState(),3,512);});next();
                }
                case 21->{
                    if(!ready||age()<20)return;check("Disabled target types remain excluded",attacks==beforeAttacks);cfg.pvpTargetPlayers=true;keepScreen=true;c.gui.setScreen(new InventoryScreen(c.player));next();
                }
                case 22->{
                    if(age()<15)return;check("Opening the inventory pauses automatic melee",attacks==beforeAttacks);keepScreen=false;c.gui.setScreen(null);ModuleControls.setEnabled(c,"killaura",false);beforeAttacks=attacks;next();
                }
                case 23->{
                    if(age()<15)return;check("Disabling Kill Aura immediately stops further attacks",attacks==beforeAttacks);
                    lookForward=true;c.options.setCameraType(CameraType.FIRST_PERSON);ModuleControls.setEnabled(c,"player_info",true);
                    fixture(c,(l,p)->{npc.setInvisible(true);npc.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.NETHERITE_SWORD));});next();
                }
                case 24->{
                    if(!ready||age()<25)return;
                    var remote=(net.minecraft.world.entity.player.Player)c.level.getEntity(npc.getId());
                    var state=avatarState(c,remote);
                    check("Invisible opponent uses vanilla hidden-body render state before enabling Invis",remote.isInvisible()&&state.isInvisible&&state.isInvisibleToPlayer);
                    shot(c,"invisible-opponent-off");ModuleControls.setEnabled(c,"invis_mode",true);beforeHands=handsSubmitted;beforeLabels=invisibleLabels;
                    check("Normal module controls enable Invis Mode",InvisMode.active());
                    state=avatarState(c,remote);
                    check("Production avatar renderer reveals an invisible opponent",!state.isInvisible&&!state.isInvisibleToPlayer);
                    check("Reveal preserves real invisibility and equipped armor",remote.isInvisible()&&remote.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET));
                    check("Invis Mode does not reveal or hide the local player",!InvisMode.reveals(c.player));next();
                }
                case 25->{
                    if(age()<20)return;
                    check("Own hands and held items remain visible with Invis Mode enabled",handsSubmitted>beforeHands);
                    check("Red Invisible header is actually drawn above Player Info",invisibleLabels>beforeLabels);shot(c,"invisible-opponent-on");
                    fixture(c,(l,p)->require(npc.isInvisible()&&!p.isInvisible(),"Server invisibility flags were altered"));
                    ModuleControls.setEnabled(c,"invis_mode",false);beforeLabels=invisibleLabels;
                    check("Disable immediately restores vanilla invisible-avatar flags",avatarState(c,c.level.getEntity(npc.getId())).isInvisible);
                    next();
                }
                case 26->{
                    if(!ready||age()<15)return;check("Invisible header stops drawing when the module is disabled",invisibleLabels==beforeLabels);shot(c,"invisible-opponent-restored");
                    check("Disabling Invis preserves the Player Info preference",cfg.isModuleEnabled("player_info"));ModuleControls.setEnabled(c,"invis_mode",true);
                    fixture(c,(l,p)->npc.setInvisible(false));next();
                }
                case 27->{
                    if(!ready||age()<20)return;var remote=c.level.getEntity(npc.getId());
                    check("Removing real invisibility clears the reveal condition",!remote.isInvisible()&&!InvisMode.reveals(remote));
                    check("Ordinary visible avatars keep their normal render state",!avatarState(c,remote).isInvisible);beforeLabels=invisibleLabels;next();
                }
                case 28->{
                    if(age()<15)return;check("Visible players do not retain a stale Invisible header",invisibleLabels==beforeLabels);c.disconnectWithSavingScreen();next();
                }
                case 29->{check("Disconnect leaves no active invisibility reveal state",!InvisMode.active());finished=true;stage=30;report();c.stop();}
            }
        }catch(Throwable failure){error=failure.toString();failure.printStackTrace();finished=true;report();c.stop();}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private net.minecraft.client.renderer.entity.state.AvatarRenderState avatarState(Minecraft c,Entity entity){
        var renderer=(net.minecraft.client.renderer.entity.player.AvatarRenderer)c.getEntityRenderDispatcher().getRenderer(entity);
        var state=new net.minecraft.client.renderer.entity.state.AvatarRenderState();renderer.extractRenderState((net.minecraft.world.entity.Avatar)entity,state,1);return state;
    }
    private void kit(ServerPlayer p,int source){p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.DIAMOND_SWORD));p.getInventory().setSelectedSlot(0);p.getInventory().setItem(source,new ItemStack(Items.TOTEM_OF_UNDYING));p.getInventory().setItem(40,new ItemStack(Items.SHIELD));p.setHealth(20);p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2000);p.setAbsorptionAmount(40);}
    @FunctionalInterface private interface Fixture{void run(ServerLevel level,ServerPlayer player)throws Exception;}
    private void fixture(Minecraft c,Fixture fixture){ready=false;c.getSingleplayerServer().execute(()->{try{fixture.run(c.getSingleplayerServer().overworld(),c.getSingleplayerServer().getPlayerList().getPlayers().getFirst());}catch(Throwable failure){error=failure.toString();}finally{ready=true;}});}
    private void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private int age(){return ticks-since;}
    private void next(){stage++;since=ticks;report();}
    private void go(int value){stage=value;since=ticks;report();}
    private void check(String label,boolean passed){checks.add(Map.of("check",label,"passed",passed));report();if(!passed)throw new AssertionError(label);}
    private void shot(Minecraft c,String name){Screenshot.takeScreenshot(c.gameRenderer.mainRenderTarget(),image->{try{image.writeToFile(OUT.resolve(name+".png"));}catch(Exception failure){error=failure.toString();}finally{image.close();}});}
    private void report(){try{
        var c=Minecraft.getInstance();var result=new LinkedHashMap<String,Object>();
        result.put("stage",stage);result.put("error",error);result.put("checks",checks);result.put("swaps",swaps);
        result.put("serverSwaps",serverSwaps);result.put("rejections",rejections);result.put("attacks",attacks);
        result.put("refillMs",refillMs);result.put("popRefillMs",popRefillMs);
        result.put("heldInventoryPackets",TotemPacketDelay.heldPackets);result.put("userProfileTouched",false);
        result.put("debug",c.player==null?Map.of():Map.of("offhand",c.player.getOffhandItem().toString(),"source9",c.player.getInventory().getItem(9).toString(),"health",c.player.getHealth(),"absorption",c.player.getAbsorptionAmount(),"menu",c.player.containerMenu.containerId,"status",ChunkFinderClient.MODULES.status("auto_totem")));
        Files.writeString(OUT.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }catch(Exception failure){throw new RuntimeException(failure);}}
}
