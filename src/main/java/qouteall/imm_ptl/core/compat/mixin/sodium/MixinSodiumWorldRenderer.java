package qouteall.imm_ptl.core.compat.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumChunkEvents;

@Mixin(SodiumWorldRenderer.class)
public abstract class MixinSodiumWorldRenderer {
    @Shadow private ClientLevel level;
    @Shadow private RenderSectionManager renderSectionManager;

    @Inject(method = "initRenderer", at = @At("HEAD"))
    private void ip_beforeReadySnapshot(CallbackInfo callback) {
        var tracker = ChunkTrackerHolder.get(level);
        SodiumChunkEvents.unregister(tracker, renderSectionManager);
        // Existing views retain these transitions; the new manager receives
        // their resulting ready state through Sodium's native initialization.
        SodiumChunkEvents.collect(tracker);
    }

    @Inject(method = "initRenderer", at = @At("RETURN"))
    private void ip_registerChunkConsumer(CallbackInfo callback) {
        SodiumChunkEvents.register(ChunkTrackerHolder.get(level), renderSectionManager);
    }

    @Inject(method = "deleteRendererState", at = @At("HEAD"))
    private void ip_unregisterChunkConsumer(CallbackInfo callback) {
        if (level != null)
            SodiumChunkEvents.unregister(ChunkTrackerHolder.get(level), renderSectionManager);
    }

    @WrapOperation(method = "processChunkEvents", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker;forEachEvent(Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker$ChunkEventHandler;Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker$ChunkEventHandler;)V"))
    private void ip_distributeChunkEvents(ChunkTracker tracker,
            ChunkTracker.ChunkEventHandler added, ChunkTracker.ChunkEventHandler removed,
            Operation<Void> original) {
        SodiumChunkEvents.deliver(tracker, renderSectionManager, added, removed);
    }
}
