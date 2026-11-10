package dev.amethyst.finder.modules.pvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;

/** Exact-signature adapters for the verified, unobfuscated Minecraft 26.3 API. */
public final class PvpAccess {
    private final Map<String, Class<?>> classes = new HashMap<>();
    private final Map<String, Method> methods = new HashMap<>();
    private final Map<String, Object> constants = new HashMap<>();

    public Class<?> type(String name) throws ReflectiveOperationException {
        Class<?> cached = classes.get(name);
        if (cached == null) { cached = Class.forName(name); classes.put(name, cached); }
        return cached;
    }
    private Method method(Class<?> owner, String name, Class<?>... parameters) throws ReflectiveOperationException {
        String key = owner.getName() + '#' + name + java.util.Arrays.toString(parameters);
        Method cached = methods.get(key);
        if (cached == null) { cached = owner.getMethod(name, parameters); methods.put(key, cached); }
        return cached;
    }
    private Object call(Class<?> owner, Object receiver, String name, Class<?>[] parameters, Object... args) throws ReflectiveOperationException {
        try { return method(owner, name, parameters).invoke(receiver, args); }
        catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            throw new ReflectiveOperationException(owner.getSimpleName() + '.' + name + ": " + (cause == null ? ex.getMessage() : cause.getClass().getSimpleName() + ": " + cause.getMessage()), cause);
        }
    }
    public Object constant(String owner, String name) throws ReflectiveOperationException {
        String key = owner + '.' + name;
        if (!constants.containsKey(key)) constants.put(key, type(owner).getField(name).get(null));
        return constants.get(key);
    }
    public boolean item(ItemStack stack, String name) throws ReflectiveOperationException {
        return !stack.isEmpty() && stack.getItem() == constant("net.minecraft.world.item.Items", name);
    }
    public boolean block(Object state, String name) throws ReflectiveOperationException {
        Object block = call(state.getClass(), state, "getBlock", new Class<?>[0]);
        return block == constant("net.minecraft.world.level.block.Blocks", name);
    }
    public boolean air(Object state) throws ReflectiveOperationException {
        return booleanValue(call(state.getClass(), state, "isAir", new Class<?>[0]), "BlockState.isAir");
    }
    public int charges(Object state) throws ReflectiveOperationException {
        Object charge = constant("net.minecraft.world.level.block.RespawnAnchorBlock", "CHARGE");
        Object value = call(type("net.minecraft.world.level.block.state.StateHolder"), state, "getValue",
                new Class<?>[]{type("net.minecraft.world.level.block.state.properties.Property")}, charge);
        if (!(value instanceof Integer count) || count < 0 || count > 4) throw new ReflectiveOperationException("Invalid respawn-anchor charge state");
        return count;
    }
    public boolean anchorWorks(Minecraft client, BlockPos pos) throws ReflectiveOperationException {
        Object reader = call(type("net.minecraft.world.level.Level"), client.level, "environmentAttributes", new Class<?>[0]);
        Object attribute = constant("net.minecraft.world.attribute.EnvironmentAttributes", "RESPAWN_ANCHOR_WORKS");
        Object value = call(type("net.minecraft.world.attribute.EnvironmentAttributeReader"), reader, "getValue",
                new Class<?>[]{type("net.minecraft.world.attribute.EnvironmentAttribute"), BlockPos.class}, attribute, pos);
        return booleanValue(value, "RESPAWN_ANCHOR_WORKS");
    }
    public boolean crystal(Entity entity) throws ReflectiveOperationException {
        return type("net.minecraft.world.entity.boss.enderdragon.EndCrystal").isInstance(entity);
    }
    public Entity crosshairEntity(Minecraft client) throws ReflectiveOperationException {
        Object hit = Minecraft.class.getField("hitResult").get(client);
        Class<?> entityHit = type("net.minecraft.world.phys.EntityHitResult");
        if (!entityHit.isInstance(hit)) return null;
        Object result = call(entityHit, hit, "getEntity", new Class<?>[0]);
        if (!(result instanceof Entity entity)) throw new ReflectiveOperationException("Unexpected entity hit result");
        return entity;
    }
    private Object clip(Minecraft client, Entity viewer, Vec3 start, Vec3 end, String shape) throws ReflectiveOperationException {
        Class<?> context = type("net.minecraft.world.level.ClipContext");
        Class<?> block = type("net.minecraft.world.level.ClipContext$Block");
        Class<?> fluid = type("net.minecraft.world.level.ClipContext$Fluid");
        Object request = context.getConstructor(Vec3.class, Vec3.class, block, fluid, Entity.class).newInstance(
                start, end, block.getField(shape).get(null), fluid.getField("NONE").get(null), viewer);
        return call(type("net.minecraft.world.level.BlockGetter"), client.level, "clip", new Class<?>[]{context}, request);
    }
    private String hitType(Object hit) throws ReflectiveOperationException {
        Object value = call(type("net.minecraft.world.phys.HitResult"), hit, "getType", new Class<?>[0]);
        return String.valueOf(value);
    }
    private boolean hits(Object hit, BlockPos pos) throws ReflectiveOperationException {
        if (!"BLOCK".equals(hitType(hit))) return false;
        Object value = call(type("net.minecraft.world.phys.BlockHitResult"), hit, "getBlockPos", new Class<?>[0]);
        return pos.equals(value);
    }
    /** Returns an actual vanilla block ray hit; never fabricates a click through another block. */
    public Object rayToBlock(Minecraft client, BlockPos block, Vec3 endpoint, boolean requireTop) throws ReflectiveOperationException {
        Object hit = clip(client, client.player, client.player.getEyePosition(), endpoint, "OUTLINE");
        if (!hits(hit, block)) return null;
        if (requireTop) {
            Object face = call(type("net.minecraft.world.phys.BlockHitResult"), hit, "getDirection", new Class<?>[0]);
            if (face != constant("net.minecraft.core.Direction", "UP")) return null;
        }
        return hit;
    }
    public boolean click(Minecraft client, Object hit) throws ReflectiveOperationException {
        return click(client, hit, InteractionHand.MAIN_HAND);
    }
    public boolean click(Minecraft client, Object hit, InteractionHand hand) throws ReflectiveOperationException {
        Object result = call(type("net.minecraft.client.multiplayer.MultiPlayerGameMode"), client.gameMode, "useItemOn",
                new Class<?>[]{LocalPlayer.class, InteractionHand.class, type("net.minecraft.world.phys.BlockHitResult")}, client.player, hand, hit);
        return consumes(result);
    }
    public boolean useItem(Minecraft client) throws ReflectiveOperationException {
        Object result = call(type("net.minecraft.client.multiplayer.MultiPlayerGameMode"), client.gameMode, "useItem",
                new Class<?>[]{Player.class, InteractionHand.class}, client.player, InteractionHand.MAIN_HAND);
        return consumes(result);
    }
    private boolean consumes(Object result) throws ReflectiveOperationException {
        if (result == null) throw new ReflectiveOperationException("Missing interaction result");
        return booleanValue(call(type("net.minecraft.world.InteractionResult"), result, "consumesAction", new Class<?>[0]), "InteractionResult.consumesAction");
    }
    private static boolean booleanValue(Object value, String source) throws ReflectiveOperationException {
        if (!(value instanceof Boolean result)) throw new ReflectiveOperationException("Invalid boolean from " + source);
        return result;
    }
    /** Vanilla exposure, with the source anchor omitted just as vanilla removes it before exploding. */
    public double exposure(Minecraft client, Vec3 center, Entity entity, BlockPos removedAnchor) throws ReflectiveOperationException {
        Method vanilla = method(type("net.minecraft.world.level.ServerExplosion"), "getSeenPercent", Vec3.class, Entity.class);
        if (removedAnchor == null) {
            Object result;
            try { result = vanilla.invoke(null, center, entity); }
            catch (InvocationTargetException ex) { throw new ReflectiveOperationException("Vanilla blast visibility failed", ex.getCause()); }
            if (!(result instanceof Number number)) throw new ReflectiveOperationException("Invalid vanilla blast exposure");
            double exposure = number.doubleValue();
            if (!Double.isFinite(exposure) || exposure < 0 || exposure > 1) throw new ReflectiveOperationException("Blast exposure outside 0..1");
            return exposure;
        }
        var box = entity.getBoundingBox();
        double sx = 1 / ((box.maxX - box.minX) * 2 + 1);
        double sy = 1 / ((box.maxY - box.minY) * 2 + 1);
        double sz = 1 / ((box.maxZ - box.minZ) * 2 + 1);
        if (sx <= 0 || sy <= 0 || sz <= 0) throw new ReflectiveOperationException("Invalid entity bounds for blast exposure");
        double ox = (1 - Math.floor(1 / sx) * sx) / 2, oz = (1 - Math.floor(1 / sz) * sz) / 2;
        int visible = 0, count = 0;
        for (double fx = 0; fx <= 1; fx += sx) for (double fy = 0; fy <= 1; fy += sy) for (double fz = 0; fz <= 1; fz += sz) {
            Vec3 sample = new Vec3(box.minX + (box.maxX - box.minX) * fx + ox,
                    box.minY + (box.maxY - box.minY) * fy, box.minZ + (box.maxZ - box.minZ) * fz + oz);
            Object hit = clip(client, entity, sample, center, "COLLIDER");
            if ("MISS".equals(hitType(hit)) || hits(hit, removedAnchor)) visible++;
            count++;
            if (count > 1024) throw new ReflectiveOperationException("Entity too large for bounded blast sampling");
        }
        return count == 0 ? 0 : (double) visible / count;
    }
}
