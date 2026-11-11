package qouteall.imm_ptl.core.network.bridge;

import io.github.n4th4not.immersiveportals.protocol.BridgeCodec;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A versioned SDK channel, independent of the Fabric-server protocol and vanilla movement packets.
 */
public record BridgePayload(byte[] bytes) implements CustomPacketPayload {
    public BridgePayload {
        bytes = bytes.clone();
        if (bytes.length > BridgeCodec.MAX_PAYLOAD)
            throw new IllegalArgumentException("Oversized bridge payload");
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public static final Type<BridgePayload> TYPE =
            new Type<>(Identifier.parse(BridgeCodec.CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, BridgePayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBytes(payload.bytes),
                    buffer -> {
                        if (buffer.readableBytes() > BridgeCodec.MAX_PAYLOAD)
                            throw new IllegalArgumentException("Oversized bridge payload");
                        byte[] bytes = new byte[buffer.readableBytes()];
                        buffer.readBytes(bytes);
                        return new BridgePayload(bytes);
                    });

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
    }

    @Override
    public Type<BridgePayload> type() {
        return TYPE;
    }
}
