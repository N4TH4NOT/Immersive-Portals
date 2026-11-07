package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.impl.client.rendering.LevelRendererExtensions;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import qouteall.imm_ptl.core.ducks.IEClientPlayNetworkHandler;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.mixin.client.particle.NativeParticleEngineAccess;
import qouteall.imm_ptl.core.mixin.client.render.NativeGameRendererAccess;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Complete native extraction/rendering contexts, scoped and restored for nested worlds. */
public final class NativeWorldRenderer {
    private NativeWorldRenderer() {}

    private static final Minecraft CLIENT = Minecraft.getInstance();

    private record Layer(ResourceKey<Level> dimension, int depth) {}

    private record SceneKey(ResourceKey<Level> dimension, int depth, int view) {}

    private static final Map<SceneKey, Scene> SCENES = new HashMap<>();
    private static final Map<Layer, Integer> VIEW_COUNTS = new HashMap<>();
    private static final Map<ClientLevel, ParticleEngine> PARTICLES = new IdentityHashMap<>();
    private static final Map<GameRenderer, Matrix4f> PROJECTIONS = new IdentityHashMap<>();
    private static final Map<GameRenderer, Object> VISIBILITY_TOKENS = new IdentityHashMap<>();

    public static Object visibilityToken(GameRenderer renderer) {
        return VISIBILITY_TOKENS.computeIfAbsent(renderer, ignored -> new Object());
    }

    private static int activeScenes;
    private static boolean lastFlipX;
    private static Matrix4f lastProjection = new Matrix4f();
    private static Matrix4f rootCameraProjection;
    private static boolean propagatingDirty;
    private static boolean reloading;

    public static final class Scene implements AutoCloseable {
        public ClientLevel world;
        public final GameRenderer gameRenderer;
        public final LevelRenderer renderer;
        public final LevelExtractor extractor;
        private final boolean borrowed;
        private final boolean ownsGameRenderer;
        private long lastUsedFrame;
        private boolean needsEndFrame;
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet loaded =
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet unloaded =
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet addedEmpty =
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet removedEmpty =
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

        private Scene(ClientLevel world, boolean borrowed) {
            this(world, borrowed, false);
        }

        private Scene(ClientLevel world, boolean borrowed, boolean primary) {
            this.world = world;
            this.borrowed = borrowed;
            this.ownsGameRenderer = !borrowed && !primary;
            if (borrowed) {
                gameRenderer = CLIENT.gameRenderer;
                renderer = CLIENT.levelRenderer;
                extractor = CLIENT.levelExtractor;
                seedLoadedChunks();
            } else {
                gameRenderer =
                        primary
                                ? CLIENT.gameRenderer
                                : new GameRenderer(
                                        CLIENT,
                                        new ItemInHandRenderer(
                                                CLIENT,
                                                CLIENT.getEntityRenderDispatcher(),
                                                CLIENT.getItemModelResolver()),
                                        CLIENT.getModelManager());
                renderer =
                        new LevelRenderer(
                                CLIENT.getEntityRenderDispatcher(),
                                CLIENT.getBlockEntityRenderDispatcher(),
                                CLIENT.getModelManager(),
                                CLIENT.getTextureManager(),
                                CLIENT.getAtlasManager(),
                                CLIENT.getShaderManager(),
                                gameRenderer,
                                CLIENT.getWindow().getWidth(),
                                CLIENT.getWindow().getHeight());
                extractor =
                        new LevelExtractor(
                                CLIENT, gameRenderer.gameRenderState().levelRenderState, renderer);
                if (world != null) attach(world);
            }
        }

        public void attach(ClientLevel world) {
            this.world = world;
            withContext(
                    this,
                    () -> {
                        if (ownsGameRenderer) gameRenderer.setLevel(world);
                        extractor.setLevel(world);
                        return null;
                    });
            seedLoadedChunks();
        }

        private void seedLoadedChunks() {
            if (world != null && world.getChunkSource()
                    instanceof qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap chunks) {
                chunks.readChunkMap(
                        map -> {
                            loaded.addAll(map.keySet());
                            return null;
                        });
            }
        }

