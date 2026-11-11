package qouteall.imm_ptl.core.block_manipulation;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import qouteall.q_misc_util.my_util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("resource")
public class BlockManipulationServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static record Context(
        ServerLevel world,
        @Nullable BlockHitResult blockHitResult
    ) {
    
    }
    
    public static final ThreadLocal<Context> REDIRECT_CONTEXT =
        ThreadLocal.withInitial(() -> null);
    
    /**
     * Use this event to conditionally disable cross portal block interaction.
     * The result will be ANDed.
     */
    public static final Event<Predicate<Player>> canDoCrossPortalInteractionEvent =
        EventFactory.createArrayBacked(Predicate.class,
            handlers -> player -> {
                for (Predicate<Player> handler : handlers) {
                    if (!handler.test(player)) {
                        return false;
                    }
                }
                return true;
            });
    
    public static Tuple<BlockHitResult, ResourceKey<Level>> getHitResultForPlacing(
        Level world,
        BlockHitResult blockHitResult
    ) {
        Direction side = blockHitResult.getDirection();
        Vec3 sideVec = Vec3.atLowerCornerOf(side.getUnitVec3i());
        BlockPos hitPos = blockHitResult.getBlockPos();
        Vec3 hitCenter = Vec3.atCenterOf(hitPos);
        
        List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);
        
        Portal portal = globalPortals.stream().filter(p ->
            p.getNormal().dot(sideVec) < -0.9
                && p.getPortalShape().isBoxInPortalProjection(
                p.getThisSideState(),
                new AABB(hitPos)
            ) && p.getDistanceToPlane(hitCenter) < 0.6
        ).findFirst().orElse(null);
        
        if (portal == null) {
            return new Tuple<>(blockHitResult, world.dimension());
        }
        
        Vec3 newCenter = portal.transformPoint(hitCenter.add(sideVec.scale(0.501)));
        BlockPos placingBlockPos = BlockPos.containing(newCenter);
        
        BlockHitResult newHitResult = new BlockHitResult(
            portal.transformPoint(blockHitResult.getLocation()),
            side.getOpposite(),
            placingBlockPos,
            blockHitResult.isInside()
        );
        
        return new Tuple<>(newHitResult, portal.getDestDim());
    }
    
    public static class RemoteCallables {
        /**
         * {@link qouteall.imm_ptl.core.mixin.client.interaction.MixinMultiPlayerGameMode#ip_redirectPacket}
         */
        @SuppressWarnings("JavadocReference")
        public static void processPlayerActionPacket(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            java.util.UUID[] path,
            Vec3 targetPoint,
            byte[] packetBytes
        ) {
            FriendlyByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
            ServerboundPlayerActionPacket packet;
            try { packet = ServerboundPlayerActionPacket.STREAM_CODEC.decode(buf); }
            finally { buf.release(); }
            
            ServerLevel world = player.level().getServer().getLevel(dimension);
            Validate.notNull(world, "missing %s", dimension.identifier());
            if (packet.getAction() != ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK
                    && PortalInteractionServer.validateBlock(player, dimension, packet.getPos(),
                            path, targetPoint, false) == null) {
                LOGGER.warn("Rejected unreachable cross-portal block action {} {}",
                        dimension.identifier(), packet.getPos());
                player.connection.ackBlockChangesUpTo(packet.getSequence());
                return;
            }
            
            withRedirect(
                new Context(world, null),
                () -> {
                    doProcessPlayerAction(world, player, packet);
                }
            );
        }
        
        /**
         * {@link qouteall.imm_ptl.core.mixin.client.interaction.MixinMultiPlayerGameMode#ip_redirectPacket}
         */
        @SuppressWarnings("JavadocReference")
        public static void processUseItemOnPacket(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            java.util.UUID[] path,
            Vec3 targetPoint,
            byte[] packetBytes
        ) {
            FriendlyByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
            ServerboundUseItemOnPacket packet;
            try { packet = ServerboundUseItemOnPacket.STREAM_CODEC.decode(buf); }
            finally { buf.release(); }
            
            ServerLevel world = player.level().getServer().getLevel(dimension);
            Validate.notNull(world, "missing %s", dimension.identifier());
            var target = PortalInteractionServer.validateBlock(player, dimension,
                    packet.getHitResult().getBlockPos(), path, targetPoint, true);
            if (target == null) {
                LOGGER.warn("Rejected unreachable cross-portal block use {} {}",
                        dimension.identifier(), packet.getHitResult().getBlockPos());
                player.connection.ackBlockChangesUpTo(packet.getSequence());
                return;
            }
            PortalInteractionServer.rememberContainer(player, target);
            
            withRedirect(
                new Context(world, packet.getHitResult()),
                () -> {
                    PortalInteractionTarget.withRotation(player, target.portals(), () -> {
                        PortalInteractionServer.withEntityAction(target,
                                () -> player.connection.handleUseItemOn(packet));
                        return null;
                    });
                }
            );
        }

        public static void processEntityAttackPacket(ServerPlayer player,
                ResourceKey<Level> dimension, java.util.UUID[] path, java.util.UUID[] projection,
                Vec3 point, byte[] bytes) {
            var packet = decode(net.minecraft.network.protocol.game.ServerboundAttackPacket.STREAM_CODEC, bytes);
            var world = player.level().getServer().getLevel(dimension);
            if (world == null) return;
            var entity = world.getEntityOrPart(packet.entityId());
            var target = PortalInteractionServer.validateEntity(player, dimension, entity,
                    path, projection, point, true);
            if (target == null) return;
            withRedirect(new Context(world, null), () -> PortalInteractionServer.withEntityAction(
                    target, () -> player.connection.handleAttack(packet)));
        }

        public static void processEntityUsePacket(ServerPlayer player,
                ResourceKey<Level> dimension, java.util.UUID[] path, java.util.UUID[] projection,
                Vec3 point, byte[] bytes) {
            var packet = decode(net.minecraft.network.protocol.game.ServerboundInteractPacket.STREAM_CODEC, bytes);
            var world = player.level().getServer().getLevel(dimension);
            if (world == null) return;
            var entity = world.getEntityOrPart(packet.entityId());
            var target = PortalInteractionServer.validateEntity(player, dimension, entity,
                    path, projection, point, false);
            if (target == null) return;
            var originalMenu = player.containerMenu;
            withRedirect(new Context(world, null), () -> PortalInteractionServer.withEntityAction(
                    target, () -> player.connection.handleInteract(packet)));
            if (player.containerMenu != originalMenu)
                PortalInteractionServer.rememberEntityContainer(player, target);
        }
    }
    
    private static <T> T decode(net.minecraft.network.codec.StreamCodec<? super FriendlyByteBuf, T> codec,
            byte[] bytes) {
        var buffer = IPMcHelper.bytesToBuf(bytes);
        try { return codec.decode(buffer); }
        finally { buffer.release(); }
    }

    public static void init() {
    
    }
    
    private static void withRedirect(
        Context context,
        Runnable runnable
    ) {
        Context original = REDIRECT_CONTEXT.get();
        REDIRECT_CONTEXT.set(context);
        try {
            PacketRedirection.withForceRedirect(
                context.world(), runnable
            );
        }
        finally {
            REDIRECT_CONTEXT.set(original);
        }
    }
    
    /**
     * {@link ServerGamePacketListenerImpl#handlePlayerAction(ServerboundPlayerActionPacket)}
     */
    @IPVanillaCopy
    private static void doProcessPlayerAction(ServerLevel world, ServerPlayer player, ServerboundPlayerActionPacket packet) {
        player.resetLastActionTime();
        BlockPos blockPos = packet.getPos();
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        
        if (isAttackingAction(action)) {
            player.gameMode.handleBlockBreakAction(
                blockPos, action, packet.getDirection(),
                world.getMaxY(), packet.getSequence()
            );
            player.connection.ackBlockChangesUpTo(packet.getSequence());
        }
    }
    
    public static boolean isAttackingAction(ServerboundPlayerActionPacket.Action action) {
        return action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK ||
            action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK ||
            action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK;
    }
    
    public static boolean validateReach(Player player, Level targetWorld, BlockPos targetPos) {
        return validateReach(player, targetWorld, targetPos, 3);
    }

    public static boolean validateReach(Player player, Level targetWorld, BlockPos targetPos, double padding) {
        return PortalInteractionServer.canKeepContainer(player, targetWorld, targetPos, padding);
    }
    
}
