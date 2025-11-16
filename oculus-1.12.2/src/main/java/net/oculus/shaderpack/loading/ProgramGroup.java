package net.oculus.shaderpack.loading;

/**
 * Grouping used to derive shader program base names.
 */
public enum ProgramGroup {
    Shadow("shadow"),
    ShadowComposite("shadowcomp"),
    Prepare("prepare"),
    Gbuffers("gbuffers"),
    Deferred("deferred"),
    Composite("composite"),
    Final("final");

    private final String baseName;

    ProgramGroup(String baseName) {
        this.baseName = baseName;
    }

    public String getBaseName() {
        return baseName;
    }
}
