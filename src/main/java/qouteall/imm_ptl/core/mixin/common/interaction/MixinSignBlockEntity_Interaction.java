package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.block_manipulation.SignInteractionServer;

import java.util.UUID;

@Mixin(SignBlockEntity.class)
public abstract class MixinSignBlockEntity_Interaction {
    @ModifyExpressionValue(method = "isFacingFrontText", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;getX()D"))
    private double ip_frontSideX(double original, Player player) {
        return player instanceof ServerPlayer server ? SignInteractionServer.positionForSign(
                server, (SignBlockEntity) (Object) this).x : original;
    }

    @ModifyExpressionValue(method = "isFacingFrontText", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;getZ()D"))
    private double ip_frontSideZ(double original, Player player) {
        return player instanceof ServerPlayer server ? SignInteractionServer.positionForSign(
                server, (SignBlockEntity) (Object) this).z : original;
    }

    @Inject(method = "playerIsTooFarAwayToEdit", at = @At("HEAD"), cancellable = true)
    private void ip_keepRemoteEditor(UUID id, CallbackInfoReturnable<Boolean> ci) {
        var sign = (SignBlockEntity) (Object) this;
        if (!(sign.getLevel() instanceof ServerLevel world)) return;
        var player = world.getServer().getPlayerList().getPlayer(id);
        if (player != null && SignInteractionServer.hasSession(player, sign))
            ci.setReturnValue(!SignInteractionServer.canEdit(player, sign));
    }
}
