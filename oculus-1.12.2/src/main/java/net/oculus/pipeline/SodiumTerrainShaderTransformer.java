package net.oculus.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.oculus.gl.shader.ShaderType;

/**
 * Source-backed 1.12.2 adaptation of Oculus 1.16.5's Sodium terrain transform.
 *
 * <p>The 1.16.5 reference uses glsl-transformer AST replacements. The 1.12.2
 * port does not carry that dependency, so this class performs equivalent
 * identifier and fixed expression replacements while skipping comments, strings,
 * character literals, and preprocessor lines.</p>
 */
public final class SodiumTerrainShaderTransformer {
    private static final String INTERNAL_INTERFACE_ERROR_PREFIX =
        "Detected a potential reference to unstable and internal Iris shader interfaces (iris_ and irisMain). "
            + "This isn't currently supported. Violation: ";
    private static final String INTERNAL_INTERFACE_ERROR_SUFFIX = ". See debugging.md for more information.";
    private static final String MISSING_VERSION_ERROR =
        "No #version directive found in source code! See debugging.md for more information.";

    private SodiumTerrainShaderTransformer() {
    }

    public static String transform(ShaderType type, String source) {
        if (source == null || type == null) {
            return source;
        }

        requireVersionDirective(source);
        rejectInternalInterfaceReferences(source);

        if (type == ShaderType.VERTEX) {
            return transformVertex(source);
        }
        if (type == ShaderType.GEOMETRY || type == ShaderType.FRAGMENT) {
            return transformShared(source);
        }
        throw new IllegalStateException("Unexpected Sodium terrain patching shader type: " + type);
    }

    private static void requireVersionDirective(String source) {
        for (int i = 0; i < source.length();) {
            int lineStart = i;
            int tokenStart = skipHorizontalWhitespace(source, lineStart);
            if (tokenStart < source.length() && source.startsWith("#version", tokenStart)) {
                int tokenEnd = tokenStart + "#version".length();
                if (tokenEnd >= source.length() || !isIdentifierPart(source.charAt(tokenEnd))) {
                    int versionStart = skipHorizontalWhitespace(source, tokenEnd);
                    if (versionStart < source.length() && Character.isDigit(source.charAt(versionStart))) {
                        return;
                    }
                }
            }
            int nextLine = lineEndIncludingBreak(source, lineStart);
            if (nextLine <= i) {
                break;
            }
            i = nextLine;
        }
        throw new IllegalArgumentException(MISSING_VERSION_ERROR);
    }

