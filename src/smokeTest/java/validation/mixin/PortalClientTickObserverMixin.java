package validation.mixin;

import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import validation.PortalPoseUseFixture;

/** Samples authoritative movement on the native client simulation clock. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PortalClientTickObserverMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleClientTickEnd", at = @At("RETURN"))
    private void portalSmoke_observeClientTick(ServerboundClientTickEndPacket packet, CallbackInfo callback) {
        PortalPoseUseFixture.observeClientTick(player);
    }
}
