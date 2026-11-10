package dev.amethyst.finder.modules.pvp;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.*;

/** Read actual item components, including every supplied enchantment and its level. */
public final class MaceLoadout {
 public enum Kind {MACE,AXE,SWORD,SHIELD,WIND,PEARL,TOTEM,OTHER}
 public record Weapon(int slot,ItemStack stack,Kind kind,Map<String,Integer> enchants,double damage,double speed,int durability){
  public int level(String name){return enchants.getOrDefault(name,0);}
  public boolean usable(int reserve){return !stack.isEmpty()&&(!stack.isDamageableItem()||durability>reserve);}
  public boolean validMace(){return java.util.stream.Stream.of("density","breach","smite","bane_of_arthropods").filter(e->level(e)>0).count()<=1;}
 }
 public static Kind kind(ItemStack s){if(s.is(Items.MACE))return Kind.MACE;if(s.is(ItemTags.AXES))return Kind.AXE;if(s.is(ItemTags.SWORDS))return Kind.SWORD;if(s.is(Items.SHIELD))return Kind.SHIELD;if(s.is(Items.WIND_CHARGE))return Kind.WIND;if(s.is(Items.ENDER_PEARL))return Kind.PEARL;if(s.is(Items.TOTEM_OF_UNDYING))return Kind.TOTEM;return Kind.OTHER;}
 public static Map<String,Integer> enchantments(ItemStack s){var result=new TreeMap<String,Integer>();var e=s.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);for(var h:e.keySet())h.unwrapKey().ifPresent(k->result.put(k.identifier().getPath(),e.getLevel(h)));return Collections.unmodifiableMap(result);}
 public static Weapon read(int slot,ItemStack stack){var a=stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);return new Weapon(slot,stack,kind(stack),enchantments(stack),a.compute(Attributes.ATTACK_DAMAGE,1,EquipmentSlot.MAINHAND),a.compute(Attributes.ATTACK_SPEED,4,EquipmentSlot.MAINHAND),stack.isDamageableItem()?stack.getMaxDamage()-stack.getDamageValue():-1);}
 public static List<Weapon> scan(Player p){var result=new ArrayList<Weapon>();if(p!=null){for(int i=0;i<36;i++)if(!p.getInventory().getItem(i).isEmpty())result.add(read(i,p.getInventory().getItem(i)));if(!p.getOffhandItem().isEmpty())result.add(read(40,p.getOffhandItem()));}return result;}
 public static Weapon best(List<Weapon> kit,Kind kind,int reserve,double fall,int armor,boolean multiple,boolean makeSpace,boolean burning){
  Weapon best=null;double score=-1;
  for(var w:kit){if(w.kind!=kind||!w.usable(reserve)||kind==Kind.MACE&&!w.validMace())continue;
   double damage=w.damage+(w.level("sharpness")>0?.5*w.level("sharpness")+.5:0);
   double value=kind==Kind.MACE?MacePhysics.reduced(MacePhysics.smashDamage(damage,fall,w.level("density")),armor,8,w.level("breach"),0):damage*Math.max(.1,w.speed);
   // Efficiency, Mending and Unbreaking grant no invented PvP damage bonus.
   if(kind==Kind.SWORD){value+=!burning?w.level("fire_aspect")*.8:0;value+=makeSpace?w.level("knockback")*2:0;value+=multiple?w.level("sweeping_edge")*.5:0;}
   value+=w.durability<0?0:Math.min(1,w.durability/100d)*.05;
   if(value>score){score=value;best=w;}
  }return best;
 }
 public static List<Map<String,Object>> state(Player p){return scan(p).stream().filter(w->w.kind!=Kind.OTHER).map(w->{var m=new LinkedHashMap<String,Object>();m.put("slot",w.slot);m.put("kind",w.kind.toString().toLowerCase(Locale.ROOT));m.put("item",BuiltInRegistries.ITEM.getKey(w.stack.getItem()).toString());m.put("name",w.stack.getHoverName().getString());m.put("enchants",w.enchants);m.put("durability",w.durability);m.put("count",w.stack.getCount());return (Map<String,Object>)m;}).toList();}
 private MaceLoadout(){}
}