        @Override
        public void close() {
            PROJECTIONS.remove(gameRenderer);
            VISIBILITY_TOKENS.remove(gameRenderer);
            if (ownsGameRenderer || renderer != CLIENT.levelRenderer) {
                withContext(
                        this,
                        () -> {
                            extractor.setLevel(null);
                            renderer.close();
                            if (ownsGameRenderer) {
                                NativeEntityPass.release(gameRenderer.gameRenderState());
                                qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface
                                        .invoker
                                        .closeContext(gameRenderer);
                                gameRenderer.close();
                            }
                            return null;
                        });
            }
        }
    }

    public static Scene createPrimaryScene() {
        return new Scene(null, false, true);
    }

    public static void registerPrimary(ClientLevel world, Scene scene) {
        scene.world = world;
        SCENES.put(new SceneKey(world.dimension(), 0, 0), scene);
    }

    public static void registerCurrentWorld() {
        Scene previous = SCENES.put(new SceneKey(CLIENT.level.dimension(), 0, 0), new Scene(CLIENT.level, true));
        if (previous != null && previous.renderer != CLIENT.levelRenderer) previous.close();
        PARTICLES.put(CLIENT.level, CLIENT.particleEngine);
    }

    public static Scene primary(ClientLevel world) {
        return SCENES.computeIfAbsent(
                new SceneKey(world.dimension(), 0, 0), ignored -> new Scene(world, world == CLIENT.level, true));
    }

    public static boolean isRenderingScene() {
        return activeScenes != 0;
    }

    public static boolean lastFlipX() {
        return lastFlipX;
    }

    public static Matrix4f lastProjection() {
        return new Matrix4f(lastProjection);
    }

    public static Matrix4f projection(GameRenderer renderer) {
        Matrix4f value = PROJECTIONS.get(renderer);
        return value == null
                ? new Matrix4f(
                        renderer.gameRenderState()
                                .levelRenderState
                                .cameraRenderState
                                .projectionMatrix)
                : new Matrix4f(value);
    }

    public static void onProjection(GameRenderer renderer, Matrix4fc projection) {
        PROJECTIONS.put(renderer, new Matrix4f(projection));
        if (!isRenderingScene()) {
            RenderStates.basicProjectionMatrix = new Matrix4f(projection);
            rootCameraProjection =
                    new Matrix4f(
                            renderer.gameRenderState()
                                    .levelRenderState
                                    .cameraRenderState
                                    .projectionMatrix);
        }
    }

    public static ParticleEngine particles(ClientLevel world) {
        return PARTICLES.computeIfAbsent(
                world,
                w ->
                        new ParticleEngine(
                                w,
                                ((NativeParticleEngineAccess) CLIENT.particleEngine)
                                        .ip_resources()));
    }

    public static <T> T withWorld(ClientLevel world, Supplier<T> operation) {
        return withContext(primary(world), operation);
    }

    private static <T> T withContext(Scene scene, Supplier<T> operation) {
        var access = (IEMinecraftClient) CLIENT;
        ClientLevel oldWorld = CLIENT.level;
        GameRenderer oldGame = CLIENT.gameRenderer;
        LevelRenderer oldRenderer = CLIENT.levelRenderer;
        LevelExtractor oldExtractor = CLIENT.levelExtractor;
        ParticleEngine oldParticles = CLIENT.particleEngine;
        var listener = CLIENT.getConnection();
        ClientLevel oldNetworkWorld = listener == null ? null : listener.getLevel();
        Runnable restoreIris =
                qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface.invoker.pushContext(
                        scene.gameRenderer, scene.ownsGameRenderer);
        try {
            CLIENT.level = scene.world;
            access.ip_setGameRenderer(scene.gameRenderer);
            access.ip_setWorldRenderer(scene.renderer);
            access.ip_setLevelExtractor(scene.extractor);
            if (scene.world != null) access.ip_setParticleEngine(particles(scene.world));
            if (listener != null) ((IEClientPlayNetworkHandler) listener).ip_setWorld(scene.world);
            return operation.get();
        } finally {
            restoreIris.run();
            CLIENT.level = oldWorld;
            access.ip_setGameRenderer(oldGame);
            access.ip_setWorldRenderer(oldRenderer);
            access.ip_setLevelExtractor(oldExtractor);
            access.ip_setParticleEngine(oldParticles);
            if (listener != null)
                ((IEClientPlayNetworkHandler) listener).ip_setWorld(oldNetworkWorld);
            CLIENT.getEntityRenderDispatcher()
                    .prepare(oldGame.mainCamera(), CLIENT.crosshairPickEntity);
            CLIENT.getBlockEntityRenderDispatcher().prepare(oldGame.mainCamera().position());
        }
    }

