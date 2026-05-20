package net.oculus.gl.blending;

import java.util.Objects;

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

	public void apply() {
		if (disabled) {
			BlendModeStorage.overrideBlend(null);
			return;
		}

		if (blendMode != null) {
			BlendModeStorage.overrideBlend(blendMode);
		}
	}

	public static void restore() {
		BlendModeStorage.restoreBlend();
	}
}
