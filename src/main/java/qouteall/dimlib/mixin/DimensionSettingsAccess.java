package qouteall.dimlib.mixin;

import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldGenSettings.class)
public interface DimensionSettingsAccess {
    @Mutable @Accessor("dimensions") void dimlib_dimensions(WorldDimensions dimensions);
}
