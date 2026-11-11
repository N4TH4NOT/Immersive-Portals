package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.q_misc_util.my_util.DQuaternion;
import java.util.ArrayList;
import java.util.List;

/** Snapshot native baked quads during extraction; submit them with their tint, opacity and animation. */
public final class OverlayRendering {
    private OverlayRendering() {}
    public record Part(List<BakedQuad> quads, Vec3 offset, DQuaternion rotation, int color) {}
    public static List<Part> extract(Portal entity) {
        if (!(entity instanceof BreakablePortalEntity portal) || portal.blockPortalShape == null ||
            !portal.isInFrontOfPortal(CHelper.getCurrentCameraPos())) return List.of();
        var overlay=portal.getActualOverlay();
        if (overlay == null || overlay.blockState() == null) return List.of();
        var model=Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(overlay.blockState());
        List<BlockStateModelPart> parts=new ArrayList<>();
        model.collectParts(RandomSource.create(0),parts);
        List<BakedQuad> quads=new ArrayList<>();
        Direction face=Direction.getApproximateNearest(portal.getNormal());
        for (var part:parts) { quads.addAll(part.getQuads(face)); quads.addAll(part.getQuads(null)); }
        if (quads.isEmpty()) for (var part:parts) for (Direction direction:Direction.values()) quads.addAll(part.getQuads(direction));
        List<BakedQuad> snapshot=List.copyOf(quads);
        for (var quad:snapshot) SodiumInterface.invoker.markSpriteActive(quad.materialInfo().sprite());
        int color=((int)(Math.clamp(overlay.opacity(),0,1)*255)<<24)|0xFFFFFF;
        Vec3 offset=portal.getNormal().scale(overlay.offset()).subtract(portal.position());
        List<Part> result=new ArrayList<>();
        for (var block:portal.blockPortalShape.area) result.add(new Part(snapshot, Vec3.atLowerCornerOf(block).add(offset), overlay.rotation(), color));
        return List.copyOf(result);
    }
    public static void submit(List<Part> parts, PoseStack pose, SubmitNodeCollector collector) {
        for (Part part:parts) {
            pose.pushPose();
            pose.translate(part.offset.x,part.offset.y,part.offset.z);
            if (part.rotation != null) pose.mulPose(part.rotation.toMcQuaternion());
            collector.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(), (transform,vertices) -> {
                QuadInstance instance=new QuadInstance();
                instance.setColor(part.color);
                instance.setLightCoords(14680304);
                instance.setOverlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                for (BakedQuad quad:part.quads) vertices.putBakedQuad(transform,quad,instance);
            });
            pose.popPose();
        }
    }
}
