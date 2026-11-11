package validation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Bounded native call-site evidence for the held-use acceptance scenarios. */
public final class PortalItemUseTrace {
    private static int traces;

    private PortalItemUseTrace() {}

    public static void capture(LivingEntity entity, String operation) {
        if (!(entity instanceof Player player)
                || !player.getGameProfile().name().equals("PortalTest")
                || !(entity.getUseItem().is(Items.BOW) || entity.getUseItem().is(Items.APPLE))
                || traces++ >= 24) return;
        System.out.println("PORTAL_ITEM_USE_NATIVE_CALL side="
                + (entity.level().isClientSide() ? "client" : "server")
                + " operation=" + operation + " playerTick=" + entity.tickCount
                + " worldTime=" + entity.level().getGameTime()
                + " dimension=" + entity.level().dimension().identifier()
                + " feet=" + entity.position() + " using=" + entity.isUsingItem()
                + " hand=" + entity.getUsedItemHand()
                + " used=" + entity.getTicksUsingItem()
                + " remaining=" + entity.getUseItemRemainingTicks()
                + " active=" + entity.getUseItem()
                + " held=" + entity.getItemInHand(entity.getUsedItemHand())
                + " callers=" + java.util.Arrays.stream(new Throwable().getStackTrace())
                        .limit(12).map(StackTraceElement::toString).toList());
    }
}
