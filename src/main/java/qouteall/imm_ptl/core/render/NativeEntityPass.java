package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.ProjectionType;
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

    private record Body(
            EntityRenderState state, Matrix4f transform, Plane clip, boolean projected) {}

    private static final Map<LevelRenderState, List<Body>> BODIES = new IdentityHashMap<>();
    private static final Minecraft CLIENT = Minecraft.getInstance();

    private record Context(
            net.minecraft.client.renderer.RenderBuffers buffers,
            net.minecraft.client.renderer.feature.FeatureRenderDispatcher dispatcher)
            implements AutoCloseable {
        @Override
        public void close() {
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
                    if (entity instanceof LocalPlayer) {
                        if (projected
                                && (!IPGlobal.renderYourselfInPortal
                                        || !portal.getDoRenderPlayer())) continue;
                        if (!projected && entity == camera.entity() && !camera.isDetached())
                            continue;
                    }
                    if (!projected && !CrossPortalEntityRenderer.shouldRenderEntityNow(entity))
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
                    if (PortalRendering.isRendering())
                        clip = PortalRendering.getActiveClippingPlane();
                    bodies.add(new Body(state, transform, clip, projected));
                }
            }
    }

    public static void render(LevelRenderState state) {
        List<Body> bodies = BODIES.get(state);
        if (bodies == null || bodies.isEmpty()) return;
        var oldProjection = RenderSystem.getProjectionMatrixBuffer();
        var oldProjectionType = RenderSystem.getProjectionType();
        var camera = state.cameraRenderState;
        Matrix4f base = NativeWorldRenderer.projection(CLIENT.gameRenderer);
        try (ProjectionMatrixBuffer buffer = new ProjectionMatrixBuffer("Portal entity clipping")) {
            for (Body body : bodies) {
                Matrix4f projection = new Matrix4f(base);
                if (body.clip != null && !PortalRendering.isRendering()) {
                    NativeCamera.clipProjection(
                            projection,
                            camera.viewRotationMatrix,
                            camera.pos,
                            body.clip,
                            RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
                }
                RenderSystem.setProjectionMatrix(
                        buffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
                SubmitNodeStorage storage = new SubmitNodeStorage();
                PoseStack pose = new PoseStack();
                double x = body.state.x - camera.pos.x,
                        y = body.state.y - camera.pos.y,
                        z = body.state.z - camera.pos.z;
                pose.translate(x, y, z);
                pose.mulPose(body.transform);
                pose.translate(-x, -y, -z);
                CrossPortalEntityRenderer.isRenderingEntityProjection = body.projected;
                CrossPortalEntityRenderer.isRenderingEntityNormally = !body.projected;
                CLIENT.getEntityRenderDispatcher()
                        .submit(body.state, camera, x, y, z, pose, storage);
                try (var features = context().dispatcher().prepareFrame(storage)) {
                    features.executeSolid();
                    features.executeTranslucent();
                    features.executeTranslucentAfterTerrain();
                    features.executeOutline();
                }
                context().buffers().endFrame();
            }
        } finally {
            CrossPortalEntityRenderer.isRenderingEntityProjection = false;
            CrossPortalEntityRenderer.isRenderingEntityNormally = false;
            RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
        }
    }
}
