package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import qouteall.q_misc_util.my_util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.function.Supplier;

public class BlockManipulationClient {
    private static final Minecraft client = Minecraft.getInstance();
    
    public static ResourceKey<Level> remotePointedDim;
    public static HitResult remoteHitResult;
    public static java.util.List<java.util.UUID> remotePortalPath = java.util.List.of();
    static java.util.List<Portal> remotePortals = java.util.List.of();
    @Nullable public static Portal remoteProjectionPortal;
    
    public static boolean isPointingToPortal() {
        return remotePointedDim != null;
    }
    
    @Nullable
    public static ClientLevel getRemotePointedWorld() {
        if (remotePointedDim == null) {
            return null;
        }
        return ClientWorldLoader.getWorld(BlockManipulationClient.remotePointedDim);
    }
    
    public static double remoteHitDistance;

    public static void updatePointedBlock(float partialTick) {
        remotePointedDim = null;
        remoteHitResult = null;
        remotePortalPath = java.util.List.of();
        remotePortals = java.util.List.of();
        remoteProjectionPortal = null;
        remoteHitDistance = Double.POSITIVE_INFINITY;
        if (client.gameMode == null || client.level == null || client.player == null) return;
        if (!BlockManipulationServer.canDoCrossPortalInteractionEvent.invoker().test(client.player)) return;
        RemoteInteractionClient.updateTarget(partialTick);
    }

    static BlockHitResult clipBlocks(ClientLevel world, Vec3 from, Vec3 to) {
        return PortalInteractionTarget.clipBlocks(world, client.player, from, to);
    }

    public static boolean isRemoteEntity(net.minecraft.world.entity.Entity entity) {
        return ClientWorldLoader.getIsWorldSwitched()
                && remoteHitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() == entity
                && (!remotePortalPath.isEmpty() || remoteProjectionPortal != null);
    }

    public static boolean isRemoteEntityWithinInteractionRange(net.minecraft.world.entity.Entity entity) {
        return isRemoteEntity(entity) && remoteHitDistance <= client.player.entityInteractionRange();
    }

    public static boolean isRemoteEntityWithinAttackRange(
            net.minecraft.world.item.component.AttackRange range) {
        return remoteHitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && isRemoteEntity(hit.getEntity())
                && remoteHitDistance >= range.effectiveMinRange(client.player)
                && remoteHitDistance <= range.effectiveMaxRange(client.player);
    }

    public static HitResult outlineHitForView(ClientLevel world, HitResult original) {
        if (!qouteall.imm_ptl.core.render.context_management.WorldRenderInfo.isRendering()) return original;
        if (world.dimension() == remotePointedDim && remoteHitResult instanceof BlockHitResult
                && qouteall.imm_ptl.core.render.context_management.PortalRendering
                        .matchesPortalPath(remotePortalPath)) return remoteHitResult;
        return null;
    }

    public static <T> T withRemoteRotation(Supplier<T> action) {
        if (qouteall.imm_ptl.core.network.bridge.BridgeClient.isActive()) return action.get();
        return PortalInteractionTarget.withRotation(client.player, remotePortals,
                remoteProjectionPortal, action);
    }

    /**
     * It will not switch the dimension of client player
     */
    public static <T> T withSwitchedContext(
        Supplier<T> func, boolean transformHitResult
    ) {
        Validate.notNull(remoteHitResult);
        
        ClientLevel remoteWorld = getRemotePointedWorld();
        Validate.notNull(remoteWorld);
        
        HitResult effectiveHitResult;
        
        if (transformHitResult && (remoteHitResult instanceof BlockHitResult blockHitResult)) {
            Tuple<BlockHitResult, ResourceKey<Level>> r =
                BlockManipulationServer.getHitResultForPlacing(remoteWorld, blockHitResult);
            effectiveHitResult = r.getA();
            remoteWorld = ClientWorldLoader.getWorld(r.getB());
            Validate.notNull(remoteWorld);
            Validate.notNull(effectiveHitResult);
        }
        else {
            effectiveHitResult = remoteHitResult;
        }
        
        return ClientWorldLoader.withSwitchedWorld(
            remoteWorld, () -> {
                HitResult originalHitResult = client.hitResult;
                client.hitResult = effectiveHitResult;
                try {
                    return func.get();
                }
                finally {
                    client.hitResult = originalHitResult;
                }
            }
        );
    }

    @Nullable
    public static String getDebugString() {
        if (remotePointedDim == null) {
            return null;
        }
        if (remoteHitResult instanceof BlockHitResult blockHitResult) {
            return "Point:%s %d %d %d".formatted(
                remotePointedDim.identifier(),
                blockHitResult.getBlockPos().getX(),
                blockHitResult.getBlockPos().getY(),
                blockHitResult.getBlockPos().getZ()
            );
        }
        else {
            return null;
        }
    }
}
