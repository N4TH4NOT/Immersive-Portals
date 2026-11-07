package qouteall.imm_ptl.core.mixin.common.interaction;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invokes vanilla mount eligibility without modifying the passenger relationship. */
@Mixin(Entity.class)
public interface EntityInteractionMountAccess {
    @Invoker("couldAcceptPassenger") boolean ip_couldAcceptPassenger();
    @Invoker("canAddPassenger") boolean ip_canAddPassenger(Entity passenger);
    @Invoker("canRide") boolean ip_canRide(Entity vehicle);
}
