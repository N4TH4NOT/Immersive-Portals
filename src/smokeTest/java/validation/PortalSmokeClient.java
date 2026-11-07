package validation;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

public final class PortalSmokeClient implements ClientModInitializer {
    private int bootTicks;
    private int ticks;
    private boolean rendered;
    private boolean crossed;
    private boolean singleplayerStarted;
    private long performanceSampleStartNanos;
    private Integer previousFramerateLimit;
    private final boolean interaction = Boolean.getBoolean("portalSmoke.interaction");
    private final boolean interactions = Boolean.getBoolean("portalSmoke.interactions");

    private static void validateItemModels(net.minecraft.client.Minecraft client) {
        var models = client.getModelManager();
        var missing = models.getItemModel(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("imm_ptl_smoke", "missing"));
        for (String name : java.util.List.of("command_stick", "portal_helper", "portal_wand")) {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("immersive_portals", name);
            if (models.getItemModel(id) == missing)
                throw new AssertionError("Missing 26.2 item model definition: " + id);
        }
        System.out.println("PORTAL_SMOKE_ITEM_MODELS_OK");
    }

    private void validatePerformanceSampling(net.minecraft.client.Minecraft client) {
        if (previousFramerateLimit == null
                || System.nanoTime() - performanceSampleStartNanos < 4_000_000_000L) return;
        try {
            int minimum = qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor.getMinimumFps();
            if (minimum >= 50)
                throw new AssertionError(
                        "Client performance sampling did not observe the temporary 30 FPS limit: "
                                + minimum);
            System.out.println("PORTAL_SMOKE_PERFORMANCE_OK minimum=" + minimum
                    + " average="
                    + qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor.getAverageFps());
        } finally {
            client.options.framerateLimit().set(previousFramerateLimit);
            previousFramerateLimit = null;
        }
    }

