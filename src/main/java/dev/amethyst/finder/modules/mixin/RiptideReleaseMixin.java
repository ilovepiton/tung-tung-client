package dev.amethyst.finder.modules.mixin;
import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Apply after vanilla has added the real manual Riptide impulse, once per release. */
@Mixin(Player.class)
public class RiptideReleaseMixin {
 @Inject(method="startAutoSpinAttack",at=@At("TAIL"))
 private void tuntung$manualRiptide(int duration,float damage,ItemStack item,CallbackInfo ci){
  Minecraft c=Minecraft.getInstance();if((Object)this!=c.player||ChunkFinderClient.CONFIG==null||!ChunkFinderClient.CONFIG.isModuleEnabled("super_trident")||!item.is(Items.TRIDENT))return;
  var velocity=c.player.getDeltaMovement();if(velocity.lengthSqr()>.01)c.player.setDeltaMovement(velocity.scale(3));
 }
}
