package qouteall.imm_ptl.core.render.optimization;

/** Never hide a portal using an unready, stale, reordered or geometrically unrelated GPU result. */
public final class PortalVisibilityHistory {
    private long submittedFrame = -1;
    private long completedFrame = -1;
    private long completedSignature;
    private boolean visible = true;

    public boolean begin(long frame) {
        if (submittedFrame == frame) return false;
        submittedFrame = frame;
        return true;
    }

    public void complete(long frame, long signature, boolean visible) {
        if (frame < completedFrame) return;
        completedFrame = frame;
        completedSignature = signature;
        this.visible = visible;
    }

    public boolean shouldRender(long frame, long signature, boolean prediction) {
        long age = frame - completedFrame;
        return completedFrame < 0
                || completedSignature != signature
                || age < 0
                || age > (prediction ? 2 : 0)
                || visible;
    }

    @Override
    public String toString() {
        return "Visibility[submitted="
                + submittedFrame
                + ", completed="
                + completedFrame
                + ", signature="
                + completedSignature
                + ", visible="
                + visible
                + "]";
    }
}
