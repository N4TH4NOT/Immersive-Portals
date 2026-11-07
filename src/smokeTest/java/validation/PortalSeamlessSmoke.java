package validation;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/** Observes real rendered cameras while ordinary movement crosses the portal in both directions. */
final class PortalSeamlessSmoke {
    private enum Stage {
        APPROACH, BEFORE_STOP, APPROACH_PLANE, STRADDLE_STOP, RETREAT, RETREAT_STOP,
        CROSS, EXIT_STOP, RETURN, RETURN_STOP, FINAL_CROSS, FINAL_STOP, COMPLETE
    }

    private record Sample(int tick, double time, ResourceKey<Level> dimension, boolean destination,
            Vec3 camera, Vec3 direction, Vec3 eye, Vec3 eyeStart, Vec3 eyeEnd, Vec3 playerDirection,
            Vec3 feet, Vec3 velocity) {}

    private static Portal portal;
    private static Sample previous;
    private static Stage stage = Stage.APPROACH;
    private static int ticks;
    private static int stageTick;
    private static int stageFrame;
    private static int phaseTick;
    private static int frames;
    private static int nearPlaneFrames;
    private static int transitions;
    private static int destinationFrames;
    private static int sameTickFrames;
    private static int sameTickCrossings;
    private static boolean controlled;
    private static boolean sameWorld;
    private static boolean verified;
    private static Vec3 settledPosition;
    private static double retreatStart;
    private static double maximumCameraStep;

