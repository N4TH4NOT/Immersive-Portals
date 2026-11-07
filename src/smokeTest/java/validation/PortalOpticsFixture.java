package validation;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Isolated physical geometry for pitched aperture, terrain occlusion and lighting oracles. */
final class PortalOpticsFixture {
    private static boolean prepared;
    private static Boolean lit;
    private static Boolean sourceOccluder;
    private static Boolean destinationOccluder;

    private PortalOpticsFixture() {}

    private static boolean allowed(net.minecraft.commands.CommandSourceStack source) {
        return source.getPlayer() != null
                && source.getPlayer().getGameProfile().name().equals("OpticsTest");
    }

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("portal_optics_setup")
                    .requires(PortalOpticsFixture::allowed).executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        var from = context.getSource().getServer().overworld();
                        var to = context.getSource().getServer().getLevel(Level.NETHER);
                        if (to == null) throw new AssertionError("The optical room has no Nether");
                        if (prepared) throw new AssertionError("The optical fixture was created twice");
                        for (var world : new ServerLevel[] {from, to}) {
                            for (int x = 7; x <= 8; x++)
                                for (int z = 7; z <= 8; z++) {
                                    world.setChunkForced(x, z, true);
                                    world.getChunk(x, z);
                                }
                            for (int x = 116; x <= 140; x++)
                                for (int z = 116; z <= 140; z++) {
                                    // Red sentinels become visible if floor faces incorrectly
                                    // disappear. They are never the geometrically nearest face.
                                    world.setBlock(new BlockPos(x, 123, z),
                                            Blocks.RED_CONCRETE.defaultBlockState(), 3);
                                    world.setBlock(new BlockPos(x, 125, z),
                                            (world == from ? Blocks.STONE : Blocks.EMERALD_BLOCK)
                                                    .defaultBlockState(), 3);
                                    for (int y = 126; y <= 135; y++)
                                        world.setBlock(new BlockPos(x, y, z),
                                                Blocks.AIR.defaultBlockState(), 3);
                                }
                        }
                        for (int x = 116; x <= 140; x++)
                            for (int y = 118; y <= 142; y++) {
                                from.setBlock(new BlockPos(x, y, 127),
                                        Blocks.STONE.defaultBlockState(), 3);
                                from.setBlock(new BlockPos(x, y, 126),
                                        Blocks.RED_CONCRETE.defaultBlockState(), 3);
                            }
                        for (int x = 116; x <= 140; x++)
                            for (int z = 116; z <= 140; z++)
                                for (int y = 126; y <= 134; y++)
                                    if (x == 116 || x == 140 || z == 116 || z == 140 || y == 134)
                                        to.setBlock(new BlockPos(x, y, z),
                                                Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
                        player.setGameMode(GameType.CREATIVE);
                        player.removeAllEffects();
                        var faces = PortalCollisionSmoke.createWandFaces(player, from, to,
                                new Vec3(128, 130, 128), 8, 8);
                        for (var face : faces) scenePortals.add(face.getUUID());
                        var server = context.getSource().getServer();
                        server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack(), "time set noon");
                        prepared = true;
                        lights(to, true);
                        occluders(from, to, false, false);
                        System.out.println("PORTAL_OPTICS_FIXTURE_READY faces=" + faces.size());
                        return 1;
                    }));
            dispatcher.register(Commands.literal("portal_optics_view")
                    .requires(PortalOpticsFixture::allowed)
                    .then(Commands.argument("distance", DoubleArgumentType.doubleArg(.000001, 4))
                            .then(Commands.argument("pitch", DoubleArgumentType.doubleArg(0, 90))
                                    .then(Commands.argument("oblique", BoolArgumentType.bool())
                                            .then(Commands.argument("mode", StringArgumentType.word())
                                                    .executes(context -> {
                                                        if (!prepared)
                                                            throw new AssertionError("Optical fixture is not ready");
                                                        var server = context.getSource().getServer();
                                                        var from = server.overworld();
                                                        var to = server.getLevel(Level.NETHER);
                                                        String mode = StringArgumentType.getString(context, "mode");
                                                        boolean reference = mode.startsWith("reference_");
                                                        lights(to, !mode.contains("dark"));
                                                        occluders(from, to, mode.equals("source"), mode.equals("destination"));
                                                        var player = context.getSource().getPlayerOrException();
                                                        player.removeAllEffects();
                                                        // This optical camera deliberately ignores body escape.
                                                        // Separate PortalTest cases retain physical movement.
                                                        player.setGameMode(GameType.SPECTATOR);
                                                        player.teleportTo(reference ? to : from,
                                                                128, 128.38,
                                                                128 + DoubleArgumentType.getDouble(context, "distance"),
                                                                java.util.Set.of(),
                                                                BoolArgumentType.getBool(context, "oblique") ? 135 : 180,
                                                                (float) DoubleArgumentType.getDouble(context, "pitch"), false);
                                                        System.out.println("PORTAL_OPTICS_VIEW_READY mode=" + mode);
                                                        return 1;
                                                    }))))));
        });
    }

    private static void lights(ServerLevel world, boolean enabled) {
        if (lit != null && lit == enabled) return;
        for (int x = 120; x <= 136; x += 4)
            for (int z = 120; z <= 136; z += 4)
                world.setBlock(new BlockPos(x, 133, z),
                        (enabled ? Blocks.SEA_LANTERN : Blocks.BLACK_CONCRETE).defaultBlockState(), 3);
        lit = enabled;
    }

    private static void occluders(ServerLevel from, ServerLevel to, boolean source, boolean destination) {
        if (sourceOccluder == null || sourceOccluder != source) {
            for (int x = 124; x <= 132; x++)
                for (int y = 126; y <= 133; y++)
                    from.setBlock(new BlockPos(x, y, 129),
                            (source ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            sourceOccluder = source;
        }
        if (destinationOccluder == null || destinationOccluder != destination) {
            for (int x = 124; x <= 132; x++)
                for (int y = 126; y <= 133; y++)
                    to.setBlock(new BlockPos(x, y, 126),
                            (destination ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            destinationOccluder = destination;
        }
    }
}
