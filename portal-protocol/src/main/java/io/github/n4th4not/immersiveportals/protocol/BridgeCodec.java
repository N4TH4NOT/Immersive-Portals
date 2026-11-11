package io.github.n4th4not.immersiveportals.protocol;

import io.github.n4th4not.gravity.api.GravityWire;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stable, bounded wire protocol. Vanilla Minecraft packets are never extended by the Minestom
 * bridge.
 */
public final class BridgeCodec {
    public static final String CHANNEL = "imm_ptl:bridge";
    public static final int FEATURE_GRAVITY = 1;
    public static final int VERSION = 2,
            MINECRAFT_PROTOCOL = 776,
            MAX_PAYLOAD = 32_767,
            MAX_PORTALS = 4096;
    private static final int MAGIC = 0x4950544c;

    private BridgeCodec() {}

    public static byte[] encode(BridgeMessage message) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            switch (message) {
                case BridgeMessage.Hello m -> {
                    out.writeByte(0);
                    out.writeInt(m.protocol());
                    out.writeInt(m.minecraftProtocol());
                    out.writeInt(m.features());
                }
                case BridgeMessage.Welcome m -> {
                    out.writeByte(1);
                    uuid(out, m.session());
                    out.writeInt(m.worlds().size());
                    for (var w : m.worlds()) {
                        text(out, w.key());
                        text(out, w.dimensionType());
                        out.writeInt(w.seaLevel());
                    }
                }
                case BridgeMessage.Upsert m -> {
                    out.writeByte(2);
                    portal(out, m.portal());
                }
                case BridgeMessage.Remove m -> {
                    out.writeByte(3);
                    uuid(out, m.portal());
                }
                case BridgeMessage.Clear m -> out.writeByte(4);
                case BridgeMessage.Teleport m -> {
                    out.writeByte(5);
                    uuid(out, m.portal());
                    vec(out, m.fromEye());
                    vec(out, m.toEye());
                }
                case BridgeMessage.Rejected m -> {
                    out.writeByte(6);
                    text(out, m.reason());
                }
                case BridgeMessage.OpenView m -> {
                    out.writeByte(7);
                    uuid(out, m.view());
                    text(out, m.world());
                    vec(out, m.center());
                    out.writeInt(m.radius());
                }
                case BridgeMessage.CloseView m -> {
                    out.writeByte(8);
                    uuid(out, m.view());
                }
                case BridgeMessage.WorldFragment m -> {
                    out.writeByte(9);
                    uuid(out, m.view());
                    out.writeInt(m.sequence());
                    out.writeInt(m.index());
                    out.writeInt(m.count());
                    out.writeInt(m.totalSize());
                    blob(out, m.bytes());
                }
                case BridgeMessage.Correction m -> {
                    out.writeByte(11);
                    text(out, m.world());
                    vec(out, m.feet());
                    vec(out, m.velocity());
                    out.writeDouble(m.portalScale());
                    out.writeFloat(m.yaw());
                    out.writeFloat(m.pitch());
                    GravityWire.write(out, m.gravity());
                }
                case BridgeMessage.Gravity m -> {
                    out.writeByte(12);
                    text(out, m.world());
                    out.writeInt(m.entityId());
                    uuid(out, m.entity());
                    GravityWire.write(out, m.state());
                }
                case BridgeMessage.Interaction m -> {
                    out.writeByte(10);
                    out.writeInt(m.path().size());
                    for (var id : m.path()) uuid(out, id);
                    blob(out, m.packet());
                }
            }
            if (bytes.size() > MAX_PAYLOAD)
                throw new IllegalArgumentException("Bridge payload exceeds 32767 bytes");
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BridgeMessage decode(byte[] bytes) {
        if (bytes.length > MAX_PAYLOAD)
            throw new IllegalArgumentException("Oversized bridge payload");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("Invalid bridge magic");
            BridgeMessage result =
                    switch (in.readUnsignedByte()) {
                        case 0 -> new BridgeMessage.Hello(in.readInt(), in.readInt(), in.readInt());
                        case 1 -> {
                            UUID session = uuid(in);
                            int n = count(in, 64);
                            var worlds = new ArrayList<BridgeMessage.World>(n);
                            for (int i = 0; i < n; i++)
                                worlds.add(
                                        new BridgeMessage.World(text(in), text(in), in.readInt()));
                            yield new BridgeMessage.Welcome(session, worlds);
                        }
                        case 2 -> new BridgeMessage.Upsert(portal(in));
                        case 3 -> new BridgeMessage.Remove(uuid(in));
                        case 4 -> new BridgeMessage.Clear();
                        case 5 -> new BridgeMessage.Teleport(uuid(in), vec(in), vec(in));
                        case 6 -> new BridgeMessage.Rejected(text(in));
                        case 7 ->
                                new BridgeMessage.OpenView(
                                        uuid(in), text(in), vec(in), in.readInt());
                        case 8 -> new BridgeMessage.CloseView(uuid(in));
                        case 9 ->
                                new BridgeMessage.WorldFragment(
                                        uuid(in),
                                        in.readInt(),
                                        in.readInt(),
                                        in.readInt(),
                                        in.readInt(),
                                        blob(in, 30_000));
                        case 11 ->
                                new BridgeMessage.Correction(
                                        text(in),
                                        vec(in),
                                        vec(in),
                                        in.readDouble(),
                                        in.readFloat(),
                                        in.readFloat(),
                                        GravityWire.read(in));
                        case 12 ->
                                new BridgeMessage.Gravity(
                                        text(in), in.readInt(), uuid(in), GravityWire.read(in));
                        case 10 -> {
                            int n = count(in, 3);
                            var path = new ArrayList<UUID>(n);
                            for (int i = 0; i < n; i++) path.add(uuid(in));
                            yield new BridgeMessage.Interaction(path, blob(in, 8192));
                        }
                        default -> throw new IllegalArgumentException("Unknown bridge message");
                    };
            if (in.available() != 0) throw new IllegalArgumentException("Trailing bridge data");
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Truncated bridge payload", e);
        }
    }

    private static int count(DataInputStream in, int limit) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > limit) throw new IllegalArgumentException("Invalid collection length");
        return n;
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void text(DataOutputStream out, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > 512) throw new IllegalArgumentException("String too long");
        blob(out, data);
    }

    private static String text(DataInputStream in) throws IOException {
        return new String(blob(in, 512), StandardCharsets.UTF_8);
    }

    private static void blob(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    private static byte[] blob(DataInputStream in, int max) throws IOException {
        int n = count(in, max);
        if (n > in.available()) throw new EOFException();
        return in.readNBytes(n);
    }

    private static void vec(DataOutputStream out, Vec3 v) throws IOException {
        out.writeDouble(v.x());
        out.writeDouble(v.y());
        out.writeDouble(v.z());
    }

    private static Vec3 vec(DataInputStream in) throws IOException {
        return new Vec3(in.readDouble(), in.readDouble(), in.readDouble());
    }

    private static void portal(DataOutputStream out, PortalDefinition p) throws IOException {
        uuid(out, p.id());
        text(out, p.sourceWorld());
        text(out, p.destinationWorld());
        vec(out, p.origin());
        vec(out, p.destination());
        vec(out, p.axisW());
        vec(out, p.axisH());
        out.writeDouble(p.width());
        out.writeDouble(p.height());
        out.writeDouble(p.scale());
        out.writeDouble(p.rotation().x());
        out.writeDouble(p.rotation().y());
        out.writeDouble(p.rotation().z());
        out.writeDouble(p.rotation().w());
        out.writeByte(p.kind().ordinal());
        blob(out, p.extraNbt());
    }

    private static PortalDefinition portal(DataInputStream in) throws IOException {
        UUID id = uuid(in);
        String from = text(in), to = text(in);
        Vec3 origin = vec(in), dest = vec(in), w = vec(in), h = vec(in);
        double width = in.readDouble(), height = in.readDouble(), scale = in.readDouble();
        Quaternion q =
                new Quaternion(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble());
        int k = in.readUnsignedByte();
        if (k >= PortalDefinition.Kind.values().length)
            throw new IllegalArgumentException("Invalid portal kind");
        return new PortalDefinition(
                id,
                from,
                to,
                origin,
                dest,
                w,
                h,
                width,
                height,
                scale,
                q,
                PortalDefinition.Kind.values()[k],
                blob(in, 16_384));
    }
}
