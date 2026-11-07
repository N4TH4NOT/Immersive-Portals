package validation;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector4f;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.NativeEntityPass;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/** GPU observations of identifiable player geometry during real, reversible portal movement. */
final class PortalBodyRenderingSmoke {
    private enum Phase {
        REMOTE_OFF, REMOTE_ON, SETUP_WALK, APPROACH, STRADDLE_DOWN,
        F5_BACK, F5_FRONT, CROSS, EXIT_DOWN, F5_EXIT, RETURN, STAND, FINAL_CROSS, COMPLETE
    }

    private record Frame(Phase phase, int index, Vec3 feet, Vec3 camera, CameraType cameraType,
            float pitch, double headX, double headY) {}

    private record Pixels(Frame frame, int width, int height, int headRegionPixels,
            int helmetInHeadRegion, int helmet, int body, int helmetOutsideExpected) {}

    private static Phase phase = Phase.REMOTE_OFF;
    private static boolean initialized;
    private static boolean started;
    private static boolean complete;
    private static boolean originalBobbing;
    private static boolean originalRenderPlayer;
    private static CameraType originalCamera;
    private static int ticks;
    private static int phaseTick;
    private static int phaseFrames;
    private static int captures;
    private static int phaseCaptures;
    private static int firstPersonPixels;
    private static int movingPixels;
    private static int thirdPersonPixels;
    private static int remoteOffPixels = -1;
    private static int remoteOnPixels = -1;
    private static int lastCaptured = -1;
    private static int normalBodies;
    private static int projectedBodies;
    private static int independentlyClippedViews;
    private static Frame lastFrame;
    private static final java.util.concurrent.atomic.AtomicInteger PENDING =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.ConcurrentLinkedQueue<Pixels> RESULTS =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.EnumMap<Phase, Integer> CAPTURE_COUNTS =
            new java.util.EnumMap<>(Phase.class);
    private static volatile String readbackFailure;

