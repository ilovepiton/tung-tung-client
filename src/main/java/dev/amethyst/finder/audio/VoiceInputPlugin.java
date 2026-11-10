package dev.amethyst.finder.audio;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import dev.amethyst.finder.*;
import java.util.*;

/** Optional Simple Voice Chat plugin. Uses its public pre-encode audio hooks. */
public final class VoiceInputPlugin implements VoicechatPlugin {
    private static volatile VoicechatClientApi api;
    private static volatile long lastFrame;
    private static volatile double inputLevel,outputLevel;
    private static volatile double sharedLevel;
    private static volatile long lastSharedFrame,queuedSounds,mixedFrames;
    private static volatile boolean microphoneTest;
    private static volatile long lastTestFrame,testFrames;
    private static volatile double testLevel,testSharedLevel;
    private static final VoicePolish polish=new VoicePolish();
    private static final VoicePolish testPolish=new VoicePolish();
    private static final ArrayDeque<Clip> clips=new ArrayDeque<>();
    private static final ArrayDeque<Clip> testClips=new ArrayDeque<>();
    private static final Map<String,short[]> samples=new HashMap<>();
    private static final class Clip {final short[] pcm;final double gain;final long at;int offset;Clip(short[] p,double g){pcm=p;gain=g;at=System.nanoTime();}}
    @Override public String getPluginId(){return "tung-input-audio";}
    @Override public void registerEvents(EventRegistration events){
        events.registerEvent(ClientVoicechatInitializationEvent.class,e->api=e.getVoicechat());
        events.registerEvent(ClientVoicechatConnectionEvent.class,e->{api=e.getVoicechat();clear();});
        events.registerEvent(MicrophoneMuteEvent.class,e->clear());
        events.registerEvent(VoicechatDisableEvent.class,e->clear());
        events.registerEvent(MergeClientSoundEvent.class,VoiceInputPlugin::merge);
        events.registerEvent(ClientSoundEvent.class,VoiceInputPlugin::speech);
    }
    private static boolean canSend(){
        var a=api;if(a==null||a.isMuted()||a.isDisabled()||a.isDisconnected())return false;
        // Voice Chat uses PTT=0 and VOICE=1. Unknown configurations fail closed.
        String mode=a.getClientConfig().getString("microphone_activation_type","PTT");
        return mode.equals("VOICE")||mode.equals("1")||a.isPushToTalkKeyPressed()||a.isWhisperKeyPressed();
    }
    public static synchronized void clear(){clips.clear();testClips.clear();polish.reset();testPolish.reset();inputLevel=outputLevel=sharedLevel=testLevel=testSharedLevel=0;}
    public static synchronized void beginMicrophoneTest(){clear();microphoneTest=true;}
    public static synchronized void endMicrophoneTest(){microphoneTest=false;clear();}
    public static boolean isMicrophoneTestActive(){return microphoneTest;}
    public static boolean hasMicrophoneTestEffects(){return microphoneTest&&testSharedLevel>0;}
    /** The official microphone test is local playback. It must never queue network audio. */
    public static synchronized short[] microphoneTestAudio(short[] microphone){
        if(!microphoneTest||microphone==null)return microphone;
        var cfg=ChunkFinderClient.CONFIG;if(cfg==null)return microphone;
        short[] effects=cfg.voiceInputSounds?mixClips(testClips,microphone.length,cfg.voiceEffectsVolume):null;
        if(!cfg.voiceInputSounds)testClips.clear();
        short[] out=microphone;
        if(effects!=null){out=microphone.clone();for(int i=0;i<out.length;i++)out[i]=(short)Math.clamp((int)out[i]+effects[i],-30146,30146);}
        if(cfg.voicePolish)out=testPolish.process(out,cfg.voiceGain);
        testLevel=rms(out);testSharedLevel=effects==null?0:rms(effects);lastTestFrame=System.currentTimeMillis();testFrames++;
        return out;
    }
    public static void openMicrophoneTest(net.minecraft.client.Minecraft client){
        var screen=new de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen(client.gui.screen());client.gui.setScreen(screen);
        for(var child:screen.children())if(child instanceof de.maxhenkel.voicechat.gui.widgets.MicTestButton button){
            if(!button.active)throw new IllegalStateException("Select a working microphone in Voice Chat Settings first");
            button.onPress(null);return;
        }
        throw new IllegalStateException("Microphone test is unavailable in this Voice Chat version");
    }
    public static void unmute(){de.maxhenkel.voicechat.voice.client.ClientManager.getPlayerStateManager().setMuted(false);}
    /** Called on a real game input edge. Loading never occurs on the voice audio thread. */
    public static synchronized void enqueue(String sample,double gain){
        var cfg=ChunkFinderClient.CONFIG;if(cfg==null||!cfg.voiceInputSounds||(!microphoneTest&&!canSend())){clips.clear();testClips.clear();return;}
        if(gain<=0||cfg.voiceEffectsVolume<=0)return;
        short[] pcm=samples.get(sample);
        if(pcm==null){
            try(var in=VoiceInputPlugin.class.getResourceAsStream("/assets/amethyst_chunk_finder/audio/"+sample+".pcm")){
                if(in==null)return;byte[] raw=in.readNBytes(48000);pcm=new short[raw.length/2];
                for(int i=0;i<pcm.length;i++)pcm[i]=(short)((raw[i*2]&255)|(raw[i*2+1]<<8));
                pcm=prepareRecording(pcm);
                if(samples.size()>=128)samples.clear();samples.put(sample,pcm);
            }catch(Exception e){return;}
        }
        if(pcm.length==0)return;
        var queue=microphoneTest?testClips:clips;if(microphoneTest)clips.clear();
        while(queue.size()>=12)queue.removeFirst();queue.addLast(new Clip(pcm,Math.clamp(gain,0,1)));queuedSounds++;
    }
    /** Normalize only the shared recording, with headroom. Local files stay exact. */
    private static short[] prepareRecording(short[] raw){
        int peak=0;for(short s:raw)peak=Math.max(peak,Math.abs((int)s));if(peak<16)return new short[0];
        int start=0,end=raw.length;while(start<end&&Math.abs((int)raw[start])<peak*.02)start++;
        start=Math.max(0,start-144);while(end>start&&Math.abs((int)raw[end-1])<peak*.02)end--;
        end=Math.min(raw.length,end+240);double frameRms=0;
        for(int at=start;at<end;at+=240){double sum=0;for(int i=at;i<Math.min(end,at+960);i++)sum+=(double)raw[i]*raw[i];frameRms=Math.max(frameRms,Math.sqrt(sum/960));}
        double gain=Math.min(8,Math.min(27852d/peak,7209d/Math.max(1,frameRms)));
        short[] out=new short[end-start];for(int i=0;i<out.length;i++)out[i]=(short)Math.round(raw[start+i]*gain);return out;
    }
    private static synchronized void merge(MergeClientSoundEvent e){
        var cfg=ChunkFinderClient.CONFIG;if(cfg==null||!cfg.voiceInputSounds||microphoneTest||!canSend()){clips.clear();return;}
        short[] audio=mixClips(clips,960,cfg.voiceEffectsVolume);
        if(audio!=null){e.mergeAudio(audio);sharedLevel=rms(audio);lastSharedFrame=System.currentTimeMillis();mixedFrames++;}
    }
    private static short[] mixClips(ArrayDeque<Clip> queue,int length,double volume){
        if(volume<=0){queue.clear();return null;}
        long now=System.nanoTime();short[] audio=new short[length];boolean any=false;
        var it=queue.iterator();
        while(it.hasNext()){
            var clip=it.next();if(now-clip.at>500_000_000L){it.remove();continue;}
            for(int i=0;i<audio.length&&clip.offset<clip.pcm.length;i++){
                int value=(int)Math.round(audio[i]+clip.pcm[clip.offset++]*clip.gain*volume);
                audio[i]=(short)Math.clamp(value,-22000,22000);any=true;
            }
            if(clip.offset>=clip.pcm.length)it.remove();
        }
        return any?audio:null;
    }
    private static synchronized void speech(ClientSoundEvent e){
        var cfg=ChunkFinderClient.CONFIG;short[] raw=e.getRawAudio();if(cfg==null||raw==null||raw.length==0)return;
        inputLevel=rms(raw);short[] out=cfg.voicePolish?polish.process(raw,cfg.voiceGain):raw;
        e.setRawAudio(out);outputLevel=rms(out);lastFrame=System.currentTimeMillis();
    }
    private static double rms(short[] a){double sum=0;for(short s:a){double f=s/32768d;sum+=f*f;}return Math.sqrt(sum/Math.max(1,a.length));}
    public static Map<String,Object> state(){
        var a=api;long now=System.currentTimeMillis();boolean recent=now-lastFrame<1500,testRecent=microphoneTest&&now-lastTestFrame<1500;
        var state=new LinkedHashMap<String,Object>();state.put("available",a!=null);state.put("connected",a!=null&&!a.isDisconnected());state.put("muted",a!=null&&a.isMuted());
        state.put("inputLevel",recent?inputLevel:0);state.put("outputLevel",testRecent?testLevel:recent?outputLevel:0);state.put("sharedLevel",testRecent?testSharedLevel:now-lastSharedFrame<1500?sharedLevel:0);
        state.put("queuedSounds",queuedSounds);state.put("mixedFrames",mixedFrames);state.put("microphoneTest",microphoneTest);state.put("testFrames",testFrames);
        state.put("status",microphoneTest?"Local microphone test · voice + ASMR · only you hear this":a==null?"Simple Voice Chat not initialized":a.isMuted()?"Microphone muted · unmute to share voice and ASMR":a.isDisabled()?"Voice Chat disabled":a.isDisconnected()?"Connect to a Voice Chat server":!canSend()?"Hold push to talk to share voice and ASMR":"Voice + ASMR ready");return state;
    }
}
