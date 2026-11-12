package dev.amethyst.finder.modules;

import dev.amethyst.finder.FinderConfig;
import dev.amethyst.finder.modules.pvp.PvpAcknowledgements;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Command → validated deposit menu → one acknowledged stack at a time → green Confirm/Sell button. */
public final class FarmAutomation {
    private enum Phase { IDLE, OPENING, DEPOSIT, ACKNOWLEDGE, CONFIRM }
    private final FinderConfig config;
    private Phase phase=Phase.IDLE;
    private Object ownedMenu;
    private long deadline,nextAttempt;
    private int sourceSlot,beforeCount,deposited;
    private long sourceRevision;
    private ItemStack pending=ItemStack.EMPTY;
    private String lastAttemptFingerprint;
    private String sellStatus="Waiting for a full selected stack",logoutStatus="Watching received players";
    public FarmAutomation(FinderConfig config) { this.config=config; }
    public void reset() {
        phase=Phase.IDLE;ownedMenu=null;pending=ItemStack.EMPTY;nextAttempt=0;lastAttemptFingerprint=null;
        sellStatus="Waiting for a full selected stack";logoutStatus="Watching received players";
    }
    public String status(String id) { return "auto_sell".equals(id)?sellStatus:logoutStatus; }
    public boolean tick(Minecraft client) {
        if(config.isModuleEnabled("auto_logout")&&client.getConnection()!=null&&client.getCurrentServer()!=null) {
            for(var other:client.level.players()) {
                if(other==client.player||!other.isAlive()||other.isSpectator())continue;
                if(other.distanceToSqr(client.player)>config.autoLogoutRange*config.autoLogoutRange)continue;
                config.setModuleEnabled("auto_logout",false);config.setModuleEnabled("auto_sell",false);config.save();
                logoutStatus="Disconnected: "+other.getName().getString()+" approached";
                // The server sends only received players; hidden/unloaded players cannot be detected.
                client.getConnection().getConnection().disconnect(Component.literal("Tung Tung: player within "+config.autoLogoutRange+" blocks: "+other.getName().getString()));
                reset();return true;
            }
            logoutStatus="Watching received players within "+config.autoLogoutRange+" blocks (friends included)";
        }
        if(!config.isModuleEnabled("auto_sell")) { if(phase!=Phase.IDLE)reset(); return false; }
        long now=System.nanoTime();
        if(client.getCurrentServer()==null||client.getConnection()==null||client.gameMode==null) { sellStatus="Join a multiplayer server with /sell";return false; }
        if(phase==Phase.IDLE) {
            if(now<nextAttempt)return false;
            if(client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.inventoryMenu.getCarried().isEmpty()) {
                sellStatus="Waiting: close inventory/chat first";return false;
            }
            StringBuilder fingerprint=new StringBuilder();
            for(int i=0;i<36;i++) {
                ItemStack stack=client.player.getInventory().getItem(i);
                if(eligible(stack))fingerprint.append(i).append(':').append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append(';');
            }
            if(fingerprint.isEmpty()) { lastAttemptFingerprint=null;sellStatus="Waiting for a full selected stack";return false; }
            if(fingerprint.toString().equals(lastAttemptFingerprint)) { sellStatus="Waiting for inventory changes; unchanged stacks are not resubmitted";return false; }
            lastAttemptFingerprint=fingerprint.toString();
            client.getConnection().sendCommand(config.autoSellCommand);
            phase=Phase.OPENING;deadline=now+8_000_000_000L;deposited=0;sellStatus="Opening /"+config.autoSellCommand;return false;
        }
        if(phase==Phase.CONFIRM&&client.player.containerMenu==client.player.inventoryMenu&&client.gui.screen()==null) {
            phase=Phase.IDLE;ownedMenu=null;pending=ItemStack.EMPTY;
            nextAttempt=now+config.autoSellIntervalSeconds*1_000_000_000L;
            sellStatus="Confirm/Sell sent · "+deposited+" stacks · server closed the menu";return false;
        }
        if(now>deadline) { stop("Stopped: server did not acknowledge the sell step within 8 seconds; no retry sent");return false; }
        if(phase==Phase.OPENING) {
            if(client.gui.screen()==null)return false;
            if(!(client.player.containerMenu instanceof ChestMenu menu)||!titleMatches(client.gui.screen().getTitle().getString(),config.autoSellMenuTitle)) {
                stop("Stopped: unexpected menu. Set the exact /sell menu title in Auto Sell settings.");return false;
            }
            // A deposit menu must accept items and contain room. Never click a shop/buy/confirmation icon.
            if(confirmSlot(menu)<0) { sellStatus="Waiting for named green Confirm/Sell glass button";return false; }
            ownedMenu=menu;phase=Phase.DEPOSIT;
        }
        if(client.player.containerMenu!=ownedMenu||client.gui.screen()==null) { stop("Stopped: sell menu changed or was closed");return false; }
        ChestMenu menu=(ChestMenu)ownedMenu;
        if(phase==Phase.CONFIRM)return false;
        if(!menu.getCarried().isEmpty()) { stop("Stopped: item on cursor; no further clicks sent");return false; }
        if(phase==Phase.ACKNOWLEDGE) {
            if(PvpAcknowledgements.inventoryRevision(client,sourceSlot)<=sourceRevision)return false;
            ItemStack current=PvpAcknowledgements.inventorySnapshot(client,sourceSlot);
            int remaining=ItemStack.isSameItemSameComponents(current,pending)?current.getCount():0;
            var receipt=FarmItems.receipt(sourceRevision,PvpAcknowledgements.inventoryRevision(client,sourceSlot),beforeCount,remaining);
            if(receipt==FarmItems.Receipt.WAIT)return false;
            if(receipt==FarmItems.Receipt.PARTIAL) { stop("Stopped: server accepted only part of the stack");return false; }
            deposited++;phase=Phase.DEPOSIT;deadline=now+8_000_000_000L;
            return false;
        }
        for(var slot:menu.slots) {
            if(slot.container!=client.player.getInventory()||slot.getContainerSlot()<0||slot.getContainerSlot()>=36)continue;
            ItemStack stack=slot.getItem();if(!eligible(stack))continue;
            boolean space=false;
            for(var target:menu.slots)if(target.container==menu.getContainer()&&target.getItem().isEmpty()&&target.mayPlace(stack)) { space=true;break; }
            if(!space) { finish(client,now,"Sell menu full");return false; }
            sourceSlot=slot.getContainerSlot();sourceRevision=PvpAcknowledgements.inventoryRevision(client,sourceSlot);pending=stack.copy();beforeCount=stack.getCount();phase=Phase.ACKNOWLEDGE;deadline=now+8_000_000_000L;
            sendClick(client,menu,slot.index,ContainerInput.QUICK_MOVE);
            sellStatus="Depositing selected full stacks · "+deposited+" submitted";return false;
        }
        finish(client,now,"Selected full stacks submitted");return false;
    }
    private void finish(Minecraft client,long now,String message) {
        if(deposited==0) { stop("Stopped: no full stack was accepted; menu left open");return; }
        ChestMenu menu=(ChestMenu)ownedMenu;
        int confirm=confirmSlot(menu);
        if(confirm<0) { stop("Stopped: named green Confirm/Sell button disappeared");return; }
        phase=Phase.CONFIRM;deadline=now+8_000_000_000L;
        sendClick(client,menu,confirm,ContainerInput.PICKUP);
        sellStatus="Confirm/Sell sent · "+deposited+" stacks · waiting for server to close menu";
    }
    private int confirmSlot(ChestMenu menu) {
        for(var slot:menu.slots) {
            if(slot.container!=menu.getContainer())continue;
            ItemStack stack=slot.getItem();
            String itemId=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            boolean green=java.util.Set.of("lime_stained_glass_pane","green_stained_glass_pane","lime_stained_glass","green_stained_glass").contains(itemId);
            if(green&&stack.has(DataComponents.CUSTOM_NAME)&&confirmLabel(stack.getHoverName().getString()))return slot.index;
        }
        return -1;
    }
    public static boolean confirmLabel(String label) {
        return label!=null&&label.toLowerCase(java.util.Locale.ROOT).matches(".*\\b(confirm|sell)\\b.*");
    }
    private void sendClick(Minecraft client,ChestMenu menu,int slot,ContainerInput action) {
        // Keep the local inventory unchanged. Empty prediction hashes request the server's real slot echoes.
        var connection=client.getConnection();
        connection.send(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),(short)slot,(byte)0,action,
                new Int2ObjectOpenHashMap<>(),HashedStack.create(menu.getCarried(),connection.decoratedHashOpsGenenerator())));
    }

    private void stop(String reason) {
        phase=Phase.IDLE;ownedMenu=null;pending=ItemStack.EMPTY;config.setModuleEnabled("auto_sell",false);config.save();sellStatus=reason;
    }
    private boolean eligible(ItemStack stack) {
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return FarmItems.fullStack(id,stack.getCount(),stack.getMaxStackSize(),stack.has(DataComponents.CUSTOM_NAME)||stack.isEnchanted(),config.autoSellItems);
    }
    public static boolean titleMatches(String actual,String expected) {
        return actual!=null&&(expected==null||expected.isBlank()||actual.strip().equalsIgnoreCase(expected.strip()));
    }
}
