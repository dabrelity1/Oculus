package net.oculus.pipeline;

/**
 * Flags describing special rendering cases (entity eyes, beacon beams, glint, etc.).
 * Only the enum shape is needed for now; behaviour will be ported alongside the
 * full rendering pipeline.
 */
public enum SpecialCondition {
    ENTITY_EYES,
    BEACON_BEAM,
    GLINT
}
