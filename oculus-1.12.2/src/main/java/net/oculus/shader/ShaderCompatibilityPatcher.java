package net.oculus.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.oculus.gl.shader.ShaderType;
import net.oculus.pipeline.InputAvailability;

/**
 * Applies small text-based fixes to shader sources before they reach the compiler.
 *
 * <p>This acts as a compatibility shim for legacy shader packs that relied on
 * OptiFine-era implicit conversions which modern GLSL compilers reject. Keeping the
 * fix here lets us patch the offending source at runtime without mutating the pack
 * on disk.</p>
 */
public final class ShaderCompatibilityPatcher {
    private static final Logger LOGGER = LogManager.getLogger(ShaderCompatibilityPatcher.class);
    private static final String LIGHTMAP_TEXTURE_MATRIX = "iris_LightmapTextureMatrix";
    private static final String LIGHTMAP_TEXTURE_MATRIX_DECLARATION =
        "uniform mat4 " + LIGHTMAP_TEXTURE_MATRIX + ";";
    private static final String IRIS_TEXTURE_MATRIX = "iris_TextureMatrix";
    private static final String ONE_OVER_256 = "iris_ONE_OVER_256";
    private static final String ONE_OVER_32 = "iris_ONE_OVER_32";
    private static final String CENTER_DEPTH_SMOOTH = "centerDepthSmooth";
    private static final String CENTER_DEPTH_SAMPLER = "iris_centerDepthSmooth";
    private static final String CENTER_DEPTH_SAMPLER_DECLARATION =
        "uniform sampler2D " + CENTER_DEPTH_SAMPLER + ";";
    private static final String CENTER_DEPTH_SAMPLE_EXPRESSION =
        "texture2D(" + CENTER_DEPTH_SAMPLER + ", vec2(0.5)).r";
    private static final int SHADER_STORAGE_BUFFER_MIN_VERSION = 430;
    private static final String SHADER_STORAGE_BUFFER_VERSION_DIRECTIVE = "#version 430 compatibility";
    private static final String[] SHADER_STORAGE_BUFFER_QUALIFIERS =
        {"readonly", "writeonly", "coherent", "volatile", "restrict"};
    private static final String TEXTURE_LOD_EXTENSION = "GL_ARB_shader_texture_lod";
    private static final String TEXTURE_LOD_EXTENSION_DECLARATION =
        "#extension " + TEXTURE_LOD_EXTENSION + " : require";
    private static final String SHIFTED_DITHER17 = "shifted_dither17";
    private static final String FRAME_MOD = "frame_mod";
    private static final String FRAME_MOD_DECLARATION = "uniform int " + FRAME_MOD + ";";
    private static final String LEGACY_SHIFTED_DITHER17_DECLARATION =
        "float " + SHIFTED_DITHER17 + "(vec2 pos) {\n" +
            "    return fract((" + FRAME_MOD + " * 0.4) + dot(pos, vec2(0.11764705882352941, 0.4117647058823529)));\n" +
            "}";
    private static final String SILDURS_WATER_FRACT_REPLACEMENT = "fract(worldpos.y + 0.01)";
    private static final String LEGACY_MID_TEX_COORD = "gl_MultiTexCoord3";
    private static final String MID_TEX_COORD = "mc_midTexCoord";
    private static final String MID_TEX_COORD_DECLARATION = "attribute vec4 " + MID_TEX_COORD + ";";
    private static final String MISSING_LEGACY_TEX_COORD = "vec4(240.0, 240.0, 0.0, 1.0)";
    private static final String CORE_PROFILE_VERTEX_SHADER_ERROR =
        "Vertex shaders must be in the compatibility profile to run properly!";
    private static final String INTERNAL_INTERFACE_ERROR_PREFIX =
        "Detected a potential reference to unstable and internal Iris shader interfaces (iris_ and irisMain). "
            + "This isn't currently supported. Violation: ";
    private static final String INTERNAL_INTERFACE_ERROR_SUFFIX = ". See debugging.md for more information.";
    private static final String GROUPED_PATCH_INTERNAL_PREFIX = "iris_template_";
    private static final String ENTITY_COLOR = "entityColor";
    private static final String ENTITY_COLOR_GEOMETRY = "entityColorGS";
    private static final String IRIS_ENTITY_COLOR = "iris_entityColor";
    private static final String IRIS_ENTITY_COLOR_DECLARATION =
        "uniform vec4 " + IRIS_ENTITY_COLOR + ";";
    private static final String ENTITY_COLOR_VARYING_DECLARATION =
        "varying vec4 " + ENTITY_COLOR + ";";
    private static final String ENTITY_COLOR_GEOMETRY_IN_DECLARATION =
        "in vec4 " + ENTITY_COLOR + "[];";
    private static final String ENTITY_COLOR_GEOMETRY_OUT_DECLARATION =
        "out vec4 " + ENTITY_COLOR_GEOMETRY + ";";
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("^\\s*#version\\s+(\\d+)", Pattern.MULTILINE);
    private static final Pattern VERSION_PROFILE_DIRECTIVE =
        Pattern.compile("^\\s*#version[ \\t]+(\\d+)(?:[ \\t]+([A-Za-z_][A-Za-z0-9_]*))?", Pattern.MULTILINE);
    private static final Pattern INTERFACE_DECLARATION = Pattern.compile(
        "^((?:(?:layout\\s*\\([^;]*\\)|flat|smooth|noperspective|centroid|sample|invariant|precise)\\s+)*)" +
            "(in|out|varying)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+(.+);$",
        Pattern.DOTALL);

    private static final Pattern LEGACY_SET_FOG_COLOR_BLOCK = Pattern.compile(
        "(?m)^(\\s*)block_color = clamp\\(block_color, vec3\\(0\\.0\\), vec3\\(50\\.0\\)\\);\\R" +
            "\\1gl_FragData\\[0\\] = vec4\\(block_color, 1\\.0\\);\\R" +
            "\\1gl_FragData\\[1\\] = vec4\\(block_color, 1\\.0\\);"
    );

    private static final String LEGACY_SET_FOG_COLOR_REPLACEMENT =
        "$1vec3 fog_color = clamp(block_color.rgb, vec3(0.0), vec3(50.0));\n" +
            "$1gl_FragData[0].rgb = fog_color;\n" +
            "$1gl_FragData[0].a = 1.0;\n" +
            "$1gl_FragData[1].rgb = fog_color;\n" +
            "$1gl_FragData[1].a = 1.0;";

    private ShaderCompatibilityPatcher() {
    }

    public static String patch(String packName, String programName, ShaderType shaderType, String source) {
        return patch(packName, programName, shaderType, source, null);
    }

    public static String patch(String packName, String programName, ShaderType shaderType, String source,
                               InputAvailability availability) {
        if (source == null || shaderType == null) {
            return source;
        }

        if (!isGeneratedSodiumTerrainProgram(programName)) {
            rejectInternalInterfaceReferences(source);
        }

        String patched = source;
        patched = patchShaderStorageBufferVersion(packName, programName, shaderType, patched);
        boolean coreProfile = isCoreProfile(patched);
        boolean runAttributeTransforms = availability != null && !coreProfile;
        if (availability != null && coreProfile && shaderType == ShaderType.VERTEX) {
            throw new IllegalStateException(CORE_PROFILE_VERTEX_SHADER_ERROR);
        }

        if (shaderType != ShaderType.COMPUTE) {
            if (runAttributeTransforms) {
                patched = patchInputAvailabilityCoordinates(packName, programName, shaderType, patched, availability);
            }
            patched = patchCenterDepthSmooth(packName, programName, patched);
            patched = patchCompositeTextureLodExtension(packName, programName, patched);
            if (availability == null) {
                patched = patchLightmapTextureMatrix(packName, programName, patched);
            } else if (runAttributeTransforms) {
                patched = patchAttributeTextureMatrices(packName, programName, shaderType, patched, availability);
            }
            if (runAttributeTransforms && availability.overlay) {
                patched = patchOverlayEntityColor(packName, programName, shaderType, patched);
            }
            patched = patchMissingLegacyShiftedDither17(packName, programName, patched);
            patched = removeDuplicateIdenticalFunctionDefinitions(packName, programName, shaderType, patched);
            patched = removeUnusedFunctionDefinitions(packName, programName, shaderType, patched);
            patched = removeConstDeclarationsInitializedByConstParameters(packName, programName, patched);
            patched = removeEmptyExternalDeclarations(packName, programName, patched);
        }

        if (shaderType == ShaderType.VERTEX) {
            patched = patchSildursWaterFract(packName, programName, patched);
            if (!coreProfile) {
                patched = patchLegacyMidTexCoordAlias(packName, programName, patched);
            }
        }

        if (shaderType == ShaderType.FRAGMENT) {
            patched = fixLegacyFogBlock(packName, programName, patched);
        }

        return patched;
    }

    private static boolean isGeneratedSodiumTerrainProgram(String programName) {
        return programName != null && programName.endsWith("_sodium");
    }

    private static void rejectInternalInterfaceReferences(String source) {
        String violation = findInternalInterfaceViolation(source);
        if (violation != null) {
            throw new IllegalStateException(INTERNAL_INTERFACE_ERROR_PREFIX + violation + INTERNAL_INTERFACE_ERROR_SUFFIX);
        }
    }

