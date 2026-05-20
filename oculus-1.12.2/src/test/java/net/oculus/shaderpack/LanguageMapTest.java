package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LanguageMapTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsLegacyUtf8LanguageFilesWithNormalizedCodes() throws Exception {
        Path langRoot = temporaryFolder.newFolder("lang").toPath();
        Files.write(langRoot.resolve("en_US.lang"),
            java.util.Collections.singletonList("option.TEST=\u00A7eTranslated Value"),
            StandardCharsets.UTF_8);

        LanguageMap languageMap = new LanguageMap(langRoot);

        assertTrue(languageMap.getLanguages().contains("en_us"));
        assertEquals("\u00A7eTranslated Value", languageMap.get("en_us", "option.TEST"));
    }
}
