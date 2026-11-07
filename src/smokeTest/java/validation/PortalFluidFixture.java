package validation;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.ProtoPortal;
import qouteall.imm_ptl.peripheral.wand.ProtoPortalSide;

/** Sealed source/destination fluid chambers observed through actual native player ticks. */
final class PortalFluidFixture {
    static final String[] CASES = {"hidden_water", "hidden_lava", "visible_water",
            "destination_water", "scaled_water", "rotated_water"};
    static final Vec3 SOURCE = new Vec3(512, 86, 64);
    static final Vec3 DESTINATION = new Vec3(576, 86, 64);
    static final Vec3 START = new Vec3(512, 80, 64.1);
    private static final java.util.List<Portal> faces = new java.util.ArrayList<>();
    private static Probe probe;

    private static final class Probe {
        final String name;
        final java.util.UUID player;
        final int started;
        int sourceSamples, destinationSamples;
        boolean verified;
        Vec3 settled;

        Probe(String name, ServerPlayer player) {
            this.name = name;
            this.player = player.getUUID();
            started = player.level().getServer().getTickCount();
        }
    }

    private PortalFluidFixture() {}

    static boolean destinationCase(String name) {
        return name.endsWith("water") && !name.equals("hidden_water") && !name.equals("visible_water");
    }

    static double scale(String name) { return name.equals("scaled_water") ? 1 : 4; }

