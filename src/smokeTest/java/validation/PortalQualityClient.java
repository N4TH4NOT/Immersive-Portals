package validation;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.block_manipulation.CrossPortalPickupClient;
import qouteall.imm_ptl.core.render.NativeEntityPass;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Drives only ordinary movement keys and observes the actual camera of each independent client. */
final class PortalQualityClient {
    private record Frame(int tick, double time, Vec3 feet, Vec3 eye, Vec3 eyeStart, Vec3 eyeEnd,
            Vec3 camera, Vec3 direction, Vec3 velocity, boolean destination) {}

    private static PortalQualitySmoke.State state;
    private static Frame previous;
    private static int phase = -1;
    private static int phaseTick;
    private static int substage;
    private static int substageTick;
    private static int substageFrame;
    private static int ticks;
    private static int frames;
    private static int phaseFrames;
    private static int transitions;
    private static int nearFrames;
    private static int exactFrames;
    private static int exactCrossings;
    private static int peerRenderStates;
    private static int peerClippedBodies;
    private static boolean initialized;
    private static boolean ready;
    private static boolean acknowledged;
    private static boolean complete;
    private static boolean peerTracked;
    private static boolean first;
    private static UUID peer;
    private static Vec3 settled;
    private static long previousFrameNanos;
    private static final List<Long> performanceFrames = new ArrayList<>();