    private PortalSeamlessSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void initialize() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            // Offscreen portal and GUI views use other cameras and must not advance the probe.
            if (portal == null || verified || WorldRenderInfo.isRendering()) return;
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            boolean destination = isDestination(client);
            require(client.level.dimension() == portal.getDestDim()
                            || client.level.dimension() == portal.getOriginDim(),
                    "Seamless movement entered an unrelated dimension");
            var forward = context.camera().forwardVector();
            Vec3 direction = new Vec3(forward.x(), forward.y(), forward.z());
            Vec3 playerDirection = client.player.getViewVector(1);
            Vec3 camera = unfold(context.camera().position(), destination);
            Vec3 feet = unfold(client.player.position(), destination);
            Vec3 velocity = destination
                    ? portal.inverseTransformLocalVec(client.player.getDeltaMovement())
                    : client.player.getDeltaMovement();
            if (destination) {
                direction = portal.inverseTransformLocalVecNonScale(direction);
                playerDirection = portal.inverseTransformLocalVecNonScale(playerDirection);
            }
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(true);
            var sample = new Sample(ticks,
                    ticks + (double) partial, client.level.dimension(), destination, camera,
                    direction.normalize(), unfold(client.player.getEyePosition(partial), destination),
                    unfold(client.player.getEyePosition(0), destination),
                    unfold(client.player.getEyePosition(1), destination),
                    playerDirection.normalize(), feet, velocity);
            require(horizontal(sample.camera().subtract(sample.eye())) <= 1e-5,
                    "First-person camera lost the interpolated player eye: " + sample);
            frames++;
            if (destination) destinationFrames++;
            if (Math.abs(portal.getDistanceToPlane(camera)) < .35) nearPlaneFrames++;
            double maximumSpeed = controlled ? .38 : .75;
            require(horizontal(velocity) < maximumSpeed,
                    "Portal added horizontal velocity: " + sample);
            if (controlled) {
                for (int y = 70; y <= 71; y++) {
                    var position = new net.minecraft.core.BlockPos(
                            (int) Math.floor(client.player.getX()), y,
                            (int) Math.floor(client.player.getZ()));
                    var fluid = client.level.getFluidState(position);
                    require(fluid.isEmpty(),
                            "Fluid entered the dry movement fixture: position=" + position
                                    + " fluid=" + fluid + " sample=" + sample);
                }
                require(Math.abs(feet.y - 70) < .025,
                        "A flat portal added vertical player movement: " + sample);
            }
            if (previous != null) {
                double elapsed = sample.time() - previous.time();
                require(elapsed >= -.01, "Rendered movement time went backwards");
                double step = horizontal(camera.subtract(previous.camera()));
                maximumCameraStep = Math.max(maximumCameraStep, step);
                require(step <= maximumSpeed * Math.max(0, elapsed) + .045,
                        "Rendered camera jumped across the portal: previous=" + previous
                                + " current=" + sample + " step=" + step);
                double verticalSpeed = Math.max(Math.abs(previous.velocity().y),
                        Math.abs(sample.velocity().y)) + .4;
                require(Math.abs(camera.y - previous.camera().y)
                                <= verticalSpeed * Math.max(0, elapsed) + .06,
                        "Rendered camera jumped vertically across the portal: previous="
                                + previous + " current=" + sample);
                require(sample.direction().dot(previous.direction()) > .995,
                        "Portal changed the unfolded camera direction: previous=" + previous
                                + " current=" + sample);
                require(sample.playerDirection().dot(previous.playerDirection()) > .995,
                        "Portal changed the unfolded player direction: previous=" + previous
                                + " current=" + sample);
                if (sample.tick() == previous.tick()) {
                    sameTickFrames++;
                    Vec3 feetChange = sample.feet().subtract(previous.feet());
                    // The scaled fixture animates its portal vertically between frames.
                    // The two controlled wand fixtures have a fixed affine transformation.
                    require((controlled ? feetChange.length() : horizontal(feetChange)) <= 1e-5,
                            "A frame-only portal crossing changed the player tick endpoint: previous="
                                    + previous + " current=" + sample);
                    Vec3 expectedEyeChange = sample.eyeEnd().subtract(sample.eyeStart())
                            .scale(Math.max(0, elapsed));
                    Vec3 actualEyeChange = sample.eye().subtract(previous.eye());
                    require(horizontal(actualEyeChange.subtract(expectedEyeChange)) <= 1e-5,
                            "Portal did not preserve eye interpolation between ticks: previous="
                                    + previous + " current=" + sample);
                }
                if (sample.tick() > previous.tick()) {
                    int elapsedTicks = sample.tick() - previous.tick();
                    // Normal acceleration is at most 0.1 blocks/tick. A portal must not add
                    // an extra movement to finish crossing after the controls have stopped.
                    double allowance = elapsedTicks == 1
                            ? horizontal(previous.velocity()) + .115
                            : maximumSpeed * elapsedTicks + .045;
                    require(horizontal(feet.subtract(previous.feet())) <= allowance,
                            "Portal forced additional player movement: previous=" + previous
                                    + " current=" + sample);
                }
                if (sample.destination() != previous.destination()) {
                    transitions++;
                    if (controlled) {
                        var handler = ((IEEntity) client.player).ip_getPortalCollisionHandler();
                        Vec3 expectedOrigin = destination
                                ? portal.getDestPos() : portal.getOriginPos();
                        Vec3 expectedNormal = destination
                                ? portal.getContentDirection() : portal.getNormal();
                        require(handler != null && handler.getCollidingPortals().stream()
                                        .anyMatch(contact -> contact.getOriginPos()
                                                        .distanceTo(expectedOrigin) < .01
                                                && contact.getNormal().dot(expectedNormal) > .99),
                                "Portal crossing lost its destination collision contact: " + sample);
                    }
                    if (sample.tick() == previous.tick()) {
                        sameTickCrossings++;
                        require(sample.velocity().distanceTo(previous.velocity()) < .025,
                                "A frame-only portal crossing changed velocity: previous="
                                        + previous + " current=" + sample);
                    }
                    System.out.println("PORTAL_SMOKE_SEAMLESS_TRANSITION stage=" + stage
                            + " previous=" + previous + " current=" + sample);
                }
            }
            previous = sample;
        });
    }

    private static Vec3 unfold(Vec3 position, boolean destination) {
        return destination ? portal.inverseTransformPoint(position) : position;
    }

    private static boolean isDestination(Minecraft client) {
        if (portal.getOriginDim() != portal.getDestDim())
            return client.level.dimension() == portal.getDestDim();
        // Same-world teleportation keeps the level object. The two wand ends are four
        // chunks apart, so the player's actual position identifies the transformed side.
        Vec3 position = client.player.position();
        return position.distanceToSqr(portal.getDestPos())
                < position.distanceToSqr(portal.getOriginPos());
    }

    private static double horizontal(Vec3 vector) {
        return Math.sqrt(vector.x * vector.x + vector.z * vector.z);
    }

    private static boolean enabled(Minecraft client) {
        String name = client.player.getGameProfile().name();
        return name.equals("PortalTest") || name.contains("Scaled");
    }

    static boolean controls(Minecraft client) {
        return client.player.getGameProfile().name().equals("PortalTest");
    }

    static void tick(Minecraft client, int currentTick) {
        if (!enabled(client) || currentTick < 150) return;
        ticks = currentTick;
        if (controlled && !sameWorld && stage == Stage.COMPLETE) {
            verifyPhase(client);
            sameWorld = true;
            stage = Stage.APPROACH;
            portal = null;
            phaseTick = currentTick;
            client.player.connection.sendCommand("portal_smoke_same_world");
            return;
        }
        if (portal == null) {
            controlled = controls(client);
            double nearest = Double.POSITIVE_INFINITY;
            for (var entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof Portal candidate) || candidate instanceof Mirror
                        || candidate.getNormal().z < .99
                        || candidate.getDestDim() != Level.NETHER) continue;
                if (sameWorld && (candidate.getOriginDim() != candidate.getDestDim()
                        || candidate.getOriginPos().distanceTo(new Vec3(0, 72, -5)) > .01
                        || candidate.getDestPos().distanceTo(new Vec3(64, 72, -5)) > .01))
                    continue;
                double distance = candidate.getOriginPos().distanceToSqr(
                        client.player.getEyePosition());
                if (distance < nearest) {
                    nearest = distance;
                    portal = candidate;
                }
            }
            if (portal == null && sameWorld && currentTick - phaseTick < 100) return;
            require(portal != null, "Seamless movement source portal is missing");
            require(client.level.dimension() == portal.getOriginDim(),
                    "Seamless movement did not start in its source world");
            stage = Stage.APPROACH;
            phaseTick = currentTick;
            stageTick = sameWorld ? currentTick : 160;
            previous = null;
            settledPosition = null;
            frames = nearPlaneFrames = transitions = destinationFrames = 0;
            sameTickFrames = sameTickCrossings = 0;
            stageFrame = 0;
            maximumCameraStep = 0;
            verified = false;
            System.out.println("PORTAL_SMOKE_SEAMLESS_START phase=" + phaseName()
                    + " origin=" + portal.getOriginPos() + " destination=" + portal.getDestPos());
        }
        if (!controlled || currentTick < 160 || complete()) return;
        require(currentTick - phaseTick < 500, "Seamless movement did not complete: stage=" + stage
                + " position=" + client.player.position());
        boolean destination = isDestination(client);
        double distance = portal.getDistanceToPlane(unfold(client.player.getEyePosition(),
                destination));
        int elapsed = currentTick - stageTick;
        switch (stage) {
            case APPROACH -> {
                keys(client, true, false, false);
                if (distance < .85) advance(client, Stage.BEFORE_STOP);
            }
            case BEFORE_STOP -> {
                pause(client, destination, false, elapsed);
                if (elapsed >= 12 && frames - stageFrame >= 8)
                    advance(client, Stage.APPROACH_PLANE);
            }
            case APPROACH_PLANE -> {
                keys(client, true, false, true);
                require(!destination, "The player crossed before the straddling stop");
                if (distance < .16) advance(client, Stage.STRADDLE_STOP);
            }
            case STRADDLE_STOP -> {
                pause(client, destination, false, elapsed);
                require(distance > 0 && distance < .31,
                        "The player could not stop with its body across the source plane: "
                                + distance);
                if (elapsed >= 16 && frames - stageFrame >= 8) {
                    retreatStart = distance;
                    advance(client, Stage.RETREAT);
                }
            }
            case RETREAT -> {
                keys(client, false, true, false);
                require(!destination, "The portal pulled a retreating player through");
                if (elapsed >= 8) advance(client, Stage.RETREAT_STOP);
            }
            case RETREAT_STOP -> {
                pause(client, destination, false, elapsed);
                if (elapsed >= 12 && frames - stageFrame >= 8) {
                    require(distance > retreatStart + .5,
                            "The player could not retreat from the portal plane");
                    advance(client, Stage.CROSS);
                }
            }
            case CROSS -> {
                keys(client, true, false, true);
                if (destination) advance(client, Stage.EXIT_STOP);
            }
            case EXIT_STOP -> {
                pause(client, destination, true, elapsed);
                require(distance > -.31,
                        "The portal forced the stopped player out of its plane: " + distance);
                if (elapsed >= 20 && frames - stageFrame >= 8)
                    advance(client, Stage.RETURN);
            }
            case RETURN -> {
                keys(client, false, true, true);
                if (!destination) advance(client, Stage.RETURN_STOP);
            }
            case RETURN_STOP -> {
                pause(client, destination, false, elapsed);
                if (elapsed >= 16 && frames - stageFrame >= 8)
                    advance(client, Stage.FINAL_CROSS);
            }
            case FINAL_CROSS -> {
                keys(client, true, false, true);
                if (destination) advance(client, Stage.FINAL_STOP);
            }
            case FINAL_STOP -> {
                pause(client, destination, true, elapsed);
                if (elapsed >= 20 && frames - stageFrame >= 8)
                    advance(client, Stage.COMPLETE);
            }
            case COMPLETE -> {}
        }
    }

    private static void keys(Minecraft client, boolean forward, boolean backward, boolean sneak) {
        client.options.keyUp.setDown(forward);
        client.options.keyDown.setDown(backward);
        client.options.keyShift.setDown(sneak);
    }

    private static void advance(Minecraft client, Stage next) {
        keys(client, false, false, false);
        stage = next;
        stageTick = ticks;
        stageFrame = frames;
        settledPosition = null;
        System.out.println("PORTAL_SMOKE_SEAMLESS_STAGE " + stage + " tick=" + ticks
                + " position=" + client.player.position()
                + " velocity=" + client.player.getDeltaMovement());
    }

    private static void pause(Minecraft client, boolean destination, boolean expectedDestination,
            int elapsed) {
        keys(client, false, false, false);
        require(destination == expectedDestination,
                "The player crossed the plane without movement input: stage=" + stage);
        Vec3 position = unfold(client.player.position(), destination);
        if (elapsed == 6) settledPosition = position;
        if (elapsed > 6 && settledPosition != null)
            require(position.distanceTo(settledPosition) < .025,
                    "A stopped player drifted at the portal: stage=" + stage
                            + " start=" + settledPosition + " current=" + position
                            + " velocity=" + client.player.getDeltaMovement()
                            + " fluid=" + client.level.getFluidState(client.player.blockPosition()));
    }

    static boolean complete() {
        return !controlled || sameWorld && portal != null && stage == Stage.COMPLETE;
    }

    static void verify(Minecraft client) {
        if (!enabled(client) || verified) return;
        require(portal != null && complete(), "The seamless movement sequence did not finish");
        verifyPhase(client);
        if (sameWorld) System.out.println("PORTAL_SMOKE_SEAMLESS_SAME_WORLD_OK");
    }

    private static String phaseName() {
        return sameWorld ? "same_world" : controlled ? "cross_world" : "scaled";
    }

    private static void verifyPhase(Minecraft client) {
        require(client.options.ambientOcclusion().get(),
                "Seamless movement did not exercise smooth lighting");
        require(frames >= 30 && destinationFrames >= 2 && nearPlaneFrames >= (controlled ? 30 : 1),
                "The camera observer missed the portal plane: frames=" + frames
                        + " destination=" + destinationFrames + " near=" + nearPlaneFrames);
        require(transitions >= (controlled ? 3 : 1),
                "The rendered camera did not cross in both directions: " + transitions);
        verified = true;
        System.out.println("PORTAL_SMOKE_SEAMLESS_CAMERA_OK phase=" + phaseName() + " frames=" + frames
                + " near=" + nearPlaneFrames + " transitions=" + transitions
                + " sameTickFrames=" + sameTickFrames + " sameTickCrossings=" + sameTickCrossings
                + " maximumHorizontalStep=" + maximumCameraStep);
        if (controlled) System.out.println("PORTAL_SMOKE_SEAMLESS_MOVEMENT_OK");
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            require(qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface.invoker
                            .isSodiumPresent(),
                    "Installed Sodium did not initialize its portal adapter");
            require(qouteall.imm_ptl.core.render.RenderDiagnostics.sodiumViewports() > 0,
                    "Sodium did not adapt the moving player's portal viewports");
            System.out.println("PORTAL_SMOKE_SODIUM_COMPAT_OK");
        }
    }
}
