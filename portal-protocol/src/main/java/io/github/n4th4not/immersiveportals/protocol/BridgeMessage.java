package io.github.n4th4not.immersiveportals.protocol;

import java.util.List;
import java.util.UUID;

public sealed interface BridgeMessage {
    record Hello(int protocol, int minecraftProtocol, int features) implements BridgeMessage {
        public Hello(int protocol, int minecraftProtocol) {
            this(protocol, minecraftProtocol, 0);
        }
    }

    record World(String key, String dimensionType, int seaLevel) {
        public World {
            PortalDefinition.worldKey(key);
            PortalDefinition.worldKey(dimensionType);
            if (seaLevel < -2032 || seaLevel > 2032)
                throw new IllegalArgumentException("Invalid sea level");
        }
    }

    record Welcome(UUID session, List<World> worlds) implements BridgeMessage {
        public Welcome {
            worlds = List.copyOf(worlds);
            if (worlds.size() > 64) throw new IllegalArgumentException("Too many worlds");
        }
    }

    record Upsert(PortalDefinition portal) implements BridgeMessage {}

    record Remove(UUID portal) implements BridgeMessage {}

    record Clear() implements BridgeMessage {}

    record Teleport(UUID portal, Vec3 fromEye, Vec3 toEye) implements BridgeMessage {}

    record Correction(
            String world,
            Vec3 feet,
            Vec3 velocity,
            double portalScale,
            float yaw,
            float pitch,
            GravityState gravity)
            implements BridgeMessage {
        public Correction(
                String world,
                Vec3 feet,
                Vec3 velocity,
                double portalScale,
                float yaw,
                float pitch) {
            this(world, feet, velocity, portalScale, yaw, pitch, GravityState.DEFAULT);
        }

        public Correction {
            java.util.Objects.requireNonNull(gravity);
            PortalDefinition.worldKey(world);
            if (!Double.isFinite(portalScale)
                    || portalScale < 1e-6
                    || portalScale > 1e6
                    || !Float.isFinite(yaw)
                    || !Float.isFinite(pitch))
                throw new IllegalArgumentException("Invalid player correction");
        }
    }

    /** Server-owned state, addressed by both numeric id and UUID to reject recycled entity ids. */
    record Gravity(String world, int entityId, UUID entity, GravityState state)
            implements BridgeMessage {
        public Gravity {
            PortalDefinition.worldKey(world);
            java.util.Objects.requireNonNull(entity);
            java.util.Objects.requireNonNull(state);
        }
    }

    record Rejected(String reason) implements BridgeMessage {}

    record OpenView(UUID view, String world, Vec3 center, int radius) implements BridgeMessage {
        public OpenView {
            PortalDefinition.worldKey(world);
            if (radius < 0 || radius > 3) throw new IllegalArgumentException("Invalid view radius");
        }
    }

    record CloseView(UUID view) implements BridgeMessage {}

    record WorldFragment(UUID view, int sequence, int index, int count, int totalSize, byte[] bytes)
            implements BridgeMessage {
        public WorldFragment {
            if (sequence < 0
                    || count < 1
                    || count > 72
                    || index < 0
                    || index >= count
                    || totalSize < 1
                    || totalSize > 2_097_152
                    || bytes.length > 30_000)
                throw new IllegalArgumentException("Invalid world fragment");
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    /**
     * Native serverbound packet body, including its protocol id; the server validates the portal
     * path and reach.
     */
    record Interaction(List<UUID> path, byte[] packet) implements BridgeMessage {
        public Interaction {
            path = List.copyOf(path);
            packet = packet.clone();
            if (path.isEmpty() || path.size() > 3 || packet.length > 8192)
                throw new IllegalArgumentException("Invalid remote interaction");
        }

        @Override
        public byte[] packet() {
            return packet.clone();
        }
    }
}
