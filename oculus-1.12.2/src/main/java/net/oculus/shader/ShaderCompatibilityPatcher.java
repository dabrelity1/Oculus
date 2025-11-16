package net.oculus.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.oculus.gl.shader.ShaderType;

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
        if (source == null || shaderType == null) {
            return source;
        }

        if (shaderType != ShaderType.FRAGMENT) {
            return source;
        }

        return fixLegacyFogBlock(packName, programName, source);
    }

    private static String fixLegacyFogBlock(String packName, String programName, String source) {
        Matcher matcher = LEGACY_SET_FOG_COLOR_BLOCK.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        LOGGER.debug("Applied SET_FOG_COLOR compatibility shim to program {} from pack {}", programName, packName);
        return matcher.replaceAll(LEGACY_SET_FOG_COLOR_REPLACEMENT);
    }
}
