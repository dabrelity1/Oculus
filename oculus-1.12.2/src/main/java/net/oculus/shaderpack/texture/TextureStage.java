package net.oculus.shaderpack.texture;

import java.util.Optional;

/**
 * Enumeration of OptiFine/Iris texture directive stages. Shader packs can
 * override sampler bindings on a per-stage basis using keys such as
 * {@code texture.gbuffers.colortex0}. The 1.12.2 port mirrors the modern
 * values so that pack metadata can be reused verbatim.
 */
public enum TextureStage {
    /** Shadow composite passes. */
    SHADOWCOMP,
    /** Prepare passes. */
    PREPARE,
    /** All gbuffers passes plus the primary shadow pass. */
    GBUFFERS_AND_SHADOW,
    /** Deferred passes. */
    DEFERRED,
    /** Composite and final passes. */
    COMPOSITE_AND_FINAL;

    public static Optional<TextureStage> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }

        switch (name) {
            case "shadowcomp":
                return Optional.of(SHADOWCOMP);
            case "prepare":
                return Optional.of(PREPARE);
            case "gbuffers":
                return Optional.of(GBUFFERS_AND_SHADOW);
            case "deferred":
                return Optional.of(DEFERRED);
            case "composite":
                return Optional.of(COMPOSITE_AND_FINAL);
            default:
                return Optional.empty();
        }
    }
}
