package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.modules.ClientModules;
import dev.amethyst.finder.modules.XrayModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void move(float forward, float up, float right);
    @Shadow private float getMaxZoom(float distance) { throw new AssertionError(); }

    @Inject(method = "alignWithEntity", at = @At("HEAD"), cancellable = true)
    private void tunTun$alignCamera(float partialTicks, CallbackInfo ci) {
        ClientModules modules = ChunkFinderClient.MODULES;
        if (modules == null || !modules.cameraActive()) return;
        setRotation(modules.cameraYaw(), modules.cameraPitch());
        setPosition(modules.cameraPosition(partialTicks));
        detached = true;
        if (modules.freelookActive()) move(-getMaxZoom(4), 0, 0);
        ci.cancel();
    }
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void tunTun$allowDetachedView(CameraRenderState state, DeltaTracker tracker, CallbackInfo ci) {
        if (XrayModule.active() || ChunkFinderClient.MODULES != null && ChunkFinderClient.MODULES.freecamActive()) state.smartCull = false;
    }
}
