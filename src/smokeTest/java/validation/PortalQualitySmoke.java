package validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.ProtoPortal;
import qouteall.imm_ptl.peripheral.wand.ProtoPortalSide;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Coordinates two real clients, natural entity ticks, and an unchanged on-disk wand cluster. */
final class PortalQualitySmoke {
    static final String TAG = "imm_ptl_quality_fixture";
    static final Vec3 SOURCE = new Vec3(0, 72, 32);
    static final Vec3 DESTINATION = new Vec3(64, 72, 32);
    static final int WAIT = 0, APPROACH_A = 1, APPROACH_B = 2, PICKUP = 3,
            CROSS = 4, RETURN = 5, ENTITIES = 6, PERFORMANCE = 7, SAVE = 8,
            REJOIN_CROSS = 9, FINISH = 10;
    private static final String SNAPSHOT = "portal-smoke-quality.json";

    record State(int phase, int serverTick, UUID first, UUID second, boolean reloaded)
            implements CustomPacketPayload {
        static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
                "imm_ptl_smoke", "quality_state"));
        static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of(
                (buffer, state) -> {
                    buffer.writeVarInt(state.phase());
                    buffer.writeVarInt(state.serverTick());
                    buffer.writeUUID(state.first());
                    buffer.writeUUID(state.second());
                    buffer.writeBoolean(state.reloaded());
                }, buffer -> new State(buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readUUID(), buffer.readUUID(), buffer.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record Tracked(String name, UUID id, Class<?> type, Vec3 position, Vec3 velocity, int age) {}
    private static final Map<String, UUID> players = new HashMap<>();
    private static final Set<UUID> ready = new HashSet<>();
    private static final Set<UUID> checkpoints = new HashSet<>();
    private static final Map<UUID, Integer> crossingTicks = new HashMap<>();
    private static final Map<UUID, Tracked> entities = new HashMap<>();
    private static final Map<UUID, Entity> originalEntities = new HashMap<>();
    private static final List<Portal> entityPortals = new ArrayList<>();
    private static final Set<UUID> crossedEntities = new HashSet<>();
    private static final Set<UUID> blockedProjectiles = new HashSet<>();
    private static final Set<UUID> resolvedHurtingProjectiles = new HashSet<>();
    private static final Set<UUID> resolvedThrowableProjectiles = new HashSet<>();
    private static final Set<UUID> faceIds = new HashSet<>();
    private static final List<Long> tickDurations = new ArrayList<>();
    private static Portal forward;
    private static Portal crossWorldEntityPortal;
    private static JsonObject saved;
    private static boolean fixtureReady;
    private static boolean reloaded;
    private static int phase;
    private static int phaseTick;
    private static long tickStart;

    private PortalQualitySmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static boolean isQuality(ServerPlayer player) {
        return player.getGameProfile().name().equals("QualityA")
                || player.getGameProfile().name().equals("QualityB");
    }

    static boolean preserve(Portal portal) {
        return portal.entityTags().contains(TAG);
    }

    static void initialize(Set<UUID> scenePortals) {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            players.clear();
            ready.clear();
            checkpoints.clear();
            crossingTicks.clear();
            entities.clear();
            originalEntities.clear();
            entityPortals.clear();
            crossedEntities.clear();
            blockedProjectiles.clear();
            resolvedHurtingProjectiles.clear();
            resolvedThrowableProjectiles.clear();
            faceIds.clear();
            tickDurations.clear();
            phase = WAIT;
            phaseTick = 0;
            forward = null;
            crossWorldEntityPortal = null;
            fixtureReady = false;
            reloaded = Boolean.getBoolean("portalSmoke.qualityReload");
            saved = null;
            if (!reloaded) return;
            try {
                saved = JsonParser.parseString(Files.readString(snapshot(server))).getAsJsonObject();
            } catch (Exception failure) {
                throw new AssertionError("The quality restart snapshot could not be read", failure);
            }
            loadChunks(server.overworld());
            for (var id : saved.getAsJsonArray("faces")) faceIds.add(UUID.fromString(id.getAsString()));
            var persisted = portals(server.overworld()).stream()
                    .filter(portal -> faceIds.contains(portal.getUUID())).toList();
            require(persisted.size() == 4,
                    "The four original wand faces were not loaded from disk: " + persisted.size());
            forward = persisted.stream().filter(portal -> portal.getOriginPos().distanceTo(SOURCE) < .01
                    && portal.getNormal().z > .99).findFirst().orElseThrow();
            scenePortals.addAll(faceIds);
            fixtureReady = true;
            requireGeometry(server.overworld());
            System.out.println("PORTAL_QUALITY_PORTALS_RELOADED_OK faces=" + faceIds);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("portal_quality_ready")
                    .requires(source -> source.getPlayer() != null && isQuality(source.getPlayer()))
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        ready.add(player.getUUID());
                        return 1;
                    }));
            dispatcher.register(Commands.literal("portal_quality_checkpoint")
                    .requires(source -> source.getPlayer() != null && isQuality(source.getPlayer()))
                    .then(Commands.argument("phase", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                            .executes(context -> {
                                int reported = com.mojang.brigadier.arguments.IntegerArgumentType
                                        .getInteger(context, "phase");
                                require(reported == phase,
                                        "A quality client acknowledged a stale phase: " + reported + "/" + phase);
                                checkpoints.add(context.getSource().getPlayerOrException().getUUID());
                                return 1;
                            })));
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server, scenePortals));
    }

    static boolean join(ServerPlayer player, Set<UUID> scenePortals) {
        if (!isQuality(player)) return false;
        players.put(player.getGameProfile().name(), player.getUUID());
        if (!fixtureReady) {
            // The actual creative-wand RPC keeps its normal permission check.
            player.setGameMode(GameType.CREATIVE);
            prepare(player, scenePortals);
        }
        player.setGameMode(GameType.SURVIVAL);
        boolean first = player.getGameProfile().name().equals("QualityA");
        if (reloaded) {
            requireSavedPlayer(player);
        } else {
            // These are initial fixture positions. Every subsequent crossing uses movement keys.
            player.teleportTo(player.level().getServer().overworld(), first ? -.65 : 64.65,
                    70, first ? 36 : 28, Set.of(), first ? 180 : 0, 0, false);
        }
        System.out.println("PORTAL_QUALITY_PLAYER_JOINED " + player.getGameProfile().name()
                + " reloaded=" + reloaded + " position=" + player.position());
        return true;
    }

    private static void tick(MinecraftServer server, Set<UUID> scenePortals) {
        var first = player(server, "QualityA");
        var second = player(server, "QualityB");
        if (first == null || second == null) return;
        if (phase == WAIT) {
            if (!ready.contains(first.getUUID()) || !ready.contains(second.getUUID())) return;
            if (!reloaded && !entityTargetTicking(server.getLevel(Level.NETHER))) return;
            requireGeometry(server.overworld());
            if (reloaded) {
                // Validate again after both actual clients have ticked at the saved plane.
                // A correct NBT load followed by a first-tick push-out must fail as well.
                requireSavedPlayer(first);
                requireSavedPlayer(second);
                System.out.println("PORTAL_QUALITY_PERSISTENCE_OK");
                advance(server, REJOIN_CROSS);
            } else {
                PortalPickupQualitySmoke.prepare(first, second, forward);
                advance(server, APPROACH_A);
            }
            return;
        }
        if (phase == SAVE || phase == FINISH) return;
        require(server.getTickCount() - phaseTick < 1600,
                "The quality scenario stalled: phase=" + phase + " checkpoints=" + checkpoints);
        require(first.level().dimension() == Level.OVERWORLD
                        && second.level().dimension() == Level.OVERWORLD,
                "The same-world quality players changed dimension");
        requireDry(first);
        requireDry(second);
        switch (phase) {
            case APPROACH_A -> {
                if (checkpoints.contains(first.getUUID())
                        && PortalPickupQualitySmoke.tickApproach(first, second, forward)) {
                    require(!destination(first), "Remote item pickup required crossing the source plane");
                    advance(server, APPROACH_B);
                }
            }
            case APPROACH_B -> {
                if (checkpoints.contains(second.getUUID())) advance(server, PICKUP);
            }
            case PICKUP -> {
                if (PortalPickupQualitySmoke.tickRace(first, second, forward)) advance(server, CROSS);
            }
            case CROSS, RETURN, REJOIN_CROSS -> {
                boolean firstDestination = phase != RETURN;
                for (var player : List.of(first, second)) {
                    boolean expected = player == first ? firstDestination : !firstDestination;
                    if (destination(player) == expected)
                        crossingTicks.putIfAbsent(player.getUUID(), server.getTickCount());
                }
                if (!checkpoints.contains(first.getUUID()) || !checkpoints.contains(second.getUUID())) return;
                require(crossingTicks.size() == 2 && destination(first) == firstDestination
                                && destination(second) != firstDestination,
                        "The two players did not cross in opposite directions");
                int separation = Math.abs(crossingTicks.get(first.getUUID())
                        - crossingTicks.get(second.getUUID()));
                require(separation <= 20,
                        "The opposing crossings were not concurrent: " + separation + " ticks");
                System.out.println("PORTAL_QUALITY_CONCURRENT_CROSSING_OK phase=" + phase
                        + " separationTicks=" + separation + " first=" + first.position()
                        + " second=" + second.position());
                if (phase == CROSS) advance(server, RETURN);
                else if (phase == REJOIN_CROSS) {
                    requireGeometry(server.overworld());
                    advance(server, FINISH);
                    System.out.println("PORTAL_QUALITY_SERVER_COMPLETE phase=rejoin");
                } else {
                    advance(server, ENTITIES);
                    spawnEntities(server.overworld());
                }
            }
            case ENTITIES -> {
                if (observeEntities(server.overworld())) {
                    System.out.println("PORTAL_QUALITY_ENTITY_PHYSICS_OK count=" + crossedEntities.size()
                            + " destinationHits=" + blockedProjectiles.size());
                    for (var id : entities.keySet()) {
                        for (var level : server.getAllLevels()) {
                            var entity = level.getEntity(id);
                            if (entity != null) entity.discard();
                        }
                    }
                    entityPortals.forEach(Portal::discard);
                    advance(server, PERFORMANCE);
                }
            }
            case PERFORMANCE -> {
                tickDurations.add(System.nanoTime() - tickStart);
                if (!checkpoints.contains(first.getUUID()) || !checkpoints.contains(second.getUUID())
                        || tickDurations.size() < 60) return;
                var sorted = tickDurations.stream().sorted().toList();
                double average = sorted.stream().mapToLong(Long::longValue).average().orElseThrow() / 1e6;
                double p95 = sorted.get((sorted.size() - 1) * 95 / 100) / 1e6;
                require(average <= 250 && p95 <= 1000,
                        "Two-client server ticks exceeded the software-runner budget: averageMs="
                                + average + " p95Ms=" + p95);
                System.out.println("PORTAL_QUALITY_SERVER_PERFORMANCE_OK samples=" + sorted.size()
                        + " averageMs=" + average + " p95Ms=" + p95);
                save(server, first, second);
                advance(server, SAVE);
                System.out.println("PORTAL_QUALITY_SERVER_COMPLETE phase=save");
            }
            default -> throw new AssertionError("Unknown quality phase " + phase);
        }
    }

    private static void advance(MinecraftServer server, int next) {
        phase = next;
        phaseTick = server.getTickCount();
        checkpoints.clear();
        crossingTicks.clear();
        var first = player(server, "QualityA");
        var second = player(server, "QualityB");
        var state = new State(next, phaseTick, first.getUUID(), second.getUUID(), reloaded);
        ServerPlayNetworking.send(first, state);
        ServerPlayNetworking.send(second, state);
        System.out.println("PORTAL_QUALITY_SERVER_STAGE " + next + " tick=" + phaseTick);
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        var id = players.get(name);
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static boolean destination(Entity entity) {
        return entity.getX() > 32;
    }

    private static Vec3 unfold(Vec3 position) {
        return position.x > 32 ? position.add(-64, 0, 0) : position;
    }

    private static void spawnEntities(ServerLevel world) {
        // The destination obstacle must remain solid after the source wall is clipped.
        // The fast arrow covers more than two blocks in one tick and must hit this face.
        for (int x = 62; x <= 66; x++)
            for (int y = 70; y <= 74; y++)
                world.setBlock(new BlockPos(x, y, 29), Blocks.STONE.defaultBlockState(), 3);
        var chicken = EntityTypes.CHICKEN.create(world, EntitySpawnReason.COMMAND);
        require(chicken != null, "The quality chicken could not be created");
        // NoAI also disables native travel. Remove autonomous input while retaining physics.
        chicken.removeFreeWill();
        chicken.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        chicken.setPersistenceRequired();
        spawn(world, "mob", chicken, new Vec3(0, 70, 32.2), new Vec3(0, 0, -.35));
        var arrow = EntityTypes.ARROW.create(world, EntitySpawnReason.COMMAND);
        require(arrow != null, "The quality arrow could not be created");
        spawn(world, "projectile", arrow, new Vec3(0, 72.5, 32.4), new Vec3(0, 0, -.6));
        var fastArrow = EntityTypes.ARROW.create(world, EntitySpawnReason.COMMAND);
        require(fastArrow != null, "The fast quality arrow could not be created");
        spawn(world, "fast_projectile", fastArrow, new Vec3(1.2, 72.5, 32.4), new Vec3(0, 0, -2.4));
        var item = new ItemEntity(world, 0, 70.1, 32.2, new ItemStack(Items.AMETHYST_SHARD, 3));
        item.setPickUpDelay(32767);
        spawn(world, "item", item, new Vec3(0, 70.1, 32.2), new Vec3(0, 0, -.35));
        prepareCrossWorldEntity(world);
    }

    private static void spawn(ServerLevel world, String name, Entity entity, Vec3 position, Vec3 velocity) {
        entity.snapTo(position, 0, 0);
        entity.setDeltaMovement(velocity);
        require(world.addFreshEntity(entity), "The quality entity could not be spawned: " + name);
        entities.put(entity.getUUID(), new Tracked(name, entity.getUUID(), entity.getClass(),
                position, velocity, entity.tickCount));
        originalEntities.put(entity.getUUID(), entity);
    }

    private static boolean observeEntities(ServerLevel world) {
        require(world.getServer().getTickCount() - phaseTick < 120,
                "A natural entity/projectile failed to traverse its intact backing wall: "
                        + entities.values() + " crossed=" + crossedEntities);
        for (var previous : List.copyOf(entities.values())) {
            if (resolvedHurtingProjectiles.contains(previous.id())
                    || resolvedThrowableProjectiles.contains(previous.id())) continue;
            Entity entity = null;
            for (var level : world.getServer().getAllLevels()) {
                var candidate = level.getEntity(previous.id());
                if (candidate != null && !candidate.isRemoved()) {
                    require(entity == null, "The same quality entity UUID exists in two dimensions");
                    entity = candidate;
                }
            }
            if (entity == null && previous.name().equals("cross_world_fireball")
                    && crossedEntities.contains(previous.id())) {
                require(world.getServer().getLevel(Level.NETHER)
                                .getBlockState(new BlockPos(191, 73, 39)).is(Blocks.FIRE)
                                && world.getBlockState(new BlockPos(2, 73, 39)).is(Blocks.STONE),
                        "The fireball did not execute its native impact in the destination world");
                resolvedHurtingProjectiles.add(previous.id());
                System.out.println("PORTAL_QUALITY_HURTING_PROJECTILE_OK uuid=" + previous.id());
                continue;
            }
            require(entity != null && !entity.isRemoved() && previous.type().isInstance(entity),
                    "Portal traversal lost or replaced the quality " + previous.name());
            boolean crossWorld = previous.name().startsWith("cross_world_");
            boolean arrived = crossWorld ? entity.level().dimension() == Level.NETHER : destination(entity);
            require(!crossedEntities.contains(previous.id()) || arrived,
                    "The portal replayed a completed entity crossing without reverse movement: "
                            + previous.name() + " previous=" + previous + " current=" + entity.position()
                            + " velocity=" + entity.getDeltaMovement() + " age=" + entity.tickCount);
            Vec3 position = crossWorld && arrived
                    ? crossWorldEntityPortal.inverseTransformPoint(entity.position())
                    : crossWorld ? entity.position() : unfold(entity.position());
            Vec3 velocity = entity.getDeltaMovement();
            if (entity instanceof ItemEntity)
                require(!entity.noPhysics,
                        "The quality item bypassed native physical collisions at the portal: "
                                + entity.position() + " velocity=" + velocity);
            double step = horizontal(position.subtract(previous.position()));
            Vec3 expectedMovement = previous.velocity();
            if (previous.age() == 0 && entity.tickCount > 0 && !(entity instanceof Projectile)) {
                require(step > .1,
                        "The first native entity move hit the portal backing wall before contact discovery: "
                                + previous.name() + " previous=" + previous + " current=" + position
                                + " velocity=" + velocity);
                System.out.println("PORTAL_QUALITY_ENTITY_FIRST_MOVE_OK type=" + previous.name()
                        + " position=" + entity.position() + " velocity=" + velocity);
            }
            if (entity instanceof SmallFireball fireball) {
                expectedMovement = previous.velocity().add(previous.velocity().normalize()
                        .scale(fireball.accelerationPower)).scale((double) .95f);
                require(velocity.distanceTo(expectedMovement) < 1e-6
                                && horizontal(position.subtract(previous.position()).subtract(expectedMovement)) < .02,
                        "Fireball traversal changed native acceleration, drag, or its tick endpoint: previous="
                                + previous + " current=" + position + " velocity=" + velocity
                                + " expected=" + expectedMovement + " age=" + entity.tickCount
                                + " ticking=" + ((ServerLevel) entity.level())
                                        .areEntitiesActuallyLoadedAndTicking(entity.chunkPosition())
                                + " block=" + entity.level().getBlockState(entity.blockPosition()));
            }
            require(step <= horizontal(expectedMovement) + .12,
                    "Portal traversal added entity displacement: " + previous.name()
                            + " previous=" + previous + " current=" + position + " velocity=" + velocity);
            require(horizontal(velocity) <= horizontal(expectedMovement) + .01,
                    "Portal traversal accelerated the unpowered quality " + previous.name()
                            + " previous=" + previous + " current=" + position + " velocity=" + velocity
                            + " noPhysics=" + entity.noPhysics + " age=" + entity.tickCount);
            if (entity instanceof AbstractArrow arrow) {
                if (arrow.shakeTime > 0) {
                    require(destination(entity) && entity.getZ() >= 29.95 && entity.getZ() <= 30.08,
                            "The arrow hit its source wall or skipped the destination obstacle: "
                                    + previous.name() + " position=" + entity.position());
                    if (blockedProjectiles.add(entity.getUUID()))
                        System.out.println("PORTAL_QUALITY_PROJECTILE_DESTINATION_HIT type="
                                + previous.name() + " position=" + entity.position());
                } else {
                    // Arrow.tick integrates its existing velocity before drag and gravity.
                    // Losing the remainder at the portal plane is a visible discontinuity.
                    require(horizontal(position.subtract(previous.position()).subtract(previous.velocity())) < .02,
                            "The portal lost projectile travel during its tick: " + previous.name()
                                    + " previous=" + previous + " current=" + position);
                }
            }
            if (arrived && crossedEntities.add(entity.getUUID())) {
                double expectedX = crossWorld ? crossWorldEntityPortal.transformPoint(previous.position()).x
                        : previous.name().equals("fast_projectile") ? 65.2 : 64;
                require(entity.getZ() < (crossWorld ? 40 : 32) && Math.abs(entity.getX() - expectedX) < .02
                                && (velocity.z < -.005 || blockedProjectiles.contains(entity.getUUID())),
                        "The quality entity did not preserve its trajectory: " + previous.name()
                                + " position=" + entity.position() + " velocity=" + velocity);
                if (entity instanceof ItemEntity item)
                    require(item.getItem().is(Items.AMETHYST_SHARD) && item.getItem().getCount() == 3,
                            "Item traversal changed its stack");
                if (crossWorld) {
                    require(entity.getCustomName() != null
                                    && entity.getCustomName().getString().equals("Quality UUID witness"),
                            "Cross-dimension traversal did not preserve the entity's NBT");
                    if (entity instanceof Projectile) {
                        require(entity == originalEntities.get(entity.getUUID()),
                                "Inline projectile traversal replaced its active native tick object");
                        System.out.println("PORTAL_QUALITY_PROJECTILE_WORLD_OK uuid=" + entity.getUUID()
                                + " destination=" + entity.level().dimension().identifier());
                    } else {
                        require(entity != originalEntities.get(entity.getUUID())
                                        && originalEntities.get(entity.getUUID()).isRemoved(),
                                "Cross-dimension mob recreation did not preserve identity");
                        System.out.println("PORTAL_QUALITY_ENTITY_UUID_OK uuid=" + entity.getUUID()
                                + " destination=" + entity.level().dimension().identifier());
                    }
                }
                System.out.println("PORTAL_QUALITY_ENTITY_CROSSED type=" + previous.name()
                        + " position=" + entity.position() + " velocity=" + velocity);
            }
            entities.put(previous.id(), new Tracked(previous.name(), previous.id(), previous.type(),
                    position, velocity, entity.tickCount));
            if (world.getServer().getTickCount() - phaseTick == 8
                    && !crossedEntities.contains(entity.getUUID()))
                System.out.println("PORTAL_QUALITY_ENTITY_PENDING type=" + previous.name()
                        + " position=" + entity.position() + " velocity=" + velocity
                        + " age=" + entity.tickCount);
        }
        return crossedEntities.size() == 7 && blockedProjectiles.size() == 2
                && resolvedHurtingProjectiles.size() == 1 && resolvedThrowableProjectiles.size() == 1;
    }

    private static void prepareCrossWorldEntity(ServerLevel source) {
        var target = source.getServer().getLevel(Level.NETHER);
        require(target != null, "The entity-copy witness has no destination dimension");
        require(entityTargetTicking(target), "The destination entity chunks are not ticking");
        for (int x = 188; x <= 196; x++)
            for (int z = 35; z <= 45; z++)
                for (int y = 69; y <= 77; y++)
                    if (x == 188 || x == 196 || z == 35 || z == 45 || y == 69 || y == 77)
                        target.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
        for (int x = 189; x <= 195; x++)
            for (int z = 36; z <= 44; z++)
                for (int y = 70; y <= 76; y++)
                    target.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        target.setBlock(new BlockPos(192, 76, 40), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        crossWorldEntityPortal = new Portal(Portal.ENTITY_TYPE, source);
        crossWorldEntityPortal.setOriginPos(new Vec3(2.5, 72, 40));
        crossWorldEntityPortal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 2, 4);
        crossWorldEntityPortal.setDestinationDimension(Level.NETHER);
        crossWorldEntityPortal.setDestination(new Vec3(192, 72, 40));
        require(source.addFreshEntity(crossWorldEntityPortal), "Could not create the entity-copy opening");
        var reverse = PortalManipulation.createReversePortal(crossWorldEntityPortal, Portal.ENTITY_TYPE);
        require(target.addFreshEntity(reverse), "Could not create the entity-copy reverse face");
        entityPortals.add(crossWorldEntityPortal);
        entityPortals.add(reverse);
        for (int y = 70; y <= 74; y++) {
            for (int x = 1; x <= 3; x++)
                source.setBlock(new BlockPos(x, y, 39), Blocks.STONE.defaultBlockState(), 3);
            for (int x = 190; x <= 194; x++)
                target.setBlock(new BlockPos(x, y, 40), Blocks.STONE.defaultBlockState(), 3);
        }
        var chicken = EntityTypes.CHICKEN.create(source, EntitySpawnReason.COMMAND);
        require(chicken != null, "Could not create the entity UUID witness");
        chicken.removeFreeWill();
        chicken.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        chicken.setPersistenceRequired();
        chicken.setCustomName(Component.literal("Quality UUID witness"));
        spawn(source, "cross_world_mob", chicken, new Vec3(2.5, 70, 40.2), new Vec3(0, 0, -.35));
        var snowball = new QualitySnowball(source);
        snowball.setCustomName(Component.literal("Quality UUID witness"));
        spawn(source, "cross_world_projectile", snowball,
                new Vec3(2.5, 72.5, 40.2), new Vec3(0, 0, -.6));
        // A fireball must retain its normal acceleration and drag, then run its
        // actual block-impact callback after crossing into the Nether.
        target.setBlock(new BlockPos(191, 73, 38), Blocks.STONE.defaultBlockState(), 3);
        target.setBlock(new BlockPos(192, 72, 38), Blocks.STONE.defaultBlockState(), 3);
        target.setBlock(new BlockPos(191, 72, 39), Blocks.STONE.defaultBlockState(), 3);
        var fireball = EntityTypes.SMALL_FIREBALL.create(source, EntitySpawnReason.COMMAND);
        require(fireball != null, "Could not create the damaging-projectile witness");
        fireball.setCustomName(Component.literal("Quality UUID witness"));
        spawn(source, "cross_world_fireball", fireball,
                new Vec3(2.2, 73.3, 40.2), new Vec3(0, 0, -.6));
    }

    /** Observes the actual native impact; it does not move or teleport the projectile. */
    private static final class QualitySnowball extends Snowball {
        private QualitySnowball(Level world) {
            super(EntityTypes.SNOWBALL, world);
        }

        @Override
        protected void onHit(HitResult hit) {
            super.onHit(hit);
            require(level().dimension() == Level.NETHER
                            && hit instanceof BlockHitResult blockHit
                            && blockHit.getBlockPos().equals(new BlockPos(192, 72, 38))
                            && Math.abs(hit.getLocation().z - 39) < 1e-6
                            && isRemoved() && crossedEntities.contains(getUUID()),
                    "The snowball did not execute its native destination impact: hit="
                            + hit.getLocation() + " world=" + level().dimension());
            resolvedThrowableProjectiles.add(getUUID());
            System.out.println("PORTAL_QUALITY_THROWABLE_PROJECTILE_OK uuid=" + getUUID()
                    + " hit=" + hit.getLocation());
        }
    }

    private static double horizontal(Vec3 vector) {
        return Math.hypot(vector.x, vector.z);
    }

    private static void prepare(ServerPlayer creator, Set<UUID> scenePortals) {
        var world = creator.level().getServer().overworld();
        loadChunks(world);
        var entityTarget = world.getServer().getLevel(Level.NETHER);
        require(entityTarget != null, "The quality entity target is missing");
        loadEntityTarget(entityTarget);
        for (int center : new int[] {0, 64}) {
            for (int x = center - 5; x <= center + 5; x++)
                for (int z = 18; z <= 46; z++)
                    for (int y = 69; y <= 77; y++)
                        if (x == center - 5 || x == center + 5 || z == 18 || z == 46
                                || y == 69 || y == 77)
                            world.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            for (int x = center - 4; x <= center + 4; x++)
                for (int z = 19; z <= 45; z++)
                    for (int y = 70; y <= 76; y++)
                        world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(new BlockPos(center, 76, 32), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        }
        var old = portals(world).stream().filter(PortalQualitySmoke::preserve).toList();
        old.forEach(Portal::discard);
        var existing = new HashSet<UUID>();
        for (var portal : portals(world)) existing.add(portal.getUUID());
        var proto = new ProtoPortal();
        proto.firstSide = new ProtoPortalSide(Level.OVERWORLD, new Vec3(-2, 70, 32));
        proto.firstSide.rightBottom = new Vec3(2, 70, 32);
        proto.firstSide.leftTop = new Vec3(-2, 74, 32);
        proto.secondSide = new ProtoPortalSide(Level.OVERWORLD, new Vec3(66, 70, 32));
        proto.secondSide.rightBottom = new Vec3(62, 70, 32);
        proto.secondSide.leftTop = new Vec3(66, 74, 32);
        PortalWandInteraction.RemoteCallables.finishPortalCreation(creator, proto);
        for (var portal : portals(world))
            if (!existing.contains(portal.getUUID())) {
                portal.addTag(TAG);
                faceIds.add(portal.getUUID());
                if (portal.getOriginPos().distanceTo(SOURCE) < .01 && portal.getNormal().z > .99)
                    forward = portal;
            }
        require(faceIds.size() == 4 && forward != null,
                "The two-player fixture did not create four wand faces");
        require(forward.transformPoint(SOURCE).distanceTo(DESTINATION) < 1e-6
                        && forward.transformLocalVec(new Vec3(0, 0, -1))
                                .distanceTo(new Vec3(0, 0, -1)) < 1e-6,
                "The quality wand fixture must preserve heading and scale");
        for (int x = -3; x <= 3; x++)
            for (int y = 70; y <= 74; y++) {
                world.setBlock(new BlockPos(x, y, 31), Blocks.STONE.defaultBlockState(), 3);
                world.setBlock(new BlockPos(64 + x, y, 32), Blocks.STONE.defaultBlockState(), 3);
            }
        scenePortals.addAll(faceIds);
        fixtureReady = true;
        requireGeometry(world);
        System.out.println("PORTAL_QUALITY_FIXTURE_READY faces=" + faceIds);
    }

    private static boolean entityTargetTicking(ServerLevel world) {
        return world != null && world.areEntitiesActuallyLoadedAndTicking(new ChunkPos(11, 2))
                && world.areEntitiesActuallyLoadedAndTicking(new ChunkPos(12, 2));
    }

    private static void loadEntityTarget(ServerLevel world) {
        // Chunk FULL and entity loading precede the ENTITY_TICKING transition. Pin
        // these rooms during initial setup, before the two clients start moving.
        for (int x : new int[] {11, 12}) {
            world.setChunkForced(x, 2, true);
            world.getChunk(x, 2);
            world.waitForEntities(new ChunkPos(x, 2), 0);
        }
    }

    private static void loadChunks(ServerLevel world) {
        for (int x : new int[] {-1, 0, 3, 4})
            for (int z : new int[] {1, 2}) {
                world.setChunkForced(x, z, true);
                world.getChunk(x, z);
                world.waitForEntities(new ChunkPos(x, z), 0);
            }
    }

    private static void requireGeometry(ServerLevel world) {
        for (int x = -3; x <= 3; x++)
            for (int y = 70; y <= 74; y++)
                require(world.getBlockState(new BlockPos(x, y, 31)).is(Blocks.STONE)
                                && world.getBlockState(new BlockPos(64 + x, y, 32)).is(Blocks.STONE),
                        "A quality backing wall was removed");
        require(portals(world).stream().filter(portal -> faceIds.contains(portal.getUUID())).count() == 4,
                "The quality wand cluster lost a face");
    }

    private static void requireDry(ServerPlayer player) {
        require(Math.abs(player.getY() - 70) < .025,
                "The stationary quality player was moved vertically: " + player.position());
        var box = player.getBoundingBox();
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++)
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++)
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++)
                    require(player.level().getFluidState(new BlockPos(x, y, z)).isEmpty(),
                            "Fluid entered the quality player's collision box");
    }

    private static int diamonds(ServerPlayer player) {
        return player.getInventory().countItem(Items.DIAMOND);
    }

    private static void requireSavedPlayer(ServerPlayer player) {
        var expected = saved.getAsJsonObject(player.getGameProfile().name());
        Vec3 position = new Vec3(expected.get("x").getAsDouble(), expected.get("y").getAsDouble(),
                expected.get("z").getAsDouble());
        require(player.level().dimension() == Level.OVERWORLD
                        && player.position().distanceTo(position) < 1e-5,
                "Rejoining displaced the saved player at the portal plane: expected=" + position
                        + " actual=" + player.position());
        require(diamonds(player) == expected.get("diamonds").getAsInt(),
                "The quality player's received inventory did not persist");
    }

    private static void save(MinecraftServer server, ServerPlayer first, ServerPlayer second) {
        requireGeometry(server.overworld());
        JsonObject snapshot = new JsonObject();
        var faces = new JsonArray();
        faceIds.forEach(id -> faces.add(id.toString()));
        snapshot.add("faces", faces);
        for (var player : List.of(first, second)) {
            double distance = Math.abs(player.getZ() - 32);
            require(distance > 0 && distance < .3,
                    "Persistence must save a player straddling its portal plane: " + player.position());
            var position = new JsonObject();
            position.addProperty("x", player.getX());
            position.addProperty("y", player.getY());
            position.addProperty("z", player.getZ());
            position.addProperty("diamonds", diamonds(player));
            snapshot.add(player.getGameProfile().name(), position);
        }
        try {
            Files.writeString(snapshot(server), snapshot.toString());
        } catch (Exception failure) {
            throw new AssertionError("Could not write the quality persistence evidence", failure);
        }
        server.saveEverything(false, true, true);
        System.out.println("PORTAL_QUALITY_PERSISTENCE_SAVED " + snapshot);
    }

    private static java.nio.file.Path snapshot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SNAPSHOT);
    }

    private static List<Portal> portals(ServerLevel world) {
        var storage = ((IELevelEntityGetterAdapter) ((IEWorld) world).portal_getEntityLookup()).getCache();
        var result = new ArrayList<Portal>();
        for (long chunk : storage.getAllChunksWithExistingSections())
            storage.getExistingSectionsInChunk(chunk).flatMap(section -> section.getEntities())
                    .forEach(entity -> {
                        if (entity instanceof Portal portal) result.add(portal);
                    });
        return result;
    }
}
