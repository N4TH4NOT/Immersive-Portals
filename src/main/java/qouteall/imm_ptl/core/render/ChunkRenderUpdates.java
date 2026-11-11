package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;

/** Detaches a view's chunk deltas from the native cache's reusable extraction buffers. */
final class ChunkRenderUpdates {
    static void snapshot(ChunkLoadingRenderState state) {
        state.addedLoadedChunks = new LongOpenHashSet(state.addedLoadedChunks);
        state.removedLoadedChunks = new LongOpenHashSet(state.removedLoadedChunks);
        state.addedEmptySections = new LongOpenHashSet(state.addedEmptySections);
        state.removedEmptySections = new LongOpenHashSet(state.removedEmptySections);
    }

    static void setEmpty(ChunkLoadingRenderState state, long section, boolean empty) {
        (empty ? state.addedEmptySections : state.removedEmptySections).add(section);
        (empty ? state.removedEmptySections : state.addedEmptySections).remove(section);
    }
}
