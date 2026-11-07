package validation;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.ProtoPortal;
import qouteall.imm_ptl.peripheral.wand.ProtoPortalSide;

/** Server-side outcomes for real key input through a translated native Fabric portal. */
final class PortalNativeInteractionFixture {
    static final BlockPos TARGET = new BlockPos(128, 71, 62);
    static final BlockPos SOURCE_WITNESS = new BlockPos(64, 71, 62);
    private static boolean prepared;
    private static String phase;
    private static ArmorStand stand;
    private static net.minecraft.world.entity.npc.villager.Villager trader;
    private static net.minecraft.world.entity.vehicle.boat.Boat boat;
    private static net.minecraft.world.entity.monster.zombie.Zombie reflectedTarget;
    private static ServerPlayer tradeClosurePlayer;
    private static int tradeClosureTicks;
    private static ServerPlayer miningCompletionPlayer;
    private static int miningCompletionTicks;
    private static boolean buttonActivated;
    private static final java.util.Set<String> VERIFIED = new java.util.HashSet<>();

    private PortalNativeInteractionFixture() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean allowed(net.minecraft.commands.CommandSourceStack source) {
        return source.getPlayer() != null
                && source.getPlayer().getGameProfile().name().equals("InteractionTest");
    }

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("portal_native_interaction_setup")
                    .requires(PortalNativeInteractionFixture::allowed)
                    .then(Commands.argument("phase", StringArgumentType.word())
                            .executes(context -> {
                                var player = context.getSource().getPlayerOrException();
                                prepare(player, scenePortals);
                                setPhase(player, StringArgumentType.getString(context, "phase"));
                                return 1;
                            })));
            dispatcher.register(Commands.literal("portal_native_interaction_verify")
                    .requires(PortalNativeInteractionFixture::allowed)
                    .then(Commands.argument("phase", StringArgumentType.word())
                            .executes(context -> {
                                var player = context.getSource().getPlayerOrException();
                                verify(player, StringArgumentType.getString(context, "phase"));
                                return 1;
                            })));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (miningCompletionPlayer != null) {
                var player = miningCompletionPlayer;
                if (server.getLevel(Level.NETHER).getBlockState(TARGET).isAir()) {
                    miningCompletionPlayer = null;
                    finishVerification(player, "mining_cross");
                    System.out.println("PORTAL_NATIVE_INTERACTION_MINING_SERVER_COMPLETE ticks="
                            + miningCompletionTicks);
                } else if (++miningCompletionTicks >= 40) {
                    throw new AssertionError("Holding attack while crossing did not finish the destination block: "
                            + miningState(player));
                }
            }
            if (tradeClosurePlayer != null && ++tradeClosureTicks >= 6) {
                var player = tradeClosurePlayer;
                tradeClosurePlayer = null;
                require(player.containerMenu == player.inventoryMenu,
                        "A remote trade remained open after a real source obstruction blocked its path");
                finishVerification(player, "trade");
                System.out.println("PORTAL_NATIVE_INTERACTION_TRADE_CLOSED_ON_OBSTRUCTION");
            }
            if (!prepared || phase == null || !phase.equals("button")) return;
            var world = server.getLevel(Level.NETHER);
            var state = world.getBlockState(TARGET);
            if (state.is(Blocks.OAK_BUTTON) && state.getValue(BlockStateProperties.POWERED))
                buttonActivated = true;
        });
    }

    private static java.util.List<Portal> portals(ServerLevel world) {
        var storage = ((IELevelEntityGetterAdapter) ((IEWorld) world).portal_getEntityLookup())
                .getCache();
        var result = new java.util.ArrayList<Portal>();
        for (long chunk : storage.getAllChunksWithExistingSections())
            storage.getExistingSectionsInChunk(chunk).flatMap(section -> section.getEntities())
                    .forEach(entity -> {
                        if (entity instanceof Portal portal) result.add(portal);
                    });
        return result;
    }

    private static void room(ServerLevel world, int centerX) {
        for (int x = centerX - 5; x <= centerX + 5; x++)
            for (int z = 54; z <= 69; z++)
                for (int y = 69; y <= 77; y++) {
                    boolean shell = x == centerX - 5 || x == centerX + 5 || z == 54 || z == 69
                            || y == 69 || y == 77;
                    world.setBlock(new BlockPos(x, y, z),
                            (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
                }
        for (int x : new int[] {centerX - 2, centerX + 2})
            for (int z : new int[] {58, 66})
                world.setBlock(new BlockPos(x, 76, z), Blocks.SEA_LANTERN.defaultBlockState(), 3);
    }

    private static void prepare(ServerPlayer player, java.util.Set<java.util.UUID> scenePortals) {
        if (prepared) return;
        var server = player.level().getServer();
        var source = server.overworld();
        var destination = server.getLevel(Level.NETHER);
        require(destination != null, "The native interaction fixture has no destination");
        for (var world : new ServerLevel[] {source, destination}) {
            int first = world == source ? 3 : 7;
            for (int x = first; x <= first + 1; x++)
                for (int z = 3; z <= 4; z++) {
                    world.setChunkForced(x, z, true);
                    world.getChunk(x, z);
                    world.waitForEntities(new ChunkPos(x, z), 0);
                }
            room(world, world == source ? 64 : 128);
        }
        for (var world : new ServerLevel[] {source, destination})
            for (var portal : portals(world))
                if (Math.abs(portal.getZ() - 64) < .01
                        && Math.abs(portal.getX() - (world == source ? 64.5 : 128.5)) < .01)
                    portal.discard();
        var before = new java.util.HashSet<java.util.UUID>();
        for (var world : new ServerLevel[] {source, destination})
            for (var portal : portals(world)) before.add(portal.getUUID());
        var proto = new ProtoPortal();
        proto.firstSide = new ProtoPortalSide(Level.OVERWORLD, new Vec3(62.5, 70, 64));
        proto.firstSide.rightBottom = new Vec3(66.5, 70, 64);
        proto.firstSide.leftTop = new Vec3(62.5, 74, 64);
        proto.secondSide = new ProtoPortalSide(Level.NETHER, new Vec3(130.5, 70, 64));
        proto.secondSide.rightBottom = new Vec3(126.5, 70, 64);
        proto.secondSide.leftTop = new Vec3(130.5, 74, 64);
        PortalWandInteraction.RemoteCallables.finishPortalCreation(player, proto);
        var created = new java.util.ArrayList<Portal>();
        for (var world : new ServerLevel[] {source, destination})
            for (var portal : portals(world))
                if (!before.contains(portal.getUUID())) created.add(portal);
        require(created.size() == 4, "The native interaction wand did not create four faces");
        for (var portal : created) scenePortals.add(portal.getUUID());
        var forward = created.stream().filter(portal -> portal.level() == source
                && portal.getNormal().z > .99).findFirst().orElseThrow();
        require(forward.transformPoint(new Vec3(64.5, 72, 64))
                        .distanceTo(new Vec3(128.5, 72, 64)) < 1e-6,
                "The interaction fixture did not translate into a distinct destination");
        for (int x = 61; x <= 68; x++)
            for (int y = 70; y <= 74; y++) {
                source.setBlock(new BlockPos(x, y, 63), Blocks.STONE.defaultBlockState(), 3);
                destination.setBlock(new BlockPos(x + 64, y, 64), Blocks.STONE.defaultBlockState(), 3);
            }
        prepared = true;
        System.out.println("PORTAL_NATIVE_INTERACTION_FIXTURE_READY faces=4 translation=64");
    }

    private static void setPhase(ServerPlayer player, String next) {
        var server = player.level().getServer();
        var source = server.overworld();
        var destination = server.getLevel(Level.NETHER);
        player.stopRiding();
        if (stand != null) stand.discard();
        if (trader != null) trader.discard();
        if (boat != null) boat.discard();
        if (reflectedTarget != null) reflectedTarget.discard();
        stand = null;
        trader = null;
        boat = null;
        reflectedTarget = null;
        player.setHealth(player.getMaxHealth());
        destination.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(123, 69, 54, 134, 78, 70))
                .forEach(net.minecraft.world.entity.Entity::discard);
        buttonActivated = false;
        for (int x = 125; x <= 131; x++)
            for (int z = 56; z <= 63; z++)
                for (int y = 70; y <= 74; y++)
                    destination.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        source.setBlock(new BlockPos(64, 71, 65), Blocks.AIR.defaultBlockState(), 3);
        source.setBlock(SOURCE_WITNESS, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.setGameMode(next.equals("mining_cross") || next.equals("projected_use")
                || next.equals("reflection")
                ? GameType.SURVIVAL : GameType.CREATIVE);
        player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE)
                .setBaseValue(next.equals("projected_use") ? .32 : 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        switch (next) {
            case "chest" -> destination.setBlock(TARGET, Blocks.CHEST.defaultBlockState(), 3);
            case "button" -> {
                destination.setBlock(TARGET.below(), Blocks.STONE.defaultBlockState(), 3);
                destination.setBlock(TARGET, Blocks.OAK_BUTTON.defaultBlockState()
                        .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3);
            }
            case "place", "break", "source_occluder", "destination_occluder", "outside" -> {
                destination.setBlock(TARGET, Blocks.STONE.defaultBlockState(), 3);
                if (next.equals("place"))
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_BLOCK, 4));
                if (next.equals("break"))
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                if (next.equals("source_occluder"))
                    source.setBlock(new BlockPos(64, 71, 65), Blocks.NETHERITE_BLOCK.defaultBlockState(), 3);
                if (next.equals("destination_occluder"))
                    destination.setBlock(TARGET.south(), Blocks.NETHERITE_BLOCK.defaultBlockState(), 3);
            }
            case "out_of_reach" -> destination.setBlock(new BlockPos(128, 71, 56),
                    Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            case "entity_use", "entity_attack" -> {
                stand = new ArmorStand(destination, 128.5, 70, 62.5);
                stand.setNoGravity(true);
                stand.setNoBasePlate(true);
                destination.addFreshEntity(stand);
                if (next.equals("entity_use"))
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HELMET));
                else player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            }
            case "mining_cross" -> {
                destination.setBlock(TARGET, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            }
            case "projected_use", "projected_attack" -> {
                stand = new ArmorStand(source, 64.5, 70, 64.1);
                stand.setNoGravity(true);
                stand.setNoBasePlate(true);
                source.addFreshEntity(stand);
                var portal = portals(source).stream().filter(candidate -> candidate.getNormal().z > .99
                        && candidate.getOriginPos().distanceTo(new Vec3(64.5, 72, 64)) < .01)
                        .findFirst().orElseThrow();
                ((IEEntity) stand).ip_notifyCollidingWithPortal(portal);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(next.equals("projected_use")
                        ? Items.DIAMOND_HELMET : Items.DIAMOND_SWORD));
            }
            case "trade" -> {
                trader = net.minecraft.world.entity.EntityTypes.VILLAGER.create(destination,
                        net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                require(trader != null, "The native trade fixture could not create its villager");
                trader.setPos(128.5, 70, 62.5);
                trader.setNoAi(true);
                var offers = new net.minecraft.world.item.trading.MerchantOffers();
                offers.add(new net.minecraft.world.item.trading.MerchantOffer(
                        new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 1),
                        new ItemStack(Items.APPLE), 10, 1, .05f));
                trader.overrideOffers(offers);
                destination.addFreshEntity(trader);
            }
            case "mount" -> {
                boat = net.minecraft.world.entity.EntityTypes.OAK_BOAT.create(destination,
                        net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                require(boat != null, "The native mount fixture could not create its boat");
                boat.setPos(128.5, 70, 62.5);
                destination.addFreshEntity(boat);
            }
            case "reflection" -> {
                reflectedTarget = new net.minecraft.world.entity.monster.zombie.Zombie(destination);
                reflectedTarget.setPos(128.5, 70, 62.5);
                reflectedTarget.setNoAi(true);
                var armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
                armor.enchant(destination.registryAccess().lookupOrThrow(
                        net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(
                                net.minecraft.world.item.enchantment.Enchantments.THORNS), 10);
                reflectedTarget.setItemSlot(EquipmentSlot.CHEST, armor);
                destination.addFreshEntity(reflectedTarget);
                source.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY,
                        false, server);
                player.setHealth(.5f);
                player.invulnerableTime = 0;
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
                player.getInventory().setItem(1, new ItemStack(Items.EMERALD, 7));
            }
            default -> throw new AssertionError("Unknown native interaction phase " + next);
        }
        player.inventoryMenu.broadcastChanges();
        if (next.startsWith("projected_")) {
            player.teleportTo(destination, 128.9, 70, 63.65, java.util.Set.of(), 180, 0, false);
            if (next.equals("projected_use"))
                require(Math.abs(player.entityInteractionRange() - .32) < 1e-6,
                        "The projected use fixture did not limit reach before the portal plane");
        } else player.teleportTo(source, 64.5, 70, next.equals("mining_cross") ? 64.4
                : next.equals("reflection") ? 65.4 : 66.5,
                java.util.Set.of(), 180, 0, false);
        phase = next;
        player.sendSystemMessage(Component.literal("PORTAL_NATIVE_SETUP " + next));
        System.out.println("PORTAL_NATIVE_INTERACTION_SETUP phase=" + next);
    }

    private static void verify(ServerPlayer player, String expected) {
        require(expected.equals(phase), "Native interaction verification used a stale phase");
        var server = player.level().getServer();
        var source = server.overworld();
        var destination = server.getLevel(Level.NETHER);
        require(source.getBlockState(SOURCE_WITNESS).is(Blocks.DIAMOND_BLOCK),
                "A native remote action modified the source world instead of its destination");
        for (int x = 61; x <= 68; x++)
            for (int y = 70; y <= 74; y++)
                require(source.getBlockState(new BlockPos(x, y, 63)).is(Blocks.STONE)
                                && destination.getBlockState(new BlockPos(x + 64, y, 64)).is(Blocks.STONE),
                        "A native interaction removed a backing wall");
        switch (expected) {
            case "chest" -> require(player.containerMenu instanceof ChestMenu,
                    "Native right click did not open the destination chest on the server");
            case "button" -> require(buttonActivated,
                    "Native right click did not activate the destination button");
            case "place" -> require(destination.getBlockState(TARGET.south()).is(Blocks.DIAMOND_BLOCK),
                    "Native use did not place a block at the destination");
            case "break" -> require(destination.getBlockState(TARGET).isAir(),
                    "Native attack did not remove the destination block");
            case "entity_use" -> require(stand != null
                            && stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                    "Native entity interaction did not equip the destination armor stand");
            case "entity_attack" -> require(stand != null && stand.isRemoved(),
                    "Native entity attack did not remove the destination armor stand");
            case "source_occluder", "destination_occluder", "outside" ->
                    require(destination.getBlockState(TARGET).is(Blocks.STONE),
                            "An occluded or out-of-aperture target received a remote action");
            case "out_of_reach" -> require(destination.getBlockState(new BlockPos(128, 71, 56))
                            .is(Blocks.GOLD_BLOCK), "An out-of-reach target received a remote action");
            case "mining_cross" -> {
                require(player.level().dimension() == Level.NETHER,
                        "The held mining trajectory did not cross the portal");
                // Client prediction can precede the vanilla server's delayed-destroy tick.
                // Observe the authoritative result without injecting another break action.
                miningCompletionPlayer = player;
                miningCompletionTicks = 0;
                System.out.println("PORTAL_NATIVE_INTERACTION_MINING_WAIT_FOR_SERVER " + miningState(player));
                return;
            }
            case "projected_use" -> require(stand != null && stand.level() == source
                            && stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                    "Native use of the projected half did not equip its real source-world stand");
            case "projected_attack" -> require(stand != null && stand.level() == source && stand.isRemoved(),
                    "Native attack of the projected half did not remove its real source-world stand");
            case "trade" -> {
                require(player.level() == source && player.containerMenu
                                instanceof net.minecraft.world.inventory.MerchantMenu
                                && trader != null && trader.getTradingPlayer() == player,
                        "The native remote trade did not remain open for the real source-world player");
                require(new net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal(trader).canUse(),
                        "The native trading goal rejected a reachable cross-portal trading player");
                source.setBlock(new BlockPos(64, 71, 65), Blocks.NETHERITE_BLOCK.defaultBlockState(), 3);
                tradeClosurePlayer = player;
                tradeClosureTicks = 0;
                return;
            }
            case "mount" -> {
                require(boat != null && player.getVehicle() == boat && player.level() == destination
                                && boat.getPassengers().stream().allMatch(rider -> rider.level() == boat.level()),
                        "Native remote mounting left the rider in another world from its real vehicle");
                System.out.println("PORTAL_NATIVE_INTERACTION_MOUNT_SAME_WORLD");
            }
            case "reflection" -> {
                require(player.isDeadOrDying() && player.level() == source
                                && reflectedTarget != null && reflectedTarget.getHealth() < 20,
                        "Lethal native Thorns did not preserve the attacking player's physical world");
                var search = new net.minecraft.world.phys.AABB(60, 68, 60, 70, 75, 69);
                int dropped = source.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                search, item -> item.getItem().is(Items.EMERALD))
                        .stream().mapToInt(item -> item.getItem().getCount()).sum();
                require(dropped == 7, "Reflected damage dropped the real inventory outside the source world");
                require(destination.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                new net.minecraft.world.phys.AABB(124, 68, 58, 133, 75, 68),
                                item -> item.getItem().is(Items.EMERALD)).isEmpty(),
                        "The temporary remote attack geometry leaked inventory drops into its target world");
                System.out.println("PORTAL_NATIVE_INTERACTION_REFLECTION_SOURCE_WORLD drops=" + dropped);
            }
            default -> throw new AssertionError("Unknown native interaction verification " + expected);
        }
        finishVerification(player, expected);
    }

    private static void finishVerification(ServerPlayer player, String expected) {
        VERIFIED.add(expected);
        player.sendSystemMessage(Component.literal("PORTAL_NATIVE_VERIFIED " + expected));
        System.out.println("PORTAL_NATIVE_INTERACTION_SERVER_OK phase=" + expected
                + " completed=" + VERIFIED.size());
    }

    private static String miningState(ServerPlayer player) {
        var state = new StringBuilder("world=").append(player.level().dimension().identifier())
                .append(" position=").append(player.position());
        for (String name : new String[] {"isDestroyingBlock", "hasDelayedDestroy", "destroyPos",
                "destroyProgressStart", "gameTicks"}) {
            try {
                var field = net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredField(name);
                field.setAccessible(true);
                state.append(' ').append(name).append('=').append(field.get(player.gameMode));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Cannot inspect native mining state", exception);
            }
        }
        return state.toString();
    }
}
