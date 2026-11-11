package validation;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.ProtoPortal;
import qouteall.imm_ptl.peripheral.wand.ProtoPortalSide;

/** Authoritative native movement, held-use and projectile outcomes in isolated wand rooms. */
public final class PortalPoseUseFixture {
    static final String[] CASES = {"sprint", "crouch", "fly", "fall", "swim", "bow", "food"};
    static final double SOURCE_X = 320, DESTINATION_X = 384, PLANE_Z = 64;
    private static final java.util.List<Portal> faces = new java.util.ArrayList<>();
    private static Probe probe;

    private static final class Probe {
        final String name;
        final java.util.UUID player;
        final ServerLevel destination;
        final int started;
        final java.util.ArrayDeque<String> useHistory = new java.util.ArrayDeque<>();
        final java.util.ArrayDeque<String> movementHistory = new java.util.ArrayDeque<>();
        Vec3 previous;
        int sourcePoseTicks, destinationPoseTicks, sourceUseTicks, destinationUseTicks;
        int lastUseTicks = -1, maximumUseTicks, clientTicks;
        boolean crossed, verified, useStarted, projectileHit, destinationUseAcknowledged;

        Probe(String name, ServerPlayer player, ServerLevel destination) {
            this.name = name;
            this.player = player.getUUID();
            this.destination = destination;
            started = player.level().getServer().getTickCount();
        }
    }

    private PortalPoseUseFixture() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static boolean heldUse(String name) {
        return name.equals("bow") || name.equals("food");
    }

    static Vec3 start(String name) {
        return new Vec3(SOURCE_X, name.equals("fall") ? 85 : name.equals("fly") ? 81 : 80,
                PLANE_Z + switch (name) {
                    case "sprint", "swim" -> 4;
                    case "crouch" -> 1;
                    case "bow" -> .45;
                    case "food" -> .2;
                    default -> 2;
                });
    }

