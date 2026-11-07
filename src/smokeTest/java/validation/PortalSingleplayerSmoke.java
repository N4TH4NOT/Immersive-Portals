package validation;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import qouteall.imm_ptl.core.mc_utils.NbtCompatibility;
import qouteall.imm_ptl.core.portal.Portal;

import java.nio.file.Files;
import java.util.List;

/** Isolated integrated-server verification, including the optional example datapack. */
final class PortalSingleplayerSmoke {
    private static boolean openedCreationScreen;
    private static int stackScreenTicks;

    static boolean prepare(Minecraft client) {
        if (!openedCreationScreen) {
            openedCreationScreen = true;
            net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(client, () -> {});
        } else if (stackScreenTicks == 0 && client.gui.screen()
                instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen screen) {
            ((qouteall.imm_ptl.peripheral.ducks.IECreateWorldScreen) screen).ip_openDimStackScreen();
            stackScreenTicks = 1;
        } else if (stackScreenTicks > 0 && ++stackScreenTicks >= 10) {
            System.out.println("PORTAL_SMOKE_CREATE_WORLD_GUI_OK");
            start(client);
            return true;
        }
        return false;
    }

    static void finishOpening(Minecraft client) {
        if (System.getProperty("portalSmoke.existingWorld", "").matches("PortalMigration-[0-9]+")
                && client.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen) {
            try {
                var field = screen.getClass().getDeclaredField("onProceed");
                field.setAccessible(true);
                ((net.minecraft.client.gui.screens.BackupConfirmScreen.Listener) field.get(screen))
                        .proceed(true, false);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    static void start(Minecraft client) {
        String existing = System.getProperty("portalSmoke.existingWorld", "");
        if (!existing.isEmpty()) {
            if (!existing.matches("PortalMigration-[0-9]+"))
                throw new AssertionError("Only an isolated verification world may be reused");
            client.createWorldOpenFlows().openWorld(existing, () -> {});
            return;
        }
        String name = "PortalMigration-" + System.currentTimeMillis();
        var datapack = client.gameDirectory.toPath().resolve("saves").resolve(name)
                .resolve("datapacks/example_custom_portal_gen.zip");
        try (var stream = PortalSingleplayerSmoke.class.getResourceAsStream(
                "/migration-example/example_custom_portal_gen.zip")) {
            if (stream == null) throw new AssertionError("Missing built example archive");
            Files.createDirectories(datapack.getParent());
            Files.copy(stream, datapack);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not prepare isolated verification world", exception);
        }
        var defaults = WorldDataConfiguration.DEFAULT;
        var config = new WorldDataConfiguration(new DataPackConfig(
                List.of("vanilla", "file/example_custom_portal_gen.zip"), List.of()),
                defaults.enabledFeatures());
        var settings = new LevelSettings(name, GameType.CREATIVE,
                LevelSettings.DifficultySettings.DEFAULT, true, config);
        client.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(987654321L, false, false),
                WorldPresets::createNormalWorldDimensions, client.gui.screen());
    }

    static void validate(MinecraftServer server, List<Portal> fixtures) {
        for (String name : List.of("skyland", "chaos", "void")) {
            var world = server.getLevel(qouteall.q_misc_util.Helper.dimIdToKey("immersive_portals:" + name));
            if (world == null) throw new AssertionError("Example dimension not loaded: " + name);
            world.getChunk(0, 0);
        }
        var registry = server.registryAccess().lookupOrThrow(
                qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration.REGISTRY_KEY);
        if (registry.size() != 7) throw new AssertionError("Example generation rules were not loaded");
        for (Portal fixture : fixtures) {
            var clone = (Portal) fixture.getType().create(fixture.level(),
                    net.minecraft.world.entity.EntitySpawnReason.LOAD);
            if (clone == null) throw new AssertionError("Could not recreate fixture " + fixture);
            NbtCompatibility.load(clone, NbtCompatibility.save(fixture));
            var expected = fixture.writePortalDataToNbt();
            var actual = clone.writePortalDataToNbt();
            // The historical format reloads quaternion components at float precision and
            // normalizes them. Compare that transform geometrically, and all other tags exactly.
            if (!qouteall.q_misc_util.my_util.DQuaternion.isClose(
                    clone.getRotationD(), fixture.getRotationD(), 1e-14))
                throw new AssertionError("Portal rotation changed on NBT reload: " + fixture);
            for (String key : List.of("rotationA", "rotationB", "rotationC", "rotationD")) {
                expected.remove(key);
                actual.remove(key);
            }
            if (!clone.getOriginPos().equals(fixture.getOriginPos())
                    || !actual.equals(expected))
                throw new AssertionError("Portal NBT round trip changed " + fixture
                        + " expected=" + expected + " actual=" + actual);
        }
        System.out.println("PORTAL_SMOKE_DATAPACK_WORLDS_AND_NBT_OK");
    }

    static void validateSameWorldTeleport(net.minecraft.server.level.ServerPlayer player) {
        var manager = qouteall.imm_ptl.core.IPPerServerInfo.of(player.level().getServer())
                .customPortalGenManager;
        if (manager == null) throw new AssertionError("Custom generation manager missing");
        try {
            var field = manager.getClass().getDeclaredField("playerPosBeforeTravel");
            field.setAccessible(true);
            if (((java.util.Map<?, ?>) field.get(manager)).containsKey(player.getUUID()))
                throw new AssertionError("Same-world teleport triggered dimension-change generation");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        System.out.println("PORTAL_SMOKE_SAME_WORLD_TELEPORT_OK");
    }
}
