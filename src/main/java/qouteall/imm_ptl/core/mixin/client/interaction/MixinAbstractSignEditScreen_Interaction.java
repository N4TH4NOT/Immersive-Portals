package qouteall.imm_ptl.core.mixin.client.interaction;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import qouteall.imm_ptl.core.block_manipulation.SignInteractionClient;

@Mixin(AbstractSignEditScreen.class)
public abstract class MixinAbstractSignEditScreen_Interaction {
    @Shadow @Final protected SignBlockEntity sign;

    @ModifyArg(method = "removed", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private Packet<?> ip_updateActualSignWorld(Packet<?> packet) {
        return packet instanceof ServerboundSignUpdatePacket update
                ? SignInteractionClient.updatePacket(sign, update) : packet;
    }
}
