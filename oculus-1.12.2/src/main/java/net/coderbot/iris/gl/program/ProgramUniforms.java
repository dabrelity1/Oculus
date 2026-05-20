package net.coderbot.iris.gl.program;

/**
 * Placeholder uniform manager. The real implementation will arrive alongside the higher level
 * rendering graph, but we keep the API surface so other classes can already depend on it.
 */
final class ProgramUniforms {
    private static final ProgramUniforms NO_OP = new ProgramUniforms();

    static ProgramUniforms noop() {
        return NO_OP;
    }

    void update() {
        // Intentionally left blank for the initial backport stage.
    }

    static void clearActiveUniforms() {
        // Nothing to reset yet.
    }
}
