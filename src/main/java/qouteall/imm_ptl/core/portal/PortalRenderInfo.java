package qouteall.imm_ptl.core.portal;

import net.minecraft.client.Minecraft;

import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.optimization.PortalVisibilityHistory;

import java.util.*;

/** Per-portal visibility accounting; GPU targets are owned by NativeWorldRenderer scenes. */
public final class PortalRenderInfo implements AutoCloseable {
    private long lastRenderedFrame = -1;

    private record View(Object renderer, List<UUID> ancestors) {}

    private record Entry(PortalVisibilityHistory history, long frame) {}

    private final Map<View, Entry> visibility = new HashMap<>();

    public PortalVisibilityHistory visibility() {
        visibility.entrySet().removeIf(e -> RenderStates.frameIndex - e.getValue().frame() > 120);
        View key =
                new View(
                        qouteall.imm_ptl.core.render.NativeWorldRenderer.visibilityToken(
                                Minecraft.getInstance().gameRenderer),
                        new ArrayList<>(WorldRenderInfo.getRenderingDescription()));
        var entry = visibility.get(key);
        var history = entry == null ? new PortalVisibilityHistory() : entry.history();
        visibility.put(key, new Entry(history, RenderStates.frameIndex));
        return history;
    }

    public static void init() {
        Portal.PORTAL_DISPOSE_SIGNAL.register(
                portal -> {
                    if (portal.portalRenderInfo != null) portal.portalRenderInfo.close();
                });
    }

    public static PortalRenderInfo getOptional(Portal portal) {
        return portal.portalRenderInfo;
    }

    public static PortalRenderInfo get(Portal portal) {
        if (portal.portalRenderInfo == null) portal.portalRenderInfo = new PortalRenderInfo();
        return portal.portalRenderInfo;
    }

    public void markRendered() {
        lastRenderedFrame = RenderStates.frameIndex;
    }

    public boolean wasRenderedLastFrame() {
        return lastRenderedFrame >= RenderStates.frameIndex - 1;
    }

    public void dispose() {
        close();
    }

    @Override
    public void close() {
        lastRenderedFrame = -1;
        visibility.clear();
    }
}
