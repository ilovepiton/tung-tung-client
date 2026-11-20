package dev.amethyst.finder.builder;

import com.google.gson.JsonObject;
import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.modules.pvp.BotNavigation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Session-scoped build/mine coordinator. Preview never changes blocks. Server receipts finish actions. */
public final class AutoBuilder {
    public record Target(BlockPos pos,BlockState state){}
    public record Mark(BlockPos min,BlockPos max,int color){}
    private final ExecutorService reader=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Schematic reader");t.setDaemon(true);return t;});
    private final BuilderNavigation navigation=new BuilderNavigation();
    private SchematicFile schematic;
    private List<Target> targets=List.of();private List<List<Object>> preview=List.of();
    private final BitSet done=new BitSet();private final Map<BlockPos,BlockState> authoritative=new HashMap<>();private int scan,clock,loadSerial,turn,layer=-1;
    private BlockPos origin,cornerA,cornerB,walkGoal;private Object world,sessionPlayer;private int markBucket=-1;private List<Mark> cachedMarks=List.of();private boolean running,loading,mine,manual,background,show=true;private Boolean oldPauseOnLostFocus;
    private String status="Import a schematic to preview your build",filename="";
    private int pending=-1,pendingAt;private boolean received;private BlockState receipt;
    private int selectedBefore=-1,ownedSlot=-1,swapAt;private Vec3 lastPosition;private int stuck;
    private Map<String,Object> view=Map.of();

