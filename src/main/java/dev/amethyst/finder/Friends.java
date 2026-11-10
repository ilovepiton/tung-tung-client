package dev.amethyst.finder;

import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Local exclusions, shared by the desktop, manual assists and autonomous combat. */
public final class Friends {
    public static final List<String> RULES=List.of("aim","attack","explosions");
    private Friends() {}
    public static List<String> names(FinderConfig c) {
        var result=new LinkedHashMap<String,String>();
        for(String part:(c.pvpFriends==null?"":c.pvpFriends).split("[,;\\s]+")) {
            String name=part.trim();
            if(name.matches("[A-Za-z0-9_]{1,16}")||name.matches("[a-fA-F0-9-]{36}")) result.putIfAbsent(name.toLowerCase(Locale.ROOT),name);
            if(result.size()==100)break;
        }
        return new ArrayList<>(result.values());
    }
    public static boolean listed(FinderConfig c,Entity entity) {
        if(!(entity instanceof Player))return false;
        return names(c).stream().anyMatch(n->n.equalsIgnoreCase(entity.getName().getString())||n.equalsIgnoreCase(entity.getUUID().toString()));
    }
    public static boolean excludes(FinderConfig c,Player self,Entity entity,String rule) {
        if(self!=null&&self.isAlliedTo(entity))return true;
        for(String name:names(c))if(name.equalsIgnoreCase(entity.getName().getString())||name.equalsIgnoreCase(entity.getUUID().toString()))
            return "bot".equals(rule)||c.friendRules.getOrDefault(name.toLowerCase(Locale.ROOT),Map.of()).getOrDefault(rule,true);
        return false;
    }
    public static void sanitize(FinderConfig c) {
        c.pvpFriends=String.join(", ",names(c));
        var clean=new LinkedHashMap<String,Map<String,Boolean>>();
        for(String name:names(c)) {
            String key=name.toLowerCase(Locale.ROOT);var values=new LinkedHashMap<String,Boolean>();
            Map<String,Boolean> raw=c.friendRules==null?null:c.friendRules.get(key);
            for(String rule:RULES)values.put(rule,raw==null||raw.get(rule)==null||raw.get(rule));
            clean.put(key,values);
        }
        c.friendRules=clean;
    }
    public static void change(FinderConfig c,String action,String raw,String rule,boolean enabled) {
        String name=raw==null?"":raw.trim();
        if(!name.matches("[A-Za-z0-9_]{1,16}")&&!("friend_remove".equals(action)&&names(c).contains(name)))throw new IllegalArgumentException("Use a Minecraft username: 1–16 letters, numbers or underscores");
        var names=names(c);String key=name.toLowerCase(Locale.ROOT);
        String existing=names.stream().filter(n->n.equalsIgnoreCase(name)).findFirst().orElse(null);
        if("friend_add".equals(action)) {
            if(existing==null){if(names.size()>=100)throw new IllegalArgumentException("The friends list holds 100 names");names.add(name);}
        } else if("friend_remove".equals(action)){names.removeIf(n->n.equalsIgnoreCase(name));c.friendRules.remove(key);}
        else if("friend_rule".equals(action)) {
            if(existing==null||!RULES.contains(rule))throw new IllegalArgumentException("Choose an existing friend and protection");
            c.friendRules.computeIfAbsent(key,k->new LinkedHashMap<>()).put(rule,enabled);
        } else throw new IllegalArgumentException("Unknown friends action");
        c.pvpFriends=String.join(", ",names);sanitize(c);c.save();
    }
    public static List<Map<String,Object>> state(FinderConfig c,List<PlayerLocator.Row> players) {
        var rows=new ArrayList<Map<String,Object>>();
        for(String name:names(c)) {
            var player=players.stream().filter(p->p.name().equalsIgnoreCase(name)||p.id().equalsIgnoreCase(name)).findFirst().orElse(null);
            var row=new LinkedHashMap<String,Object>();row.put("name",name);row.put("rules",c.friendRules.getOrDefault(name.toLowerCase(Locale.ROOT),Map.of()));
            row.put("online",player!=null);row.put("observable",player!=null&&player.observable());row.put("id",player==null?"":player.id());rows.add(row);
        }
        return rows;
    }
}
