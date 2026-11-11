package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.block_manipulation.SignInteractionServer;
import qouteall.imm_ptl.core.network.PacketRedirection;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer_SignInteraction {
    @WrapMethod(method = "openTextEdit")
    private void ip_bindRemoteSignEditor(SignBlockEntity sign, boolean front, Operation<Void> original) {
        SignInteractionServer.open((ServerPlayer) (Object) this, sign, front,
                () -> original.call(sign, front));
    }

    @Redirect(method = "openTextEdit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;level()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel ip_signUpdateWorld(ServerPlayer player) {
        var world = SignInteractionServer.openingWorld();
        return world == null ? player.level() : world;
    }

    @WrapOperation(method = "openTextEdit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void ip_sendEditorPath(net.minecraft.server.network.ServerGamePacketListenerImpl listener,
            Packet<?> packet, Operation<Void> original) {
        var editor = SignInteractionServer.editorPacket(packet);
        if (editor == packet) original.call(listener, packet);
        else PacketRedirection.withoutForceRedirect(() -> {
            original.call(listener, editor);
            return null;
        });
    }
}
