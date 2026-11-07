package validation;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import qouteall.imm_ptl.core.miscellaneous.IPortalInitialScreen;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Exercises native startup registration and the persisted four-page acknowledgement. */
final class PortalOnboardingSmoke {
    static void validate(Minecraft client) {
        IPConfig config = IPConfig.getConfig();
        boolean previousAcknowledgement = config.initialScreenShown;
        Screen previousScreen = client.gui.screen();
        try {
            config.initialScreenShown = false;
            AtomicInteger closed = new AtomicInteger();
            var screens = initialScreens(client,
                    () -> {
                        closed.incrementAndGet();
                        client.gui.setScreen(previousScreen);
                    });
            if (screens.size() != 1)
                throw new AssertionError("Expected one portal onboarding screen, got " + screens.size());
            IPortalInitialScreen screen = screens.getFirst();
            client.gui.setScreen(screen);
            assertPage(screen, 1);
            if (button(screen, "iportal.initial_screen.prev").visible)
                throw new AssertionError("Previous-page button is visible on the first page");
            press(button(screen, "iportal.initial_screen.i_know"));
            assertPage(screen, 2);
            press(button(screen, "iportal.initial_screen.prev"));
            assertPage(screen, 1);
            press(button(screen, "iportal.initial_screen.i_know"));
            screen.resize(Math.max(320, screen.width - 40), Math.max(240, screen.height - 30));
            assertPage(screen, 2);
            press(button(screen, "iportal.initial_screen.i_know"));
            assertPage(screen, 3);
            press(button(screen, "iportal.initial_screen.i_know"));
            assertPage(screen, 4);
            if (config.initialScreenShown || closed.get() != 0)
                throw new AssertionError("Onboarding was acknowledged before the final page");
            press(button(screen, "iportal.initial_screen.i_know"));
            if (!config.initialScreenShown || closed.get() != 1
                    || client.gui.screen() != previousScreen)
                throw new AssertionError("Onboarding did not acknowledge and resume the startup flow");
            try (var reader = Files.newBufferedReader(
                    FabricLoader.getInstance().getConfigDir().resolve("immersive_portals.json"))) {
                if (!JsonParser.parseReader(reader).getAsJsonObject()
                        .get("initialScreenShown").getAsBoolean())
                    throw new AssertionError("Onboarding acknowledgement was not saved");
            }
            if (!initialScreens(client, () -> {}).isEmpty())
                throw new AssertionError("Acknowledged onboarding was registered again");
            System.out.println("PORTAL_SMOKE_ONBOARDING_OK");
        } catch (ReflectiveOperationException | java.io.IOException exception) {
            throw new AssertionError("Could not verify native onboarding", exception);
        } finally {
            config.initialScreenShown = previousAcknowledgement;
            config.saveConfigFile();
            client.gui.setScreen(previousScreen);
        }
    }

    private static List<IPortalInitialScreen> initialScreens(Minecraft client, Runnable onClose)
            throws ReflectiveOperationException {
        var method = client.gui.getClass().getDeclaredMethod("addInitialScreens", List.class);
        method.setAccessible(true);
        List<Function<Runnable, Screen>> factories = new ArrayList<>();
        method.invoke(client.gui, factories);
        List<IPortalInitialScreen> result = new ArrayList<>();
        for (var factory : factories) {
            if (factory.apply(onClose) instanceof IPortalInitialScreen screen) result.add(screen);
        }
        return result;
    }

    private static void assertPage(IPortalInitialScreen screen, int page) {
        if (screen.children().stream().noneMatch(widget -> widget instanceof StringWidget text
                && text.getMessage().getString().equals(page + " / 4")))
            throw new AssertionError("Onboarding did not show page " + page);
    }

    private static Button button(IPortalInitialScreen screen, String translation) {
        String label = Component.translatable(translation).getString();
        return screen.children().stream().filter(widget -> widget instanceof Button button
                        && button.getMessage().getString().equals(label))
                .map(widget -> (Button) widget).findFirst()
                .orElseThrow(() -> new AssertionError("Missing onboarding button: " + translation));
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
    }
}
