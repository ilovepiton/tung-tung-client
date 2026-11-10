package dev.amethyst.finder.modules.pvp;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.FinderConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normal initial equip, plus an authoritative swap immediately after our own totem pop. */
public final class TotemSwaps {
    private LocalPlayer player;
    private ClientLevel level;
    private ClientPacketListener connection;
    private int clock, lastAttempt = -100, lastSource = -1;
    private ItemStack previousOffhand = ItemStack.EMPTY, previousSource = ItemStack.EMPTY;
    private final TotemRefillGate refill = new TotemRefillGate();
    private final ItemStack[] reservedStacks = new ItemStack[36];
    private final ArrayDeque<Map<String,Object>> events = new ArrayDeque<>();
    private Map<String,Object> lastDeath = Map.of();
    private boolean sending, popRefill, deathSeen, initialPrediction;
    private long pops, requests, confirmations, restoredSnapshots, lastPopAt, lastRequestAt, requestHandRevision;
    private long reactionDeadline, cooldownUntil;
    private int lastRestockClock = -100;
    private String status = "Waiting for a world";

    public void reset() {
        player = null; level = null; connection = null;
        clock = 0; lastAttempt = -100; lastSource = -1;
        previousOffhand = previousSource = ItemStack.EMPTY;
        sending = popRefill = deathSeen = initialPrediction = false;
        reactionDeadline=cooldownUntil=0;lastRestockClock=-100;
        refill.reset(); java.util.Arrays.fill(reservedStacks,null);
        pops = requests = confirmations = restoredSnapshots = lastPopAt = lastRequestAt = requestHandRevision = 0;
        events.clear(); status = "Waiting for a world";
        // Keep the bounded last-death diagnostic across a respawn/session reset.
    }
    public boolean pending() { return sending || refill.waiting(); }

    public static boolean enabled(FinderConfig config) {
        return config.isModuleEnabled("auto_totem")
            || ChunkFinderClient.BOT!=null&&ChunkFinderClient.BOT.autoTotem()
            || ChunkFinderClient.MACE!=null&&ChunkFinderClient.MACE.autoTotem();
    }
    private static boolean protectedHand(ItemStack stack) { return !stack.isEmpty()&&stack.has(DataComponents.DEATH_PROTECTION); }
    private boolean reserved(int slot) {
        return ChunkFinderClient.PVP!=null&&ChunkFinderClient.PVP.reservesInventorySlot(slot);
    }
    private void bind(Minecraft client) {
        if (client.player != player || client.level != level || client.getConnection() != connection) {
            reset(); player = client.player; level = client.level; connection = client.getConnection();
        }
    }
    private String state(String value) { return status=value; }
    private static long randomizedDelay(int baseMs,double minimum,double spread) {
        if(baseMs<=0)return 0;
        double factor=minimum+java.util.concurrent.ThreadLocalRandom.current().nextDouble()*spread;
        return (long)(Math.max(0,baseMs*factor)*1_000_000L);
    }
    private void event(String kind) {
        events.addLast(Map.of("event",kind,"atNanos",System.nanoTime()));
        while(events.size()>16)events.removeFirst();
    }

    public String tick(Minecraft client, FinderConfig config) { clock++; return update(client,config,false); }
    public String received(Minecraft client, FinderConfig config) { return update(client,config,false); }
    public String inventoryReceived(Minecraft client, FinderConfig config, boolean fullInventory) {
        bind(client);
        if(player!=null&&refill.waiting()&&fullInventory) {
            int source=refill.source();
            if(refill.restored(ItemStack.matches(PvpAcknowledgements.inventorySnapshot(client,source),reservedStacks[source]),
                    protectedHand(PvpAcknowledgements.inventorySnapshot(client,40)))) {
                restoredSnapshots++; event("restored_inventory");
            }
        }
        return update(client,config,false);
    }

    /** Event 35 confirms survival before the separate inventory-empty packet arrives. */
    public String popped(Minecraft client, FinderConfig config) {
        bind(client);
        if(player==null||deathSeen)return status;
        pops++; lastPopAt=System.nanoTime(); event("self_pop");
        if(!enabled(config))return state("Disabled");
        // Vanilla consumes MAIN_HAND first. Ambiguous main-hand/stacked pops use
        // ordinary inventory receipts, so an intact offhand is never swapped out.
        if(protectedHand(player.getMainHandItem())
                ||protectedHand(player.getOffhandItem())&&player.getOffhandItem().getCount()>1)
            return update(client,config,false);
        if(refill.nextPop()){confirmations++;event("previous_refill_proved_by_pop");}
        initialPrediction=false;popRefill=true;
        return update(client,config,true);
    }

