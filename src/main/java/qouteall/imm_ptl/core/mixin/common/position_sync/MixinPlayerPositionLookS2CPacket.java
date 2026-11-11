package qouteall.imm_ptl.core.mixin.common.position_sync;

import com.google.common.collect.MapMaker;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import java.util.Map;

@Mixin(ClientboundPlayerPositionPacket.class)
public abstract class MixinPlayerPositionLookS2CPacket implements IEPlayerPositionLookS2CPacket {
    // Records retain their native fields. Weak identity keys also distinguish equal packets for different connections.
    @Unique private static final Map<Object, ResourceKey<Level>> ip_dimensions = new MapMaker().weakKeys().makeMap();
    @Shadow @Final @Mutable public static StreamCodec<FriendlyByteBuf, ClientboundPlayerPositionPacket> STREAM_CODEC;
    @Override public ResourceKey<Level> ip_getPlayerDimension() { return ip_dimensions.get(this); }
    @Override public void ip_setPlayerDimension(ResourceKey<Level> dimension) { ip_dimensions.put(this, dimension); }

    @Inject(method="<clinit>", at=@At("RETURN"))
    private static void ip_codec(CallbackInfo ci) {
        var vanilla = STREAM_CODEC;
        STREAM_CODEC = new StreamCodec<>() {
            @Override public ClientboundPlayerPositionPacket decode(FriendlyByteBuf buffer) {
                var packet = vanilla.decode(buffer);
                if (!O_O.isDedicatedServer() && ImmPtlNetworkConfig.doesServerHaveImmPtl()) {
                    ((IEPlayerPositionLookS2CPacket)(Object)packet).ip_setPlayerDimension(buffer.readResourceKey(Registries.DIMENSION));
                }
                return packet;
            }
            @Override public void encode(FriendlyByteBuf buffer, ClientboundPlayerPositionPacket packet) {
                vanilla.encode(buffer, packet);
                ResourceKey<Level> dimension = ((IEPlayerPositionLookS2CPacket)(Object)packet).ip_getPlayerDimension();
                if (dimension != null) buffer.writeResourceKey(dimension);
            }
        };
    }
}
