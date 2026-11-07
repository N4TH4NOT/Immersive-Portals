package validation;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.network.PacketRedirectionClient;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.NativeWorldRenderer;

/** End-to-end sound delivery, single-rate remote particles and native environment extraction. */
final class PortalEnvironmentSmoke {
    private record Heard(String route, Vec3 position, float volume, float pitch, float radius,
            SoundInstance.Attenuation attenuation, boolean relative,
            ResourceKey<Level> redirected, ResourceKey<Level> activeLevel) {}

    private static final java.util.List<Heard> sounds = new java.util.ArrayList<>();
    private static final String[] ROUTES = {"remote", "shortcut", "direct"};
    private static int started = -1, readyAt = -1, requestedAt, route;
    private static int lastParticleTicks = -1, particleSamples;
    private static int remoteEnvironmentFrames, wetRootFrames;
    private static ProbeParticle particle;
    private static boolean observing, complete;
    private static SoundEventListener listener;

    private PortalEnvironmentSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ProbeParticle extends NoRenderParticle {
        final ClientLevel world;
        int ticks;

        ProbeParticle(ClientLevel world) {
            super(world, 128, 82, 384);
            this.world = world;
            setLifetime(1000);
        }

        @Override
        public void tick() {
            var client = Minecraft.getInstance();
            require(client.level == level && client.particleEngine == NativeWorldRenderer.particles(level),
                    "A remote particle tick used the physical player's world or particle engine");
            ticks++;
            super.tick();
        }
    }

