package qouteall.imm_ptl.core.block_manipulation;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

import java.util.UUID;

/** Native sign screens retain their server-issued portal path after the crosshair moves away. */
public final class SignInteractionClient {
    private record Session(SignBlockEntity sign, boolean front, UUID token, UUID[] path, Vec3 point) {}
    private static Session session;
    private static final Minecraft CLIENT = Minecraft.getInstance();

    static {
        qouteall.imm_ptl.core.IPCGlobal.CLIENT_CLEANUP_EVENT.register(() -> session = null);
    }

    private SignInteractionClient() {}

    public static boolean hasSession(SignBlockEntity sign, UUID player) {
        return session != null && session.sign() == sign && CLIENT.player != null
                && CLIENT.player.getUUID().equals(player);
    }

    public static boolean canEdit(SignBlockEntity sign) {
        if (session == null || session.sign() != sign || !ClientWorldLoader.isPrimaryWorldReady()
                || !CLIENT.player.isAlive() || sign.isRemoved()) return false;
        var target = PortalInteractionTarget.traceBlockPath(CLIENT.player, sign.getLevel().dimension(),
                sign.getBlockPos(), session.path(), session.point(), CLIENT.player.blockInteractionRange() + 4);
        return target != null && target.hit() instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(sign.getBlockPos());
    }

    public static Packet<?> updatePacket(SignBlockEntity sign, ServerboundSignUpdatePacket packet) {
        var current = session;
        boolean bound = current != null && current.sign() == sign;
        if (!bound && CLIENT.player != null && sign.getLevel() == CLIENT.player.level()) return packet;
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerboundSignUpdatePacket.STREAM_CODEC.encode(buffer, packet);
            // A missing session must never fall back to source-world coordinates.
            UUID token = bound ? current.token() : new UUID(0, 0);
            UUID[] path = bound ? current.path() : new UUID[0];
            return McRemoteProcedureCall.createPacketToSendToServer(
                    "qouteall.imm_ptl.core.block_manipulation.SignInteractionServer.RemoteCallables.update",
                    sign.getLevel().dimension(), token, path, IPMcHelper.bufToBytes(buffer));
        } finally {
            buffer.release();
            if (bound && session == current) session = null;
        }
    }

    public static final class RemoteCallables {
        public static void openEditor(ResourceKey<Level> dimension, BlockPos pos, boolean front,
                UUID token, UUID[] path, Vec3 point) {
            if (!ClientWorldLoader.isPrimaryWorldReady()) return;
            var world = ClientWorldLoader.getOptionalWorld(dimension);
            if (world == null || !(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
            if (CLIENT.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen)
                CLIENT.gui.setScreen(null);
            session = new Session(sign, front, token, path.clone(), point);
            ClientWorldLoader.withSwitchedWorld(world, () -> {
                CLIENT.getConnection().handleOpenSignEditor(new ClientboundOpenSignEditorPacket(pos, front));
            });
        }
    }
}
