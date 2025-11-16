package net.oculus.sodium.extensions;

import net.oculus.pipeline.BlockContextHolder;

/**
 * Bridge interface injected onto Sodium's chunk buffer builder so the pipeline
 * can retrieve the context holder without referencing mixin-only packages.
 */
public interface IChunkBuildBuffers {
    BlockContextHolder oculus_getContextHolder();
}
