package net.oculus.gl.blending;

import java.util.Objects;

/**
 * Carries blend override requests parsed from shaders.properties. The actual
 * GL plumbing will be hooked up once the deferred renderer is active, but
 * retaining this metadata ensures parity with the upstream Iris pipeline.
 */
public final class BlendModeOverride {
	public static final BlendModeOverride DEFAULT = new BlendModeOverride(null, false);
	public static final BlendModeOverride OFF = new BlendModeOverride(null, true);

	private final BlendMode blendMode;
	private final boolean disabled;

	private BlendModeOverride(BlendMode blendMode, boolean disabled) {
		this.blendMode = blendMode;
		this.disabled = disabled;
	}

	public static BlendModeOverride of(BlendMode blendMode) {
		Objects.requireNonNull(blendMode, "blendMode");
		return new BlendModeOverride(blendMode, false);
	}

	public boolean isDisabled() {
		return disabled;
	}

	public boolean hasCustomBlendMode() {
		return blendMode != null;
	}

	public BlendMode getBlendMode() {
		return blendMode;
	}
}
