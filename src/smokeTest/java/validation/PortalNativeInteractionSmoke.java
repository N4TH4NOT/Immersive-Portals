package validation;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.render.NativeEntityPass;
import qouteall.imm_ptl.core.render.NativeWorldRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/** Native input and visible targeting, followed by assertions against the real server world. */
final class PortalNativeInteractionSmoke {
    private enum Stage { SETUP, SETTLE, OUTLINE_ON, OUTLINE_OFF, ACTION, VERIFY, FINISHED }
    private static final String[] PHASES = {
        "chest", "button", "place", "break", "entity_use", "entity_attack",
        "source_occluder", "destination_occluder", "outside", "out_of_reach", "mining_cross",
        "projected_use", "projected_attack"
    };
    private static final java.util.Set<String> ACKNOWLEDGED = new java.util.HashSet<>();
    private static int phase;
    private static Stage stage = Stage.SETUP;
    private static int stageTick;
    private static int outlineDisabledFrame;
    private static int phaseTick;
    private static int remoteOutlineFrames;
    private static int primaryOutlineChecks;
    private static int currentTick;
    private static boolean initialized;
    private static String pendingCapture;
    private static String captured;
    private static boolean miningCrossed;
    private static int lastMiningProgress = -1;
    private static int miningProgressBeforeCross = -1;
    private static int projectedBodyFrames;
    private static int projectedBodyEntity = -1;

    private PortalNativeInteractionSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String phaseName() {
        return PHASES[phase];
    }

