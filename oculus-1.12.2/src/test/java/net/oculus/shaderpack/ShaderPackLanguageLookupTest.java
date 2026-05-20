package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ShaderPackLanguageLookupTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void configuredLanguageWinsAndEnglishFallbackIsUsed() throws Exception {
        Path langRoot = temporaryFolder.newFolder("lang").toPath();
        Files.write(langRoot.resolve("en_US.lang"), java.util.Arrays.asList(
            "option.TEST=English",
            "option.ONLY_EN=English Only"
        ), StandardCharsets.UTF_8);
        Files.write(langRoot.resolve("pt_BR.lang"), java.util.Collections.singletonList(
            "option.TEST=Portuguese"
        ), StandardCharsets.UTF_8);

        LanguageMap languageMap = new LanguageMap(langRoot);

        assertEquals("Portuguese", ShaderPackLanguageLookup.lookup(languageMap, "option.TEST", "pt_BR"));
        assertEquals("English Only", ShaderPackLanguageLookup.lookup(languageMap, "option.ONLY_EN", "pt_BR"));
        assertNull(ShaderPackLanguageLookup.lookup(languageMap, "option.MISSING", "pt_BR"));
    }

    @Test
    public void languageCodesNormalizeToLegacyLowercaseAndDefaultToEnglish() {
        assertEquals("pt_br", ShaderPackLanguageLookup.normalizeLanguageCode("pt_BR"));
        assertEquals("en_us", ShaderPackLanguageLookup.normalizeLanguageCode(""));
        assertEquals("en_us", ShaderPackLanguageLookup.normalizeLanguageCode(null));
    }

    @Test
    public void vanillaFormattingMatchesLegacyMinecraftFailureText() {
        assertEquals("Hello world", ShaderPackLanguageLookup.formatVanilla("Hello %s", "world"));
        assertEquals("Format error: Broken %", ShaderPackLanguageLookup.formatVanilla("Broken %"));
        assertEquals("Broken %", ShaderPackLanguageLookup.formatLenient("Broken %", "ignored"));
    }
}
