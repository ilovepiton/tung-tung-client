package qa;

import com.google.gson.*;
import dev.amethyst.finder.*;
import dev.amethyst.finder.builder.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec2;
import java.nio.file.*;
import java.util.*;

/** Real client/server actions in a generated, offline world. Does not use player accounts or saves. */
public final class BuilderQa implements ClientModInitializer {
    private final Path out=Path.of(System.getProperty("tung.qa.output"));
    private final List<Map<String,Object>> checks=new ArrayList<>();
    private int ticks,since,stage;private volatile boolean fixture;private volatile String error="";private boolean finished;
    private static final class Driven extends ClientInput {
        @Override public void tick(){keyPresses=ChunkFinderClient.AUTO_BUILDER.movement(Minecraft.getInstance(),Input.EMPTY);moveVector=new Vec2((keyPresses.left()?1:0)-(keyPresses.right()?1:0),(keyPresses.forward()?1:0)-(keyPresses.backward()?1:0)).normalized();}
    }
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void cmd(Minecraft c,String json)throws Exception{ChunkFinderClient.AUTO_BUILDER.command(c,JsonParser.parseString(json).getAsJsonObject());}
    private void tick(Minecraft c){
        if(finished)return;ticks++;
        try{
            if(!error.isEmpty())throw new AssertionError(error);
            if(ticks-since>700)throw new AssertionError("Timeout at "+stage+": "+ChunkFinderClient.AUTO_BUILDER.state());
            if(c.player!=null){c.player.input=new Driven();c.gui.setScreen(null);for(var key:c.options.keyMappings){key.setDown(false);while(key.consumeClick()){}}}
            var b=ChunkFinderClient.AUTO_BUILDER;var s=b.state();
            switch(stage){
                case 0->{if(!c.isGameLoadFinished())return;ChunkFinderClient.CONFIG.modules.replaceAll((k,v)->false);ChunkFinderClient.CONFIG.enabled=false;c.options.pauseOnLostFocus=false;c.getTutorial().setStep(TutorialSteps.NONE);
                    c.createWorldOpenFlows().createFreshLevel("BuilderQA",new LevelSettings("BuilderQA",GameType.SURVIVAL,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(237,false,false),lookup->lookup.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),c.gui.screen());next();}
                case 1->{if(c.player==null||c.getSingleplayerServer()==null)return;c.getSingleplayerServer().execute(()->{try{var server=c.getSingleplayerServer();var w=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=63;y<=68;y++)w.setBlock(new BlockPos(x,y,z),(y==63?Blocks.STONE:Blocks.AIR).defaultBlockState(),3,512);player.getInventory().setItem(0,new ItemStack(Items.STONE,32));player.getInventory().setItem(1,new ItemStack(Items.DIAMOND_PICKAXE));player.teleportTo(w,.5,64,-3.5,Set.of(),0,0,true);}catch(Throwable ex){error=ex.toString();}fixture=true;});next();}
                case 2->{if(!fixture||ticks-since<90||c.player.getY()<63)return;check("Real survival fixture received",c.level.getBlockState(new BlockPos(0,63,0)).is(Blocks.STONE));cmd(c,"{\"op\":\"import\",\"file\":\"00000000000000000000000000000000.schem\",\"label\":\"QA wall\"}");next();}
                case 3->{if(!s.containsKey("name")||Boolean.TRUE.equals(s.get("loading")))return;check("Async import creates four target blocks",((Number)s.get("total")).intValue()==4);check("Import never arms automatic actions",!b.active());check("Material count reads actual inventory",s.get("materials").toString().contains("have=32"));cmd(c,"{\"op\":\"place\",\"x\":0,\"y\":64,\"z\":0,\"rotation\":0,\"manual\":true}");check("Manual guide does not build",!b.active());try{cmd(c,"{\"op\":\"start\"}");throw new AssertionError("Manual guide started");}catch(IllegalStateException expected){check("Manual mode rejects automatic start",true);}cmd(c,"{\"op\":\"place\",\"x\":0,\"y\":64,\"z\":0,\"rotation\":0,\"manual\":false}");check("In-world hologram marks missing blocks",b.marks(c).size()==4);cmd(c,"{\"op\":\"start\"}");next();}
                case 4->{if(b.active())return;check("Survival auto-build reaches actual completion",b.state().get("status").toString().startsWith("Build complete"));check("All four blocks are present in received terrain",c.level.getBlockState(new BlockPos(0,64,0)).is(Blocks.STONE)&&c.level.getBlockState(new BlockPos(1,65,0)).is(Blocks.STONE));check("Server accepted material consumption",c.player.getInventory().getItem(0).getCount()==28);check("Completed blocks disappear from hologram",b.marks(c).isEmpty());cmd(c,"{\"op\":\"corner\",\"corner\":\"a\",\"x\":0,\"y\":64,\"z\":0}");cmd(c,"{\"op\":\"corner\",\"corner\":\"b\",\"x\":1,\"y\":65,\"z\":0}");cmd(c,"{\"op\":\"area\"}");check("Mine preview has exactly four cells",((Number)b.state().get("total")).intValue()==4);check("Preview alone preserves built wall",c.level.getBlockState(new BlockPos(0,64,0)).is(Blocks.STONE));check("Mine bounds are a single overlay",b.marks(c).size()==1);cmd(c,"{\"op\":\"start\"}");next();}
                case 5->{if(b.active())return;check("Survival mining reaches actual completion",b.state().get("status").equals("Area cleared"));check("Selected wall removed",c.level.getBlockState(new BlockPos(0,64,0)).isAir()&&c.level.getBlockState(new BlockPos(1,65,0)).isAir());check("Floor outside selection is untouched",c.level.getBlockState(new BlockPos(0,63,0)).is(Blocks.STONE));cmd(c,"{\"op\":\"place\",\"x\":8,\"y\":64,\"z\":0,\"rotation\":1,\"manual\":false}");check("Quarter turn swaps X/Z bounds",b.state().get("size").equals(List.of(1,2,2)));cmd(c,"{\"op\":\"start\"}");next();}
                case 6->{if(b.active())return;check("Builder walks to a distant build without teleporting",c.player.getX()>3);check("Distant build completes through normal placement",b.state().get("status").toString().startsWith("Build complete"));fixture=false;c.getSingleplayerServer().execute(()->{var server=c.getSingleplayerServer();var player=server.getPlayerList().getPlayers().getFirst();player.getInventory().setItem(0,ItemStack.EMPTY);server.overworld().setBlock(new BlockPos(4,64,-2),Blocks.CHEST.defaultBlockState(),3,512);fixture=true;});next();}
                case 7->{if(!fixture||ticks-since<30)return;cmd(c,"{\"op\":\"place\",\"x\":5,\"y\":64,\"z\":0,\"rotation\":0,\"manual\":false}");cmd(c,"{\"op\":\"start\"}");next();}
                case 8->{if(b.active())return;check("Missing materials pause instead of fabricating items",b.state().get("status").toString().startsWith("Missing material"));cmd(c,"{\"op\":\"corner\",\"corner\":\"a\",\"x\":4,\"y\":64,\"z\":-2}");cmd(c,"{\"op\":\"corner\",\"corner\":\"b\",\"x\":4,\"y\":64,\"z\":-2}");cmd(c,"{\"op\":\"area\"}");cmd(c,"{\"op\":\"start\"}");next();}
                case 9->{if(b.active())return;check("Containers pause mining",b.state().get("status").toString().startsWith("Container"));check("Chest remains intact",c.level.getBlockState(new BlockPos(4,64,-2)).is(Blocks.CHEST));cmd(c,"{\"op\":\"pause\"}");check("Explicit pause releases builder control",!b.active());finished=true;next();c.stop();}
            }
        }catch(Throwable failure){failure.printStackTrace();error=failure.toString();finished=true;report();c.stop();}
    }
    private void next(){stage++;since=ticks;report();}
    private void check(String label,boolean passed){checks.add(Map.of("check",label,"passed",passed));report();if(!passed)throw new AssertionError(label+" · "+ChunkFinderClient.AUTO_BUILDER.state().get("status"));}
    private void report(){try{Files.writeString(out.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("stage",stage,"error",error,"checks",checks,"userProfileTouched",false)));}catch(Exception ex){throw new RuntimeException(ex);}}
}