    private PortalBodyRenderingSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            if (!started || complete) return;
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            if (phase.ordinal() >= Phase.APPROACH.ordinal())
                for (var body : NativeEntityPass.snapshots(context.levelState())) {
                    if (!body.localPlayer()) continue;
                    boolean destination = client.player.getX() > 160;
                    double physicalX = destination ? PortalBodyRenderFixture.DESTINATION_X
                            : PortalBodyRenderFixture.SOURCE_X;
                    double otherX = destination ? PortalBodyRenderFixture.SOURCE_X
                            : PortalBodyRenderFixture.DESTINATION_X;
                    double expectedX = body.projected() ? otherX : physicalX;
                    require(Math.abs(body.position().x - expectedX) < .02,
                            "A player body was extracted at an unrelated portal position: " + body);
                    require(body.clip() != null
                                    && Math.abs(body.clip().pos().x - expectedX) < .02
                                    && Math.abs(body.clip().pos().z) < .001
                                    && body.clip().normal().z
                                            * (expectedX == PortalBodyRenderFixture.SOURCE_X ? 1 : -1)
                                            > .99,
                            "The body clipping plane did not keep its physical portal side: " + body);
                    if (body.projected()) projectedBodies++;
                    else normalBodies++;
                    if (PortalRendering.isRendering()) {
                        require(body.viewClip() != null,
                                "A portal view replaced the body's independent clipping plane: " + body);
                        independentlyClippedViews++;
                    }
                }
            if (WorldRenderInfo.isRendering()) return;
            Vec3 head = client.player.getEyePosition(
                    context.deltaTracker().getGameTimeDeltaPartialTick(true)).add(0, -.12, 0);
            // This real same-world view looks through the source aperture at the player
            // ten blocks behind its virtual camera. Express that body in root-view space.
            if (phase == Phase.REMOTE_OFF || phase == Phase.REMOTE_ON) head = head.add(0, 0, -10);
            Vec3 relative = head.subtract(context.camera().position());
            var clip = context.camera().getViewRotationProjectionMatrix(new org.joml.Matrix4f())
                    .transform(new Vector4f((float) relative.x, (float) relative.y,
                            (float) relative.z, 1));
            lastFrame = new Frame(phase, RenderStates.frameIndex, client.player.position(),
                    context.camera().position(), client.options.getCameraType(),
                    context.camera().xRot(), .5 + .5 * clip.x / clip.w,
                    .5 - .5 * clip.y / clip.w);
            phaseFrames++;
        });
        IPGlobal.PRE_GAME_RENDER_EVENT.register(() -> {
            // This is the finalized previous frame, including Iris's final composite.
            // Its camera metadata was saved during that frame's primary extraction.
            Frame frame = lastFrame;
            if (!started || complete || frame == null
                    || frame.index == lastCaptured || PENDING.get() >= 4) return;
            boolean moving = frame.phase == Phase.CROSS || frame.phase == Phase.RETURN
                    || frame.phase == Phase.FINAL_CROSS;
            if (frame.phase != phase && !moving) return;
            boolean capture = switch (frame.phase) {
                case REMOTE_OFF, REMOTE_ON -> phaseFrames >= 12 && phaseCaptures < 1;
                case STRADDLE_DOWN, EXIT_DOWN, STAND -> phaseFrames >= 8 && phaseCaptures < 3;
                case F5_BACK, F5_FRONT, F5_EXIT -> phaseFrames >= 8 && phaseCaptures < 2;
                case CROSS, RETURN, FINAL_CROSS -> Math.abs(frame.feet.z) < .35
                        && CAPTURE_COUNTS.getOrDefault(frame.phase, 0) < 4;
                default -> false;
            };
            if (capture) capture(frame);
        });
    }

    static boolean tick(Minecraft client, int currentTick) {
        initialize();
        ticks = currentTick;
        if (!started) {
            started = true;
            originalCamera = client.options.getCameraType();
            originalBobbing = client.options.bobView().get();
            originalRenderPlayer = IPGlobal.renderYourselfInPortal;
            client.options.bobView().set(false);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            IPGlobal.renderYourselfInPortal = false;
            phaseTick = ticks;
            client.player.connection.sendCommand("portal_smoke_body true");
        }
        require(readbackFailure == null, "The body screenshot could not be read: " + readbackFailure);
        for (Pixels pixels; (pixels = RESULTS.poll()) != null; ) validate(pixels);
        if (complete) return true;
        require(ticks - phaseTick < 400,
                "The body rendering probe stalled: " + phase + " feet=" + client.player.position());
        int elapsed = ticks - phaseTick;
        boolean destination = client.player.getX() > 160;
        switch (phase) {
            case REMOTE_OFF -> {
                if (elapsed < 30 || !fixtureReady(client, true) || remoteOffPixels < 0) return false;
                IPGlobal.renderYourselfInPortal = true;
                advance(client, Phase.REMOTE_ON, 0, CameraType.FIRST_PERSON);
            }
            case REMOTE_ON -> {
                if (remoteOnPixels < 0) return false;
                require(remoteOnPixels > Math.max(20, remoteOffPixels + 20),
                        "The genuine distant portal view did not render the player's helmet: off="
                                + remoteOffPixels + " on=" + remoteOnPixels);
                System.out.println("PORTAL_RENDER_BODY_REMOTE_OK off=" + remoteOffPixels
                        + " on=" + remoteOnPixels);
                advance(client, Phase.SETUP_WALK, 45, CameraType.FIRST_PERSON);
                client.player.connection.sendCommand("portal_smoke_body false");
            }
            case SETUP_WALK -> {
                if (elapsed < 30 || !fixtureReady(client, false)) return false;
                require(Math.abs(client.player.getZ() - 1.5) < .05,
                        "The natural body probe did not start before its portal");
                advance(client, Phase.APPROACH, 45, CameraType.FIRST_PERSON);
            }
            case APPROACH -> {
                keys(client, true, false, true);
                require(!destination, "The player crossed before the body straddling probe");
                if (client.player.getZ() < .16)
                    advance(client, Phase.STRADDLE_DOWN, 89, CameraType.FIRST_PERSON);
            }
            case STRADDLE_DOWN -> {
                keys(client, false, false, true);
                require(!destination && client.player.getZ() > 0 && client.player.getZ() < .3,
                        "The first-person body probe did not remain across the source plane");
                if (elapsed >= 12 && phaseCaptures >= 3 && PENDING.get() == 0)
                    advance(client, Phase.F5_BACK, 30, CameraType.THIRD_PERSON_BACK);
            }
            case F5_BACK -> {
                keys(client, false, false, true);
                if (elapsed >= 8 && phaseCaptures >= 2 && PENDING.get() == 0)
                    advance(client, Phase.F5_FRONT, 30, CameraType.THIRD_PERSON_FRONT);
            }
            case F5_FRONT -> {
                keys(client, false, false, true);
                if (elapsed >= 8 && phaseCaptures >= 2 && PENDING.get() == 0)
                    advance(client, Phase.CROSS, 45, CameraType.FIRST_PERSON);
            }
            case CROSS -> {
                keys(client, true, false, true);
                if (destination) advance(client, Phase.EXIT_DOWN, 89, CameraType.FIRST_PERSON);
            }
            case EXIT_DOWN -> {
                keys(client, false, false, true);
                require(destination && client.player.getZ() > -.3,
                        "The first-person body probe was pushed away from the exit plane");
                if (elapsed >= 12 && phaseCaptures >= 3 && PENDING.get() == 0)
                    advance(client, Phase.F5_EXIT, 30, CameraType.THIRD_PERSON_BACK);
            }
            case F5_EXIT -> {
                keys(client, false, false, true);
                if (elapsed >= 8 && phaseCaptures >= 2 && PENDING.get() == 0)
                    advance(client, Phase.RETURN, 45, CameraType.FIRST_PERSON);
            }
            case RETURN -> {
                keys(client, false, true, true);
                if (!destination) advance(client, Phase.STAND, 45, CameraType.FIRST_PERSON);
            }
            case STAND -> {
                keys(client, false, false, false);
                if (elapsed >= 12 && phaseCaptures >= 3 && PENDING.get() == 0)
                    advance(client, Phase.FINAL_CROSS, 45, CameraType.FIRST_PERSON);
            }
            case FINAL_CROSS -> {
                keys(client, true, false, false);
                if (destination) advance(client, Phase.COMPLETE, 45, CameraType.FIRST_PERSON);
            }
            case COMPLETE -> {
                keys(client, false, false, false);
                if (elapsed < 3 || phaseFrames < 3 || PENDING.get() != 0) return false;
                require(firstPersonPixels >= 9 && movingPixels >= 2 && thirdPersonPixels >= 6,
                        "The GPU body probe missed a camera or movement mode: first="
                                + firstPersonPixels + " moving=" + movingPixels
                                + " third=" + thirdPersonPixels);
                require(normalBodies > 0 && projectedBodies > 0 && independentlyClippedViews > 0,
                        "The GPU probe missed a clipped body side: normal=" + normalBodies
                                + " projected=" + projectedBodies
                                + " combined=" + independentlyClippedViews);
                requireWall(client);
                complete = true;
                client.options.bobView().set(originalBobbing);
                client.options.setCameraType(originalCamera);
                IPGlobal.renderYourselfInPortal = originalRenderPlayer;
                System.out.println("PORTAL_RENDER_BODY_CAMERA_OK first=" + firstPersonPixels
                        + " moving=" + movingPixels + " third=" + thirdPersonPixels
                        + " captures=" + captures + " normalBodies=" + normalBodies
                        + " projectedBodies=" + projectedBodies
                        + " independentlyClippedViews=" + independentlyClippedViews);
                return true;
            }
        }
        return false;
    }

    private static void advance(Minecraft client, Phase next, float pitch, CameraType camera) {
        keys(client, false, false, true);
        phase = next;
        phaseTick = ticks;
        phaseFrames = phaseCaptures = 0;
        client.player.setXRot(pitch);
        client.player.xRotO = pitch;
        client.player.setYRot(180);
        client.player.yRotO = 180;
        client.options.setCameraType(camera);
        System.out.println("PORTAL_RENDER_BODY_STAGE " + phase + " feet="
                + client.player.position());
    }

    private static void keys(Minecraft client, boolean forward, boolean backward, boolean crouch) {
        client.options.keyUp.setDown(forward);
        client.options.keyDown.setDown(backward);
        client.options.keyShift.setDown(crouch);
    }

    private static boolean fixtureReady(Minecraft client, boolean remote) {
        if (client.level.dimension() != Level.OVERWORLD
                || Math.abs(client.player.getX() - PortalBodyRenderFixture.SOURCE_X) > .05
                || Math.abs(client.player.getY() - PortalBodyRenderFixture.FLOOR_Y - 1) > .05)
            return false;
        var color = client.player.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.DYED_COLOR);
        if (color == null || color.rgb() != PortalBodyRenderFixture.MAGENTA) return false;
        int faces = 0;
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof Portal portal
                    && portal.getOriginPos().distanceTo(new Vec3(
                            PortalBodyRenderFixture.SOURCE_X,
                            PortalBodyRenderFixture.FLOOR_Y + 3, 0)) < .01
                    && Math.abs(portal.getDestPos().x
                            - (remote ? PortalBodyRenderFixture.SOURCE_X
                                    : PortalBodyRenderFixture.DESTINATION_X)) < .01) faces++;
        return faces == 2;
    }

    private static void requireWall(Minecraft client) {
        for (int x = -3; x <= 3; x++)
            for (int y = PortalBodyRenderFixture.FLOOR_Y + 1;
                    y <= PortalBodyRenderFixture.FLOOR_Y + 6; y++)
                require(client.level.getBlockState(new BlockPos(
                                PortalBodyRenderFixture.SOURCE_X + x, y, -1)).is(Blocks.STONE)
                                && client.level.getBlockState(new BlockPos(
                                        PortalBodyRenderFixture.DESTINATION_X + x, y, 0))
                                        .is(Blocks.STONE),
                        "The player body probe removed a backing wall");
    }

    private static void capture(Frame frame) {
        lastCaptured = frame.index;
        CAPTURE_COUNTS.merge(frame.phase, 1, Integer::sum);
        if (frame.phase == phase) phaseCaptures++;
        captures++;
        PENDING.incrementAndGet();
        var client = Minecraft.getInstance();
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                var directory = client.gameDirectory.toPath().resolve("screenshots");
                java.nio.file.Files.createDirectories(directory);
                image.writeToFile(directory.resolve("portal-body-"
                        + frame.phase.name().toLowerCase(java.util.Locale.ROOT)
                        + "-" + frame.index + ".png"));
                RESULTS.add(pixels(frame, image));
            } catch (Throwable failure) {
                readbackFailure = failure.toString();
            } finally {
                PENDING.decrementAndGet();
            }
        });
    }

    private static Pixels pixels(Frame frame, NativeImage image) {
        int width = image.getWidth(), height = image.getHeight();
        int helmet = 0, body = 0, headRegion = 0, headPixels = 0, outside = 0;
        for (int y = height / 20; y < height * 9 / 10; y++)
            for (int x = width / 10; x < width * 9 / 10; x++) {
                int rgb = image.getPixel(x, y);
                int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
                boolean magenta = red > 35 && blue > 35 && red > green * 1.45
                        && blue > green * 1.25;
                boolean cyan = green > 35 && blue > 35 && green > red * 1.45
                        && blue > red * 1.25;
                boolean head = x >= width / 4 && x < width * 3 / 4
                        && y < height * 11 / 20;
                if (head) {
                    headPixels++;
                    if (magenta) headRegion++;
                }
                if (magenta) {
                    helmet++;
                    if (Math.abs((double) x / width - frame.headX) > .13
                            || Math.abs((double) y / height - frame.headY) > .16) outside++;
                }
                if (cyan) body++;
            }
        return new Pixels(frame, width, height, headPixels, headRegion, helmet, body, outside);
    }

    private static void validate(Pixels pixels) {
        Phase captured = pixels.frame.phase;
        if (captured == Phase.REMOTE_OFF) remoteOffPixels = pixels.helmet;
        else if (captured == Phase.REMOTE_ON) remoteOnPixels = pixels.helmet;
        else if (pixels.frame.cameraType == CameraType.FIRST_PERSON) {
            require(pixels.helmetInHeadRegion <= Math.max(2, pixels.headRegionPixels * .002),
                    "The first-person camera saw the inside of its own helmet: " + pixels);
            firstPersonPixels++;
            if (captured == Phase.CROSS || captured == Phase.RETURN
                    || captured == Phase.FINAL_CROSS) movingPixels++;
        } else {
            require(pixels.helmet >= 20 && pixels.body >= 50,
                    "The third-person portal view lost the visible player body: " + pixels);
            require(pixels.helmetOutsideExpected <= Math.max(3, pixels.helmet * .05),
                    "The third-person player helmet appeared away from its projected head: "
                            + pixels);
            thirdPersonPixels++;
        }
        System.out.println("PORTAL_RENDER_BODY_PIXELS phase=" + captured
                + " frame=" + pixels.frame.index + " helmet=" + pixels.helmet
                + " cyan=" + pixels.body + " interior=" + pixels.helmetInHeadRegion
                + " outside=" + pixels.helmetOutsideExpected);
    }
}
