package io.github.n4th4not.immersiveportals.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class BridgeProtocolTest {
    private static PortalDefinition portal() {
        return PortalDefinition.builder("demo:a", "demo:b")
                .origin(new Vec3(1, 2, 3))
                .destination(new Vec3(20, 30, 40))
                .scale(2)
                .rotation(Quaternion.axisAngle(new Vec3(0, 1, 0), Math.PI / 2))
                .build();
    }

    @Test
    void geometryRoundTripKeepsRotationAndMetadata() {
        var definition = portal().toBuilder().extraNbt(new byte[] {10, 0}).build();
        var decoded =
                ((BridgeMessage.Upsert)
                                BridgeCodec.decode(
                                        BridgeCodec.encode(new BridgeMessage.Upsert(definition))))
                        .portal();
        assertEquals(definition.id(), decoded.id());
        assertEquals(
                definition.transformPoint(new Vec3(4, 5, 6)),
                decoded.transformPoint(new Vec3(4, 5, 6)));
        assertArrayEquals(definition.extraNbt(), decoded.extraNbt());
    }

    @Test
    void everyControlMessageRoundTrips() {
        UUID id = UUID.randomUUID();
        for (BridgeMessage message :
                List.of(
                        new BridgeMessage.Hello(1, 776),
                        new BridgeMessage.Welcome(
                                id,
                                List.of(
                                        new BridgeMessage.World(
                                                "demo:a", "minecraft:overworld", 63))),
                        new BridgeMessage.Remove(id),
                        new BridgeMessage.Clear(),
                        new BridgeMessage.Teleport(id, new Vec3(1, 2, 3), new Vec3(3, 2, 1)),
                        new BridgeMessage.Rejected("unsupported version"),
                        new BridgeMessage.OpenView(id, "demo:a", Vec3.ZERO, 3),
                        new BridgeMessage.CloseView(id)))
            assertEquals(message, BridgeCodec.decode(BridgeCodec.encode(message)));
        var correction =
                new BridgeMessage.Correction(
                        "demo:a", new Vec3(1, 70, 2), new Vec3(0, -3, 1), .5, 90, -20);
        assertEquals(correction, BridgeCodec.decode(BridgeCodec.encode(correction)));
    }

    @Test
    void malformedPayloadsFailBeforeAllocation() {
        byte[] valid = BridgeCodec.encode(new BridgeMessage.Hello(1, 776));
        for (int length = 0; length < valid.length; length++) {
            int n = length;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BridgeCodec.decode(Arrays.copyOf(valid, n)));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> BridgeCodec.decode(new byte[BridgeCodec.MAX_PAYLOAD + 1]));
        byte[] welcome =
                BridgeCodec.encode(new BridgeMessage.Welcome(UUID.randomUUID(), List.of()));
        Arrays.fill(welcome, 21, 25, (byte) 0x7f);
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(welcome));
    }

    @Test
    void invalidGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Vec3(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Quaternion(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> portal().toBuilder().scale(0).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> portal().toBuilder().axes(new Vec3(1, 0, 0), new Vec3(1, 0, 0)).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> PortalDefinition.builder("../invalid", "demo:b").build());
    }

    @Test
    void crossingRequiresTheFrontFaceAndAnIntersectionInsideTheAperture() {
        var p = PortalDefinition.builder("demo:a", "demo:b").size(2, 2).build();
        assertEquals(.5, p.intersection(new Vec3(0, 0, 1), new Vec3(0, 0, -1)));
        assertTrue(Double.isNaN(p.intersection(new Vec3(0, 0, -1), new Vec3(0, 0, 1))));
        assertTrue(Double.isNaN(p.intersection(new Vec3(2, 0, 1), new Vec3(2, 0, -1))));
    }

    @Test
    void packetReassemblyAcceptsReorderingAndRejectsAmbiguousFragments() {
        var reassembler = new PacketReassembler();
        UUID view = UUID.randomUUID();
        assertTrue(
                reassembler
                        .accept(
                                new BridgeMessage.WorldFragment(
                                        view, 0, 1, 2, 5, new byte[] {4, 5}),
                                0)
                        .isEmpty());
        assertArrayEquals(
                new byte[] {1, 2, 3, 4, 5},
                reassembler
                        .accept(
                                new BridgeMessage.WorldFragment(
                                        view, 0, 0, 2, 5, new byte[] {1, 2, 3}),
                                1)
                        .orElseThrow());
        var fragment = new BridgeMessage.WorldFragment(view, 1, 0, 2, 5, new byte[] {1, 2, 3});
        reassembler.accept(fragment, 2);
        assertThrows(IllegalArgumentException.class, () -> reassembler.accept(fragment, 3));
        reassembler.closeView(view);
        assertTrue(reassembler.accept(fragment, 4).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BridgeMessage.WorldFragment(view, 0, 0, 99, 5, new byte[] {1}));
    }

    @Test
    void metadataArraysCannotBeMutatedByCallers() {
        byte[] bytes = {1, 2};
        var p = portal().toBuilder().extraNbt(bytes).build();
        bytes[0] = 9;
        p.extraNbt()[1] = 9;
        assertArrayEquals(new byte[] {1, 2}, p.extraNbt());
    }

    @Test
    void equivalentDefinitionsHaveValueEqualityIncludingTheirNbt() {
        var original = portal().toBuilder().extraNbt(new byte[] {1, 2}).build();
        var rebuilt = original.toBuilder().build();
        assertEquals(original, rebuilt);
        assertEquals(original.hashCode(), rebuilt.hashCode());
        assertTrue(java.util.Set.of(original).contains(rebuilt));
        assertNotEquals(original, rebuilt.toBuilder().extraNbt(new byte[] {1, 3}).build());
        assertNotEquals(original, rebuilt.toBuilder().scale(3).build());
    }
}
