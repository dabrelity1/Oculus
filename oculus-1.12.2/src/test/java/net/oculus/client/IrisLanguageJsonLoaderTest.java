package net.oculus.client;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class IrisLanguageJsonLoaderTest {
    @Test
    public void bundledJsonLanguagesLoadInLegacyOrderAndOverrideEnglish() {
        Map<String, String> target = new HashMap<>();

        int loaded = IrisLanguageJsonLoader.loadInto(target, Arrays.asList("en_us", "pt_br"));

        assertEquals(2, loaded);
        assertEquals("Aplicar", target.get("options.iris.apply"));
        assertEquals("Recarregar shaders", target.get("iris.keybind.reload"));
    }

    @Test
    public void missingBundledLanguagesAreIgnored() {
        Map<String, String> target = new HashMap<>();

        int loaded = IrisLanguageJsonLoader.loadInto(target, Arrays.asList("zz_zz"));

        assertEquals(0, loaded);
        assertEquals(0, target.size());
    }

    @Test
    public void nullLanguageListLoadsEnglishFallback() {
        Map<String, String> target = new HashMap<>();

        int loaded = IrisLanguageJsonLoader.loadInto(target, null);

        assertEquals(1, loaded);
        assertEquals("Apply", target.get("options.iris.apply"));
    }
}
