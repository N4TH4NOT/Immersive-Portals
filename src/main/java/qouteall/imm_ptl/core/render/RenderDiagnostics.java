package qouteall.imm_ptl.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Opt-in counters for graphics regression scenarios; no atomic operations in normal play. */
public final class RenderDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("imm_ptl.renderDiagnostics");
    private static final LongAdder TESTS = new LongAdder(),
            REJECTED = new LongAdder(),
            SODIUM = new LongAdder();
    private static final Map<String, Long> DIRTY_ENTRIES = new LinkedHashMap<>();
    private static final Map<String, Long> DIRTY_BROADCASTS = new LinkedHashMap<>();
    private static final Map<String, Long> DIRTY_TARGETS = new LinkedHashMap<>();
    private static String lastDirtyEntry = "none", lastDirtyTarget = "none";

    public static void frustum(boolean rejected) {
        if (ENABLED) {
            TESTS.increment();
            if (rejected) REJECTED.increment();
        }
    }

    public static void sodiumViewport() {
        if (ENABLED) SODIUM.increment();
    }

    public static void chunkDirtyEntry(String hook, LevelExtractor source, ClientLevel world) {
        if (!ENABLED) return;
        String dimension = world == null ? "null" : world.dimension().identifier().toString();
        DIRTY_ENTRIES.merge(hook + ":" + dimension, 1L, Long::sum);
        var client = Minecraft.getInstance();
        lastDirtyEntry = hook + ":world=" + dimension + "@" + System.identityHashCode(world)
                + ":extractor=" + System.identityHashCode(source)
                + ":activeExtractor=" + System.identityHashCode(client.levelExtractor)
                + ":activeWorld=" + (client.level == null ? "null"
                        : client.level.dimension().identifier())
                + ":frame=" + qouteall.imm_ptl.core.render.context_management.RenderStates.frameIndex;
    }

    public static void chunkDirtyBroadcast(ClientLevel world, boolean suppressed) {
        if (ENABLED)
            DIRTY_BROADCASTS.merge(world.dimension().identifier() + ":suppressed=" + suppressed,
                    1L, Long::sum);
    }

    public static void chunkDirtyTarget(ClientLevel world, LevelExtractor target,
            LevelRenderer renderer, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        if (!ENABLED) return;
        DIRTY_TARGETS.merge(world.dimension().identifier().toString(), 1L, Long::sum);
        lastDirtyTarget = "world=" + world.dimension().identifier()
                + "@" + System.identityHashCode(world)
                + ":extractor=" + System.identityHashCode(target)
                + ":renderer=" + System.identityHashCode(renderer)
                + ":range=" + minX + "," + minY + "," + minZ
                + ".." + maxX + "," + maxY + "," + maxZ;
    }

    public static String chunkUpdates() {
        return "entries=" + DIRTY_ENTRIES + " broadcasts=" + DIRTY_BROADCASTS
                + " targets=" + DIRTY_TARGETS + " lastEntry=" + lastDirtyEntry
                + " lastTarget=" + lastDirtyTarget;
    }

    public static long tests() {
        return TESTS.sum();
    }

    public static long rejected() {
        return REJECTED.sum();
    }

    public static long sodiumViewports() {
        return SODIUM.sum();
    }
}