    public void command(Minecraft c,JsonObject data)throws Exception {
        String op=data.get("op").getAsString();
        if(op.equals("pause")){pause(c,"Paused");return;}
        if(c.level==null||c.player==null)throw new IllegalStateException("Join a world first");
        if(op.equals("import")) {
            requireIdle();pause(c,"Reading schematic…");String file=data.get("file").getAsString();
            if(!file.matches("[a-f0-9]{32}\\.(schem|schematic|litematic|nbt)"))throw new IllegalArgumentException("Choose a schematic with the file picker");
            Path dir=FabricLoader.getInstance().getConfigDir().resolve("tuntun-schematics");Path path=dir.resolve(file);
            if(Files.isSymbolicLink(path)||!path.toRealPath().getParent().equals(dir.toRealPath()))throw new IllegalArgumentException("Invalid schematic file");
            int serial=++loadSerial;Object level=c.level;Object player=c.player;world=level;sessionPlayer=player;loading=true;String label=data.has("label")?data.get("label").getAsString():file;
            reader.execute(()->{try {SchematicFile raw=SchematicFile.read(path);SchematicFile parsed=new SchematicFile(label.substring(0,Math.min(120,label.length())),raw.format(),raw.width(),raw.height(),raw.length(),raw.cells(),raw.warnings());c.execute(()->{
                if(serial!=loadSerial||c.level!=level||c.player!=player)return;
                loading=false;try {schematic=parsed;filename=file;origin=c.player.blockPosition();world=level;mine=false;turn=0;layer=-1;rebuild(c);status="Preview ready · choose position and mode";refresh(c);}catch(Exception ex){schematic=null;targets=List.of();status=ex.getMessage();refresh(c);}
            });}catch(Exception ex){c.execute(()->{if(serial==loadSerial){loading=false;status="Import failed: "+ex.getMessage();refresh(c);}});}});
        } else if(op.equals("place")) {
            requireIdle();if(schematic==null)throw new IllegalStateException("Import a schematic first");
            BlockPos next=data.has("here")&&data.get("here").getAsBoolean()?c.player.blockPosition():position(data,"x","y","z");
            int rotation=intValue(data,"rotation",0,0,3),cut=intValue(data,"layer",-1,-1,schematic.height()-1);
            validateHeight(c,next.getY(),next.getY()+schematic.height()-1);
            origin=next;turn=rotation;layer=cut;manual=data.has("manual")&&data.get("manual").getAsBoolean();background=data.has("background")&&data.get("background").getAsBoolean();show=!data.has("show")||data.get("show").getAsBoolean();world=c.level;sessionPlayer=c.player;mine=false;rebuild(c);status=manual?"Manual guide ready · no automatic actions":"Build preview ready";
        } else if(op.equals("corner")) {
            requireIdle();String which=data.get("corner").getAsString();
            BlockPos p=data.has("here")&&data.get("here").getAsBoolean()?c.player.blockPosition():position(data,"x","y","z");
            validateHeight(c,p.getY(),p.getY());if(which.equals("a"))cornerA=p;else if(which.equals("b"))cornerB=p;else throw new IllegalArgumentException("Choose corner A or B");
            if(mine){targets=List.of();done.clear();authoritative.clear();origin=null;}world=c.level;sessionPlayer=c.player;status="Corner "+which.toUpperCase(Locale.ROOT)+" selected";
        } else if(op.equals("area")) {
            requireIdle();if(cornerA==null||cornerB==null)throw new IllegalStateException("Select both corners first");
            BlockPos low=new BlockPos(Math.min(cornerA.getX(),cornerB.getX()),Math.min(cornerA.getY(),cornerB.getY()),Math.min(cornerA.getZ(),cornerB.getZ()));
            BlockPos high=new BlockPos(Math.max(cornerA.getX(),cornerB.getX()),Math.max(cornerA.getY(),cornerB.getY()),Math.max(cornerA.getZ(),cornerB.getZ()));
            int volume=SchematicFile.volume(high.getX()-low.getX()+1,high.getY()-low.getY()+1,high.getZ()-low.getZ()+1);
            if(volume>SchematicFile.MAX_BLOCKS)throw new IllegalArgumentException("Mine Area supports up to 200,000 blocks");
            var plan=new ArrayList<Target>(volume);for(int y=high.getY();y>=low.getY();y--)for(int z=low.getZ();z<=high.getZ();z++)for(int x=low.getX();x<=high.getX();x++)plan.add(new Target(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState()));
            targets=List.copyOf(plan);mine=true;manual=false;background=data.has("background")&&data.get("background").getAsBoolean();world=c.level;sessionPlayer=c.player;origin=low;done.clear();authoritative.clear();markBucket=-1;scan=0;show=true;status="Area preview ready · Start mining will remove blocks inside these bounds";
        } else if(op.equals("start")) {
            if(pending>=0)throw new IllegalStateException("Wait for the last server confirmation before resuming");
            if(loading||targets.isEmpty()||world!=c.level)throw new IllegalStateException("Prepare a preview in the current world first");
            if(manual)throw new IllegalStateException("Manual guide selected · build yourself or select Automatic");
            if(c.gameMode.isSpectator()||c.gameMode.getPlayerMode()==net.minecraft.world.level.GameType.ADVENTURE)throw new IllegalStateException("Automatic actions need Survival or Creative mode");
            if(combatActive())throw new IllegalStateException("Turn off combat bots before starting the builder");
            if(background&&oldPauseOnLostFocus==null){oldPauseOnLostFocus=c.options.pauseOnLostFocus;c.options.pauseOnLostFocus=false;}
            running=true;selectedBefore=c.player.getInventory().getSelectedSlot();ownedSlot=-1;pending=-1;walkGoal=null;navigation.reset();stuck=0;status="Starting · return to Minecraft";
        } else throw new IllegalArgumentException("Unknown builder operation");
        refresh(c);
    }
    private static boolean combatActive(){return ChunkFinderClient.BOT.enabled()||ChunkFinderClient.MACE.enabled()||Set.of("auto_crystal","auto_anchor","hard_crystal","kill_aura","trigger_bot").stream().anyMatch(ChunkFinderClient.CONFIG::isModuleEnabled);}
    private void requireIdle(){if(running||loading||pending>=0)throw new IllegalStateException("Pause the current task before changing its preview");}
    private static int intValue(JsonObject j,String key,int fallback,int min,int max){if(!j.has(key))return fallback;var v=j.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Double.isFinite(v.getAsDouble())||v.getAsDouble()!=v.getAsInt()||v.getAsInt()<min||v.getAsInt()>max)throw new IllegalArgumentException("Invalid "+key);return v.getAsInt();}
    private static BlockPos position(JsonObject j,String x,String y,String z){for(String key:List.of(x,y,z))if(!j.has(key))throw new IllegalArgumentException("Enter all three coordinates");return new BlockPos(intValue(j,x,0,-29_999_000,29_999_000),intValue(j,y,0,-2048,2048),intValue(j,z,0,-29_999_000,29_999_000));}
    private static void validateHeight(Minecraft c,int low,int high){if(low<c.level.getMinY()||high>c.level.getMaxY())throw new IllegalArgumentException("Selection is outside this world's height");}
    private void rebuild(Minecraft c)throws Exception {
        Map<String,BlockState> palette=new HashMap<>();var plan=new ArrayList<Target>();var voxels=new ArrayList<List<Object>>();
        Rotation rotation=Rotation.values()[turn];
        for(var cell:schematic.cells()) {
            BlockState state=palette.get(cell.state());if(state==null){state=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,cell.state(),false).blockState().rotate(rotation);palette.put(cell.state(),state);}
            int x=cell.x(),z=cell.z();switch(turn){case 1->{x=schematic.length()-1-cell.z();z=cell.x();}case 2->{x=schematic.width()-1-cell.x();z=schematic.length()-1-cell.z();}case 3->{x=cell.z();z=schematic.width()-1-cell.x();}}
            plan.add(new Target(origin.offset(x,cell.y(),z),state));
            if(voxels.size()<2000)voxels.add(List.of(x,cell.y(),z,cell.state()));
        }
        targets=List.copyOf(plan);preview=List.copyOf(voxels);done.clear();authoritative.clear();markBucket=-1;scan=0;pending=-1;
    }
    public boolean active(){return running;}
    public boolean ownsMining(Minecraft c){return running&&mine&&c.player!=null&&world==c.level&&c.gui.screen()==null&&c.gui.overlay()==null&&(background||c.isWindowActive()&&c.mouseHandler.isMouseGrabbed())&&!manualInput(c);}
    public Map<String,Object> state(){return view;}
    public void pause(Minecraft c,String reason) {
        running=false;walkGoal=null;navigation.reset();
        if(oldPauseOnLostFocus!=null){c.options.pauseOnLostFocus=oldPauseOnLostFocus;oldPauseOnLostFocus=null;}
        if(c.gameMode!=null)c.gameMode.stopDestroyBlock();
        if(c.player!=null&&ownedSlot>=0&&c.player.getInventory().getSelectedSlot()==ownedSlot&&selectedBefore>=0)c.player.getInventory().setSelectedSlot(selectedBefore);
        ownedSlot=selectedBefore=-1;status=reason;refresh(c);
    }
    public void received(BlockPos p,BlockState state) {if(authoritative.containsKey(p))authoritative.put(p.immutable(),state);if(pending>=0&&targets.get(pending).pos.equals(p)){received=true;receipt=state;}}
    public void tick(Minecraft c) {
        clock++;
        if(world!=null&&(world!=c.level||sessionPlayer!=c.player)) {pause(c,"World changed · prepare a new preview");world=null;targets=List.of();preview=List.of();done.clear();authoritative.clear();cornerA=cornerB=origin=null;loadSerial++;loading=false;pending=-1;refresh(c);return;}
        if(c.level==null||c.player==null)return;
        if(pending>=0) {
            if(received) {if(matches(receipt,targets.get(pending).state)){done.set(pending);pending=-1;received=false;}else {pending=-1;pause(c,"Server rejected the block action · check protection, tools and materials");}}
            else if(clock-pendingAt>100){pending=-1;pause(c,"No server block confirmation · task paused");}
        }
        for(int i=0;i<Math.min(512,targets.size());i++){if(scan>=targets.size())scan=0;Target t=targets.get(scan);if(scan!=pending)done.set(scan,c.level.hasChunkAt(t.pos)&&matches(c.level.getBlockState(t.pos),t.state)&&(!authoritative.containsKey(t.pos)||matches(authoritative.get(t.pos),t.state)));scan++;}
        if(running) {
            if(!c.player.isAlive()||c.player.getHealth()<6){pause(c,"Low health or death · paused");return;}
            if(combatActive()){pause(c,"Combat bot active · builder paused");return;}
            if(c.gui.screen()!=null||c.gui.overlay()!=null||!background&&(!c.isWindowActive()||!c.mouseHandler.isMouseGrabbed())){walkGoal=null;status="Paused while a menu is open · return to Minecraft";}
            else if(manualInput(c)){walkGoal=null;status="Your controls have priority";}
            else if(pending<0&&clock-swapAt>=4)work(c);
            if(done.cardinality()==targets.size()&&pending<0)pause(c,mine?"Area cleared":"Build complete · all block states match");
        }
        if(clock%10==0)refresh(c);
    }
    private void work(Minecraft c) {
        int best=-1;double nearest=Double.MAX_VALUE;int first=done.nextClearBit(0),end=Math.min(targets.size(),first+2048);
        for(int i=first;i<end;i++)if(!done.get(i)) {
            if(targets.get(i).pos.getY()!=targets.get(first).pos.getY())break;Target t=targets.get(i);if(!c.level.hasChunkAt(t.pos))continue;
            if(matches(c.level.getBlockState(t.pos),t.state)&&(!authoritative.containsKey(t.pos)||matches(authoritative.get(t.pos),t.state))){done.set(i);continue;}
            double distance=c.player.position().distanceToSqr(Vec3.atCenterOf(t.pos));
            if(distance<nearest){nearest=distance;best=i;}
        }
        if(best<0){walkGoal=null;status="Waiting for selected chunks to load";return;}
        Target target=targets.get(best);BlockPos p=target.pos;
        if(c.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))>9) {walk(c,p);return;}
        walkGoal=null;
        if(mine)mine(c,best);else place(c,best);
    }
    private void walk(Minecraft c,BlockPos p){walk(c,p,false);}
    private void walk(Minecraft c,BlockPos p,boolean reposition) {
        BlockPos best=null;double distance=Double.MAX_VALUE;
        for(int dy=-3;dy<=1;dy++)for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            BlockPos stand=p.offset(dx,dy,dz);if(!BotNavigation.standing(c,stand)||stand.equals(p)||stand.above().equals(p)||reposition&&stand.equals(c.player.blockPosition()))continue;
            if(!visibleFrom(c,Vec3.atBottomCenterOf(stand).add(0,c.player.getEyeHeight(),0),p))continue;
            if(Vec3.atBottomCenterOf(stand).add(0,c.player.getEyeHeight(),0).distanceToSqr(Vec3.atCenterOf(p))>8)continue;
            double d=c.player.position().distanceToSqr(Vec3.atBottomCenterOf(stand));if(d<distance){distance=d;best=stand;}
        }
        if(best==null){pause(c,"No safe standing place · add access or scaffolding, then resume");return;}
        walkGoal=best;status="Walking to the next block";
        if(lastPosition!=null&&c.player.position().distanceToSqr(lastPosition)<.0004)stuck++;else stuck=0;
        lastPosition=c.player.position();if(stuck>100)pause(c,"Route blocked · move closer or add access, then resume");
    }
    private boolean visibleFrom(Minecraft c,Vec3 eye,BlockPos p){
        if(mine){var hit=c.level.clip(new ClipContext(eye,Vec3.atCenterOf(p),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(p);}
        for(Direction side:Direction.values()){
            BlockPos support=p.relative(side.getOpposite());if(!c.level.hasChunkAt(support)||c.level.getBlockState(support).getCollisionShape(c.level,support).isEmpty())continue;
            Vec3 point=Vec3.atCenterOf(support).add(side.getStepX()*.5,side.getStepY()*.5,side.getStepZ()*.5);
            if(eye.distanceToSqr(point)>16)continue;
            var hit=c.level.clip(new ClipContext(eye,point.add(Vec3.atCenterOf(support).subtract(point).scale(.001)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(support)&&hit.getDirection()==side)return true;
        }
        return false;
    }
    private boolean manualInput(Minecraft c){return c.options.keyUp.isDown()||c.options.keyDown.isDown()||c.options.keyLeft.isDown()||c.options.keyRight.isDown()||c.options.keyJump.isDown()||c.options.keyShift.isDown()||c.options.keyAttack.isDown()||c.options.keyUse.isDown();}
    public Input movement(Minecraft c,Input manualKeys) {
        if(!running||walkGoal==null||c.player==null||c.level!=world||c.gui.screen()!=null||!background&&(!c.isWindowActive()||!c.mouseHandler.isMouseGrabbed())||manualInput(c))return manualKeys;
        Vec3 direction=navigation.direction(c,walkGoal);if(direction.lengthSqr()<.01)return manualKeys;
        Vec3 d=direction.multiply(1,0,1).normalize();double yaw=Math.toRadians(c.player.getYRot()),f=d.dot(new Vec3(-Math.sin(yaw),0,Math.cos(yaw))),side=d.dot(new Vec3(Math.cos(yaw),0,Math.sin(yaw)));
        return new Input(f>.25,f<-.25,side>.25,side<-.25,direction.y>.35&&c.player.onGround(),false,false);
    }
    private void select(Minecraft c,int slot){if(ownedSlot<0)selectedBefore=c.player.getInventory().getSelectedSlot();c.player.getInventory().setSelectedSlot(slot);ownedSlot=slot;}
    private void mine(Minecraft c,int index) {
        Target target=targets.get(index);BlockPos p=target.pos;var state=c.level.getBlockState(p);
        if(state.hasBlockEntity()){pause(c,"Container or block entity at "+p.toShortString()+" · remove it manually");return;}
        if(!c.level.getFluidState(p).isEmpty()||state.getDestroySpeed(c.level,p)<0){pause(c,"Fluid or unbreakable block at "+p.toShortString());return;}
        if(c.player.getBoundingBox().inflate(.1,.05,.1).intersects(new AABB(p))||p.equals(c.player.blockPosition().below())){pause(c,"Move outside the block being mined, then resume");return;}
        Vec3 eye=c.player.getEyePosition();BlockHitResult hit=c.level.clip(new ClipContext(eye,Vec3.atCenterOf(p),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(p)){walk(c,p,true);return;}
        int tool=-1;float speed=1;
        for(int i=0;i<9;i++){var stack=c.player.getInventory().getItem(i);if(!stack.isEmpty()&&(!stack.isDamageableItem()||stack.getMaxDamage()-stack.getDamageValue()>2)&&stack.getDestroySpeed(state)>speed){speed=stack.getDestroySpeed(state);tool=i;}}
        if(tool>=0)select(c,tool);
        var held=c.player.getMainHandItem();if(held.isDamageableItem()&&held.getMaxDamage()-held.getDamageValue()<=2){pause(c,"Tool nearly broken · replace it before resuming");return;}
        if(state.requiresCorrectToolForDrops()&&!c.player.getMainHandItem().isCorrectToolForDrops(state)){pause(c,"Put the correct mining tool in your hotbar");return;}
        face(c,hit.getLocation(),Float.NaN);status="Mining "+p.toShortString();
        if(c.gameMode.isDestroying())c.gameMode.continueDestroyBlock(p,hit.getDirection());else c.gameMode.startDestroyBlock(p,hit.getDirection());
        c.player.swing(InteractionHand.MAIN_HAND,c.player.getMainHandItem().getAttackAnimation(),false);
        // Vanilla prediction becomes air only when the break has finished; still wait for the server.
        if(c.level.getBlockState(p).isAir())beginPending(index);
    }
    private void place(Minecraft c,int index) {
        Target t=targets.get(index);BlockPos p=t.pos;
        if(!c.level.getBlockState(p).canBeReplaced()){pause(c,"Wrong block at "+p.toShortString()+" · clear it first");return;}
        Item item=t.state.getBlock().asItem();if(!(item instanceof BlockItem)){pause(c,"This block needs manual placement: "+BuiltInRegistries.BLOCK.getKey(t.state.getBlock()));return;}
        int slot=-1;for(int i=0;i<9;i++)if(c.player.getInventory().getItem(i).is(item)){slot=i;break;}
        if(slot<0) {for(int i=9;i<36;i++)if(c.player.getInventory().getItem(i).is(item)){int hot=c.player.getInventory().getSelectedSlot();c.gameMode.handleContainerInput(c.player.inventoryMenu.containerId,i,hot,ContainerInput.SWAP,c.player);swapAt=clock;status="Moving material to the hotbar";return;}
            pause(c,"Missing material: "+item.getName(new ItemStack(item)).getString());return;}
        select(c,slot);var stack=c.player.getMainHandItem();
        float oldYaw=c.player.getYRot(),oldPitch=c.player.getXRot();BlockHitResult chosen=null;float chosenYaw=oldYaw;
        outer:for(Direction direction:Direction.values()) {
            BlockPos support=p.relative(direction.getOpposite());if(!c.level.hasChunkAt(support))continue;var supportState=c.level.getBlockState(support);
            if(supportState.getCollisionShape(c.level,support).isEmpty()||supportState.hasBlockEntity()||supportState.getBlock() instanceof DoorBlock||supportState.getBlock() instanceof TrapDoorBlock||supportState.getBlock() instanceof FenceGateBlock)continue;
            for(double vertical:new double[]{.5,.25,.75}) {
                Vec3 point=Vec3.atCenterOf(support).add(direction.getStepX()*.5,direction.getStepY()*.5,direction.getStepZ()*.5);
                if(direction.getAxis()!=Direction.Axis.Y)point=new Vec3(point.x,support.getY()+vertical,point.z);
                if(c.player.getEyePosition().distanceToSqr(point)>16)continue;
                var ray=c.level.clip(new ClipContext(c.player.getEyePosition(),point.add(Vec3.atCenterOf(support).subtract(point).scale(.001)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player));
                if(ray.getType()!=HitResult.Type.BLOCK||!ray.getBlockPos().equals(support)||ray.getDirection()!=direction)continue;
                var hit=new BlockHitResult(point,direction,support,false);
                for(float yaw:new float[]{oldYaw,0,90,180,270}) {
                    face(c,point,yaw);var context=new BlockPlaceContext(c.player,InteractionHand.MAIN_HAND,stack,hit);
                    BlockState placed=t.state.getBlock().getStateForPlacement(context);
                    if(context.getClickedPos().equals(p)&&placed!=null&&matches(placed,t.state)&&placed.canSurvive(c.level,p)&&c.level.isUnobstructed(placed,p,net.minecraft.world.phys.shapes.CollisionContext.of(c.player))) {chosen=hit;chosenYaw=yaw;break outer;}
                }
            }
        }
        c.player.setYRot(oldYaw);c.player.setXRot(oldPitch);
        if(chosen==null){walk(c,p,true);return;}
        face(c,chosen.getLocation(),chosenYaw);c.player.connection.send(new ServerboundMovePlayerPacket.Rot(c.player.getYRot(),c.player.getXRot(),c.player.onGround(),c.player.horizontalCollision));
        beginPending(index);c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,chosen);c.player.swing(InteractionHand.MAIN_HAND,c.player.getMainHandItem().getAttackAnimation(),false);status="Placing · waiting for the server";
    }
    private void beginPending(int index){pending=index;pendingAt=clock;received=false;receipt=null;authoritative.put(targets.get(index).pos,null);done.clear(index);}
    private static void face(Minecraft c,Vec3 point,float yaw){Vec3 d=point.subtract(c.player.getEyePosition());c.player.setYRot(Float.isNaN(yaw)?(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90):yaw);c.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
    public static boolean matches(BlockState actual,BlockState desired){
        if(actual==null)return false;if(desired.isAir())return actual.isAir();if(actual.getBlock()!=desired.getBlock())return false;
        for(var property:desired.getProperties())if(!Set.of("powered","power","lit","open","age","moisture","distance","persistent","signal_fire","snowy","occupied","triggered","note","instrument","locked").contains(property.getName())&&!Objects.equals(actual.getValue(property),desired.getValue(property)))return false;
        return true;
    }
    public List<Mark> marks(Minecraft c) {
        if(!show||origin==null||world!=c.level||targets.isEmpty())return List.of();if(markBucket==clock/5)return cachedMarks;markBucket=clock/5;var result=new ArrayList<Mark>();
        if(mine){BlockPos a=targets.getFirst().pos,b=targets.getLast().pos;result.add(new Mark(new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())),new BlockPos(Math.max(a.getX(),b.getX())+1,Math.max(a.getY(),b.getY())+1,Math.max(a.getZ(),b.getZ())+1),0xE9A25C));}
        else for(int i=0;i<targets.size()&&result.size()<256;i++){Target t=targets.get(i);if(done.get(i)||layer>=0&&t.pos.getY()!=origin.getY()+layer||c.player.position().distanceToSqr(Vec3.atCenterOf(t.pos))>4096)continue;result.add(new Mark(t.pos,t.pos.offset(1,1,1),0x8CCDC0));}
        cachedMarks=List.copyOf(result);return cachedMarks;
    }
    private void refresh(Minecraft c) {
        var m=new LinkedHashMap<String,Object>();m.put("available",true);m.put("running",running);m.put("loading",loading);m.put("manual",manual);m.put("mine",mine);m.put("show",show);m.put("background",background);m.put("status",status);m.put("total",targets.size());m.put("done",done.cardinality());m.put("rotation",turn);m.put("layer",layer);m.put("placed",origin!=null&&world==c.level);m.put("file",filename);
        if(c.player!=null)m.put("actor",c.player.getName().getString());
        if(origin!=null)m.put("origin",List.of(origin.getX(),origin.getY(),origin.getZ()));if(cornerA!=null)m.put("cornerA",List.of(cornerA.getX(),cornerA.getY(),cornerA.getZ()));if(cornerB!=null)m.put("cornerB",List.of(cornerB.getX(),cornerB.getY(),cornerB.getZ()));
        if(schematic!=null) {m.put("name",schematic.name());m.put("format",schematic.format());m.put("size",turn%2==0?List.of(schematic.width(),schematic.height(),schematic.length()):List.of(schematic.length(),schematic.height(),schematic.width()));m.put("warnings",schematic.warnings());m.put("preview",preview);m.put("previewLimited",schematic.cells().size()>preview.size());}
        var counts=new TreeMap<String,int[]>();
        if(!mine)for(int i=0;i<targets.size();i++){Target t=targets.get(i);String name=BuiltInRegistries.ITEM.getKey(t.state.getBlock().asItem()).toString();int[] row=counts.computeIfAbsent(name,k->new int[3]);row[0]++;if(!done.get(i))row[1]++;}
        if(c.player!=null)for(int i=0;i<36;i++){var stack=c.player.getInventory().getItem(i);int[] row=counts.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());if(row!=null)row[2]+=stack.getCount();}
        m.put("materials",counts.entrySet().stream().map(e->Map.of("id",e.getKey(),"total",e.getValue()[0],"needed",e.getValue()[1],"have",e.getValue()[2])).toList());view=Collections.unmodifiableMap(m);
    }
}