    private PortalQualityClient() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(PortalQualitySmoke.State.TYPE,
                (packet, context) -> context.client().execute(() -> state = packet));
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            if (state == null || complete) return;
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            var other = peer == null ? null : client.level.getPlayerByUUID(peer);
            if (other != null) {
                peerTracked = true;
                for (var entity : context.levelState().entityRenderStates)
                    if (entity instanceof AvatarRenderState avatar && avatar.id == other.getId())
                        peerRenderStates++;
                for (var body : NativeEntityPass.snapshots(context.levelState()))
                    if (!body.localPlayer() && body.entityId() == other.getId()) peerClippedBodies++;
            }
            if (WorldRenderInfo.isRendering()) return;
            require(client.level.dimension() == Level.OVERWORLD,
                    "A quality player's primary camera changed dimension");
            boolean destination = destination(client);
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(true);
            var forward = context.camera().forwardVector();
            var frame = new Frame(ticks, ticks + (double) partial,
                    unfold(client.player.position(), destination),
                    unfold(client.player.getEyePosition(partial), destination),
                    unfold(client.player.getEyePosition(0), destination),
                    unfold(client.player.getEyePosition(1), destination),
                    unfold(context.camera().position(), destination),
                    new Vec3(forward.x(), forward.y(), forward.z()).normalize(),
                    client.player.getDeltaMovement(), destination);
            frames++;
            phaseFrames++;
            if (Math.abs(frame.feet().z - 32) < .35) nearFrames++;
            require(Math.abs(frame.feet().y - 70) < .025 && horizontal(frame.velocity()) < .38,
                    "Quality traversal changed a player's flat-floor trajectory: " + frame);
            require(horizontal(frame.camera().subtract(frame.eye())) <= 1e-5,
                    "The quality camera did not match its interpolated eye: " + frame);
            if (previous != null) {
                double elapsed = frame.time() - previous.time();
                require(elapsed >= -.01, "The quality camera's simulation time moved backwards");
                require(horizontal(frame.camera().subtract(previous.camera()))
                                <= .38 * Math.max(0, elapsed) + .045,
                        "A quality camera jumped across the portal: previous=" + previous + " current=" + frame);
                require(frame.direction().dot(previous.direction()) > .995,
                        "Quality traversal changed camera orientation");
                if (frame.tick() == previous.tick()) {
                    exactFrames++;
                    require(frame.feet().distanceTo(previous.feet()) <= 1e-5,
                            "A frame-only quality crossing changed the player endpoint");
                    Vec3 expected = frame.eyeEnd().subtract(frame.eyeStart()).scale(Math.max(0, elapsed));
                    require(horizontal(frame.eye().subtract(previous.eye()).subtract(expected)) <= 1e-5,
                            "A quality crossing changed eye interpolation");
                } else {
                    int elapsedTicks = frame.tick() - previous.tick();
                    double allowance = elapsedTicks == 1 ? horizontal(previous.velocity()) + .115
                            : .38 * elapsedTicks + .045;
                    require(horizontal(frame.feet().subtract(previous.feet())) <= allowance,
                            "The portal added movement to a quality player");
                }
                if (destination != previous.destination()) {
                    transitions++;
                    if (frame.tick() == previous.tick()) {
                        exactCrossings++;
                        require(frame.velocity().distanceTo(previous.velocity()) < .025,
                                "A frame-only quality crossing changed velocity");
                    }
                    var handler = ((IEEntity) client.player).ip_getPortalCollisionHandler();
                    require(handler != null && handler.getCollidingPortals().stream().anyMatch(contact ->
                                    contact.getOriginPos().distanceTo(destination
                                            ? PortalQualitySmoke.DESTINATION : PortalQualitySmoke.SOURCE) < .01
                                            && contact.getNormal().z * (destination ? -1 : 1) > .99),
                            "A quality crossing lost the opposite wand face's collision contact");
                    System.out.println("PORTAL_QUALITY_CLIENT_CROSSED player="
                            + client.player.getGameProfile().name() + " phase=" + phase
                            + " previous=" + previous + " current=" + frame);
                }
            }
            previous = frame;
            if (phase == PortalQualitySmoke.PERFORMANCE && !acknowledged) {
                long now = System.nanoTime();
                if (previousFrameNanos != 0) performanceFrames.add(now - previousFrameNanos);
                previousFrameNanos = now;
            }
        });
    }

    static void tick(Minecraft client, int currentTick) {
        initialize();
        ticks = currentTick;
        require(currentTick < 6000, "The two-client quality scenario did not finish");
        client.options.keySprint.setDown(false);
        if (currentTick == 1) client.options.framerateLimit().set(30);
        if (!ready && currentTick >= 80) {
            ready = true;
            client.player.connection.sendCommand("portal_quality_ready");
        }
        if (state == null) {
            keys(client, false, false, false);
            return;
        }
        first = client.player.getUUID().equals(state.first());
        require(first || client.player.getUUID().equals(state.second()),
                "The quality protocol did not identify this independent player");
        require(!state.first().equals(state.second()), "The two quality players share an identity");
        peer = first ? state.second() : state.first();
        if (phase != state.phase()) {
            phase = state.phase();
            phaseTick = ticks;
            phaseFrames = 0;
            substage = 0;
            substageTick = ticks;
            substageFrame = frames;
            acknowledged = false;
            settled = null;
            previousFrameNanos = 0;
            keys(client, false, false, false);
            System.out.println("PORTAL_QUALITY_CLIENT_STAGE player="
                    + client.player.getGameProfile().name() + " phase=" + phase
                    + " position=" + client.player.position());
        }
        switch (phase) {
            case PortalQualitySmoke.APPROACH_A -> {
                if (first) approach(client);
                else keys(client, false, false, false);
            }
            case PortalQualitySmoke.APPROACH_B -> {
                if (!first) approach(client);
                else keys(client, false, false, false);
            }
            case PortalQualitySmoke.CROSS, PortalQualitySmoke.REJOIN_CROSS -> cross(client, false);
            case PortalQualitySmoke.RETURN -> cross(client, true);
            case PortalQualitySmoke.PERFORMANCE -> {
                keys(client, false, false, false);
                if (phaseFrames == 0)
                    net.minecraft.client.Screenshot.grab(client.gameDirectory,
                            "portal-quality-" + client.player.getGameProfile().name() + ".png",
                            client.gameRenderer.mainRenderTarget(), 1, message -> {});
                if (!acknowledged && performanceFrames.size() >= 40) {
                    var sorted = performanceFrames.stream().sorted().toList();
                    double median = sorted.get(sorted.size() / 2) / 1e6;
                    double p95 = sorted.get((sorted.size() - 1) * 95 / 100) / 1e6;
                    require(median <= 500 && p95 <= 2000,
                            "Two-client frame times exceeded the Mesa software-runner budget: medianMs="
                                    + median + " p95Ms=" + p95);
                    System.out.println("PORTAL_QUALITY_CLIENT_PERFORMANCE_OK player="
                            + client.player.getGameProfile().name() + " samples=" + sorted.size()
                            + " medianMs=" + median + " p95Ms=" + p95);
                    acknowledge(client);
                }
            }
            case PortalQualitySmoke.SAVE, PortalQualitySmoke.FINISH -> {
                keys(client, false, false, false);
                require(client.options.ambientOcclusion().get(), "Quality rendering disabled smooth lighting");
                require(transitions >= (state.reloaded() ? 1 : 2)
                                && frames >= (state.reloaded() ? 8 : 30)
                                && nearFrames >= (state.reloaded() ? 8 : 24),
                        "The quality camera did not observe the complete natural movement sequence");
                require(peerTracked
                                && (state.reloaded() || peerRenderStates > 0),
                        "The other actual player was never tracked and extracted for rendering: tracked="
                                + peerTracked + " rendered=" + peerRenderStates + " clipped="
                                + peerClippedBodies + " peer=" + peer);
                if (first && !state.reloaded()) {
                    var animation = CrossPortalPickupClient.lastAnimation();
                    require(CrossPortalPickupClient.animationCount() >= 2 && animation != null
                                    && animation.collector() == client.player.getId()
                                    && animation.world() == Level.NETHER
                                    && animation.item().distanceTo(animation.target()) < 3,
                            "Remote pickup did not produce a nearby cross-world animation anchor: " + animation);
                    System.out.println("PORTAL_QUALITY_PICKUP_ANIMATION_OK count="
                            + CrossPortalPickupClient.animationCount() + " last=" + animation);
                }
                complete = true;
                System.out.println("PORTAL_QUALITY_CAMERA_OK player=" + client.player.getGameProfile().name()
                        + " frames=" + frames + " near=" + nearFrames + " transitions=" + transitions
                        + " sameTickFrames=" + exactFrames + " sameTickCrossings=" + exactCrossings);
                System.out.println("PORTAL_QUALITY_TWO_PLAYERS_OK player=" + client.player.getGameProfile().name()
                        + " peer=" + peer + " rendered=" + peerRenderStates + " clipped=" + peerClippedBodies);
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                    require(qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface.invoker
                                    .isSodiumPresent()
                                    && qouteall.imm_ptl.core.render.RenderDiagnostics.sodiumViewports() > 0,
                            "The two-player scenario did not exercise Sodium's portal viewports");
                    System.out.println("PORTAL_QUALITY_SODIUM_COMPAT_OK");
                }
                System.out.println("PORTAL_QUALITY_COMPLETE player=" + client.player.getGameProfile().name()
                        + " phase=" + (state.reloaded() ? "rejoin" : "save"));
                client.stop();
            }
            default -> keys(client, false, false, false);
        }
    }

    private static void approach(Minecraft client) {
        boolean initialDestination = !first;
        require(destination(client) == initialDestination,
                "A quality player crossed before its controlled stop");
        double distance = Math.abs(client.player.getZ() - 32);
        switch (substage) {
            case 0 -> {
                keys(client, true, false, false);
                if (distance < .85) next(client);
            }
            case 1 -> {
                pause(client);
                if (ticks - substageTick >= 12 && frames - substageFrame >= 8) next(client);
            }
            case 2 -> {
                keys(client, true, false, true);
                if (distance < .16) next(client);
            }
            default -> {
                pause(client);
                require(distance > 0 && distance < .31,
                        "The quality player could not hold a partially crossed body");
                if (!acknowledged && ticks - substageTick >= 16 && frames - substageFrame >= 8)
                    acknowledge(client);
            }
        }
    }

    private static void cross(Minecraft client, boolean returning) {
        boolean expectedDestination = returning ? !first : first;
        if (substage == 0) {
            // A rejoin can start only a few native ticks from the plane. Establish
            // a real camera baseline before input, even while terrain is compiling.
            if (phaseFrames == 0) {
                keys(client, false, false, false);
                return;
            }
            keys(client, !returning, returning, true);
            if (destination(client) == expectedDestination) next(client);
        } else {
            pause(client);
            require(destination(client) == expectedDestination && Math.abs(client.player.getZ() - 32) < .31,
                    "The quality portal forced a stopped player out of its plane");
            if (!acknowledged && ticks - substageTick >= 20 && frames - substageFrame >= 8)
                acknowledge(client);
        }
    }

    private static void pause(Minecraft client) {
        keys(client, false, false, false);
        if (ticks - substageTick == 6) settled = client.player.position();
        if (settled != null && ticks - substageTick > 6)
            require(client.player.position().distanceTo(settled) < .025,
                    "The stopped quality player drifted: phase=" + phase + " start=" + settled
                            + " actual=" + client.player.position());
    }

    private static void next(Minecraft client) {
        keys(client, false, false, false);
        substage++;
        substageTick = ticks;
        substageFrame = frames;
        settled = null;
    }

    private static void acknowledge(Minecraft client) {
        acknowledged = true;
        client.player.connection.sendCommand("portal_quality_checkpoint " + phase);
    }

    private static void keys(Minecraft client, boolean forward, boolean backward, boolean sneak) {
        client.options.keyUp.setDown(forward);
        client.options.keyDown.setDown(backward);
        client.options.keyShift.setDown(sneak);
    }

    private static boolean destination(Minecraft client) {
        return client.player.getX() > 32;
    }

    private static Vec3 unfold(Vec3 position, boolean destination) {
        return destination ? position.add(-64, 0, 0) : position;
    }

    private static double horizontal(Vec3 vector) {
        return Math.hypot(vector.x, vector.z);
    }
}
