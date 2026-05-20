package net.oculus.shader;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.oculus.gl.shader.ShaderType;
import net.oculus.pipeline.InputAvailability;
import net.oculus.shaderpack.StringPair;

/**
 * Applies the common source preparation used before compiling any shaderpack
 * program. Runtime renderers must use the same path as the loader so the
 * program actually executed by the pipeline receives the same compatibility
 * defines and source fixes that were validated during load.
 */
public final class ShaderSourcePreparer {
    private static final String MISSING_VERSION_ERROR =
        "No #version directive found in source code! See debugging.md for more information.";
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("^\\s*#version\\s+(\\d+)", Pattern.MULTILINE);

    private ShaderSourcePreparer() {
    }

    public static String prepare(String packName,
                                 String programName,
                                 ShaderType shaderType,
                                 String source,
                                 List<StringPair> environmentDefines) {
        return prepare(packName, programName, shaderType, source, environmentDefines, null);
    }

    public static String prepare(String packName,
                                 String programName,
                                 ShaderType shaderType,
                                 String source,
                                 List<StringPair> environmentDefines,
                                 InputAvailability availability) {
        if (source == null) {
            return null;
        }

        requireNumericVersionDirective(source, shaderType);

        List<StringPair> defines = environmentDefines == null ? Collections.emptyList() : environmentDefines;
        String withDefines = ShaderPreprocessor.applyDefines(source, shaderType, programName, defines);
        String patched = ShaderCompatibilityPatcher.patch(packName, programName, shaderType, withDefines, availability);
        return ensureShaderCompatibility(patched, shaderType);
    }

    public static PreparedProgram prepareProgram(String packName,
                                                 String programName,
                                                 String vertexSource,
                                                 String geometrySource,
                                                 String fragmentSource,
                                                 List<StringPair> environmentDefines) {
        return prepareProgram(packName, programName, vertexSource, geometrySource, fragmentSource, environmentDefines, null);
    }

    public static PreparedProgram prepareProgram(String packName,
                                                 String programName,
                                                 String vertexSource,
                                                 String geometrySource,
                                                 String fragmentSource,
                                                 List<StringPair> environmentDefines,
                                                 InputAvailability availability) {
        String preparedVertex = prepare(packName, programName, ShaderType.VERTEX, vertexSource, environmentDefines, availability);
        String preparedGeometry = prepare(packName, programName, ShaderType.GEOMETRY, geometrySource, environmentDefines, availability);
        String preparedFragment = prepare(packName, programName, ShaderType.FRAGMENT, fragmentSource, environmentDefines, availability);
        ShaderCompatibilityPatcher.PatchedShaderSources grouped = ShaderCompatibilityPatcher.patchGrouped(
            packName,
            programName,
            preparedVertex,
            preparedGeometry,
            preparedFragment,
            availability);
        return new PreparedProgram(grouped.getVertexSource(), grouped.getGeometrySource(), grouped.getFragmentSource());
    }

    private static void requireNumericVersionDirective(String source, ShaderType shaderType) {
        if (shaderType == ShaderType.COMPUTE) {
            return;
        }

        if (!VERSION_DIRECTIVE.matcher(source).find()) {
            throw new IllegalArgumentException(MISSING_VERSION_ERROR);
        }
    }

    private static String ensureShaderCompatibility(String source, ShaderType shaderType) {
        if (source == null || shaderType == null) {
            return source;
        }

        switch (shaderType) {
            case VERTEX:
            case GEOMETRY:
                return ensureGpuShader4Support(source);
            default:
                return source;
        }
    }

    private static String ensureGpuShader4Support(String source) {
        String upgraded = bumpVersionDirective(source, 130);
        return ensureExtensionEnabled(upgraded, "GL_EXT_gpu_shader4");
    }

    private static String bumpVersionDirective(String source, int minimumVersion) {
        Matcher matcher = VERSION_DIRECTIVE.matcher(source);
        if (matcher.find()) {
            int current;
            try {
                current = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                current = minimumVersion;
            }

            if (current >= minimumVersion) {
                return source;
            }

            return matcher.replaceFirst("#version " + minimumVersion);
        }

        return "#version " + minimumVersion + '\n' + source;
    }

    private static String ensureExtensionEnabled(String source, String extension) {
        if (source.contains("#extension " + extension)) {
            return source;
        }

        int versionIndex = source.indexOf("#version");
        if (versionIndex < 0) {
            return "#extension " + extension + " : enable\n" + source;
        }

        int lineEnd = source.indexOf('\n', versionIndex);
        if (lineEnd < 0) {
            return source + '\n' + "#extension " + extension + " : enable\n";
        }

        StringBuilder builder = new StringBuilder(source.length() + extension.length() + 32);
        builder.append(source, 0, lineEnd + 1)
            .append("#extension ").append(extension).append(" : enable\n")
            .append(source.substring(lineEnd + 1));
        return builder.toString();
    }

    public static final class PreparedProgram {
        private final String vertexSource;
        private final String geometrySource;
        private final String fragmentSource;

        private PreparedProgram(String vertexSource, String geometrySource, String fragmentSource) {
            this.vertexSource = vertexSource;
            this.geometrySource = geometrySource;
            this.fragmentSource = fragmentSource;
        }

        public String getVertexSource() {
            return vertexSource;
        }

        public String getGeometrySource() {
            return geometrySource;
        }

        public String getFragmentSource() {
            return fragmentSource;
        }
    }
}
