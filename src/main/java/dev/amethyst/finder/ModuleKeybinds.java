package dev.amethyst.finder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLKeycode;
import java.util.*;

public final class ModuleKeybinds {
    private static final Set<Integer> held=new HashSet<>();
    private static final Map<Integer,String> labels=new HashMap<>();
    private ModuleKeybinds() {}
    public static boolean valid(int code){return code==0||(code>=4&&code<=221&&code!=40&&code!=41&&code!=42&&code!=76);}
    public static int key(String id){return ChunkFinderClient.CONFIG.moduleKeybinds.getOrDefault(id,0);}
    public static String label(int code){return code==0?"Not set":labels.computeIfAbsent(code,k->InputConstants.Type.KEYBOARD.getOrCreate(k).getDisplayName().getString());}
    public static String conflict(Minecraft c,int code) {
        if(code==0||c.options==null||c.options.keyMappings==null)return "";
        var input=InputConstants.Type.KEYBOARD.getOrCreate(code);
        for(var mapping:c.options.keyMappings) {
            if(!mapping.matches(input))continue;
            // P may replace Social Interactions; F3 combinations retain their debug behavior.
            if(mapping==c.options.keySocialInteractions)continue;
            if(mapping!=c.options.keyDebugModifier&&mapping!=c.options.keyDebugOverlay&&Arrays.asList(c.options.debugKeys).contains(mapping))continue;
            return Component.translatable(mapping.getName()).getString();
        }
        return "";
    }
    public static void assign(Minecraft c,String id,int code) {
        if(ModuleCatalog.find(id)==null||!ModuleCatalog.available(id))throw new IllegalArgumentException("Module is not available");
        if(!valid(code))throw new IllegalArgumentException("Choose a letter, number or function key");
        if(code!=0) {
            for(var entry:ChunkFinderClient.CONFIG.moduleKeybinds.entrySet())if(!entry.getKey().equals(id)&&entry.getValue()==code)
                throw new IllegalArgumentException(label(code)+" is already assigned to "+ModuleCatalog.find(entry.getKey()).name());
            String clash=conflict(c,code);if(!clash.isEmpty())throw new IllegalArgumentException(label(code)+" is used by Minecraft: "+clash+". Choose another key.");
        }
        if(code==0)ChunkFinderClient.CONFIG.moduleKeybinds.remove(id);else ChunkFinderClient.CONFIG.moduleKeybinds.put(id,code);
        held.clear();ChunkFinderClient.CONFIG.save();
    }
    public static void clearHeld(){held.clear();KeyboardSounds.clearHeld();MouseSounds.clearHeld();}
    public static boolean handle(Minecraft c,long window,int action,KeyEvent event) {
        if(window==0||window!=c.getWindow().handle())return false;
        int code=event.key();
        if(action==0){held.remove(code);return false;}
        if(action!=1||!held.add(code))return false;
        if(c.player==null||c.level==null||!c.getWindow().isFocused()||!c.mouseHandler.isMouseGrabbed()||c.gui.screen()!=null||c.gui.overlay()!=null)return false;
        if((event.modifiers()&(SDLKeycode.SDL_KMOD_CTRL|SDLKeycode.SDL_KMOD_ALT|SDLKeycode.SDL_KMOD_GUI))!=0)return false;
        if(c.options.keyDebugModifier.isDown()||InputConstants.isKeyDown(InputConstants.getKey(c.options.keyDebugModifier.saveString()).getValue()))return false;
        if(!conflict(c,code).isEmpty())return false;
        for(var entry:ChunkFinderClient.CONFIG.moduleKeybinds.entrySet())if(entry.getValue()==code&&ModuleCatalog.available(entry.getKey())) {
            String id=entry.getKey();boolean enabled=!ChunkFinderClient.CONFIG.isModuleEnabled(id);
            ModuleControls.setEnabled(c,id,enabled);
            c.getFramerateLimitTracker().onInputReceived();
            c.gui.hud.setOverlayMessage(Component.literal(ModuleCatalog.find(id).name()+" · "+(enabled?"ON":"OFF")),true);
            if(ChunkFinderClient.BRIDGE!=null)ChunkFinderClient.BRIDGE.publish(c);
            return true;
        }
        return false;
    }
}