    private static void rejectInternalInterfaceReferences(String source) {
        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            if (!isIdentifierStart(source.charAt(i))) {
                i++;
                continue;
            }

            int end = i + 1;
            while (end < source.length() && isIdentifierPart(source.charAt(end))) {
                end++;
            }

            String identifier = source.substring(i, end);
            if (identifier.startsWith("iris_") || identifier.startsWith("irisMain")) {
                throw new IllegalStateException(INTERNAL_INTERFACE_ERROR_PREFIX + identifier
                    + INTERNAL_INTERFACE_ERROR_SUFFIX);
            }
            i = end;
        }
    }

    private static String transformVertex(String source) {
        String patched = injectDeclarations(source,
            "attribute vec3 iris_Pos;",
            "attribute vec4 iris_Color;",
            "attribute vec2 iris_TexCoord;",
            "attribute vec2 iris_LightCoord;",
            "attribute vec3 iris_Normal;",
            "uniform vec3 u_ModelScale;",
            "uniform vec2 u_TextureScale;",
            "attribute vec4 iris_ModelOffset;",
            "vec4 iris_LightTexCoord = vec4(iris_LightCoord, 0, 1);",
            "vec4 iris_ftransform() { return u_ModelViewProjectionMatrix * vec4((iris_Pos * u_ModelScale) "
                + "+ iris_ModelOffset.xyz, 1.0); }");
        patched = replaceModelViewVertexSandwich(patched);
        patched = transformShared(patched);

        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("gl_Vertex", "vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0)");
        replacements.put("gl_MultiTexCoord0", "vec4(iris_TexCoord * u_TextureScale, 0.0, 1.0)");
        replacements.put("gl_MultiTexCoord1", "iris_LightTexCoord");
        replacements.put("gl_MultiTexCoord2", "iris_LightTexCoord");
        replacements.put("gl_Color", "iris_Color");
        replacements.put("gl_Normal", "iris_Normal");
        replacements.put("ftransform", "iris_ftransform");
        return replaceIdentifiers(patched, replacements);
    }

    private static String transformShared(String source) {
        String patched = injectDeclarations(source,
            "uniform mat4 iris_ModelViewMatrix;",
            "uniform mat4 iris_ProjectionMatrix;",
            "uniform mat4 u_ModelViewProjectionMatrix;",
            "uniform mat4 iris_NormalMatrix;",
            "uniform mat4 iris_LightmapTextureMatrix;");

        patched = replaceTextureMatrix(patched, 0, "mat4(1.0)");
        patched = replaceTextureMatrix(patched, 1, "iris_LightmapTextureMatrix");

        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        replacements.put("gl_ProjectionMatrix", "iris_ProjectionMatrix");
        replacements.put("gl_ModelViewProjectionMatrix", "u_ModelViewProjectionMatrix");
        replacements.put("gl_NormalMatrix", "mat3(iris_NormalMatrix)");
        return replaceIdentifiers(patched, replacements);
    }

    private static String replaceModelViewVertexSandwich(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                builder.append(source, i, skipped);
                i = skipped;
                continue;
            }

            int matchEnd = isIdentifierStart(source.charAt(i))
                ? modelViewVertexSandwichMatchEnd(source, i)
                : -1;
            if (matchEnd > i) {
                builder.append("gl_Vertex");
                i = matchEnd;
                continue;
            }

            builder.append(source.charAt(i));
            i++;
        }
        return builder.toString();
    }

    private static String injectDeclarations(String source, String... declarations) {
        List<String> missing = new ArrayList<>();
        for (String declaration : declarations) {
            missing.add(declaration);
        }

        if (missing.isEmpty()) {
            return source;
        }

        int declarationLength = 0;
        for (String declaration : missing) {
            declarationLength += declaration.length() + 1;
        }

        int insertIndex = findDeclarationInsertIndex(source);
        StringBuilder builder = new StringBuilder(source.length() + declarationLength + 1);
        builder.append(source, 0, insertIndex);
        if (insertIndex > 0 && !endsWithLineBreak(builder)) {
            builder.append('\n');
        }
        appendDeclarations(builder, missing);
        builder.append(source.substring(insertIndex));
        return builder.toString();
    }

    private static int findDeclarationInsertIndex(String source) {
        int index = 0;
        while (index < source.length()) {
            int lineStart = index;
            int tokenStart = skipHorizontalWhitespace(source, index);
            if (tokenStart >= source.length()) {
                return source.length();
            }

            char ch = source.charAt(tokenStart);
            if (isLineBreak(ch)) {
                index = skipLineBreak(source, tokenStart);
                continue;
            }

            if (isPreamblePreprocessorLine(source, tokenStart)) {
                index = lineEndIncludingBreak(source, tokenStart + 1);
                continue;
            }

            return lineStart;
        }
        return source.length();
    }

    private static boolean isPreamblePreprocessorLine(String source, int tokenStart) {
        return source.startsWith("#version", tokenStart)
            || source.startsWith("#extension", tokenStart)
            || source.startsWith("#define", tokenStart)
            || source.startsWith("#include", tokenStart)
            || source.startsWith("#line", tokenStart);
    }

    private static int skipHorizontalWhitespace(String source, int index) {
        int cursor = index;
        while (cursor < source.length()) {
            char ch = source.charAt(cursor);
            if (ch != ' ' && ch != '\t' && ch != '\f') {
                break;
            }
            cursor++;
        }
        return cursor;
    }

    private static int lineEndIncludingBreak(String source, int index) {
        int cursor = index;
        while (cursor < source.length() && !isLineBreak(source.charAt(cursor))) {
            cursor++;
        }
        return skipLineBreak(source, cursor);
    }

    private static int skipLineBreak(String source, int index) {
        if (index >= source.length()) {
            return index;
        }
        if (source.charAt(index) == '\r' && index + 1 < source.length() && source.charAt(index + 1) == '\n') {
            return index + 2;
        }
        return isLineBreak(source.charAt(index)) ? index + 1 : index;
    }

    private static boolean isLineBreak(char ch) {
        return ch == '\n' || ch == '\r';
    }

    private static boolean endsWithLineBreak(StringBuilder builder) {
        if (builder.length() == 0) {
            return true;
        }
        char ch = builder.charAt(builder.length() - 1);
        return ch == '\n' || ch == '\r';
    }

    private static void appendDeclarations(StringBuilder builder, List<String> declarations) {
        for (String declaration : declarations) {
            builder.append(declaration).append('\n');
        }
    }

    private static String replaceIdentifiers(String source, Map<String, String> replacements) {
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                builder.append(source, i, skipped);
                i = skipped;
                continue;
            }

            if (isIdentifierStart(source.charAt(i))) {
                int end = i + 1;
                while (end < source.length() && isIdentifierPart(source.charAt(end))) {
                    end++;
                }

                String identifier = source.substring(i, end);
                String replacement = replacements.get(identifier);
                builder.append(replacement == null ? identifier : replacement);
                i = end;
                continue;
            }

            builder.append(source.charAt(i));
            i++;
        }
        return builder.toString();
    }

    private static String replaceTextureMatrix(String source, int index, String replacement) {
        StringBuilder builder = new StringBuilder(source.length());
        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                builder.append(source, i, skipped);
                i = skipped;
                continue;
            }

            int matchEnd = textureMatrixMatchEnd(source, i, index);
            if (matchEnd > i) {
                builder.append(replacement);
                i = matchEnd;
                continue;
            }

            builder.append(source.charAt(i));
            i++;
        }
        return builder.toString();
    }

    private static int textureMatrixMatchEnd(String source, int index, int matrixIndex) {
        String token = "gl_TextureMatrix";
        if (!source.startsWith(token, index)
            || !isIdentifierBoundary(source, index - 1)
            || !isIdentifierBoundary(source, index + token.length())) {
            return -1;
        }

        int cursor = skipWhitespace(source, index + token.length());
        if (cursor >= source.length() || source.charAt(cursor) != '[') {
            return -1;
        }
        cursor = skipWhitespace(source, cursor + 1);
        if (cursor >= source.length() || source.charAt(cursor) != (char) ('0' + matrixIndex)) {
            return -1;
        }
        cursor = skipWhitespace(source, cursor + 1);
        if (cursor >= source.length() || source.charAt(cursor) != ']') {
            return -1;
        }
        return cursor + 1;
    }

    private static int modelViewVertexSandwichMatchEnd(String source, int index) {
        int cursor = matchIdentifier(source, index, "gbufferModelViewInverse");
        if (cursor < 0) {
            return -1;
        }
        cursor = matchOperator(source, cursor, '*');
        if (cursor < 0) {
            return -1;
        }
        cursor = matchIdentifier(source, cursor, "gl_ModelViewMatrix");
        if (cursor < 0) {
            return -1;
        }
        cursor = matchOperator(source, cursor, '*');
        if (cursor < 0) {
            return -1;
        }
        return matchIdentifier(source, cursor, "gl_Vertex");
    }

    private static int matchIdentifier(String source, int index, String identifier) {
        int cursor = skipWhitespace(source, index);
        if (!source.startsWith(identifier, cursor)
            || !isIdentifierBoundary(source, cursor - 1)
            || !isIdentifierBoundary(source, cursor + identifier.length())) {
            return -1;
        }
        return cursor + identifier.length();
    }

    private static int matchOperator(String source, int index, char operator) {
        int cursor = skipWhitespace(source, index);
        if (cursor >= source.length() || source.charAt(cursor) != operator) {
            return -1;
        }
        return cursor + 1;
    }

    private static int skipWhitespace(String source, int index) {
        int cursor = index;
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int skipNonCodeToken(String source, int index) {
        char ch = source.charAt(index);
        if (ch == '/' && index + 1 < source.length()) {
            char next = source.charAt(index + 1);
            if (next == '/') {
                int end = source.indexOf('\n', index + 2);
                return end < 0 ? source.length() : end;
            }
            if (next == '*') {
                int end = source.indexOf("*/", index + 2);
                return end < 0 ? source.length() : end + 2;
            }
        }

        if (ch == '"' || ch == '\'') {
            char quote = ch;
            int cursor = index + 1;
            boolean escaped = false;
            while (cursor < source.length()) {
                char current = source.charAt(cursor++);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == quote) {
                    break;
                }
            }
            return cursor;
        }

        if (ch == '#' && isLineStart(source, index)) {
            int end = source.indexOf('\n', index + 1);
            return end < 0 ? source.length() : end;
        }

        return index;
    }

    private static boolean isLineStart(String source, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char ch = source.charAt(i);
            if (ch == '\n' || ch == '\r') {
                return true;
            }
            if (!Character.isWhitespace(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifierStart(char ch) {
        return ch == '_' || Character.isLetter(ch);
    }

    private static boolean isIdentifierPart(char ch) {
        return ch == '_' || Character.isLetterOrDigit(ch);
    }

    private static boolean isIdentifierBoundary(String source, int index) {
        return index < 0 || index >= source.length() || !isIdentifierPart(source.charAt(index));
    }
}
