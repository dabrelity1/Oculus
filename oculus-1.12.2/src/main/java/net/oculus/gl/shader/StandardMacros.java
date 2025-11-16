package net.oculus.gl.shader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.shaderpack.StringPair;

/**
 * Provides the OptiFine-compatible macro set used when preprocessing shader pack files.
 */
public final class StandardMacros {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
    private static final Pattern GL_VERSION_PATTERN = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)(?:\\.(?<bugfix>\\d+))?.*");
    private static final int MINIMUM_COMPAT_VERSION = 11605;
    private static final String VERSION_OVERRIDE_PROPERTY = "oculus.mcVersionOverride";
    private static final String DEFAULT_HAND_DEPTH = "0.125";

    private StandardMacros() {
    }

    public static Iterable<StringPair> createStandardEnvironmentDefines() {
        List<StringPair> defines = new ArrayList<>();

        define(defines, "MC_VERSION", getEffectiveMinecraftVersion());
        define(defines, "MC_GL_VERSION", getGlVersion(GL11.GL_VERSION));
        define(defines, "MC_GLSL_VERSION", getGlVersion(GL20.GL_SHADING_LANGUAGE_VERSION));

        String osMacro = getOsMacro();
        if (osMacro != null) {
            define(defines, osMacro);
        }

        String vendorMacro = getVendorMacro();
        if (vendorMacro != null) {
            define(defines, vendorMacro);
        }

        String rendererMacro = getRendererMacro();
        if (rendererMacro != null) {
            define(defines, rendererMacro);
        }

        for (String extension : getGlExtensions()) {
            define(defines, extension);
        }

        define(defines, "MC_NORMAL_MAP");
        define(defines, "MC_SPECULAR_MAP");
        define(defines, "MC_RENDER_QUALITY", "1.0");
        define(defines, "MC_SHADOW_QUALITY", "1.0");
        define(defines, "MC_HAND_DEPTH", DEFAULT_HAND_DEPTH);

        getRenderStages().forEach((stage, index) -> define(defines, stage, index));

        return defines;
    }

    public static Map<String, String> getRenderStages() {
        Map<String, String> stages = new HashMap<>();
        for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
            stages.put("MC_RENDER_STAGE_" + phase.name(), String.valueOf(phase.ordinal()));
        }
        return stages;
    }

    private static void define(List<StringPair> defines, String key) {
        define(defines, key, "");
    }

    private static void define(List<StringPair> defines, String key, String value) {
        defines.add(new StringPair(key, value));
    }

    private static String getEffectiveMinecraftVersion() {
        int override = parseOverride();
        if (override > 0) {
            return formatVersion(override);
        }

        String reported = null;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null) {
            reported = minecraft.getVersion();
        }

        int parsed = parseMinecraftVersion(reported);
        if (parsed < MINIMUM_COMPAT_VERSION) {
            parsed = MINIMUM_COMPAT_VERSION;
        }

        return formatVersion(parsed);
    }

    private static int parseOverride() {
        String property = System.getProperty(VERSION_OVERRIDE_PROPERTY);
        if (property == null) {
            return -1;
        }
        try {
            return Integer.parseInt(property.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int parseMinecraftVersion(String versionString) {
        if (versionString == null || versionString.trim().isEmpty()) {
            return -1;
        }

        Matcher matcher = VERSION_PATTERN.matcher(versionString.trim());
        if (!matcher.matches()) {
            return -1;
        }

        String major = matcher.group(1);
        String minor = matcher.group(2);
        String bugfix = matcher.group(3);

        int majorInt = parsePart(major);
        int minorInt = parsePart(minor);
        int bugfixInt = parsePart(bugfix);

        if (minorInt < 0) {
            minorInt = 0;
        }
        if (bugfixInt < 0) {
            bugfixInt = 0;
        }

        return majorInt * 10000 + minorInt * 100 + bugfixInt;
    }

    private static int parsePart(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String formatVersion(int version) {
        return String.format(Locale.ROOT, "%05d", Math.max(version, 0));
    }

    private static String getGlVersion(int parameter) {
        String info = GL11.glGetString(parameter);
        if (info == null) {
            return formatVersion(MINIMUM_COMPAT_VERSION);
        }

        Matcher matcher = GL_VERSION_PATTERN.matcher(info.trim());
        if (!matcher.matches()) {
            return formatVersion(MINIMUM_COMPAT_VERSION);
        }

        String major = matcher.group("major");
        String minor = matcher.group("minor");
        String bugfix = matcher.group("bugfix");

        int majorInt = parsePart(major);
        int minorInt = parsePart(minor);
        int bugfixInt = parsePart(bugfix);
        if (bugfixInt < 0) {
            bugfixInt = 0;
        }

        int combined = Math.max(majorInt, 0) * 100 + Math.max(minorInt, 0) * 10 + bugfixInt;
        return String.format(Locale.ROOT, "%04d", combined);
    }

    private static String getOsMacro() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return "MC_OS_WINDOWS";
        }
        if (name.contains("mac")) {
            return "MC_OS_MAC";
        }
        if (name.contains("nux") || name.contains("nix")) {
            return "MC_OS_LINUX";
        }
        return "MC_OS_UNKNOWN";
    }

    private static String getVendorMacro() {
        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        if (vendor == null) {
            return null;
        }
        String lower = vendor.toLowerCase(Locale.ROOT);
        if (lower.startsWith("nvidia")) {
            return "MC_GL_VENDOR_NVIDIA";
        }
        if (lower.startsWith("ati") || lower.startsWith("amd")) {
            return "MC_GL_VENDOR_AMD";
        }
        if (lower.startsWith("intel")) {
            return "MC_GL_VENDOR_INTEL";
        }
        if (lower.startsWith("x.org")) {
            return "MC_GL_VENDOR_XORG";
        }
        return "MC_GL_VENDOR_OTHER";
    }

    private static String getRendererMacro() {
        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        if (renderer == null) {
            return null;
        }
        String lower = renderer.toLowerCase(Locale.ROOT);
        if (lower.startsWith("geforce") || lower.startsWith("nvidia")) {
            return "MC_GL_RENDERER_GEFORCE";
        }
        if (lower.startsWith("quadro")) {
            return "MC_GL_RENDERER_QUADRO";
        }
        if (lower.startsWith("radeon") || lower.startsWith("amd") || lower.startsWith("ati")) {
            return "MC_GL_RENDERER_RADEON";
        }
        if (lower.startsWith("intel")) {
            return "MC_GL_RENDERER_INTEL";
        }
        if (lower.startsWith("gallium")) {
            return "MC_GL_RENDERER_GALLIUM";
        }
        if (lower.startsWith("mesa")) {
            return "MC_GL_RENDERER_MESA";
        }
        return "MC_GL_RENDERER_OTHER";
    }

    private static Set<String> getGlExtensions() {
        Set<String> extensions = new HashSet<>();
        String raw = GL11.glGetString(GL11.GL_EXTENSIONS);
        if (raw == null || raw.isEmpty()) {
            return extensions;
        }

        String[] split = raw.split("\\s+");
        for (String extension : split) {
            if (extension.isEmpty()) {
                continue;
            }
            extensions.add("MC_" + extension);
        }

        return extensions;
    }
}
