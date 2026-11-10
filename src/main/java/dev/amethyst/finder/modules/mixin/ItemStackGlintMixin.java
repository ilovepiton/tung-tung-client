package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.WhiteGlint;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackGlintMixin {
    @Inject(method="hasFoil",at=@At("RETURN"),cancellable=true)
    private void tung$whiteGearGlint(CallbackInfoReturnable<Boolean> result) {
        if(!result.getReturnValueZ()&&WhiteGlint.cosmeticFoil((ItemStack)(Object)this))result.setReturnValue(true);
    }
}
