package net.oculus.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class InternalTranslationResourceTest {
    private static final Pattern JAVA_KEY_PATTERN = Pattern.compile(
        "\"((?:iris\\.[^\"]+)|(?:options\\.iris[^\"]+)|(?:pack\\.iris[^\"]+)|(?:label\\.iris[^\"]+)|profile\\.comment)\""
    );

    @Test
    public void allGuiTranslationKeysUsedBySourceHaveEnglishResources() throws Exception {
        Set<String> used = collectUsedGuiKeys();
        Set<String> available = new HashSet<>();
        available.addAll(readLegacyLangKeys("src/main/resources/assets/oculus/lang/en_us.lang"));
        available.addAll(readJsonKeys("src/main/resources/assets/iris/lang/en_us.json"));

        Set<String> missing = new TreeSet<>(used);
        missing.removeAll(available);
        missing.remove("iris.features.required");
        missing.remove("iris.features.optional");

        assertTrue("Missing English translations for " + missing, missing.isEmpty());
    }

    @Test
    public void bundledEnglishJsonContains1_12GuiOnlyKeys() {
        Map<String, String> loaded = new java.util.HashMap<>();

        IrisLanguageJsonLoader.loadInto(loaded, java.util.Collections.singletonList("en_us"));

        assertFalse(loaded.get("options.iris.noShaderOptions").isEmpty());
        assertFalse(loaded.get("options.iris.reset.pendingApply").isEmpty());
        assertFalse(loaded.get("options.iris.importSettings.success").isEmpty());
        assertFalse(loaded.get("options.iris.exportSettings.success").isEmpty());
    }

    private static Set<String> collectUsedGuiKeys() throws Exception {
        Set<String> keys = new HashSet<>();
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(Paths.get("src/main/java/net/oculus/gui"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        Matcher matcher = JAVA_KEY_PATTERN.matcher(source);
                        while (matcher.find()) {
                            keys.add(matcher.group(1));
                        }
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
        }
        return keys;
    }

    private static Set<String> readLegacyLangKeys(String path) throws Exception {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separator = line.indexOf('=');
            if (separator > 0) {
                keys.add(line.substring(0, separator));
            }
        }
        return keys;
    }

    private static Set<String> readJsonKeys(String path) throws Exception {
        JsonObject object = new JsonParser()
            .parse(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8))
            .getAsJsonObject();
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            keys.add(entry.getKey());
        }
        return keys;
    }
}
