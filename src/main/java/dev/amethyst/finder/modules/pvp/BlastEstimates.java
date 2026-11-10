package dev.amethyst.finder.modules.pvp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** Vanilla armor/enchantments received by the client; server-specific changes remain unknown. */
public final class BlastEstimates {
    private BlastEstimates() {}
    public static double damage(Minecraft c,LivingEntity entity,double raw) {
        float damage=(float)Math.ceil(raw);
        damage=switch(c.level.getDifficulty()) {case PEACEFUL->0;case EASY->Math.min(damage/2+1,damage);case HARD->damage*1.5f;default->damage;};
        var source=c.level.damageSources().explosion(null,null);
        damage=CombatRules.getDamageAfterAbsorb(entity,damage,source,entity.getArmorValue(),(float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var registry=c.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var protection=registry.getOrThrow(Enchantments.PROTECTION);var blast=registry.getOrThrow(Enchantments.BLAST_PROTECTION);
        int points=0;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            var stack=entity.getItemBySlot(slot);
            points+=EnchantmentHelper.getItemEnchantmentLevel(protection,stack)+2*EnchantmentHelper.getItemEnchantmentLevel(blast,stack);
        }
        // Ignore temporary resistance for the self check: it may expire before detonation.
        return CombatRules.getDamageAfterMagicAbsorb(damage,Math.min(20,points));
    }
}
