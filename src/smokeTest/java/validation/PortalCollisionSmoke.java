package validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.ProtoPortal;
import qouteall.imm_ptl.peripheral.wand.ProtoPortalSide;

/** Verifies actual entity movement through a portal placed flush against an intact wall. */
final class PortalCollisionSmoke {
    private PortalCollisionSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean enabled(ServerPlayer player) {
        return player.getGameProfile().name().equals("PortalTest");
    }

    static void keepFixtureLoaded(ServerLevel... worlds) {
        for (var world : worlds) {
            require(world != null, "The portal fixture level is missing");
            // The client may take minutes to boot. Keep the rooms and their entities loaded
            // from initial cleanup through the later JOIN callback, including after restart.
            for (int x = -1; x <= 0; x++)
                for (int z = -1; z <= 0; z++) {
                    world.setChunkForced(x, z, true);
                    world.getChunk(x, z);
                }
            // Load saved faces before the caller removes stale fixture entities. Otherwise
            // older copies can arrive from disk after the fresh scene has been created.
            for (int x = -1; x <= 0; x++)
                for (int z = -1; z <= 0; z++)
                    world.waitForEntities(new ChunkPos(x, z), 0);
        }
        System.out.println("PORTAL_SMOKE_FIXTURE_CHUNKS_READY count=" + worlds.length * 4);
    }

    static void prepare(ServerPlayer player, java.util.Set<java.util.UUID> scenePortals) {
        if (!enabled(player)) return;
        var server = player.level().getServer();
        var source = server.overworld();
        var destination = server.getLevel(Level.NETHER);
        require(destination != null, "Collision fixture has no Nether");
        validateMovement(player, source, destination);
        replacePlayerPortals(player, source, destination, scenePortals);
        // The normal client walk must cross this wall without removing its blocks.
        for (int x = -3; x <= 3; x++)
            for (int y = 70; y <= 74; y++)
                source.setBlock(new BlockPos(x, y, -1), Blocks.STONE.defaultBlockState(), 3);
        requirePlayerWall(source);
        System.out.println("PORTAL_SMOKE_WALL_PORTAL_READY");
    }

    static void validatePlayerArrival(ServerPlayer player) {
        if (!enabled(player)) return;
        require(player.level().dimension() == Level.NETHER,
                "Player did not reach the wall portal's destination");
        var source = player.level().getServer().overworld();
        requirePlayerWall(source);
        for (var world : new ServerLevel[] {source, (ServerLevel) player.level()}) {
            int faces = 0;
            for (var portal : portals(world))
                if (portal.getOriginPos().distanceTo(new Vec3(0, 72, 0)) < .01
                        && portal.getDestDim() == (world == source
                                ? Level.NETHER : Level.OVERWORLD)) faces++;
            require(faces == 2, "The player did not traverse an intact four-face wand cluster");
        }
        System.out.println("PORTAL_SMOKE_SERVER_WALL_PORTAL_OK");
    }

    private static void requirePlayerWall(ServerLevel source) {
        for (int x = -3; x <= 3; x++)
            for (int y = 70; y <= 74; y++)
                require(source.getBlockState(new BlockPos(x, y, -1)).is(Blocks.STONE),
                        "Traversing the portal removed its source wall");
    }

