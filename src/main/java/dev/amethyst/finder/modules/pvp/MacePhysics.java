package dev.amethyst.finder.modules.pvp;

/** Bounded estimates for planning. No predicted value grants movement or a hit. */
public final class MacePhysics {
 public static double smashDamage(double base,double fall,int density){double f=Math.max(0,fall);return base+4*Math.min(3,f)+2*Math.min(5,Math.max(0,f-3))+Math.max(0,f-8)+f*.5*Math.clamp(density,0,5);}
 public static double reduced(double damage,int armor,double toughness,int breach,int protection){double armorTerm=Math.clamp(armor-damage/(2+Math.max(0,toughness)/4),armor*.2,20)*(1-.15*Math.clamp(breach,0,4));return damage*(1-armorTerm/25)*(1-Math.min(20,Math.max(0,protection))*.04);}
 public static int descentTicks(double height,double vertical){double h=height,v=vertical;for(int t=1;t<=60;t++){h+=v;v=(v-.08)*.98;if(h<=0)return t;}return 60;}
 public static double fallDamage(double fall,int protection,int feather){return Math.max(0,Math.ceil(fall-3))*(1-Math.min(20,Math.max(0,protection)+3*Math.max(0,feather))*.04);}
 public static boolean launchAllowed(boolean grounded,boolean headroom,boolean healthy,boolean targetVisible,boolean landingSafe,boolean alreadyLaunching,int charges,int reserve,int elapsed,int cooldown){return grounded&&headroom&&healthy&&targetVisible&&landingSafe&&!alreadyLaunching&&charges>reserve&&elapsed>=cooldown;}
 public static boolean shieldAllowed(boolean lethal,boolean incomingMace,boolean enemyAxe,boolean cooldown,boolean defensiveWindow){return !lethal&&!incomingMace&&!enemyAxe&&!cooldown&&defensiveWindow;}
 public static boolean confirmedBurst(int enchantLevel,boolean descendingSmash,boolean hurtObserved,boolean upwardMotion){return enchantLevel>0&&descendingSmash&&hurtObserved&&upwardMotion;}
 private MacePhysics(){}
}
