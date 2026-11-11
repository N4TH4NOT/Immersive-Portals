package qouteall.dimlib.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keep native world lookups consistent with the server's replacement dimension registry. */
@Mixin(Level.class)
public interface DimensionLevelAccess {
    @Mutable @Accessor("registryAccess") void dimlib_registryAccess(RegistryAccess registries);
}
