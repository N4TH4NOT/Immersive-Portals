package qouteall.imm_ptl.core.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.*;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.mixin.client.render.NativeGameRendererAccess;
import qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor;
import qouteall.imm_ptl.core.portal.animation.*;
import qouteall.imm_ptl.core.render.NativeWorldRenderer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements IEMinecraftClient {
    @Shadow @Mutable @Final public LevelRenderer levelRenderer;
    @Shadow @Mutable @Final public LevelExtractor levelExtractor;
    @Shadow @Mutable @Final public GameRenderer gameRenderer;
    @Shadow @Mutable @Final public ParticleEngine particleEngine;
    @Shadow public ClientLevel level;
    @Shadow private Thread gameThread;
    @Unique private long ip_lastPerformanceSampleNanos = System.nanoTime();

    @Inject(method="renderFrame",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/LevelRenderer;endFrame()V",shift=At.Shift.AFTER))
    private void ip_endSubmittedFrame(CallbackInfo ci) { NativeWorldRenderer.endFrame(); }

    @Inject(method = "renderFrame", at = @At("TAIL"))
    private void ip_sampleClientPerformance(CallbackInfo ci) {
        long now = System.nanoTime();
        if (now - ip_lastPerformanceSampleNanos >= 1_000_000_000L) {
            ip_lastPerformanceSampleNanos = now;
            ClientPerformanceMonitor.updateEverySecond(((Minecraft) (Object) this).getFps());
        }
    }

    @Inject(method = "tick", at = @At(value="INVOKE", target="Lnet/minecraft/client/multiplayer/ClientLevel;tick(Ljava/util/function/BooleanSupplier;)V", shift=At.Shift.AFTER))
    private void ip_afterWorldTick(CallbackInfo ci) {
        Minecraft client = (Minecraft)(Object)this;
        if (level == null || client.getConnection() == null) return;
        ClientWorldLoader.tick();
        RenderStates.setPartialTick(0);
        StableClientTimer.tick();
        StableClientTimer.update(level.getGameTime(), 0);
        ClientPortalAnimationManagement.tick();
        ClientTeleportationManager.manageTeleportation(true);
        IPGlobal.POST_CLIENT_TICK_EVENT.invoker().run();
    }
    @Inject(method="updateLevelInEngines", at=@At("HEAD"))
    private void ip_onLevelChange(ClientLevel next, CallbackInfo ci) {
        if (ClientWorldLoader.getIsInitialized()) {
            IPCGlobal.CLIENT_CLEANUP_EVENT.invoker().run();
            if (next == null) IPCGlobal.CLIENT_EXIT_EVENT.invoker().run();
            ClientWorldLoader.cleanUp();
        }
    }
    @Override public void ip_setFrameBuffer(RenderTarget target) { ((NativeGameRendererAccess)gameRenderer).ip_setNativeTarget(target); }
    @Override public Screen ip_getCurrentScreen() { return ((Minecraft)(Object)this).gui.screen(); }
    @Override public void ip_setWorldRenderer(LevelRenderer renderer) { levelRenderer = renderer; }
    @Override public void ip_setRenderBuffers(RenderBuffers buffers) { ((NativeGameRendererAccess)gameRenderer).ip_setNativeBuffers(buffers); }
    @Override public Thread ip_getRunningThread() { return gameThread; }
    @Override public void ip_setGameRenderer(GameRenderer renderer) { gameRenderer = renderer; }
    @Override public void ip_setLevelExtractor(LevelExtractor extractor) { levelExtractor = extractor; }
    @Override public void ip_setParticleEngine(ParticleEngine particles) { particleEngine = particles; }
}
