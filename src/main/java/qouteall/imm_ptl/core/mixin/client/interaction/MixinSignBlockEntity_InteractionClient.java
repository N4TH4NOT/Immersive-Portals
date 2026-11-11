package qouteall.imm_ptl.core.mixin.client.interaction;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.block_manipulation.SignInteractionClient;
import java.util.UUID;

@Mixin(SignBlockEntity.class)
public abstract class MixinSignBlockEntity_InteractionClient {
    @Inject(method = "playerIsTooFarAwayToEdit", at = @At("HEAD"), cancellable = true)
    private void ip_keepRemoteScreen(UUID id, CallbackInfoReturnable<Boolean> ci) {
        var sign = (SignBlockEntity) (Object) this;
        if (SignInteractionClient.hasSession(sign, id))
            ci.setReturnValue(!SignInteractionClient.canEdit(sign));
    }
}
