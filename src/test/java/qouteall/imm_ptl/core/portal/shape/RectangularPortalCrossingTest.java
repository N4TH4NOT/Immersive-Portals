package qouteall.imm_ptl.core.portal.shape;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.q_misc_util.my_util.DQuaternion;

class RectangularPortalCrossingTest {
    private static final RectangularPortalShape SHAPE = RectangularPortalShape.INSTANCE;
    private static final UnilateralPortalState STATE =
            new UnilateralPortalState(Level.OVERWORLD, Vec3.ZERO, DQuaternion.identity, 2, 3);

    @Test
    void inwardMovementAfterPausingOnThePlaneCrossesAtItsStartingPoint() {
        Vec3 onPlane = new Vec3(.3, -.4, 0);
        assertNull(SHAPE.raytracePortalShapeByLocalPos(
                STATE, new Vec3(.3, -.4, .01), onPlane, 0));
        assertNull(SHAPE.raytracePortalShapeByLocalPos(STATE, onPlane, onPlane, 0));

        var crossing = SHAPE.raytracePortalShapeByLocalPos(
                STATE, onPlane, new Vec3(.32, -.36, -.01), 0);
        assertNotNull(crossing);
        assertEquals(0, crossing.t(), 0);
        assertEquals(onPlane, crossing.hitPos());
        assertEquals(new Vec3(0, 0, 1), crossing.surfaceNormal());
    }

    @Test
    void destinationCheckpointDoesNotImmediatelyCrossTheReverseFace() {
        // The teleportation manager advances its checkpoint slightly into the exit.
        // Its current interpolated eye may still be exactly on the destination plane.
        Vec3 checkpoint = new Vec3(0, 0, .0001);
        assertNull(SHAPE.raytracePortalShapeByLocalPos(STATE, checkpoint, Vec3.ZERO, 0));
        assertNull(SHAPE.raytracePortalShapeByLocalPos(
                STATE, Vec3.ZERO, new Vec3(0, 0, .01), 0));
        assertNotNull(SHAPE.raytracePortalShapeByLocalPos(
                STATE, Vec3.ZERO, new Vec3(0, 0, -.01), 0));
    }

    @Test
    void aReverseOrientedPortalUsesItsOwnInwardDirection() {
        Vec3 origin = new Vec3(128, 72, 64);
        var reversed = new UnilateralPortalState(
                Level.OVERWORLD, origin, new DQuaternion(0, 1, 0, 0), 2, 3);

        var crossing = SHAPE.raytracePortalShape(reversed, origin, origin.add(0, 0, .01), 0);
        assertNotNull(crossing);
        assertEquals(0, crossing.t(), 0);
        assertEquals(origin, crossing.hitPos());
        assertNull(SHAPE.raytracePortalShape(reversed, origin, origin.add(0, 0, -.01), 0));
    }

    @Test
    void theBoundaryCaseDoesNotWidenTheApertureOrPermitBackSideCrossings() {
        assertNull(SHAPE.raytracePortalShapeByLocalPos(
                STATE, new Vec3(1.1, 0, 0), new Vec3(1.1, 0, -.01), 0));
        assertNull(SHAPE.raytracePortalShapeByLocalPos(
                STATE, new Vec3(0, 0, -.01), new Vec3(0, 0, -.02), 0));
    }
}
