package dev.amethyst.finder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Fixed useful-item selection; market prices are server-specific. No container inspection. */
public final class ValuableItems {
    public record Entry(String id,String name,String group,int color) {}
    private static Entry pvp(String id,String name) {return new Entry(id,name,"PvP",0xFFFFC875);}
    private static Entry gear(String id,String name) {return new Entry(id,name,"Gear",0xFF70E6EB);}
    private static Entry rare(String id,String name) {return new Entry(id,name,"Rare",0xFFD5A5FF);}
    public static final List<Entry> ENTRIES=List.of(
        pvp("totem_of_undying","Totem of Undying"), pvp("end_crystal","End Crystal"),
        pvp("respawn_anchor","Respawn Anchor"), pvp("glowstone","Glowstone"),
        pvp("obsidian","Obsidian"), pvp("crying_obsidian","Crying Obsidian"),
        gear("netherite_helmet","Netherite Helmet"), gear("netherite_chestplate","Netherite Chestplate"),
        gear("netherite_leggings","Netherite Leggings"), gear("netherite_boots","Netherite Boots"),
        gear("netherite_sword","Netherite Sword"), gear("netherite_axe","Netherite Axe"),
        gear("netherite_pickaxe","Netherite Pickaxe"), gear("netherite_shovel","Netherite Shovel"),
        pvp("wind_charge","Wind Charge"),
        gear("diamond_helmet","Diamond Helmet"), gear("diamond_chestplate","Diamond Chestplate"),
        gear("diamond_leggings","Diamond Leggings"), gear("diamond_boots","Diamond Boots"),
        gear("diamond_sword","Diamond Sword"), gear("diamond_axe","Diamond Axe"),
        gear("diamond_pickaxe","Diamond Pickaxe"), gear("diamond_shovel","Diamond Shovel"),
        rare("netherite_upgrade_smithing_template","Netherite Upgrade"),
        gear("elytra","Elytra"), gear("mace","Mace"), gear("trident","Trident"),
        pvp("enchanted_golden_apple","Enchanted Golden Apple"), pvp("golden_apple","Golden Apple"),
        pvp("ender_pearl","Ender Pearl"), pvp("experience_bottle","Experience Bottle"),
        pvp("firework_rocket","Firework Rocket"), rare("enchanted_book","Enchanted Book"),
        rare("netherite_ingot","Netherite Ingot"), rare("netherite_scrap","Netherite Scrap"),
        rare("ancient_debris","Ancient Debris"), rare("diamond","Diamond"),
        rare("diamond_block","Diamond Block"), rare("emerald","Emerald"), rare("emerald_block","Emerald Block"),
        rare("netherite_block","Netherite Block"), rare("gold_block","Gold Block"),
        rare("beacon","Beacon"), rare("nether_star","Nether Star"), rare("shulker_shell","Shulker Shell"),
        rare("shulker_box","Shulker Box (all colors)"), rare("heavy_core","Heavy Core"),
        rare("breeze_rod","Breeze Rod"), rare("dragon_egg","Dragon Egg"), gear("shield","Shield")
    );
    private static final Map<String,Entry> BY_ID=new LinkedHashMap<>();
    static {for(var entry:ENTRIES) {if(BY_ID.put(entry.id,entry)!=null)throw new IllegalStateException("Duplicate item");}if(ENTRIES.size()!=50)throw new IllegalStateException("Expected 50 item types");}
    private ValuableItems() {}
    public static Entry find(String id) {return BY_ID.get(id);}
    public static Map<String,Boolean> sanitize(Map<String,Boolean> raw) {
        var result=new LinkedHashMap<String,Boolean>();
        for(var entry:ENTRIES) result.put(entry.id,raw==null||!Boolean.FALSE.equals(raw.get(entry.id)));
        return result;
    }
    public static Entry match(ItemStack stack) {
        if(stack==null||stack.isEmpty())return null;
        var key=BuiltInRegistries.ITEM.getKey(stack.getItem());
        if(!key.getNamespace().equals("minecraft"))return null;
        String id=key.getPath();
        return BY_ID.get(id.endsWith("_shulker_box")?"shulker_box":id);
    }
}
