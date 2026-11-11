package qouteall.imm_ptl.core.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import qouteall.imm_ptl.core.render.optimization.PortalVisibilityHistory;

class PortalVisibilityHistoryTest {
    @Test
    void missingOrDelayedResultsNeverHideNewViews() {
        var history = new PortalVisibilityHistory();
        assertTrue(history.shouldRender(1, 42, true));
        assertTrue(history.begin(1));
        assertFalse(history.begin(1));
        assertTrue(history.shouldRender(2, 42, true));
    }

    @Test
    void cameraAndPortalChangesInvalidatePredictedOcclusion() {
        var history = new PortalVisibilityHistory();
        history.complete(10, 42, false);
        assertFalse(history.shouldRender(11, 42, true));
        assertFalse(history.shouldRender(12, 42, true));
        assertTrue(history.shouldRender(11, 43, true));
        assertTrue(history.shouldRender(13, 42, true));
    }

    @Test
    void disablingPredictionOnlyAcceptsThisFramesMeasurement() {
        var history = new PortalVisibilityHistory();
        history.complete(10, 42, false);
        assertFalse(history.shouldRender(10, 42, false));
        assertTrue(history.shouldRender(11, 42, false));
    }

    @Test
    void lateReadbacksCannotOverwriteANewerVisibleResult() {
        var history = new PortalVisibilityHistory();
        history.complete(11, 42, true);
        history.complete(10, 42, false);
        assertTrue(history.shouldRender(12, 42, true));
        assertTrue(history.shouldRender(9, 42, true));
    }
}
