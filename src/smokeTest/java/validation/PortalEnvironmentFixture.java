package validation;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;

/** Native server broadcasts and separated worlds for audio and remote environment checks. */
final class PortalEnvironmentFixture {
    static final Vec3 REMOTE_ENTRY = new Vec3(128, 82, 384);
    static final Vec3 REMOTE_EXIT = new Vec3(256, 82, 384);
    static final Vec3 SHORTCUT_ENTRY = new Vec3(280, 82, 376);
    static final Vec3 SHORTCUT_EXIT = new Vec3(260, 82, 384);
    static final Vec3 REMOTE_SOUND = REMOTE_ENTRY.add(0, 0, 4);
    static final Vec3 SHORTCUT_SOUND = SHORTCUT_ENTRY.add(0, 0, 4);
    static final Vec3 DIRECT_SOUND = new Vec3(256, 82, 378);
    private static boolean prepared;

    private PortalEnvironmentFixture() {}

    private static boolean allowed(net.minecraft.commands.CommandSourceStack source) {
        return source.getPlayer() != null
                && source.getPlayer().getGameProfile().name().equals("OcclusionTest");
    }

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("portal_environment_setup")
                    .requires(PortalEnvironmentFixture::allowed).executes(context -> {
                        if (prepared) throw new AssertionError("The environment fixture was created twice");
                        var server = context.getSource().getServer();
                        var overworld = server.overworld();
                        var nether = server.getLevel(Level.NETHER);
                        if (nether == null) throw new AssertionError("The environment fixture has no Nether");
                        room(nether, REMOTE_ENTRY, Blocks.GOLD_BLOCK);
                        room(overworld, REMOTE_EXIT, Blocks.EMERALD_BLOCK);
                        room(overworld, SHORTCUT_ENTRY, Blocks.EMERALD_BLOCK);
                        pair(nether, overworld, REMOTE_ENTRY, REMOTE_EXIT, 2, scenePortals);
                        pair(overworld, overworld, SHORTCUT_ENTRY, SHORTCUT_EXIT, 1, scenePortals);
                        var player = context.getSource().getPlayerOrException();
                        player.removeAllEffects();
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                        player.teleportTo(overworld, 256, 80, 380, java.util.Set.of(), 0, 0, false);
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                                "weather rain 1000");
                        overworld.setRainLevel(1);
                        overworld.setThunderLevel(0);
                        qouteall.imm_ptl.core.chunk_loading.WorldInfoSender.sendWorldInfo(player, overworld);
                        prepared = true;
                        System.out.println("PORTAL_ENVIRONMENT_FIXTURE_READY scaled=2 shortcut=true");
                        return 1;
                    }));
            dispatcher.register(Commands.literal("portal_environment_sound")
                    .requires(PortalEnvironmentFixture::allowed)
                    .then(Commands.argument("route", StringArgumentType.word()).executes(context -> {
                        if (!prepared) throw new AssertionError("The environment fixture is not ready");
                        String route = StringArgumentType.getString(context, "route");
                        var server = context.getSource().getServer();
                        var world = route.equals("remote") ? server.getLevel(Level.NETHER) : server.overworld();
                        var position = switch (route) {
                            case "remote" -> REMOTE_SOUND;
                            case "shortcut" -> SHORTCUT_SOUND;
                            case "direct" -> DIRECT_SOUND;
                            default -> throw new AssertionError("Unknown acoustic route " + route);
                        };
                        var sound = switch (route) {
                            case "remote" -> SoundEvents.NOTE_BLOCK_BELL;
                            case "shortcut" -> SoundEvents.NOTE_BLOCK_CHIME;
                            default -> SoundEvents.NOTE_BLOCK_PLING;
                        };
                        // Exercise ServerLevel -> PlayerList broadcast -> redirected packet ->
                        // ClientLevel sound injection -> the real SoundManager listener.
                        world.playSound(null, position.x, position.y, position.z,
                                sound, SoundSource.BLOCKS, 2, .75f);
                        System.out.println("PORTAL_ENVIRONMENT_SOUND_SENT route=" + route);
                        return 1;
                    })));
        });
    }

    private static void room(ServerLevel world, Vec3 center,
            net.minecraft.world.level.block.Block floor) {
        int centerX = (int) center.x, centerZ = (int) center.z;
        for (int x = (centerX - 12) >> 4; x <= (centerX + 12) >> 4; x++)
            for (int z = (centerZ - 12) >> 4; z <= (centerZ + 12) >> 4; z++) {
                world.setChunkForced(x, z, true);
                world.getChunk(x, z);
            }
        for (int x = centerX - 12; x <= centerX + 12; x++)
            for (int z = centerZ - 12; z <= centerZ + 12; z++) {
                world.setBlock(new BlockPos(x, 79, z), floor.defaultBlockState(), 3);
                for (int y = 80; y <= 94; y++)
                    world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
    }

    private static void pair(ServerLevel from, ServerLevel to, Vec3 entry, Vec3 exit,
            double scale, java.util.Set<java.util.UUID> scenePortals) {
        var portal = new Portal(Portal.ENTITY_TYPE, from);
        portal.setOriginPos(entry);
        portal.setOrientationAndSize(Vec3.X_AXIS, Vec3.Y_AXIS, 4, 4);
        portal.setDestinationDimension(to.dimension());
        portal.setDestination(exit);
        portal.setScaling(scale);
        var reverse = PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
        from.addFreshEntity(portal);
        to.addFreshEntity(reverse);
        scenePortals.add(portal.getUUID());
        scenePortals.add(reverse.getUUID());
    }
}
