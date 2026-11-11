package qouteall.imm_ptl.core.compat.sodium_compatibility;

/** Immutable per-view state, safe to consume from Sodium's asynchronous cull jobs. */
public interface PortalViewport {
    boolean ip_allowCaveCulling();
}
