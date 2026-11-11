package io.github.n4th4not.immersiveportals.protocol;

import java.util.*;

/** Bounded reassembly of native world packets. Call clear() when a connection closes. */
public final class PacketReassembler {
    private record Key(UUID view, int sequence) {}

    private static final class Pending {
        final long started;
        final int size;
        final byte[][] fragments;
        int count, bytes;

        Pending(BridgeMessage.WorldFragment part, long now) {
            started = now;
            size = part.totalSize();
            fragments = new byte[part.count()][];
        }
    }

    private final Map<Key, Pending> pending = new HashMap<>();

    public Optional<byte[]> accept(BridgeMessage.WorldFragment part, long nanoTime) {
        pending.values().removeIf(p -> nanoTime - p.started > 10_000_000_000L);
        Key key = new Key(part.view(), part.sequence());
        Pending frame = pending.get(key);
        if (frame == null) {
            if (pending.size() >= 8)
                throw new IllegalArgumentException("Too many incomplete world packets");
            frame = new Pending(part, nanoTime);
            pending.put(key, frame);
        }
        if (frame.size != part.totalSize() || frame.fragments.length != part.count())
            throw new IllegalArgumentException("Inconsistent world fragments");
        byte[] bytes = part.bytes();
        if (frame.fragments[part.index()] != null)
            throw new IllegalArgumentException("Duplicate world fragment");
        frame.fragments[part.index()] = bytes;
        frame.bytes += bytes.length;
        frame.count++;
        if (frame.bytes > frame.size) {
            pending.remove(key);
            throw new IllegalArgumentException("World packet exceeds declared size");
        }
        if (frame.count < frame.fragments.length) return Optional.empty();
        pending.remove(key);
        if (frame.bytes != frame.size) throw new IllegalArgumentException("Truncated world packet");
        byte[] result = new byte[frame.size];
        int offset = 0;
        for (byte[] fragment : frame.fragments) {
            System.arraycopy(fragment, 0, result, offset, fragment.length);
            offset += fragment.length;
        }
        return Optional.of(result);
    }

    public void closeView(UUID view) {
        pending.keySet().removeIf(k -> k.view.equals(view));
    }

    public void clear() {
        pending.clear();
    }
}
