package validation;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/** Actual movement/use keys with independent pose, camera and native consumption observations. */
final class PortalPoseUseSmoke {
    private enum Stage { SETUP, ARM, MOVE, AFTER, VERIFY, COMPLETE }
    private record Frame(String stage, int tick, int helperTick, int playerTick, long worldTime,
            int renderFrame, double time, String dimension, Vec3 rawFeet, Vec3 previousTickFeet,
            Vec3 feet, Vec3 camera, Vec3 eye, Vec3 velocity, Pose pose, boolean destination) {}

    private static final java.util.Set<String> acknowledgements = new java.util.HashSet<>();
    private static Stage stage = Stage.SETUP;
    private static int phase = -1, ticks, started, stageTick, crossingTick;
    private static int sourceFrames, destinationFrames, transitions, sourcePoseTicks, destinationPoseTicks;
    private static int heldBefore, heldAfter, lastUsed = -1, poseSince;
    private static int gameTicks;
    private static boolean initialized, observing, pressedUse, crossed, released, useStarted;
    private static Frame previous;
    private static Pose lastPose;

    private PortalPoseUseSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String name() {
        return PortalPoseUseFixture.CASES[phase];
    }

    private static boolean destination(Minecraft client) {
        return PortalPoseUseFixture.heldUse(name()) ? client.level.dimension() == Level.NETHER
                : client.player.getX() > 352;
    }

    private static Vec3 unfold(Vec3 position, boolean destination) {
        return destination ? position.add(-64, 0, 0) : position;
    }

    private static boolean correctPose(Minecraft client) {
        return switch (name()) {
            case "sprint" -> client.player.isSprinting();
            case "crouch" -> client.player.isCrouching() && client.player.getPose() == Pose.CROUCHING;
            case "fly" -> client.player.getAbilities().flying;
            case "fall" -> !client.player.onGround() && client.player.getDeltaMovement().y < -.08;
            case "swim" -> client.player.isSwimming() && client.player.getPose() == Pose.SWIMMING
                    && client.player.isInWater();
            default -> client.player.isUsingItem();
        };
    }

