package net.oculus.gl.blending;

/**
 * Placeholder for blend mode overrides requested by shader packs. The 1.16
 * implementation supports a wide variety of configurations; for the skeleton
 * we only differentiate between default behaviour and fully disabled blending.
 */
public enum BlendModeOverride {
	DEFAULT,
	OFF
}
