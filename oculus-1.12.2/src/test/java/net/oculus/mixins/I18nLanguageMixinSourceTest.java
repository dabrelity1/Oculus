package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class I18nLanguageMixinSourceTest {
    @Test
    public void clientLocaleMixinAddsShaderPackEntriesWithoutOverridingVanillaKeys() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/ClientLocaleLanguageMixin.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("@Mixin(value = net.minecraft.client.resources.Locale.class, priority = 990)"));
        assertTrue(source.contains("@Inject(method = \"loadLocaleDataFiles\""));
        assertTrue(source.contains("IrisLanguageJsonLoader.loadInto(this.properties, languageCodes)"));
        assertTrue(source.contains("properties.containsKey(key)"));
        assertTrue(source.contains("ShaderPackLanguageLookup.lookupActive(key)"));
        assertTrue(source.contains("ShaderPackLanguageLookup.formatVanilla(translation, parameters)"));
        assertTrue(source.contains("@Inject(method = \"formatMessage(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;\""));
        assertTrue(source.contains("@Inject(method = \"hasKey(Ljava/lang/String;)Z\""));
    }

    @Test
    public void textLanguageMapMixinKeepsTextComponentsInSyncWithShaderPackEntries() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/TextLanguageMapMixin.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("@Mixin(value = net.minecraft.util.text.translation.LanguageMap.class, priority = 990)"));
        assertTrue(source.contains("languageList.containsKey(key)"));
        assertTrue(source.contains("ShaderPackLanguageLookup.lookupActive(key)"));
        assertTrue(source.contains("ShaderPackLanguageLookup.getTextComponentLanguageVersion(cir.getReturnValue())"));
        assertTrue(source.contains("@Inject(method = \"translateKey(Ljava/lang/String;)Ljava/lang/String;\""));
        assertTrue(source.contains("@Inject(method = \"translateKeyFormat(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;\""));
        assertTrue(source.contains("@Inject(method = \"isKeyTranslated(Ljava/lang/String;)Z\""));
    }

    @Test
    public void mixinConfigQueuesBothLanguageHooks() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/oculus.mixins.json")), StandardCharsets.UTF_8);

        assertTrue(source.contains("\"pipeline.ClientLocaleLanguageMixin\""));
        assertTrue(source.contains("\"pipeline.TextLanguageMapMixin\""));
    }
}
