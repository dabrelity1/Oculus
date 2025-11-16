package net.oculus.shaderpack.materialmap;

import java.util.Objects;

public final class NamespacedId {
    private final String namespace;
    private final String name;

    public NamespacedId(String combined) {
        int colon = combined.indexOf(':');
        if (colon == -1) {
            this.namespace = "minecraft";
            this.name = combined;
        } else {
            this.namespace = combined.substring(0, colon);
            this.name = combined.substring(colon + 1);
        }
    }

    public NamespacedId(String namespace, String name) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NamespacedId)) {
            return false;
        }
        NamespacedId that = (NamespacedId) o;
        return namespace.equals(that.namespace) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name);
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }
}
