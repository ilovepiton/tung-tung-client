package dev.amethyst.finder.modules.pvp;

import dev.amethyst.finder.ChunkFinderClient;
import dev.amethyst.finder.Friends;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import java.util.LinkedHashMap;

/** Stops outgoing crystal/anchor interactions before prediction and packet creation. */
public final class LootProtection {
    private static Object world;
    private static final LinkedHashMap<Integer,Entity> defeated = new LinkedHashMap<>();
    private LootProtection() {}

    private static void session(Minecraft client) {
        if (world != client.level) { world=client.level; defeated.clear(); }
    }
    public static void observedDeath(Minecraft client,Entity entity) {
        session(client);
        if (!(entity instanceof Player) || entity==client.player) return;
        defeated.put(entity.getId(),entity);
        if(defeated.size()>128) defeated.remove(defeated.keySet().iterator().next());
    }
    public static boolean opponent(Minecraft client,Player player) {
        var config=ChunkFinderClient.CONFIG;
        return player!=client.player && player.isAlive() && player.getHealth()>0 && !player.isRemoved()
            && !player.isSpectator() && defeated.get(player.getId())!=player
            && !Friends.listed(config,player) && !client.player.isAlliedTo(player)
            && player.distanceToSqr(client.player)<=(double)config.pvpLootProtectionRange*config.pvpLootProtectionRange;
    }
    public static boolean blocked(Minecraft client) {
        if(client==null)return false;
        session(client);
        if(client.level==null||client.player==null||ChunkFinderClient.CONFIG==null)return false;
        if(!ChunkFinderClient.CONFIG.pvpLootProtection) return false;
        for(var player:client.level.players()) if(opponent(client,player)) return false;
        return true;
    }
    public static String status(Minecraft client) {
        if(client!=null)session(client);
        if(!ChunkFinderClient.CONFIG.pvpLootProtection) return "Loot protection off";
        return blocked(client) ? "Loot protected · no opponent within "+ChunkFinderClient.CONFIG.pvpLootProtectionRange+"m"
            : "Opponent nearby · crystal/anchor interactions allowed";
    }
    public static boolean blocksAttack(Minecraft client,Entity entity) {
        return entity instanceof EndCrystal && blocked(client);
    }
    public static boolean blocksUse(Minecraft client,InteractionHand hand,BlockHitResult hit) {
        if(!blocked(client)) return false;
        var held=client.player.getItemInHand(hand);
        if(held.is(Items.END_CRYSTAL)) return true;
        var state=client.level.getBlockState(hit.getBlockPos());
        if(!state.is(Blocks.RESPAWN_ANCHOR)
            || client.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.RESPAWN_ANCHOR_WORKS,hit.getBlockPos())) return false;
        // Vanilla skips block activation when sneaking with either hand occupied.
        // This permits ordinary placement against an existing anchor as well.
        if(client.player.isSecondaryUseActive()
            && (!client.player.getMainHandItem().isEmpty()||!client.player.getOffhandItem().isEmpty())) return false;
        int charge=state.getValue(RespawnAnchorBlock.CHARGE);
        if(charge==0) return false;
        // Glowstone charges a non-full anchor. The main-hand interaction also
        // passes to offhand glowstone in this case; neither action can explode.
        if(charge<4 && (held.is(Items.GLOWSTONE)
            || hand==InteractionHand.MAIN_HAND&&client.player.getOffhandItem().is(Items.GLOWSTONE))) return false;
        return true;
    }
}
