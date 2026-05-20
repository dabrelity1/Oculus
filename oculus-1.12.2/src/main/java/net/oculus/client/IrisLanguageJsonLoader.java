package net.oculus.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.oculus.Oculus;
import net.oculus.shaderpack.ShaderPackLanguageLookup;

/**
 * Loads bundled modern Iris/Oculus JSON translations into Minecraft 1.12's legacy language map.
 */
public final class IrisLanguageJsonLoader {
    private IrisLanguageJsonLoader() {
    }

    public static int loadInto(Map<String, String> target, List<String> languageCodes) {
        if (target == null) {
            return 0;
        }

        List<String> codes = languageCodes == null || languageCodes.isEmpty()
            ? Collections.singletonList(ShaderPackLanguageLookup.FALLBACK_LANGUAGE)
            : languageCodes;
        int loaded = 0;

        for (String code : codes) {
            if (loadLanguage(target, ShaderPackLanguageLookup.normalizeLanguageCode(code))) {
                loaded++;
            }
        }

        return loaded;
    }

    private static boolean loadLanguage(Map<String, String> target, String languageCode) {
        String resourcePath = "/assets/iris/lang/" + languageCode + ".json";

        try (InputStream stream = IrisLanguageJsonLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return false;
            }

            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject object = new JsonParser().parse(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        target.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            return true;
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            Oculus.LOGGER.warn("Failed to load bundled Iris language resource {}", resourcePath, exception);
            return false;
        }
    }
}
