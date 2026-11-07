package validation;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/** Exercises the Fabric API contract used by external render-state consumers such as BlockLab. */
final class FabricExtractionSmoke {
    private record Probe(ClientLevel level, GameRenderer gameRenderer, LevelRenderer levelRenderer,
            int view, int generation) {}

    private static final RenderStateDataKey<Probe> KEY =
            RenderStateDataKey.create(() -> "Immersive Portals extraction smoke");
    private static final long[] extracted = new long[4];
    private static final long[] rendered = new long[4];
    private static int generation;
    private static boolean checkFixture;

    private FabricExtractionSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void initialize() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            var client = Minecraft.getInstance();
            require(context.level() == client.level, "Fabric extraction exposed another level");
            require(context.gameRenderer() == client.gameRenderer,
                    "Fabric extraction exposed another game renderer");
            require(context.levelRenderer() == client.levelRenderer,
                    "Fabric extraction exposed another level renderer");
            require(context.levelState() == client.gameRenderer.gameRenderState().levelRenderState,
                    "Fabric extraction exposed a missing or stale level render state");
            require(context.camera() == client.gameRenderer.mainCamera(),
                    "Fabric extraction exposed a missing or stale camera");
            require(context.deltaTracker() == client.getDeltaTracker(),
                    "Fabric extraction exposed a missing or stale delta tracker");
            int view = !WorldRenderInfo.isRendering() ? 0
                    : WorldRenderInfo.getRenderingLayer() > 1 ? 3
                    : PortalRendering.isRendering() ? 2 : 1;
            if (WorldRenderInfo.isRendering()) {
                require(context.level() == WorldRenderInfo.getTopRenderInfo().world,
                        "Fabric extraction did not expose the requested view's level");
            }
            if (checkFixture) {
                var floor = context.level().getBlockState(new BlockPos(0, 69, 0));
                if (context.level().dimension() == Level.OVERWORLD)
                    require(floor.is(Blocks.STONE), "Fabric consumer read the wrong Overworld");
                if (context.level().dimension() == Level.NETHER)
                    require(floor.is(Blocks.EMERALD_BLOCK), "Fabric consumer read the wrong Nether");
            }
            // This is the public API call that crashed in BlockLab's AssemblyPreview.
            context.levelState().setData(KEY,
                    new Probe(context.level(), context.gameRenderer(), context.levelRenderer(),
                            view, generation));
            extracted[view]++;
        });
        LevelRenderEvents.END_MAIN.register(context -> {
            var probe = context.levelState().getData(KEY);
            require(probe != null, "Fabric render lost data attached during level extraction");
            var client = Minecraft.getInstance();
            require(probe.level() == client.level
                            && probe.gameRenderer() == context.gameRenderer()
                            && probe.levelRenderer() == context.levelRenderer()
                            && probe.generation() == generation,
                    "Fabric render consumed another view's extraction data");
            rendered[probe.view()]++;
        });
    }

    static void afterReload() {
        generation++;
        java.util.Arrays.fill(extracted, 0);
        java.util.Arrays.fill(rendered, 0);
    }

    static void validateViews(Minecraft client, Portal portal) {
        var level = client.level;
        var gameRenderer = client.gameRenderer;
        var levelRenderer = client.levelRenderer;
        var extractor = client.levelExtractor;
        var camera = gameRenderer.mainCamera();
        int initialLayer = WorldRenderInfo.getRenderingLayer();
        int initialPortalLayer = PortalRendering.getPortalLayer();
        var info = new WorldRenderInfo.Builder()
                .setWorld(ClientWorldLoader.getWorld(portal.getDestDim()))
                .setCameraPos(portal.transformPoint(camera.position()))
                .setCameraTransformation(portal.getAdditionalCameraTransformation())
                .setOverwriteCameraTransformation(false)
                .setRenderDistance(client.options.getEffectiveRenderDistance())
                .setDoRenderHand(false)
                .build();
        checkFixture = true;
        try {
            IPCGlobal.renderer.invokeWorldRendering(info);
            PortalRendering.pushPortalLayer(portal);
            try {
                IPCGlobal.renderer.invokeWorldRendering(info);
                // An enclosing view retains its scope while the nested view is extracted.
                WorldRenderInfo.pushRenderInfo(info);
                PortalRendering.pushPortalLayer(portal);
                try {
                    IPCGlobal.renderer.invokeWorldRendering(info);
                } finally {
                    PortalRendering.popPortalLayer();
                    WorldRenderInfo.popRenderInfo();
                }
            } finally {
                PortalRendering.popPortalLayer();
            }
        } finally {
            checkFixture = false;
        }
        require(client.level == level && client.gameRenderer == gameRenderer
                        && client.levelRenderer == levelRenderer && client.levelExtractor == extractor
                        && gameRenderer.mainCamera() == camera
                        && WorldRenderInfo.getRenderingLayer() == initialLayer
                        && PortalRendering.getPortalLayer() == initialPortalLayer,
                "Fabric extraction probes did not restore the enclosing rendering context");
        for (int view = 0; view < extracted.length; view++) {
            require(extracted[view] > 0 && rendered[view] > 0,
                    "Fabric extraction/render consumer did not observe view " + view);
        }
        System.out.println((generation == 0 ? "PORTAL_RENDER_FABRIC_CONTEXT_OK"
                : "PORTAL_RENDER_FABRIC_CONTEXT_RELOAD_OK")
                + " extracted=" + java.util.Arrays.toString(extracted)
                + " rendered=" + java.util.Arrays.toString(rendered));
    }
}
