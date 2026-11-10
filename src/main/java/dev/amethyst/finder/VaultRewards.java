package dev.amethyst.finder;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Catalog extracted from this release's official vanilla loot tables. */
public final class VaultRewards {
    public record Reward(String id,String type,String item,String enchantment,String name,String group,String icon,String potion) {
        public boolean matches(ItemStack stack) {
            if(stack.isEmpty()||!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item))return false;
            if(!potion.isEmpty()&&!stack.getOrDefault(DataComponents.POTION_CONTENTS,net.minecraft.world.item.alchemy.PotionContents.EMPTY).potion().map(h->h.unwrapKey().map(k->k.identifier().toString().equals(potion)).orElse(false)).orElse(false))return false;
            if(enchantment.isEmpty())return true;
            return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).keySet().stream()
                    .anyMatch(e->e.unwrapKey().map(k->k.identifier().toString().equals(enchantment)).orElse(false));
        }
    }
    public static final List<Reward> ALL=load();
    private static List<Reward> load(){
        try(var in=VaultRewards.class.getResourceAsStream("/tung-vault-rewards.json")){
            return List.of(new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),Reward[].class));
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    public static Reward find(String type,String id){return ALL.stream().filter(r->r.type.equals(type)&&r.id.equals(id)).findFirst().orElse(null);}
    public static boolean validType(String type){return Set.of("normal","ominous").contains(type);}
    private VaultRewards(){}
}
