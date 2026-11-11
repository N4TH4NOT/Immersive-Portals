package qouteall.imm_ptl.core.compat.gravity;

import io.github.n4th4not.gravity.fabric.GravityAdapter;
import io.github.n4th4not.gravity.fabric.GravityPlatform;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;

/** Retained for compatibility. Implementation is owned by immersive-portals-gravity. */
public final class OnGravityChangerPresent extends GravityChangerInterface.Invoker {
    @Override
    protected GravityAdapter adapter() { return GravityPlatform.adapter(); }
}