    @Override
    public void onInitializeClient() {
        FabricExtractionSmoke.initialize();
        if (interactions) PortalInteractionSmoke.init();
        IPConfig.getConfig().initialScreenShown = true;
        ClientTickEvents.END_CLIENT_TICK.register(
                client -> {
                    if (client.player == null) {
                        if (bootTicks == 0) {
                            client.options.renderDistance().set(3);
                            client.options.setCameraType(
                                    net.minecraft.client.CameraType.FIRST_PERSON);
                        }
                        if (bootTicks > (Boolean.getBoolean("portalSmoke.singleplayer") ? 2400 : 600))
                            throw new AssertionError(
                                    "Could not join the local verification server");
                        if (++bootTicks % 100 == 0)
                            System.out.println(
                                    "PORTAL_SMOKE_WAIT "
                                            + client.isGameLoadFinished()
                                            + " "
                                            + client.gui.screen());
                        if (Boolean.getBoolean("portalSmoke.singleplayer") && bootTicks >= 100
                                && client.isGameLoadFinished()) {
                            PortalSingleplayerSmoke.finishOpening(client);
                            if (!singleplayerStarted)
                                singleplayerStarted = PortalSingleplayerSmoke.prepare(client);
                            return;
                        }
                        if (bootTicks >= 100
                                && client.isGameLoadFinished()
                                && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.ConnectScreen)
                                && bootTicks % 100 == 0) {
                            var data =
                                    new net.minecraft.client.multiplayer.ServerData(
                                            "Local verification",
                                            System.getProperty(
                                                    "portalSmoke.address", "127.0.0.1:25567"),
                                            net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                                    new net.minecraft.client.gui.screens.TitleScreen(),
                                    client,
                                    net.minecraft.client.multiplayer.resolver.ServerAddress
                                            .parseString(data.ip),
                                    data,
                                    false,
                                    null);
                        }
                        return;
                    }
                    ticks++;
                    if (ticks == 1) {
                        previousFramerateLimit = client.options.framerateLimit().get();
                        client.options.framerateLimit().set(30);
                        performanceSampleStartNanos = System.nanoTime();
                        validateItemModels(client);
                        PortalMigrationSmoke.validate();
                        PortalOnboardingSmoke.validate(client);
                    }
                    validatePerformanceSampling(client);
                    if (Boolean.getBoolean("portalSmoke.gravity")) {
                        PortalGravitySmoke.tick(client, ticks);
                        return;
                    }
                    if (Boolean.getBoolean("portalSmoke.rendering")) {
                        PortalRenderingSmoke.tick(client, ticks);
                        return;
                    }
                    rendered |= RenderStates.getRenderedPortalNum() > 0;
                    if (ticks == 10)
                        client.gui.setScreen(
                                qouteall.imm_ptl.core.platform_specific.IPConfigGUI
                                        .createClothConfigScreen(null));
                    if (ticks == 20) {
                        client.gui.setScreen(null);
                        System.out.println("PORTAL_SMOKE_CONFIG_GUI_OK");
                    }
                    if (ticks == 25) {
                        var controller =
                                new qouteall.imm_ptl.peripheral.dim_stack.DimStackGuiController(
                                        null,
                                        () ->
                                                java.util.List.copyOf(
                                                        ClientWorldLoader.getServerDimensions()),
                                        info -> {});
                        controller.initializeAsDefault();
                        client.gui.setScreen(controller.view);
                    }
                    if (ticks == 40) {
                        client.gui.setScreen(null);
                        System.out.println("PORTAL_SMOKE_DIM_STACK_GUI_OK");
                    }
                    if (ticks == 45) {
                        var dimension =
                                ClientWorldLoader.getClientWorlds().stream()
                                        .map(w -> w.dimension())
                                        .filter(d -> d != client.level.dimension())
                                        .findFirst()
                                        .orElseThrow();
                        qouteall.imm_ptl.core.api.example.ExampleGuiPortalRendering.RemoteCallables
                                .clientActivateExampleGuiPortal(
                                        dimension, new net.minecraft.world.phys.Vec3(0, 72, 0));
                    }
                    if (ticks == 75) {
                        client.gui.setScreen(null);
                        System.out.println("PORTAL_SMOKE_GUI_OK");
                    }
                    if (ticks == 60)
                        net.minecraft.client.Screenshot.grab(
                                client.gameDirectory,
                                "portal-gui-smoke.png",
                                client.gameRenderer.mainRenderTarget(),
                                1,
                                message -> System.out.println(message.getString()));
                    if (ticks == 100) {
                        if (!rendered || ClientWorldLoader.getClientWorlds().size() < 2)
                            throw new AssertionError("Remote world was not rendered");
                        System.out.println("PORTAL_SMOKE_RENDER_OK");
                        net.minecraft.client.Screenshot.grab(
                                client.gameDirectory,
                                "portal-smoke.png",
                                client.gameRenderer.mainRenderTarget(),
                                1,
                                message -> System.out.println(message.getString()));
                    }
                    if (ticks == 110 && interaction) {
                        var block =
                                qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient
                                        .remoteHitResult;
                        if (!(block instanceof net.minecraft.world.phys.BlockHitResult hit)
                                || !hit.getBlockPos()
                                        .equals(new net.minecraft.core.BlockPos(0, 71, -2)))
                            throw new AssertionError(
                                    "Remote chest was not targeted: "
                                            + (block
                                                            instanceof
                                                            net.minecraft.world.phys.BlockHitResult
                                                                    b
                                                    ? b.getBlockPos() + " " + b.getType()
                                                    : block)
                                            + " eye="
                                            + client.player.getEyePosition()
                                            + " look="
                                            + client.player.getViewVector(1)
                                            + " reach="
                                            + client.player.blockInteractionRange());
                        qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient
                                .withSwitchedContext(
                                        () ->
                                                client.gameMode.useItemOn(
                                                        client.player,
                                                        net.minecraft.world.InteractionHand
                                                                .MAIN_HAND,
                                                        (net.minecraft.world.phys.BlockHitResult)
                                                                client.hitResult),
                                        true);
                    }
                    if (ticks == 140 && interaction) {
                        if (!(client.gui.screen()
                                instanceof
                                net.minecraft.client.gui.screens.inventory.AbstractContainerScreen))
                            throw new AssertionError("Remote inventory did not open");
                        client.player.closeContainer();
                        System.out.println("PORTAL_SMOKE_INTERACTION_OK");
                    }
                    if (interactions) PortalInteractionSmoke.tick(client, ticks);
                    if (ticks == 160) client.options.keyUp.setDown(true);
                    if (ticks > 160
                            && client.level
                                    .dimension()
                                    .identifier()
                                    .toString()
                                    .equals(
                                            System.getProperty(
                                                    "portalSmoke.target",
                                                    "minecraft:the_nether"))) {
                        client.options.keyUp.setDown(false);
                        crossed = true;
                    }
                    if (ticks == 240) {
                        if (!crossed) throw new AssertionError("Player did not cross the portal");
                        if (client.player.getGameProfile().name().contains("Scaled")
                                && Math.abs(
                                                client.player.getAttributeValue(
                                                                net.minecraft.world.entity.ai
                                                                        .attributes.Attributes
                                                                        .SCALE)
                                                        - .5)
                                        > .001)
                            throw new AssertionError("Client scale was not synchronized");
                        System.out.println("PORTAL_SMOKE_TELEPORT_OK");
                    }
                    if (ticks >= 280
                            && (!Boolean.getBoolean("portalSmoke.dynamicDimensions")
                                || DimensionLibrarySmoke.tickClient(client))) {
                        System.out.println("PORTAL_SMOKE_COMPLETE");
                        client.stop();
                    }
                });
    }
}
