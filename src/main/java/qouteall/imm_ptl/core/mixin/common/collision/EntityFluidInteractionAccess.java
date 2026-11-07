package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(EntityFluidInteraction.class)
public interface EntityFluidInteractionAccess {
    @Accessor("trackerByFluid")
    Map<TagKey<Fluid>, ?> ip_trackers();
}
