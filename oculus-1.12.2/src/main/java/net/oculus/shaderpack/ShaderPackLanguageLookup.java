package net.oculus.shaderpack;

import java.util.IllegalFormatException;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.oculus.pipeline.PipelineManager;

/**
 * Shared lookup path for OptiFine-style shader pack language entries.
 */
public final class ShaderPackLanguageLookup {
    public static final String FALLBACK_LANGUAGE = "en_us";

    private ShaderPackLanguageLookup() {
    }

    public static String lookupActive(String key) {
        return lookup(PipelineManager.INSTANCE.getActivePack(), key, currentLanguageCode());
    }

    public static String lookup(ShaderPack pack, String key) {
        return lookup(pack, key, currentLanguageCode());
    }

    public static String lookup(ShaderPack pack, String key, String configuredLanguage) {
        if (pack == null) {
            return null;
        }

        return lookup(pack.getLanguageMap(), key, configuredLanguage);
    }

    public static String lookup(LanguageMap languageMap, String key, String configuredLanguage) {
        if (languageMap == null || key == null) {
            return null;
        }

        String language = normalizeLanguageCode(configuredLanguage);
        String translated = languageMap.get(language, key);
        if (translated != null) {
            return translated;
        }

        if (!FALLBACK_LANGUAGE.equals(language)) {
            return languageMap.get(FALLBACK_LANGUAGE, key);
        }

        return null;
    }

    public static String currentLanguageCode() {
        Minecraft minecraft = Minecraft.getMinecraft();
        String language = minecraft != null && minecraft.gameSettings != null
            ? minecraft.gameSettings.language
            : null;
        return normalizeLanguageCode(language);
    }

    public static String normalizeLanguageCode(String language) {
        if (language == null || language.trim().isEmpty()) {
            return FALLBACK_LANGUAGE;
        }

        return language.toLowerCase(Locale.ROOT);
    }

    public static String formatVanilla(String translation, Object... args) {
        if (translation == null) {
            return null;
        }

        try {
            return String.format(translation, args == null ? new Object[0] : args);
        } catch (IllegalFormatException exception) {
            return "Format error: " + translation;
        }
    }

    public static String formatLenient(String translation, Object... args) {
        if (translation == null || args == null || args.length == 0) {
            return translation;
        }

        try {
            return String.format(Locale.ROOT, translation, args);
        } catch (RuntimeException ignored) {
            return translation;
        }
    }

    public static long getTextComponentLanguageVersion(long vanillaVersion) {
        return vanillaVersion + PipelineManager.INSTANCE.getVersionCounterForSodiumShaderReload();
    }
}