    static boolean physicalFluid(net.minecraft.world.entity.Entity entity) {
        var box = entity.getFluidInteractionBox();
        if (box == null) return false;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (!entity.level().getFluidState(pos).isEmpty()) return true;
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean allowed(net.minecraft.commands.CommandSourceStack source) {
        return source.getPlayer() != null
                && source.getPlayer().getGameProfile().name().equals("PortalTest");
    }

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("portal_fluid_setup").requires(PortalFluidFixture::allowed)
                    .then(Commands.argument("case", StringArgumentType.word()).executes(context -> {
                        setup(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "case"), scenePortals);
                        return 1;
                    })));
            dispatcher.register(Commands.literal("portal_fluid_verify").requires(PortalFluidFixture::allowed)
                    .then(Commands.argument("case", StringArgumentType.word()).executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        String name = StringArgumentType.getString(context, "case");
                        require(probe != null && probe.name.equals(name) && probe.player.equals(player.getUUID()),
                                "The native fluid verification referred to another player or case");
                        require(probe.sourceSamples >= (destinationCase(name) ? 8 : 40),
                                "The source fluid body did not receive enough authoritative native ticks");
                        if (destinationCase(name)) require(probe.destinationSamples >= 30,
                                "The destination fluid was not observed after natural traversal");
                        require(player.getAirSupply() < 290 || name.startsWith("hidden_"),
                                "Visible physical water did not retain normal air depletion");
                        probe.verified = true;
                        player.sendSystemMessage(Component.literal("PORTAL_FLUID_VERIFIED " + name));
                        System.out.println("PORTAL_FLUID_SERVER_OK case=" + name + " source="
                                + probe.sourceSamples + " destination=" + probe.destinationSamples);
                        return 1;
                    })));
            dispatcher.register(Commands.literal("portal_fluid_restore").requires(PortalFluidFixture::allowed)
                    .executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        require(probe != null && probe.verified, "Fluid restoration preceded verification");
                        ScaleUtils.setIPortalScaling(player, 1);
                        ScaleUtils.setBaseScale(player, 1);
                        player.setAirSupply(player.getMaxAirSupply());
                        player.sendSystemMessage(Component.literal("PORTAL_FLUID_RESTORED"));
                        return 1;
                    }));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (probe == null || probe.verified) return;
            var player = server.getPlayerList().getPlayer(probe.player);
            if (player != null) observe(player);
        });
    }

    private static void setup(ServerPlayer player, String name,
            java.util.Set<java.util.UUID> scenePortals) {
        require(java.util.Arrays.asList(CASES).contains(name), "Unknown native fluid case " + name);
        require(probe == null || probe.verified, "The native fluid case changed before verification");
        var server = player.level().getServer();
        var source = server.overworld();
        var destination = server.getLevel(Level.NETHER);
        require(destination != null, "The fluid fixture has no Nether");
        for (var portal : faces) {
            scenePortals.remove(portal.getUUID());
            portal.discard();
        }
        faces.clear();
        room(source, 512);
        room(destination, 576);
        boolean rotated = name.equals("rotated_water"), scaled = name.equals("scaled_water");
        for (int horizontal = -7; horizontal <= 7; horizontal++)
            for (int y = 80; y <= 90; y++) {
                source.setBlock(new BlockPos(512 + horizontal, y, 63), Blocks.STONE.defaultBlockState(), 3);
                destination.setBlock(rotated ? new BlockPos(576, y, 64 + horizontal)
                                : new BlockPos(576 + horizontal, y, 64),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        if (name.startsWith("hidden_"))
            fill(source, 505, 519, 57, 62, name.equals("hidden_lava") ? Blocks.LAVA : Blocks.WATER);
        if (name.equals("visible_water")) fill(source, 505, 519, 64, 71, Blocks.WATER);
        if (destinationCase(name)) {
            if (rotated) fill(destination, 569, 575, 57, 71, Blocks.WATER);
            else fill(destination, 569, 583, 57, 63, Blocks.WATER);
        }
        player.setGameMode(GameType.CREATIVE);
        var proto = new ProtoPortal();
        double width = scaled ? 4 : 8, height = scaled ? 4 : 12;
        double sourceY = scaled ? 82 : 86, destinationY = scaled ? 84 : 86;
        double destinationWidth = width * (scaled ? 2 : 1);
        double destinationHeight = height * (scaled ? 2 : 1);
        proto.firstSide = new ProtoPortalSide(source.dimension(),
                new Vec3(512 - width / 2, sourceY - height / 2, 64));
        proto.firstSide.rightBottom = new Vec3(512 + width / 2, sourceY - height / 2, 64);
        proto.firstSide.leftTop = new Vec3(512 - width / 2, sourceY + height / 2, 64);
        Vec3 destinationCenter = new Vec3(576, destinationY, 64);
        Vec3 destinationRight = rotated ? new Vec3(0, 0, 1) : new Vec3(-1, 0, 0);
        Vec3 leftBottom = destinationCenter.subtract(destinationRight.scale(destinationWidth / 2))
                .add(0, -destinationHeight / 2, 0);
        proto.secondSide = new ProtoPortalSide(destination.dimension(), leftBottom);
        proto.secondSide.rightBottom = leftBottom.add(destinationRight.scale(destinationWidth));
        proto.secondSide.leftTop = leftBottom.add(0, destinationHeight, 0);
        var previous = new java.util.HashSet<java.util.UUID>();
        for (var world : new ServerLevel[] {source, destination})
            for (var portal : portals(world)) previous.add(portal.getUUID());
        PortalWandInteraction.RemoteCallables.finishPortalCreation(player, proto);
        for (var world : new ServerLevel[] {source, destination})
            for (var portal : portals(world))
                if (!previous.contains(portal.getUUID())) {
                    faces.add(portal);
                    scenePortals.add(portal.getUUID());
                }
        require(faces.size() == 4, "The fluid fixture did not create four actual wand faces");
        ScaleUtils.setIPortalScaling(player, 1);
        ScaleUtils.setBaseScale(player, scale(name));
        player.removeAllEffects();
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setAirSupply(player.getMaxAirSupply());
        player.getFoodData().setFoodLevel(20);
        player.getInventory().clearContent();
        player.teleportTo(source, START.x, START.y, START.z, java.util.Set.of(), 180, 0, false);
        player.setDeltaMovement(Vec3.ZERO);
        probe = new Probe(name, player);
        player.sendSystemMessage(Component.literal("PORTAL_FLUID_SETUP " + name));
        System.out.println("PORTAL_FLUID_FIXTURE_READY case=" + name + " faces=4 scale=" + scale(name));
    }

    private static void fill(ServerLevel world, int minX, int maxX, int minZ, int maxZ,
            net.minecraft.world.level.block.Block fluid) {
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                for (int y = 80; y <= 90; y++)
                    world.setBlock(new BlockPos(x, y, z), fluid.defaultBlockState(), 3);
    }

    private static void room(ServerLevel world, int xCenter) {
        for (int chunkX = (xCenter - 8) >> 4; chunkX <= (xCenter + 8) >> 4; chunkX++)
            for (int chunkZ = 3; chunkZ <= 4; chunkZ++) {
                world.setChunkForced(chunkX, chunkZ, true);
                world.getChunk(chunkX, chunkZ);
                world.waitForEntities(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), 0);
            }
        for (int x = xCenter - 8; x <= xCenter + 8; x++)
            for (int z = 56; z <= 72; z++)
                for (int y = 79; y <= 91; y++) {
                    boolean boundary = x == xCenter - 8 || x == xCenter + 8
                            || z == 56 || z == 72 || y == 79 || y == 91;
                    world.setBlock(new BlockPos(x, y, z),
                            (boundary ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
                }
    }

    private static void observe(ServerPlayer player) {
        int elapsed = player.level().getServer().getTickCount() - probe.started;
        require(elapsed < 600, "The server native fluid fixture stalled: " + probe.name);
        if (elapsed < 25) return;
        if (probe.name.startsWith("hidden_")) {
            require(physicalFluid(player), "The hidden-fluid chamber does not intersect the raw player body");
            require(player.level().dimension() == Level.OVERWORLD && !player.isInWater()
                            && !player.isInLava() && !player.isEyeInFluid(FluidTags.WATER)
                            && player.getFluidHeight(FluidTags.WATER) == 0
                            && player.getFluidHeight(FluidTags.LAVA) == 0,
                    "An invisible source fluid affected the native player: " + probe.name);
            require(player.getAirSupply() == player.getMaxAirSupply()
                            && player.getHealth() == player.getMaxHealth(),
                    "An invisible source fluid drowned or damaged the player");
            if (probe.settled == null) probe.settled = player.position();
            require(player.position().distanceTo(probe.settled) < .025,
                    "An invisible source fluid pushed the stopped authoritative player");
            probe.sourceSamples++;
            return;
        }
        require(player.isInWater() && !player.isInLava()
                        && player.getFluidHeight(FluidTags.WATER) > 0,
                "A physically visible water body lost its native interaction: " + probe.name);
        if (player.level().dimension() == Level.NETHER) {
            require(destinationCase(probe.name) && player.isEyeInFluid(FluidTags.WATER),
                    "The physical destination water did not submerge the actual eye");
            probe.destinationSamples++;
        } else {
            require(player.level().dimension() == Level.OVERWORLD, "The fluid player entered an unrelated world");
            if (probe.name.equals("visible_water"))
                require(player.isEyeInFluid(FluidTags.WATER), "Visible source water did not submerge the native eye");
            else require(!physicalFluid(player) && !player.isEyeInFluid(FluidTags.WATER),
                    "The projected-water probe no longer has a dry physical source and eye");
            probe.sourceSamples++;
        }
    }

    private static java.util.List<Portal> portals(ServerLevel world) {
        var storage = ((qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter)
                ((qouteall.imm_ptl.core.ducks.IEWorld) world).portal_getEntityLookup()).getCache();
        var result = new java.util.ArrayList<Portal>();
        for (long chunk : storage.getAllChunksWithExistingSections())
            storage.getExistingSectionsInChunk(chunk).flatMap(section -> section.getEntities())
                    .forEach(entity -> { if (entity instanceof Portal portal) result.add(portal); });
        return result;
    }
}
