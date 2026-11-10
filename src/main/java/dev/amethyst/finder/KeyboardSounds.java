package dev.amethyst.finder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import java.util.*;
/** Per-key recorded switch samples, played locally without recording typed text. */
public final class KeyboardSounds {
 private static final Set<Integer> held=new HashSet<>();
 public static final Set<String> PROFILES=Set.of("red","blue","brown","black","topre","pandas","cream","oreo","purple","black_abs","blue_abs","brown_abs","red_abs","cream_travel","turquoise","alpaca","bluealps","blackink","redink","boxnavy");
 private KeyboardSounds(){}
 public static void handle(Minecraft c,long window,int action,KeyEvent key){
  if(window!=c.getWindow().handle())return;
  if(action==0){held.remove(key.key());return;}
  if(action!=1||!held.add(key.key())||ChunkFinderClient.CONFIG==null||!ChunkFinderClient.CONFIG.asmrEnabled||!c.getWindow().isFocused()||c.gui.overlay()!=null)return;
  play(c,key.key(),profileFor(key.key()),true);
 }
 public static void clearHeld(){held.clear();}
 public static boolean supported(int code){return (code>=4&&code<=49)||(code>=51&&code<=56)||(code>=79&&code<=82)||(code>=224&&code<=230&&code!=227);}
 public static String profileFor(int code){var cfg=ChunkFinderClient.CONFIG;return cfg.asmrKeyProfiles.getOrDefault(Integer.toString(code),cfg.asmrProfile);}
 public static boolean play(Minecraft c,int code){return play(c,code,profileFor(code));}
 public static boolean play(Minecraft c,int code,String profile){return play(c,code,profile,false);}
 public static boolean play(Minecraft c,int code,String profile,boolean broadcast){
  if(!supported(code)||!PROFILES.contains(profile))return false;
  var cfg=ChunkFinderClient.CONFIG;var id=Identifier.fromNamespaceAndPath("amethyst_chunk_finder","keyboard."+profile+"."+code);
  double keyVolume=cfg.asmrKeyVolumes.getOrDefault(Integer.toString(code),1d),volume=cfg.asmrVolume*keyVolume;
  boolean monitored=broadcast&&net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat")&&dev.amethyst.finder.audio.VoiceInputPlugin.isMicrophoneTestActive();
  if(!monitored)c.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id),1,(float)volume));
  if(broadcast&&net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat"))dev.amethyst.finder.audio.VoiceInputPlugin.enqueue("keyboard/"+profile+"/"+code,keyVolume);return true;
 }
}