    private static void initialize(Minecraft client) {
        client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        IPGlobal.renderMode = IPGlobal.RenderMode.normal;
        IPGlobal.enableCrossPortalSound = true;
        IPCGlobal.isClientRemoteTickingEnabled = true;
        listener = (sound, event, radius) -> {
            String acousticRoute = switch (sound.getIdentifier().toString()) {
                case "minecraft:block.note_block.bell" -> "remote";
                case "minecraft:block.note_block.chime" -> "shortcut";
                case "minecraft:block.note_block.pling" -> "direct";
                default -> null;
            };
            if (acousticRoute == null || !observing) return;
            require(sound.getSource() == SoundSource.BLOCKS,
                    "The acoustic fixture changed sound category");
            sounds.add(new Heard(acousticRoute, new Vec3(sound.getX(), sound.getY(), sound.getZ()),
                    sound.getVolume(), sound.getPitch(), radius, sound.getAttenuation(),
                    sound.isRelative(), PacketRedirectionClient.clientTaskRedirection.get(),
                    client.level.dimension()));
        };
        client.getSoundManager().addListener(listener);
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            if (!observing) return;
            float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
            var world = context.level();
            var state = context.levelState();
            require(Math.abs(state.weatherRenderState.intensity - world.getRainLevel(partial)) < 1.0E-5,
                    "The weather extraction reused another world's rain intensity");
            if (world.dimension() == Level.OVERWORLD
                    && context.camera().position().distanceTo(new Vec3(256, 81.62, 380)) < 1) {
                if (world.getRainLevel(partial) > .75) wetRootFrames++;
                return;
            }
            if (world.dimension() != Level.NETHER
                    || context.camera().position().distanceTo(PortalEnvironmentFixture.REMOTE_ENTRY) > 16)
                return;
            require(world != client.player.level(), "The remote environment probe used the player's world");
            require(world.getRainLevel(partial) < .01 && state.weatherRenderState.rainColumns.isEmpty()
                            && state.weatherRenderState.snowColumns.isEmpty(),
                    "A dry Nether view inherited the wet Overworld's weather");
            // An independently seeded native probe samples the destination's biome and time.
            // These values must be initialized before the native fog and sky extraction.
            var expected = new EnvironmentAttributeProbe();
            expected.tick(world, context.camera().position());
            var actual = context.camera().attributeProbe();
            for (var attribute : java.util.List.of(EnvironmentAttributes.FOG_COLOR,
                    EnvironmentAttributes.AMBIENT_LIGHT_COLOR, EnvironmentAttributes.SKY_COLOR))
                require(actual.getValue(attribute, partial).equals(expected.getValue(attribute, partial)),
                        "The portal camera used another world's environment color: " + attribute);
            float fogStart = expected.getValue(EnvironmentAttributes.FOG_START_DISTANCE, partial);
            float fogEnd = expected.getValue(EnvironmentAttributes.FOG_END_DISTANCE, partial);
            require(Math.abs(state.cameraRenderState.fogData.environmentalStart - fogStart) < .001
                            && Math.abs(state.cameraRenderState.fogData.environmentalEnd - fogEnd) < .001,
                    "The dry destination fog did not use the destination environment distances");
            float sky = actual.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partial);
            require(Math.abs(sky - world.environmentAttributes()
                            .getDimensionValue(EnvironmentAttributes.SKY_LIGHT_FACTOR)) < 1.0E-5,
                    "The Nether camera inherited the surface sky-light factor");
            remoteEnvironmentFrames++;
        });
    }

    private static boolean ready(Minecraft client) {
        if (client.level.dimension() != Level.OVERWORLD
                || client.player.position().distanceTo(new Vec3(256, 80, 380)) > .05
                || client.level.getRainLevel(1) < .75) return false;
        // Packet handling can update the player before the next completed frame.
        // Sound routing uses the actual main camera as its listener.
        var camera = client.gameRenderer.mainCamera();
        if (!camera.isInitialized()
                || camera.position().distanceTo(client.player.getEyePosition()) > .02
                || Math.abs(camera.yRot()) > .01 || Math.abs(camera.xRot()) > .01)
            return false;
        var remote = ClientWorldLoader.getWorld(Level.NETHER);
        if (!remote.getBlockState(new BlockPos(128, 79, 384)).is(Blocks.GOLD_BLOCK)
                || !client.level.getBlockState(new BlockPos(256, 79, 384)).is(Blocks.EMERALD_BLOCK))
            return false;
        boolean remoteOpening = false, shortcutOpening = false, shortcutEntry = false;
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof Portal portal) {
                if (portal.getDestDim() == Level.NETHER
                        && portal.getOriginPos().distanceTo(PortalEnvironmentFixture.REMOTE_EXIT) < .01)
                    remoteOpening = portal.getDistanceToNearestPointInPortal(client.player.getEyePosition()) < 10;
                if (portal.getDestDim() == Level.OVERWORLD
                        && portal.getOriginPos().distanceTo(PortalEnvironmentFixture.SHORTCUT_EXIT) < .01)
                    shortcutOpening = true;
                if (portal.getDestDim() == Level.OVERWORLD
                        && portal.getOriginPos().distanceTo(PortalEnvironmentFixture.SHORTCUT_ENTRY) < .01)
                    shortcutEntry = true;
            }
        // A ready exit and its chunk packets do not imply that the remote entry
        // entity has arrived. The native acoustic search starts at that entry.
        boolean remoteEntry = false;
        for (var entity : remote.entitiesForRendering())
            if (entity instanceof Portal portal && portal.getDestDim() == Level.OVERWORLD
                    && portal.getOriginPos().distanceTo(PortalEnvironmentFixture.REMOTE_ENTRY) < .01)
                remoteEntry = true;
        return remoteOpening && remoteEntry && shortcutOpening && shortcutEntry;
    }

    private static void validateSound(Minecraft client, Heard heard) {
        require(heard.route.equals(ROUTES[route]), "The native sound routes arrived out of order: " + sounds);
        require(heard.redirected == (route == 0 ? Level.NETHER : Level.OVERWORLD)
                        && heard.activeLevel == heard.redirected,
                "The server sound did not reach SoundManager through its redirected world: " + heard);
        require(Math.abs(heard.volume - 2) < 1.0E-5 && Math.abs(heard.pitch - .75) < 1.0E-5
                        && Math.abs(heard.radius - 32) < 1.0E-5
                        && heard.attenuation == SoundInstance.Attenuation.LINEAR && !heard.relative,
                "The portal sound changed native gain, pitch or attenuation: " + heard);
        Vec3 listenerPosition = client.gameRenderer.mainCamera().position();
        Vec3 expected;
        double acousticDistance;
        if (route == 2) {
            expected = PortalEnvironmentFixture.DIRECT_SOUND;
            acousticDistance = expected.distanceTo(listenerPosition);
        } else {
            Vec3 exit = route == 0 ? PortalEnvironmentFixture.REMOTE_EXIT
                    : PortalEnvironmentFixture.SHORTCUT_EXIT;
            // Independent fixture geometry: four blocks before the source aperture,
            // scaled by two only for the cross-dimensional route, then the exit leg.
            acousticDistance = (route == 0 ? 8 : 4) + exit.distanceTo(listenerPosition);
            expected = listenerPosition.add(exit.subtract(listenerPosition).normalize().scale(acousticDistance));
            if (route == 1)
                require(acousticDistance < PortalEnvironmentFixture.SHORTCUT_SOUND.distanceTo(listenerPosition),
                        "The same-world fixture did not provide a shorter portal route");
        }
        require(heard.position.distanceTo(expected) < .02
                        && Math.abs(heard.position.distanceTo(listenerPosition) - acousticDistance) < .02,
                "Native playback used the wrong acoustic direction or folded distance: " + heard
                        + " expected=" + expected + " distance=" + acousticDistance);
        System.out.println("PORTAL_SOUND_NATIVE_ROUTE_OK route=" + heard.route
                + " distance=" + acousticDistance + " position=" + heard.position
                + " gain=" + heard.volume + " radius=" + heard.radius);
    }

    static boolean tick(Minecraft client, int ticks) {
        if (complete) return true;
        if (started < 0) {
            started = ticks;
            initialize(client);
            client.player.connection.sendCommand("portal_environment_setup");
        }
        require(ticks - started < 400, "The native environment/audio fixture stalled");
        if (readyAt < 0) {
            if (!ready(client)) return false;
            readyAt = ticks;
            observing = true;
            particle = new ProbeParticle(ClientWorldLoader.getWorld(Level.NETHER));
            NativeWorldRenderer.particles(particle.world).add(particle);
            requestedAt = ticks;
            client.player.connection.sendCommand("portal_environment_sound " + ROUTES[route]);
        }
        if (ticks - readyAt >= 2) {
            if (lastParticleTicks >= 0) {
                require(particle.ticks - lastParticleTicks == 1,
                        "Remote particles did not advance once per client tick: "
                                + lastParticleTicks + " -> " + particle.ticks);
                particleSamples++;
            }
            lastParticleTicks = particle.ticks;
        }
        if (route < ROUTES.length) {
            require(ticks - requestedAt < 100, "No native sound playback arrived for " + ROUTES[route]);
            if (sounds.size() > route) {
                validateSound(client, sounds.get(route));
                if (++route < ROUTES.length) {
                    requestedAt = ticks;
                    client.player.connection.sendCommand("portal_environment_sound " + ROUTES[route]);
                }
            }
        }
        if (route < ROUTES.length || particleSamples < 20 || remoteEnvironmentFrames < 5
                || wetRootFrames < 5) return false;
        require(sounds.size() == 3, "A server sound was played more than once: " + sounds);
        client.getSoundManager().removeListener(listener);
        particle.remove();
        observing = false;
        complete = true;
        System.out.println("PORTAL_SOUND_NATIVE_OK routes=3 redirected=true");
        System.out.println("PORTAL_REMOTE_PARTICLE_RATE_OK samples=" + particleSamples + " rate=1");
        System.out.println("PORTAL_REMOTE_ENVIRONMENT_OK dryNetherFrames=" + remoteEnvironmentFrames
                + " wetOverworldFrames=" + wetRootFrames);
        return true;
    }
}
