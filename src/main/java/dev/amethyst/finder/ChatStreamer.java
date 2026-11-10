package dev.amethyst.finder;
import net.minecraft.client.Minecraft;
import java.util.*;
/** An explicitly started, finite chat sequence. Never starts on login or reconnect. */
public final class ChatStreamer {
 private static boolean running;private static Object world,connection;private static long next;private static int index;private static List<String> messages=List.of();private static String lastMessage="",status="Ready",error="";
 private ChatStreamer(){}
 public static String defaults(){
  String[] patterns={"TPA to {player}.","TPA to {player}, please.","{player}, I sent you a TPA request.","Can you TPA to {player}?","Meet up with {player}.","{player}, ready when you are.","TPA request for {player}.","{player}, let's meet up.","Please check your TPA, {player}.","{player}, waiting for your teleport."};
  var rows=new ArrayList<String>();String[] lead={"", "Quick meetup: ", "Team meetup: ", "When you are ready: ", "Teleport meetup: "};for(int i=0;i<50;i++)rows.add(lead[i/10]+patterns[i%patterns.length]);return String.join("\n",rows);
 }
 public static void start(Minecraft c,String target,String templates,int interval){
  if(c.player==null||c.level==null||c.getConnection()==null)throw new IllegalArgumentException("Join a world first");
  save(target,templates,interval);
  messages=prepare(target,templates);index=0;lastMessage="";error="";status="Running";next=System.nanoTime();world=c.level;connection=c.getConnection();running=true;
 }
 private static List<String> prepare(String target,String templates){
  if(target==null||!target.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Enter a Minecraft username");
  if(templates==null||templates.length()>12000)throw new IllegalArgumentException("Messages are too long");
  var rows=templates.lines().map(String::trim).filter(s->!s.isEmpty()).toList();
  if(rows.isEmpty()||rows.size()>50||rows.stream().anyMatch(s->s.length()>220||s.startsWith("/")||s.contains("§")||s.chars().anyMatch(ch->Character.isISOControl(ch))))throw new IllegalArgumentException("Use 1–50 plain chat messages, up to 220 characters each");
  var result=rows.stream().map(s->s.replace("{player}",target)).toList();
  if(result.stream().anyMatch(s->s.length()>256))throw new IllegalArgumentException("A preview message exceeds Minecraft's chat limit");
  return result;
 }
 public static void save(String target,String templates,int interval){
  prepare(target,templates);
  if(interval<10||interval>300)throw new IllegalArgumentException("Choose 10–300 seconds between messages");
  var cfg=ChunkFinderClient.CONFIG;cfg.streamTarget=target;cfg.streamTemplates=templates;cfg.streamInterval=interval;cfg.save();
 }
 public static void stop(){running=false;status="Stopped";}
 public static Map<String,Object> state(){return Map.of("running",running,"sent",index,"total",messages.size(),"status",status,"error",error,"lastMessage",lastMessage,"nextIn",running?Math.max(0,(next-System.nanoTime()+999999999L)/1000000000L):0);}
 public static void tick(Minecraft c){
  if(!running)return;if(c.level!=world||c.getConnection()!=connection||c.player==null||!c.player.isAlive()){stop();status="Stopped: world or player changed";return;}
  long now=System.nanoTime();if(now<next)return;
  try{String text=messages.get(index);c.getConnection().sendChat(text);lastMessage=text;index++;next=now+ChunkFinderClient.CONFIG.streamInterval*1_000_000_000L;if(index>=messages.size()){stop();status="Complete";}}
  catch(RuntimeException ex){stop();error="Minecraft could not submit the message: "+ex.getClass().getSimpleName();status="Stopped after an error";}
 }
}
