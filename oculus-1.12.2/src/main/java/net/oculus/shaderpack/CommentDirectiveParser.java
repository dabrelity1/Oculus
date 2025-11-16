package net.oculus.shaderpack;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses OptiFine comment directives such as {@code /* DRAWBUFFERS:01234 *\/}.
 */
public final class CommentDirectiveParser {
    private static final Pattern DIRECTIVE_PATTERN = Pattern.compile("/\\*\\s*(DRAWBUFFERS|RENDERTARGETS)\\s*:(.*?)\\s*\\*/");

    private CommentDirectiveParser() {
    }

    public static Optional<CommentDirective> findDirective(String fragment, CommentDirective.Type type) {
        return findDirective(fragment, type == null ? null : type.name());
    }

    public static Optional<CommentDirective> findDirective(String fragment, String directiveType) {
        if (fragment == null || fragment.isEmpty()) {
            return Optional.empty();
        }

        Matcher matcher = DIRECTIVE_PATTERN.matcher(fragment);
        while (matcher.find()) {
            String type = matcher.group(1);
            if (directiveType == null || directiveType.equals(type)) {
                String directive = matcher.group(2).trim();
                return Optional.of(new CommentDirective(CommentDirective.Type.valueOf(type), directive, matcher.start()));
            }
        }

        return Optional.empty();
    }
}
