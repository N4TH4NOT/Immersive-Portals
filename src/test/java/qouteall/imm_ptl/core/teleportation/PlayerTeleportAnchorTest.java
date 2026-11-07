package qouteall.imm_ptl.core.teleportation;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.network.ImmPtlNetworking;

import java.util.UUID;
import java.util.function.UnaryOperator;

class PlayerTeleportAnchorTest {
    private static void assertPosition(Vec3 expected, Vec3 actual) {
        assertEquals(0, expected.distanceTo(actual), 1e-12);
    }

    @Test
    void rapidPoseSnapshotsKeepTheSameFeetThroughAnIdentityOpening() {
        Vec3 feet = new Vec3(-.65, 70, 32.02);
        Vec3 translation = new Vec3(64, 0, 0);
        // Standing, crouching and swimming can change before the server handles
        // a crossing. The same snapshot must select both endpoint eye offsets.
        for (double height : new double[] {1.27, 1.62, .4, 1.27, 1.62}) {
            Vec3 offset = new Vec3(0, height, 0);
            assertPosition(feet.add(translation), PlayerTeleportAnchor.transformFeet(
                    feet, offset, offset, position -> position.add(translation)));
        }
    }

    @Test
    void rotatingGravityAndScalingPreserveTheMappedFeetAndEyes() {
        Vec3 feet = new Vec3(1, 70, 32);
        Vec3 sourceOffset = new Vec3(0, 1.27, 0);
        Vec3 destinationOffset = new Vec3(-.635, 0, 0);
        UnaryOperator<Vec3> transform = position -> new Vec3(
                128 - position.y * .5, position.x * .5, position.z * .5);
        Vec3 actual = PlayerTeleportAnchor.transformFeet(
                feet, sourceOffset, destinationOffset, transform);
        assertPosition(transform.apply(feet), actual);
        assertPosition(transform.apply(feet.add(sourceOffset)), actual.add(destinationOffset));
    }

    @Test
    void unchangedGravityUsesTheDestinationPoseOffsetAfterPortalRotation() {
        Vec3 feet = new Vec3(1, 70, 32);
        Vec3 sourceOffset = new Vec3(0, 1.27, 0);
        Vec3 destinationOffset = new Vec3(0, .635, 0);
        UnaryOperator<Vec3> transform = position -> new Vec3(
                128 - position.y * .5, position.x * .5, position.z * .5);
        Vec3 actual = PlayerTeleportAnchor.transformFeet(
                feet, sourceOffset, destinationOffset, transform);
        assertPosition(transform.apply(feet.add(sourceOffset)), actual.add(destinationOffset));
        assertTrue(actual.distanceTo(transform.apply(feet)) > .8);
    }

    @Test
    void crossingCodecKeepsFeetAndEachPoseInPacketOrder() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var id = UUID.fromString("f16da856-e8d2-42f4-aee7-e24d868965d4");
            var poses = new Pose[] {Pose.CROUCHING, Pose.STANDING, Pose.SWIMMING, Pose.CROUCHING};
            for (int index = 0; index < poses.length; index++) {
                var packet = new ImmPtlNetworking.TeleportPacket(-1,
                        new Vec3(64, 70, 32 - index * .01), poses[index], id);
                ImmPtlNetworking.TeleportPacket.CODEC.encode(buffer, packet);
            }
            for (int index = 0; index < poses.length; index++) {
                var packet = ImmPtlNetworking.TeleportPacket.CODEC.decode(buffer);
                assertEquals(-1, packet.dimensionId());
                assertEquals(id, packet.portalId());
                assertEquals(poses[index], packet.poseBeforeTeleportation());
                assertPosition(new Vec3(64, 70, 32 - index * .01), packet.feetPosBeforeTeleportation());
            }
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void nonFiniteAnchorsCannotPassTheServerCoordinateCheck() {
        assertTrue(PlayerTeleportAnchor.isFinite(new Vec3(-.65, 70, 32)));
        assertFalse(PlayerTeleportAnchor.isFinite(new Vec3(Double.NaN, 70, 32)));
        assertFalse(PlayerTeleportAnchor.isFinite(new Vec3(0, Double.POSITIVE_INFINITY, 32)));
        assertFalse(PlayerTeleportAnchor.isFinite(new Vec3(0, 70, Double.NEGATIVE_INFINITY)));
    }
}
