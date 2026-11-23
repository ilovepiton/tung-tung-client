package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.integration.BetterMaceModules;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.ritesh.feature.Combat",remap=false)
public abstract class BetterMaceAxeMixin {
 @Inject(method="findItem",at=@At("HEAD"),cancellable=true,remap=false)
 private static void tung$realAxe(Player player,String name,CallbackInfoReturnable<Integer> cir){
  if("axe".equals(name))cir.setReturnValue(BetterMaceModules.usableAxe(player));
 }
}
