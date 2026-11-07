package qouteall.imm_ptl.core.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import org.joml.*;

import qouteall.imm_ptl.core.mixin.client.render.NativeCameraAccess;
import qouteall.q_misc_util.my_util.Plane;

/** A camera owns its projection; clipping never mutates its parent's matrix. */
public final class NativeCamera extends Camera {
    private final ClientLevel world;
    private final boolean caveGraphSafe;
    private final Matrix4f view;
    private final Matrix4f projection;
    private final Frustum frustum;
    private final float fov;
    private final float far;
    public final boolean flipX;

    public NativeCamera(
            ClientLevel world,
            Vec3 position,
            Camera parent,
            Matrix4fc view,
            Matrix4fc projection,
            Plane clip,
            int width,
            int height,
            float far,
            boolean zeroToOne) {
        this.world = world;
        var active =
                qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering()
                        ? qouteall.imm_ptl.core.render.context_management.PortalRendering
                                .getRenderingPortal()
                        : null;
        // The native cave graph assumes a physical, unscaled camera in a connected room.
        // Transformed/volumetric views still use portal frustum and GPU occlusion culling.
        caveGraphSafe =
                active == null
                        || ((active.getRotation() == null
                                        || qouteall.q_misc_util.my_util.DQuaternion.isClose(
                                                active.getRotation(),
                                                qouteall.q_misc_util.my_util.DQuaternion.identity))
                                && !active.hasScaling()
                                && !(active instanceof qouteall.imm_ptl.core.portal.Mirror)
                                && active.getPortalShape().isPlanar());
        this.view = new Matrix4f(view);
        this.projection = new Matrix4f(projection);
        this.flipX = this.view.determinant3x3() < 0;
        if (flipX) this.view.scaleLocal(-1, 1, 1);
        this.fov = parent.getFov();
        this.far = far;
        setLevel(world);
        setEntity(parent.entity());
        setPosition(position);
        // These cameras are recreated for offscreen views and never receive vanilla
        // Camera.tick(). Seed their biome/environment probe before sky and fog extraction.
        attributeProbe().tick(world, position);
        Vector3f forward =
                this.view
                        .invert(new Matrix4f())
                        .transformDirection(new Vector3f(0, 0, -1))
                        .normalize();
        setRotation(
                (float) java.lang.Math.toDegrees(java.lang.Math.atan2(-forward.x, forward.z)),
                (float)
                        -java.lang.Math.toDegrees(
                                java.lang.Math.asin(java.lang.Math.clamp(forward.y, -1, 1))));
        rotation().setFromUnnormalized(this.view).conjugate().normalize();
        // setRotation also passes through Gravity Changer. This camera's view already includes
        // that transform and portal roll: rebuild every axis from its final quaternion.
        var access = (NativeCameraAccess) (Object) this;
        rotation().transform(0, 0, -1, access.ip_forwards());
        rotation().transform(0, 1, 0, access.ip_up());
        rotation().transform(-1, 0, 0, access.ip_left());
        access.ip_panoramicForwards().set(access.ip_forwards());
        ((NativeCameraAccess) (Object) this).ip_setupPerspective(.05f, far, fov, width, height);
        // Visibility uses a finite, conventional depth range. Expanding the native
        // culling frustum with an oblique reverse-Z projection can otherwise loop forever.
        Matrix4f cullProjection =
                new Matrix4f()
                        .perspective(
                                (float) java.lang.Math.toRadians(fov),
                                (float) width / height,
                                .05f,
                                far);
        if (TransformationManager.isIsometricView)
            cullProjection = TransformationManager.getIsometricCullingProjection();
        frustum = new Frustum(this.view, cullProjection);
        frustum.prepare(position.x, position.y, position.z);
        if (clip != null
                && qouteall.imm_ptl.core.IPGlobal.enableClippingMechanism
                && qouteall.imm_ptl.core.IPCGlobal.useFrontClipping)
            clipProjection(this.projection, this.view, position, clip, zeroToOne);
    }

    public static void clipProjection(
            Matrix4f projection, Matrix4fc view, Vec3 camera, Plane plane, boolean zeroToOne) {
        Vec3 relative = plane.pos().subtract(camera);
        Vector4f equation =
                new Vector4f(
                        (float) plane.normal().x,
                        (float) plane.normal().y,
                        (float) plane.normal().z,
                        (float) -plane.normal().dot(relative));
        new Matrix4f(view).invert().transpose().transform(equation);
        Vector4f corner =
                new Vector4f(
                        java.lang.Math.copySign(1, equation.x),
                        java.lang.Math.copySign(1, equation.y),
                        zeroToOne ? 0 : -1,
                        1);
        new Matrix4f(projection).invert().transform(corner);
        float denominator = equation.dot(corner);
        if (!Float.isFinite(denominator) || denominator <= 1e-6f) return;
        equation.mul((zeroToOne ? 1 : 2) / denominator);
        projection
                .m02(projection.m03() - equation.x)
                .m12(projection.m13() - equation.y)
                .m22(projection.m23() - equation.z)
                .m32(projection.m33() - equation.w);
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isDetached() {
        // Match the root camera mode while allowing the portal policy to expose the
        // first-person player. Always returning true bypasses renderYourselfInPortal
        // and the near-mirror head guard in shouldRenderPlayerDefault().
        return !net.minecraft.client.Minecraft.getInstance()
                        .options.getCameraType().isFirstPerson()
                || CrossPortalEntityRenderer.shouldRenderPlayerDefault(this);
    }

    @Override
    public net.minecraft.world.level.material.FogType getFluidInCamera() {
        // The virtual camera can lie in fluid behind the destination clipping plane.
        // Preserve the upstream portal policy instead of fogging the visible remote world.
        return qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering()
                ? net.minecraft.world.level.material.FogType.NONE
                : super.getFluidInCamera();
    }

    @Override
    public float getFov() {
        return fov;
    }

    @Override
    public Frustum getCullFrustum() {
        return frustum;
    }

    @Override
    public Matrix4f getViewRotationMatrix(Matrix4f out) {
        return out.set(view);
    }

    @Override
    public Matrix4f getViewRotationProjectionMatrix(Matrix4f out) {
        return out.set(projection).mul(view);
    }

    @Override
    public Vector3fc forwardVector() {
        return view.invert(new Matrix4f()).transformDirection(new Vector3f(0, 0, -1)).normalize();
    }

    @Override
    public Vector3fc panoramicForwards() {
        return forwardVector();
    }

    @Override
    public Vector3fc upVector() {
        return view.invert(new Matrix4f()).transformDirection(new Vector3f(0, 1, 0)).normalize();
    }

    @Override
    public Vector3fc leftVector() {
        return view.invert(new Matrix4f()).transformDirection(new Vector3f(-1, 0, 0)).normalize();
    }

    @Override
    public void extractRenderState(CameraRenderState state, float partialTick) {
        super.extractRenderState(state, partialTick);
        state.initialized = true;
        state.pos = position();
        state.blockPos = blockPosition();
        state.projectionMatrix.set(projection);
        state.viewRotationMatrix.set(view);
        state.cullFrustum.set(frustum);
        // A transformed camera can lie inside a wall behind the destination aperture.
        // In that case the vanilla cave graph has no usable seed: keep the view conservative.
        state.smartCull =
                MyGameRenderer.enablePortalCaveCulling
                        && caveGraphSafe
                        && net.minecraft.client.Minecraft.getInstance().smartCull
                        && !world.getBlockState(blockPosition()).isSolidRender();
        state.depthFar = far;
        state.orientation.set(rotation());
    }
}
