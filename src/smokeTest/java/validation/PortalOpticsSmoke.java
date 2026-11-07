package validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/** Actual GPU pictures with independent physical-scene and direct-world lighting references. */
final class PortalOpticsSmoke {
    private static final com.google.gson.Gson FRAME_JSON = new com.google.gson.Gson();
    private record View(String name, double distance, int pitch, boolean oblique, String mode) {
        boolean reference() { return mode.startsWith("reference_"); }
        boolean lighting() { return mode.contains("dark") || mode.contains("lit"); }
    }

    private static final java.util.List<View> views = new java.util.ArrayList<>();
    private static int index = -1;
    private static int started;
    private static int captured;
    private static int lastFrame = -1;
    private static boolean initialized;
    private static boolean capturing;
    private static boolean complete;

    private PortalOpticsSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void initialize() {
        initialized = true;
        double[] distances = {.9, .3, .04, .001};
        for (int d = 0; d < distances.length; d++)
            for (int pitch : new int[] {30, 45, 60, 89})
                for (boolean oblique : new boolean[] {false, true})
                    views.add(new View("sweep-" + d + "-" + pitch + "-" + (oblique ? 1 : 0),
                            distances[d], pitch, oblique, "clear"));
        for (String mode : new String[] {"source", "destination", "restored"})
            views.add(new View("occlusion-" + mode, 3, 30, false, mode));
        for (String state : new String[] {"dark", "lit", "dark_again"}) {
            views.add(new View(state + "-portal", 3, 30, false, state));
            views.add(new View(state + "-reference", 3, 30, false, "reference_" + state));
        }
        IPGlobal.PRE_GAME_RENDER_EVENT.register(() -> {
            if (!capturing || WorldRenderInfo.isRendering()) return;
            var client = Minecraft.getInstance();
            var view = views.get(index);
            int frame = RenderStates.frameIndex;
            if (frame == lastFrame) return;
            require(lastFrame < 0 || frame == lastFrame + 1,
                    "The pitched optical probe skipped a completed GPU frame");
            lastFrame = frame;
            checkCamera(client, view);
            var camera = client.gameRenderer.mainCamera();
            var projection = client.gameRenderer.gameRenderState().levelRenderState
                    .cameraRenderState.projectionMatrix;
            var pose = new java.util.LinkedHashMap<String, Object>();
            pose.put("name", view.name());
            pose.put("sample", captured);
            pose.put("frame", frame);
            pose.put("mode", view.mode());
            pose.put("camera", new double[] {camera.position().x, camera.position().y, camera.position().z});
            var forward = camera.forwardVector();
            var left = camera.leftVector();
            var up = camera.upVector();
            pose.put("forward", new float[] {forward.x(), forward.y(), forward.z()});
            pose.put("left", new float[] {left.x(), left.y(), left.z()});
            pose.put("up", new float[] {up.x(), up.y(), up.z()});
            pose.put("projectionScale", new float[] {Math.abs(projection.m00()), Math.abs(projection.m11())});
            pose.put("dimension", client.level.dimension().identifier().toString());
            pose.put("gamma", client.options.gamma().get());
            System.out.println("PORTAL_OPTICS_FRAME " + FRAME_JSON.toJson(pose));
            Screenshot.grab(client.gameDirectory,
                    "portal-optics-" + view.name() + "-" + String.format(java.util.Locale.ROOT, "%02d", captured) + ".png",
                    client.gameRenderer.mainRenderTarget(), 1, message -> {});
            if (++captured == 12) capturing = false;
        });
    }

    private static void checkCamera(Minecraft client, View view) {
        require(client.level.dimension() == (view.reference() ? Level.NETHER : Level.OVERWORLD),
                "The optical camera is in the wrong level: " + view.name());
        var camera = client.gameRenderer.mainCamera();
        require(Math.abs(camera.position().x - 128) < 1.0E-5
                        && Math.abs(camera.position().z - 128 - view.distance()) < 1.0E-5,
                "The optical camera moved: " + view.name() + " " + camera.position());
        require(Math.abs(camera.xRot() - view.pitch()) < .01
                        && Math.abs(camera.yRot() - (view.oblique() ? 135 : 180)) < .01,
                "The optical camera did not retain its prescribed pitch/yaw: " + view.name());
        require(!client.player.hasEffect(MobEffects.NIGHT_VISION),
                "Night vision invalidated the physical lighting reference");
        require(client.options.ambientOcclusion().get(), "The optical probe disabled smooth lighting");
    }

