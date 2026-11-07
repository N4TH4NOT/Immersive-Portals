package validation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;

public final class PortalSmoke implements ModInitializer {
    private final java.util.Map<java.util.UUID, Long> arrived = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Boolean> pending = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> scenePortals = new java.util.HashSet<>();

    @Override
    public void onInitialize() {
        DimensionLibrarySmoke.init();
        PortalWallRenderFixture.initialize(scenePortals);
        PortalNativeInteractionFixture.initialize(scenePortals);
        PortalOpticsFixture.initialize(scenePortals);
        PortalBodyRenderFixture.initialize(scenePortals);
        PortalEnvironmentFixture.initialize(scenePortals);
        PortalPoseUseFixture.initialize(scenePortals);
        PortalFluidFixture.initialize(scenePortals);
        PortalSameWorldSmoke.initialize(scenePortals);
        PortalQualitySmoke.initialize(scenePortals);
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, access, environment) ->
                        dispatcher.register(
                                net.minecraft.commands.Commands.literal("portal_smoke_wall")
                                        .requires(
                                                source ->
                                                        source.getPlayer() != null
                                                                && source.getPlayer()
                                                                        .getGameProfile()
                                                                        .name()
                                                                        .equals("OcclusionTest"))
                                        .then(
                                                net.minecraft.commands.Commands.argument(
                                                                "closed",
                                                                com.mojang.brigadier.arguments
                                                                        .BoolArgumentType.bool())
                                                        .executes(
                                                                context -> {
                                                                    boolean closed =
                                                                            com.mojang.brigadier
                                                                                    .arguments
                                                                                    .BoolArgumentType
                                                                                    .getBool(
                                                                                            context,
                                                                                            "closed");
                                                                    for (int x = -3; x <= 3; x++)
                                                                        for (int y = 70;
                                                                                y <= 76;
                                                                                y++)
                                                                            context.getSource()
                                                                                    .getServer()
                                                                                    .overworld()
                                                                                    .setBlock(
                                                                                            new BlockPos(
                                                                                                    x,
                                                                                                    y,
                                                                                                    2),
                                                                                            (closed
                                                                                                            ? Blocks
                                                                                                                    .STONE
                                                                                                            : Blocks
                                                                                                                    .AIR)
                                                                                                    .defaultBlockState(),
                                                                                            3);
                                                                    return 1;
                                                                }))));
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> {
                    PortalMigrationSmoke.validateAlternateDimensions(server);
                    DimensionLibrarySmoke.validateOnServerStart(server);
                    var from = server.overworld();
                    var to = server.getLevel(Level.NETHER);
                    PortalCollisionSmoke.keepFixtureLoaded(from, to);
                    for (var world : new net.minecraft.server.level.ServerLevel[] {from, to}) {
                        var stale = new java.util.ArrayList<Portal>();
                        for (var entity : world.getAllEntities())
                            if (entity instanceof Portal portal && !PortalQualitySmoke.preserve(portal))
                                stale.add(portal);
                        stale.forEach(Portal::discard);
                        for (int x = -12; x <= 12; x++)
                            for (int z = -12; z <= 12; z++) {
                                var roomBlock = (world == from ? Blocks.STONE : Blocks.EMERALD_BLOCK)
                                        .defaultBlockState();
                                world.setBlock(
                                        new BlockPos(x, 69, z),
                                        roomBlock,
                                        3);
                                // The Nether retains its native terrain above and around this
                                // room. Seal every boundary so natural lava cannot contaminate
                                // the dry collision and stopped-movement acceptance scenes.
                                world.setBlock(new BlockPos(x, 77, z), roomBlock, 3);
                                for (int y = 70; y < 77; y++)
                                    world.setBlock(
                                            new BlockPos(x, y, z),
                                            Math.abs(x) == 12 || Math.abs(z) == 12
                                                    ? roomBlock : Blocks.AIR.defaultBlockState(),
                                            3);
                            }
                    }
                    // These are real block lights, outside the legacy portal pixel regions.
                    // The lighting regression no longer permits an uninitialized white map.
                    for (int x = -10; x <= 10; x += 5)
                        for (int z = -10; z <= 10; z += 5)
                            to.setBlock(new BlockPos(x, 76, z),
                                    Blocks.SEA_LANTERN.defaultBlockState(), 3);
                    Portal portal = new Portal(Portal.ENTITY_TYPE, from);
                    portal.setOriginPos(new Vec3(0, 72, 0));
                    portal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 4, 4);
                    portal.setDestinationDimension(Level.NETHER);
                    portal.setDestination(new Vec3(0, 72, 0));
                    from.addFreshEntity(portal);
                    var reverse =
                            PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
                    to.addFreshEntity(reverse);
                    var triangle = extra(from, to, new Vec3(-3.75, 72, 0), 3, 4);
                    var mesh = new qouteall.q_misc_util.my_util.Mesh2D();
                    mesh.addTriangle(-1.0, -1.0, 1.0, -1.0, 0.0, 1.0);
                    triangle.setPortalShape(
                            new qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape(mesh));
                    triangle.setScaling(.5);
                    triangle.setRotationTransformation(
                            qouteall.q_misc_util.my_util.DQuaternion.rotationByDegrees(
                                    new Vec3(0, 1, 0), 90));
                    from.addFreshEntity(triangle);
                    triangle.addThisSideAnimationDriver(
                            new qouteall.imm_ptl.core.portal.animation.NormalAnimation(
                                    java.util.List.of(
                                            new qouteall.imm_ptl.core.portal.animation
                                                    .NormalAnimation.Phase(
                                                    40,
                                                    new qouteall.imm_ptl.core.portal.animation
                                                            .DeltaUnilateralPortalState(
                                                            new Vec3(0, .4, 0), null, (Vec3) null),
                                                    qouteall.imm_ptl.core.portal.animation
                                                            .TimingFunction.sine),
                                            new qouteall.imm_ptl.core.portal.animation
                                                    .NormalAnimation.Phase(
                                                    40,
                                                    qouteall.imm_ptl.core.portal.animation
                                                            .DeltaUnilateralPortalState.identity,
                                                    qouteall.imm_ptl.core.portal.animation
                                                            .TimingFunction.sine)),
                                    from.getGameTime(),
                                    100000,
                                    false));
                    var box = extra(from, to, new Vec3(3.75, 72, 0), 2.5, 4);
                    box.setPortalShape(
                            qouteall.imm_ptl.core.portal.shape.BoxPortalShape.FACING_OUTWARDS);
                    box.setThickness(2);
                    from.addFreshEntity(box);
                    var mirror =
                            new qouteall.imm_ptl.core.portal.Mirror(
                                    qouteall.imm_ptl.core.portal.Mirror.ENTITY_TYPE, from);
                    mirror.setOriginPos(new Vec3(0, 75.5, 0));
                    mirror.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), 3, 2);
                    mirror.setDestinationDimension(Level.OVERWORLD);
                    mirror.setDestination(mirror.getOriginPos());
                    from.addFreshEntity(mirror);
                    var fused = extra(from, to, new Vec3(-3.75, 75.5, 0), 3, 2);
                    fused.setFuseView(true);
                    from.addFreshEntity(fused);
                    for (Portal fixture :
                            java.util.List.of(portal, reverse, triangle, box, mirror, fused))
                        scenePortals.add(fixture.getUUID());
                    // Regression coverage for the inclusive region bound and negative world
                    // heights.
                    from.getChunk(-1, -1);
                    from.setBlock(
                            new BlockPos(-1, -60, -1), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                    var access =
                            qouteall.imm_ptl.core.portal.nether_portal.FastBlockAccess.from(
                                    from, new net.minecraft.world.level.ChunkPos(-1, -1), 0);
                    if (!access.getBlockState(-1, -60, -1).is(Blocks.DIAMOND_BLOCK)
                            || access.lX() != 1
                            || access.lZ() != 1)
                        throw new AssertionError("FastBlockAccess boundary regression");
                    System.out.println("PORTAL_SMOKE_ADVANCED_SHAPES_READY");
                    if (Boolean.getBoolean("portalSmoke.singleplayer"))
                        PortalSingleplayerSmoke.validate(server,
                                java.util.List.of(portal, reverse, triangle, box, mirror, fused));
                    to.setBlock(new BlockPos(0, 70, -5), Blocks.CHEST.defaultBlockState(), 3);
                    System.out.println("PORTAL_SMOKE_SERVER_READY");
                });
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    if (PortalQualitySmoke.join(handler.player, scenePortals)) return;
                    handler.player.setGameMode(GameType.CREATIVE);
                    PortalCollisionSmoke.prepare(handler.player, scenePortals);
                    boolean scaled = handler.player.getGameProfile().name().contains("Scaled");
                    handler.player
                            .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)
                            .removeModifier(
                                    net.minecraft.resources.Identifier.parse("iportal:scaling"));
                    handler.player.teleportTo(
                            server.overworld(),
                            scaled ? -3.75 : 0,
                            70,
                            5,
                            java.util.Set.of(),
                            180,
                            0,
                            false);
                    if (Boolean.getBoolean("portalSmoke.singleplayer"))
                        PortalSingleplayerSmoke.validateSameWorldTeleport(handler.player);
                    pending.put(handler.player.getUUID(), scaled);
                    System.out.println("PORTAL_SMOKE_PLAYER_JOINED");
                });
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    // Entity data in these fixture chunks may only load when a player arrives.
                    // The automated scene owns this small region; keep older saved copies out.
                    for (var world : server.getAllLevels()) {
                        var stale = new java.util.ArrayList<Portal>();
                        for (var entity : world.getAllEntities())
                            if (entity instanceof Portal p
                                    && Math.abs(p.getX()) <= 12
                                    && Math.abs(p.getZ()) <= 12
                                    && p.getY() >= 70
                                    && p.getY() <= 78
                                    && !scenePortals.contains(p.getUUID())) stale.add(p);
                        stale.forEach(Portal::discard);
                    }
                    for (var entry : java.util.List.copyOf(pending.entrySet())) {
                        var player = server.getPlayerList().getPlayer(entry.getKey());
                        if (player == null) {
                            pending.remove(entry.getKey());
                            continue;
                        }
                        if (player.level().dimension() == Level.NETHER) {
                            long first =
                                    arrived.computeIfAbsent(
                                            player.getUUID(), id -> server.getTickCount() * 1L);
                            if (server.getTickCount() - first < 10) continue;
                            if (entry.getValue()
                                    && Math.abs(
                                                    player.getAttributeValue(
                                                                    net.minecraft.world.entity.ai
                                                                            .attributes.Attributes
                                                                            .SCALE)
                                                            - .5)
                                            > .001)
                                throw new AssertionError("Server scale was not transformed");
                            if (entry.getValue()
                                    && Math.abs(
                                                    net.minecraft.util.Mth.wrapDegrees(
                                                            player.getYRot() - 90))
                                            > 1)
                                throw new AssertionError(
                                        "Server view was not rotated: " + player.getYRot());
                            if (player.getGameProfile().name().equals("GravityTest")) {
                                var state =
                                        qouteall.imm_ptl.core.compat.GravityChangerInterface.invoker
                                                .getState(player);
                                System.out.println("PORTAL_SMOKE_SERVER_GRAVITY " + state);
                                if (state.direction()
                                        != io.github.n4th4not.immersiveportals.protocol
                                                .GravityDirection.NORTH)
                                    throw new AssertionError(
                                            "Server gravity was not transformed: " + state);
                            }
                            pending.remove(entry.getKey());
                            PortalCollisionSmoke.validatePlayerArrival(player);
                            System.out.println(
                                    entry.getValue()
                                            ? "PORTAL_SMOKE_SERVER_SCALED_TELEPORT_OK"
                                            : "PORTAL_SMOKE_SERVER_TELEPORT_OK");
                        }
                    }
                });
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("gravity_changer"))
            FabricGravitySmoke.init();
    }

    private static Portal extra(
            net.minecraft.server.level.ServerLevel source,
            net.minecraft.server.level.ServerLevel target,
            Vec3 origin,
            double width,
            double height) {
        Portal portal = new Portal(Portal.ENTITY_TYPE, source);
        portal.setOriginPos(origin);
        portal.setOrientationAndSize(new Vec3(1, 0, 0), new Vec3(0, 1, 0), width, height);
        portal.setDestinationDimension(target.dimension());
        portal.setDestination(new Vec3(0, 72, 0));
        return portal;
    }
}