    private static void clearArea(ServerLevel world) {
        for (int x = 61; x <= 67; x++)
            for (int z = 61; z <= 67; z++) {
                world.setBlock(new BlockPos(x, 199, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 200; y <= 204; y++)
                    world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
    }

    private static void wall(ServerLevel world, boolean solid) {
        for (int x = 62; x <= 66; x++)
            for (int y = 200; y <= 203; y++)
                world.setBlock(new BlockPos(x, y, 63),
                        (solid ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
    }

    private static java.util.List<Portal> portals(ServerLevel world) {
        // getAllEntities() only exposes visible chunks. The isolated probe chunks have no
        // player tickets, but their newly spawned wand faces already exist in these sections.
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

    private static java.util.Set<java.util.UUID> portalIds(ServerLevel... worlds) {
        var result = new java.util.HashSet<java.util.UUID>();
        for (var world : worlds)
            for (var portal : portals(world)) result.add(portal.getUUID());
        return result;
    }

    static java.util.List<Portal> createWandFaces(ServerPlayer player, ServerLevel source,
            ServerLevel destination, Vec3 center, double width, double height) {
        var previous = portalIds(source, destination);
        double left = center.x - width / 2;
        double right = center.x + width / 2;
        double bottom = center.y - height / 2;
        double top = center.y + height / 2;
        var proto = new ProtoPortal();
        proto.firstSide = new ProtoPortalSide(source.dimension(), new Vec3(left, bottom, center.z));
        proto.firstSide.rightBottom = new Vec3(right, bottom, center.z);
        proto.firstSide.leftTop = new Vec3(left, top, center.z);
        proto.secondSide = new ProtoPortalSide(destination.dimension(),
                new Vec3(right, bottom, center.z));
        proto.secondSide.rightBottom = new Vec3(left, bottom, center.z);
        proto.secondSide.leftTop = new Vec3(right, top, center.z);
        PortalWandInteraction.RemoteCallables.finishPortalCreation(player, proto);
        var created = new java.util.ArrayList<Portal>();
        for (var world : new ServerLevel[] {source, destination})
            for (var portal : portals(world))
                if (!previous.contains(portal.getUUID())) created.add(portal);
        require(created.size() == 4,
                "Creative wand did not create its four portal faces: found " + created.size());
        return created;
    }

    static void replacePlayerPortals(ServerPlayer player, ServerLevel source,
            ServerLevel destination, java.util.Set<java.util.UUID> scenePortals) {
        var center = new Vec3(0, 72, 0);
        var old = new java.util.ArrayList<Portal>();
        for (var world : new ServerLevel[] {source, destination})
            for (var portal : portals(world))
                if (portal.getOriginPos().distanceTo(center) < .01
                        && portal.getDestDim() == (world == source
                                ? destination.dimension() : source.dimension()))
                    old.add(portal);
        require(old.size() >= 2, "The original player portal pair is missing");
        for (var portal : old) {
            scenePortals.remove(portal.getUUID());
            portal.discard();
        }
        var created = createWandFaces(player, source, destination, center, 4, 4);
        for (var portal : created) scenePortals.add(portal.getUUID());
        System.out.println("PORTAL_SMOKE_WAND_CLUSTER_READY faces=" + created.size());
    }

    private static Portal createWithWand(ServerPlayer player, ServerLevel source,
            ServerLevel destination) {
        var created = createWandFaces(player, source, destination,
                new Vec3(64.5, 201.5, 64), 2, 3);
        var forward = created.stream()
                .filter(portal -> portal.level() == source && portal.getNormal().z > .99)
                .findFirst().orElseThrow(() -> new AssertionError("Wand source portal is missing"));
        // Isolate the collision contract from recursive collisions through the other faces.
        created.stream().filter(portal -> portal != forward).forEach(Portal::discard);
        require(forward.transformLocalVec(new Vec3(0, 0, -1)).distanceTo(new Vec3(0, 0, -1))
                        < 1e-6,
                "Wand fixture unexpectedly rotated the attempted movement");
        return forward;
    }

    private static double move(ServerLevel world, Portal portal, double x) {
        // Do not tick or spawn the stand: Entity.move still executes the production collision
        // hooks, while server teleport scheduling cannot interfere with the measured movement.
        var entity = new ArmorStand(world, x, 200, 64.6);
        if (portal != null) ((IEEntity) entity).ip_notifyCollidingWithPortal(portal);
        double start = entity.getZ();
        entity.move(MoverType.SELF, new Vec3(0, 0, -1));
        return entity.getZ() - start;
    }

    private static void blocked(double movement, String reason) {
        require(movement > -.5 && movement <= 0, reason + ": movement=" + movement);
    }

    private static void validateMovement(ServerPlayer player, ServerLevel source,
            ServerLevel destination) {
        clearArea(source);
        clearArea(destination);
        wall(source, true);
        var portal = createWithWand(player, source, destination);
        try {
            blocked(move(source, null, 64.5), "The intact source wall did not collide normally");
            double flush = move(source, portal, 64.5);
            require(Math.abs(flush + 1) < 1e-6,
                    "The wall behind a flush portal blocked movement: " + flush);
            blocked(move(source, portal, 66.5), "A source wall outside the opening lost collision");
            wall(destination, true);
            blocked(move(source, portal, 64.5), "A visible destination obstacle lost collision");
            wall(destination, false);
            portal.setOriginPos(new Vec3(64.5, 201.5, 63.9));
            require(portal.getThisSideState().position().equals(portal.getOriginPos()),
                    "Moving the collision fixture did not invalidate its cached portal state");
            blocked(move(source, portal, 64.5), "A portal buried inside a block removed collision");
            require(source.getBlockState(new BlockPos(64, 201, 63)).is(Blocks.STONE),
                    "Collision probes removed their source wall");
            System.out.println("PORTAL_SMOKE_COLLISION_PHYSICS_OK flush=" + flush);
        } finally {
            portal.discard();
        }
    }
}
