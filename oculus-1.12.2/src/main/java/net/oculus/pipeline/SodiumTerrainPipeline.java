package net.oculus.pipeline;

/**
 * Placeholder used while the Sodium terrain integration is being backported. The
 * class only exists so that the pipeline contract can expose the same shape as
 * the 1.16.5 codebase.
 */
public final class SodiumTerrainPipeline {
    public static final SodiumTerrainPipeline NULL_PIPELINE = new SodiumTerrainPipeline();

    private SodiumTerrainPipeline() {
    }

    public boolean isInitialized() {
        return false;
    }
}
