package validation;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.portal.Portal;

/** Observes real native fluid state; only normal movement keys drive destination crossings. */
final class PortalFluidSmoke {
    private enum Stage { SETUP, SOURCE, CROSS, DESTINATION, VERIFY, RESTORE, COMPLETE }
    private static final java.util.Set<String> messages = new java.util.HashSet<>();
    private static boolean initialized;
    private static int phase = -1, ticks, started, stageTick, sourceSamples, destinationSamples;
    private static Stage stage;
    private static Vec3 settled;
    private static double projectedLavaCurrent, destinationLavaCurrent;

    private PortalFluidSmoke() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String name() { return PortalFluidFixture.CASES[phase]; }

    private static void stop(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyUse.setDown(false);
        client.options.keyAttack.setDown(false);
    }

    private static void begin(Minecraft client) {
        phase++;
        sourceSamples = destinationSamples = 0;
        settled = null;
        projectedLavaCurrent = destinationLavaCurrent = 0;
        stop(client);
        started = stageTick = ticks;
        if (phase == PortalFluidFixture.CASES.length) {
            stage = Stage.RESTORE;
            client.player.connection.sendCommand("portal_fluid_restore");
            return;
        }
        stage = Stage.SETUP;
        client.player.connection.sendCommand("portal_fluid_setup " + name());
    }

