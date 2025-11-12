package net.oculus.gl.program;

/**
 * Placeholder image binding manager. Compute shaders and modern packs expect this structure,
 * but the concrete texture binding work will come later in the port.
 */
public final class ProgramImages {
    private ProgramImages() {
    }

    public void update() {
        // No image bindings yet.
    }

    public int getActiveImages() {
        return 0;
    }

    public static Builder builder(int program) {
        return new Builder();
    }

    public static final class Builder {
        public ProgramImages build() {
            return new ProgramImages();
        }
    }
}
