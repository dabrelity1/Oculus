package net.oculus.shaderpack;

/**
 * Subset of the cloud rendering options supported by the modern pipeline. The
 * 1.12.2 build currently forwards everything to vanilla behaviour.
 */
public enum CloudSetting {
    DEFAULT,
    OFF,
    FAST,
    FANCY;
}