    private static boolean ready(Minecraft client) {
        if (!messages.contains("PORTAL_FLUID_SETUP " + name())
                || client.level.dimension() != Level.OVERWORLD
                || (!name().equals("destination_lava")
                    && client.player.position().distanceTo(PortalFluidFixture.START) > .08)
                || (name().equals("destination_lava")
                    && (Math.abs(client.player.getX() - PortalFluidFixture.START.x) > 2.5
                        || Math.abs(client.player.getY() - PortalFluidFixture.START.y) > .08
                        || Math.abs(client.player.getZ() - PortalFluidFixture.START.z) > .08))
                || Math.abs(ScaleUtils.getScale(client.player) - PortalFluidFixture.scale(name())) > .001)
            return false;
        if (PortalFluidFixture.physicalFluid(client.player)
                == PortalFluidFixture.destinationCase(name())) return false;
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof Portal portal && portal.getOriginPos().x == 512
                    && portal.getOriginPos().z == 64 && portal.getNormal().z > .99)
                return true;
        return false;
    }

    static boolean tick(Minecraft client, int currentTick) {
        if (!client.player.getGameProfile().name().equals("PortalTest")) return true;
        ticks = currentTick;
        if (!initialized) {
            initialized = true;
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> messages.add(message.getString()));
            begin(client);
        }
        if (stage == Stage.COMPLETE) return true;
        require(ticks - started < 600, "The native fluid case stalled: "
                + (phase < PortalFluidFixture.CASES.length ? name() : "restore") + " " + stage);
        switch (stage) {
            case SETUP -> {
                stop(client);
                if (ticks - started < (name().equals("destination_lava") ? 220 : 30)
                        || !ready(client)) return false;
                stage = Stage.SOURCE;
                stageTick = ticks;
            }
            case SOURCE -> {
                stop(client);
                require(client.level.dimension() == Level.OVERWORLD,
                        "The stationary fluid probe crossed without ordinary movement input");
                boolean hidden = name().startsWith("hidden_");
                if (hidden) {
                    require(PortalFluidFixture.physicalFluid(client.player),
                            "The hidden-fluid chamber never reached the raw actual client body");
                    require(!client.player.isInWater() && !client.player.isInLava()
                                    && !client.player.isEyeInFluid(FluidTags.WATER)
                                    && client.player.getFluidHeight(FluidTags.WATER) == 0
                                    && client.player.getFluidHeight(FluidTags.LAVA) == 0,
                            "An invisible source fluid affected the actual client body: " + name());
                    require(client.player.getAirSupply() == client.player.getMaxAirSupply()
                                    && client.player.getHealth() == client.player.getMaxHealth(),
                            "An invisible fluid damaged or drowned the actual client");
                    if (settled == null) settled = client.player.position();
                    require(client.player.position().distanceTo(settled) < .025,
                            "An invisible fluid pushed the stopped actual client");
                } else if (name().equals("destination_lava")) {
                    require(client.player.isInLava() && !client.player.isInWater()
                                    && client.player.getFluidHeight(FluidTags.LAVA) > 0
                                    && !PortalFluidFixture.physicalFluid(client.player)
                                    && client.player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE),
                            "Projected Nether lava lost its real current/state or gained source fluid");
                    if (sourceSamples >= 12)
                        require(client.player.getDeltaMovement().x >= .006
                                        && client.player.getDeltaMovement().x <= .0085,
                                "The projected lava current did not preserve Nether strength: "
                                        + client.player.getDeltaMovement());
                    projectedLavaCurrent = client.player.getDeltaMovement().x;
                } else {
                    require(client.player.isInWater() && !client.player.isInLava()
                                    && client.player.getFluidHeight(FluidTags.WATER) > 0,
                            "Visible source or projected destination water lost native behavior: " + name());
                    require(client.player.isEyeInFluid(FluidTags.WATER) == name().equals("visible_water"),
                            "The wet/dry eye did not remain in its physical world: " + name());
                    if (PortalFluidFixture.destinationCase(name()))
                        require(!PortalFluidFixture.physicalFluid(client.player),
                                "The destination-water probe has fluid in its physical source");
                }
                sourceSamples++;
                if (sourceSamples < (PortalFluidFixture.destinationCase(name()) ? 35 : 80)) return false;
                if (PortalFluidFixture.destinationCase(name())) {
                    stage = Stage.CROSS;
                    stageTick = ticks;
                } else verify(client);
            }
            case CROSS -> {
                client.options.keyUp.setDown(true);
                if (client.level.dimension() == Level.NETHER) {
                    stop(client);
                    stage = Stage.DESTINATION;
                    stageTick = ticks;
                }
            }
            case DESTINATION -> {
                stop(client);
                boolean lava = name().equals("destination_lava");
                require(client.level.dimension() == Level.NETHER
                                && (lava ? client.player.isInLava() && !client.player.isInWater()
                                        : client.player.isInWater() && !client.player.isInLava()
                                                && client.player.isEyeInFluid(FluidTags.WATER)),
                        "A real destination crossing lost native water/eye state: " + name());
                if (lava && destinationSamples >= 12)
                    require(client.player.getDeltaMovement().x >= .006
                                    && client.player.getDeltaMovement().x <= .0085,
                            "The actual Nether lava current changed after crossing: "
                                    + client.player.getDeltaMovement());
                if (lava) destinationLavaCurrent = client.player.getDeltaMovement().x;
                destinationSamples++;
                if (destinationSamples >= 70) verify(client);
            }
            case VERIFY -> {
                stop(client);
                if (!messages.contains("PORTAL_FLUID_VERIFIED " + name())) return false;
                System.out.println("PORTAL_FLUID_CLIENT_OK case=" + name() + " source="
                        + sourceSamples + " destination=" + destinationSamples);
                if (name().equals("destination_lava"))
                    System.out.println("PORTAL_FLUID_LAVA_CURRENT_OK projected=" + projectedLavaCurrent
                            + " destination=" + destinationLavaCurrent + " nativeNetherStrength=0.007");
                begin(client);
            }
            case RESTORE -> {
                stop(client);
                if (messages.contains("PORTAL_FLUID_RESTORED")
                        && Math.abs(ScaleUtils.getScale(client.player) - 1) < .001
                        && !client.player.isInWater() && !client.player.isInLava()) {
                    stage = Stage.COMPLETE;
                    System.out.println("PORTAL_FLUID_COMPLETE cases=" + PortalFluidFixture.CASES.length);
                }
            }
            default -> throw new AssertionError("Unexpected native fluid stage " + stage);
        }
        return stage == Stage.COMPLETE;
    }

    private static void verify(Minecraft client) {
        stop(client);
        client.player.connection.sendCommand("portal_fluid_verify " + name());
        stage = Stage.VERIFY;
        stageTick = ticks;
    }
}
