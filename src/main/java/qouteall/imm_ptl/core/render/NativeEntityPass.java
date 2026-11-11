package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import qouteall.imm_ptl.core.*;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.my_util.Plane;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws crossing bodies in clipped native feature passes, including arbitrary portal rotations and
 * scale.
 */
public final class NativeEntityPass {
    private NativeEntityPass() {}

    private record Body(EntityRenderState state, Matrix4f transform, Plane clip, Plane viewClip,
            boolean projected, int entityId, boolean localPlayer) {}

    public record BodySnapshot(int entityId, boolean projected, boolean localPlayer,
            Vec3 position, Plane clip, Plane viewClip) {}

    /** Immutable extraction metadata for graphics regression observers. */
    public static List<BodySnapshot> snapshots(LevelRenderState state) {
        return BODIES.getOrDefault(state, List.of()).stream().map(body -> new BodySnapshot(
            body.entityId, body.projected, body.localPlayer,
            new Vec3(body.state.x, body.state.y, body.state.z), body.clip, body.viewClip
        )).toList();
    }

    private static final Map<LevelRenderState, List<Body>> BODIES = new IdentityHashMap<>();
    private static final Minecraft CLIENT = Minecraft.getInstance();

    private static final class Context implements AutoCloseable {
        final net.minecraft.client.renderer.RenderBuffers buffers;
        final net.minecraft.client.renderer.feature.FeatureRenderDispatcher dispatcher;
        private TextureTarget clippedBody;

        Context(net.minecraft.client.renderer.RenderBuffers buffers,
                net.minecraft.client.renderer.feature.FeatureRenderDispatcher dispatcher) {
            this.buffers = buffers;
            this.dispatcher = dispatcher;
        }

        TextureTarget target(RenderTarget parent) {
            if (clippedBody != null
                    && clippedBody.getColorTexture().getFormat() != parent.getColorTexture().getFormat()) {
                clippedBody.destroyBuffers();
                clippedBody = null;
            }
            if (clippedBody == null)
                clippedBody = new TextureTarget("Portal body features", parent.width, parent.height,
                    true, parent.getColorTexture().getFormat());
            else if (clippedBody.width != parent.width || clippedBody.height != parent.height)
                clippedBody.resize(parent.width, parent.height);
            return clippedBody;
        }

        @Override
        public void close() {
            if (clippedBody != null) clippedBody.destroyBuffers();
            dispatcher.close();
            buffers.close();
        }
    }

    private static final Map<net.minecraft.client.renderer.state.GameRenderState, Context>
            CONTEXTS = new IdentityHashMap<>();

    public static void release(net.minecraft.client.renderer.state.GameRenderState state) {
        BODIES.remove(state.levelRenderState);
        Context context = CONTEXTS.remove(state);
        if (context != null) context.close();
    }

    public static void init() {
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(
                () -> {
                    BODIES.clear();
                    CONTEXTS.values().forEach(Context::close);
                    CONTEXTS.clear();
                });
    }

    private static Context context() {
        return CONTEXTS.computeIfAbsent(
                CLIENT.gameRenderer.gameRenderState(),
                state -> {
                    var buffers = new net.minecraft.client.renderer.RenderBuffers(1);
                    return new Context(
                            buffers,
                            new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(
                                    buffers,
                                    CLIENT.getModelManager(),
                                    CLIENT.getAtlasManager(),
                                    CLIENT.font,
                                    state));
                });
    }

