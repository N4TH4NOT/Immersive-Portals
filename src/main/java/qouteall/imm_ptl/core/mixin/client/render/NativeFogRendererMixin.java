package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;

import org.joml.Vector4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Isolates native rain-fog interpolation for each renderer and loaded world. */
@Mixin(FogRenderer.class)
public abstract class NativeFogRendererMixin {
    @Unique
    private final Map<ClientLevel, List<FogEnvironment>> ip_environments = new WeakHashMap<>();

    @ModifyExpressionValue(
            method = "computeFogColor",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/fog/FogRenderer;FOG_ENVIRONMENTS:Ljava/util/List;",
                    opcode = Opcodes.GETSTATIC))
    private List<FogEnvironment> ip_colorEnvironments(List<FogEnvironment> original,
            Camera camera, float partialTick, ClientLevel level, int renderDistance,
            float darkenWorldAmount, Vector4f color) {
        return ip_environmentsFor(level, original);
    }

    @ModifyExpressionValue(
            method = "setupFog",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/fog/FogRenderer;FOG_ENVIRONMENTS:Ljava/util/List;",
                    opcode = Opcodes.GETSTATIC))
    private List<FogEnvironment> ip_distanceEnvironments(List<FogEnvironment> original,
            Camera camera, int renderDistance, DeltaTracker deltaTracker,
            float darkenWorldAmount, ClientLevel level) {
        return ip_environmentsFor(level, original);
    }

    @Unique
    private List<FogEnvironment> ip_environmentsFor(ClientLevel level, List<FogEnvironment> original) {
        return ip_environments.computeIfAbsent(level, ignored -> original.stream()
                // Only vanilla atmospheric fog owns this mutable rain accumulator.
                // Preserve the order and identity of all other environments, including mods.
                .map(environment -> environment.getClass() == AtmosphericFogEnvironment.class
                        ? new AtmosphericFogEnvironment() : environment)
                .toList());
    }
}