    public void died(Minecraft client) {
        bind(client);
        if(player==null||deathSeen)return;
        Map<String,Object> record=new LinkedHashMap<>(diagnostics(client));
        record.remove("lastDeath"); record.remove("events");
        record.put("health",player.getHealth());
        lastDeath=java.util.Collections.unmodifiableMap(record);
        deathSeen=true; refill.reset(); popRefill=false; event("self_death");
        state("Paused: player unavailable");
    }

    private void acknowledge(Minecraft client) {
        for(int source=0;source<36;source++)if(refill.reserved(source)) {
            ItemStack server=PvpAcknowledgements.inventorySnapshot(client,source);
            boolean changed=!ItemStack.matches(server,reservedStacks[source]);
            refill.sourceUpdate(source,PvpAcknowledgements.inventoryRevision(client,source),changed,
                    changed&&!protectedHand(server));
        }
        long revision=PvpAcknowledgements.inventoryRevision(client,40);
        if(refill.handUpdate(revision,protectedHand(PvpAcknowledgements.inventorySnapshot(client,40)))
                ||refill.settle(protectedHand(player.getOffhandItem()))) {
            confirmations++; popRefill=false; event("server_refill_confirmed");
        }
        if(initialPrediction&&revision>requestHandRevision&&protectedHand(PvpAcknowledgements.inventorySnapshot(client,40))) {
            initialPrediction=false; confirmations++; event("server_equip_confirmed");
        }
        // If the server restored an empty hand, try a different unspent source.
        // Never repeat a possibly consumed source: that could undo a refill.
        if(refill.retryElsewhere(System.nanoTime(),1_000_000_000L,
                revision>requestHandRevision&&!protectedHand(PvpAcknowledgements.inventorySnapshot(client,40)))) {
            popRefill=true; event("retry_with_another_source");
        }
    }

