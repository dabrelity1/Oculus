package net.oculus.shaderpack;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;

import net.oculus.Oculus;

/**
 * Loads OptiFine-style language .lang files bundled with shader packs.
 */
public final class LanguageMap {
    private final Map<String, Map<String, String>> translations = new HashMap<>();

    public LanguageMap(Path langRoot) {
        if (langRoot == null || !Files.exists(langRoot)) {
            return;
        }

        try (Stream<Path> stream = Files.list(langRoot)) {
            stream.filter(path -> !Files.isDirectory(path))
                .filter(LanguageMap::hasLangExtension)
                .forEach(this::readLanguage);
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to enumerate shader pack languages at {}", langRoot, exception);
        }
    }

    private static boolean hasLangExtension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".lang");
    }

    private void readLanguage(Path path) {
        String fileName = path.getFileName().toString();
        String langCode = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);

        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            Oculus.LOGGER.error("Failed to parse shader language file {}", path, exception);
            return;
        }

        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        properties.forEach((key, value) -> builder.put(key.toString(), value.toString()));
        translations.put(langCode, builder.build());
    }

    public Set<String> getLanguages() {
        return Collections.unmodifiableSet(translations.keySet());
    }

    public Map<String, String> getTranslations(String language) {
        return translations.getOrDefault(language, Collections.emptyMap());
    }
}
