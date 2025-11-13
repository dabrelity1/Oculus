package net.oculus.shaderpack;

import java.util.Optional;

import net.oculus.vendored.joml.Vector2f;
import net.oculus.vendored.joml.Vector3i;

/**
 * Port of the Iris {@code ComputeSource}. Compute shaders are not executed in
 * the 1.12 build yet, but the data model is kept so the pipeline can be wired
 * without large refactors.
 */
public final class ComputeSource {
    private final String name;
    private final String source;
    private final ProgramSet parent;
    private Vector3i workGroups;
    private Vector2f workGroupRelative;

    public ComputeSource(String name, String source, ProgramSet parent) {
        this.name = name;
        this.source = source;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getSource() {
        return Optional.ofNullable(source);
    }

    public ProgramSet getParent() {
        return parent;
    }

    public boolean isValid() {
        return source != null;
    }

    public void setWorkGroups(Vector3i workGroups) {
        this.workGroups = workGroups;
    }

    public void setWorkGroupRelative(Vector2f workGroupRelative) {
        this.workGroupRelative = workGroupRelative;
    }

    public Vector2f getWorkGroupRelative() {
        return workGroupRelative;
    }

    public Vector3i getWorkGroups() {
        return workGroups;
    }

    public Optional<ComputeSource> requireValid() {
        return this.isValid() ? Optional.of(this) : Optional.empty();
    }
}
