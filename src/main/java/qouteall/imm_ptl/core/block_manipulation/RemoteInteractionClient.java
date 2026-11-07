package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import qouteall.imm_ptl.core.network.bridge.BridgeClient;

/** Native and bridge clients follow the same bounded, scaled portal path. */
final class RemoteInteractionClient {
    private static final Minecraft CLIENT = Minecraft.getInstance();

    static void updateTarget(float partialTick) {
        double blockReach = CLIENT.player.blockInteractionRange();
        double entityReach = Math.max(CLIENT.player.entityInteractionRange(),
                CLIENT.player.getAttackRangeWith(CLIENT.player.getMainHandItem())
                        .effectiveMaxRange(CLIENT.player));
        if (BridgeClient.isActive()) {
            blockReach = Math.min(6, blockReach);
            entityReach = Math.min(6, entityReach);
        }
        var target = PortalInteractionTarget.trace(CLIENT.player, partialTick, blockReach, entityReach);
        if (target == null) return;
        // The bridge protocol has no projected-body identity; native Fabric validates it separately.
        if (BridgeClient.isActive() && target.projectionPortal() != null) return;
        var direction = CLIENT.player.getViewVector(partialTick);
        CLIENT.hitResult = BlockHitResult.miss(target.firstPortalHit(),
                Direction.getApproximateNearest(direction.x, direction.y, direction.z),
                BlockPos.containing(target.firstPortalHit()));
        CLIENT.crosshairPickEntity = null;
        if (BridgeClient.isActive() && target.portals().size() > 3) return;
        if (target.hit().getType() == HitResult.Type.MISS) return;
        BlockManipulationClient.remotePointedDim = target.world().dimension();
        BlockManipulationClient.remoteHitResult = target.hit();
        BlockManipulationClient.remotePortalPath = target.portals().stream()
                .map(portal -> portal.getUUID()).toList();
        BlockManipulationClient.remotePortals = target.portals();
        BlockManipulationClient.remoteProjectionPortal = target.projectionPortal();
        BlockManipulationClient.remoteHitDistance = target.distance();
        if (target.hit() instanceof EntityHitResult hit) CLIENT.crosshairPickEntity = hit.getEntity();
    }
}
