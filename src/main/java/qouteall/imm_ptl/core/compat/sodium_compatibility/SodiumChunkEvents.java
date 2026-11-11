package qouteall.imm_ptl.core.compat.sodium_compatibility;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.WeakHashMap;

/** Distributes Sodium's destructive per-world event stream to independent native views. */
public final class SodiumChunkEvents {
    private SodiumChunkEvents() {}

    private static final class Pending {
        final LongOpenHashSet removed = new LongOpenHashSet();
        final LongOpenHashSet added = new LongOpenHashSet();

        void remove(long chunk) {
            if (!added.remove(chunk)) removed.add(chunk);
        }

        void add(long chunk) {
            if (!removed.remove(chunk)) added.add(chunk);
        }
    }

    // Accessed on the native client thread. Neither the queued coordinates nor
    // the weak registrations keep a discarded world or renderer alive.
    private static final Map<ChunkTracker, Map<RenderSectionManager, Pending>> CONSUMERS =
            new WeakHashMap<>();

    public static void register(ChunkTracker tracker, RenderSectionManager manager) {
        consumers(tracker).put(manager, new Pending());
    }

    public static void unregister(ChunkTracker tracker, RenderSectionManager manager) {
        var consumers = CONSUMERS.get(tracker);
        if (consumers != null) consumers.remove(manager);
    }

    /** Collect before a new manager takes Sodium's canonical ready-chunk snapshot. */
    public static void collect(ChunkTracker tracker) {
        var removed = new LongArrayList();
        var added = new LongArrayList();
        tracker.forEachEvent((x, z) -> added.add(ChunkPos.pack(x, z)),
                (x, z) -> removed.add(ChunkPos.pack(x, z)));
        if (removed.isEmpty() && added.isEmpty()) return;
        // Match ChunkTracker.updateMerged's cancellation rule at each view's
        // own consumption boundary. An idle view must not load a chunk whose
        // transient ready state has already ended in the authoritative tracker.
        for (var pending : consumers(tracker).values()) {
            for (long chunk : removed) pending.remove(chunk);
            for (long chunk : added) pending.add(chunk);
        }
    }

    public static void deliver(ChunkTracker tracker, RenderSectionManager manager,
            ChunkTracker.ChunkEventHandler added, ChunkTracker.ChunkEventHandler removed) {
        collect(tracker);
        var pending = consumers(tracker).get(manager);
        if (pending == null)
            throw new IllegalStateException("Sodium chunk consumer was not initialized");
        // Apply only at the caller's own native chunk-update boundary, after
        // beforeSectionUpdates; never mutate another view's asynchronous state.
        for (long chunk : pending.removed)
            removed.apply(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
        pending.removed.clear();
        for (long chunk : pending.added)
            added.apply(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
        pending.added.clear();
    }

    private static Map<RenderSectionManager, Pending> consumers(ChunkTracker tracker) {
        return CONSUMERS.computeIfAbsent(tracker, ignored -> new WeakHashMap<>());
    }
}