    public static void extract(LevelRenderState output, Camera camera, float partial) {
        List<Body> bodies = new ArrayList<>();
        BODIES.put(output, bodies);
        if (!IPGlobal.correctCrossPortalEntityRendering
                || !ClientWorldLoader.getIsInitialized()) return;
        for (var world : ClientWorldLoader.getClientWorlds())
            for (Entity entity : world.entitiesForRendering()) {
                if (entity instanceof Portal || entity.isRemoved()) continue;
                Portal portal = ((IEEntity) entity).ip_getCollidingPortal();
                if (portal == null || !portal.isPortalValid()) continue;
                for (int side = 0; side < 2; side++) {
                    boolean projected = side == 1;
                    if ((!projected && world != CLIENT.level)
                            || (projected && portal.getDestDim() != CLIENT.level.dimension()))
                        continue;
                    if (entity instanceof LocalPlayer player) {
                        if (projected
                                && (!IPGlobal.renderYourselfInPortal
                                        || !portal.getDoRenderPlayer())) continue;
                        if (!projected && entity == camera.entity() && !camera.isDetached())
                            continue;
                        if (!CrossPortalEntityRenderer.shouldRenderLocalPlayerBody(
                                player, camera, partial, projected ? portal : null)) continue;
                    }
                    if (entity instanceof net.minecraft.world.entity.player.Player
                            && PortalRendering.isRendering()
                            && !PortalRendering.getRenderingPortal().getDoRenderPlayer())
                        continue;
                    var box =
                            projected
                                    ? qouteall.q_misc_util.Helper.transformBox(
                                            entity.getBoundingBox(), portal::transformPoint)
                                    : entity.getBoundingBox();
                    if (!camera.getCullFrustum().isVisible(box)) continue;
                    EntityRenderState state =
                            CLIENT.getEntityRenderDispatcher().extractEntity(entity, partial);
                    Matrix4f transform = new Matrix4f();
                    Plane clip;
                    if (projected) {
                        Vec3 position = portal.transformPoint(new Vec3(state.x, state.y, state.z));
                        state.x = position.x;
                        state.y = position.y;
                        state.z = position.z;
                        Vec3 x =
                                portal.transformLocalVecNonScale(Vec3.X_AXIS)
                                        .scale(portal.getScale());
                        Vec3 y =
                                portal.transformLocalVecNonScale(Vec3.Y_AXIS)
                                        .scale(portal.getScale());
                        Vec3 z =
                                portal.transformLocalVecNonScale(Vec3.Z_AXIS)
                                        .scale(portal.getScale());
                        transform
                                .m00((float) x.x)
                                .m01((float) x.y)
                                .m02((float) x.z)
                                .m10((float) y.x)
                                .m11((float) y.y)
                                .m12((float) y.z)
                                .m20((float) z.x)
                                .m21((float) z.y)
                                .m22((float) z.z);
                        state.boundingBoxWidth *= (float) portal.getScale();
                        state.boundingBoxHeight *= (float) portal.getScale();
                        clip = portal.getInnerClipping();
                    } else
                        clip = portal.getPortalShape().getOuterClipping(portal.getThisSideState());
                    Plane viewClip = PortalRendering.isRendering()
                        ? PortalRendering.getActiveClippingPlane() : null;
                    bodies.add(new Body(state, transform, clip, viewClip, projected,
                        entity.getId(), entity instanceof LocalPlayer));
                }
            }
    }

    public static void render(LevelRenderState state) {
        if (qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface.invoker.isRenderingShadowMap())
            return;
        List<Body> bodies = BODIES.get(state);
        if (bodies == null || bodies.isEmpty()) return;
        var oldProjection = RenderSystem.getProjectionMatrixBuffer();
        var oldProjectionType = RenderSystem.getProjectionType();
        var oldColorTarget = RenderSystem.outputColorTextureOverride;
        var oldDepthTarget = RenderSystem.outputDepthTextureOverride;
        var camera = state.cameraRenderState;
        Matrix4f base = NativeWorldRenderer.projection(CLIENT.gameRenderer);
        try (ProjectionMatrixBuffer buffer = new ProjectionMatrixBuffer("Portal entity clipping")) {
            qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface.invoker.withCompositeTarget(
                CLIENT.gameRenderer.mainRenderTarget(), target -> {
                    var context = context();
                    var bodyTarget = context.target(target);
                    for (Body body : bodies) {
                        // A near-plane replacement cannot clip a body when the camera lies
                        // in the half-space being kept, or represent two independent planes.
                        // Keep native perspective/depth and clip reconstructed fragments instead.
                        RenderSystem.getDevice().createCommandEncoder()
                            .clearColorTexture(bodyTarget.getColorTexture(), new Vector4f(0, 0, 0, 0));
                        bodyTarget.copyDepthFrom(target);
                        RenderSystem.outputColorTextureOverride = bodyTarget.getColorTextureView();
                        RenderSystem.outputDepthTextureOverride = bodyTarget.getDepthTextureView();
                        RenderSystem.setProjectionMatrix(buffer.getBuffer(base), ProjectionType.PERSPECTIVE);
                        SubmitNodeStorage storage = new SubmitNodeStorage();
                        PoseStack pose = new PoseStack();
                        double x = body.state.x - camera.pos.x,
                            y = body.state.y - camera.pos.y, z = body.state.z - camera.pos.z;
                        pose.translate(x, y, z);
                        pose.mulPose(body.transform);
                        pose.translate(-x, -y, -z);
                        CrossPortalEntityRenderer.isRenderingEntityProjection = body.projected;
                        CrossPortalEntityRenderer.isRenderingEntityNormally = !body.projected;
                        CLIENT.getEntityRenderDispatcher()
                            .submit(body.state, camera, x, y, z, pose, storage);
                        try (var features = context.dispatcher.prepareFrame(storage)) {
                            features.executeSolid();
                            features.executeTranslucent();
                            features.executeTranslucentAfterTerrain();
                            features.executeOutline();
                        }
                        context.buffers.endFrame();
                        RenderSystem.outputColorTextureOverride = oldColorTarget;
                        RenderSystem.outputDepthTextureOverride = oldDepthTarget;
                        NativeBodyCompositor.draw(bodyTarget, target, base, camera.viewRotationMatrix,
                            camera.pos, body.clip, body.viewClip);
                    }
                });
        } finally {
            CrossPortalEntityRenderer.isRenderingEntityProjection = false;
            CrossPortalEntityRenderer.isRenderingEntityNormally = false;
            RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
            RenderSystem.outputColorTextureOverride = oldColorTarget;
            RenderSystem.outputDepthTextureOverride = oldDepthTarget;
        }
    }
}
