package validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.block_manipulation.CrossPortalPickup;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;

import java.util.UUID;

/** Uses the real server tick pickup path with two connected players and vanilla inventories. */
final class PortalPickupQualitySmoke {
    private enum Stage {
        DELAY_WAIT, DELAY_RELEASE, FULL_WAIT, FULL_RELEASE, OWNER_WAIT, OWNER_RELEASE,
        APERTURE_WAIT, REACH_WAIT, RACE_WAIT, CROSS_WORLD_WAIT, COMPLETE
    }

    private static ItemEntity approach;
    private static ItemEntity item;
    private static Portal remote;
    private static Stage stage;
    private static int stageTick;
    private static int initialA;
    private static int initialB;
    private static int approachA;
    private static int approachB;
    private static int savedEmeralds;
    private static ItemStack[] inventory;
    private static boolean approachComplete;
    private static final BlockPos[] APERTURE_POCKET = {
            new BlockPos(63, 69, 31), new BlockPos(63, 69, 32)
    };
    private static BlockState[] apertureFloor;
    private static Vec3 aperturePosition;

    private PortalPickupQualitySmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int ticks(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }

    private static int count(ServerPlayer player, Item type) {
        int result = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(type)) result += stack.getCount();
        }
        return result;
    }

    private static ItemEntity drop(ServerLevel world, Item type, Vec3 position,
            UUID owner, int delay) {
        var entity = new ItemEntity(world, position.x, position.y, position.z,
                new ItemStack(type));
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoGravity(true);
        entity.setUnlimitedLifetime();
        entity.setPickUpDelay(delay);
        entity.setTarget(owner);
        require(world.addFreshEntity(entity), "Could not spawn the pickup witness");
        return entity;
    }

    static void prepare(ServerPlayer a, ServerPlayer b, Portal forward) {
        approachA = count(a, Items.DIAMOND);
        approachB = count(b, Items.DIAMOND);
        approach = drop((ServerLevel) forward.getDestWorld(), Items.DIAMOND,
                new Vec3(63.35, 70.15, 31.7), a.getUUID(), 0);
        System.out.println("PORTAL_QUALITY_PICKUP_READY");
    }

    static boolean tickApproach(ServerPlayer a, ServerPlayer b, Portal forward) {
        if (approachComplete) return true;
        if (!approach.isRemoved()) return false;
        require(count(a, Items.DIAMOND) == approachA + 1
                        && count(b, Items.DIAMOND) == approachB,
                "The remotely owned item was not received exactly once by its owner");
        require(a.level() == forward.level()
                        && forward.getDistanceToPlane(a.getEyePosition()) > 0,
                "Pickup only succeeded after the player crossed the portal");
        approachComplete = true;
        System.out.println("PORTAL_QUALITY_PICKUP_APPROACH_OK");
        return true;
    }

    private static void begin(Stage next, ServerPlayer a, ServerPlayer b, Portal forward,
            Item type, Vec3 position, UUID owner, int delay) {
        stage = next;
        stageTick = ticks(a);
        initialA = count(a, type);
        initialB = count(b, type);
        item = drop((ServerLevel) forward.getDestWorld(), type, position, owner, delay);
    }

    private static Vec3 closePosition(ServerPlayer a, Portal forward) {
        return forward.transformPoint(new Vec3(a.getX(), 70.15, 31.8));
    }

    private static void requireUncollected(ServerPlayer a, ServerPlayer b, Item type,
            String condition) {
        String witness = "; item=" + item.getId() + ", position=" + item.position()
                + ", box=" + item.getBoundingBox() + ", velocity=" + item.getDeltaMovement()
                + ", removed=" + item.isRemoved() + ", stack=" + item.getItem()
                + ", countA=" + count(a, type) + ", countB=" + count(b, type)
                + ", playerA=" + a.position() + ", playerB=" + b.position();
        require(!item.isRemoved() && item.getItem().getCount() == 1,
                condition + " did not preserve the ground item" + witness);
        require(count(a, type) == initialA && count(b, type) == initialB,
                condition + " changed an inventory" + witness);
    }

    private static boolean receivedByA(ServerPlayer a, ServerPlayer b, Item type) {
        if (!item.isRemoved()) return false;
        require(count(a, type) == initialA + 1 && count(b, type) == initialB,
                "A pickup did not change exactly the intended inventory");
        return true;
    }

    static boolean tickRace(ServerPlayer a, ServerPlayer b, Portal forward) {
        require(approachComplete, "The natural remote pickup did not finish first");
        if (stage == Stage.COMPLETE) return true;
        if (stage == null) {
            begin(Stage.DELAY_WAIT, a, b, forward, Items.GOLD_INGOT,
                    closePosition(a, forward), a.getUUID(), 100);
            return false;
        }
        int elapsed = ticks(a) - stageTick;
        require(elapsed < 160, "The pickup quality fixture stalled in " + stage);
        switch (stage) {
            case DELAY_WAIT -> {
                requireUncollected(a, b, Items.GOLD_INGOT, "Vanilla pickup delay");
                if (elapsed < 6) return false;
                item.setNoPickUpDelay();
                stage = Stage.DELAY_RELEASE;
            }
            case DELAY_RELEASE -> {
                if (!receivedByA(a, b, Items.GOLD_INGOT)) return false;
                System.out.println("PORTAL_QUALITY_PICKUP_DELAY_OK");
                inventory = new ItemStack[a.getInventory().getContainerSize()];
                savedEmeralds = count(a, Items.EMERALD);
                for (int slot = 0; slot < inventory.length; slot++) {
                    inventory[slot] = a.getInventory().getItem(slot).copy();
                    a.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                }
                a.containerMenu.broadcastChanges();
                begin(Stage.FULL_WAIT, a, b, forward, Items.EMERALD,
                        closePosition(a, forward), a.getUUID(), 0);
            }
            case FULL_WAIT -> {
                requireUncollected(a, b, Items.EMERALD, "A full survival inventory");
                if (elapsed < 6) return false;
                for (int slot = 0; slot < inventory.length; slot++)
                    a.getInventory().setItem(slot, inventory[slot]);
                a.containerMenu.broadcastChanges();
                initialA = savedEmeralds;
                stage = Stage.FULL_RELEASE;
            }
            case FULL_RELEASE -> {
                if (!receivedByA(a, b, Items.EMERALD)) return false;
                System.out.println("PORTAL_QUALITY_PICKUP_CAPACITY_OK");
                begin(Stage.OWNER_WAIT, a, b, forward, Items.IRON_INGOT,
                        closePosition(a, forward), UUID.randomUUID(), 0);
            }
            case OWNER_WAIT -> {
                requireUncollected(a, b, Items.IRON_INGOT, "Vanilla item ownership");
                if (elapsed < 6) return false;
                item.setTarget(a.getUUID());
                stage = Stage.OWNER_RELEASE;
            }
            case OWNER_RELEASE -> {
                if (!receivedByA(a, b, Items.IRON_INGOT)) return false;
                System.out.println("PORTAL_QUALITY_PICKUP_OWNERSHIP_OK");
                prepareApertureWitness(a, b, forward);
            }
            case APERTURE_WAIT -> {
                requireUncollected(a, b, Items.LAPIS_LAZULI, "Pickup outside the aperture");
                requireApertureWitness(a, forward);
                if (elapsed < 6) return false;
                item.discard();
                for (int i = 0; i < APERTURE_POCKET.length; i++)
                    forward.getDestWorld().setBlock(APERTURE_POCKET[i], apertureFloor[i], 3);
                System.out.println("PORTAL_QUALITY_PICKUP_APERTURE_OK");
                begin(Stage.REACH_WAIT, a, b, forward, Items.NETHERITE_SCRAP,
                        new Vec3(64, 70.15, 28), a.getUUID(), 0);
            }
            case REACH_WAIT -> {
                requireUncollected(a, b, Items.NETHERITE_SCRAP, "Pickup beyond the ordinary range");
                if (elapsed < 6) return false;
                item.discard();
                System.out.println("PORTAL_QUALITY_PICKUP_REACH_OK");
                begin(Stage.RACE_WAIT, a, b, forward, Items.AMETHYST_SHARD,
                        new Vec3(64, 70.15, 31.8), null, 0);
            }
            case RACE_WAIT -> {
                if (elapsed < 6) return false;
                require(item.isRemoved() && count(a, Items.AMETHYST_SHARD)
                                + count(b, Items.AMETHYST_SHARD) == initialA + initialB + 1,
                        "Two eligible players duplicated or lost a single pickup");
                System.out.println("PORTAL_QUALITY_PICKUP_RACE_OK");
                prepareCrossWorld(a, b);
            }
            case CROSS_WORLD_WAIT -> {
                if (elapsed < 20)
                    requireUncollected(a, b, Items.PRISMARINE_SHARD,
                            "Cross-world pickup delay while tracking arrives");
                if (!receivedByA(a, b, Items.PRISMARINE_SHARD)) return false;
                require(a.level() == forward.level(), "Cross-world pickup moved the player");
                remote.discard();
                System.out.println("PORTAL_QUALITY_PICKUP_CROSS_WORLD_OK");
                System.out.println("PORTAL_QUALITY_PICKUP_OK");
                stage = Stage.COMPLETE;
                return true;
            }
            case COMPLETE -> { return true; }
        }
        return false;
    }

    private static void prepareApertureWitness(ServerPlayer a, ServerPlayer b, Portal forward) {
        var world = forward.getDestWorld();
        apertureFloor = new BlockState[APERTURE_POCKET.length];
        for (int i = 0; i < APERTURE_POCKET.length; i++) {
            apertureFloor[i] = world.getBlockState(APERTURE_POCKET[i]);
            world.setBlock(APERTURE_POCKET[i], Blocks.AIR.defaultBlockState(), 3);
        }
        // The entire item is below the opening but within vanilla's vertical pickup range.
        // An empty pocket prevents vanilla block ejection from moving it into the aperture.
        aperturePosition = new Vec3(63.35, 69.6, 31.999999);
        begin(Stage.APERTURE_WAIT, a, b, forward, Items.LAPIS_LAZULI,
                aperturePosition, a.getUUID(), 0);
        requireApertureWitness(a, forward);
    }

    private static void requireApertureWitness(ServerPlayer a, Portal forward) {
        var unfolded = Helper.transformBox(item.getBoundingBox(), forward::inverseTransformPoint);
        require(CrossPortalPickup.pickupBox(a).intersects(unfolded),
                "The aperture rejection witness must remain inside the ordinary pickup range");
        require(unfolded.maxY < 70,
                "The aperture rejection witness must remain wholly below the opening: " + unfolded);
        require(item.position().distanceToSqr(aperturePosition) < 1e-10,
                "The aperture rejection witness moved: " + item.position());
        require(item.level().noBlockCollision(item, item.getBoundingBox()),
                "The aperture rejection witness overlaps a block: " + item.getBoundingBox());
    }

    private static void prepareCrossWorld(ServerPlayer a, ServerPlayer b) {
        var world = a.level().getServer().getLevel(Level.NETHER);
        require(world != null, "No Nether exists for cross-world pickup");
        world.setChunkForced(8, 2, true);
        world.getChunk(8, 2);
        world.waitForEntities(new ChunkPos(8, 2), 0);
        for (int x = 126; x <= 130; x++)
            for (int z = 28; z <= 34; z++)
                for (int y = 69; y <= 75; y++) {
                    boolean shell = x == 126 || x == 130 || z == 28 || z == 34
                            || y == 69 || y == 75;
                    world.setBlock(new BlockPos(x, y, z),
                            (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
                }
        world.setBlock(new BlockPos(128, 75, 31), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        remote = new Portal(Portal.ENTITY_TYPE, a.level());
        remote.setOriginPos(new Vec3(a.getX(), 72, a.getZ() - .01));
        remote.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 1, 4);
        remote.setDestinationDimension(Level.NETHER);
        remote.setDestination(new Vec3(128, 72, 32));
        require(((ServerLevel) a.level()).addFreshEntity(remote),
                "Could not create the cross-world pickup opening");
        initialA = count(a, Items.PRISMARINE_SHARD);
        initialB = count(b, Items.PRISMARINE_SHARD);
        item = drop(world, Items.PRISMARINE_SHARD,
                new Vec3(128, 70.15, 31.8), a.getUUID(), 40);
        stage = Stage.CROSS_WORLD_WAIT;
        stageTick = ticks(a);
    }
}
