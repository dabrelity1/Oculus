package net.oculus.shaderpack;

import java.util.Objects;

/**
 * Represents OptiFine-style comment directives such as DRAWBUFFERS and
 * RENDERTARGETS. Parsed directives include their textual payload and the
 * character offset where they were found to emulate upstream conflict rules.
 */
public final class CommentDirective {
    public enum Type {
        DRAWBUFFERS,
        RENDERTARGETS
    }

    private final Type type;
    private final String directive;
    private final int location;

    public CommentDirective(Type type, String directive, int location) {
        this.type = Objects.requireNonNull(type, "type");
        this.directive = Objects.requireNonNull(directive, "directive");
        this.location = location;
    }

    public Type getType() {
        return type;
    }

    public String getDirective() {
        return directive;
    }

    public int getLocation() {
        return location;
    }
}
