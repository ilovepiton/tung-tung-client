package dev.amethyst.finder.modules;

import dev.amethyst.finder.FinderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** Intentionally reconnects only the saved target from its own cancelled-able countdown screen. */
public final class AutoRelog {
    private final FinderConfig config;
    private final RelogPolicy policy=new RelogPolicy();
    private ServerData target;
    private WaitScreen countdown;
    private long due;
    private String status="Disabled";
    private boolean connecting;
    public AutoRelog(FinderConfig config) { this.config=config; }
    public String status() { return status; }
    public void setEnabled(boolean enabled) {
        cancel();config.setModuleEnabled("auto_relog",enabled);
        if(enabled) { policy.arm();status="Armed · one relog after 1 second at Y ≤ "+config.autoRelogY; }
        config.save();
    }
    public void cancel() { policy.cancel();target=null;countdown=null;connecting=false;status="Cancelled / off"; }
    public void tick(Minecraft client) {
        if(target!=null) {
            if(connecting) {
                if(client.level!=null) { cancel();status="Relog complete · disabled";return; }
                if(client.gui.screen() instanceof DisconnectedScreen||client.gui.screen() instanceof TitleScreen||System.nanoTime()>due+45_000_000_000L) {
                    cancel();status="Connection ended · no automatic retry";
                }
                return;
            }
            if(client.gui.screen()!=countdown) { cancel();return; }
            if(System.nanoTime()<due)return;
            connecting=true;status="Connecting once to "+target.name;
            try { ConnectScreen.startConnecting(new TitleScreen(),client,ServerAddress.parseString(target.ip),target,false,null); }
            catch(RuntimeException error) { cancel();status="Reconnect failed · no retry";client.gui.setScreen(new TitleScreen()); }
            return;
        }
        if(!config.isModuleEnabled("auto_relog"))return;
        boolean eligible=client.level!=null&&client.player!=null&&client.player.isAlive()
                &&client.getCurrentServer()!=null&&client.getConnection()!=null&&client.gui.screen()==null;
        // Nearby Player Logout owns departures when its proximity condition is met.
        if(eligible&&config.isModuleEnabled("auto_logout"))for(var other:client.level.players())
            if(other!=client.player&&other.isAlive()&&!other.isSpectator()
                    &&other.distanceToSqr(client.player)<=config.autoLogoutRange*(double)config.autoLogoutRange) { eligible=false;break; }
        if(client.level==null) { config.setModuleEnabled("auto_relog",false);config.save();cancel();return; }
        if(!policy.observe(client.player==null?Double.NaN:client.player.getY(),config.autoRelogY,eligible))return;
        target=client.getCurrentServer();
        config.setModuleEnabled("auto_relog",false);config.save();
        due=System.nanoTime()+config.autoRelogDelaySeconds*1_000_000_000L;
        countdown=new WaitScreen();status="Relog countdown · one connection attempt";
        client.disconnect(countdown,false);
    }
    private final class WaitScreen extends Screen {
        WaitScreen() { super(Component.literal("Tung Tung · Auto Relog")); }
        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.literal("Cancel reconnect"),button->onClose())
                    .bounds(width/2-100,height/2+30,200,20).build());
        }
        @Override public void onClose() { cancel();Minecraft.getInstance().gui.setScreen(new TitleScreen()); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
            extractBackground(graphics,mouseX,mouseY,delta);
            var font=Minecraft.getInstance().font;
            String title="Auto Relog · "+Math.max(0,(due-System.nanoTime()+999_999_999L)/1_000_000_000L)+"s";
            graphics.text(font,title,(width-font.width(title))/2,height/2-20,0xFFFFB768,false);
            String hint="One reconnect. No camera or movement actions.";
            graphics.text(font,hint,(width-font.width(hint))/2,height/2,0xFFF0E6D8,false);
            super.extractRenderState(graphics,mouseX,mouseY,delta);
        }
    }
}
