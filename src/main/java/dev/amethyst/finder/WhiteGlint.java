package dev.amethyst.finder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Uses crelay's unchanged resource pack; only the additional gear coverage is local. */
public final class WhiteGlint {
    public static final String ID="crelay-white-glint";
    public static final String FILE="White-Enchantment-Glint-crelay-1.5.zip";
    public static final String PACK="file/"+FILE;
    private static volatile boolean active;
    private WhiteGlint() {}

    public static List<String> onTop(Collection<String> current) {
        var next=new ArrayList<>(current);boolean enabled=next.removeIf(PACK::equals);
        if(enabled)next.add(PACK);
        return List.copyOf(next);
    }
    public static void tick(Minecraft client) {
        if(!client.isGameLoadFinished())return;
        boolean selected=client.getResourcePackRepository().getSelectedIds().contains(PACK);
        if(selected&&!active){
            client.options.glintStrength().set(1.0);
            client.options.glintSpeed().set(0.5);
            client.options.save();
        }
        active=selected;
    }
    public static boolean supported(ItemStack stack) {
        if(stack.isEmpty())return false;
        var equip=stack.get(DataComponents.EQUIPPABLE);
        return stack.has(DataComponents.WEAPON)||stack.has(DataComponents.TOOL)
            ||stack.has(DataComponents.PIERCING_WEAPON)||stack.has(DataComponents.KINETIC_WEAPON)
            ||equip!=null&&equip.slot().isArmor()
            ||stack.is(Items.BOW)||stack.is(Items.CROSSBOW)||stack.is(Items.TRIDENT)
            ||stack.is(Items.MACE)||stack.is(Items.SHIELD)||stack.is(Items.TOTEM_OF_UNDYING);
    }
    public static boolean cosmeticFoil(ItemStack stack) {
        // Never modify stack components, enchantments or integrated-server behavior.
        var config=ChunkFinderClient.CONFIG;
        return active&&config!=null&&config.whiteGlintAllGear
            &&Minecraft.getInstance().isSameThread()&&supported(stack);
    }
}
