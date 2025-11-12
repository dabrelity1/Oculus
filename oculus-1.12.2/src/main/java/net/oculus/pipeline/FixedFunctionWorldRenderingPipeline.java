package net.oculus.pipeline;

/**
 * Stand-in pipeline used when shaders are disabled or fail to initialize. For now the
 * implementation simply performs no work, providing a safe baseline that mirrors the
 * fixed-function renderer from the 1.16.5 branch.
 */
public final class FixedFunctionWorldRenderingPipeline implements WorldRenderingPipeline {
}
