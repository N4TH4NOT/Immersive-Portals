package qouteall.imm_ptl.core.ducks;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;

public interface IEWorldRenderer {
    EntityRenderDispatcher ip_getEntityRenderDispatcher();
    ViewArea ip_getBuiltChunkStorage();
    RenderBuffers ip_getRenderBuffers();
    ObjectArrayList<SectionRenderDispatcher.RenderSection> portal_getChunkInfoList();
}
