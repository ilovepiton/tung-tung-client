package dev.amethyst.finder.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts the game's actual textured model faces, including animated block entities and equipment.
 * The collector never submits shadows, text or bounding boxes to the normal world renderer.
 * Reflective signatures below are checked against the official Minecraft 26.3 class declarations.
 */
public final class ModelGeometryCapture {
    private static final String PREFIX = "net.minecraft.client.renderer.";
    private static final Map<String, Class<?>> TYPES = new ConcurrentHashMap<>();
    private static final ClassValue<Map<CallKey, Method>> METHODS = new ClassValue<>() {
        @Override protected Map<CallKey, Method> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Field> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private ModelGeometryCapture() {}

    /** Four world-space vertices (12 doubles), four normalized texture coordinates (8 floats). */
    public record Face(double[] xyz, float[] uv, Identifier texture) {}
    public record Capture(List<Face> faces, int unsupported, boolean truncated, String issue) {}

    /** Received fluid surfaces, not a substitute block-model capture. */
    public static Capture lava(net.minecraft.client.Minecraft client, net.minecraft.core.BlockPos pos) {
        var state=client.level.getBlockState(pos);var fluid=state.getFluidState();
        if(!state.is(net.minecraft.world.level.block.Blocks.LAVA))return new Capture(List.of(),0,false,"");
        double x=pos.getX(),y=pos.getY(),z=pos.getZ(),height=fluid.getHeight(client.level,pos);
        List<Face> faces=new ArrayList<>();float[] uv={0,0,1,0,1,1,0,1};
        var texture=Identifier.fromNamespaceAndPath("minecraft","textures/block/lava_still.png");
        if(!client.level.getBlockState(pos.above()).is(net.minecraft.world.level.block.Blocks.LAVA))
            faces.add(new Face(new double[]{x,y+height,z,x+1,y+height,z,x+1,y+height,z+1,x,y+height,z+1},uv,texture));
        return new Capture(List.copyOf(faces),0,false,"");
    }

    public static Capture block(Object client, Object position, Object blockState, float tickDelta, int quadBudget) {
        Collector out = new Collector(quadBudget);
        try {
            out.offsetX = number(call(position, "getX"));
            out.offsetY = number(call(position, "getY"));
            out.offsetZ = number(call(position, "getZ"));
            Object pose = type("com.mojang.blaze3d.vertex.PoseStack").getConstructor().newInstance();
            if ("MODEL".equals(String.valueOf(call(blockState, "getRenderShape")))) {
                Object manager = call(client, "getModelManager");
                Object model = call(call(manager, "getBlockStateModelSet"), "get", blockState);
                long seed = ((Number)call(blockState, "getSeed", position)).longValue();
                Object random = call(type("net.minecraft.util.RandomSource"), "create", seed);
                List<Object> parts = new ArrayList<>();
                call(model, "collectParts", random, parts);
                Object offset = call(blockState, "getOffset", position);
                call(pose, "translate", number(field(offset, "x")), number(field(offset, "y")), number(field(offset, "z")));
                out.parts(parts, pose);
                // A block's baked offset must not move its block-entity renderer too.
                pose = type("com.mojang.blaze3d.vertex.PoseStack").getConstructor().newInstance();
            }
            Object level = field(client, "level");
            Object blockEntity = call(level, "getBlockEntity", position);
            if (blockEntity != null) {
                Object renderer = call(call(client, "getBlockEntityRenderDispatcher"), "getRenderer", blockEntity);
                if (renderer != null) {
                    Object camera = cameraState(client);
                    Object state = call(renderer, "createRenderState");
                    call(renderer, "extractRenderState", blockEntity, state, tickDelta, field(camera, "pos"), null);
                    call(renderer, "submit", state, pose, out.queue(), camera);
                }
            }
            if (out.faces.isEmpty() && !out.truncated && out.unsupported == 0) out.unsupported("No renderable model faces");
        } catch (Throwable error) { out.failure(error); }
        return out.result();
    }

    public static Capture entity(Object client, Object entity, float tickDelta, int quadBudget) {
        Collector out = new Collector(quadBudget);
        try {
            Object renderer = call(call(client, "getEntityRenderDispatcher"), "getRenderer", entity);
            Object state = call(renderer, "createRenderState", entity, tickDelta);
            out.offsetX = number(field(state, "x"));
            out.offsetY = number(field(state, "y"));
            out.offsetZ = number(field(state, "z"));
            Object offset = call(renderer, "getRenderOffset", state);
            out.offsetX += number(field(offset, "x"));
            out.offsetY += number(field(offset, "y"));
            out.offsetZ += number(field(offset, "z"));
            Object pose = type("com.mojang.blaze3d.vertex.PoseStack").getConstructor().newInstance();
            call(renderer, "submit", state, pose, out.queue(), cameraState(client));
            if (out.faces.isEmpty() && !out.truncated && out.unsupported == 0) out.unsupported("No renderable entity faces");
        } catch (Throwable error) { out.failure(error); }
        return out.result();
    }

    private static Object cameraState(Object client) throws ReflectiveOperationException {
        return field(field(call(field(client, "gameRenderer"), "gameRenderState"), "levelRenderState"), "cameraRenderState");
    }

    private static final class Collector implements InvocationHandler {
        private final int budget;
        private final List<Face> faces = new ArrayList<>();
        private int unsupported;
        private boolean truncated;
        private String issue = "";
        private double offsetX, offsetY, offsetZ;
        private Object queue;

        Collector(int budget) { this.budget = Math.max(1, Math.min(8192, budget)); }
        Capture result() { return new Capture(List.copyOf(faces), unsupported, truncated, issue); }
        Object queue() throws ReflectiveOperationException {
            if (queue == null) queue = Proxy.newProxyInstance(ModelGeometryCapture.class.getClassLoader(),
                    new Class<?>[]{type(PREFIX + "SubmitNodeCollector")}, this);
            return queue;
        }
        @Override public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object[] a = arguments == null ? new Object[0] : arguments;
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, name, a);
            if (name.equals("order")) return proxy;
            // Execute the real 26.3 default overloads, including sprite mapping and model-part wrappers.
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, a);
            switch (name) {
                case "submitModel" -> model(a);
                case "submitBlockModel" -> parts(castList(a[2]), a[0]);
                case "submitItem" -> quads(castList(call(a[6], "all")), a[0]);
                case "submitCustomGeometry" -> custom(a);
                case "submitMovingBlock" -> unsupported("Moving-block model submission");
                // Decorative effects do not belong to a selected block or entity silhouette.
                case "submitShadow", "submitNameTag", "submitText", "submitTextBackground", "submitFlame",
                        "submitLeash", "submitCrumblingOverlay", "submitBreakingBlockModel", "submitShapeOutline",
                        "submitQuadParticleGroup", "submitGizmoPrimitives" -> { }
                default -> unsupported("Unsupported model submission: " + name);
            }
            return null;
        }
        private void model(Object[] a) throws Throwable {
            if (Boolean.TRUE.equals(call(a[3], "isOutline"))) return;
            Identifier texture = texture(a[3], a[7]);
            if (texture == null) { unsupported("Model texture is unavailable"); return; }
            VertexSink sink = new VertexSink(texture);
            Object consumer = sink;
            if (a[7] != null) consumer = call(a[7], "wrap", consumer);
            call(a[0], "setupAnim", a[1]);
            try {
                call(a[0], "renderToBuffer", a[2], consumer, a[4], a[5], a[6]);
            } finally { sink.finish(); }
        }
        private void custom(Object[] a) throws Throwable {
            if (Boolean.TRUE.equals(call(a[1], "isOutline"))) return;
            Identifier texture = texture(a[1], null);
            if (texture == null) { unsupported("Custom geometry has no texture"); return; }
            VertexSink sink = new VertexSink(texture);
            try { call(a[2], "render", call(a[0], "last"), sink); }
            finally { sink.finish(); }
        }
        private void parts(List<?> parts, Object pose) throws ReflectiveOperationException {
            int visited = 0;
            for (Object part : parts) {
                if (++visited > 256) { truncated = true; throw Limit.INSTANCE; }
                BlockStateModelPart modelPart = (BlockStateModelPart)part;
                for (Direction direction : Direction.values()) quads(modelPart.getQuads(direction), pose);
                quads(modelPart.getQuads(null), pose);
            }
        }
        private void quads(List<?> quads, Object pose) throws ReflectiveOperationException {
            Matrix4f matrix = (Matrix4f)call(call(pose, "last"), "pose");
            Vector3f transformed = new Vector3f();
            for (Object value : quads) {
                BakedQuad quad = (BakedQuad)value;
                Identifier atlas = quad.materialInfo().sprite().atlasLocation();
                double[] xyz = new double[12];
                float[] uv = new float[8];
                boolean finite = true;
                for (int i = 0; i < 4; i++) {
                    Vector3fc vertex = quad.position(i);
                    matrix.transformPosition(vertex, transformed);
                    xyz[i*3] = offsetX + transformed.x;
                    xyz[i*3+1] = offsetY + transformed.y;
                    xyz[i*3+2] = offsetZ + transformed.z;
                    long packed = quad.packedUV(i);
                    // Official UVPair packing uses U in the high 32 bits and V in the low 32 bits.
                    uv[i*2] = Float.intBitsToFloat((int)(packed >> 32));
                    uv[i*2+1] = Float.intBitsToFloat((int)packed);
                    finite &= Double.isFinite(xyz[i*3]) && Double.isFinite(xyz[i*3+1]) && Double.isFinite(xyz[i*3+2])
                            && Float.isFinite(uv[i*2]) && Float.isFinite(uv[i*2+1]);
                }
                if (finite && atlas != null) add(new Face(xyz, uv, atlas));
                else unsupported("Invalid baked model face");
            }
        }
        private void add(Face face) {
            if (faces.size() >= budget) { truncated = true; throw Limit.INSTANCE; }
            faces.add(face);
        }
        private void unsupported(String description) {
            unsupported++;
            if (issue.isEmpty()) issue = description.length() <= 160 ? description : description.substring(0, 157) + "...";
        }
        private void failure(Throwable error) {
            // Proxies and nested display-entity dispatch can wrap a deliberate budget stop again.
            Throwable cause = error;
            for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause())
                if (cause instanceof Limit) { truncated = true; return; }
            while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
            if (error instanceof Limit) { truncated = true; return; }
            if (error instanceof VirtualMachineError) throw (Error)error;
            unsupported(error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage()));
        }
        /** A direct consumer keeps vertex emission allocation-free between completed faces. */
        private final class VertexSink implements VertexConsumer {
            private final Identifier texture;
            private final double[] xyz = new double[12];
            private final float[] uv = new float[8];
            private int vertices;
            private int current = -1;
            private boolean finite = true;
            private boolean hasUv;
            VertexSink(Identifier texture) { this.texture = texture; }
            @Override public VertexConsumer addVertex(float x, float y, float z) {
                if (vertices == 4) flush();
                current = vertices++;
                xyz[current*3] = offsetX + x;
                xyz[current*3+1] = offsetY + y;
                xyz[current*3+2] = offsetZ + z;
                finite &= Double.isFinite(xyz[current*3]) && Double.isFinite(xyz[current*3+1]) && Double.isFinite(xyz[current*3+2]);
                return this;
            }
            @Override public VertexConsumer setUv(float u, float v) {
                if (current >= 0) {
                    uv[current*2] = u; uv[current*2+1] = v; hasUv = true;
                    finite &= Float.isFinite(u) && Float.isFinite(v);
                }
                return this;
            }
            @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            @Override public VertexConsumer setColor(int color) { return this; }
            @Override public VertexConsumer setUv1(int u, int v) { return this; }
            @Override public VertexConsumer setUv2(int u, int v) { return this; }
            @Override public VertexConsumer setUv3(float u, float v) { return this; }
            @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
            @Override public VertexConsumer setLineWidth(float width) { return this; }
            private void flush() {
                if (vertices == 4 && finite && hasUv) add(new Face(xyz.clone(), uv.clone(), texture));
                else if (vertices != 0) unsupported("Incomplete textured model face");
                vertices = 0; current = -1; finite = true; hasUv = false;
                Arrays.fill(uv, 0);
            }
            void finish() { flush(); }
        }
    }

    /** Reads only the primary texture binding; light maps and overlay textures must not replace it. */
    private static Identifier texture(Object renderType, Object mapping) throws ReflectiveOperationException {
        if (mapping != null && type(PREFIX + "texture.TextureAtlasSprite").isInstance(mapping))
            return (Identifier)call(mapping, "atlasLocation");
        Map<?, ?> bindings = (Map<?, ?>)field(field(renderType, "state"), "textures");
        Object binding = bindings.get("Sampler0");
        if (binding == null) binding = bindings.get("Texture");
        return binding == null ? null : (Identifier)call(binding, "location");
    }
    private static Object objectMethod(Object proxy, String name, Object[] arguments) {
        return switch (name) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            case "toString" -> "Tung Tung model geometry collector";
            default -> null;
        };
    }
    @SuppressWarnings("unchecked") private static List<?> castList(Object list) { return (List<Object>)list; }
    private static double number(Object value) { return ((Number)value).doubleValue(); }
    private static Class<?> type(String name) throws ClassNotFoundException {
        Class<?> result = TYPES.get(name);
        if (result == null) { result = Class.forName(name, false, ModelGeometryCapture.class.getClassLoader()); TYPES.put(name, result); }
        return result;
    }
    private record CallKey(String name, List<Class<?>> arguments) {}
    private static final class NullArgument {}
    private static Object call(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        Class<?> owner = target instanceof Class<?> c ? c : target.getClass();
        CallKey key = new CallKey(name, Arrays.stream(arguments).map(a -> a == null ? NullArgument.class : a.getClass()).toList());
        Map<CallKey, Method> cache = METHODS.get(owner);
        Method method = cache.get(key);
        if (method == null) {
            int best = -1;
            for (Method candidate : owner.getMethods()) {
                if (!candidate.getName().equals(name) || candidate.getParameterCount() != arguments.length
                        || (target instanceof Class<?> && !Modifier.isStatic(candidate.getModifiers()))) continue;
                Class<?>[] parameters = candidate.getParameterTypes();
                int score = 0;
                boolean compatible = true;
                for (int i = 0; i < parameters.length; i++) {
                    Class<?> expected = boxed(parameters[i]);
                    if (arguments[i] == null) { if (parameters[i].isPrimitive()) compatible = false; }
                    else if (!expected.isInstance(arguments[i])) compatible = false;
                    else score += expected == arguments[i].getClass() ? 2 : 1;
                }
                if (compatible && score > best) { method = candidate; best = score; }
            }
            if (method == null) throw new NoSuchMethodException(owner.getName() + "." + name + "/" + arguments.length);
            method.trySetAccessible();
            cache.put(key, method);
        }
        return method.invoke(target instanceof Class<?> ? null : target, arguments);
    }
    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Map<String, Field> cache = FIELDS.get(target.getClass());
        Field field = cache.get(name);
        if (field == null) {
            for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
                try { field = owner.getDeclaredField(name); break; }
                catch (NoSuchFieldException ignored) { }
            }
            if (field == null) throw new NoSuchFieldException(target.getClass().getName() + "." + name);
            field.trySetAccessible(); cache.put(name, field);
        }
        return field.get(target);
    }
    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }
    private static final class Limit extends RuntimeException {
        static final Limit INSTANCE = new Limit();
        private Limit() { super(null, null, false, false); }
    }
}