    private static BlockPos expectedRemoteBlock() {
        return switch (phaseName()) {
            case "chest", "button", "place", "break", "mining_cross" ->
                    PortalNativeInteractionFixture.TARGET;
            case "destination_occluder" -> PortalNativeInteractionFixture.TARGET.south();
            default -> null;
        };
    }

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                ACKNOWLEDGED.add(message.getString()));
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            if (phase >= PHASES.length || stage == Stage.SETUP || stage == Stage.FINISHED) return;
            if (phaseName().startsWith("projected_") && !WorldRenderInfo.isRendering()
                    && context.level().dimension() == Level.NETHER) {
                var source = ClientWorldLoader.getOptionalWorld(Level.OVERWORLD);
                if (source != null)
                    for (var body : NativeEntityPass.snapshots(context.levelState())) {
                        var entity = source.getEntity(body.entityId());
                        if (body.projected() && entity instanceof ArmorStand
                                && entity.position().distanceTo(new Vec3(64.5, 70, 64.1)) < .01) {
                            projectedBodyFrames++;
                            projectedBodyEntity = body.entityId();
                        }
                    }
            }
            if (BlockManipulationClient.remotePointedDim == null
                    || !(BlockManipulationClient.remoteHitResult instanceof BlockHitResult remote)) return;
            var expected = remote.getBlockPos();
            var outline = context.levelState().blockOutlineRenderState;
            if (context.level().dimension() == Level.NETHER
                    && PortalRendering.getPortalLayer() == 1
                    && BlockManipulationClient.remotePortalPath.size() == 1
                    && PortalRendering.getRenderingPortal().getUUID()
                            .equals(BlockManipulationClient.remotePortalPath.getFirst())) {
                require(outline != null && outline.pos().equals(expected),
                        "Native remote selection did not extract the targeted block: phase="
                                + phaseName() + " outline=" + outline);
                remoteOutlineFrames++;
            } else if (!WorldRenderInfo.isRendering()) {
                require(outline == null, "The destination selection leaked into the primary world");
                primaryOutlineChecks++;
            }
        });
        IPGlobal.PRE_GAME_RENDER_EVENT.register(() -> {
            if (pendingCapture == null || WorldRenderInfo.isRendering()) return;
            var client = Minecraft.getInstance();
            Screenshot.grab(client.gameDirectory, pendingCapture + ".png",
                    client.gameRenderer.mainRenderTarget(), 1, message -> {});
            captured = pendingCapture;
            pendingCapture = null;
        });
    }

    private static void releaseKeys(Minecraft client) {
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
        client.options.keyUp.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keySprint.setDown(false);
    }

    private static void aim(Minecraft client) {
        if (phase >= PHASES.length) return;
        double x = client.level.dimension() == Level.NETHER ? 128.5 : 64.5;
        Vec3 point = switch (phaseName()) {
            case "button" -> new Vec3(x, 71.08, 62.5);
            case "entity_use", "entity_attack" -> new Vec3(x, 71.7, 62.5);
            case "projected_use", "projected_attack" -> new Vec3(128.7, 71.62, 63.9);
            case "outside" -> new Vec3(68.5, 71.5, 64);
            case "out_of_reach" -> new Vec3(x, 71.5, 57);
            default -> new Vec3(x, 71.5, 63);
        };
        Vec3 direction = point.subtract(client.player.getEyePosition());
        client.player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        client.player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
    }

    private static void checkTarget(Minecraft client) {
        String phase = phaseName();
        BlockPos expected = expectedRemoteBlock();
        if (expected != null) {
            require(BlockManipulationClient.remotePointedDim == Level.NETHER
                            && BlockManipulationClient.remoteHitResult instanceof BlockHitResult hit
                            && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(expected),
                    "Native remote block target is wrong: phase=" + phase
                            + " target=" + BlockManipulationClient.remoteHitResult
                            + " path=" + BlockManipulationClient.remotePortalPath
                            + " eye=" + client.player.getEyePosition());
            require(BlockManipulationClient.remotePortalPath.size() == 1,
                    "The native fixture did not select exactly one opening");
        } else if (phase.startsWith("projected_")) {
            require(BlockManipulationClient.remotePointedDim == Level.OVERWORLD
                            && BlockManipulationClient.remoteHitResult instanceof EntityHitResult hit
                            && hit.getEntity() instanceof ArmorStand
                            && hit.getEntity().getId() == projectedBodyEntity
                            && hit.getEntity().level().dimension() == Level.OVERWORLD
                            && hit.getLocation().z < 64
                            && BlockManipulationClient.remoteProjectionPortal != null
                            && BlockManipulationClient.remotePortalPath.isEmpty(),
                    "Native projected-half interaction did not target the visible source-world body: "
                            + BlockManipulationClient.remoteHitResult);
            require(projectedBodyFrames > 0,
                    "The projected half was targeted without a matching native rendered body");
            if (phase.equals("projected_use")) {
                require(Math.abs(client.player.entityInteractionRange() - .32) < 1e-6,
                        "The projected use reach was not synchronized to the client");
                require(client.player.getEyePosition().z < 63.7,
                        "The projected-use player moved too close to the portal plane");
                require(BlockManipulationClient.remoteHitDistance <= .32
                                && (64 - client.player.getEyePosition().z)
                                        / client.player.getViewVector(1).z > .4,
                        "Projected use did not distinguish the reachable body from the farther portal plane");
            }
            System.out.println("PORTAL_NATIVE_INTERACTION_PROJECTED_BODY phase=" + phase
                    + " entity=" + ((EntityHitResult) BlockManipulationClient.remoteHitResult)
                            .getEntity().getId() + " frames=" + projectedBodyFrames
                    + " reach=" + client.player.entityInteractionRange()
                    + " distance=" + BlockManipulationClient.remoteHitDistance);
        } else if (phase.startsWith("entity_")) {
            require(BlockManipulationClient.remotePointedDim == Level.NETHER
                            && BlockManipulationClient.remoteHitResult instanceof EntityHitResult hit
                            && hit.getEntity() instanceof ArmorStand,
                    "Native remote entity was not targeted: " + BlockManipulationClient.remoteHitResult);
        } else {
            require(BlockManipulationClient.remotePointedDim == null,
                    "An occluded, out-of-aperture or out-of-reach object was remotely targeted");
            if (phase.equals("source_occluder"))
                require(client.hitResult instanceof BlockHitResult hit
                                && hit.getBlockPos().equals(new BlockPos(64, 71, 65)),
                        "The real source obstacle did not take priority over the portal");
            if (phase.equals("out_of_reach"))
                require(client.hitResult.getType() == HitResult.Type.MISS,
                        "The empty in-range destination selected terrain beyond normal reach");
        }
    }

    private static boolean fixtureDataReady(Minecraft client) {
        if (phaseName().startsWith("projected_")) {
            if (client.level.dimension() != Level.NETHER
                    || Math.abs(client.player.getX() - 128.9) > .01
                    || Math.abs(client.player.getZ() - 63.65) > .01) return false;
            var source = ClientWorldLoader.getOptionalWorld(Level.OVERWORLD);
            if (source == null || !source.getBlockState(PortalNativeInteractionFixture.SOURCE_WITNESS)
                    .is(Blocks.DIAMOND_BLOCK) || projectedBodyFrames == 0) return false;
            if (phaseName().equals("projected_use")
                    && (Math.abs(client.player.entityInteractionRange() - .32) > 1e-6
                            || !client.player.getMainHandItem().is(Items.DIAMOND_HELMET))) return false;
            for (var entity : source.entitiesForRendering())
                if (entity instanceof ArmorStand && entity.position()
                        .distanceTo(new Vec3(64.5, 70, 64.1)) < .01) return true;
            return false;
        }
        if (client.level.dimension() != Level.OVERWORLD
                || Math.abs(client.player.getX() - 64.5) > .01) return false;
        var world = ClientWorldLoader.getOptionalWorld(Level.NETHER);
        if (world == null) return false;
        if (!client.level.getBlockState(PortalNativeInteractionFixture.SOURCE_WITNESS)
                        .is(Blocks.DIAMOND_BLOCK)
                || !world.getBlockState(new BlockPos(128, 71, 64)).is(Blocks.STONE)) return false;
        boolean sourcePortal = false;
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof qouteall.imm_ptl.core.portal.Portal portal
                    && portal.getNormal().z > .99
                    && portal.getOriginPos().distanceTo(new Vec3(64.5, 72, 64)) < .01)
                sourcePortal = true;
        if (!sourcePortal) return false;
        var target = world.getBlockState(PortalNativeInteractionFixture.TARGET);
        return switch (phaseName()) {
            case "chest" -> target.is(Blocks.CHEST);
            case "button" -> target.is(Blocks.OAK_BUTTON);
            case "place", "break", "source_occluder", "destination_occluder", "outside" ->
                    target.is(Blocks.STONE);
            case "mining_cross" -> target.is(Blocks.IRON_BLOCK)
                    && client.player.getMainHandItem().is(Items.IRON_PICKAXE);
            case "out_of_reach" -> world.getBlockState(new BlockPos(128, 71, 56)).is(Blocks.GOLD_BLOCK);
            case "entity_use", "entity_attack" -> {
                boolean present = false;
                for (var entity : world.entitiesForRendering())
                    if (entity instanceof ArmorStand && entity.position()
                            .distanceTo(new Vec3(128.5, 70, 62.5)) < .01) present = true;
                yield present;
            }
            default -> false;
        };
    }

    private static void setOutlineRendering(Minecraft client, boolean enabled) {
        client.gameRenderer.setRenderBlockOutline(enabled);
        try {
            var field = NativeWorldRenderer.class.getDeclaredField("SCENES");
            field.setAccessible(true);
            for (Object value : ((java.util.Map<?, ?>) field.get(null)).values())
                ((NativeWorldRenderer.Scene) value).gameRenderer.setRenderBlockOutline(enabled);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot control the public fixture's native outline renderer", exception);
        }
    }

    private static void action(Minecraft client) {
        checkTarget(client);
        boolean attack = phaseName().equals("break") || phaseName().equals("entity_attack")
                || phaseName().equals("mining_cross") || phaseName().equals("projected_attack");
        var key = attack ? client.options.keyAttack : client.options.keyUse;
        KeyMapping.click(key.getDefaultKey());
        key.setDown(true);
        if (phaseName().equals("mining_cross")) {
            miningCrossed = false;
            lastMiningProgress = -1;
            miningProgressBeforeCross = -1;
            client.options.keyUp.setDown(true);
            client.options.keyShift.setDown(true);
        }
        stage = Stage.ACTION;
        stageTick = currentTick;
        System.out.println("PORTAL_NATIVE_INTERACTION_INPUT phase=" + phaseName()
                + " target=" + BlockManipulationClient.remoteHitResult);
    }

    private static boolean actionComplete(Minecraft client, int elapsed) {
        if (phaseName().equals("mining_cross")) {
            var destination = ClientWorldLoader.getWorld(Level.NETHER);
            boolean broken = destination.getBlockState(PortalNativeInteractionFixture.TARGET).isAir();
            int progress = client.gameMode.getDestroyStage();
            if (client.level.dimension() == Level.NETHER) {
                client.options.keyUp.setDown(false);
                client.options.keyShift.setDown(false);
                if (!miningCrossed) {
                    require(miningProgressBeforeCross >= 0 && progress >= miningProgressBeforeCross,
                            "Mining progress reset when ordinary movement crossed the portal: before="
                                    + miningProgressBeforeCross + " after=" + progress);
                    miningCrossed = true;
                    System.out.println("PORTAL_NATIVE_INTERACTION_MINING_CROSS progress=" + progress);
                }
            } else {
                require(!broken, "The mining witness broke before the player crossed");
                miningProgressBeforeCross = Math.max(miningProgressBeforeCross, progress);
            }
            if (!broken && progress >= 0) {
                require(progress >= lastMiningProgress,
                        "Held mining progress decreased while targeting the same destination block");
                lastMiningProgress = progress;
            }
            if (!broken) return false;
            require(miningCrossed, "The mining trajectory did not cross before finishing");
            releaseKeys(client);
            return true;
        }
        if (elapsed >= 2) releaseKeys(client);
        if (phaseName().equals("chest"))
            return client.gui.screen() instanceof AbstractContainerScreen;
        return elapsed >= 8;
    }

    static void tick(Minecraft client, int ticks) {
        initialize();
        currentTick = ticks;
        if (stage == Stage.FINISHED) return;
        if (ticks < 10) return;
        require(phaseTick == 0 || ticks - phaseTick < 240,
                "Native interaction phase did not finish: " + phaseName() + " stage=" + stage);
        if (stage == Stage.SETUP) {
            releaseKeys(client);
            phaseTick = ticks;
            stageTick = ticks;
            remoteOutlineFrames = primaryOutlineChecks = 0;
            projectedBodyFrames = 0;
            projectedBodyEntity = -1;
            captured = null;
            client.options.highContrastBlockOutline().set(true);
            client.player.connection.sendCommand("portal_native_interaction_setup " + phaseName());
            stage = Stage.SETTLE;
            return;
        }
        aim(client);
        int elapsed = ticks - stageTick;
        switch (stage) {
            case SETTLE -> {
                if (elapsed < 35 || !ACKNOWLEDGED.contains("PORTAL_NATIVE_SETUP " + phaseName())) return;
                if (!fixtureDataReady(client)) return;
                checkTarget(client);
                if (phaseName().equals("place"))
                    require(client.player.getMainHandItem().is(Items.DIAMOND_BLOCK),
                            "The placing fixture did not synchronize its held item");
                if (phaseName().equals("chest")) {
                    if (remoteOutlineFrames <= 2 || primaryOutlineChecks <= 2) return;
                    pendingCapture = "portal-native-outline-on";
                    stage = Stage.OUTLINE_ON;
                    stageTick = ticks;
                } else action(client);
            }
            case OUTLINE_ON -> {
                if (!"portal-native-outline-on".equals(captured)) return;
                setOutlineRendering(client, false);
                outlineDisabledFrame = qouteall.imm_ptl.core.render.context_management.RenderStates.frameIndex;
                stage = Stage.OUTLINE_OFF;
                stageTick = ticks;
            }
            case OUTLINE_OFF -> {
                if (elapsed < 8 || qouteall.imm_ptl.core.render.context_management.RenderStates.frameIndex
                        < outlineDisabledFrame + 2) return;
                if (!"portal-native-outline-off".equals(captured)) {
                    pendingCapture = "portal-native-outline-off";
                    return;
                }
                setOutlineRendering(client, true);
                System.out.println("PORTAL_NATIVE_INTERACTION_OUTLINE_OK remoteFrames="
                        + remoteOutlineFrames + " primaryChecks=" + primaryOutlineChecks);
                action(client);
            }
            case ACTION -> {
                if (!actionComplete(client, elapsed)) return;
                releaseKeys(client);
                client.player.connection.sendCommand("portal_native_interaction_verify " + phaseName());
                stage = Stage.VERIFY;
                stageTick = ticks;
            }
            case VERIFY -> {
                if (!ACKNOWLEDGED.contains("PORTAL_NATIVE_VERIFIED " + phaseName())) return;
                if (client.gui.screen() instanceof AbstractContainerScreen) client.player.closeContainer();
                if (elapsed < 5) return;
                System.out.println("PORTAL_NATIVE_INTERACTION_CLIENT_OK phase=" + phaseName());
                if (++phase == PHASES.length) {
                    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                        require(qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface.invoker
                                        .isSodiumPresent()
                                        && qouteall.imm_ptl.core.render.RenderDiagnostics.sodiumViewports() > 0,
                                "The native interaction scenario did not exercise Sodium's portal viewports");
                        System.out.println("PORTAL_NATIVE_INTERACTION_SODIUM_COMPAT_OK");
                    }
                    Screenshot.grab(client.gameDirectory, "portal-native-interactions-complete.png",
                            client.gameRenderer.mainRenderTarget(), 1, message -> {});
                    System.out.println("PORTAL_NATIVE_INTERACTION_COMPLETE phases=" + PHASES.length);
                    stage = Stage.FINISHED;
                    client.stop();
                } else stage = Stage.SETUP;
            }
            case SETUP, FINISHED -> {}
        }
    }
}
