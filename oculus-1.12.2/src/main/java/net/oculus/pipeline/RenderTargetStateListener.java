package net.oculus.pipeline;

/**
 * Minimal stub mirroring Iris's listener so framebuffer overrides can plug into the
 * rendering pipeline once the full backport lands.
 */
public interface RenderTargetStateListener {
    RenderTargetStateListener NOP = new RenderTargetStateListener() {
        @Override
        public void beginPostChain() {
        }

        @Override
        public void endPostChain() {
        }

        @Override
        public void setIsMainBound(boolean bound) {
        }
    };

    void beginPostChain();
    void endPostChain();
    void setIsMainBound(boolean bound);
}
