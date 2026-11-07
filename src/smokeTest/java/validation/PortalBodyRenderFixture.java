package validation;

import com.mojang.brigadier.arguments.BoolArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.ProtoPortal;
import qouteall.imm_ptl.peripheral.wand.ProtoPortalSide;

/** Isolated, identifiable player geometry for real first- and third-person portal views. */
final class PortalBodyRenderFixture {
    static final int SOURCE_X = 128;
    static final int DESTINATION_X = 192;
    static final int FLOOR_Y = 199;
    static final int MAGENTA = 0xff00ff;
    static final int CYAN = 0x00ffff;
    private static final java.util.Set<java.util.UUID> CREATED = new java.util.HashSet<>();

    private PortalBodyRenderFixture() {}

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CREATED.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("portal_smoke_body")
                        .requires(source -> source.getPlayer() != null
                                && source.getPlayer().getGameProfile().name().equals("OcclusionTest"))
                        .then(Commands.argument("remote", BoolArgumentType.bool())
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    var world = context.getSource().getServer().overworld();
                                    boolean remote = BoolArgumentType.getBool(context, "remote");
                                    for (var portal : portals(world))
                                        if (CREATED.remove(portal.getUUID())) {
                                            scenePortals.remove(portal.getUUID());
                                            portal.discard();
                                        }
                                    int destinationX = remote ? SOURCE_X : DESTINATION_X;
                                    int destinationZ = remote ? 10 : 0;
                                    for (int centerX : new int[] {SOURCE_X, DESTINATION_X})
                                        for (int x = centerX - 7; x <= centerX + 7; x++)
                                            for (int z = -9; z <= 18; z++) {
                                                world.setBlock(new BlockPos(x, FLOOR_Y, z),
                                                        Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
                                                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 9; y++)
                                                    world.setBlock(new BlockPos(x, y, z),
                                                            Blocks.AIR.defaultBlockState(), 3);
                                            }
                                    var previous = new java.util.HashSet<java.util.UUID>();
                                    for (var portal : portals(world)) previous.add(portal.getUUID());
                                    var proto = new ProtoPortal();
                                    proto.firstSide = new ProtoPortalSide(Level.OVERWORLD,
                                            new Vec3(SOURCE_X - 2, FLOOR_Y + 1, 0));
                                    proto.firstSide.rightBottom =
                                            new Vec3(SOURCE_X + 2, FLOOR_Y + 1, 0);
                                    proto.firstSide.leftTop =
                                            new Vec3(SOURCE_X - 2, FLOOR_Y + 5, 0);
                                    proto.secondSide = new ProtoPortalSide(Level.OVERWORLD,
                                            new Vec3(destinationX + 2, FLOOR_Y + 1, destinationZ));
                                    proto.secondSide.rightBottom =
                                            new Vec3(destinationX - 2, FLOOR_Y + 1, destinationZ);
                                    proto.secondSide.leftTop =
                                            new Vec3(destinationX + 2, FLOOR_Y + 5, destinationZ);
                                    player.setGameMode(GameType.CREATIVE);
                                    PortalWandInteraction.RemoteCallables.finishPortalCreation(player, proto);
                                    for (var portal : portals(world))
                                        if (!previous.contains(portal.getUUID())) {
                                            CREATED.add(portal.getUUID());
                                            scenePortals.add(portal.getUUID());
                                        }
                                    if (CREATED.size() != 4)
                                        throw new AssertionError("The body probe did not create four wand faces");
                                    if (!remote)
                                        for (int x = -3; x <= 3; x++)
                                            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 6; y++) {
                                                world.setBlock(new BlockPos(SOURCE_X + x, y, -1),
                                                        Blocks.STONE.defaultBlockState(), 3);
                                                world.setBlock(new BlockPos(DESTINATION_X + x, y, 0),
                                                        Blocks.STONE.defaultBlockState(), 3);
                                            }
                                    equip(player, EquipmentSlot.HEAD, Items.LEATHER_HELMET, MAGENTA);
                                    equip(player, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, CYAN);
                                    equip(player, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, CYAN);
                                    equip(player, EquipmentSlot.FEET, Items.LEATHER_BOOTS, CYAN);
                                    player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                                    player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                                    player.getAbilities().flying = false;
                                    player.onUpdateAbilities();
                                    player.teleportTo(world, SOURCE_X, FLOOR_Y + 1,
                                            remote ? 5 : 1.5, java.util.Set.of(), 180, 0, false);
                                    context.getSource().getServer().getCommands().performPrefixedCommand(
                                            context.getSource().getServer().createCommandSourceStack(),
                                            "time set noon");
                                    System.out.println("PORTAL_RENDER_BODY_FIXTURE_READY remote=" + remote
                                            + " faces=" + CREATED.size());
                                    return 1;
                                }))));
    }

    private static void equip(net.minecraft.server.level.ServerPlayer player,
            EquipmentSlot slot, net.minecraft.world.item.Item item, int color) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
        player.setItemSlot(slot, stack);
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
}
