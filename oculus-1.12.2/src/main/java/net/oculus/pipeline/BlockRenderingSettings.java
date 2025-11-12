package net.oculus.pipeline;

/**
 * Simplified version of the block rendering settings helper from the 1.16.5 branch. The
 * shader pipeline uses this to trigger buffer rebuilds when shader state changes.
 */
public final class BlockRenderingSettings {
    public static final BlockRenderingSettings INSTANCE = new BlockRenderingSettings();

    private boolean reloadRequired;

    private BlockRenderingSettings() {
    }

    public boolean isReloadRequired() {
        return reloadRequired;
    }

    public void markReloadRequired() {
        this.reloadRequired = true;
    }

    public void clearReloadRequired() {
        this.reloadRequired = false;
    }
}
