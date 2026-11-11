package qouteall.imm_ptl.core.portal.custom_portal_gen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Multimap;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

class CustomPortalGenManagerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reversibleRulesRegisterBothDirectionsForEveryTrigger() throws Exception {
        for (PortalGenTrigger trigger : triggers()) {
            var reverse = new Generation(trigger, false, true);
            var forward = new Generation(trigger, true, true);
            forward.reverse = reverse;
            var manager = register(forward);
            assertEquals(2, registered(manager, trigger).size());
            assertTrue(registered(manager, trigger).contains(reverse));
            assertEquals(forward.identifier, reverse.identifier);
        }
    }

    @Test
    void oneWayRulesAndInvalidReverseDimensionsDoNotRegisterAReverse() throws Exception {
        for (PortalGenTrigger trigger : triggers()) {
            var forward = new Generation(trigger, false, true);
            forward.reverse = new Generation(trigger, false, true);
            assertEquals(List.of(forward), List.copyOf(registered(register(forward), trigger)));

            forward = new Generation(trigger, true, true);
            forward.reverse = new Generation(trigger, false, false);
            assertEquals(List.of(forward), List.copyOf(registered(register(forward), trigger)));
        }
    }

    private static List<PortalGenTrigger> triggers() {
        return List.of(new PortalGenTrigger.UseItemTrigger(Items.FLINT_AND_STEEL, false),
                new PortalGenTrigger.ThrowItemTrigger(Items.DIAMOND),
                new PortalGenTrigger.ConventionalDimensionChangeTrigger());
    }

    private static CustomPortalGenManager register(Generation generation) throws Exception {
        var manager = new CustomPortalGenManager();
        var method = CustomPortalGenManager.class.getDeclaredMethod("addEntry",
                MinecraftServer.class, ResourceKey.class, CustomPortalGeneration.class);
        method.setAccessible(true);
        method.invoke(manager, null, ResourceKey.create(CustomPortalGeneration.REGISTRY_KEY,
                Identifier.fromNamespaceAndPath("test", "reversible")), generation);
        return manager;
    }

    @SuppressWarnings("unchecked")
    private static Collection<CustomPortalGeneration> registered(
            CustomPortalGenManager manager, PortalGenTrigger trigger) throws Exception {
        String fieldName = trigger instanceof PortalGenTrigger.UseItemTrigger ? "useItemGen"
                : trigger instanceof PortalGenTrigger.ThrowItemTrigger ? "throwItemGen" : "convGen";
        var field = CustomPortalGenManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(manager);
        if (value instanceof Multimap<?, ?> rules) {
            Object item = trigger instanceof PortalGenTrigger.UseItemTrigger use ? use.item
                    : ((PortalGenTrigger.ThrowItemTrigger) trigger).item;
            return ((Multimap<Object, CustomPortalGeneration>) rules).get(item);
        }
        return (Collection<CustomPortalGeneration>) value;
    }

    private static final class Generation extends CustomPortalGeneration {
        private final boolean valid;
        private CustomPortalGeneration reverse;

        private Generation(PortalGenTrigger trigger, boolean reversible, boolean valid) {
            super(List.of(Level.OVERWORLD), Level.NETHER, 1, 1, reversible, null, trigger,
                    List.of(), List.of());
            this.valid = valid;
        }

        @Override public InitializationResult initAndCheck(MinecraftServer server) {
            return valid ? new InitializationOk() : new NotLoadedBecauseOfDstDimensionInvalid(Level.NETHER);
        }
        @Override public CustomPortalGeneration getReverse() { return reverse; }
        @Override public String toString() { return "test generation"; }
    }
}
