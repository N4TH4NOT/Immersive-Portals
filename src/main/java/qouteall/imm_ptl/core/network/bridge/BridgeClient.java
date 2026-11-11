package qouteall.imm_ptl.core.network.bridge;

import io.github.n4th4not.immersiveportals.protocol.*;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import qouteall.imm_ptl.core.*;
import qouteall.imm_ptl.core.network.PacketRedirectionClient;
import qouteall.imm_ptl.core.portal.*;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.dimension.*;

import java.util.*;

/** Client adapter for the Minestom SDK; all mutation is performed on the client thread. */
public final class BridgeClient {
    private BridgeClient() {}

    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static final Map<UUID, Portal> PORTALS = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> VIEWS = new HashMap<>();
    private static final PacketReassembler PACKETS = new PacketReassembler();
    private static UUID session;
    private static int nextEntityId = -1_000_000;
    private static ResourceKey<Level> expectedRespawn;
    private static long expectedRespawnUntil;
    private static final Set<String> WORLD_PACKETS =
            Set.of(
                    "ClientboundLevelChunkWithLightPacket",
                    "ClientboundLightUpdatePacket",
                    "ClientboundForgetLevelChunkPacket",
                    "ClientboundBlockUpdatePacket",
                    "ClientboundBlockChangedAckPacket",
                    "ClientboundSectionBlocksUpdatePacket",
                    "ClientboundChunksBiomesPacket",
                    "ClientboundBlockEntityDataPacket",
                    "ClientboundBlockEventPacket",
                    "ClientboundLevelEventPacket",
                    "ClientboundLevelParticlesPacket",
                    "ClientboundSoundPacket",
                    "ClientboundSoundEntityPacket",
                    "ClientboundSetTimePacket",
                    "ClientboundGameEventPacket",
                    "ClientboundAddEntityPacket",
                    "ClientboundRemoveEntitiesPacket",
                    "ClientboundSetEntityDataPacket",
                    "ClientboundSetEntityMotionPacket",
                    "ClientboundEntityPositionSyncPacket",
                    "ClientboundTeleportEntityPacket",
                    "ClientboundRotateHeadPacket",
                    "ClientboundUpdateAttributesPacket",
                    "ClientboundSetEquipmentPacket",
                    "ClientboundUpdateMobEffectPacket",
                    "ClientboundRemoveMobEffectPacket",
                    "ClientboundEntityEventPacket",
                    "ClientboundTakeItemEntityPacket",
                    "ClientboundSetEntityLinkPacket",
                    "ClientboundSetPassengersPacket",
                    "ClientboundHurtAnimationPacket",
                    "ClientboundDamageEventPacket");

    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> {
                    if (isActive()) BridgeGravity.tick();
                });
        ClientPlayNetworking.registerGlobalReceiver(
                BridgePayload.TYPE,
                (payload, context) -> handle(BridgeCodec.decode(payload.bytes())));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> clear());
        IPCGlobal.CLIENT_EXIT_EVENT.register(BridgeClient::clear);
    }

    public static boolean isActive() {
        return session != null;
    }

    public static void send(BridgeMessage message) {
        if (CLIENT.getConnection() != null)
            CLIENT.getConnection()
                    .send(
                            new ServerboundCustomPayloadPacket(
                                    new BridgePayload(BridgeCodec.encode(message))));
    }

    private static ResourceKey<Level> world(String key) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(key));
    }

    private static net.minecraft.world.phys.Vec3 vec(
            io.github.n4th4not.immersiveportals.protocol.Vec3 p) {
        return new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z());
    }

    private static io.github.n4th4not.immersiveportals.protocol.Vec3 vec(
            net.minecraft.world.phys.Vec3 p) {
        return new io.github.n4th4not.immersiveportals.protocol.Vec3(p.x, p.y, p.z);
    }

    private static void handle(BridgeMessage message) {
        if (CLIENT.getConnection() == null || CLIENT.level == null) return;
        switch (message) {
            case BridgeMessage.Hello hello -> {
                if (hello.protocol() == BridgeCodec.VERSION
                        && hello.minecraftProtocol() == BridgeCodec.MINECRAFT_PROTOCOL)
                    send(
                            new BridgeMessage.Hello(
                                    BridgeCodec.VERSION,
                                    BridgeCodec.MINECRAFT_PROTOCOL,
                                    qouteall.imm_ptl.core.compat.GravityChangerInterface.invoker
                                                    .isGravityChangerPresent()
                                            ? BridgeCodec.FEATURE_GRAVITY
                                            : 0));
            }
            case BridgeMessage.Welcome welcome -> {
                if (session != null && !session.equals(welcome.session())) clear();
                session = welcome.session();
                var types = new HashMap<ResourceKey<Level>, ResourceKey<DimensionType>>();
                var seaLevels = new HashMap<ResourceKey<Level>, Integer>();
                for (var metadata : welcome.worlds()) {
                    var type =
                            ResourceKey.create(
                                    Registries.DIMENSION_TYPE,
                                    Identifier.parse(metadata.dimensionType()));
                    CLIENT.getConnection()
                            .registryAccess()
                            .lookupOrThrow(Registries.DIMENSION_TYPE)
                            .getOrThrow(type);
                    types.put(world(metadata.key()), type);
                    seaLevels.put(world(metadata.key()), metadata.seaLevel());
                }
                var map = new DimIntIdMap();
                int index = 2;
                for (var key :
                        types.keySet().stream()
                                .sorted(Comparator.comparing(k -> k.identifier().toString()))
                                .toList()) {
                    int id =
                            key == Level.OVERWORLD
                                    ? 0
                                    : key == Level.NETHER ? -1 : key == Level.END ? 1 : index++;
                    map.add(key, id);
                }
                org.slf4j.LoggerFactory.getLogger("ImmPtlBridge")
                        .info("Connected Minestom portal bridge: {} worlds", types.size());
                DimensionIntId.clientRecord = map;
                ClientWorldLoader.dimIdToDimTypeId = Map.copyOf(types);
                ClientWorldLoader.seaLevels = Map.copyOf(seaLevels);
                DimensionLifecycle.CLIENT_DIMENSION_UPDATE_EVENT.invoker().accept(types.keySet());
            }
            case BridgeMessage.Upsert upsert -> {
                if (isActive()) upsert(upsert.portal());
            }
            case BridgeMessage.Remove remove -> remove(remove.portal());
            case BridgeMessage.Clear ignored -> {
                clear();
            }
            case BridgeMessage.OpenView open -> {
                if (!isActive() || (!VIEWS.containsKey(open.view()) && VIEWS.size() >= 8)) return;
                ResourceKey<Level> key = world(open.world());
                if (!ClientWorldLoader.getServerDimensions().contains(key)) return;
                VIEWS.put(open.view(), key);
                ClientWorldLoader.getWorld(key);
            }
            case BridgeMessage.CloseView close -> {
                VIEWS.remove(close.view());
                PACKETS.closeView(close.view());
            }
            case BridgeMessage.WorldFragment fragment -> {
                var dimension = VIEWS.get(fragment.view());
                if (dimension == null) return;
                PACKETS.accept(fragment, System.nanoTime())
                        .ifPresent(bytes -> worldPacket(dimension, bytes));
            }
            case BridgeMessage.Correction correction -> {
                if (!isActive() || CLIENT.player == null) return;
                ScaleUtils.setIPortalScaling(CLIENT.player, correction.portalScale());
                qouteall.imm_ptl.core.compat.GravityChangerInterface.invoker.applyState(
                        CLIENT.player, correction.gravity(), true);
                qouteall.imm_ptl.core.teleportation.ClientTeleportationManager.forceTeleportPlayer(
                        world(correction.world()), vec(correction.feet()));
                CLIENT.player.setYRot(correction.yaw());
                CLIENT.player.setXRot(correction.pitch());
                McHelper.setWorldVelocity(
                        CLIENT.player, vec(correction.velocity()).scale(1.0 / 20));
                expectedRespawn = null;
            }
            case BridgeMessage.Gravity gravity -> {
                if (isActive()) BridgeGravity.receive(gravity);
            }
            case BridgeMessage.Rejected rejected ->
                    CHelper.printChat(
                            net.minecraft.network.chat.Component.literal(rejected.reason()));
            default -> {}
        }
    }

    private static void upsert(PortalDefinition definition) {
        ResourceKey<Level> from = world(definition.sourceWorld()),
                to = world(definition.destinationWorld());
        if (!ClientWorldLoader.getServerDimensions().containsAll(List.of(from, to))) return;
        Portal portal = PORTALS.get(definition.id());
        boolean created = portal == null;
        boolean mirror = definition.kind() == PortalDefinition.Kind.MIRROR;
        if (portal != null
                && (portal.level().dimension() != from || (portal instanceof Mirror) != mirror)) {
            remove(definition.id());
            portal = null;
            created = true;
        }
        if (portal == null) {
            var level = ClientWorldLoader.getWorld(from);
            portal =
                    mirror
                            ? new Mirror(Mirror.ENTITY_TYPE, level)
                            : new Portal(Portal.ENTITY_TYPE, level);
            portal.setId(nextEntityId--);
            portal.setUUID(definition.id());
            portal.setOriginPos(vec(definition.origin()));
            PORTALS.put(definition.id(), portal);
        }
        CompoundTag data;
        if (definition.extraNbt().length == 0) data = new CompoundTag();
        else {
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(definition.extraNbt()));
            try {
                data = buffer.readNbt();
                if (data == null || buffer.isReadable())
                    throw new IllegalArgumentException("Invalid portal NBT");
            } finally {
                buffer.release();
            }
        }
        data.putDouble("width", definition.width());
        data.putDouble("height", definition.height());
        data.putDouble("scale", definition.scale());
        // SDK defaults are authoritative, independently of each client's local portal defaults.
        if (!data.contains("teleportChangesGravity"))
            data.putBoolean("teleportChangesGravity", false);
        Helper.putVec3d(data, "axisW", vec(definition.axisW()));
        Helper.putVec3d(data, "axisH", vec(definition.axisH()));
        Helper.putVec3d(data, "destination", vec(definition.destination()));
        Helper.putWorldId(data, "dimensionTo", to);
        data.putFloat("rotationB", (float) definition.rotation().x());
        data.putFloat("rotationC", (float) definition.rotation().y());
        data.putFloat("rotationD", (float) definition.rotation().z());
        data.putFloat("rotationA", (float) definition.rotation().w());
        if (created) {
            portal.readPortalDataFromNbt(data);
            ClientWorldLoader.getWorld(from).addEntity(portal);
        } else portal.acceptDataSync(vec(definition.origin()), data);
    }

    private static void remove(UUID id) {
        Portal p = PORTALS.remove(id);
        if (p != null) p.discard();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void worldPacket(ResourceKey<Level> dimension, byte[] bytes) {
        var listener = CLIENT.getConnection();
        if (listener == null) return;
        var buffer =
                new RegistryFriendlyByteBuf(
                        Unpooled.wrappedBuffer(bytes), listener.registryAccess());
        try {
            Packet packet =
                    GameProtocols.CLIENTBOUND_TEMPLATE
                            .bind(RegistryFriendlyByteBuf.decorator(listener.registryAccess()))
                            .codec()
                            .decode(buffer);
            String name = packet.getClass().getSimpleName();
            if (!WORLD_PACKETS.contains(name) && !(packet instanceof ClientboundMoveEntityPacket))
                throw new IllegalArgumentException("Disallowed world packet: " + name);
            if (buffer.isReadable())
                throw new IllegalArgumentException("Trailing world packet data");
            PacketRedirectionClient.handleRedirectedPacket(
                    DimensionIntId.getClientMap().toIntegerId(dimension), packet, listener);
        } finally {
            buffer.release();
        }
    }

    public static boolean teleport(
            Portal portal, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
        if (!isActive()) return false;
        if (!PORTALS.containsKey(portal.getUUID())) return false;
        expectedRespawn = portal.getDestDim();
        expectedRespawnUntil = System.nanoTime() + 5_000_000_000L;
        send(new BridgeMessage.Teleport(portal.getUUID(), vec(from), vec(to)));
        return true;
    }

    public static boolean consumeRespawn(ClientboundRespawnPacket packet) {
        if (!isActive()
                || System.nanoTime() > expectedRespawnUntil
                || packet.commonPlayerSpawnInfo().dimension() != expectedRespawn
                || CLIENT.level.dimension() != expectedRespawn) return false;
        expectedRespawn = null;
        CLIENT.gameMode.setLocalMode(
                packet.commonPlayerSpawnInfo().gameType(),
                packet.commonPlayerSpawnInfo().previousGameType());
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Packet<?> interaction(Packet<?> packet, List<UUID> path) {
        var listener = CLIENT.getConnection();
        if (!isActive() || listener == null) return packet;
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), listener.registryAccess());
        try {
            GameProtocols.SERVERBOUND_TEMPLATE
                    .bind(
                            RegistryFriendlyByteBuf.decorator(listener.registryAccess()),
                            () -> CLIENT.player.hasInfiniteMaterials())
                    .codec()
                    .encode(buffer, (Packet) packet);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new ServerboundCustomPayloadPacket(
                    new BridgePayload(
                            BridgeCodec.encode(new BridgeMessage.Interaction(path, bytes))));
        } finally {
            buffer.release();
        }
    }

    private static void clear() {
        for (var portal : PORTALS.values()) portal.discard();
        PORTALS.clear();
        VIEWS.clear();
        PACKETS.clear();
        BridgeGravity.clear();
        session = null;
        expectedRespawn = null;
    }
}
