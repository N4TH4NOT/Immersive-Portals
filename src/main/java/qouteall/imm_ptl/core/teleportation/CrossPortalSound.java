package qouteall.imm_ptl.core.teleportation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.NativeWorldRenderer;

import java.util.Comparator;

@Environment(EnvType.CLIENT)
public class CrossPortalSound {
    public static final float VOLUME_RADIUS_MULT = 16f;
    public static final float MIN_SOUND_RADIUS = 16f;
    
    public static boolean isPlayerWorld(ClientLevel world) {
        var player = Minecraft.getInstance().player;
        return player != null && world.dimension() == player.level().dimension();
    }

    private record Route(Vec3 position, double distance) {}
    
    @Nullable
    public static SimpleSoundInstance createCrossPortalSound(
        ClientLevel soundWorld,
        SoundEvent soundEvent,
        SoundSource soundSource,
        Vec3 soundPos,
        float soundVol,
        float soundPitch,
        long seed
    ) {
        Minecraft client = Minecraft.getInstance();
        
        if (client.player == null) {
            return null;
        }
        
        net.minecraft.util.profiling.Profiler.get().push("cross_portal_sound");
        
        try {
            double searchRadius = Math.min(128,
                    Math.max(VOLUME_RADIUS_MULT * soundVol, MIN_SOUND_RADIUS));
            Vec3 listener = NativeWorldRenderer.isRenderingScene()
                            && RenderStates.originalCamera != null
                    ? RenderStates.originalCamera.position()
                    : client.gameRenderer.mainCamera().position();
            if (!client.gameRenderer.mainCamera().isInitialized())
                listener = client.player.getEyePosition();
            final Vec3 listenerPosition = listener;
            var listenerDimension = client.player.level().dimension();
            double directDistance = isPlayerWorld(soundWorld)
                    ? listener.distanceTo(soundPos) : Double.POSITIVE_INFINITY;

            Route route = IPMcHelper.getNearbyPortalList(
                    soundWorld, soundPos, searchRadius + IPGlobal.maxNormalPortalRadius,
                    portal -> portal.getDestDim() == listenerDimension && portal.isPortalValid()
                            && (!portal.getPortalShape().isPlanar()
                                || portal.getDistanceToPlane(soundPos) >= 0))
                    .stream().map(portal -> {
                        Vec3 entry = portal.getNearestPointInPortal(soundPos);
                        Vec3 exit = portal.transformPoint(entry);
                        double sourceDistance = entry.distanceTo(soundPos) * portal.getScaling();
                        double distance = sourceDistance + exit.distanceTo(listenerPosition);
                        Vec3 position = PortalSoundPath.virtualSource(listenerPosition, exit,
                                sourceDistance, portal.transformPoint(soundPos).subtract(exit));
                        return new Route(position, distance);
                    }).filter(candidate -> candidate.distance() + 1.0E-7 < directDistance)
                    .min(Comparator.comparingDouble(Route::distance)).orElse(null);
            if (route == null) return null;
            // Keep vanilla gain and attenuation. Position encodes the whole acoustic path,
            // so the source-to-opening leg is counted once, including portal scaling.
            return new SimpleSoundInstance(soundEvent, soundSource, soundVol, soundPitch,
                    RandomSource.create(seed), route.position().x, route.position().y,
                    route.position().z);
        } finally {
            net.minecraft.util.profiling.Profiler.get().pop();
        }
    }
}
