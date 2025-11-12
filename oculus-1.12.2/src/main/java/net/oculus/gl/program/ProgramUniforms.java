package net.oculus.gl.program;

/**
 * Simplified placeholder for the uniform management layer. Call sites can build and update
 * uniform groups without yet touching the real OpenGL state; the full 1.16.5 behaviour will
 * be copied in once the rest of the shader pipeline is ready.
 */
public final class ProgramUniforms {
    private ProgramUniforms() {
    }

    public void update() {
        // Nothing to do until uniforms are wired up.
    }

    public static void clearActiveUniforms() {
        // Nothing to clear yet.
    }

    public static Builder builder(String name, int program) {
        return new Builder();
    }

    public static final class Builder {
        public ProgramUniforms build() {
            return new ProgramUniforms();
        }
    }
}
