package dev.crec.mapinslot.mixin;

import dev.amethyst.finder.ChunkFinderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Map in Slot 3.5.0 with a Tung Tung runtime switch. The original mod is MIT licensed. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Unique private final MapRenderState mapRenderState = new MapRenderState();

    @Shadow public abstract Matrix3x2fStack pose();
    @Shadow public abstract void map(MapRenderState state);

    @Inject(method="itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",at=@At("HEAD"))
    private void drawMap(Font font, ItemStack stack, int x, int y, String countText, CallbackInfo ci) {
        if(ChunkFinderClient.CONFIG!=null&&!ChunkFinderClient.CONFIG.mapInSlotEnabled)return;
        if(!stack.is(Items.FILLED_MAP))return;
        MapId id=stack.get(DataComponents.MAP_ID);
        if(id==null||minecraft.level==null)return;
        MapItemSavedData data=MapItem.getSavedData(id,minecraft.level);
        if(data==null)return;
        pose().pushMatrix();
        pose().translate((float)x,(float)y);
        pose().scale(0.125F,0.125F);
        minecraft.getMapRenderer().extractRenderState(id,data,mapRenderState);
        map(mapRenderState);
        pose().popMatrix();
    }
}
