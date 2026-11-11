package qouteall.imm_ptl.peripheral.mixin.client.portal_wand;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.*;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.NativeLineBuffer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.peripheral.wand.PortalWandItem;

@Mixin(LevelExtractor.class)
public class MixinDebugRenderer {
    @Inject(method="extract", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/extract/LevelExtractor;extractGizmos()V"))
    private void ip_guides(DeltaTracker delta, Camera camera, float partial, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || PortalRendering.isRendering() || qouteall.imm_ptl.core.render.context_management.WorldRenderInfo.isRendering()) return;
        var position = camera.position();
        try (var collector = ((LevelExtractor)(Object)this).collectPerFrameMainThreadGizmos();
             var lines = new NativeLineBuffer(position)) {
            var stack = new PoseStack();
            var item = client.player.getMainHandItem();
            if (item.getItem() == PortalWandItem.instance) {
                PortalWandItem.clientRender(client.player, item, stack, lines, position.x,position.y,position.z);
            }
            if (IPGlobal.debugRenderPortalShapeMesh) {
                var buffer = lines.getBuffer(NativeLineBuffer.Mode.lines());
                for (var entity : client.level.entitiesForRendering()) if (entity instanceof Portal portal) {
                    stack.pushPose();
                    var offset = portal.getOriginPos().subtract(position);
                    stack.translate(offset.x,offset.y,offset.z);
                    WireRenderingHelper.renderPortalShapeMeshDebug(stack, buffer, portal);
                    stack.popPose();
                }
            }
        }
    }
}
