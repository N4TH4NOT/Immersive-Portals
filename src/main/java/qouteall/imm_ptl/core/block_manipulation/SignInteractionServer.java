package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Keeps a native sign editor bound to the validated world across asynchronous text filtering. */
public final class SignInteractionServer {
    public record Session(SignBlockEntity sign, boolean front, UUID token, UUID[] path, Vec3 point) {}
    private record Pending(ServerPlayer player, Session session) {}
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerboundSignUpdatePacket, Pending> PENDING = new WeakHashMap<>();
    private static final ThreadLocal<Session> OPENING = new ThreadLocal<>();
    private static final ThreadLocal<Session> APPLYING = new ThreadLocal<>();

    private SignInteractionServer() {}

    public static void open(ServerPlayer player, SignBlockEntity sign, boolean front, Runnable original) {
        var target = PortalInteractionServer.currentEntityTarget();
        if (target == null || !(target.hit() instanceof BlockHitResult hit)
                || sign.getLevel() != PortalInteractionServer.entityWorld()
                || hit.getBlockPos().distManhattan(sign.getBlockPos()) > 1) {
            SESSIONS.remove(player);
            original.run();
            return;
        }
        var session = new Session(sign, front, UUID.randomUUID(),
                target.portals().stream().map(Portal::getUUID).toArray(UUID[]::new),
                hit.getBlockPos().equals(sign.getBlockPos()) ? hit.getLocation()
                        : Vec3.atCenterOf(sign.getBlockPos()));
        SESSIONS.put(player, session);
        var previous = OPENING.get();
        OPENING.set(session);
        try { PacketRedirection.withForceRedirect((ServerLevel) sign.getLevel(), original); }
        finally { OPENING.set(previous); }
    }

    @Nullable
    public static ServerLevel openingWorld() {
        var session = OPENING.get();
        return session == null ? null : (ServerLevel) session.sign().getLevel();
    }

    public static net.minecraft.network.protocol.Packet<?> editorPacket(
            net.minecraft.network.protocol.Packet<?> original) {
        var session = OPENING.get();
        if (session == null || !(original instanceof net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket))
            return original;
        return McRemoteProcedureCall.createPacketToSendToClient(
                "qouteall.imm_ptl.core.block_manipulation.SignInteractionClient.RemoteCallables.openEditor",
                session.sign().getLevel().dimension(), session.sign().getBlockPos(), session.front(),
                session.token(), session.path(), session.point());
    }

    public static boolean canEdit(ServerPlayer player, SignBlockEntity sign) {
        var session = SESSIONS.get(player);
        return session != null && session.sign() == sign && valid(player, session);
    }

    private static boolean valid(ServerPlayer player, Session session) {
        var sign = session.sign();
        if (!player.isAlive() || sign.isRemoved() || sign.isWaxed()
                || !(sign.getLevel() instanceof ServerLevel world)
                || player.level().getServer().getLevel(world.dimension()) != world
                || !player.getUUID().equals(sign.getPlayerWhoMayEdit())
                || !world.hasChunkAt(sign.getBlockPos()) || world.getBlockEntity(sign.getBlockPos()) != sign
                || !BlockManipulationServer.canDoCrossPortalInteractionEvent.invoker().test(player)) return false;
        var trace = PortalInteractionTarget.traceBlockPath(player, world.dimension(), sign.getBlockPos(), session.path(),
                session.point(), player.blockInteractionRange() + 4);
        return trace != null && trace.hit() instanceof BlockHitResult hit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && hit.getBlockPos().equals(sign.getBlockPos());
    }

    public static boolean hasSession(ServerPlayer player, SignBlockEntity sign) {
        var session = SESSIONS.get(player);
        return session != null && session.sign() == sign;
    }

    public static Vec3 positionForSign(ServerPlayer player, SignBlockEntity sign) {
        var target = PortalInteractionServer.currentEntityTarget();
        Vec3 position = player.position();
        if (target != null && target.hit() instanceof BlockHitResult hit
                && hit.getBlockPos().distManhattan(sign.getBlockPos()) <= 1
                && sign.getLevel() == PortalInteractionServer.entityWorld())
            for (Portal portal : target.portals()) position = portal.transformPoint(position);
        return position;
    }

    public static void applyFiltered(ServerPlayer player, ServerboundSignUpdatePacket packet, Runnable original) {
        var pending = PENDING.remove(packet);
        if (pending == null) { original.run(); return; }
        var session = pending.session();
        if (pending.player() != player || SESSIONS.get(player) != session || !valid(player, session)) return;
        var previous = APPLYING.get();
        APPLYING.set(session);
        try { PacketRedirection.withForceRedirect((ServerLevel) session.sign().getLevel(), original); }
        finally { APPLYING.set(previous); SESSIONS.remove(player, session); }
    }

    @Nullable
    public static ServerLevel applyingWorld() {
        var session = APPLYING.get();
        return session == null ? null : (ServerLevel) session.sign().getLevel();
    }

    public static final class RemoteCallables {
        public static void update(ServerPlayer player, ResourceKey<Level> dimension, UUID token,
                UUID[] path, byte[] bytes) {
            var buffer = IPMcHelper.bytesToBuf(bytes);
            ServerboundSignUpdatePacket packet;
            try { packet = ServerboundSignUpdatePacket.STREAM_CODEC.decode(buffer); }
            finally { buffer.release(); }
            var session = SESSIONS.get(player);
            if (session == null || !session.token().equals(token) || !Arrays.equals(session.path(), path)
                    || session.sign().getLevel().dimension() != dimension
                    || !session.sign().getBlockPos().equals(packet.getPos())
                    || session.front() != packet.isFrontText() || !valid(player, session)) return;
            PENDING.put(packet, new Pending(player, session));
            player.connection.handleSignUpdate(packet);
        }
    }
}
