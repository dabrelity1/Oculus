package net.oculus.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.oculus.gl.shader.ShaderType;
import net.oculus.gl.shader.StandardMacros;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.StringPair;
import net.oculus.shaderpack.include.AbsolutePackPath;

/**
 * Shared helper for building the OptiFine-style define environment and applying
 * it to shader sources. This keeps the legacy macro shims, dimension-specific
 * flags, and stage/program markers in one place so both raster and compute
 * shaders stay in sync.
 */
public final class ShaderPreprocessor {
    private ShaderPreprocessor() {
    }

    public static List<StringPair> createEnvironmentDefines(ProgramSet programSet) {
        List<StringPair> defines = new ArrayList<>();
        for (StringPair pair : StandardMacros.createStandardEnvironmentDefines()) {
            defines.add(pair);
        }
        defines.addAll(buildDimensionDefines(programSet));
        defines.addAll(buildLegacyCompatibilityDefines());
        return Collections.unmodifiableList(defines);
    }

    public static String applyDefines(String source, ShaderType shaderType, String programName,
                                      List<StringPair> environmentDefines) {
        if (source == null) {
            return null;
        }

        List<StringPair> defines = new ArrayList<>(environmentDefines);
        defines.addAll(buildStageDefines(shaderType));
        defines.addAll(buildProgramDefines(programName));
        return ensureShaderCompatibility(injectDefines(source, defines), shaderType);
    }

    private static List<StringPair> buildDimensionDefines(ProgramSet programSet) {
        List<StringPair> defines = new ArrayList<>();
        if (programSet == null) {
            return defines;
        }

        ShaderPack pack = programSet.getPack();
        AbsolutePackPath programRoot = pack == null ? null : pack.getProgramRoot();
        String dimensionMacro = determineDimensionMacro(programRoot);
        if (dimensionMacro == null || dimensionMacro.isEmpty()) {
            return defines;
        }

        defines.add(new StringPair(dimensionMacro, ""));
        defines.add(new StringPair("WORLD_" + dimensionMacro, ""));
        return defines;
    }

    private static String determineDimensionMacro(AbsolutePackPath programRoot) {
        if (programRoot == null) {
            return "OVERWORLD";
        }

        String segment = extractLeadingSegment(programRoot.getPathString());
        if (segment.isEmpty() || segment.equals("world0")) {
            return "OVERWORLD";
        }

        if (segment.equals("world-1")) {
            return "NETHER";
        }

        if (segment.equals("world1")) {
            return "END";
        }

        return null;
    }

    private static String extractLeadingSegment(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }

        String trimmed = path.charAt(0) == '/' ? path.substring(1) : path;
        int slash = trimmed.indexOf('/');
        return slash >= 0 ? trimmed.substring(0, slash) : trimmed;
    }

    private static List<StringPair> buildLegacyCompatibilityDefines() {
        List<StringPair> defines = new ArrayList<>();
        // OptiFine-era alias uniforms used by Complementary's line rendering path.
        defines.add(new StringPair("projectionMatrix", "gbufferProjection"));
        defines.add(new StringPair("modelViewMatrix", "gbufferModelView"));
        defines.add(new StringPair("vaPosition", "gl_Vertex"));
        defines.add(new StringPair("vaNormal", "gl_Normal"));
        return defines;
    }

    private static List<StringPair> buildStageDefines(ShaderType shaderType) {
        List<StringPair> defines = new ArrayList<>();
        if (shaderType == null) {
            return defines;
        }

        switch (shaderType) {
            case VERTEX:
                defines.add(new StringPair("VERTEX_SHADER", ""));
                break;
            case GEOMETRY:
                defines.add(new StringPair("GEOMETRY_SHADER", ""));
                break;
            case FRAGMENT:
                defines.add(new StringPair("FRAGMENT_SHADER", ""));
                break;
            case COMPUTE:
                defines.add(new StringPair("COMPUTE_SHADER", ""));
                break;
            default:
                break;
        }

        return defines;
    }

    private static List<StringPair> buildProgramDefines(String programName) {
        List<StringPair> defines = new ArrayList<>();
        if (programName == null || programName.isEmpty()) {
            return defines;
        }

        String macroSafe = sanitizeMacroIdentifier(programName);
        if (macroSafe.isEmpty()) {
            return defines;
        }

        addProgramDefine(defines, "PROGRAM_" + macroSafe);
        addProgramDefine(defines, macroSafe);

        String group = extractProgramGroup(macroSafe);
        if (!group.isEmpty() && !group.equals(macroSafe)) {
            addProgramDefine(defines, "PROGRAM_" + group);
            addProgramDefine(defines, group);
        }

        return defines;
    }

    private static void addProgramDefine(List<StringPair> defines, String macro) {
        if (macro == null || macro.isEmpty()) {
            return;
        }

        defines.add(new StringPair(macro, ""));
    }

    private static String extractProgramGroup(String macroSafe) {
        if (macroSafe == null || macroSafe.isEmpty()) {
            return "";
        }

        for (int i = 0; i < macroSafe.length(); i++) {
            char ch = macroSafe.charAt(i);
            if (ch == '_' || Character.isDigit(ch)) {
                return i == 0 ? "" : macroSafe.substring(0, i);
            }
        }

        return macroSafe;
    }

    private static String sanitizeMacroIdentifier(String input) {
        StringBuilder builder = new StringBuilder();
        boolean previousUnderscore = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            char upper = Character.toUpperCase(ch);
            if ((upper >= 'A' && upper <= 'Z') || (upper >= '0' && upper <= '9')) {
                builder.append(upper);
                previousUnderscore = false;
            } else if (!previousUnderscore) {
                builder.append('_');
                previousUnderscore = true;
            }
        }

        String macro = builder.toString();
        while (macro.startsWith("_")) {
            macro = macro.substring(1);
        }
        while (macro.endsWith("_")) {
            macro = macro.substring(0, macro.length() - 1);
        }
        return macro;
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
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.trim().startsWith("#version")) {
                continue;
            }

            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                try {
                    int current = Integer.parseInt(parts[1]);
                    if (current >= minimumVersion) {
                        return source;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through to replace line
                }
            }

            lines[i] = "#version " + minimumVersion;
            return String.join("\n", lines);
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

    private static String injectDefines(String source, List<StringPair> defines) {
        if (defines == null || defines.isEmpty()) {
            return source;
        }

        StringBuilder block = new StringBuilder();
        for (StringPair define : defines) {
            if (define == null) {
                continue;
            }

            String key = define.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }

            block.append("#define ").append(key);
            String value = define.getValue();
            if (value != null && !value.isEmpty()) {
                block.append(' ').append(value);
            }
            block.append('\n');
        }

        if (block.length() == 0) {
            return source;
        }

        int versionIndex = source.indexOf("#version");
        if (versionIndex < 0) {
            return block.append(source).toString();
        }

        int insertIndex = source.indexOf('\n', versionIndex);
        if (insertIndex < 0) {
            insertIndex = source.length();
            return source + '\n' + block;
        }

        insertIndex += 1;
        return source.substring(0, insertIndex) + block + source.substring(insertIndex);
    }
}
