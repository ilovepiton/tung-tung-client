package dev.amethyst.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import java.util.*;

/** One recorded sound per physical press; held buttons never repeat. */
public final class MouseSounds {
    private static final Set<Integer> held=new HashSet<>();
    public static final Set<String> PROFILES=Set.of("click01","click02","click03","click04","click05","click06","click07");
    public static int logicalButton(int sdl){return switch(sdl){case 1->0;case 3->1;case 2->2;case 4->3;case 5->4;default->-1;};}
    public static void handle(Minecraft c,int nativeButton,int action){
        int button=logicalButton(nativeButton);if(button<0)return;
        if(action==0){held.remove(button);return;}
        var cfg=ChunkFinderClient.CONFIG;
        if(action!=1||!held.add(button)||button<0||button>4||cfg==null||!cfg.mouseSoundsEnabled||!c.getWindow().isFocused()||c.gui.overlay()!=null)return;
        play(c,button,profileFor(button),true);
    }
    public static void clearHeld(){held.clear();}
    public static String profileFor(int button){var cfg=ChunkFinderClient.CONFIG;return cfg.mouseButtonProfiles.getOrDefault(Integer.toString(button),cfg.mouseSoundProfile);}
    public static boolean play(Minecraft c,int button,String profile,boolean broadcast){
        if(button<0||button>4||!PROFILES.contains(profile))return false;
        var cfg=ChunkFinderClient.CONFIG;double buttonVolume=cfg.mouseButtonVolumes.getOrDefault(Integer.toString(button),1d),volume=cfg.mouseSoundVolume*buttonVolume;
        var id=Identifier.fromNamespaceAndPath("amethyst_chunk_finder","mouse."+profile);
        boolean monitored=broadcast&&net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat")&&dev.amethyst.finder.audio.VoiceInputPlugin.isMicrophoneTestActive();
        if(!monitored)c.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id),1,(float)volume));
        if(broadcast&&net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat"))dev.amethyst.finder.audio.VoiceInputPlugin.enqueue("mouse/"+profile,buttonVolume);return true;
    }
}
