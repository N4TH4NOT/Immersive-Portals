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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.collision.PortalFluidInteraction;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.ProtoPortal;
import qouteall.imm_ptl.peripheral.wand.ProtoPortalSide;

/** Sealed source/destination fluid chambers observed through actual native player ticks. */
final class PortalFluidFixture {
    static final String[] CASES = {"hidden_water", "hidden_lava", "visible_water",
            "destination_water", "scaled_water", "rotated_water", "narrow_edge_water",
            "nonplayer_lava", "destination_lava"};
    static final Vec3 SOURCE = new Vec3(512, 86, 64);
    static final Vec3 DESTINATION = new Vec3(576, 86, 64);
    static final Vec3 START = new Vec3(512, 80, 64.1);
    private static final java.util.List<Portal> faces = new java.util.ArrayList<>();
    private static Probe probe;

    private static final class Probe {
        final String name;
        final java.util.UUID player;
        final int started;
        final java.util.ArrayDeque<String> nativeHistory = new java.util.ArrayDeque<>();
        int sourceSamples, destinationSamples;
        boolean verified;
        boolean positiveForce, shallowForce, nonplayerReported;
        ItemEntity positiveItem, shallowItem;
        Vec3 settled;

        Probe(String name, ServerPlayer player) {
            this.name = name;
            this.player = player.getUUID();
            started = player.level().getServer().getTickCount();
        }
    }

    private PortalFluidFixture() {}

    static boolean destinationCase(String name) {
        return name.equals("destination_lava") || name.equals("destination_water")
                || name.equals("scaled_water") || name.equals("rotated_water");
    }

    static double scale(String name) {
        return name.equals("scaled_water") || name.equals("narrow_edge_water")
                || name.equals("nonplayer_lava") ? 1 : 4;
    }

    static Vec3 start(String name) {
        return name.equals("narrow_edge_water") ? new Vec3(513.1, 80.875, 64.1)
                : name.equals("nonplayer_lava") ? new Vec3(512, 80, 70) : START;
    }

    static boolean rawSourceFluid(String name) {
        return !destinationCase(name) && !name.equals("nonplayer_lava");
    }

    static boolean physicalFluid(net.minecraft.world.entity.Entity entity) {
        var box = entity.getFluidInteractionBox();
        if (box == null) return false;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (!entity.level().getFluidState(pos).isEmpty()) return true;
        return false;
    }

    static String nativeState(net.minecraft.world.entity.Entity entity) {
        return "playerTick=" + entity.tickCount + " dimension=" + entity.level().dimension().identifier()
                + " feet=" + entity.position() + " eye=" + entity.getEyePosition()
                + " velocity=" + entity.getDeltaMovement() + " pose=" + entity.getPose()
                + " water=" + entity.isInWater() + " lava=" + entity.isInLava()
                + " waterHeight=" + entity.getFluidHeight(FluidTags.WATER)
                + " lavaHeight=" + entity.getFluidHeight(FluidTags.LAVA)
                + " waterEye=" + entity.isEyeInFluid(FluidTags.WATER)
                + " scale=" + ScaleUtils.getScale(entity);
    }

