package validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import qouteall.imm_ptl.core.*;
import qouteall.imm_ptl.core.compat.iris_compatibility.*;
import qouteall.imm_ptl.core.portal.*;
import qouteall.imm_ptl.core.render.*;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.renderer.*;

/** Measures occlusion/reveal and exercises each rendering strategy and its runtime controls. */
final class PortalRenderingSmoke {
    private static int firstFrame = -1;
    private static int logicalTicks;

    private static Portal center(Minecraft client) {
        for (var entity : client.level.entitiesForRendering())
            if (entity instanceof Portal portal
                    && !(portal instanceof Mirror)
                    && portal.getOriginPos().distanceTo(new net.minecraft.world.phys.Vec3(0, 72, 0))
                            < .1) return portal;
        throw new AssertionError("Central portal is missing");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void capture(Minecraft client, String name) {
        net.minecraft.client.Screenshot.grab(
                client.gameDirectory,
                name + ".png",
                client.gameRenderer.mainRenderTarget(),
                1,
                text -> {});
    }

    private static void captureTransformedSources(Minecraft client) {
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Portal portal)
                    || (!(portal instanceof Mirror)
                            && !(portal.getPortalShape() instanceof
                                    qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape)))
                continue;
            String name = portal instanceof Mirror ? "mirror" : "triangle";
            qouteall.imm_ptl.core.render.context_management.PortalRendering.pushPortalLayer(portal);
            try {
                var info = new qouteall.imm_ptl.core.render.context_management.WorldRenderInfo.Builder()
                        .setWorld(ClientWorldLoader.getWorld(portal.getDestDim()))
                        .setCameraPos(qouteall.imm_ptl.core.render.context_management.PortalRendering
                                .getRenderingCameraPos())
                        .setCameraTransformation(portal.getAdditionalCameraTransformation())
                        .setOverwriteCameraTransformation(false)
                        .setRenderDistance(client.options.getEffectiveRenderDistance())
                        .setDoRenderHand(false)
                        .setDoRenderSky(!portal.isFuseView())
                        .build();
                IPCGlobal.renderer.invokeWorldRendering(info);
                net.minecraft.client.Screenshot.grab(
                        client.gameDirectory,
                        "portal-source-" + name + ".png",
                        MyGameRenderer.renderedTarget(),
                        1,
                        text -> {});
                System.out.println("PORTAL_RENDER_SOURCE " + name
                        + " camera=" + info.cameraPos
                        + " world=" + info.world.dimension().identifier()
                        + " transform=" + portal.getAdditionalCameraTransformation()
                        + " projection=" + NativeWorldRenderer.lastProjection());
            } finally {
                qouteall.imm_ptl.core.render.context_management.PortalRendering.popPortalLayer();
            }
        }
    }

    private static void validatePlayerVisibility(Minecraft client) {
        var previousCamera = client.options.getCameraType();
        boolean previousRenderPlayer = IPGlobal.renderYourselfInPortal;
        var parent = client.gameRenderer.mainCamera();
        var info = new qouteall.imm_ptl.core.render.context_management.WorldRenderInfo.Builder()
                .setWorld(client.level)
                .setCameraPos(parent.position())
                .setRenderDistance(client.options.getEffectiveRenderDistance())
                .build();
        var camera = new NativeCamera(
                client.level,
                parent.position(),
                parent,
                parent.getViewRotationMatrix(new org.joml.Matrix4f()),
                NativeWorldRenderer.projection(client.gameRenderer),
                null,
                client.getWindow().getWidth(),
                client.getWindow().getHeight(),
                256,
                com.mojang.blaze3d.systems.RenderSystem.getDevice()
                        .getDeviceInfo().isZZeroToOne());
        require(camera.leftVector().dot(parent.leftVector()) > .999f,
                "Native camera reversed the vanilla left axis");
        var sky = camera.attributeProbe().getValue(
                net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR, 1);
        require(!sky.equals(net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR.defaultValue()),
                "The native Overworld camera kept the default sky environment");
        System.out.println("PORTAL_RENDER_ENVIRONMENT_OK sky=" + Integer.toHexString(sky));
        qouteall.imm_ptl.core.render.context_management.WorldRenderInfo.pushRenderInfo(info);
        try {
            client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            IPGlobal.renderYourselfInPortal = false;
            require(!camera.isDetached(), "Disabled first-person player policy was ignored");
            IPGlobal.renderYourselfInPortal = true;
            require(camera.isDetached(), "Enabled first-person portal player was hidden");
            client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            IPGlobal.renderYourselfInPortal = false;
            require(camera.isDetached(), "Third-person portal player was hidden");
            System.out.println("PORTAL_RENDER_PLAYER_VISIBILITY_OK");
        } finally {
            qouteall.imm_ptl.core.render.context_management.WorldRenderInfo.popRenderInfo();
            client.options.setCameraType(previousCamera);
            IPGlobal.renderYourselfInPortal = previousRenderPlayer;
        }
    }

    static void tick(Minecraft client, int realTicks) {
        if (firstFrame < 0) firstFrame = RenderStates.frameIndex;
        require(realTicks < 2400, "Rendering scenario did not finish within two minutes");
        // Shader compilation can stall frames while client ticks catch up. Wait for real
        // rendered frames as well, including the initial eye-height interpolation.
        if ((RenderStates.frameIndex - firstFrame) * 2 <= logicalTicks) return;
        int ticks = ++logicalTicks;
        if (ticks == 10) validatePlayerVisibility(client);
        if (ticks == 20) client.player.connection.sendCommand("portal_smoke_wall true");
        if (ticks == 50) capture(client, "portal-occlusion-check");
        if (ticks == 60) {
            require(
                    !PortalRenderInfo.get(center(client)).wasRenderedLastFrame(),
                    "Predicted occlusion still renders its world: "
                            + NativePortalOcclusion.statistics() + " wall="
                            + client.level.getBlockState(new BlockPos(0, 72, 2))
                            + " camera=" + client.gameRenderer.mainCamera().position()
                            + " yaw=" + client.player.getYRot());
            IPGlobal.offsetOcclusionQuery = false;
        }
        if (ticks == 80) {
            require(
                    client.level.getBlockState(new BlockPos(0, 72, 2)).is(Blocks.STONE),
                    "Occluder has not arrived");
            // Without prediction, a timed-out GPU measurement must conservatively render.
            require(
                    NativePortalOcclusion.statistics().skipped() > 0,
                    "No GPU occlusion decisions were made");
            require(
                    NativePortalOcclusion.statistics().synchronousOccluded() > 0,
                    "Non-predictive occlusion never used a current-frame result");
            capture(client, "portal-occluded");
            System.out.println("PORTAL_RENDER_OCCLUDED_OK " + NativePortalOcclusion.statistics());
            IPGlobal.offsetOcclusionQuery = true;
            client.player.connection.sendCommand("portal_smoke_wall false");
        }
        if (ticks == 120) {
            require(
                    !client.level.getBlockState(new BlockPos(0, 72, 2)).is(Blocks.STONE),
                    "Occluder was not removed");
            require(
                    PortalRenderInfo.get(center(client)).wasRenderedLastFrame(),
                    "Newly revealed portal remained culled");
            capture(client, "portal-revealed");
            System.out.println("PORTAL_RENDER_REVEALED_OK");
            IPGlobal.offsetOcclusionQuery = false;
            IPGlobal.enableDepthClampForPortalRendering = false;
            MyGameRenderer.enablePortalCaveCulling = false;
            IPGlobal.alwaysOverrideTerrainSetup = true;
        }
        if (ticks == 125) FabricExtractionSmoke.validateViews(client, center(client));
        if (ticks == 135) {
            capture(client, "portal-no-caves");
            IPCGlobal.doUseAdvancedFrustumCulling = false;
        }
        if (ticks == 145) {
            capture(client, "portal-no-culling");
            require(
                    PortalRenderInfo.get(center(client)).wasRenderedLastFrame(),
                    "Non-predictive mode lost the portal");
            System.out.println("PORTAL_RENDER_NO_PREDICTION_OK");
            IPGlobal.renderMode = IPGlobal.RenderMode.compatibility;
        }
        if (ticks == 165) {
            require(
                    IPCGlobal.renderer.getClass()
                            == (IrisInterface.invoker.isShaders()
                                    ? IrisCompatibilityPortalRenderer.class
                                    : RendererUsingFrameBuffer.class),
                    "Compatibility strategy was not selected");
            capture(client, "portal-compatibility");
            IPGlobal.renderMode = IPGlobal.RenderMode.debug;
        }
        if (ticks == 185) {
            capture(client, "portal-debug");
            IPGlobal.renderMode = IPGlobal.RenderMode.none;
        }
        if (ticks == 200) {
            require(
                    IPCGlobal.renderer instanceof RendererDummy
                            && RenderStates.getRenderedPortalNum() == 0,
                    "Disabled mode rendered a portal");
            IPGlobal.renderMode = IPGlobal.RenderMode.normal;
            IPGlobal.offsetOcclusionQuery = true;
            IPGlobal.enableDepthClampForPortalRendering = true;
            IPCGlobal.doUseAdvancedFrustumCulling = true;
            MyGameRenderer.enablePortalCaveCulling = true;
            IPGlobal.alwaysOverrideTerrainSetup = false;
            IPCGlobal.experimentalIrisPortalRenderer = IrisInterface.invoker.isShaders();
        }
        if (ticks == 230) {
            if (IrisInterface.invoker.isShaders()) {
                require(
                        IPCGlobal.renderer instanceof ExperimentalIrisPortalRenderer,
                        "Experimental Iris strategy was not selected");
                capture(client, "portal-iris-batched");
                IPCGlobal.experimentalIrisPortalRenderer = false;
                IPCGlobal.debugEnableStencilWithIris = true;
            }
        }
        if (ticks == 255) {
            if (IrisInterface.invoker.isShaders()) {
                require(
                        IPCGlobal.renderer == IPCGlobal.rendererUsingStencil,
                        "Iris texture-stencil override was ignored");
                IPCGlobal.debugEnableStencilWithIris = false;
                IrisInterface.invoker.reloadPipelines();
            } else NativeWorldRenderer.reload();
            FabricExtractionSmoke.afterReload();
        }
        if (ticks == 290) {
            FabricExtractionSmoke.validateViews(client, center(client));
            capture(client, "portal-after-reload");
            captureTransformedSources(client);
            require(
                    PortalRenderInfo.get(center(client)).wasRenderedLastFrame(),
                    "Reload lost the portal view");
            require(
                    RenderDiagnostics.tests() > 0 && RenderDiagnostics.rejected() > 0,
                    "Advanced frustum culling was not exercised");
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
                require(qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface.invoker
                                .isSodiumPresent(),
                        "Installed Sodium did not initialize its portal adapter");
                require(RenderDiagnostics.sodiumViewports() > 0,
                        "Sodium viewports were not adapted");
                System.out.println("PORTAL_RENDER_SODIUM_COMPAT_OK");
            }
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
                require(IrisInterface.invoker.isIrisPresent() && IrisInterface.invoker.isShaders(),
                        "Installed Iris did not enable the verification shaderpack");
                require(IPCGlobal.renderer instanceof IrisPortalRenderer,
                        "Iris did not restore its normal portal pipeline after reload");
                System.out.println("PORTAL_RENDER_IRIS_COMPAT_OK");
            }
            require(NativePortalOcclusion.statistics().pending() <= 128, "Readbacks are unbounded");
            System.out.println(
                    "PORTAL_RENDER_MODES_OK "
                            + RenderDiagnostics.tests()
                            + " "
                            + RenderDiagnostics.rejected()
                            + " "
                            + RenderDiagnostics.sodiumViewports());
        }
        if (ticks >= 300 && PortalWallRenderingSmoke.tick(client, ticks)) {
            System.out.println("PORTAL_RENDER_COMPLETE " + NativePortalOcclusion.statistics());
            client.stop();
        }
    }
}
