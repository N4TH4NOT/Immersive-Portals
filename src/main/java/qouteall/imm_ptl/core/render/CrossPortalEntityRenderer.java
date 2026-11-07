package qouteall.imm_ptl.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.*;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.*;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.render.context_management.*;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.q_misc_util.Helper;

public final class CrossPortalEntityRenderer {
    private static final Minecraft client = Minecraft.getInstance();
    public static boolean isRenderingEntityNormally;
    public static boolean isRenderingEntityProjection;
    public static void init() { NativeEntityPass.init(); }
    public static void onEntityTickClient(Entity entity) { /* Collision state is collected during native extraction. */ }
    public static boolean shouldRenderPlayerDefault() {
        return shouldRenderPlayerDefault(client.gameRenderer.mainCamera());
    }

    public static boolean shouldRenderPlayerDefault(Camera camera) {
        if (!IPGlobal.renderYourselfInPortal) {
            return false;
        }
        if (!WorldRenderInfo.isRendering()) {
            return false;
        }
        LocalPlayer player = client.player;
        assert player != null;
        
        return client.level == player.level() && shouldRenderLocalPlayerBody(
            player, camera, RenderStates.getPartialTick(), null
        );
    }

    /** Retain remote views of oneself while excluding a model surrounding a first-person eye. */
    public static boolean shouldRenderLocalPlayerBody(
        LocalPlayer player, Camera camera, float partial, @org.jetbrains.annotations.Nullable Portal projection
    ) {
        if (!client.options.getCameraType().isFirstPerson()) return true;
        Vec3 position = new Vec3(
            Mth.lerp(partial, player.xOld, player.getX()),
            Mth.lerp(partial, player.yOld, player.getY()),
            Mth.lerp(partial, player.zOld, player.getZ())
        );
        AABB box = player.getBoundingBox().move(position.subtract(player.position()));
        double scale = ScaleUtils.getScale(player);
        if (projection != null) {
            box = Helper.transformBox(box, projection::transformPoint);
            scale *= projection.getScale();
        }
        return NativePlayerVisibility.canShowBody(box, camera.position(), .05 * scale);
    }
    
    public static boolean shouldRenderEntityNow(Entity entity) {
        Validate.notNull(entity);
        if (IrisInterface.invoker.isRenderingShadowMap()) {
            return true;
        }
        if (entity instanceof LocalPlayer player && WorldRenderInfo.isRendering()
            && client.options.getCameraType().isFirstPerson()
            && (!IPGlobal.renderYourselfInPortal || !shouldRenderLocalPlayerBody(
                player, client.gameRenderer.mainCamera(), RenderStates.getPartialTick(), null
            ))) {
            return false;
        }
        if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            Portal collidingPortal = ((IEEntity) entity).ip_getCollidingPortal();
            
            if (entity instanceof Player && !renderingPortal.getDoRenderPlayer()) {
                return false;
            }
            
            // client colliding portal update is not immediate
            if (collidingPortal != null && !(entity instanceof LocalPlayer)) {
                if (renderingPortal instanceof Portal) {
                    if (!Portal.isReversePortal(collidingPortal, ((Portal) renderingPortal))) {
                        Vec3 cameraPos = PortalRenderer.client.gameRenderer.mainCamera().position();
                        
                        boolean isHidden = cameraPos.subtract(collidingPortal.getOriginPos())
                            .dot(collidingPortal.getNormal()) < 0;
                        if (isHidden) {
                            return false;
                        }
                    }
                }
            }
            
            return renderingPortal.isOnDestinationSide(
                getRenderingCameraPos(entity), -0.01
            );
        }
        return true;
    }
    
    public static boolean shouldRenderPlayerNormally(Entity entity) {
        return !(entity instanceof LocalPlayer player) || shouldRenderLocalPlayerBody(
            player, client.gameRenderer.mainCamera(), RenderStates.getPartialTick(), null
        );
    }
    
    public static Vec3 getRenderingCameraPos(Entity entity) {
        if (entity instanceof LocalPlayer) {
            return RenderStates.originalPlayerPos.add(
                McHelper.getEyeOffset(entity)
            );
        }
        return entity.getEyePosition(RenderStates.getPartialTick());
    }
}