    private static boolean ready(Minecraft client, View view) {
        var expected = view.reference() ? Level.NETHER : Level.OVERWORLD;
        if (client.level.dimension() != expected) return false;
        var source = ClientWorldLoader.getWorld(Level.OVERWORLD);
        var destination = ClientWorldLoader.getWorld(Level.NETHER);
        if (!source.getBlockState(new BlockPos(128, 130, 127)).is(Blocks.STONE)
                || !destination.getBlockState(new BlockPos(128, 125, 128)).is(Blocks.EMERALD_BLOCK))
            return false;
        boolean dark = view.mode().contains("dark");
        if (!destination.getBlockState(new BlockPos(128, 133, 128))
                .is(dark ? Blocks.CONCRETE.black() : Blocks.SEA_LANTERN)) return false;
        int blockLight = destination.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                new BlockPos(128, 126, 124));
        if ((dark && blockLight != 0) || (!dark && blockLight < 5)) return false;
        boolean sourceClosed = view.mode().equals("source");
        boolean destinationClosed = view.mode().equals("destination");
        if (source.getBlockState(new BlockPos(128, 130, 129)).is(Blocks.STONE) != sourceClosed
                || destination.getBlockState(new BlockPos(128, 130, 126)).is(Blocks.STONE) != destinationClosed)
            return false;
        if (!view.reference()) {
            int faces = 0;
            for (var entity : client.level.entitiesForRendering())
                if (entity instanceof Portal portal
                        && portal.getOriginPos().distanceTo(new net.minecraft.world.phys.Vec3(128, 130, 128)) < .01)
                    faces++;
            if (faces != 2) return false;
        }
        return true;
    }

    private static String readinessDetails(Minecraft client) {
        var source = ClientWorldLoader.getWorld(Level.OVERWORLD);
        var destination = ClientWorldLoader.getWorld(Level.NETHER);
        int faces = 0;
        for (var entity : source.entitiesForRendering())
            if (entity instanceof Portal portal
                    && portal.getOriginPos().distanceTo(new net.minecraft.world.phys.Vec3(128, 130, 128)) < .01)
                faces++;
        return "level=" + client.level.dimension().identifier()
                + " position=" + client.player.position() + " faces=" + faces
                + " sourceWall=" + source.getBlockState(new BlockPos(128, 130, 127))
                + " destinationFloor=" + destination.getBlockState(new BlockPos(128, 125, 128))
                + " lamp=" + destination.getBlockState(new BlockPos(128, 133, 128))
                + " blockLight=" + destination.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                        new BlockPos(128, 126, 124))
                + " sourceOccluder=" + source.getBlockState(new BlockPos(128, 130, 129))
                + " destinationOccluder=" + destination.getBlockState(new BlockPos(128, 130, 126));
    }

    private static void begin(Minecraft client, int ticks) {
        var view = views.get(index);
        captured = 0;
        lastFrame = -1;
        started = ticks;
        IPGlobal.renderMode = view.reference() ? IPGlobal.RenderMode.none : IPGlobal.RenderMode.normal;
        client.player.connection.sendCommand(String.format(java.util.Locale.ROOT,
                "portal_optics_view %.9f %d %s %s", view.distance(), view.pitch(), view.oblique(), view.mode()));
        System.out.println("PORTAL_OPTICS_VIEW_START name=" + view.name());
    }

    static void tick(Minecraft client, int ticks) {
        if (complete) return;
        require(ticks < 4800, "The optical scenario did not finish within four minutes");
        if (!initialized) {
            initialize();
            if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.gui.toastManager().clear();
            client.player.connection.sendCommand("portal_optics_setup");
            started = ticks;
            return;
        }
        if (index < 0) {
            if (ticks - started < 80) return;
            index = 0;
            begin(client, ticks);
            return;
        }
        if (capturing) return;
        if (captured == 12) {
            System.out.println("PORTAL_OPTICS_VIEW_OK name=" + views.get(index).name());
            if (++index == views.size()) {
                complete = true;
                var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
                if (loader.isModLoaded("sodium")) {
                    require(qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface
                                    .invoker.isSodiumPresent()
                                    && qouteall.imm_ptl.core.render.RenderDiagnostics.sodiumViewports() > 0,
                            "The optical probe did not use its installed Sodium adapter");
                    System.out.println("PORTAL_OPTICS_SODIUM_OK");
                }
                if (loader.isModLoaded("iris")) {
                    require(qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface
                                    .invoker.isShaders(),
                            "The optical probe did not enable the physical-lighting shaderpack");
                    System.out.println("PORTAL_OPTICS_IRIS_OK");
                }
                System.out.println("PORTAL_OPTICS_COMPLETE views=" + views.size());
                client.stop();
                return;
            }
            begin(client, ticks);
            return;
        }
        if (ticks - started == 100 || ticks - started == 250)
            System.out.println("PORTAL_OPTICS_READINESS name=" + views.get(index).name()
                    + " " + readinessDetails(client));
        require(ticks - started < 300, "The optical fixture did not settle: " + views.get(index)
                + " " + readinessDetails(client));
        var view = views.get(index);
        if (ticks - started < (view.lighting() ? 60 : 16) || !ready(client, view)) return;
        checkCamera(client, view);
        capturing = true;
    }
}
