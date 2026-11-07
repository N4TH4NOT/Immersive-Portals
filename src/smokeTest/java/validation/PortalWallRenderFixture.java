package validation;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Builds the creative wand's real four-face cluster against an intact source wall. */
final class PortalWallRenderFixture {
    private PortalWallRenderFixture() {}

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
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
                            for (int x = -3; x <= 3; x++)
                                for (int y = 70; y <= 76; y++) {
                                    source.setBlock(new BlockPos(x, y, -1),
                                            Blocks.STONE.defaultBlockState(), 3);
                                    // Its green face provides an independent pixel-level witness
                                    // that the destination, rather than the source wall, is visible.
                                    destination.setBlock(new BlockPos(x, y, -4),
                                            Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
                                }
                            System.out.println("PORTAL_RENDER_WALL_FIXTURE_READY faces=4");
                            return 1;
                        })));
    }
}
