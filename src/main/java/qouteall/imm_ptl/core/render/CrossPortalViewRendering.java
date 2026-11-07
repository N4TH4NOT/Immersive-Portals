package qouteall.imm_ptl.core.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

// in third person view it may render cross portal.
// if view bobbing make the camera go through portal before the actual player go through portal,
//  it will also render cross-portal
public class CrossPortalViewRendering {
    public static final Minecraft client = Minecraft.getInstance();
    private static java.util.List<java.util.UUID> cameraPortalPath = java.util.List.of();

    /** Portals crossed by the view camera before its independently moving physical entity. */
    public static java.util.List<java.util.UUID> getCameraPortalPath() {
        return cameraPortalPath;
    }
    
    // if rendered, return true
    public static boolean renderCrossPortalView() {
        if (!IPGlobal.enableCrossPortalView) {
            return false;
        }
        
        Entity cameraEntity = client.getCameraEntity();
        
        Camera camera=RenderStates.originalCamera;

        Vec3 realCameraPos = camera.position();
        Vec3 isometricAdjustedOriginalCameraPos =
            TransformationManager.getIsometricAdjustedCameraPos(camera);
        
        Vec3 physicalPlayerHeadPos = ClientTeleportationManager.getPlayerEyePos(RenderStates.getPartialTick());
        
        var path = PortalCameraCollision.cameraPath(client.level, physicalPlayerHeadPos,
            isometricAdjustedOriginalCameraPos, cameraEntity);
        if (path.portals().isEmpty()) {
            return false;
        }

        // Preserve the native collision-limited endpoint. Extending it back to the
        // configured third-person distance would put the camera through real walls.
        Vec3 renderingCameraPos = realCameraPos;
        Matrix4f cameraTransformation = null;
        for (Portal portal : path.portals()) {
            renderingCameraPos = portal.transformPoint(renderingCameraPos);
            Matrix4f transformation = portal.getAdditionalCameraTransformation();
            if (transformation != null) {
                if (cameraTransformation == null) cameraTransformation = new Matrix4f(transformation);
                else cameraTransformation.mul(transformation);
            }
        }
        
        
        WorldRenderInfo worldRenderInfo = new WorldRenderInfo.Builder()
            .setWorld((net.minecraft.client.multiplayer.ClientLevel) path.world())
            .setCameraPos(renderingCameraPos)
            .setCameraTransformation(cameraTransformation)
            .setOverwriteCameraTransformation(false)
            .setDescription(null)
            .setRenderDistance(client.options.getEffectiveRenderDistance())
            .setDoRenderHand(false)
            .setEnableViewBobbing(false)
            .build();
        
        var destination=client.gameRenderer.mainRenderTarget();
        var previousPath = cameraPortalPath;
        cameraPortalPath = path.portals().stream().map(Portal::getUUID).toList();
        try {
            IPCGlobal.renderer.invokeWorldRendering(worldRenderInfo);
        } finally {
            cameraPortalPath = previousPath;
        }
        var source=MyGameRenderer.renderedTarget();
        NativePortalCompositor.drawRectangle(source,destination,0,0,destination.width,destination.height,false,true);
        destination.copyDepthFrom(source);
        
        return true;
    }
    
}