    private static void initialize() {
        initialized = true;
        // Fixture actions run at END_CLIENT_TICK. Count the simulation boundary
        // independently so a skipped action callback never looks like a frame-only move.
        ClientTickEvents.START_CLIENT_TICK.register(client -> gameTicks++);
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                acknowledgements.add(message.getString()));
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            if (!observing || WorldRenderInfo.isRendering()) return;
            var client = Minecraft.getInstance();
            boolean destination = destination(client);
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(true);
            var currentPose = client.player.getPose();
            if (currentPose != lastPose) {
                lastPose = currentPose;
                poseSince = ticks;
            }
            var frame = new Frame(stage.name(), gameTicks, ticks, client.player.tickCount,
                    context.level().getGameTime(), RenderStates.frameIndex,
                    gameTicks + (double) partial, context.level().dimension().identifier().toString(),
                    client.player.position(),
                    unfold(new Vec3(client.player.xo, client.player.yo, client.player.zo), destination),
                    unfold(client.player.position(), destination),
                    unfold(context.camera().position(), destination),
                    unfold(client.player.getEyePosition(partial), destination),
                    client.player.getDeltaMovement(), currentPose, destination);
            var cameraOffset = frame.camera.subtract(frame.eye);
            require(Math.hypot(cameraOffset.x, cameraOffset.z) < 1.0E-5,
                    "A pose/use crossing detached the camera horizontally from its interpolated eye: "
                            + name() + " current=" + frame);
            // Vanilla eases eye height when crouch/swim begins. After that ordinary
            // transition settles, the camera must again match the actual eye in 3D.
            if (ticks - poseSince >= 12)
                require(Math.abs(cameraOffset.y) < .025,
                        "The settled pose camera did not follow the interpolated eye: " + name()
                                + " offset=" + cameraOffset + " current=" + frame);
            if (previous != null) {
                double elapsed = frame.time - previous.time;
                require(elapsed >= -.01, "Pose/use simulation time moved backwards: previous="
                        + previous + " current=" + frame);
                Vec3 displacement = frame.camera.subtract(previous.camera);
                require(Math.hypot(displacement.x, displacement.z) <= .8 * Math.max(0, elapsed) + .055,
                        "A pose/use camera gained horizontal travel at its portal: " + name()
                                + " displacement=" + displacement + " previous=" + previous + " current=" + frame);
                if (frame.tick == previous.tick && frame.pose == previous.pose) {
                    require(frame.feet.distanceTo(previous.feet) < 1.0E-5,
                            "A frame-only pose/use crossing changed the player's movement endpoint: case="
                                    + name() + " previous=" + previous + " current=" + frame);
                    require(frame.velocity.distanceTo(previous.velocity) < .025,
                            "A frame-only pose/use crossing changed native velocity: case=" + name()
                                    + " previous=" + previous + " current=" + frame);
                } else if (frame.tick - previous.tick == 1) {
                    var step = frame.feet.subtract(previous.feet);
                    require(Math.hypot(step.x, step.z) <= Math.hypot(previous.velocity.x,
                                    previous.velocity.z) + .16,
                            "The pose/use portal added horizontal travel beyond native acceleration: case="
                                    + name() + " previous=" + previous + " current=" + frame);
                }
                if (frame.destination != previous.destination) {
                    require(destination && correctPose(client),
                            "The active pose/item use was not preserved at the actual rendered crossing: " + name());
                    transitions++;
                    System.out.println("PORTAL_POSE_USE_FRAME_CROSS case=" + name()
                            + " previous=" + previous + " current=" + frame);
                }
            }
            if (destination) destinationFrames++; else sourceFrames++;
            previous = frame;
        });
    }

    private static void releaseKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
    }

    private static void begin(Minecraft client) {
        phase++;
        if (phase == PortalPoseUseFixture.CASES.length) {
            stage = Stage.COMPLETE;
            System.out.println("PORTAL_POSE_USE_COMPLETE cases=" + phase + " heldDimensionCrossings=2");
            return;
        }
        observing = false;
        previous = null;
        lastPose = null;
        sourceFrames = destinationFrames = transitions = sourcePoseTicks = destinationPoseTicks = 0;
        heldBefore = heldAfter = 0;
        lastUsed = -1;
        pressedUse = crossed = released = useStarted = false;
        stage = Stage.SETUP;
        started = stageTick = ticks;
        releaseKeys(client);
        client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        client.player.connection.sendCommand("portal_pose_use_setup " + name());
        logStage(client);
    }

    private static void logStage(Minecraft client) {
        System.out.println("PORTAL_POSE_USE_STAGE case=" + name() + " stage=" + stage
                + " helperTick=" + ticks + " gameTick=" + gameTicks
                + " playerTick=" + client.player.tickCount + " position=" + client.player.position());
    }

    private static boolean ready(Minecraft client) {
        if (!acknowledgements.contains("PORTAL_POSE_USE_SETUP " + name())
                || client.level.dimension() != Level.OVERWORLD
                || client.player.position().distanceTo(PortalPoseUseFixture.start(name())) > .08)
            return false;
        if (name().equals("bow") && !client.player.getMainHandItem().is(Items.BOW)) return false;
        if (name().equals("food") && (!client.player.getMainHandItem().is(Items.APPLE)
                || client.player.getMainHandItem().getCount() != 2
                || client.player.getFoodData().getFoodLevel() > 5)) return false;
        if (name().equals("fly") && !client.player.getAbilities().flying) return false;
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof Portal portal
                    && portal.getOriginPos().distanceTo(new Vec3(320, 85, 64)) < .01
                    && portal.getNormal().z > .99
                    && portal.getDestPos().distanceTo(new Vec3(384, 85, 64)) < .01)
                return client.gameRenderer.mainCamera().position()
                        .distanceTo(client.player.getEyePosition()) < .7;
        return false;
    }

    private static void heldUse(Minecraft client) {
        if (!PortalPoseUseFixture.heldUse(name()) || released) return;
        if (!pressedUse) {
            pressedUse = true;
            KeyMapping.click(client.options.keyUse.getDefaultKey());
        }
        client.options.keyUse.setDown(true);
        if (client.player.isUsingItem()) {
            int used = client.player.getTicksUsingItem();
            require(lastUsed < 0 || used >= lastUsed,
                    "The client held-use counter restarted at a portal: " + name());
            lastUsed = used;
            useStarted = true;
            if (destination(client)) heldAfter++; else heldBefore++;
        } else if (useStarted) {
            require(name().equals("food") && crossed
                            && client.player.getMainHandItem().getCount() == 1,
                    "Native held use stopped before its requested release or consumption: " + name());
        }
    }

    private static void movement(Minecraft client, boolean active) {
        client.options.keyUp.setDown(active);
        client.options.keyShift.setDown(name().equals("crouch"));
        client.options.keySprint.setDown(active && (name().equals("sprint") || name().equals("swim")));
    }

    private static boolean outcome(Minecraft client) {
        if (name().equals("bow")) return released && ticks - crossingTick >= 30;
        if (name().equals("food")) {
            if (client.player.getMainHandItem().getCount() != 1) return false;
            released = true;
            client.options.keyUse.setDown(false);
            return ticks - crossingTick >= 25;
        }
        if (name().equals("fall")) return client.player.onGround() && ticks - crossingTick >= 10;
        return ticks - crossingTick >= 8;
    }

    static boolean tick(Minecraft client, int currentTick) {
        if (!client.player.getGameProfile().name().equals("PortalTest")) return true;
        ticks = currentTick;
        if (!initialized) {
            initialize();
            begin(client);
        }
        if (stage == Stage.COMPLETE) return true;
        require(ticks - started < 450, "The native pose/use case stalled: " + name() + " " + stage);
        switch (stage) {
            case SETUP -> {
                releaseKeys(client);
                if (ticks - stageTick < 15 || !ready(client)) return false;
                stage = Stage.ARM;
                stageTick = ticks;
                poseSince = ticks;
                observing = true;
                logStage(client);
            }
            case ARM -> {
                movement(client, false);
                heldUse(client);
                int warmup = name().equals("food") ? 4 : 12;
                if (ticks - stageTick < warmup || (PortalPoseUseFixture.heldUse(name())
                        && (!client.player.isUsingItem() || lastUsed < warmup))) return false;
                stage = Stage.MOVE;
                stageTick = ticks;
                logStage(client);
            }
            case MOVE -> {
                heldUse(client);
                movement(client, true);
                boolean destination = destination(client);
                if (correctPose(client)) {
                    if (destination) destinationPoseTicks++; else sourcePoseTicks++;
                }
                if (!destination) return false;
                require(correctPose(client) && sourcePoseTicks >= 2,
                        "Ordinary movement did not preserve the active pose/use across the portal: " + name());
                crossed = true;
                crossingTick = ticks;
                stage = Stage.AFTER;
                stageTick = ticks;
                logStage(client);
            }
            case AFTER -> {
                heldUse(client);
                // Preserve sprint/swim until both sides have independent observations.
                // All other cases stop ordinary movement immediately after crossing.
                movement(client, (name().equals("sprint") || name().equals("swim"))
                        && ticks - crossingTick < 4);
                if (correctPose(client)) destinationPoseTicks++;
                require(destination(client), "The player was pushed back after its pose/use crossing");
                if (name().equals("bow") && ticks - crossingTick >= 6) {
                    released = true;
                    client.options.keyUse.setDown(false);
                }
                if (!outcome(client)) return false;
                require(sourceFrames >= 2 && destinationFrames >= 2 && transitions == 1
                                && sourcePoseTicks >= 2 && destinationPoseTicks >= 2,
                        "The pose/use probe did not independently observe both sides: " + name());
                if (PortalPoseUseFixture.heldUse(name()))
                    require(heldBefore >= 3 && heldAfter >= 3,
                            "Native held use did not continue on both sides of the crossing");
                releaseKeys(client);
                client.player.connection.sendCommand("portal_pose_use_verify " + name());
                stage = Stage.VERIFY;
                stageTick = ticks;
                logStage(client);
            }
            case VERIFY -> {
                releaseKeys(client);
                if (!acknowledgements.contains("PORTAL_POSE_USE_VERIFIED " + name())) return false;
                observing = false;
                System.out.println("PORTAL_POSE_USE_CLIENT_OK case=" + name() + " source=" + sourceFrames
                        + " destination=" + destinationFrames + " transitions=" + transitions
                        + " heldBefore=" + heldBefore + " heldAfter=" + heldAfter);
                begin(client);
            }
            default -> throw new AssertionError("Unexpected pose/use stage " + stage);
        }
        return stage == Stage.COMPLETE;
    }
}
