package net.oculus.shaderpack;

import java.util.Objects;
import java.util.Optional;

/**
 * Skeleton wrapper around a shader program definition. The modern Iris code
 * keeps GLSL sources, directives, and parent metadata; the 1.12 port mirrors
 * the public surface so the pipeline can be compiled incrementally.
 */
public final class ProgramSource {
    private final String name;
    private final ProgramSet parent;
    private final Optional<String> vertexSource;
    private final Optional<String> geometrySource;
    private final Optional<String> fragmentSource;
    private final boolean valid;
    private final ProgramDirectives directives;

    private ProgramSource(String name, ProgramSet parent, Optional<String> vertexSource, Optional<String> geometrySource,
                          Optional<String> fragmentSource, boolean valid, ProgramDirectives directives) {
        this.name = Objects.requireNonNull(name, "name");
        this.parent = parent;
        this.vertexSource = vertexSource;
        this.geometrySource = geometrySource;
        this.fragmentSource = fragmentSource;
        this.valid = valid;
        this.directives = directives == null ? new ProgramDirectives() : directives;
    }

    public static ProgramSource missing(String name) {
        return new ProgramSource(name, null, Optional.empty(), Optional.empty(), Optional.empty(), false, new ProgramDirectives());
    }

    public static ProgramSource create(String name, ProgramSet parent, String vertexSource, String geometrySource,
                                       String fragmentSource) {
        boolean hasVertex = vertexSource != null && !vertexSource.isEmpty();
        boolean hasFragment = fragmentSource != null && !fragmentSource.isEmpty();
        boolean isValid = hasVertex && hasFragment;
        Optional<String> vertex = hasVertex ? Optional.of(vertexSource) : Optional.empty();
        Optional<String> geometry = geometrySource == null || geometrySource.isEmpty() ? Optional.empty() : Optional.of(geometrySource);
        Optional<String> fragment = hasFragment ? Optional.of(fragmentSource) : Optional.empty();

        if (!isValid) {
            return missing(name);
        }

        return new ProgramSource(name, parent, vertex, geometry, fragment, true, new ProgramDirectives());
    }

    public String getName() {
        return name;
    }

    public ProgramSet getParent() {
        return parent;
    }

    public Optional<String> getVertexSource() {
        return vertexSource;
    }

    public Optional<String> getGeometrySource() {
        return geometrySource;
    }

    public Optional<String> getFragmentSource() {
        return fragmentSource;
    }

    public ProgramDirectives getDirectives() {
        return directives;
    }

    public boolean isValid() {
        return valid;
    }

    public Optional<ProgramSource> requireValid() {
        return valid ? Optional.of(this) : Optional.empty();
    }

    public ProgramSource withDirectiveOverride(ProgramDirectives newDirectives) {
        if (!valid) {
            return this;
        }

        return new ProgramSource(name, parent, vertexSource, geometrySource, fragmentSource, true,
            newDirectives == null ? directives : newDirectives);
    }
}
