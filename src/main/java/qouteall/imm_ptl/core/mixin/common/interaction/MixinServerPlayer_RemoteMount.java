package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.block_manipulation.PortalEntityActionContext;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer_RemoteMount {
    @WrapMethod(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z")
    private boolean ip_transferBeforeRemoteMount(Entity vehicle, boolean force, boolean sendEvent,
            Operation<Boolean> original) {
        return PortalEntityActionContext.mount((ServerPlayer) (Object) this, vehicle, force,
                () -> original.call(vehicle, force, sendEvent));
    }
}
