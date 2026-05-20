package net.oculus.pipeline;

import java.util.Objects;

/**
 * Minimal analogue of the Iris {@code NamespacedId}. Acts as a lightweight key for
 * per-dimension resources managed by the {@link PipelineManager}.
 */
public final class NamespacedId {
    private final String namespace;
    private final String path;

    private static final NamespacedId OVERWORLD = new NamespacedId("minecraft", "overworld");
    private static final NamespacedId NETHER = new NamespacedId("minecraft", "the_nether");
    private static final NamespacedId END = new NamespacedId("minecraft", "the_end");
    private static final NamespacedId WILDCARD = new NamespacedId("*", "*");

    public NamespacedId(String combined) {
        int colon = combined.indexOf(':');
        if (colon < 0) {
            this.namespace = "minecraft";
            this.path = combined;
        } else {
            this.namespace = combined.substring(0, colon);
            this.path = combined.substring(colon + 1);
        }
    }

    private NamespacedId(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static NamespacedId of(String namespace, String path) {
        return new NamespacedId(namespace, path);
    }

    public static NamespacedId overworld() {
        return OVERWORLD;
    }

    public static NamespacedId nether() {
        return NETHER;
    }

    public static NamespacedId end() {
        return END;
    }

    public static NamespacedId wildcard() {
        return WILDCARD;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof NamespacedId)) {
            return false;
        }

        NamespacedId other = (NamespacedId) obj;
        return namespace.equals(other.namespace) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
