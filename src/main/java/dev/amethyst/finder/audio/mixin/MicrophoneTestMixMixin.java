package dev.amethyst.finder.audio.mixin;

import dev.amethyst.finder.audio.VoiceInputPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Include the same input mix in Simple Voice Chat's local microphone monitor. */
@Pseudo
@Mixin(targets="de.maxhenkel.voicechat.gui.widgets.MicTestButton$VoiceThread",remap=false)
public abstract class MicrophoneTestMixMixin {
    @Inject(method="run",at=@At("HEAD"),remap=false)
    private void tung$begin(CallbackInfo ci){VoiceInputPlugin.beginMicrophoneTest();}
    @ModifyVariable(method="run",at=@At("STORE"),ordinal=0,remap=false)
    private short[] tung$monitor(short[] microphone){return VoiceInputPlugin.microphoneTestAudio(microphone);}
    @Redirect(method="run",at=@At(value="INVOKE",target="Lde/maxhenkel/voicechat/voice/client/MicThread;shouldTransmitAudio()Z"),remap=false)
    private boolean tung$monitorEffects(de.maxhenkel.voicechat.voice.client.MicThread mic){return mic.shouldTransmitAudio()||VoiceInputPlugin.hasMicrophoneTestEffects();}
    @Inject(method="run",at=@At("RETURN"),remap=false)
    private void tung$end(CallbackInfo ci){VoiceInputPlugin.endMicrophoneTest();}
    @Inject(method="close",at=@At("HEAD"),remap=false)
    private void tung$close(CallbackInfo ci){VoiceInputPlugin.endMicrophoneTest();}
}
