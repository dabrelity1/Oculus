package net.coderbot.iris.gl.program;

final class ProgramImages {
    private static final ProgramImages NO_OP = new ProgramImages();

    static ProgramImages noop() {
        return NO_OP;
    }

    void update() {
        // No image units yet; the 1.12.2 renderer will add them in a later pass.
    }

    int getActiveImages() {
        return 0;
    }
}