    static String contactState(net.minecraft.world.entity.Entity entity) {
        var handler = ((qouteall.imm_ptl.core.ducks.IEEntity) entity).ip_getPortalCollisionHandler();
        var contacts = handler == null ? java.util.List.<String>of()
                : handler.getCollidingPortals().stream().map(portal -> {
                    var world = portal.getDestinationWorld();
                    var position = BlockPos.containing(portal.transformPoint(entity.position()
                            .subtract(portal.getNormal().scale(.5)).add(0, .1, 0)));
                    return "id=" + portal.getUUID() + " removed=" + portal.isRemoved()
                            + " source=" + portal.getOriginPos() + " normal=" + portal.getNormal()
                            + " eyeDistance=" + portal.getDistanceToPlane(entity.getEyePosition())
                            + " destination=" + portal.getDestDim().identifier()
                            + " remoteProbe=" + position + " remoteFluid="
                            + (world == null ? "missing_world" : world.getFluidState(position));
                }).toList();
        return "body=" + entity.getBoundingBox() + " fluidBox=" + entity.getFluidInteractionBox()
                + " rawFluid=" + physicalFluid(entity) + " contacts=" + contacts
                + " waterForce=" + PortalFluidInteraction.lastForce(entity, FluidTags.WATER);
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
                        require(player.getAirSupply() < 290 || name.startsWith("hidden_")
                                        || name.equals("destination_lava") || name.equals("narrow_edge_water")
                                        || name.equals("nonplayer_lava"),
                                "Visible physical water did not retain normal air depletion");
                        if (name.equals("destination_lava")) require(player.getHealth() == player.getMaxHealth(),
                                "The fire-resistant lava observer took damage");
                        if (name.equals("nonplayer_lava")) require(probe.nonplayerReported,
                                "The native non-player lava force was not independently observed");
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
                        player.teleportTo((ServerLevel) player.level(), 576, 80, 69,
                                java.util.Set.of(), 180, 0, false);
                        player.removeEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
                        player.clearFire();
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
        if (name.equals("narrow_edge_water")) {
            for (int x = 505; x <= 519; x++)
                for (int z = 64; z <= 71; z++)
                    source.setBlock(new BlockPos(x, 80, z), Blocks.SOUL_SAND.defaultBlockState(), 3);
            // The raised feet touch the top of water outside the opening's right edge.
            // Solid neighboring cells keep this genuine source contact stationary.
            source.setBlock(new BlockPos(513, 80, 62), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 81; y <= 90; y++)
                source.setBlock(new BlockPos(513, y, 63), Blocks.AIR.defaultBlockState(), 3);
            source.setBlock(new BlockPos(513, 80, 63), Blocks.WATER.defaultBlockState(), 3);
        }
        if (name.equals("destination_lava") || name.equals("nonplayer_lava")) {
            // A permanent source strip feeds a genuine horizontal Nether lava flow.
            // Its positive-X current is tangential to the portal plane, so it cannot
            // substitute for the later ordinary movement key used to cross it.
            // A single layer keeps the stream horizontal instead of creating a
            // falling-fluid column whose native downward current changes direction.
            for (int z = 57; z <= 63; z++)
                destination.setBlock(new BlockPos(573, 80, z), Blocks.LAVA.defaultBlockState(), 3);
        } else if (destinationCase(name)) {
            if (rotated) fill(destination, 569, 575, 57, 71, Blocks.WATER);
            else fill(destination, 569, 583, 57, 63, Blocks.WATER);
        }
        player.setGameMode(GameType.CREATIVE);
        var proto = new ProtoPortal();
        double width = name.equals("narrow_edge_water") ? 2 : name.equals("nonplayer_lava") ? 12
                : scaled ? 4 : 8;
        double height = name.equals("narrow_edge_water") || scaled ? 4 : 12;
        double sourceY = name.equals("narrow_edge_water") ? 82.875 : scaled ? 82 : 86;
        double destinationY = name.equals("narrow_edge_water") ? 82.875 : scaled ? 84 : 86;
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
        Vec3 initial = start(name);
        player.teleportTo(source, initial.x, initial.y, initial.z, java.util.Set.of(), 180, 0, false);
        if (name.equals("destination_lava"))
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 2000, 0, false, false));
        qouteall.imm_ptl.core.collision.PortalCollisionHandler.updateCollidingPortalAfterTeleportation(
                player, player.getEyePosition(), player.getEyePosition(), 1);
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

    private static boolean projectedWaterEye(ServerPlayer player) {
        Portal portal = faces.stream().filter(candidate -> candidate.level() == player.level()
                        && candidate.getNormal().z > .99)
                .findFirst().orElseThrow(() -> new AssertionError("The water-eye oracle lost its source face"));
        Vec3 eye = player.getEyePosition();
        var geometry = portal.getThisSideState();
        Vec3 local = geometry.transformGlobalToLocal(eye);
        // The physical eye can enter the destination half before the rendered
        // crossing packet transfers its authoritative entity anchor.
        boolean projected = local.z < 0 && Math.abs(local.x) < geometry.width() / 2
                && Math.abs(local.y) < geometry.height() / 2;
        var world = projected ? portal.getDestinationWorld() : player.level();
        require(world != null, "The water-eye oracle lost its physical sample world");
        Vec3 sample = projected ? portal.transformPoint(eye) : eye;
        BlockPos position = BlockPos.containing(sample);
        var fluid = world.getFluidState(position);
        return fluid.is(FluidTags.WATER) && sample.y >= position.getY()
                && sample.y <= position.getY() + fluid.getHeight(world, position);
    }

    private static void observe(ServerPlayer player) {
        int elapsed = player.level().getServer().getTickCount() - probe.started;
        require(elapsed < 600, "The server native fluid fixture stalled: " + probe.name);
        if (destinationCase(probe.name)) {
            if (probe.nativeHistory.size() == 32) probe.nativeHistory.removeFirst();
            probe.nativeHistory.addLast("elapsed=" + elapsed + " " + nativeState(player));
        }
        if (elapsed < ((probe.name.equals("destination_lava") || probe.name.equals("nonplayer_lava"))
                ? 220 : 25)) return;
        if (probe.name.equals("nonplayer_lava")) {
            require(player.level().dimension() == Level.OVERWORLD && !physicalFluid(player)
                            && !player.isInWater() && !player.isInLava()
                            && player.getAirSupply() == player.getMaxAirSupply()
                            && player.getHealth() == player.getMaxHealth(),
                    "The stationary non-player observer entered fluid or took damage");
            observeItems(player);
            probe.sourceSamples++;
            return;
        }
        if (probe.name.equals("narrow_edge_water")) {
            require(player.level().dimension() == Level.OVERWORLD && physicalFluid(player)
                            && player.isInWater() && !player.isInLava()
                            && player.getFluidHeight(FluidTags.WATER) > 0
                            && !player.isEyeInFluid(FluidTags.WATER),
                    "Real source water outside the finite aperture lost native contact");
            require(Math.abs(player.getY() - 80.875) < .025
                            && player.getAirSupply() == player.getMaxAirSupply()
                            && player.getHealth() == player.getMaxHealth(),
                    "The shallow edge-water observer lost its raised floor or dry eye");
            if (probe.settled == null) probe.settled = player.position();
            require(player.position().distanceTo(probe.settled) < .025,
                    "The enclosed edge-water pocket moved the stationary observer");
            probe.sourceSamples++;
            return;
        }
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
        if (probe.name.equals("destination_lava")) {
            require(player.isInLava() && !player.isInWater()
                            && player.getFluidHeight(FluidTags.LAVA) > 0
                            && player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE),
                    "Visible destination lava lost its native fluid state or observer protection");
            require(player.getHealth() == player.getMaxHealth(), "The protected lava observer took damage");
            if (player.level().dimension() == Level.OVERWORLD) {
                require(!physicalFluid(player), "The projected lava probe no longer has a dry source");
                probe.sourceSamples++;
            } else {
                require(player.level().dimension() == Level.NETHER,
                        "The lava observer entered an unrelated dimension");
                probe.destinationSamples++;
            }
            return;
        }
        if (!(player.isInWater() && !player.isInLava() && player.getFluidHeight(FluidTags.WATER) > 0))
            throw new AssertionError("A physically visible water body lost its native interaction: "
                    + probe.name + " " + nativeState(player) + " " + contactState(player)
                    + " history=" + probe.nativeHistory);
        if (player.level().dimension() == Level.NETHER) {
            require(destinationCase(probe.name) && player.isEyeInFluid(FluidTags.WATER),
                    "The physical destination water did not submerge the actual eye");
            probe.destinationSamples++;
        } else {
            require(player.level().dimension() == Level.OVERWORLD, "The fluid player entered an unrelated world");
            if (probe.name.equals("visible_water"))
                require(player.isEyeInFluid(FluidTags.WATER), "Visible source water did not submerge the native eye");
            else {
                boolean expectedEye = projectedWaterEye(player);
                if (physicalFluid(player) || player.isEyeInFluid(FluidTags.WATER) != expectedEye)
                    throw new AssertionError("The projected-water probe lost its dry source body or geometric eye state: "
                            + probe.name + " expectedEye=" + expectedEye + " " + nativeState(player)
                            + " " + contactState(player) + " history=" + probe.nativeHistory);
            }
            probe.sourceSamples++;
        }
    }

    private static void observeItems(ServerPlayer player) {
        var source = player.level().getServer().overworld();
        var destination = player.level().getServer().getLevel(Level.NETHER);
        require(destination != null, "The non-player lava witness lost its destination");
        if (probe.positiveItem == null && !probe.positiveForce) {
            var positivePos = new BlockPos(576, 80, 63);
            var shallowPos = new BlockPos(580, 80, 63);
            var positiveFluid = destination.getFluidState(positivePos);
            var shallowFluid = destination.getFluidState(shallowPos);
            require(positiveFluid.is(FluidTags.LAVA) && shallowFluid.is(FluidTags.LAVA)
                            && positiveFluid.getFlow(destination, positivePos).x > .99
                            && shallowFluid.getFlow(destination, shallowPos).x > .99,
                    "The non-player witnesses do not have a genuine horizontal lava current");
            double shallowY = 80 + shallowFluid.getHeight(destination, shallowPos) - .002;
            probe.positiveItem = item(source, new Vec3(512, 80.2, 64.05));
            probe.shallowItem = item(source, new Vec3(516.5, shallowY, 64.05));
        }
        if (!probe.positiveForce) {
            var force = PortalFluidInteraction.lastForce(probe.positiveItem, FluidTags.LAVA);
            require(!probe.positiveItem.isRemoved() && (force != null || probe.positiveItem.tickCount < 20),
                    "The real positive lava witness never produced a native force sample: age="
                            + probe.positiveItem.tickCount + " position=" + probe.positiveItem.position()
                            + " height=" + probe.positiveItem.getFluidHeight(FluidTags.LAVA));
            if (probe.positiveItem.tickCount > 0 && force != null) {
                require(probe.positiveItem.level() == source && force.sourceCells() == 0
                                && force.destinationCells() > 0
                                && probe.positiveItem.getFluidHeight(FluidTags.LAVA) > 0
                                && force.physical().lengthSqr() == 0
                                && Math.abs(force.applied().x - .007) < 1.0E-6
                                && Math.abs(force.applied().y) < 1.0E-7
                                && Math.abs(force.applied().z) < 1.0E-7,
                        "A real non-player tick lost the destination's native lava force: " + force);
                probe.positiveForce = true;
                probe.positiveItem.discard();
            }
        }
        if (!probe.shallowForce) {
            var force = PortalFluidInteraction.lastForce(probe.shallowItem, FluidTags.LAVA);
            require(!probe.shallowItem.isRemoved() && (force != null || probe.shallowItem.tickCount < 20),
                    "The real shallow lava witness never produced a native force sample: age="
                            + probe.shallowItem.tickCount + " position=" + probe.shallowItem.position()
                            + " height=" + probe.shallowItem.getFluidHeight(FluidTags.LAVA));
            if (probe.shallowItem.tickCount > 0 && force != null) {
                double height = probe.shallowItem.getFluidHeight(FluidTags.LAVA);
                require(probe.shallowItem.level() == source && force.sourceCells() == 0
                                && force.destinationCells() > 0 && height > 0 && height < .004
                                && force.physical().lengthSqr() == 0 && force.remote().lengthSqr() == 0
                                && force.applied().lengthSqr() == 0,
                        "Shallow native current eligibility changed across the portal: height="
                                + height + " force=" + force);
                probe.shallowForce = true;
                probe.shallowItem.discard();
            }
        }
        if (probe.positiveForce && probe.shallowForce && !probe.nonplayerReported) {
            PortalFluidForceContract.verify(player, faces.stream()
                    .filter(portal -> portal.level() == source && portal.getNormal().z > .99)
                    .findFirst().orElseThrow(() -> new AssertionError("The native force contract lost its source portal")));
            probe.nonplayerReported = true;
            player.sendSystemMessage(Component.literal("PORTAL_FLUID_NONPLAYER_NATIVE_OK"));
            System.out.println("PORTAL_FLUID_NONPLAYER_NATIVE_OK strength=0.007 shallowForce=0");
        }
    }

    private static ItemEntity item(ServerLevel world, Vec3 position) {
        var entity = new ItemEntity(world, position.x, position.y, position.z,
                new ItemStack(Items.NETHERITE_SCRAP));
        entity.setDeltaMovement(new Vec3(.01, 0, 0));
        entity.setNoGravity(true);
        entity.setPickUpDelay(32767);
        entity.setUnlimitedLifetime();
        require(world.addFreshEntity(entity), "Could not create the real non-player fluid witness");
        qouteall.imm_ptl.core.collision.PortalCollisionHandler.updateCollidingPortalAfterTeleportation(
                entity, entity.getEyePosition(), entity.getEyePosition(), 1);
        return entity;
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
