package qouteall.imm_ptl.core.render;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.core.SectionPos;
import org.junit.jupiter.api.Test;

class ChunkRenderUpdatesTest {
    @Test
    void recursiveExtractionCannotClearTheParentsPendingChunkUpdates() {
        var state = new ChunkLoadingRenderState();
        state.addedLoadedChunks.add(11);
        state.removedLoadedChunks.add(22);
        state.addedEmptySections.add(33);
        state.removedEmptySections.add(44);
        var nativeAdded = state.addedLoadedChunks;
        var nativeRemoved = state.removedLoadedChunks;
        var nativeEmpty = state.addedEmptySections;
        var nativeNonempty = state.removedEmptySections;

        ChunkRenderUpdates.snapshot(state);
        // Another view extracts the world and flips/clears the original native buffers.
        nativeAdded.clear();
        nativeRemoved.clear();
        nativeEmpty.clear();
        nativeNonempty.clear();

        assertTrue(state.addedLoadedChunks.contains(11));
        assertTrue(state.removedLoadedChunks.contains(22));
        assertTrue(state.addedEmptySections.contains(33));
        assertTrue(state.removedEmptySections.contains(44));
    }

    @Test
    void fillingOrEmptyingASectionReplacesItsPreviousEmptinessDelta() {
        var state = new ChunkLoadingRenderState();
        long section = SectionPos.asLong(-50000, -4, 50000);
        ChunkRenderUpdates.setEmpty(state, section, true);
        ChunkRenderUpdates.setEmpty(state, section, false);
        assertFalse(state.addedEmptySections.contains(section));
        assertTrue(state.removedEmptySections.contains(section));

        ChunkRenderUpdates.setEmpty(state, section, true);
        assertTrue(state.addedEmptySections.contains(section));
        assertFalse(state.removedEmptySections.contains(section));
    }
}
