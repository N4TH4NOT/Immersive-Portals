package validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;

/** Exercise real keybind dispatch, transformed reach, two-hop paths and no-op block callbacks. */
final class PortalInteractionSmoke {
    private static final java.util.Set<String> ACKNOWLEDGED = new java.util.HashSet<>();

    static void init() {
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> ACKNOWLEDGED.add(message.getString()));
    }

    static void tick(Minecraft client, int ticks) {
        if (ticks == 110) {
            if (!(BlockManipulationClient.remoteHitResult instanceof BlockHitResult hit)
                    || !hit.getBlockPos().equals(new BlockPos(0, 71, -1))
                    || BlockManipulationClient.remotePortalPath.size() != 2)
                throw new AssertionError(
                        "Block through two portals was not targeted: "
                                + BlockManipulationClient.remoteHitResult
                                + " "
                                + BlockManipulationClient.remotePortalPath);
            client.options.keyUse.setDown(true);
        }
        if (ticks == 112) client.options.keyUse.setDown(false);
        if (ticks == 120) {
            net.minecraft.client.KeyMapping.click(client.options.keyAttack.getDefaultKey());
            client.options.keyAttack.setDown(true);
        }
        if (ticks == 122) client.options.keyAttack.setDown(false);
        if (ticks == 132) {
            var world = BlockManipulationClient.getRemotePointedWorld();
            if (world == null || !world.getBlockState(new BlockPos(0, 71, -1)).is(Blocks.STONE))
                throw new AssertionError("No-op block callback did not restore predicted changes");
            if (!ACKNOWLEDGED.containsAll(
                    java.util.List.of(
                            "MINESTOM_REMOTE_BLOCK_USE 2", "MINESTOM_REMOTE_BLOCK_ATTACK 2")))
                throw new AssertionError(
                        "Server did not receive both block callbacks: " + ACKNOWLEDGED);
            System.out.println("PORTAL_SMOKE_NO_ACTION_BLOCK_OK");
        }
        if (ticks == 140) {
            Vec3 delta = new Vec3(1.5, 71.4, -.4).subtract(client.player.getEyePosition());
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setXRot(
                    (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
        }
        if (ticks == 145) {
            if (!(BlockManipulationClient.remoteHitResult instanceof EntityHitResult)
                    || BlockManipulationClient.remotePortalPath.size() != 1)
                throw new AssertionError(
                        "Remote entity was not targeted: "
                                + BlockManipulationClient.remoteHitResult
                                + " reach="
                                + client.player.entityInteractionRange()
                                + " eye="
                                + client.player.getEyePosition());
            client.options.keyUse.setDown(true);
        }
        if (ticks == 147) client.options.keyUse.setDown(false);
        if (ticks == 150) {
            net.minecraft.client.KeyMapping.click(client.options.keyAttack.getDefaultKey());
            client.options.keyAttack.setDown(true);
        }
        if (ticks == 152) {
            client.options.keyAttack.setDown(false);
            System.out.println("PORTAL_SMOKE_ENTITY_INTERACTION_SENT");
        }
        if (ticks == 158) {
            if (!ACKNOWLEDGED.containsAll(
                    java.util.List.of(
                            "MINESTOM_REMOTE_ENTITY_USE_OK", "MINESTOM_REMOTE_ENTITY_ATTACK_OK")))
                throw new AssertionError(
                        "Server did not receive both entity callbacks: " + ACKNOWLEDGED);
            System.out.println("PORTAL_SMOKE_ENTITY_INTERACTION_OK");
            client.player.setYRot(180);
            client.player.setXRot(0);
        }
    }
}