    private static boolean allowed(net.minecraft.commands.CommandSourceStack source) {
        return source.getPlayer() != null
                && source.getPlayer().getGameProfile().name().equals("PortalTest");
    }

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("portal_pose_use_setup")
                    .requires(PortalPoseUseFixture::allowed)
                    .then(Commands.argument("case", StringArgumentType.word()).executes(context -> {
                        String name = StringArgumentType.getString(context, "case");
                        require(java.util.Arrays.asList(CASES).contains(name), "Unknown pose/use case " + name);
                        setup(context.getSource().getPlayerOrException(), name, scenePortals);
                        return 1;
                    })));
            dispatcher.register(Commands.literal("portal_pose_use_verify")
                    .requires(PortalPoseUseFixture::allowed)
                    .then(Commands.argument("case", StringArgumentType.word()).executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        verify(player, StringArgumentType.getString(context, "case"));
                        player.sendSystemMessage(Component.literal("PORTAL_POSE_USE_VERIFIED " + probe.name));
                        return 1;
                    })));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (probe == null || probe.verified) return;
            var player = server.getPlayerList().getPlayer(probe.player);
            if (player != null) observe(player);
        });
    }

    private static void setup(ServerPlayer player, String name,
            java.util.Set<java.util.UUID> scenePortals) {
        require(probe == null || probe.verified, "A pose/use case was replaced before verification");
        var server = player.level().getServer();
        var source = server.overworld();
        var destination = heldUse(name) ? server.getLevel(Level.NETHER) : source;
        require(destination != null, "The held-use destination is missing");
        for (var portal : faces) {
            scenePortals.remove(portal.getUUID());
            portal.discard();
        }
        faces.clear();
        room(source, (int) SOURCE_X, name.equals("swim"));
        room(destination, (int) DESTINATION_X, name.equals("swim"));
        player.stopUsingItem();
        player.setGameMode(GameType.CREATIVE);
        var existing = new java.util.HashSet<java.util.UUID>();
        var worlds = source == destination ? java.util.List.of(source) : java.util.List.of(source, destination);
        for (var world : worlds)
            for (var portal : portals(world)) existing.add(portal.getUUID());
        var proto = new ProtoPortal();
        proto.firstSide = new ProtoPortalSide(source.dimension(), new Vec3(SOURCE_X - 2, 80, PLANE_Z));
        proto.firstSide.rightBottom = new Vec3(SOURCE_X + 2, 80, PLANE_Z);
        proto.firstSide.leftTop = new Vec3(SOURCE_X - 2, 90, PLANE_Z);
        proto.secondSide = new ProtoPortalSide(destination.dimension(), new Vec3(DESTINATION_X + 2, 80, PLANE_Z));
        proto.secondSide.rightBottom = new Vec3(DESTINATION_X - 2, 80, PLANE_Z);
        proto.secondSide.leftTop = new Vec3(DESTINATION_X + 2, 90, PLANE_Z);
        PortalWandInteraction.RemoteCallables.finishPortalCreation(player, proto);
        for (var world : worlds)
            for (var portal : portals(world))
                if (!existing.contains(portal.getUUID())) {
                    faces.add(portal);
                    scenePortals.add(portal.getUUID());
                }
        require(faces.size() == 4, "The pose/use fixture did not create four wand faces");
        for (int x = -3; x <= 3; x++)
            for (int y = 80; y <= 89; y++) {
                source.setBlock(new BlockPos((int) SOURCE_X + x, y, 63), Blocks.STONE.defaultBlockState(), 3);
                destination.setBlock(new BlockPos((int) DESTINATION_X + x, y, 64), Blocks.STONE.defaultBlockState(), 3);
            }
        if (name.equals("fall"))
            for (int x = (int) SOURCE_X - 2; x <= SOURCE_X + 2; x++)
                for (int z = 65; z <= 69; z++)
                    source.setBlock(new BlockPos(x, 84, z), Blocks.STONE.defaultBlockState(), 3);
        if (name.equals("bow"))
            for (int x = (int) DESTINATION_X - 3; x <= DESTINATION_X + 3; x++)
                for (int y = 80; y <= 89; y++)
                    destination.setBlock(new BlockPos(x, y, 55), Blocks.STONE.defaultBlockState(), 3);
        player.setGameMode(name.equals("fly") ? GameType.CREATIVE : GameType.SURVIVAL);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        // One apple must fill hunger, so holding the real key cannot immediately
        // start eating the remaining apple after the observed native completion.
        player.getFoodData().setFoodLevel(name.equals("food") ? 17 : 20);
        player.getFoodData().setSaturation(0);
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.setItemSlot(EquipmentSlot.MAINHAND, name.equals("bow") ? new ItemStack(Items.BOW)
                : name.equals("food") ? new ItemStack(Items.APPLE, 2) : ItemStack.EMPTY);
        if (name.equals("bow")) player.getInventory().setItem(1, new ItemStack(Items.ARROW, 8));
        player.getAbilities().flying = name.equals("fly");
        player.onUpdateAbilities();
        var position = start(name);
        player.teleportTo(source, position.x, position.y, position.z, java.util.Set.of(), 180, 0, false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.inventoryMenu.broadcastChanges();
        probe = new Probe(name, player, destination);
        player.sendSystemMessage(Component.literal("PORTAL_POSE_USE_SETUP " + name));
        System.out.println("PORTAL_POSE_USE_FIXTURE_READY case=" + name + " faces=4 crossDimension=" + heldUse(name));
    }

    private static void room(ServerLevel world, int center, boolean water) {
        for (int x = (center - 6) >> 4; x <= (center + 6) >> 4; x++)
            for (int z = 3; z <= 4; z++) {
                world.setChunkForced(x, z, true);
                world.getChunk(x, z);
            }
        for (int x = center - 6; x <= center + 6; x++)
            for (int z = 54; z <= 74; z++)
                for (int y = 79; y <= 91; y++) {
                    boolean shell = x == center - 6 || x == center + 6 || z == 54 || z == 74 || y == 79 || y == 91;
                    var block = y == 91 ? Blocks.SEA_LANTERN
                            : shell ? Blocks.STONE : water && y <= 83 ? Blocks.WATER : Blocks.AIR;
                    world.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
                }
        // The remote room can receive natural spawns before the player arrives.
        // Keep native mob pushes and projectile knockback out of movement observations.
        var bounds = new AABB(center - 7, 78, 53, center + 7, 93, 75);
        var stale = world.getEntities((net.minecraft.world.entity.Entity) null, bounds,
                entity -> !(entity instanceof ServerPlayer) && !(entity instanceof Portal));
        for (var entity : stale) entity.discard();
        System.out.println("PORTAL_POSE_USE_ROOM_ISOLATED dimension=" + world.dimension().identifier()
                + " centerX=" + center + " removedEntities=" + stale.size());
    }

    private static boolean destination(ServerPlayer player) {
        return heldUse(probe.name) ? player.level() == probe.destination : player.getX() > 352;
    }

    private static boolean correctPose(ServerPlayer player, double verticalStep) {
        return switch (probe.name) {
            case "sprint" -> player.isSprinting();
            case "crouch" -> player.isCrouching() && player.getPose() == Pose.CROUCHING;
            case "fly" -> player.getAbilities().flying;
            case "fall" -> !player.onGround() && verticalStep < -.02;
            case "swim" -> player.isSwimming() && player.getPose() == Pose.SWIMMING && player.isInWater();
            default -> player.isUsingItem();
        };
    }

    /** Checks every native client simulation boundary after its movement packets. */
    public static void observeClientTick(ServerPlayer player) {
        if (probe == null || probe.verified || !probe.player.equals(player.getUUID())) return;
        int tick = player.level().getServer().getTickCount();
        probe.clientTicks++;
        boolean destination = destination(player);
        Vec3 position = destination ? player.position().add(-64, 0, 0) : player.position();
        double vertical = probe.previous == null ? 0 : position.y - probe.previous.y;
        if (probe.movementHistory.size() == 16) probe.movementHistory.removeFirst();
        probe.movementHistory.addLast("clientTick=" + probe.clientTicks + " serverTick=" + tick
                + " feet=" + position + " destination=" + destination
                + " pose=" + player.getPose() + " grounded=" + player.onGround()
                + " sprinting=" + player.isSprinting() + " swimming=" + player.isSwimming()
                + " flying=" + player.getAbilities().flying + " using=" + player.isUsingItem());
        // One server tick can process several ordinary client movement ticks.
        // Sampling ClientTickEnd retains the strict per-tick bounds in both
        // dedicated and integrated servers without treating packet batches as jumps.
        if (probe.previous != null) {
            var step = position.subtract(probe.previous);
            require(Math.hypot(step.x, step.z) < .9 && Math.abs(step.y) < 2.5,
                    "Portal traversal added authoritative movement: " + probe.name + " " + step
                            + " history=" + probe.movementHistory);
        }
        boolean pose = correctPose(player, vertical);
        if (pose) {
            if (destination) probe.destinationPoseTicks++; else probe.sourcePoseTicks++;
        }
        if (destination && !probe.crossed) {
            require(pose && probe.sourcePoseTicks >= 2,
                    "The server crossing did not preserve its active pose/use: " + probe.name
                            + " movementHistory=" + probe.movementHistory
                            + " useHistory=" + probe.useHistory);
            probe.crossed = true;
        }
        require(!probe.crossed || destination, "A pose/use crossing was pushed back into its source room");
        probe.previous = position;
    }

    private static void observe(ServerPlayer player) {
        int tick = player.level().getServer().getTickCount();
        require(tick - probe.started < 500, "The server pose/use fixture stalled: " + probe.name);
        boolean destination = destination(player);
        if (heldUse(probe.name)) {
            if (probe.useHistory.size() == 16) probe.useHistory.removeFirst();
            probe.useHistory.addLast("tick=" + tick + " playerTick=" + player.tickCount
                    + " dimension=" + player.level().dimension().identifier()
                    + " feet=" + player.position() + " using=" + player.isUsingItem()
                    + " hand=" + player.getUsedItemHand() + " used=" + player.getTicksUsingItem()
                    + " remaining=" + player.getUseItemRemainingTicks()
                    + " active=" + player.getUseItem()
                    + " held=" + player.getItemInHand(player.getUsedItemHand())
                    + " crossed=" + probe.crossed + " destinationUse=" + probe.destinationUseTicks);
        }
        if (heldUse(probe.name)) {
            if (player.isUsingItem()) {
                int used = player.getTicksUsingItem();
                require(probe.lastUseTicks < 0 || used >= probe.lastUseTicks,
                        "The authoritative held item restarted its use counter across the portal: "
                                + probe.name + " history=" + probe.useHistory);
                probe.useStarted = true;
                probe.lastUseTicks = used;
                probe.maximumUseTicks = Math.max(probe.maximumUseTicks, used);
                if (destination) probe.destinationUseTicks++; else probe.sourceUseTicks++;
                if (probe.name.equals("bow") && probe.destinationUseTicks >= 3
                        && !probe.destinationUseAcknowledged) {
                    probe.destinationUseAcknowledged = true;
                    player.sendSystemMessage(Component.literal("PORTAL_POSE_USE_DESTINATION_HELD bow"));
                    System.out.println("PORTAL_POSE_USE_DESTINATION_HELD case=bow sourceUse="
                            + probe.sourceUseTicks + " destinationUse=" + probe.destinationUseTicks
                            + " used=" + used);
                }
            } else if (probe.useStarted) {
                require(probe.crossed && probe.destinationUseTicks >= 2,
                        "The held item stopped during the authoritative crossing: " + probe.name
                                + " sourceUse=" + probe.sourceUseTicks
                                + " destinationUse=" + probe.destinationUseTicks
                                + " crossed=" + probe.crossed + " history=" + probe.useHistory);
            }
            if (probe.name.equals("bow") && probe.crossed)
                for (var entity : probe.destination.getAllEntities())
                    if (entity instanceof AbstractArrow arrow && arrow.getOwner() != null
                            && arrow.getOwner().getUUID().equals(player.getUUID()) && arrow.shakeTime > 0) {
                        require(arrow.getX() > 380 && arrow.getX() < 388
                                        && arrow.getZ() > 55.9 && arrow.getZ() < 56.2,
                                "The released bow hit its source wall or missed the destination target");
                        probe.projectileHit = true;
                    }
        }
    }

    private static void verify(ServerPlayer player, String name) {
        require(probe != null && probe.name.equals(name) && probe.player.equals(player.getUUID()),
                "The pose/use verification referred to another case");
        require(probe.crossed && probe.sourcePoseTicks >= 2 && probe.destinationPoseTicks >= 2,
                "The requested pose/use was not observed on both sides: " + name);
        if (heldUse(name)) {
            require(probe.sourceUseTicks >= 3 && probe.destinationUseTicks >= 3,
                    "Native item use did not span both sides of the portal");
            if (name.equals("bow"))
                require(probe.projectileHit && probe.maximumUseTicks >= 20
                                && player.getMainHandItem().getDamageValue() > 0
                                && player.getInventory().countItem(Items.ARROW) == 7,
                        "Native bow release did not consume one arrow and hit the destination target");
            else require(player.getMainHandItem().is(Items.APPLE)
                            && player.getMainHandItem().getCount() == 1
                            && player.getFoodData().getFoodLevel() == 20,
                    "The held food did not complete native consumption after crossing");
        }
        var source = player.level().getServer().overworld();
        for (int x = -3; x <= 3; x++)
            for (int y = 80; y <= 89; y++)
                require(source.getBlockState(new BlockPos((int) SOURCE_X + x, y, 63)).is(Blocks.STONE)
                                && probe.destination.getBlockState(new BlockPos((int) DESTINATION_X + x, y, 64)).is(Blocks.STONE),
                        "A pose/use traversal removed a backing wall");
        probe.verified = true;
        System.out.println("PORTAL_POSE_USE_SERVER_OK case=" + name + " source=" + probe.sourcePoseTicks
                + " destination=" + probe.destinationPoseTicks + " heldTicks=" + probe.maximumUseTicks
                + " projectileHit=" + probe.projectileHit + " movementClock=client_tick_end"
                + " clientTicks=" + probe.clientTicks);
    }

    private static java.util.List<Portal> portals(ServerLevel world) {
        var storage = ((IELevelEntityGetterAdapter) ((IEWorld) world).portal_getEntityLookup()).getCache();
        var result = new java.util.ArrayList<Portal>();
        for (long chunk : storage.getAllChunksWithExistingSections())
            storage.getExistingSectionsInChunk(chunk).flatMap(section -> section.getEntities())
                    .forEach(entity -> { if (entity instanceof Portal portal) result.add(portal); });
        return result;
    }
}
