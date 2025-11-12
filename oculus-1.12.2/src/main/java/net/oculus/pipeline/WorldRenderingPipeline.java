package net.oculus.pipeline;

/**
 * Represents a rendering pipeline capable of drawing the world with custom shaders. The
 * initial 1.12.2 port keeps these methods as no-ops so that the rest of the system can be
 * wired up incrementally.
 */
public interface WorldRenderingPipeline {
    default void beginWorldRendering(float partialTicks) {
    }

    default void endWorldRendering() {
    }

    default void destroy() {
    }
}
