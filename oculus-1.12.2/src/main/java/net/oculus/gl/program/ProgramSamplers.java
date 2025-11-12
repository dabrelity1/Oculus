package net.oculus.gl.program;

/**
 * Stubbed sampler manager. The upstream implementation tracks texture unit assignments, but
 * the 1.12.2 port only needs to keep the API alive for now.
 */
public final class ProgramSamplers {
    private ProgramSamplers() {
    }

    public void update() {
        // Texture unit updates will be implemented during the full port.
    }

    public static void clearActiveSamplers() {
        // No state yet.
    }

    public int getActiveSamplers() {
        return 0;
    }

    public static Builder builder(int program) {
        return new Builder();
    }

    public static final class Builder {
        public ProgramSamplers build() {
            return new ProgramSamplers();
        }
    }
}
