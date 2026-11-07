package validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import qouteall.dimlib.DimensionTemplate;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.dimension.DimensionIntId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Connected-client coverage for dynamic worlds, command cloning, evacuation and saved data. */
public final class DimensionLibrarySmoke {
    private static final Identifier KEPT_ID = Identifier.parse("imm_ptl_smoke:kept");
    private static final Identifier REMOVED_ID = Identifier.parse("imm_ptl_smoke:removed");
    private static final ResourceKey<Level> KEPT = ResourceKey.create(Registries.DIMENSION, KEPT_ID);
    private static final ResourceKey<Level> REMOVED = ResourceKey.create(Registries.DIMENSION, REMOVED_ID);
    private static final BlockPos MARKER = new BlockPos(8, 96, 8);
    private static final String SNAPSHOT = "portal-smoke-dynamic-dimensions.json";
    private static final Map<MinecraftServer, ServerState> STATES = new WeakHashMap<>();

    private enum Stage { IDLE, ADDED, OCCUPIED, REMOVING, REMOVED, COMPLETE }

    private static final class ServerState {
        Stage stage = Stage.IDLE;
        UUID actor;
        ServerLevel removedWorld;
        boolean keptUpdate;
        boolean cloneUpdate;
        boolean removedUpdate;
        boolean preRemove;
        boolean finishRequested;
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                dispatcher.register(Commands.literal("portal_smoke_dimension_library")
                        .requires(source -> source.getPlayer() != null)
                        .then(Commands.literal("begin").executes(context -> {
                            begin(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("enter").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ServerState state = requireState(player, Stage.ADDED);
                            PortalAPI.teleportEntity(player, state.removedWorld, new Vec3(0, 72, 0));
                            state.stage = Stage.OCCUPIED;
                            return 1;
                        }))
                        .then(Commands.literal("remove").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ServerState state = requireState(player, Stage.OCCUPIED);
                            if (player.level() != state.removedWorld)
                                throw new AssertionError("Player did not enter the removable dimension");
                            state.stage = Stage.REMOVING;
                            run(player.level().getServer(), "dims remove_dimension " + REMOVED_ID);
                            return 1;
                        }))
                        .then(Commands.literal("finish").executes(context -> {
                            requestFinish(context.getSource().getPlayerOrException());
                            return 1;
                        }))));
        DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.register((server, dimensions) -> {
            ServerState state = STATES.get(server);
            if (state == null) return;
            state.keptUpdate |= dimensions.contains(KEPT);
            state.cloneUpdate |= dimensions.contains(REMOVED);
            if (state.stage == Stage.REMOVING) state.removedUpdate |= !dimensions.contains(REMOVED);
        });
        DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.register(world -> {
            ServerState state = STATES.get(world.getServer());
            if (state != null && world == state.removedWorld) {
                if (!DimensionAPI.isDimensionAlive(world))
                    throw new AssertionError("Pre-remove event arrived after the world was detached");
                state.preRemove = true;
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerState state = STATES.get(server);
            if (state == null || state.stage != Stage.REMOVING || server.getLevel(REMOVED) != null) return;
            ServerPlayer player = server.getPlayerList().getPlayer(state.actor);
            if (!state.preRemove || !state.removedUpdate || DimensionAPI.isDimensionAlive(state.removedWorld)
                    || DimensionAPI.dimensionExistsInRegistry(server, REMOVED_ID)
                    || player == null || player.level() != server.overworld())
                throw new AssertionError("Dimension removal did not detach, notify and evacuate the player");
            if (!Files.isRegularFile(removedRegion(server)))
                throw new AssertionError("Dimension removal deleted or failed to save the old chunk data");
            state.stage = Stage.REMOVED;
            if (state.finishRequested) finish(player);
        });
    }

    public static void validateOnServerStart(MinecraftServer server) {
        STATES.put(server, new ServerState());
        if (!Boolean.getBoolean("portalSmoke.dimensionReload")) return;
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(snapshot(server))).getAsJsonObject();
            ServerLevel kept = server.getLevel(KEPT);
            if (kept == null || server.getLevel(REMOVED) != null
                    || DimensionAPI.dimensionExistsInRegistry(server, REMOVED_ID)
                    || !DimensionAPI.dimensionExistsInRegistry(server, KEPT_ID)
                    || !kept.getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK)
                    || !saved.get("kept").equals(codec(server, KEPT))
                    || !Files.isRegularFile(removedRegion(server)))
                throw new AssertionError("Dynamic dimensions or their saved chunks changed after restart");
            System.out.println("PORTAL_SMOKE_DYNAMIC_DIMENSION_STATE_OK");
        } catch (IOException exception) {
            throw new AssertionError("Missing dynamic-dimension restart evidence", exception);
        }
    }

    private static void begin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerState state = STATES.computeIfAbsent(server, ignored -> new ServerState());
        if (state.stage != Stage.IDLE) throw new AssertionError("Dynamic dimension scenario started twice");
        state.actor = player.getUUID();
        boolean reload = Boolean.getBoolean("portalSmoke.dimensionReload");
        if (!reload) {
            if (server.getLevel(KEPT) != null || server.getLevel(REMOVED) != null)
                throw new AssertionError("Dynamic fixtures already exist in a fresh verification world");
            DimensionAPI.addDimensionDynamically(server, KEPT_ID,
                    DimensionTemplate.VOID_TEMPLATE.createLevelStem(server));
        }
        ServerLevel kept = server.getLevel(KEPT);
        if (kept == null) throw new AssertionError("Dynamic dimension was not created");
        DimensionAPI.addDimensionIfNotExists(server, KEPT_ID, () -> {
            throw new AssertionError("Existing dimension invoked the replacement supplier");
        });
        kept.setBlock(MARKER, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        run(server, "dims clone_dimension " + KEPT_ID + " \"" + REMOVED_ID + "\"");
        ServerLevel clone = server.getLevel(REMOVED);
        if (clone == null || !codec(server, KEPT).equals(codec(server, REMOVED))
                || clone.getChunkSource().getGenerator() == kept.getChunkSource().getGenerator())
            throw new AssertionError("Dimension cloning did not recreate independent world-generation settings");
        if (reload) {
            if (!clone.getBlockState(MARKER).is(Blocks.EMERALD_BLOCK))
                throw new AssertionError("Recreating a removed dimension did not recover its saved blocks");
        } else {
            if (!clone.getBlockState(MARKER).isAir())
                throw new AssertionError("Cloning settings also copied unrelated source-world blocks");
            clone.setBlock(MARKER, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
        }
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                clone.setBlock(new BlockPos(x, 71, z), Blocks.BEDROCK.defaultBlockState(), 3);
        state.removedWorld = clone;
        state.stage = Stage.ADDED;
    }

    private static ServerState requireState(ServerPlayer player, Stage stage) {
        ServerState state = STATES.get(player.level().getServer());
        if (state == null || state.stage != stage || !player.getUUID().equals(state.actor))
            throw new AssertionError("Unexpected dynamic-dimension command stage: " + stage);
        return state;
    }

    private static void finish(ServerPlayer player) {
        ServerState state = requireState(player, Stage.REMOVED);
        MinecraftServer server = player.level().getServer();
        if (!state.keptUpdate || !state.cloneUpdate
                || !DimensionIntId.getServerMap(server).containsDimId(KEPT)
                || DimensionIntId.getServerMap(server).containsDimId(REMOVED))
            throw new AssertionError("Server dimension events or integer mapping did not follow changes");
        JsonObject saved = new JsonObject();
        saved.add("kept", codec(server, KEPT));
        try {
            Files.writeString(snapshot(server), saved.toString());
        } catch (IOException exception) {
            throw new AssertionError("Could not write dynamic-dimension restart evidence", exception);
        }
        server.saveEverything(false, true, true);
        state.stage = Stage.COMPLETE;
        System.out.println("PORTAL_SMOKE_SERVER_DYNAMIC_DIMENSIONS_OK");
        McRemoteProcedureCall.tellClientToInvoke(player,
                "validation.DimensionLibrarySmoke.RemoteCallables.finished");
    }

    private static void requestFinish(ServerPlayer player) {
        ServerState state = STATES.get(player.level().getServer());
        if (state == null || !player.getUUID().equals(state.actor)
                || (state.stage != Stage.REMOVING && state.stage != Stage.REMOVED))
            throw new AssertionError("Unexpected dynamic-dimension completion request");
        if (state.stage == Stage.REMOVING) state.finishRequested = true;
        else finish(player);
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static JsonElement codec(MinecraftServer server, ResourceKey<Level> dimension) {
        LevelStem stem = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM)
                .getValue(dimension.identifier());
        if (stem == null) throw new AssertionError("Missing dimension settings: " + dimension.identifier());
        return LevelStem.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), stem)
                .getOrThrow();
    }

    private static Path snapshot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SNAPSHOT);
    }

    private static Path removedRegion(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("dimensions")
                .resolve(REMOVED_ID.getNamespace()).resolve(REMOVED_ID.getPath()).resolve("region/r.0.0.mca");
    }

    private static int clientStage;
    private static long clientDeadline;
    private static boolean clientFinished;

    /** Call after the normal traversal checks; true permits the verification client to exit. */
    public static boolean tickClient(Minecraft client) {
        if (clientStage == 0) {
            command(client, "begin");
            nextClientStage();
        } else if (clientStage == 1) {
            if (!DimensionAPI.getClientDimensionIds().containsAll(java.util.Set.of(KEPT, REMOVED)))
                return waiting();
            if (!DimensionAPI.getClientDimensionIdToTypeMap().keySet().containsAll(java.util.Set.of(KEPT, REMOVED))
                    || !ClientWorldLoader.getServerDimensions().containsAll(java.util.Set.of(KEPT, REMOVED))
                    || !DimensionIntId.getClientMap().containsDimId(KEPT)
                    || !DimensionIntId.getClientMap().containsDimId(REMOVED))
                throw new AssertionError("Client dimension metadata and integer mappings disagree");
            ClientWorldLoader.getWorld(REMOVED);
            command(client, "enter");
            nextClientStage();
        } else if (clientStage == 2) {
            if (client.level.dimension() != REMOVED) return waiting();
            command(client, "remove");
            nextClientStage();
        } else if (clientStage == 3) {
            if (client.level.dimension() != Level.OVERWORLD
                    || DimensionAPI.getClientDimensionIds().contains(REMOVED)) return waiting();
            if (DimensionAPI.getClientDimensionIdToTypeMap().containsKey(REMOVED)
                    || ClientWorldLoader.getServerDimensions().contains(REMOVED)
                    || DimensionIntId.getClientMap().containsDimId(REMOVED)
                    || ClientWorldLoader.getOptionalWorld(REMOVED) != null
                    || ClientWorldLoader.WORLD_RENDERER_MAP.containsKey(REMOVED)
                    || !DimensionAPI.getClientDimensionIds().contains(KEPT))
                throw new AssertionError("Removed dimension left stale client worlds, renderers or mappings");
            command(client, "finish");
            nextClientStage();
        } else if (clientStage == 4) {
            if (!clientFinished) return waiting();
            System.out.println(Boolean.getBoolean("portalSmoke.dimensionReload")
                    ? "PORTAL_SMOKE_DYNAMIC_DIMENSION_RELOAD_OK"
                    : "PORTAL_SMOKE_DYNAMIC_DIMENSIONS_OK");
            clientStage++;
            return true;
        }
        return clientStage == 5;
    }

    private static void nextClientStage() {
        clientStage++;
        clientDeadline = System.nanoTime() + 30_000_000_000L;
    }

    private static boolean waiting() {
        if (System.nanoTime() >= clientDeadline)
            throw new AssertionError("Dynamic dimension synchronization timed out at stage " + clientStage);
        return false;
    }

    private static void command(Minecraft client, String action) {
        client.player.connection.sendCommand("portal_smoke_dimension_library " + action);
    }

    public static final class RemoteCallables {
        public static void finished() {
            clientFinished = true;
        }
    }
}
