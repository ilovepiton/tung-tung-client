package dev.amethyst.finder;

import com.google.gson.*;
import java.util.*;
import java.util.function.IntPredicate;

/** Validates all sound controls before a bridge request mutates any settings. */
public final class InputSoundSettings {
    public record Update(boolean mouse,String profile,double volume,Map<String,String> buttons,Map<String,Double> keys,Map<String,Double> mouseVolumes,boolean send,boolean polish,double effects,double gain){
        public void apply(FinderConfig c){c.mouseSoundsEnabled=mouse;c.mouseSoundProfile=profile;c.mouseSoundVolume=volume;c.mouseButtonProfiles=buttons;c.asmrKeyVolumes=keys;c.mouseButtonVolumes=mouseVolumes;c.voiceInputSounds=send;c.voicePolish=polish;c.voiceEffectsVolume=effects;c.voiceGain=gain;}
    }
    public static Update validate(JsonObject d,FinderConfig c){
        var buttons=new LinkedHashMap<>(c.mouseButtonProfiles);
        if(d.has("mouseButtonProfiles")){
            JsonObject map=object(d,"mouseButtonProfiles",5);buttons.clear();
            for(var e:map.entrySet()){
                code(e.getKey(),n->n>=0&&n<=4);
                if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isString()||!MouseSounds.PROFILES.contains(e.getValue().getAsString()))throw new IllegalArgumentException("Choose a recorded mouse click");
                buttons.put(e.getKey(),e.getValue().getAsString());
            }
        }
        String profile=d.has("mouseSoundProfile")?d.get("mouseSoundProfile").getAsString():c.mouseSoundProfile;
        if(!MouseSounds.PROFILES.contains(profile))throw new IllegalArgumentException("Choose a recorded mouse click");
        return new Update(bool(d,"mouseSoundsEnabled",c.mouseSoundsEnabled),profile,number(d,"mouseSoundVolume",c.mouseSoundVolume,0,1),Map.copyOf(buttons),
            volumes(d,"asmrKeyVolumes",c.asmrKeyVolumes,KeyboardSounds::supported,100),volumes(d,"mouseButtonVolumes",c.mouseButtonVolumes,n->n>=0&&n<=4,5),
            bool(d,"voiceInputSounds",c.voiceInputSounds),bool(d,"voicePolish",c.voicePolish),number(d,"voiceEffectsVolume",c.voiceEffectsVolume,0,1),number(d,"voiceGain",c.voiceGain,.5,3));
    }
    private static JsonObject object(JsonObject d,String key,int max){if(!d.get(key).isJsonObject()||d.getAsJsonObject(key).size()>max)throw new IllegalArgumentException("Invalid sound mapping");return d.getAsJsonObject(key);}
    private static int code(String s,IntPredicate supported){try{int n=Integer.parseInt(s);if(!Integer.toString(n).equals(s)||!supported.test(n))throw new NumberFormatException();return n;}catch(Exception e){throw new IllegalArgumentException("Invalid sound key");}}
    private static Map<String,Double> volumes(JsonObject d,String key,Map<String,Double> current,IntPredicate supported,int max){
        if(!d.has(key))return Map.copyOf(current);var out=new LinkedHashMap<String,Double>();
        for(var e:object(d,key,max).entrySet()){code(e.getKey(),supported);var v=e.getValue();if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Double.isFinite(v.getAsDouble())||v.getAsDouble()<0||v.getAsDouble()>1)throw new IllegalArgumentException("Volume must be between zero and one");out.put(e.getKey(),v.getAsDouble());}return Map.copyOf(out);
    }
    private static boolean bool(JsonObject d,String key,boolean fallback){if(!d.has(key))return fallback;var v=d.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid sound switch");return v.getAsBoolean();}
    private static double number(JsonObject d,String key,double fallback,double min,double max){if(!d.has(key))return fallback;var v=d.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Double.isFinite(v.getAsDouble())||v.getAsDouble()<min||v.getAsDouble()>max)throw new IllegalArgumentException("Invalid sound volume");return v.getAsDouble();}
    public static void publish(Map<String,Object> s,FinderConfig c){
        s.put("mouseSoundsEnabled",c.mouseSoundsEnabled);s.put("mouseSoundProfile",c.mouseSoundProfile);s.put("mouseSoundVolume",c.mouseSoundVolume);s.put("mouseButtonProfiles",c.mouseButtonProfiles);s.put("asmrKeyVolumes",c.asmrKeyVolumes);s.put("mouseButtonVolumes",c.mouseButtonVolumes);s.put("voiceInputSounds",c.voiceInputSounds);s.put("voicePolish",c.voicePolish);s.put("voiceEffectsVolume",c.voiceEffectsVolume);s.put("voiceGain",c.voiceGain);
    }
}
