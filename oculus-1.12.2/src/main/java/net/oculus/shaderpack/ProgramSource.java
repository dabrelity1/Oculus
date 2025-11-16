package net.oculus.shaderpack;

import java.util.Optional;

import net.oculus.gl.blending.BlendModeOverride;

/**
 * Port of the Iris {@code ProgramSource}. All behaviour related to directive
 * parsing and shader compilation is stubbed, but the public surface matches the
 * upstream implementation so dependent systems can be migrated verbatim.
 */
public final class ProgramSource {
    private final String name;
    private final String vertexSource;
    private final String geometrySource;
    private final String fragmentSource;
    private final ProgramDirectives directives;
    private final ProgramSet parent;

    private ProgramSource(String name, String vertexSource, String geometrySource, String fragmentSource,
                          ProgramDirectives directives, ProgramSet parent) {
        this.name = name;
        this.vertexSource = vertexSource;
        this.geometrySource = geometrySource;
        this.fragmentSource = fragmentSource;
        this.directives = directives;
        this.parent = parent;
    }

    public static ProgramSource missing(String name) {
        return new ProgramSource(name, null, null, null, new ProgramDirectives(), null);
    }

    public ProgramSource(String name, String vertexSource, String geometrySource, String fragmentSource,
                         ProgramSet parent, ShaderProperties properties, BlendModeOverride defaultBlendModeOverride) {
        this(name, vertexSource, geometrySource, fragmentSource,
        new ProgramDirectives(parent, name, fragmentSource, properties, defaultBlendModeOverride), parent);
    }

    public ProgramSource withDirectiveOverride(ProgramDirectives overrideDirectives) {
        return new ProgramSource(name, vertexSource, geometrySource, fragmentSource,
            overrideDirectives == null ? directives : overrideDirectives, parent);
    }

    public String getName() {
        return name;
    }

    public Optional<String> getVertexSource() {
        return Optional.ofNullable(vertexSource);
    }

    public Optional<String> getGeometrySource() {
        return Optional.ofNullable(geometrySource);
    }

    public Optional<String> getFragmentSource() {
        return Optional.ofNullable(fragmentSource);
    }

    public ProgramDirectives getDirectives() {
        return this.directives;
    }

    public ProgramSet getParent() {
        return parent;
    }

    public boolean isValid() {
        return vertexSource != null && fragmentSource != null;
    }

    public Optional<ProgramSource> requireValid() {
        return this.isValid() ? Optional.of(this) : Optional.empty();
    }
}
