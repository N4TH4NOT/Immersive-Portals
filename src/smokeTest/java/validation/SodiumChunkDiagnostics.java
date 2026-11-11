package validation;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.PalettedContainerRO;

import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.render.NativeWorldRenderer;

import java.util.Map;

/** Read-only snapshots of Sodium's per-view mesh and cloned-block state. */
final class SodiumChunkDiagnostics {
    private SodiumChunkDiagnostics() {}

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    static void dump(String phase) {
        if (!SodiumInterface.invoker.isSodiumPresent()) return;
        try {
            var scenesField = NativeWorldRenderer.class.getDeclaredField("SCENES");
            scenesField.setAccessible(true);
            var scenes = (Map<?, ?>) scenesField.get(null);
            for (var entry : scenes.entrySet()) {
                var scene = (NativeWorldRenderer.Scene) entry.getValue();
                if (scene.world == null || scene.world.dimension() != Level.NETHER) continue;
                Object renderer = scene.renderer.getClass()
                        .getMethod("sodium$getWorldRenderer").invoke(scene.renderer);
                Object manager = field(renderer, "renderSectionManager");
                if (manager == null) continue;
                var getSection = manager.getClass()
                        .getDeclaredMethod("getRenderSection", int.class, int.class, int.class);
                getSection.setAccessible(true);
                Object section = getSection.invoke(manager, 0, 4, -1);
                Object cache = field(manager, "sectionCache");
                Object cloned = ((Map<?, ?>) field(cache, "positionToEntry"))
                        .get(SectionPos.asLong(0, 4, -1));
                Object cachedBlock = cloned == null ? "absent"
                        : ((PalettedContainerRO<?>) cloned.getClass()
                                .getMethod("getBlockData").invoke(cloned)).get(0, 8, 12);
                System.out.println("PORTAL_RENDER_SODIUM_SECTION phase=" + phase
                        + " scene=" + entry.getKey()
                        + " world=" + System.identityHashCode(scene.world)
                        + " extractor=" + System.identityHashCode(scene.extractor)
                        + " renderer=" + System.identityHashCode(scene.renderer)
                        + " cachedBlock=" + cachedBlock
                        + " cacheWorld=" + System.identityHashCode(field(cache, "level"))
                        + " section=" + section
                        + (section == null ? "" : " built=" + section.getClass()
                                .getMethod("isBuilt").invoke(section)
                                + " pending=" + section.getClass()
                                .getMethod("getPendingUpdate").invoke(section)
                                + " submitted=" + field(section, "frameOfBuildSubmit")
                                + " jobs=" + field(section, "runningJobs")));
            }
        } catch (ReflectiveOperationException exception) {
            System.out.println("PORTAL_RENDER_SODIUM_SECTION_DIAGNOSTIC_ERROR " + exception);
        }
    }
}
