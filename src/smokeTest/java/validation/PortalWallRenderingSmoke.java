package validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/** Captures consecutive GPU frames to detect source-wall flashes at a coplanar portal. */
final class PortalWallRenderingSmoke {
    private static final int SAMPLES = 12;
    private static final String[] PHASES = {
        "prediction", "synchronous", "angled", "compatibility", "occluded", "revealed",
        "near-04-normal", "near-04-oblique", "near-01-normal", "near-01-oblique",
        "near-001-normal", "near-001-oblique", "near-direct",
        "near-micro-normal", "near-micro-oblique"
    };
    private static final double[] NEAR_DISTANCES = {
        .04, .04, .01, .01, .001, .001, .001, .000001, .000001
    };
    private static int phase = -1;
    private static int phaseTick;
    private static int captured;
    private static int lastFrame = -1;
    private static boolean capturing;
    private static boolean initialized;

    private PortalWallRenderingSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Portal front(Minecraft client) {
        Portal result = null;
        int faces = 0;
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof Portal portal
                    && portal.getDestDim() == Level.NETHER
                    && portal.getOriginPos().distanceTo(new Vec3(0, 72, 0)) < .01) {
                faces++;
                if (portal.getNormal().z > .99) result = portal;
            }
        require(faces == 2 && result != null,
                "The coplanar rendering fixture lost its two source wand faces");
        return result;
    }

    private static void requireWall(Minecraft client) {
        for (int x = -3; x <= 3; x++)
            for (int y = 70; y <= 76; y++)
                require(client.level.getBlockState(new BlockPos(x, y, -1)).is(Blocks.STONE),
                        "The coplanar portal's intact backing wall was removed");
    }

    private static boolean oblique() {
        return switch (PHASES[phase]) {
            case "near-04-oblique", "near-01-oblique", "near-001-oblique",
                    "near-direct", "near-micro-oblique" -> true;
            default -> false;
        };
    }

    private static void requireNearCamera(Minecraft client) {
        if (phase < 6) return;
        double distance = NEAR_DISTANCES[phase - 6];
        double tolerance = Math.min(1.0E-5, distance * .001);
        var camera = client.gameRenderer.mainCamera().position();
        require(client.level.dimension() == Level.OVERWORLD
                        && Math.abs(camera.x) <= tolerance
                        && camera.z > 0 && Math.abs(camera.z - distance) <= tolerance,
                "The near-plane probe camera moved away from its source plane: " + camera);
        require(IPGlobal.enableDepthClampForPortalRendering,
                "The near-plane regression probe disabled portal depth clamping");
        boolean oblique = oblique();
        require(Math.abs(client.player.getYRot() - (oblique ? 135 : 180)) < .01,
                "The near-plane probe camera did not retain its intended angle");
        if (oblique) {
            var look = client.player.getViewVector(1);
            double left = new Vec3(-2, 72, 0).subtract(camera).dot(look);
            double right = new Vec3(2, 72, 0).subtract(camera).dot(look);
            require(left * right < 0,
                    "The oblique aperture no longer straddles the camera's eye plane");
        }
    }

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        // Read the completed previous frame before the next update clears its target.
        // END_MAIN is too early for Iris: its final composite has not run at that point.
        IPGlobal.PRE_GAME_RENDER_EVENT.register(() -> {
            if (!capturing || WorldRenderInfo.isRendering()) return;
            var client = Minecraft.getInstance();
            int frame = RenderStates.frameIndex;
            if (frame == lastFrame) return;
            require(lastFrame < 0 || frame == lastFrame + 1,
                    "The wall flicker probe skipped a rendered frame");
            lastFrame = frame;
            requireWall(client);
            requireNearCamera(client);
            if (phase != 4)
                require(PortalRenderInfo.get(front(client)).wasRenderedLastFrame(),
                        "The coplanar portal stopped rendering in " + PHASES[phase]
                                + " frame " + frame);
            String name = "portal-wall-" + PHASES[phase] + "-frame-"
                    + String.format(java.util.Locale.ROOT, "%02d", captured);
            Screenshot.grab(client.gameDirectory, name + ".png",
                    client.gameRenderer.mainRenderTarget(), 1, message -> {});
            System.out.println("PORTAL_RENDER_WALL_FRAME phase=" + PHASES[phase]
                    + " sample=" + captured + " frame=" + frame);
            if (++captured == SAMPLES) capturing = false;
        });
    }

    static boolean tick(Minecraft client, int ticks) {
        if (phase >= PHASES.length) {
            capturing = false;
            return true;
        }
        initialize();
        if (phase < 0) {
            phase = 0;
            phaseTick = ticks;
            client.player.connection.sendCommand("portal_smoke_backing_wall");
            return false;
        }
        require(ticks - phaseTick < 160, "The wall rendering fixture did not settle");
        if (capturing) return false;
        if (captured == SAMPLES) {
            System.out.println("PORTAL_RENDER_WALL_SERIES_OK phase=" + PHASES[phase]
                    + " frames=" + captured);
            if (++phase == PHASES.length) {
                requireWall(client);
                System.out.println("PORTAL_RENDER_WALL_STABLE_OK");
                return true;
            }
            phaseTick = ticks;
            captured = 0;
            lastFrame = -1;
            switch (phase) {
                case 1 -> IPGlobal.offsetOcclusionQuery = false;
                case 2 -> {
                    IPGlobal.offsetOcclusionQuery = true;
                    client.player.setYRot(176);
                    client.player.setXRot(2);
                }
                case 3 -> IPGlobal.renderMode = IPGlobal.RenderMode.compatibility;
                case 4 -> {
                    IPGlobal.renderMode = IPGlobal.RenderMode.normal;
                    client.player.setYRot(180);
                    client.player.setXRot(0);
                    client.player.connection.sendCommand("portal_smoke_wall true");
                }
                case 5 -> client.player.connection.sendCommand("portal_smoke_wall false");
                case 6, 7, 8, 9, 10, 11, 12, 13, 14 -> {
                    IPGlobal.renderMode = PHASES[phase].equals("near-direct")
                            ? IPGlobal.RenderMode.compatibility : IPGlobal.RenderMode.normal;
                    // Brigadier's numeric reader does not accept exponent notation.
                    String distance = String.format(java.util.Locale.ROOT, "%.9f",
                            NEAR_DISTANCES[phase - 6]);
                    client.player.connection.sendCommand("portal_smoke_wall_camera "
                            + distance + " " + oblique());
                }
                default -> throw new AssertionError("Unexpected wall probe phase " + phase);
            }
        }
        // Let block updates, terrain rebuilds and visibility prediction settle before each
        // consecutive-frame series. Timing is based on actual rendered-frame progress.
        if (ticks - phaseTick < 25) return false;
        require(client.options.ambientOcclusion().get(),
                "The wall rendering probe disabled smooth lighting");
        requireWall(client);
        requireNearCamera(client);
        var destination = ClientWorldLoader.getWorld(Level.NETHER);
        require(destination.getBlockState(new BlockPos(0, 72, -4)).is(Blocks.EMERALD_BLOCK),
                "The wall probe's visible destination witness has not arrived");
        boolean occluded = phase == 4;
        require(client.level.getBlockState(new BlockPos(0, 72, 2)).is(Blocks.CONCRETE.blue()) == occluded,
                "The wall probe's foreground occluder is stale");
        if (occluded)
            require(client.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                            new BlockPos(0, 72, 3)) >= 5,
                    "The opaque foreground witness has no real source illumination");
        if (occluded)
            require(!PortalRenderInfo.get(front(client)).wasRenderedLastFrame(),
                    "The foreground wall did not occlude the coplanar portal");
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            System.out.println("PORTAL_RENDER_WALL_CHUNK_UPDATES phase=" + PHASES[phase]
                    + " " + qouteall.imm_ptl.core.render.RenderDiagnostics.chunkUpdates());
            SodiumChunkDiagnostics.dump(PHASES[phase]);
        }
        capturing = true;
        return false;
    }
}
