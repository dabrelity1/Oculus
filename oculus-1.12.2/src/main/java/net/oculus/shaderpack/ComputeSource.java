package net.oculus.shaderpack;

import java.util.Objects;
import java.util.Optional;

/**
 * Simplified representation of a compute shader entry. Compute shaders are not
 * executed in the 1.12 build yet, but the data model is kept so the pipeline
 * can be wired without large refactors.
 */
public final class ComputeSource {
    private final String name;
    private final ProgramSet parent;
    private final Optional<String> source;
    private final boolean valid;
    private boolean workGroupRelative;
    private int[] workGroups;

    private ComputeSource(String name, ProgramSet parent, Optional<String> source, boolean valid) {
        this.name = Objects.requireNonNull(name, "name");
        this.parent = parent;
        this.source = source;
        this.valid = valid;
        this.workGroups = new int[] {1, 1, 1};
    }

    public static ComputeSource create(String name, ProgramSet parent, String source) {
        boolean hasSource = source != null && !source.isEmpty();
        Optional<String> payload = hasSource ? Optional.of(source) : Optional.empty();
        return new ComputeSource(name, parent, payload, hasSource);
    }

    public static ComputeSource missing(String name) {
        return new ComputeSource(name, null, Optional.empty(), false);
    }

    public String getName() {
        return name;
    }

    public ProgramSet getParent() {
        return parent;
    }

    public Optional<String> getSource() {
        return source;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isWorkGroupRelative() {
        return workGroupRelative;
    }

    public int[] getWorkGroups() {
        return workGroups.clone();
    }

    public void setWorkGroupInfo(boolean workGroupRelative, int[] workGroups) {
        this.workGroupRelative = workGroupRelative;
        this.workGroups = workGroups == null ? new int[] {1, 1, 1} : workGroups.clone();
    }
}
