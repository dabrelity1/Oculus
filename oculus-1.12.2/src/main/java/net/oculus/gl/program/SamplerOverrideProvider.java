package net.oculus.gl.program;

/**
 * Supplies sampler override maps for individual shader programs. The modern
 * Iris pipeline derives program-specific overrides from pack metadata; the
 * 1.12.2 port keeps the same hook so that data can be threaded through once
 * directive parsing is implemented.
 */
@FunctionalInterface
public interface SamplerOverrideProvider {
    SamplerOverrideMap overridesFor(String programName);

    SamplerOverrideProvider NONE = programName -> SamplerOverrideMap.empty();
}