    private static String findInternalInterfaceViolation(String source) {
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
            while (end < source.length() && isIdentifierCharacter(source.charAt(end))) {
                end++;
            }

            String identifier = source.substring(i, end);
            if (identifier.startsWith("iris_") || identifier.startsWith("irisMain")) {
                return identifier;
            }
            i = end;
        }
        return null;
    }

    private static boolean isCoreProfile(String source) {
        Matcher matcher = VERSION_PROFILE_DIRECTIVE.matcher(source);
        if (!matcher.find()) {
            return false;
        }

        int version;
        try {
            version = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return false;
        }

        String profile = matcher.group(2);
        if (profile == null) {
            return version > 140;
        }
        return "core".equalsIgnoreCase(profile);
    }

    private static String patchShaderStorageBufferVersion(String packName, String programName, ShaderType shaderType,
                                                          String source) {
        if (shaderType == ShaderType.COMPUTE || !containsShaderStorageBufferDeclaration(source)) {
            return source;
        }

        Matcher matcher = VERSION_PROFILE_DIRECTIVE.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        int version;
        try {
            version = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            version = 0;
        }

        if (version >= SHADER_STORAGE_BUFFER_MIN_VERSION) {
            return source;
        }

        LOGGER.debug("Raised GLSL version to {} for SSBO program {} from pack {}",
            SHADER_STORAGE_BUFFER_VERSION_DIRECTIVE, programName, packName);
        return source.substring(0, matcher.start())
            + SHADER_STORAGE_BUFFER_VERSION_DIRECTIVE
            + source.substring(matcher.end());
    }

    private static boolean containsShaderStorageBufferDeclaration(String source) {
        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            if (matchesToken(source, i, "layout")) {
                int openParen = skipWhitespaceAndComments(source, i + "layout".length());
                if (openParen < source.length() && source.charAt(openParen) == '(') {
                    int closeParen = source.indexOf(')', openParen + 1);
                    if (closeParen > openParen && source.substring(openParen + 1, closeParen).contains("std430")) {
                        int afterQualifiers = skipShaderStorageBufferQualifiers(source, closeParen + 1);
                        if (matchesToken(source, afterQualifiers, "buffer")) {
                            return true;
                        }
                    }
                }
            }

            i++;
        }

        return false;
    }

    private static int skipShaderStorageBufferQualifiers(String source, int start) {
        int index = skipWhitespaceAndComments(source, start);
        for (;;) {
            int qualifierLength = shaderStorageBufferQualifierLength(source, index);
            if (qualifierLength == 0) {
                return index;
            }
            index = skipWhitespaceAndComments(source, index + qualifierLength);
        }
    }

    private static int shaderStorageBufferQualifierLength(String source, int index) {
        for (String qualifier : SHADER_STORAGE_BUFFER_QUALIFIERS) {
            if (matchesToken(source, index, qualifier)) {
                return qualifier.length();
            }
        }
        return 0;
    }

    private static String patchInputAvailabilityCoordinates(String packName, String programName, ShaderType shaderType,
                                                            String source, InputAvailability availability) {
        if (availability == null) {
            return source;
        }

        String patched = source;
        boolean changed = false;

        if (availability.lightmap) {
            ReplacementResult lightmapAlias = replaceIdentifierReferences(
                patched,
                "gl_MultiTexCoord2",
                "gl_MultiTexCoord1");
            patched = lightmapAlias.source;
            changed |= lightmapAlias.changed;
        } else {
            ReplacementResult lightmapOne = replaceIdentifierReferences(
                patched,
                "gl_MultiTexCoord1",
                MISSING_LEGACY_TEX_COORD);
            ReplacementResult lightmapTwo = replaceIdentifierReferences(
                lightmapOne.source,
                "gl_MultiTexCoord2",
                MISSING_LEGACY_TEX_COORD);
            patched = lightmapTwo.source;
            changed |= lightmapOne.changed || lightmapTwo.changed;
        }

        if (!availability.texture) {
            ReplacementResult texture = replaceIdentifierReferences(
                patched,
                "gl_MultiTexCoord0",
                MISSING_LEGACY_TEX_COORD);
            patched = texture.source;
            changed |= texture.changed;
        }

        if (changed) {
            LOGGER.debug("Patched legacy texture coordinate inputs for {} shader program {} from pack {} using {}",
                shaderType.name().toLowerCase(Locale.ROOT), programName, packName, availability);
        }

        return patched;
    }

    public static PatchedShaderSources patchGrouped(String packName, String programName,
                                                    String vertexSource, String geometrySource, String fragmentSource) {
        return patchGrouped(packName, programName, vertexSource, geometrySource, fragmentSource, null);
    }

    public static PatchedShaderSources patchGrouped(String packName, String programName,
                                                    String vertexSource, String geometrySource, String fragmentSource,
                                                    InputAvailability availability) {
        StagePatchSource[] stages = new StagePatchSource[] {
            new StagePatchSource(ShaderType.VERTEX, vertexSource),
            new StagePatchSource(ShaderType.GEOMETRY, geometrySource),
            new StagePatchSource(ShaderType.FRAGMENT, fragmentSource)
        };

        if (availability != null && availability.overlay && stages[1].source != null && stages[2].source != null) {
            stages[2].source = patchOverlayEntityColorFragmentWithGeometry(packName, programName, stages[2].source);
        }

        int previous = -1;
        for (int i = 0; i < stages.length; i++) {
            if (stages[i].source == null) {
                continue;
            }

            if (previous >= 0) {
                stages[previous].source = patchInterfaceBetweenStages(
                    packName,
                    programName,
                    stages[previous].type,
                    stages[previous].source,
                    stages[i].type,
                    stages[i].source);
            }
            previous = i;
        }

        return new PatchedShaderSources(stages[0].source, stages[1].source, stages[2].source);
    }

    private static String patchOverlayEntityColor(String packName, String programName, ShaderType shaderType,
                                                  String source) {
        String withoutUniform = removeUniformDeclarations(source, "vec4", ENTITY_COLOR);

        if (shaderType == ShaderType.VERTEX) {
            String patched = withoutUniform;
            List<String> declarations = new ArrayList<>();
            if (!declaresTypedName(patched, "vec4", IRIS_ENTITY_COLOR)) {
                declarations.add(IRIS_ENTITY_COLOR_DECLARATION);
            }
            if (!declaresTypedName(patched, "vec4", ENTITY_COLOR)) {
                declarations.add(ENTITY_COLOR_VARYING_DECLARATION);
            }
            if (!declarations.isEmpty()) {
                patched = injectGlobalDeclaration(patched, joinLines(declarations));
            }
            patched = prependMainStatements(patched, entityColorVertexStatements());
            LOGGER.debug("Patched 1.12 entityColor overlay passthrough for vertex program {} from pack {}",
                programName, packName);
            return patched;
        }

        if (shaderType == ShaderType.GEOMETRY) {
            ReplacementResult replaced = replaceGeometryEntityColorReferences(withoutUniform);
            String patched = replaced.source;
            List<String> declarations = new ArrayList<>();
            if (!declaresTypedName(patched, "vec4", ENTITY_COLOR_GEOMETRY)) {
                declarations.add(ENTITY_COLOR_GEOMETRY_OUT_DECLARATION);
            }
            if (!declaresTypedName(patched, "vec4", ENTITY_COLOR)) {
                declarations.add(ENTITY_COLOR_GEOMETRY_IN_DECLARATION);
            }
            if (!declarations.isEmpty()) {
                patched = injectGlobalDeclaration(patched, joinLines(declarations));
            }
            patched = prependMainStatements(patched,
                Collections.singletonList(ENTITY_COLOR_GEOMETRY + " = " + ENTITY_COLOR + "[0];"));
            LOGGER.debug("Patched 1.12 entityColor overlay passthrough for geometry program {} from pack {}",
                programName, packName);
            return patched;
        }

        if (shaderType == ShaderType.FRAGMENT) {
            String patched = withoutUniform;
            if (!declaresTypedName(patched, "vec4", ENTITY_COLOR)) {
                patched = injectGlobalDeclaration(patched, ENTITY_COLOR_VARYING_DECLARATION);
            }
            LOGGER.debug("Patched 1.12 entityColor overlay passthrough for fragment program {} from pack {}",
                programName, packName);
            return patched;
        }

        return source;
    }

    private static List<String> entityColorVertexStatements() {
        List<String> statements = new ArrayList<>();
        statements.add(ENTITY_COLOR + " = " + IRIS_ENTITY_COLOR + ";");
        statements.add(ENTITY_COLOR + ".rgb *= float(" + ENTITY_COLOR + ".a != 0.0);");
        return statements;
    }

    private static String patchOverlayEntityColorFragmentWithGeometry(String packName, String programName,
                                                                      String fragmentSource) {
        if (!declaresInputInterfaceName(fragmentSource, ShaderType.FRAGMENT, ENTITY_COLOR)) {
            return fragmentSource;
        }

        ReplacementResult replaced = replaceIdentifierReferences(fragmentSource, ENTITY_COLOR, ENTITY_COLOR_GEOMETRY);
        if (replaced.changed) {
            LOGGER.debug("Renamed fragment entityColor passthrough to {} for geometry program {} from pack {}",
                ENTITY_COLOR_GEOMETRY, programName, packName);
        }
        return replaced.source;
    }

    private static ReplacementResult replaceGeometryEntityColorReferences(String source) {
        List<TokenRange> excludedRanges = new ArrayList<>();
        addInterfaceDeclarationRangesForName(excludedRanges,
            findInterfaceDeclarations(source, ShaderType.GEOMETRY, false), ENTITY_COLOR);
        addInterfaceDeclarationRangesForName(excludedRanges,
            findInterfaceDeclarations(source, ShaderType.GEOMETRY, true), ENTITY_COLOR);

        List<SourceReplacement> replacements = new ArrayList<>();
        collectIdentifierReferenceReplacements(
            source,
            ENTITY_COLOR,
            ENTITY_COLOR + "[0]",
            excludedRanges,
            replacements);

        if (replacements.isEmpty()) {
            return new ReplacementResult(source, false);
        }
        return new ReplacementResult(applyReplacements(source, replacements), true);
    }

    private static boolean declaresInputInterfaceName(String source, ShaderType shaderType, String name) {
        return findDeclarationForName(findInterfaceDeclarations(source, shaderType, false), name) != null;
    }

    private static void addInterfaceDeclarationRangesForName(List<TokenRange> ranges,
                                                             List<InterfaceDeclaration> declarations,
                                                             String name) {
        for (InterfaceDeclaration declaration : declarations) {
            if (declaration.memberNamed(name) != null) {
                ranges.add(new TokenRange(declaration.declarationStart, declaration.declarationEnd));
            }
        }
    }

    private static String patchInterfaceBetweenStages(String packName, String programName,
                                                      ShaderType previousType, String previousSource,
                                                      ShaderType currentType, String currentSource) {
        List<InterfaceDeclaration> previousOutputs = findInterfaceDeclarations(previousSource, previousType, true);
        List<InterfaceDeclaration> currentInputs = findInterfaceDeclarations(currentSource, currentType, false);
        if (currentInputs.isEmpty()) {
            return previousSource;
        }

        if (containsIdentifierWithPrefix(previousSource, GROUPED_PATCH_INTERNAL_PREFIX)) {
            LOGGER.warn("Skipped grouped shader interface patching for {} -> {} in program {} from pack {} because {} is already used",
                previousType, currentType, programName, packName, GROUPED_PATCH_INTERNAL_PREFIX);
            return previousSource;
        }

        String patched = previousSource;
        Set<String> handledNames = new HashSet<>();

        for (InterfaceDeclaration currentInput : currentInputs) {
            for (InterfaceMember currentMember : currentInput.members) {
                String name = currentMember.name;
                if (name.startsWith("gl_") || !handledNames.add(name)) {
                    continue;
                }

                if (countIdentifierReferences(currentSource, name) <= 1) {
                    continue;
                }

                previousOutputs = findInterfaceDeclarations(patched, previousType, true);
                InterfaceDeclaration previousOutput = findDeclarationForName(previousOutputs, name);
                if (previousOutput == null) {
                    patched = injectGlobalDeclaration(patched, currentInput.toPreviousStageDeclaration(name));
                    patched = prependMainStatements(patched,
                        Collections.singletonList(name + " = " + defaultValueForType(currentInput.typeName) + ";"));
                    LOGGER.warn("Added missing {} output declaration {} for {} input in program {} from pack {}",
                        previousType, name, currentType, programName, packName);
                    continue;
                }

                if (previousOutput.typeName.equals(currentInput.typeName)) {
                    if (countIdentifierReferences(patched, name) <= 1) {
                        patched = prependMainStatements(patched,
                            Collections.singletonList(name + " = " + defaultValueForType(currentInput.typeName) + ";"));
                        LOGGER.warn("Initialized unassigned {} output {} used by {} in program {} from pack {}",
                            previousType, name, currentType, programName, packName);
                    }
                    continue;
                }

                if (!hasSameInterfaceDimensionality(previousOutput.typeName, currentInput.typeName)) {
                    LOGGER.warn("Skipped grouped shader interface type mismatch for {} between {} and {} in program {} from pack {} because {} and {} have different scalar/vector/matrix dimensionality",
                        name, previousType, currentType, programName, packName, previousOutput.typeName, currentInput.typeName);
                    continue;
                }

                InterfaceMember previousMember = previousOutput.memberNamed(name);
                if (previousMember == null) {
                    continue;
                }

                patched = patchMismatchedInterfaceOutput(patched, previousOutput, previousMember, currentInput);
                LOGGER.warn("Patched grouped shader interface type mismatch for {} between {} {} and {} {} in program {} from pack {}",
                    name, previousType, previousOutput.typeName, currentType, currentInput.typeName, programName, packName);
            }
        }

        return patched;
    }

    private static String patchMismatchedInterfaceOutput(String source,
                                                         InterfaceDeclaration previousOutput,
                                                         InterfaceMember previousMember,
                                                         InterfaceDeclaration currentInput) {
        String internalName = GROUPED_PATCH_INTERNAL_PREFIX + previousMember.name;
        boolean singleMember = previousOutput.members.size() == 1;
        List<SourceReplacement> replacements = new ArrayList<>();
        List<TokenRange> excludedRanges = new ArrayList<>();

        if (singleMember) {
            excludedRanges.add(new TokenRange(previousMember.nameStart, previousMember.nameEnd));
            replacements.add(new SourceReplacement(previousOutput.typeStart, previousOutput.typeEnd, currentInput.typeName));
        } else {
            excludedRanges.add(new TokenRange(previousOutput.declarationStart, previousOutput.declarationEnd));
            replacements.add(new SourceReplacement(previousOutput.declarationStart, previousOutput.declarationEnd,
                previousOutput.withoutMember(previousMember)));
        }

        collectIdentifierReferenceReplacements(source, previousMember.name, internalName, excludedRanges, replacements);

        String patched = applyReplacements(source, replacements);
        List<String> declarations = new ArrayList<>();
        if (!singleMember) {
            declarations.add(previousOutput.withTypeAndSingleMember(currentInput.typeName, previousMember.name));
        }
        declarations.add(previousOutput.typeName + " " + internalName + ";");
        patched = injectGlobalDeclaration(patched, joinLines(declarations));
        return appendMainStatements(patched,
            Collections.singletonList(previousMember.name + " = "
                + castExpression(currentInput.typeName, previousOutput.typeName, internalName) + ";"));
    }

    private static String removeUnusedFunctionDefinitions(String packName, String programName, ShaderType shaderType, String source) {
        UnusedFunctionRemovalResult replaced = removeUnusedFunctionDefinitions(source);
        if (!replaced.changed) {
            return source;
        }

        String stage = shaderType.name().toLowerCase(Locale.ROOT);
        String firstFunction = replaced.removedFunctionNames.get(0);
        int omitted = replaced.removedFunctionNames.size() - 1;
        if (omitted > 0) {
            LOGGER.debug("Removed unused function {} from {} shader for program {} in pack {}; omitted {} further unused function removal message(s)",
                firstFunction, stage, programName, packName, omitted);
        } else {
            LOGGER.debug("Removed unused function {} from {} shader for program {} in pack {}",
                firstFunction, stage, programName, packName);
        }
        return replaced.source;
    }

    private static String removeDuplicateIdenticalFunctionDefinitions(String packName, String programName,
                                                                      ShaderType shaderType, String source) {
        DuplicateFunctionRemovalResult replaced = removeDuplicateIdenticalFunctionDefinitions(source);
        if (!replaced.changed) {
            return source;
        }

        String stage = shaderType.name().toLowerCase(Locale.ROOT);
        String firstFunction = replaced.removedFunctionNames.get(0);
        int omitted = replaced.removedFunctionNames.size() - 1;
        if (omitted > 0) {
            LOGGER.debug("Removed duplicate identical function {} from {} shader for program {} in pack {}; omitted {} further duplicate function removal message(s)",
                firstFunction, stage, programName, packName, omitted);
        } else {
            LOGGER.debug("Removed duplicate identical function {} from {} shader for program {} in pack {}",
                firstFunction, stage, programName, packName);
        }
        return replaced.source;
    }

    private static DuplicateFunctionRemovalResult removeDuplicateIdenticalFunctionDefinitions(String source) {
        Map<String, Set<String>> seenBodiesBySignature = new HashMap<>();
        List<FunctionRange> duplicates = new ArrayList<>();
        List<String> removedFunctionNames = new ArrayList<>();

        for (FunctionRange function : findFunctionRanges(source)) {
            String signature = canonicalizeCode(source, function.declarationStart, function.bodyStart);
            String body = canonicalizeCode(source, function.bodyStart, function.bodyEnd + 1);
            Set<String> seenBodies = seenBodiesBySignature.get(signature);
            if (seenBodies == null) {
                seenBodies = new HashSet<>();
                seenBodiesBySignature.put(signature, seenBodies);
            }

            if (seenBodies.contains(body)) {
                duplicates.add(function);
                removedFunctionNames.add(function.name);
                continue;
            }

            seenBodies.add(body);
        }

        if (duplicates.isEmpty()) {
            return new DuplicateFunctionRemovalResult(source, false, removedFunctionNames);
        }

        Collections.sort(duplicates, (left, right) -> Integer.compare(right.declarationStart, left.declarationStart));
        StringBuilder builder = new StringBuilder(source);
        for (FunctionRange function : duplicates) {
            builder.delete(function.declarationStart, function.bodyEnd + 1);
        }
        return new DuplicateFunctionRemovalResult(builder.toString(), true, removedFunctionNames);
    }

    private static String canonicalizeCode(String source, int start, int end) {
        StringBuilder builder = new StringBuilder(end - start);
        for (int i = start; i < end;) {
            if (Character.isWhitespace(source.charAt(i))) {
                i++;
                continue;
            }

            if (i + 1 < end && source.charAt(i) == '/' && source.charAt(i + 1) == '/') {
                int lineEnd = source.indexOf('\n', i + 2);
                i = lineEnd < 0 || lineEnd > end ? end : lineEnd + 1;
                continue;
            }

            if (i + 1 < end && source.charAt(i) == '/' && source.charAt(i + 1) == '*') {
                int commentEnd = source.indexOf("*/", i + 2);
                i = commentEnd < 0 || commentEnd + 2 > end ? end : commentEnd + 2;
                continue;
            }

            if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                int literalEnd = copyStringLiteral(source, i, end, builder);
                i = literalEnd;
                continue;
            }

            builder.append(source.charAt(i));
            i++;
        }
        return builder.toString();
    }

    private static int copyStringLiteral(String source, int start, int end, StringBuilder output) {
        char quote = source.charAt(start);
        int i = start;
        boolean escaped = false;
        while (i < end) {
            char current = source.charAt(i++);
            output.append(current);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == quote) {
                break;
            }
        }
        return i;
    }

    private static UnusedFunctionRemovalResult removeUnusedFunctionDefinitions(String source) {
        List<FunctionRange> unusedFunctions = new ArrayList<>();
        List<String> removedFunctionNames = new ArrayList<>();
        for (FunctionRange function : findFunctionRanges(source)) {
            if ("main".equals(function.name)) {
                continue;
            }

            if (countIdentifierReferences(source, function.name) <= 1) {
                unusedFunctions.add(function);
                removedFunctionNames.add(function.name);
            }
        }

        if (unusedFunctions.isEmpty()) {
            return new UnusedFunctionRemovalResult(source, false, removedFunctionNames);
        }

        Collections.sort(unusedFunctions, (left, right) -> Integer.compare(right.declarationStart, left.declarationStart));
        StringBuilder builder = new StringBuilder(source);
        for (FunctionRange function : unusedFunctions) {
            builder.delete(function.declarationStart, function.bodyEnd + 1);
        }
        return new UnusedFunctionRemovalResult(builder.toString(), true, removedFunctionNames);
    }

    private static int countIdentifierReferences(String source, String identifier) {
        int count = 0;
        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            if (source.startsWith(identifier, i)
                && isIdentifierBoundary(source, i - 1)
                && isIdentifierBoundary(source, i + identifier.length())) {
                count++;
                i += identifier.length();
                continue;
            }

            i++;
        }
        return count;
    }

    private static List<InterfaceDeclaration> findInterfaceDeclarations(String source, ShaderType shaderType, boolean outputs) {
        List<InterfaceDeclaration> declarations = new ArrayList<>();
        int depth = 0;
        int declarationStart = 0;

        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth = Math.max(0, depth - 1);
                if (depth == 0) {
                    declarationStart = i + 1;
                }
            } else if (ch == ';' && depth == 0) {
                int start = skipLeadingNonCode(source, declarationStart, i);
                InterfaceDeclaration declaration = parseInterfaceDeclaration(source, start, i + 1, shaderType, outputs);
                if (declaration != null) {
                    declarations.add(declaration);
                }
                declarationStart = i + 1;
            }

            i++;
        }

        return declarations;
    }

    private static InterfaceDeclaration parseInterfaceDeclaration(String source, int start, int end,
                                                                 ShaderType shaderType, boolean outputs) {
        int declarationStart = start;
        int declarationEnd = end;
        while (declarationStart < declarationEnd && Character.isWhitespace(source.charAt(declarationStart))) {
            declarationStart++;
        }
        while (declarationEnd > declarationStart && Character.isWhitespace(source.charAt(declarationEnd - 1))) {
            declarationEnd--;
        }

        String declaration = source.substring(declarationStart, declarationEnd);
        Matcher matcher = INTERFACE_DECLARATION.matcher(declaration);
        if (!matcher.matches()) {
            return null;
        }

        String storage = matcher.group(2);
        if (outputs) {
            if (!isOutputInterfaceStorage(storage, shaderType)) {
                return null;
            }
        } else if (!isInputInterfaceStorage(storage, shaderType)) {
            return null;
        }

        String typeName = matcher.group(3);
        if (!isSupportedInterfaceType(typeName)) {
            return null;
        }

        int typeStart = declarationStart + matcher.start(3);
        int typeEnd = declarationStart + matcher.end(3);
        int memberStart = declarationStart + matcher.start(4);
        int memberEnd = declarationStart + matcher.end(4);

        List<InterfaceMember> members = new ArrayList<>();
        for (TokenRange member : splitTopLevel(source, memberStart, memberEnd, ',')) {
            TokenRange nameRange = lastIdentifierRangeAtTopLevel(source, member.start, member.end);
            if (nameRange != null) {
                members.add(new InterfaceMember(
                    source.substring(nameRange.start, nameRange.end),
                    nameRange.start,
                    nameRange.end,
                    source.substring(member.start, member.end).trim()));
            }
        }

        if (members.isEmpty()) {
            return null;
        }

        return new InterfaceDeclaration(matcher.group(1), storage, typeName,
            declarationStart, declarationEnd, typeStart, typeEnd, members);
    }

    private static boolean isOutputInterfaceStorage(String storage, ShaderType shaderType) {
        return "out".equals(storage)
            || ("varying".equals(storage) && (shaderType == ShaderType.VERTEX || shaderType == ShaderType.GEOMETRY));
    }

    private static boolean isInputInterfaceStorage(String storage, ShaderType shaderType) {
        return "in".equals(storage)
            || ("varying".equals(storage) && shaderType == ShaderType.FRAGMENT);
    }

    private static boolean isSupportedInterfaceType(String typeName) {
        return interfaceTypeKind(typeName) >= 0;
    }

    private static InterfaceDeclaration findDeclarationForName(List<InterfaceDeclaration> declarations, String name) {
        for (InterfaceDeclaration declaration : declarations) {
            if (declaration.memberNamed(name) != null) {
                return declaration;
            }
        }
        return null;
    }

    private static String defaultValueForType(String typeName) {
        if (isBooleanScalarType(typeName)) {
            return "false";
        }
        if (isIntegerScalarType(typeName)) {
            return "0";
        }
        if (isFloatingPointScalarType(typeName)) {
            return "0.0";
        }
        if (isBooleanVectorType(typeName)) {
            return typeName + "(false)";
        }
        if (isIntegerVectorType(typeName)) {
            return typeName + "(0)";
        }
        return typeName + "(0.0)";
    }

    private static boolean hasSameInterfaceDimensionality(String previousTypeName, String currentTypeName) {
        return interfaceTypeKind(previousTypeName) == interfaceTypeKind(currentTypeName);
    }

    private static int interfaceTypeKind(String typeName) {
        if (isScalarType(typeName)) {
            return 0;
        }
        if (isVectorType(typeName)) {
            return 1;
        }
        if (isMatrixType(typeName)) {
            return 2;
        }
        return -1;
    }

    private static String castExpression(String targetTypeName, String sourceTypeName, String sourceName) {
        if (isVectorType(sourceTypeName)
            && vectorComponentCount(sourceTypeName) < vectorComponentCount(targetTypeName)) {
            return targetTypeName + "(" + sourceName + ", vec4(0))";
        }
        return targetTypeName + "(" + sourceName + ")";
    }

    private static boolean isVectorType(String typeName) {
        return isBooleanVectorType(typeName)
            || isIntegerVectorType(typeName)
            || isFloatingPointVectorType(typeName);
    }

    private static int vectorComponentCount(String typeName) {
        if (!isVectorType(typeName)) {
            return -1;
        }
        return Character.digit(typeName.charAt(typeName.length() - 1), 10);
    }

    private static boolean isScalarType(String typeName) {
        return isBooleanScalarType(typeName)
            || isIntegerScalarType(typeName)
            || isFloatingPointScalarType(typeName);
    }

    private static boolean isBooleanScalarType(String typeName) {
        return "bool".equals(typeName);
    }

    private static boolean isBooleanVectorType(String typeName) {
        return typeName.matches("bvec[234]");
    }

    private static boolean isIntegerScalarType(String typeName) {
        return "int".equals(typeName)
            || "uint".equals(typeName)
            || typeName.matches("u?int(8|16|32|64)_t");
    }

    private static boolean isIntegerVectorType(String typeName) {
        return typeName.matches("[iu]vec[234]")
            || typeName.matches("i(8|16|32|64)vec[234]")
            || typeName.matches("ui(8|16|32|64)vec[234]");
    }

    private static boolean isFloatingPointScalarType(String typeName) {
        return "float".equals(typeName)
            || "double".equals(typeName)
            || typeName.matches("float(16|32|64)_t");
    }

    private static boolean isFloatingPointVectorType(String typeName) {
        return typeName.matches("vec[234]")
            || typeName.matches("dvec[234]")
            || typeName.matches("f(16|32|64)vec[234]");
    }

    private static boolean isMatrixType(String typeName) {
        return typeName.matches("[d]?mat[234](x[234])?")
            || typeName.matches("f(16|32|64)mat[234](x[234])?");
    }

    private static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private static String prependMainStatements(String source, List<String> statements) {
        for (FunctionRange function : findFunctionRanges(source)) {
            if (!"main".equals(function.name)) {
                continue;
            }

            StringBuilder insertion = new StringBuilder();
            insertion.append('\n');
            for (String statement : statements) {
                insertion.append("    ").append(statement).append('\n');
            }

            StringBuilder builder = new StringBuilder(source.length() + insertion.length());
            builder.append(source, 0, function.bodyStart + 1);
            builder.append(insertion);
            builder.append(source, function.bodyStart + 1, source.length());
            return builder.toString();
        }
        return source;
    }

    private static String appendMainStatements(String source, List<String> statements) {
        for (FunctionRange function : findFunctionRanges(source)) {
            if (!"main".equals(function.name)) {
                continue;
            }

            StringBuilder insertion = new StringBuilder();
            for (String statement : statements) {
                insertion.append("    ").append(statement).append('\n');
            }

            StringBuilder builder = new StringBuilder(source.length() + insertion.length());
            builder.append(source, 0, function.bodyEnd);
            if (function.bodyEnd > 0 && source.charAt(function.bodyEnd - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(insertion);
            builder.append(source, function.bodyEnd, source.length());
            return builder.toString();
        }
        return source;
    }

    private static String removeConstDeclarationsInitializedByConstParameters(String packName, String programName, String source) {
        ReplacementResult replaced = removeConstDeclarationsInitializedByConstParameters(source);
        if (!replaced.changed) {
            return source;
        }

        LOGGER.warn("Removed const qualifiers from declarations initialized by const parameters in program {} from pack {}",
            programName, packName);
        return replaced.source;
    }

    private static ReplacementResult removeConstDeclarationsInitializedByConstParameters(String source) {
        List<TokenRange> constTokensToRemove = new ArrayList<>();

        for (FunctionRange function : findFunctionRanges(source)) {
            Set<String> trackedNames = parseConstParameterNames(source, function.parametersStart, function.parametersEnd);
            if (trackedNames.isEmpty()) {
                continue;
            }

            List<LocalConstDeclaration> declarations = findLocalConstDeclarations(source, function.bodyStart + 1, function.bodyEnd);
            boolean changed;
            do {
                changed = false;
                for (LocalConstDeclaration declaration : declarations) {
                    if (declaration.removeConst) {
                        continue;
                    }

                    String referencedName = firstReferencedTrackedName(source, declaration.initializerRanges, trackedNames);
                    if (referencedName == null) {
                        continue;
                    }

                    declaration.removeConst = true;
                    changed = true;
                    for (String memberName : declaration.memberNames) {
                        if (trackedNames.contains(memberName)) {
                            throw new IllegalStateException("Illegal redefinition of const parameter " + referencedName);
                        }
                        trackedNames.add(memberName);
                    }
                }
            } while (changed);

            for (LocalConstDeclaration declaration : declarations) {
                if (declaration.removeConst) {
                    constTokensToRemove.add(declaration.constToken);
                }
            }
        }

        if (constTokensToRemove.isEmpty()) {
            return new ReplacementResult(source, false);
        }

        Collections.sort(constTokensToRemove, (left, right) -> Integer.compare(right.start, left.start));
        StringBuilder builder = new StringBuilder(source);
        for (TokenRange range : constTokensToRemove) {
            builder.delete(range.start, range.end);
        }
        return new ReplacementResult(builder.toString(), true);
    }

    private static List<FunctionRange> findFunctionRanges(String source) {
        List<FunctionRange> functions = new ArrayList<>();
        int depth = 0;
        int topLevelStart = 0;

        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            char ch = source.charAt(i);
            if (ch == '{') {
                if (depth == 0 && previousNonWhitespace(source, i) == ')') {
                    int parametersEnd = previousNonWhitespaceIndex(source, i);
                    int parametersStart = findMatchingOpenParen(source, parametersEnd);
                    int bodyEnd = findMatchingCloseBrace(source, i);
                    if (parametersStart >= 0 && bodyEnd > i) {
                        int declarationStart = skipLeadingNonCode(source, topLevelStart, parametersStart);
                        String functionName = lastIdentifierAtTopLevel(source, declarationStart, parametersStart);
                        if (functionName != null) {
                            functions.add(new FunctionRange(functionName, declarationStart,
                                parametersStart + 1, parametersEnd, i, bodyEnd));
                        }
                        i = bodyEnd + 1;
                        topLevelStart = i;
                        continue;
                    }
                }
                depth++;
            } else if (ch == '}') {
                depth = Math.max(0, depth - 1);
                if (depth == 0) {
                    topLevelStart = i + 1;
                }
            } else if (depth == 0 && ch == ';') {
                topLevelStart = i + 1;
            }

            i++;
        }

        return functions;
    }

    private static int skipLeadingNonCode(String source, int start, int end) {
        int index = start;
        while (index < end) {
            while (index < end && Character.isWhitespace(source.charAt(index))) {
                index++;
            }

            int skipped = skipNonCodeToken(source, index);
            if (skipped > index && skipped <= end) {
                index = skipped;
                continue;
            }

            break;
        }
        return index;
    }

    private static Set<String> parseConstParameterNames(String source, int start, int end) {
        Set<String> names = new HashSet<>();
        for (TokenRange parameter : splitTopLevel(source, start, end, ',')) {
            if (!containsToken(source, parameter.start, parameter.end, "const")) {
                continue;
            }

            String name = lastIdentifierAtTopLevel(source, parameter.start, parameter.end);
            if (name != null && !"const".equals(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static List<LocalConstDeclaration> findLocalConstDeclarations(String source, int bodyStart, int bodyEnd) {
        List<LocalConstDeclaration> declarations = new ArrayList<>();
        int statementStart = bodyStart;
        int parenDepth = 0;
        int bracketDepth = 0;

        for (int i = bodyStart; i < bodyEnd;) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            char ch = source.charAt(i);
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')' && parenDepth > 0) {
                parenDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
            } else if ((ch == '{' || ch == '}') && parenDepth == 0 && bracketDepth == 0) {
                statementStart = i + 1;
            } else if (ch == ';' && parenDepth == 0 && bracketDepth == 0) {
                LocalConstDeclaration declaration = parseLocalConstDeclaration(source, statementStart, i + 1);
                if (declaration != null) {
                    declarations.add(declaration);
                }
                statementStart = i + 1;
            }

            i++;
        }

        return declarations;
    }

    private static LocalConstDeclaration parseLocalConstDeclaration(String source, int start, int end) {
        TokenRange constToken = findTokenRange(source, start, end, "const");
        if (constToken == null) {
            return null;
        }

        List<String> memberNames = new ArrayList<>();
        List<TokenRange> initializerRanges = new ArrayList<>();
        for (TokenRange declarator : splitTopLevel(source, start, end - 1, ',')) {
            int equals = findTopLevelChar(source, declarator.start, declarator.end, '=');
            int declaratorNameEnd = equals >= 0 ? equals : declarator.end;
            String memberName = lastIdentifierAtTopLevel(source, declarator.start, declaratorNameEnd);
            if (memberName != null && !"const".equals(memberName)) {
                memberNames.add(memberName);
            }

            if (equals >= 0) {
                initializerRanges.add(new TokenRange(equals + 1, declarator.end));
            }
        }

        if (memberNames.isEmpty() || initializerRanges.isEmpty()) {
            return null;
        }

        return new LocalConstDeclaration(constToken, memberNames, initializerRanges);
    }

    private static String firstReferencedTrackedName(String source, List<TokenRange> ranges, Set<String> trackedNames) {
        for (TokenRange range : ranges) {
            for (String name : trackedNames) {
                if (containsToken(source, range.start, range.end, name)) {
                    return name;
                }
            }
        }
        return null;
    }

    private static TokenRange findTokenRange(String source, int start, int end, String token) {
        for (int i = start; i < end;) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            if (i + token.length() <= end
                && source.startsWith(token, i)
                && isIdentifierBoundary(source, i - 1)
                && isIdentifierBoundary(source, i + token.length())) {
                return new TokenRange(i, i + token.length());
            }
            i++;
        }
        return null;
    }

    private static boolean containsToken(String source, int start, int end, String token) {
        return findTokenRange(source, start, end, token) != null;
    }

    private static String lastIdentifierAtTopLevel(String source, int start, int end) {
        TokenRange range = lastIdentifierRangeAtTopLevel(source, start, end);
        return range == null ? null : source.substring(range.start, range.end);
    }

    private static TokenRange lastIdentifierRangeAtTopLevel(String source, int start, int end) {
        TokenRange last = null;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;

        for (int i = start; i < end;) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            char ch = source.charAt(i);
            if (ch == '(') {
                parenDepth++;
                i++;
                continue;
            }
            if (ch == ')' && parenDepth > 0) {
                parenDepth--;
                i++;
                continue;
            }
            if (ch == '[') {
                bracketDepth++;
                i++;
                continue;
            }
            if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
                i++;
                continue;
            }
            if (ch == '{') {
                braceDepth++;
                i++;
                continue;
            }
            if (ch == '}' && braceDepth > 0) {
                braceDepth--;
                i++;
                continue;
            }

            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && isIdentifierStart(ch)) {
                int tokenStart = i;
                i++;
                while (i < end && isIdentifierCharacter(source.charAt(i))) {
                    i++;
                }
                last = new TokenRange(tokenStart, i);
                continue;
            }

            i++;
        }

        return last;
    }

    private static boolean containsIdentifierWithPrefix(String source, String prefix) {
        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            if (isIdentifierStart(source.charAt(i))) {
                int tokenStart = i;
                i++;
                while (i < source.length() && isIdentifierCharacter(source.charAt(i))) {
                    i++;
                }
                if (source.substring(tokenStart, i).startsWith(prefix)) {
                    return true;
                }
                continue;
            }

            i++;
        }
        return false;
    }

    private static void collectIdentifierReferenceReplacements(String source,
                                                               String identifier,
                                                               String replacement,
                                                               List<TokenRange> excludedRanges,
                                                               List<SourceReplacement> replacements) {
        for (int i = 0; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            if (source.startsWith(identifier, i)
                && isIdentifierBoundary(source, i - 1)
                && isIdentifierBoundary(source, i + identifier.length())) {
                int end = i + identifier.length();
                if (!isInsideAnyRange(i, end, excludedRanges)) {
                    replacements.add(new SourceReplacement(i, end, replacement));
                }
                i = end;
                continue;
            }

            i++;
        }
    }

    private static boolean isInsideAnyRange(int start, int end, List<TokenRange> ranges) {
        for (TokenRange range : ranges) {
            if (start >= range.start && end <= range.end) {
                return true;
            }
        }
        return false;
    }

    private static String applyReplacements(String source, List<SourceReplacement> replacements) {
        Collections.sort(replacements, (left, right) -> Integer.compare(right.start, left.start));
        StringBuilder builder = new StringBuilder(source);
        int lastStart = source.length() + 1;
        for (SourceReplacement replacement : replacements) {
            if (replacement.end > lastStart) {
                continue;
            }
            builder.replace(replacement.start, replacement.end, replacement.replacement);
            lastStart = replacement.start;
        }
        return builder.toString();
    }

    private static int findTopLevelChar(String source, int start, int end, char target) {
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;

        for (int i = start; i < end;) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            char ch = source.charAt(i);
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')' && parenDepth > 0) {
                parenDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
            } else if (ch == '{') {
                braceDepth++;
            } else if (ch == '}' && braceDepth > 0) {
                braceDepth--;
            } else if (ch == target && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                return i;
            }
            i++;
        }

        return -1;
    }

    private static List<TokenRange> splitTopLevel(String source, int start, int end, char delimiter) {
        List<TokenRange> ranges = new ArrayList<>();
        int rangeStart = start;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;

        for (int i = start; i < end;) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            char ch = source.charAt(i);
            if (ch == '(') {
                parenDepth++;
            } else if (ch == ')' && parenDepth > 0) {
                parenDepth--;
            } else if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
            } else if (ch == '{') {
                braceDepth++;
            } else if (ch == '}' && braceDepth > 0) {
                braceDepth--;
            } else if (ch == delimiter && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                ranges.add(new TokenRange(rangeStart, i));
                rangeStart = i + 1;
            }
            i++;
        }

        ranges.add(new TokenRange(rangeStart, end));
        return ranges;
    }

    private static int findMatchingOpenParen(String source, int closeParen) {
        int depth = 0;
        for (int i = closeParen; i >= 0; i--) {
            char ch = source.charAt(i);
            if (ch == ')') {
                depth++;
            } else if (ch == '(') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findMatchingCloseBrace(String source, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < source.length();) {
            int skipped = skipNonCodeToken(source, i);
            if (skipped > i) {
                i = skipped;
                continue;
            }

            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static String removeEmptyExternalDeclarations(String packName, String programName, String source) {
        ReplacementResult replaced = removeEmptyExternalDeclarations(source);
        if (!replaced.changed) {
            return source;
        }

        LOGGER.warn("Removed empty external declarations from program {} in pack {}", programName, packName);
        return replaced.source;
    }

    private static ReplacementResult removeEmptyExternalDeclarations(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        int braceDepth = 0;
        boolean sawTopLevelContent = false;
        boolean topLevelBlockIsFunction = false;
        boolean changed = false;

        for (int i = 0; i < source.length();) {
            int copied = copySkippedToken(source, i, builder);
            if (copied > i) {
                i = copied;
                continue;
            }

            copied = copyPreprocessorDirective(source, i, builder);
            if (copied > i) {
                i = copied;
                continue;
            }

            char ch = source.charAt(i);
            if (braceDepth == 0) {
                if (Character.isWhitespace(ch)) {
                    builder.append(ch);
                    i++;
                    continue;
                }

                if (ch == ';') {
                    if (!sawTopLevelContent) {
                        changed = true;
                    } else {
                        builder.append(ch);
                        sawTopLevelContent = false;
                    }
                    i++;
                    continue;
                }

                if (ch == '{') {
                    topLevelBlockIsFunction = previousNonWhitespace(builder) == ')';
                    sawTopLevelContent = true;
                    braceDepth++;
                    builder.append(ch);
                    i++;
                    continue;
                }

                sawTopLevelContent = true;
                builder.append(ch);
                i++;
                continue;
            }

            if (ch == '{') {
                braceDepth++;
            } else if (ch == '}') {
                braceDepth--;
            }

            builder.append(ch);
            i++;

            if (braceDepth == 0 && topLevelBlockIsFunction) {
                sawTopLevelContent = false;
                topLevelBlockIsFunction = false;
            }
        }

        return new ReplacementResult(builder.toString(), changed);
    }

    private static String patchSildursWaterFract(String packName, String programName, String source) {
        ReplacementResult replaced = replaceSildursWaterFract(source);
        if (!replaced.changed) {
            return source;
        }

        LOGGER.warn("Patched fract(worldpos.y + 0.001) to fract(worldpos.y + 0.01) in vertex program {} from pack {}",
            programName, packName);
        return replaced.source;
    }

    private static ReplacementResult replaceSildursWaterFract(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        boolean changed = false;

        for (int i = 0; i < source.length();) {
            int copied = copySkippedToken(source, i, builder);
            if (copied > i) {
                i = copied;
                continue;
            }

            int replacementEnd = findSildursWaterFractEnd(source, i);
            if (replacementEnd > i) {
                builder.append(SILDURS_WATER_FRACT_REPLACEMENT);
                i = replacementEnd;
                changed = true;
                continue;
            }

            builder.append(source.charAt(i));
            i++;
        }

        return new ReplacementResult(builder.toString(), changed);
    }

    private static String patchLegacyMidTexCoordAlias(String packName, String programName, String source) {
        if (countIdentifierReferences(source, MID_TEX_COORD) > 0) {
            return source;
        }

        ReplacementResult replaced = replaceIdentifierReferences(source, LEGACY_MID_TEX_COORD, MID_TEX_COORD);
        if (!replaced.changed) {
            return source;
        }

        LOGGER.warn("Replaced legacy {} alias with {} in vertex program {} from pack {}",
            LEGACY_MID_TEX_COORD, MID_TEX_COORD, programName, packName);
        if (declaresTypedName(replaced.source, "vec4", MID_TEX_COORD)) {
            return replaced.source;
        }
        return injectGlobalDeclaration(replaced.source, MID_TEX_COORD_DECLARATION);
    }

    private static int findSildursWaterFractEnd(String source, int start) {
        if (!matchesToken(source, start, "fract")) {
            return -1;
        }

        int index = skipWhitespace(source, start + "fract".length());
        if (index >= source.length() || source.charAt(index) != '(') {
            return -1;
        }

        index = skipWhitespace(source, index + 1);
        if (!matchesToken(source, index, "worldpos")) {
            return -1;
        }

        index = skipWhitespace(source, index + "worldpos".length());
        if (index >= source.length() || source.charAt(index) != '.') {
            return -1;
        }

        index = skipWhitespace(source, index + 1);
        if (!matchesToken(source, index, "y")) {
            return -1;
        }

        index = skipWhitespace(source, index + 1);
        if (index >= source.length() || source.charAt(index) != '+') {
            return -1;
        }

        index = skipWhitespace(source, index + 1);
        if (!source.startsWith("0.001", index)) {
            return -1;
        }

        int afterLiteral = index + "0.001".length();
        if (afterLiteral < source.length() && isNumericSuffixCharacter(source.charAt(afterLiteral))) {
            return -1;
        }

        index = skipWhitespace(source, afterLiteral);
        if (index >= source.length() || source.charAt(index) != ')') {
            return -1;
        }

        return index + 1;
    }

    private static String patchCompositeTextureLodExtension(String packName, String programName, String source) {
        if (!isCompositeTransformProgram(programName)
            || !usesLegacyTextureLodFunction(source)
            || !isGlsl120OrOlder(source)) {
            return source;
        }

        LOGGER.debug("Injected {} for postprocess program {} from pack {}",
            TEXTURE_LOD_EXTENSION, programName, packName);
        return injectGlobalDeclaration(source, TEXTURE_LOD_EXTENSION_DECLARATION);
    }

    private static boolean isCompositeTransformProgram(String programName) {
        if (programName == null) {
            return false;
        }

        String normalized = programName.toLowerCase(Locale.ROOT);
        return normalized.startsWith("prepare")
            || normalized.startsWith("deferred")
            || normalized.startsWith("composite")
            || normalized.startsWith("final")
            || normalized.startsWith("shadowcomp");
    }

    private static boolean usesLegacyTextureLodFunction(String source) {
        return containsFunctionCall(source, "texture2DLod")
            || containsFunctionCall(source, "texture3DLod");
    }

    private static String patchMissingLegacyShiftedDither17(String packName, String programName, String source) {
        if (!isCompositeTransformProgram(programName)
            || !containsFunctionCall(source, SHIFTED_DITHER17)
            || definesFunction(source, SHIFTED_DITHER17)
            || !declaresTypedName(source, "int", FRAME_MOD)) {
            return source;
        }

        int insertionIndex = indexAfterLineContaining(source, FRAME_MOD_DECLARATION);
        if (insertionIndex < 0) {
            return source;
        }

        LOGGER.debug("Injected legacy {} fallback for program {} from pack {}",
            SHIFTED_DITHER17, programName, packName);
        StringBuilder builder = new StringBuilder(source.length() + LEGACY_SHIFTED_DITHER17_DECLARATION.length() + 2);
        builder.append(source, 0, insertionIndex);
        if (insertionIndex > 0 && source.charAt(insertionIndex - 1) != '\n') {
            builder.append('\n');
        }
        builder.append(LEGACY_SHIFTED_DITHER17_DECLARATION).append('\n');
        builder.append(source, insertionIndex, source.length());
        return builder.toString();
    }

    private static boolean definesFunction(String source, String functionName) {
        for (FunctionRange function : findFunctionRanges(source)) {
            if (functionName.equals(function.name)) {
                return true;
            }
        }
        return false;
    }

    private static int indexAfterLineContaining(String source, String needle) {
        int index = source.indexOf(needle);
        if (index < 0) {
            return -1;
        }

        int lineEnd = source.indexOf('\n', index + needle.length());
        return lineEnd < 0 ? source.length() : lineEnd + 1;
    }

    private static boolean containsFunctionCall(String source, String functionName) {
        for (int i = 0; i < source.length();) {
            StringBuilder ignored = new StringBuilder();
            int copied = copySkippedToken(source, i, ignored);
            if (copied > i) {
                i = copied;
                continue;
            }

            if (source.startsWith(functionName, i)
                && isIdentifierBoundary(source, i - 1)
                && isIdentifierBoundary(source, i + functionName.length())) {
                int afterName = skipWhitespace(source, i + functionName.length());
                if (afterName < source.length() && source.charAt(afterName) == '(') {
                    return true;
                }
            }

            i++;
        }

        return false;
    }

    private static boolean isGlsl120OrOlder(String source) {
        Matcher matcher = VERSION_DIRECTIVE.matcher(source);
        if (!matcher.find()) {
            return true;
        }

        try {
            return Integer.parseInt(matcher.group(1)) <= 120;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private static String patchCenterDepthSmooth(String packName, String programName, String source) {
        if (!isCompositeTransformProgram(programName)) {
            return source;
        }

        DeclarationRange declaration = findCenterDepthSmoothUniformDeclaration(source);
        if (declaration == null) {
            return source;
        }

        String withoutUniform = source.substring(0, declaration.start) + source.substring(declaration.end);
        ReplacementResult replaced = replaceIdentifierReferences(
            withoutUniform,
            CENTER_DEPTH_SMOOTH,
            CENTER_DEPTH_SAMPLE_EXPRESSION);

        String patched = declaresTypedName(replaced.source, "sampler2D", CENTER_DEPTH_SAMPLER)
            ? replaced.source
            : injectGlobalDeclaration(replaced.source, CENTER_DEPTH_SAMPLER_DECLARATION);

        LOGGER.debug("Replaced uniform float {} with sampler {} in program {} from pack {}",
            CENTER_DEPTH_SMOOTH, CENTER_DEPTH_SAMPLER, programName, packName);

        return patched;
    }

    private static DeclarationRange findCenterDepthSmoothUniformDeclaration(String source) {
        for (int i = 0; i < source.length();) {
            StringBuilder ignored = new StringBuilder();
            int copied = copySkippedToken(source, i, ignored);
            if (copied > i) {
                i = copied;
                continue;
            }

            int end = matchCenterDepthSmoothUniformDeclarationEnd(source, i);
            if (end > i) {
                return new DeclarationRange(i, end);
            }

            i++;
        }

        return null;
    }

    private static int matchCenterDepthSmoothUniformDeclarationEnd(String source, int start) {
        if (!matchesToken(source, start, "uniform")) {
            return -1;
        }

        int index = skipWhitespace(source, start + "uniform".length());
        if (!matchesToken(source, index, "float")) {
            return -1;
        }

        index = skipWhitespace(source, index + "float".length());
        if (!matchesToken(source, index, CENTER_DEPTH_SMOOTH)) {
            return -1;
        }

        index = skipWhitespace(source, index + CENTER_DEPTH_SMOOTH.length());
        return index < source.length() && source.charAt(index) == ';' ? index + 1 : -1;
    }

    private static String removeUniformDeclarations(String source, String typeName, String uniformName) {
        List<DeclarationRange> declarations = new ArrayList<>();

        for (int i = 0; i < source.length();) {
            StringBuilder ignored = new StringBuilder();
            int copied = copySkippedToken(source, i, ignored);
            if (copied > i) {
                i = copied;
                continue;
            }

            int end = matchUniformDeclarationEnd(source, i, typeName, uniformName);
            if (end > i) {
                declarations.add(new DeclarationRange(i, end));
                i = end;
                continue;
            }

            i++;
        }

        if (declarations.isEmpty()) {
            return source;
        }

        Collections.sort(declarations, (left, right) -> Integer.compare(right.start, left.start));
        StringBuilder builder = new StringBuilder(source);
        for (DeclarationRange declaration : declarations) {
            builder.delete(declaration.start, declaration.end);
        }
        return builder.toString();
    }

    private static int matchUniformDeclarationEnd(String source, int start, String typeName, String uniformName) {
        if (!matchesToken(source, start, "uniform")) {
            return -1;
        }

        int index = skipWhitespace(source, start + "uniform".length());
        if (!matchesToken(source, index, typeName)) {
            return -1;
        }

        index = skipWhitespace(source, index + typeName.length());
        if (!matchesToken(source, index, uniformName)) {
            return -1;
        }

        index = skipWhitespace(source, index + uniformName.length());
        return index < source.length() && source.charAt(index) == ';' ? index + 1 : -1;
    }

    private static String fixLegacyFogBlock(String packName, String programName, String source) {
        Matcher matcher = LEGACY_SET_FOG_COLOR_BLOCK.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        LOGGER.debug("Applied SET_FOG_COLOR compatibility shim to program {} from pack {}", programName, packName);
        return matcher.replaceAll(LEGACY_SET_FOG_COLOR_REPLACEMENT);
    }

    private static String patchAttributeTextureMatrices(String packName, String programName, ShaderType shaderType,
                                                        String source, InputAvailability availability) {
        ReplacementResult replaced = replaceIdentifierReferences(source, "gl_TextureMatrix", IRIS_TEXTURE_MATRIX);
        if (declaresTypedName(replaced.source, "mat4", IRIS_TEXTURE_MATRIX)) {
            return replaced.source;
        }

        if (replaced.changed) {
            LOGGER.debug("Replaced fixed-function texture matrix array for {} shader program {} from pack {} using {}",
                shaderType.name().toLowerCase(Locale.ROOT), programName, packName, availability);
        } else {
            LOGGER.debug("Injected fixed-function texture matrix array for {} shader program {} from pack {} using {}",
                shaderType.name().toLowerCase(Locale.ROOT), programName, packName, availability);
        }

        return injectGlobalDeclaration(replaced.source, buildAttributeTextureMatrixDeclarations(availability));
    }

    private static String buildAttributeTextureMatrixDeclarations(InputAvailability availability) {
        List<String> declarations = new ArrayList<>();
        declarations.add("const float " + ONE_OVER_256 + " = 0.00390625;");
        declarations.add("const float " + ONE_OVER_32 + " = " + ONE_OVER_256 + " * 8;");
        if (availability.lightmap) {
            declarations.add("mat4 " + LIGHTMAP_TEXTURE_MATRIX + " = gl_TextureMatrix[1];");
        } else {
            declarations.add("mat4 " + LIGHTMAP_TEXTURE_MATRIX + " = mat4("
                + ONE_OVER_256 + ", 0.0, 0.0, 0.0,"
                + " 0.0, " + ONE_OVER_256 + ", 0.0, 0.0,"
                + " 0.0, 0.0, " + ONE_OVER_256 + ", 0.0,"
                + " " + ONE_OVER_32 + ", " + ONE_OVER_32 + ", " + ONE_OVER_32 + ", " + ONE_OVER_256 + ");");
        }
        declarations.add("mat4 " + IRIS_TEXTURE_MATRIX + "[8] = mat4[8]("
            + "gl_TextureMatrix[0], "
            + LIGHTMAP_TEXTURE_MATRIX + ", "
            + "mat4(1.0), "
            + "mat4(1.0), "
            + "mat4(1.0), "
            + "mat4(1.0), "
            + "mat4(1.0), "
            + "mat4(1.0));");
        return joinLines(declarations);
    }

    private static String patchLightmapTextureMatrix(String packName, String programName, String source) {
        ReplacementResult replaced = replaceLightmapTextureMatrixReferences(source);
        if (!replaced.changed) {
            return source;
        }

        LOGGER.debug("Replaced gl_TextureMatrix[1] with {} in program {} from pack {}",
            LIGHTMAP_TEXTURE_MATRIX, programName, packName);

        if (declaresLightmapTextureMatrix(source)) {
            return replaced.source;
        }

        return injectGlobalDeclaration(replaced.source, LIGHTMAP_TEXTURE_MATRIX_DECLARATION);
    }

    private static ReplacementResult replaceLightmapTextureMatrixReferences(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        boolean changed = false;

        for (int i = 0; i < source.length();) {
            char ch = source.charAt(i);
            int copied = copySkippedToken(source, i, builder);
            if (copied > i) {
                i = copied;
                continue;
            }

            int replacementEnd = findTextureMatrixIndexOneEnd(source, i);
            if (replacementEnd > i) {
                builder.append(LIGHTMAP_TEXTURE_MATRIX);
                i = replacementEnd;
                changed = true;
                continue;
            }

            builder.append(ch);
            i++;
        }

        return new ReplacementResult(builder.toString(), changed);
    }

    private static ReplacementResult replaceIdentifierReferences(String source, String identifier, String replacement) {
        StringBuilder builder = new StringBuilder(source.length());
        boolean changed = false;

        for (int i = 0; i < source.length();) {
            int copied = copySkippedToken(source, i, builder);
            if (copied > i) {
                i = copied;
                continue;
            }

            if (source.startsWith(identifier, i)
                && isIdentifierBoundary(source, i - 1)
                && isIdentifierBoundary(source, i + identifier.length())) {
                builder.append(replacement);
                i += identifier.length();
                changed = true;
                continue;
            }

            builder.append(source.charAt(i));
            i++;
        }

        return new ReplacementResult(builder.toString(), changed);
    }

    private static int findTextureMatrixIndexOneEnd(String source, int start) {
        String symbol = "gl_TextureMatrix";
        if (!source.startsWith(symbol, start)) {
            return -1;
        }

        if (!isIdentifierBoundary(source, start - 1) || !isIdentifierBoundary(source, start + symbol.length())) {
            return -1;
        }

        int index = skipWhitespace(source, start + symbol.length());
        if (index >= source.length() || source.charAt(index) != '[') {
            return -1;
        }

        index = skipWhitespace(source, index + 1);
        if (index >= source.length() || source.charAt(index) != '1') {
            return -1;
        }

        index = skipWhitespace(source, index + 1);
        if (index >= source.length() || source.charAt(index) != ']') {
            return -1;
        }

        return index + 1;
    }

    private static boolean declaresLightmapTextureMatrix(String source) {
        return declaresTypedName(source, "mat4", LIGHTMAP_TEXTURE_MATRIX);
    }

    private static boolean declaresTypedName(String source, String declarationPrefix, String name) {
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < source.length();) {
            int copied = copySkippedToken(source, i, token);
            if (copied > i) {
                token.setLength(0);
                i = copied;
                continue;
            }

            if (source.startsWith(name, i)
                && isIdentifierBoundary(source, i - 1)
                && isIdentifierBoundary(source, i + name.length())) {
                int previous = previousTokenStart(source, i);
                if (matchesToken(source, previous, declarationPrefix)) {
                    return true;
                }
            }

            i++;
        }

        return false;
    }

    private static int previousTokenStart(String source, int beforeIndex) {
        int index = beforeIndex - 1;
        while (index >= 0 && Character.isWhitespace(source.charAt(index))) {
            index--;
        }
        while (index >= 0 && isIdentifierCharacter(source.charAt(index))) {
            index--;
        }
        return index + 1;
    }

    private static boolean matchesToken(String source, int start, String token) {
        return start >= 0
            && start + token.length() <= source.length()
            && source.startsWith(token, start)
            && isIdentifierBoundary(source, start - 1)
            && isIdentifierBoundary(source, start + token.length());
    }

    private static int copySkippedToken(String source, int start, StringBuilder output) {
        if (start + 1 >= source.length()) {
            return start;
        }

        char ch = source.charAt(start);
        char next = source.charAt(start + 1);
        if (ch == '/' && next == '/') {
            int end = source.indexOf('\n', start + 2);
            int copyEnd = end < 0 ? source.length() : end + 1;
            output.append(source, start, copyEnd);
            return copyEnd;
        }

        if (ch == '/' && next == '*') {
            int end = source.indexOf("*/", start + 2);
            int copyEnd = end < 0 ? source.length() : end + 2;
            output.append(source, start, copyEnd);
            return copyEnd;
        }

        if (ch == '"' || ch == '\'') {
            int i = start + 1;
            boolean escaped = false;
            while (i < source.length()) {
                char current = source.charAt(i++);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == ch) {
                    break;
                }
            }
            output.append(source, start, i);
            return i;
        }

        return start;
    }

    private static int copyPreprocessorDirective(String source, int start, StringBuilder output) {
        if (start >= source.length() || source.charAt(start) != '#' || !isLineStartIgnoringWhitespace(source, start)) {
            return start;
        }

        int end = source.indexOf('\n', start + 1);
        int copyEnd = end < 0 ? source.length() : end + 1;
        output.append(source, start, copyEnd);
        return copyEnd;
    }

    private static boolean isLineStartIgnoringWhitespace(String source, int index) {
        int cursor = index - 1;
        while (cursor >= 0 && source.charAt(cursor) != '\n' && source.charAt(cursor) != '\r') {
            if (!Character.isWhitespace(source.charAt(cursor))) {
                return false;
            }
            cursor--;
        }
        return true;
    }

    private static int skipNonCodeToken(String source, int start) {
        StringBuilder ignored = new StringBuilder();
        int skipped = copySkippedToken(source, start, ignored);
        if (skipped > start) {
            return skipped;
        }
        return copyPreprocessorDirective(source, start, ignored);
    }

    private static int skipWhitespace(String source, int start) {
        int index = start;
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int skipWhitespaceAndComments(String source, int start) {
        int index = start;
        for (;;) {
            index = skipWhitespace(source, index);

            StringBuilder ignored = new StringBuilder();
            int skipped = copySkippedToken(source, index, ignored);
            if (skipped <= index) {
                return index;
            }

            index = skipped;
        }
    }

    private static boolean isIdentifierBoundary(String source, int index) {
        return index < 0 || index >= source.length() || !isIdentifierCharacter(source.charAt(index));
    }

    private static boolean isIdentifierStart(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '_';
    }

    private static boolean isIdentifierCharacter(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_';
    }

    private static boolean isNumericSuffixCharacter(char ch) {
        return (ch >= '0' && ch <= '9')
            || (ch >= 'A' && ch <= 'Z')
            || (ch >= 'a' && ch <= 'z')
            || ch == '.'
            || ch == '_';
    }

    private static char previousNonWhitespace(StringBuilder builder) {
        for (int i = builder.length() - 1; i >= 0; i--) {
            char ch = builder.charAt(i);
            if (!Character.isWhitespace(ch)) {
                return ch;
            }
        }
        return '\0';
    }

    private static char previousNonWhitespace(String source, int beforeIndex) {
        int index = previousNonWhitespaceIndex(source, beforeIndex);
        return index >= 0 ? source.charAt(index) : '\0';
    }

    private static int previousNonWhitespaceIndex(String source, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            char ch = source.charAt(i);
            if (!Character.isWhitespace(ch)) {
                return i;
            }
        }
        return -1;
    }

    private static String injectGlobalDeclaration(String source, String declaration) {
        String[] lines = source.split("\n", -1);
        int insertLine = 0;
        while (insertLine < lines.length) {
            String trimmed = lines[insertLine].trim();
            if (trimmed.isEmpty()
                || trimmed.startsWith("#version")
                || trimmed.startsWith("#extension")
                || trimmed.startsWith("#define")
                || trimmed.startsWith("#line")
                || trimmed.startsWith("//")) {
                insertLine++;
                continue;
            }
            break;
        }

        StringBuilder builder = new StringBuilder(source.length() + declaration.length() + 1);
        for (int i = 0; i < lines.length; i++) {
            if (i == insertLine) {
                builder.append(declaration).append('\n');
            }
            builder.append(lines[i]);
            if (i + 1 < lines.length) {
                builder.append('\n');
            }
        }
        if (insertLine == lines.length) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(declaration).append('\n');
        }
        return builder.toString();
    }

    private static final class ReplacementResult {
        private final String source;
        private final boolean changed;

        private ReplacementResult(String source, boolean changed) {
            this.source = source;
            this.changed = changed;
        }
    }

    private static final class UnusedFunctionRemovalResult {
        private final String source;
        private final boolean changed;
        private final List<String> removedFunctionNames;

        private UnusedFunctionRemovalResult(String source, boolean changed, List<String> removedFunctionNames) {
            this.source = source;
            this.changed = changed;
            this.removedFunctionNames = removedFunctionNames;
        }
    }

    private static final class DuplicateFunctionRemovalResult {
        private final String source;
        private final boolean changed;
        private final List<String> removedFunctionNames;

        private DuplicateFunctionRemovalResult(String source, boolean changed, List<String> removedFunctionNames) {
            this.source = source;
            this.changed = changed;
            this.removedFunctionNames = removedFunctionNames;
        }
    }

    private static final class DeclarationRange {
        private final int start;
        private final int end;

        private DeclarationRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class FunctionRange {
        private final String name;
        private final int declarationStart;
        private final int parametersStart;
        private final int parametersEnd;
        private final int bodyStart;
        private final int bodyEnd;

        private FunctionRange(String name, int declarationStart, int parametersStart, int parametersEnd,
                              int bodyStart, int bodyEnd) {
            this.name = name;
            this.declarationStart = declarationStart;
            this.parametersStart = parametersStart;
            this.parametersEnd = parametersEnd;
            this.bodyStart = bodyStart;
            this.bodyEnd = bodyEnd;
        }
    }

    private static final class LocalConstDeclaration {
        private final TokenRange constToken;
        private final List<String> memberNames;
        private final List<TokenRange> initializerRanges;
        private boolean removeConst;

        private LocalConstDeclaration(TokenRange constToken, List<String> memberNames, List<TokenRange> initializerRanges) {
            this.constToken = constToken;
            this.memberNames = memberNames;
            this.initializerRanges = initializerRanges;
        }
    }

    private static final class TokenRange {
        private final int start;
        private final int end;

        private TokenRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class SourceReplacement {
        private final int start;
        private final int end;
        private final String replacement;

        private SourceReplacement(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }
    }

    private static final class StagePatchSource {
        private final ShaderType type;
        private String source;

        private StagePatchSource(ShaderType type, String source) {
            this.type = type;
            this.source = source;
        }
    }

    private static final class InterfaceDeclaration {
        private final String qualifierPrefix;
        private final String storage;
        private final String typeName;
        private final List<InterfaceMember> members;
        private final int declarationStart;
        private final int declarationEnd;
        private final int typeStart;
        private final int typeEnd;

        private InterfaceDeclaration(String qualifierPrefix, String storage, String typeName,
                                     int declarationStart, int declarationEnd, int typeStart, int typeEnd,
                                     List<InterfaceMember> members) {
            this.qualifierPrefix = qualifierPrefix == null ? "" : qualifierPrefix;
            this.storage = storage;
            this.typeName = typeName;
            this.declarationStart = declarationStart;
            this.declarationEnd = declarationEnd;
            this.typeStart = typeStart;
            this.typeEnd = typeEnd;
            this.members = members;
        }

        private String toPreviousStageDeclaration(String name) {
            return qualifierPrefix + previousStageStorage() + " " + typeName + " " + name + ";";
        }

        private String previousStageStorage() {
            return "varying".equals(storage) ? "varying" : "out";
        }

        private InterfaceMember memberNamed(String name) {
            for (InterfaceMember member : members) {
                if (member.name.equals(name)) {
                    return member;
                }
            }
            return null;
        }

        private String withoutMember(InterfaceMember removedMember) {
            List<String> remaining = new ArrayList<>();
            for (InterfaceMember member : members) {
                if (member != removedMember) {
                    remaining.add(memberSource(member));
                }
            }
            return qualifierPrefix + storage + " " + typeName + " " + joinLinesWithComma(remaining) + ";";
        }

        private String withTypeAndSingleMember(String newTypeName, String name) {
            return qualifierPrefix + storage + " " + newTypeName + " " + name + ";";
        }

        private String memberSource(InterfaceMember member) {
            return member.sourceText;
        }
    }

    private static String joinLinesWithComma(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static final class InterfaceMember {
        private final String name;
        private final int nameStart;
        private final int nameEnd;
        private final String sourceText;

        private InterfaceMember(String name, int nameStart, int nameEnd, String sourceText) {
            this.name = name;
            this.nameStart = nameStart;
            this.nameEnd = nameEnd;
            this.sourceText = sourceText;
        }
    }

    public static final class PatchedShaderSources {
        private final String vertexSource;
        private final String geometrySource;
        private final String fragmentSource;

        private PatchedShaderSources(String vertexSource, String geometrySource, String fragmentSource) {
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
