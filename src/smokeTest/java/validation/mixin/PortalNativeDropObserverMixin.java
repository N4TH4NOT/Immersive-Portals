package validation.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import validation.PortalNativeInteractionFixture;

/** Records the real native drop after spawning, before ordinary item movement. */
@Mixin(ServerPlayer.class)
public abstract class PortalNativeDropObserverMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void portalSmoke_observeNativeDrop(ItemStack stack, boolean random, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> callback) {
        PortalNativeInteractionFixture.observeNativeDrop((ServerPlayer) (Object) this,
                callback.getReturnValue());
    }
}
