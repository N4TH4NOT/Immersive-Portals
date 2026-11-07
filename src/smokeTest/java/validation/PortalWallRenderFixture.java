package validation;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Builds the creative wand's real four-face cluster against an intact source wall. */
final class PortalWallRenderFixture {
    private PortalWallRenderFixture() {}

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
                dispatcher.register(Commands.literal("portal_smoke_backing_wall")
                        .requires(source -> source.getPlayer() != null
                                && source.getPlayer().getGameProfile().name().equals("OcclusionTest"))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var source = context.getSource().getServer().overworld();
                            var destination = context.getSource().getServer().getLevel(Level.NETHER);
                            if (destination == null)
                                throw new AssertionError("The wall rendering fixture has no Nether");
                            PortalCollisionSmoke.replacePlayerPortals(
                                    player, source, destination, scenePortals);
                            // Illuminate the source face with real block light. An opaque
                            // stone wall in this sealed room otherwise looks nearly black;
                            // that is unrelated to whether it hides the green destination.
                            for (int x = -10; x <= 10; x += 5)
                                for (int z = 5; z <= 10; z += 5)
                                    source.setBlock(new BlockPos(x, 76, z),
                                            Blocks.SEA_LANTERN.defaultBlockState(), 3);
                            for (int x = -3; x <= 3; x++)
                                for (int y = 70; y <= 76; y++)
                                    source.setBlock(new BlockPos(x, y, -1),
                                            Blocks.STONE.defaultBlockState(), 3);
                            for (int x = -12; x <= 12; x++)
                                for (int y = 70; y <= 76; y++) {
                                    // Its green face provides an independent pixel-level witness
                                    // that the destination, rather than the source wall, is visible.
                                    destination.setBlock(new BlockPos(x, y, -4),
                                            Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
                                }
                            System.out.println("PORTAL_RENDER_WALL_FIXTURE_READY faces=4");
                            return 1;
                        }));
                dispatcher.register(Commands.literal("portal_smoke_wall_camera")
                        .requires(source -> source.getPlayer() != null
                                && source.getPlayer().getGameProfile().name().equals("OcclusionTest"))
                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(.0000001, .05))
                                .then(Commands.argument("oblique", BoolArgumentType.bool())
                                        .executes(context -> {
                                            double distance = DoubleArgumentType.getDouble(
                                                    context, "distance");
                                            boolean oblique = BoolArgumentType.getBool(
                                                    context, "oblique");
                                            // Place the camera deliberately without depending on
                                            // walking or changing the separate teleport scenario.
                                            // A setup teleport puts the body inside the backing
                                            // block before portal contact is registered, allowing
                                            // vanilla suffocation recovery to move the camera.
                                            // Spectator mode isolates this optical probe; the
                                            // PortalTest scenarios retain physical movement.
                                            var player = context.getSource().getPlayerOrException();
                                            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                                            player.teleportTo(
                                                    context.getSource().getServer().overworld(),
                                                    0, 70, distance, java.util.Set.of(),
                                                    oblique ? 135 : 180, 0, false);
                                            System.out.println("PORTAL_RENDER_WALL_CAMERA_READY distance="
                                                    + distance + " oblique=" + oblique);
                                            return 1;
                                        }))));
        });
    }
}
