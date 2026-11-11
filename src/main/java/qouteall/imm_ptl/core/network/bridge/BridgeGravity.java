package qouteall.imm_ptl.core.network.bridge;

import io.github.n4th4not.gravity.api.GravityIdentity;
import io.github.n4th4not.gravity.api.GravityStateCache;
import io.github.n4th4not.immersiveportals.protocol.BridgeMessage;
import net.minecraft.world.entity.Entity;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;

/** Connects portal worlds and wire envelopes to the external gravity state cache. */
final class BridgeGravity {
    private static final GravityStateCache<Entity> STATES = new GravityStateCache<>(
            key -> {
                for (var world : ClientWorldLoader.getClientWorlds()) {
                    if (!world.dimension().identifier().toString().equals(key.world())) continue;
                    var entity = world.getEntity(key.id());
                    return entity != null && entity.getUUID().equals(key.uuid()) ? entity : null;
                }
                return null;
            },
            (entity, state) -> GravityChangerInterface.invoker.applyState(entity, state, false));

    static void receive(BridgeMessage.Gravity message) {
        if (GravityChangerInterface.invoker.isGravityChangerPresent())
            STATES.receive(
                    new GravityIdentity(message.world(), message.entityId(), message.entity()),
                    message.state());
    }
    static void tick() { STATES.tick(); }
    static void clear() {
        STATES.clear((entity, state) -> GravityChangerInterface.invoker.applyState(entity, state, true));
    }
}
