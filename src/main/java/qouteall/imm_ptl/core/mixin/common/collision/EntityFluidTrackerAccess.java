package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.entity.EntityFluidInteraction$Tracker")
public interface EntityFluidTrackerAccess {
    @Accessor("height") double ip_height();
    @Accessor("height") void ip_height(double value);
    @Accessor("eyesInside") boolean ip_eyesInside();
    @Accessor("eyesInside") void ip_eyesInside(boolean value);
    @Accessor("accumulatedCurrent") Vec3 ip_current();
    @Accessor("accumulatedCurrent") void ip_current(Vec3 value);
    @Accessor("currentCount") int ip_currentCount();
    @Accessor("currentCount") void ip_currentCount(int value);
}
