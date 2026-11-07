package qouteall.imm_ptl.core.block_manipulation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ClientWorldLoader;

/** Keeps pickup animation anchors valid even when the collector is not tracked locally. */
@Environment(EnvType.CLIENT)
public final class CrossPortalPickupClient {
    public record Anchor(ResourceKey<Level> world, int collector, Vec3 position) {}
    public record Animation(int collector, ResourceKey<Level> world, Vec3 item, Vec3 target) {}

    private static final ThreadLocal<Anchor> ACTIVE = new ThreadLocal<>();
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("imm_ptl.renderDiagnostics");
    private static long animationCount;
    private static Animation lastAnimation;

    private CrossPortalPickupClient() {}

    public static Anchor currentAnchor() {
        return ACTIVE.get();
    }

    public static long animationCount() {
        return animationCount;
    }

    public static Animation lastAnimation() {
        return lastAnimation;
    }

    public static void recordAnimation(int collector, ResourceKey<Level> world,
            Vec3 item, Vec3 target) {
        if (!DIAGNOSTICS) return;
        animationCount++;
        lastAnimation = new Animation(collector, world, item, target);
        System.out.println("PORTAL_PICKUP_ANIMATION collector=" + collector
                + " world=" + world.identifier() + " item=" + item + " target=" + target);
    }

    public static final class RemoteCallables {
        private RemoteCallables() {}

        public static void take(ResourceKey<Level> world, int itemId, int collectorId,
                int count, Vec3 target) {
            var client = Minecraft.getInstance();
            if (client.getConnection() == null) return;
            ClientWorldLoader.withSwitchedWorldFailSoft(world, () -> {
                Anchor previous = ACTIVE.get();
                ACTIVE.set(new Anchor(world, collectorId, target));
                try {
                    client.getConnection().handleTakeItemEntity(
                            new ClientboundTakeItemEntityPacket(itemId, collectorId, count));
                } finally {
                    if (previous == null) ACTIVE.remove();
                    else ACTIVE.set(previous);
                }
            });
        }
    }
}