    public static RenderTarget render(WorldRenderInfo info) {
        Layer layer =
                new Layer(info.world.dimension(), Math.max(1, WorldRenderInfo.getRenderingLayer()));
        int viewIndex = VIEW_COUNTS.merge(layer, 1, Integer::sum) - 1;
        // A native camera owns mutable projection, fog and lightmap buffers. Reusing it for
        // another view before GPU submission can overwrite the first view's uniforms.
        Scene scene =
                SCENES.computeIfAbsent(
                        new SceneKey(layer.dimension(), layer.depth(), viewIndex),
                        ignored -> new Scene(info.world, false));
        scene.lastUsedFrame = RenderStates.frameIndex;
        scene.needsEndFrame = true;
        Camera parent = CLIENT.gameRenderer.mainCamera();
        Matrix4f baseProjection =
                RenderStates.basicProjectionMatrix == null
                        ? projection(CLIENT.gameRenderer)
                        : new Matrix4f(RenderStates.basicProjectionMatrix);
        if (!WorldRenderInfo.isViewBobbingEnabled() && rootCameraProjection != null)
            baseProjection = new Matrix4f(rootCameraProjection);
        PoseStack view = new PoseStack();
        view.mulPose(RenderStates.originalCamera.getViewRotationMatrix(new Matrix4f()));
        WorldRenderInfo.applyAdditionalTransformations(view);
        NativeCamera camera =
                new NativeCamera(
                        info.world,
                        info.cameraPos,
                        parent,
                        view.last().pose(),
                        baseProjection,
                        PortalRendering.isRendering()
                                ? PortalRendering.getActiveClippingPlane()
                                : null,
                        CLIENT.getWindow().getWidth(),
                        CLIENT.getWindow().getHeight(),
                        Math.max(64, info.renderDistance * 16),
                        RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        GpuBufferSlice oldProjection = RenderSystem.getProjectionMatrixBuffer();
        var oldProjectionType = RenderSystem.getProjectionType();
        var oldFog = RenderSystem.getShaderFog();
        var oldLights = RenderSystem.getShaderLights();
        var oldGlobals = RenderSystem.getGlobalSettingsUniform();
        var oldColorTarget = RenderSystem.outputColorTextureOverride;
        var oldDepthTarget = RenderSystem.outputDepthTextureOverride;
        RenderSystem.getModelViewStack().pushMatrix().identity();
        activeScenes++;
        try {
            return withContext(
                    scene,
                    () -> {
                        NativeGameRendererAccess access =
                                (NativeGameRendererAccess) scene.gameRenderer;
                        access.ip_setNativeCamera(camera);
                        access.ip_extractWindow();
                        access.ip_extractOptions();
                        var state = scene.gameRenderer.gameRenderState();
                        state.optionsRenderState.bobView = false;
                        state.optionsRenderState.screenEffectScale = 0;
                        state.optionsRenderState.renderDistance = info.renderDistance;
                        var delta = CLIENT.getDeltaTracker();
                        float partial = delta.getGameTimeDeltaPartialTick(true);
                        access.ip_lightmapExtractor().extract(state.lightmapRenderState, partial);
                        access.ip_extractCamera(delta, partial, partial);
                        state.levelRenderState.cameraRenderState.entityRenderState.isLiving = false;
                        // Direct offscreen extraction bypasses Fabric's GameRenderer.extract hook.
                        // Prepare this scene's context after installing its world, renderer and camera.
                        ((LevelRendererExtensions) scene.extractor)
                                .fabric_prepareLevelExtractionContext(delta);
                        scene.extractor.extract(delta, camera, partial);
                        if (scene.gameRenderer.mainRenderTarget().width
                                        != state.windowRenderState.width
                                || scene.gameRenderer.mainRenderTarget().height
                                        != state.windowRenderState.height) {
                            scene.gameRenderer.resize(
                                    state.windowRenderState.width, state.windowRenderState.height);
                        }
                        access.ip_lightmap().render(state.lightmapRenderState);
                        access.ip_globals()
                                .update(
                                        state.windowRenderState.width,
                                        state.windowRenderState.height,
                                        state.optionsRenderState.glintStrength,
                                        info.world.getGameTime(),
                                        delta,
                                        state.optionsRenderState.menuBackgroundBlurriness,
                                        info.cameraPos,
                                        false);
                        scene.gameRenderer.renderLevel(delta);
                        if (!info.doRenderHand)
                            qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface.invoker
                                    .finishOffscreen();
                        scene.gameRenderer.renderBuffers().endFrame();
                        return scene.gameRenderer.mainRenderTarget();
                    });
        } finally {
            activeScenes--;
            lastFlipX = camera.flipX;
            lastProjection = projection(scene.gameRenderer);
            RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
            RenderSystem.setShaderFog(oldFog);
            RenderSystem.setShaderLights(oldLights);
            RenderSystem.setGlobalSettingsUniform(oldGlobals);
            RenderSystem.outputColorTextureOverride = oldColorTarget;
            RenderSystem.outputDepthTextureOverride = oldDepthTarget;
            RenderSystem.getModelViewStack().popMatrix();
        }
    }

    public static void reload() {
        if (reloading) return;
        NativePortalOcclusion.close();
        reloading = true;
        try {
            SCENES.values()
                    .forEach(
                            scene ->
                                    withContext(
                                            scene,
                                            () -> {
                                                scene.extractor.onResourceManagerReload(
                                                        CLIENT.getResourceManager());
                                                return null;
                                            }));
        } finally {
            reloading = false;
        }
    }

    /** Advance native ring buffers once per submitted frame, after every portal and GUI view. */
    public static void endFrame() {
        NativePortalOcclusion.endFrame();
        VIEW_COUNTS.clear();
        for (Scene scene : SCENES.values())
            if (scene.needsEndFrame) {
                scene.needsEndFrame = false;
                withContext(
                        scene,
                        () -> {
                            scene.renderer.endFrame();
                            var access = (NativeGameRendererAccess) scene.gameRenderer;
                            access.ip_fogRenderer().endFrame();
                            access.ip_resourcePool().endFrame();
                            return null;
                        });
            }
        SCENES.entrySet()
                .removeIf(
                        entry -> {
                            Scene scene = entry.getValue();
                            if (scene.ownsGameRenderer
                                    && RenderStates.frameIndex - scene.lastUsedFrame > 120) {
                                scene.close();
                                return true;
                            }
                            return false;
                        });
        var idle =
                SCENES.entrySet().stream()
                        .filter(
                                entry ->
                                        entry.getValue().ownsGameRenderer
                                                && entry.getValue().lastUsedFrame
                                                        < RenderStates.frameIndex)
                        .sorted(
                                java.util.Comparator.comparingLong(
                                        entry -> entry.getValue().lastUsedFrame))
                        .toList();
        int excess =
                idle.size() - Math.max(0, qouteall.imm_ptl.core.IPCGlobal.maxIdleChunkRendererNum);
        for (int i = 0; i < excess; i++) {
            var entry = idle.get(i);
            SCENES.remove(entry.getKey());
            entry.getValue().close();
        }
    }

    /** Shaderpack reloads invalidate the pipelines owned by offscreen renderers as well. */
    public static void resetOffscreen() {
        if (isRenderingScene())
            throw new IllegalStateException("Cannot reload shaders during a portal render");
        NativePortalOcclusion.close();
        SCENES.entrySet()
                .removeIf(
                        entry -> {
                            if (entry.getValue().ownsGameRenderer) {
                                entry.getValue().close();
                                return true;
                            }
                            return false;
                        });
    }

    public static void chunkChanged(ClientLevel world, long position, boolean loaded) {
        boolean previous = propagatingDirty;
        propagatingDirty = true;
        try {
            for (Scene scene : SCENES.values())
                if (scene.world == world) {
                    (loaded ? scene.loaded : scene.unloaded).add(position);
                    (loaded ? scene.unloaded : scene.loaded).remove(position);
                    int x = net.minecraft.world.level.ChunkPos.getX(position),
                            z = net.minecraft.world.level.ChunkPos.getZ(position);
                    withContext(
                            scene,
                            () -> {
                                for (int y = world.getMinSectionY();
                                        y < world.getMaxSectionY();
                                        y++) scene.extractor.setSectionDirtyWithNeighbors(x, y, z);
                                return null;
                            });
                }
        } finally {
            propagatingDirty = previous;
        }
    }

    public static void applyChunkChanges(
            LevelExtractor extractor,
            net.minecraft.client.renderer.state.level.LevelRenderState state) {
        for (Scene scene : SCENES.values())
            if (scene.extractor == extractor) {
                // The native cache alternates two delta buffers after each extraction.
                // Recursive views may extract the same world again before this view renders.
                // Keep each view's input independent of the next flip/clear.
                ChunkRenderUpdates.snapshot(state.chunkLoadingRenderState);
                state.chunkLoadingRenderState.addedLoadedChunks.addAll(scene.loaded);
                state.chunkLoadingRenderState.removedLoadedChunks.addAll(scene.unloaded);
                state.chunkLoadingRenderState.addedEmptySections.addAll(scene.addedEmpty);
                state.chunkLoadingRenderState.removedEmptySections.addAll(scene.removedEmpty);
                for (long position : scene.unloaded)
                    for (int y = scene.world.getMinSectionY(); y < scene.world.getMaxSectionY(); y++)
                        setSectionEmpty(state, net.minecraft.core.SectionPos.asLong(
                                net.minecraft.world.level.ChunkPos.getX(position), y,
                                net.minecraft.world.level.ChunkPos.getZ(position)), false);
                for (long position : scene.loaded) {
                    var chunk =
                            scene.world
                                    .getChunkSource()
                                    .getChunk(
                                            net.minecraft.world.level.ChunkPos.getX(position),
                                            net.minecraft.world.level.ChunkPos.getZ(position),
                                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                            false);
                    if (chunk != null)
                        for (int i = 0; i < chunk.getSectionsCount(); i++)
                            setSectionEmpty(state, net.minecraft.core.SectionPos.asLong(
                                    chunk.getPos().x(), scene.world.getMinSectionY() + i,
                                    chunk.getPos().z()), chunk.getSection(i).hasOnlyAir());
                }
                scene.loaded.clear();
                scene.unloaded.clear();
                scene.addedEmpty.clear();
                scene.removedEmpty.clear();
                return;
            }
    }

    private static void setSectionEmpty(net.minecraft.client.renderer.state.level.LevelRenderState state,
            long section, boolean empty) {
        ChunkRenderUpdates.setEmpty(state.chunkLoadingRenderState, section, empty);
    }

    public static void sectionEmptinessChanged(ClientLevel world, int x, int y, int z, boolean empty) {
        long section = net.minecraft.core.SectionPos.asLong(x, y, z);
        for (Scene scene : SCENES.values()) if (scene.world == world) {
            (empty ? scene.addedEmpty : scene.removedEmpty).add(section);
            (empty ? scene.removedEmpty : scene.addedEmpty).remove(section);
        }
    }

    public static void markSectionDirty(
            LevelExtractor source, ClientLevel world, int x, int y, int z) {
        markSectionRangeDirty(source, world, x, y, z, x, y, z);
    }

    public static void markSectionRangeDirty(
            LevelExtractor source, ClientLevel world,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        RenderDiagnostics.chunkDirtyBroadcast(world, propagatingDirty || reloading);
        if (propagatingDirty || reloading) return;
        propagatingDirty = true;
        try {
            for (Scene scene : SCENES.values())
                if (scene.world == world && scene.extractor != source)
                    withContext(
                            scene,
                            () -> {
                                RenderDiagnostics.chunkDirtyTarget(world, scene.extractor,
                                        scene.renderer, minX, minY, minZ, maxX, maxY, maxZ);
                                scene.extractor.setSectionRangeDirty(
                                        minX, minY, minZ, maxX, maxY, maxZ);
                                return null;
                            });
        } finally {
            propagatingDirty = false;
        }
    }

    public static void remove(ResourceKey<Level> dimension) {
        SCENES.entrySet()
                .removeIf(
                        entry -> {
                            if (entry.getKey().dimension.equals(dimension)) {
                                entry.getValue().close();
                                return true;
                            }
                            return false;
                        });
        PARTICLES.keySet().removeIf(world -> world.dimension().equals(dimension));
    }

    public static void close() {
        NativePortalOcclusion.close();
        SCENES.values().forEach(Scene::close);
        CLIENT.levelExtractor.setLevel(null);
        SCENES.clear();
        PARTICLES.clear();
        PROJECTIONS.clear();
        VISIBILITY_TOKENS.clear();
        VIEW_COUNTS.clear();
    }
}
