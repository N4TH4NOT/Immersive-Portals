package io.github.n4th4not.immersiveportals.protocol;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Geometry plus optional native network NBT for shapes, animations and portal settings. */
public record PortalDefinition(
        UUID id,
        String sourceWorld,
        String destinationWorld,
        Vec3 origin,
        Vec3 destination,
        Vec3 axisW,
        Vec3 axisH,
        double width,
        double height,
        double scale,
        Quaternion rotation,
        Kind kind,
        byte[] extraNbt) {
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public enum Kind {
        PORTAL,
        MIRROR
    }

    public PortalDefinition {
        Objects.requireNonNull(id);
        worldKey(sourceWorld);
        worldKey(destinationWorld);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(rotation);
        Objects.requireNonNull(kind);
        if (Math.abs(axisW.lengthSquared() - 1) > 1e-6
                || Math.abs(axisH.lengthSquared() - 1) > 1e-6
                || Math.abs(axisW.dot(axisH)) > 1e-6)
            throw new IllegalArgumentException("Portal axes must be orthonormal");
        if (!Double.isFinite(width)
                || !Double.isFinite(height)
                || width <= 0
                || height <= 0
                || width > 30_000_000
                || height > 30_000_000) throw new IllegalArgumentException("Invalid portal size");
        if (!Double.isFinite(scale) || scale < 1e-6 || scale > 1e6)
            throw new IllegalArgumentException("Invalid portal scale");
        extraNbt = extraNbt.clone();
        if (extraNbt.length > 16_384)
            throw new IllegalArgumentException("Portal NBT exceeds 16 KiB");
    }

    public static String worldKey(String value) {
        if (value == null || value.length() > 128 || !KEY.matcher(value).matches())
            throw new IllegalArgumentException("Invalid world key: " + value);
        return value;
    }

    @Override
    public byte[] extraNbt() {
        return extraNbt.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PortalDefinition p
                && id.equals(p.id) && sourceWorld.equals(p.sourceWorld)
                && destinationWorld.equals(p.destinationWorld) && origin.equals(p.origin)
                && destination.equals(p.destination) && axisW.equals(p.axisW) && axisH.equals(p.axisH)
                && Double.compare(width, p.width) == 0 && Double.compare(height, p.height) == 0
                && Double.compare(scale, p.scale) == 0 && rotation.equals(p.rotation) && kind == p.kind
                && java.util.Arrays.equals(extraNbt, p.extraNbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sourceWorld, destinationWorld, origin, destination, axisW, axisH,
                width, height, scale, rotation, kind, java.util.Arrays.hashCode(extraNbt));
    }

    public Vec3 normal() {
        return axisW.cross(axisH);
    }

    public Vec3 transformPoint(Vec3 point) {
        return destination.add(transformVector(point.subtract(origin)));
    }

    public Vec3 transformVector(Vec3 vector) {
        return rotation.rotate(vector).multiply(scale);
    }

    public double signedDistance(Vec3 point) {
        return point.subtract(origin).dot(normal());
    }

    /**
     * Front-to-back swept intersection. The server also checks terrain and advanced shape
     * containment.
     */
    public double intersection(Vec3 from, Vec3 to) {
        double a = signedDistance(from), b = signedDistance(to);
        if (a < 0 || b > 0 || a - b < 1e-9) return Double.NaN;
        double t = a / (a - b);
        Vec3 local = from.add(to.subtract(from).multiply(t)).subtract(origin);
        return Math.abs(local.dot(axisW)) <= width / 2 && Math.abs(local.dot(axisH)) <= height / 2
                ? t
                : Double.NaN;
    }

    public static Builder builder(String source, String destination) {
        return new Builder(source, destination);
    }

    public Builder toBuilder() {
        return new Builder(sourceWorld, destinationWorld)
                .id(id)
                .origin(origin)
                .destination(destination)
                .axes(axisW, axisH)
                .size(width, height)
                .scale(scale)
                .rotation(rotation)
                .kind(kind)
                .extraNbt(extraNbt);
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private final String source, target;
        private Vec3 origin = Vec3.ZERO,
                destination = Vec3.ZERO,
                w = new Vec3(1, 0, 0),
                h = new Vec3(0, 1, 0);
        private double width = 2, height = 3, scale = 1;
        private Quaternion rotation = Quaternion.IDENTITY;
        private Kind kind = Kind.PORTAL;
        private byte[] nbt = new byte[0];

        private Builder(String source, String target) {
            this.source = source;
            this.target = target;
        }

        public Builder id(UUID v) {
            id = v;
            return this;
        }

        public Builder origin(Vec3 v) {
            origin = v;
            return this;
        }

        public Builder destination(Vec3 v) {
            destination = v;
            return this;
        }

        public Builder axes(Vec3 a, Vec3 b) {
            w = a;
            h = b;
            return this;
        }

        public Builder size(double a, double b) {
            width = a;
            height = b;
            return this;
        }

        public Builder scale(double v) {
            scale = v;
            return this;
        }

        public Builder rotation(Quaternion v) {
            rotation = v;
            return this;
        }

        public Builder kind(Kind v) {
            kind = v;
            return this;
        }

        public Builder extraNbt(byte[] v) {
            nbt = v.clone();
            return this;
        }

        public PortalDefinition build() {
            return new PortalDefinition(
                    id,
                    source,
                    target,
                    origin,
                    destination,
                    w,
                    h,
                    width,
                    height,
                    scale,
                    rotation,
                    kind,
                    nbt);
        }
    }
}
