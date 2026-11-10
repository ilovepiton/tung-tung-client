package dev.amethyst.finder.modules.mixin;

import dev.amethyst.finder.modules.pvp.LootProtection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class LootProtectionMixin {
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void tung$protectDrops(Player player,Entity target,CallbackInfo callback) {
        if(LootProtection.blocksAttack(Minecraft.getInstance(),target)) callback.cancel();
    }
}
