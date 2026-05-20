package net.oculus.shaderpack;

import java.util.Optional;

/**
 * Parses OptiFine comment directives such as {@code /* DRAWBUFFERS:01234 *\/}.
 */
public final class CommentDirectiveParser {
    private CommentDirectiveParser() {
    }

    public static Optional<CommentDirective> findDirective(String fragment, CommentDirective.Type type) {
        return findDirective(fragment, type == null ? null : type.name());
    }

    public static Optional<CommentDirective> findDirective(String fragment, String directiveType) {
        if (fragment == null || fragment.isEmpty()) {
            return Optional.empty();
        }

        if (directiveType == null) {
            Optional<CommentDirective> drawBuffers = findDirective(fragment, CommentDirective.Type.DRAWBUFFERS);
            Optional<CommentDirective> renderTargets = findDirective(fragment, CommentDirective.Type.RENDERTARGETS);
            if (drawBuffers.isPresent() && renderTargets.isPresent()) {
                return drawBuffers.get().getLocation() > renderTargets.get().getLocation()
                    ? drawBuffers
                    : renderTargets;
            }
            return drawBuffers.isPresent() ? drawBuffers : renderTargets;
        }

        String prefix = directiveType + ":";
        int prefixIndex = fragment.lastIndexOf(prefix);
        if (prefixIndex < 0) {
            return Optional.empty();
        }

        String before = fragment.substring(0, prefixIndex).trim();
        if (!before.endsWith("/*")) {
            return Optional.empty();
        }

        String afterPrefix = fragment.substring(prefixIndex + prefix.length());
        int suffixIndex = afterPrefix.indexOf("*/");
        if (suffixIndex < 0) {
            return Optional.empty();
        }

        String directive = afterPrefix.substring(0, suffixIndex).trim();
        return Optional.of(new CommentDirective(CommentDirective.Type.valueOf(directiveType), directive, prefixIndex));
    }
}
