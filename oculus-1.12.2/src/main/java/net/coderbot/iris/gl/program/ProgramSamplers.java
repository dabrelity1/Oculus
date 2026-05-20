package net.coderbot.iris.gl.program;

final class ProgramSamplers {
    private static final ProgramSamplers NO_OP = new ProgramSamplers();

    static ProgramSamplers noop() {
        return NO_OP;
    }

    void update() {
        // No sampler state has been ported yet.
    }

    static void clearActiveSamplers() {
        // Nothing to clear for now.
    }
}