    private String update(Minecraft client, FinderConfig config, boolean survivedPop) {
        if(sending)return state("Totem swap in progress");
        bind(client);
        if(player!=null)acknowledge(client);
        if(player==null||level==null||connection==null||client.gameMode==null||deathSeen||player.isSpectator())
            return state("Paused: player unavailable");
        if(refill.waiting())return state("Waiting for server to refill the offhand");
        if(!enabled(config)){reactionDeadline=0;return state("Disabled");}
        // A just-received survival event can precede the health correction too.
        if(!survivedPop&&!player.isAlive())return state("Paused: player unavailable");
        long now=System.nanoTime();
        if(!popRefill&&protectedHand(player.getOffhandItem())) {
            reactionDeadline=0;
            if(now>=cooldownUntil&&restockPreferred(client,config))return status;
            return state(initialPrediction?"Totem in offhand · equip sent":"Totem in offhand");
        }
        if(now<cooldownUntil)return state("Auto Totem cooldown · "+Math.max(1,(cooldownUntil-now)/1_000_000)+" ms");
        if(reactionDeadline==0)reactionDeadline=now+randomizedDelay(config.autoTotemReactionDelayMs,.5,1.0);
        if(now<reactionDeadline)return state("Reacting · "+Math.max(1,(reactionDeadline-now)/1_000_000)+" ms");
        if(reserved(40))return state("Waiting for the active offhand swap confirmation");
        var menu=player.containerMenu;
        boolean available=false;
        for(int order=0;order<36;order++) {
            int preferred=config.autoTotemHotbarSlot-1;
            int source=order==0?preferred:(order+8)%36;
            if(order>0&&source==preferred)continue;
            ItemStack stack=player.getInventory().getItem(source);
            if(!stack.is(Items.TOTEM_OF_UNDYING))continue;
            available=true;
            if(reserved(source)||refill.reserved(source))continue;
            var slot=menu.slots.stream().filter(s->s.container==player.getInventory()&&s.getContainerSlot()==source
                &&s.mayPickup(player)&&(player.getOffhandItem().isEmpty()||s.mayPlace(player.getOffhandItem()))).findFirst().orElse(null);
            if(slot==null)continue;
            if(!popRefill&&source==lastSource&&ItemStack.matches(player.getOffhandItem(),previousOffhand)
                    &&ItemStack.matches(stack,previousSource)&&clock==lastAttempt)
                return state("Server restored the previous slots · retry next tick");
            lastSource=source;lastAttempt=clock;previousOffhand=player.getOffhandItem().copy();previousSource=stack.copy();
            requestHandRevision=PvpAcknowledgements.inventoryRevision(client,40);
            lastRequestAt=System.nanoTime();requests++;sending=true;
            reactionDeadline=0;cooldownUntil=lastRequestAt+randomizedDelay(config.autoTotemCooldownMs,.6,.8);
            try {
                if(popRefill) {
                    reservedStacks[source]=stack.copy();
                    refill.begin(source,PvpAcknowledgements.inventoryRevision(client,source),requestHandRevision,lastRequestAt);
                    // Do not predict using the consumed hand still displayed by the client.
                    // Empty changed-slot hashes request authoritative source/hand echoes.
                    connection.send(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),
                        (short)slot.index,(byte)40,ContainerInput.SWAP,new Int2ObjectOpenHashMap<>(),
                        HashedStack.create(menu.getCarried(),connection.decoratedHashOpsGenenerator())));
                    event("pop_refill_sent");
                } else {
                    initialPrediction=true;
                    client.gameMode.handleContainerInput(menu.containerId,slot.index,40,ContainerInput.SWAP,player);
                    event("initial_equip_sent");
                }
            } finally { sending=false; }
            return state(popRefill?"Refilling after totem activation":protectedHand(player.getOffhandItem())
                    ?"Totem in offhand · equip sent":"Totem swap was not applied · check the inventory");
        }
        return state(available?"Waiting for an accessible, unspent inventory totem":"No spare totem in inventory");
    }

    /** Keep the Auto Totem Pro preferred hotbar slot supplied for the next pop. */
    private boolean restockPreferred(Minecraft client,FinderConfig config) {
        int preferred=config.autoTotemHotbarSlot-1;
        if(clock==lastRestockClock||reserved(preferred))return false;
        ItemStack current=player.getInventory().getItem(preferred);
        if(!current.isEmpty())return false;
        var menu=player.containerMenu;
        for(int order=0;order<36;order++) {
            int source=(order+9)%36;
            if(source==preferred||reserved(source)||refill.reserved(source))continue;
            ItemStack stack=player.getInventory().getItem(source);
            if(!stack.is(Items.TOTEM_OF_UNDYING))continue;
            var slot=menu.slots.stream().filter(s->s.container==player.getInventory()&&s.getContainerSlot()==source
                    &&s.mayPickup(player)&&s.mayPlace(current)).findFirst().orElse(null);
            if(slot==null)continue;
            lastRestockClock=clock;sending=true;
            try { client.gameMode.handleContainerInput(menu.containerId,slot.index,preferred,ContainerInput.SWAP,player); }
            finally { sending=false; }
            state("Totem in offhand · restocking hotbar slot "+(preferred+1));
            return true;
        }
        return false;
    }

    public Map<String,Object> diagnostics(Minecraft client) {
        long now=System.nanoTime(); int spares=0;
        if(client.player!=null)for(int i=0;i<36;i++) {
            ItemStack stack=client.player.getInventory().getItem(i);
            if(stack.is(Items.TOTEM_OF_UNDYING))spares+=stack.getCount();
        }
        var result=new LinkedHashMap<String,Object>();
        result.put("status",status);result.put("spareTotems",spares);
        result.put("hotbarSlot",ChunkFinderClient.CONFIG.autoTotemHotbarSlot);
        result.put("reactionDelayMs",ChunkFinderClient.CONFIG.autoTotemReactionDelayMs);
        result.put("cooldownMs",ChunkFinderClient.CONFIG.autoTotemCooldownMs);
        result.put("offhandTotem",client.player!=null&&protectedHand(client.player.getOffhandItem()));
        result.put("pending",pending());result.put("localPrediction",initialPrediction);
        result.put("pops",pops);result.put("requests",requests);result.put("confirmations",confirmations);
        result.put("restoredSnapshots",restoredSnapshots);
        result.put("waitingMs",refill.waiting()?(now-refill.sentAt())/1_000_000:0);
        result.put("lastPopMs",lastPopAt==0?-1:(now-lastPopAt)/1_000_000);
        result.put("lastRequestMs",lastRequestAt==0?-1:(now-lastRequestAt)/1_000_000);
        result.put("lastDeath",lastDeath);
        result.put("events",events.stream().map(e->Map.of("event",e.get("event"),
            "ageMs",(now-(long)e.get("atNanos"))/1_000_000)).toList());
        return result;
    }
}
