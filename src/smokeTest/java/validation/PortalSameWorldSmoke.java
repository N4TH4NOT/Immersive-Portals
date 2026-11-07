package validation;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.ProtoPortal;
import qouteall.imm_ptl.peripheral.wand.ProtoPortalSide;

/** Keeps both backing walls intact while ordinary movement crosses within one dimension. */
final class PortalSameWorldSmoke {
    private static final Vec3 SOURCE = new Vec3(0, 72, -5);
    private static final Vec3 DESTINATION = new Vec3(64, 72, -5);

    private static final class Probe {
        final java.util.Set<java.util.UUID> faces;
        boolean destination;
        int transitions;
        int arrivalTick;
        boolean complete;

        Probe(java.util.Set<java.util.UUID> faces) {
            this.faces = faces;
        }
    }

    private PortalSameWorldSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void initialize(java.util.Set<java.util.UUID> scenePortals) {
        var probes = new java.util.HashMap<java.util.UUID, Probe>();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> probes.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("portal_smoke_same_world")
                        .requires(source -> source.getPlayer() != null
                                && source.getPlayer().getGameProfile().name().equals("PortalTest"))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            require(!probes.containsKey(player.getUUID()),
                                    "The same-world trajectory was initialized twice");
                            probes.put(player.getUUID(), prepare(player, scenePortals));
                            return 1;
                        })));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var entry : probes.entrySet()) {
                var probe = entry.getValue();
                if (probe.complete) continue;
                var player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                require(player.level().dimension() == Level.NETHER,
                        "Same-world traversal changed dimension");
                boolean destination = player.getX() > 32;
                if (destination != probe.destination) {
                    probe.destination = destination;
                    probe.transitions++;
                    probe.arrivalTick = server.getTickCount();
                    System.out.println("PORTAL_SMOKE_SERVER_SAME_WORLD_TRANSITION count="
                            + probe.transitions + " destination=" + destination
                            + " position=" + player.position());
                }
                if (!destination || probe.transitions < 3
                        || server.getTickCount() - probe.arrivalTick < 10) continue;
                var world = (ServerLevel) player.level();
                require(Math.abs(player.getX() - DESTINATION.x) < .05
                                && Math.abs(player.getY() - 70) < .025,
                        "The same-world wall portal displaced the stopped player: "
                                + player.position());
                requireWalls(world);
                long faces = portals(world).stream()
                        .filter(portal -> probe.faces.contains(portal.getUUID())).count();
                require(faces == 4, "The same-world wand cluster lost a portal face: " + faces);
                probe.complete = true;
                System.out.println("PORTAL_SMOKE_SERVER_SAME_WORLD_OK transitions="
                        + probe.transitions + " faces=" + faces);
            }
        });
    }

    private static Probe prepare(ServerPlayer player, java.util.Set<java.util.UUID> scenePortals) {
        require(player.level().dimension() == Level.NETHER,
                "The same-world fixture must follow the cross-dimension trajectory");
        var world = (ServerLevel) player.level();
        for (int centerX : new int[] {0, 64})
            for (int x = centerX - 4; x <= centerX + 4; x++)
                for (int z = -13; z <= 2; z++) {
                    world.setBlock(new BlockPos(x, 69, z),
                            Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
                    for (int y = 70; y <= 76; y++)
                        world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
        for (var portal : portals(world))
            if (portal.getOriginPos().distanceTo(SOURCE) < .01
                    || portal.getOriginPos().distanceTo(DESTINATION) < .01) {
                scenePortals.remove(portal.getUUID());
                portal.discard();
            }
        var previous = new java.util.HashSet<java.util.UUID>();
        for (var portal : portals(world)) previous.add(portal.getUUID());
        var proto = new ProtoPortal();
        proto.firstSide = new ProtoPortalSide(Level.NETHER, new Vec3(-2, 70, -5));
        proto.firstSide.rightBottom = new Vec3(2, 70, -5);
        proto.firstSide.leftTop = new Vec3(-2, 74, -5);
        proto.secondSide = new ProtoPortalSide(Level.NETHER, new Vec3(66, 70, -5));
        proto.secondSide.rightBottom = new Vec3(62, 70, -5);
        proto.secondSide.leftTop = new Vec3(66, 74, -5);
        PortalWandInteraction.RemoteCallables.finishPortalCreation(player, proto);
        var created = new java.util.HashSet<java.util.UUID>();
        Portal forward = null;
        for (var portal : portals(world))
            if (!previous.contains(portal.getUUID())) {
                created.add(portal.getUUID());
                if (portal.getOriginPos().distanceTo(SOURCE) < .01
                        && portal.getNormal().z > .99) forward = portal;
            }
        require(created.size() == 4 && forward != null,
                "The same-world creative wand did not create four faces: " + created.size());
        require(forward.getDestDim() == Level.NETHER
                        && forward.transformPoint(SOURCE).distanceTo(DESTINATION) < 1e-6
                        && forward.transformLocalVec(new Vec3(0, 0, -1))
                                .distanceTo(new Vec3(0, 0, -1)) < 1e-6,
                "The same-world fixture must translate across entity sections without rotation");
        scenePortals.addAll(created);
        for (int x = -3; x <= 3; x++)
            for (int y = 70; y <= 74; y++) {
                world.setBlock(new BlockPos(x, y, -6), Blocks.STONE.defaultBlockState(), 3);
                world.setBlock(new BlockPos(x + 64, y, -5), Blocks.STONE.defaultBlockState(), 3);
            }
        requireWalls(world);
        System.out.println("PORTAL_SMOKE_SAME_WORLD_READY faces=" + created.size()
                + " translation=64");
        return new Probe(created);
    }

    private static void requireWalls(ServerLevel world) {
        for (int x = -3; x <= 3; x++)
            for (int y = 70; y <= 74; y++)
                require(world.getBlockState(new BlockPos(x, y, -6)).is(Blocks.STONE)
                                && world.getBlockState(new BlockPos(x + 64, y, -5)).is(Blocks.STONE),
                        "Same-world traversal removed a backing wall");
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
