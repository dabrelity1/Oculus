package net.oculus.pipeline.gterrain;

import net.oculus.pipeline.framebuffer.FramebufferManager;
import net.oculus.shaderpack.PackDirectives;

/**
 * Placeholder for the shared terrain framebuffer bundle. On the modern codebase
 * these buffers back the Sodium terrain renderer; the port keeps the object so
 * dependent classes can wire themselves together.
 */
public final class GlobalTerrainFramebuffers {
    private final FramebufferManager framebufferManager;
    private final PackDirectives directives;
    private boolean initialized;

    public GlobalTerrainFramebuffers(FramebufferManager framebufferManager, PackDirectives directives) {
        this.framebufferManager = framebufferManager;
        this.directives = directives;
    }

    public void initialize() {
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void destroy() {
        initialized = false;
    }
}
