package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.block_manipulation.PortalEntityActionContext;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer_RemoteDamage {
    @WrapMethod(method = "hurtServer")
    private boolean ip_reflectDamageInPhysicalWorld(ServerLevel world, DamageSource source,
            float amount, Operation<Boolean> original) {
        return PortalEntityActionContext.withPhysicalDamage((ServerPlayer) (Object) this,
                world, source, physicalWorld -> original.call(physicalWorld, source, amount));
    }
}
