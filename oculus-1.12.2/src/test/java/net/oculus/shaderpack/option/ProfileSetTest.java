package net.oculus.shaderpack.option;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.oculus.shaderpack.option.ProfileSet.ProfileResult;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileSetTest {
    @Test
    public void resolvesInheritanceNegationAndProgramDisables() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("base", Arrays.asList("bloom", "exposure=medium"));
        definitions.put("cinematic", Arrays.asList("profile.base", "!clouds", "exposure=high", "!program.shadow"));

        OptionSet optionSet = optionSetBuilder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "bloom", "Bloom", false))
            .addBoolean(new BooleanOption(OptionType.DEFINE, "clouds", "Clouds", true))
            .build();

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSet);
        Profile cinematic = profileSet.get("cinematic").orElseThrow(() -> new AssertionError("Profile missing"));

        assertEquals("true", cinematic.optionValues.get("bloom"));
        assertEquals("false", cinematic.optionValues.get("clouds"));
        assertEquals("high", cinematic.optionValues.get("exposure"));
        assertTrue("disabled program captured", cinematic.disabledPrograms.contains("shadow"));
    }

    @Test
    public void scanIdentifiesMatchingProfile() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("quality", Arrays.asList("bloom", "clouds"));
        definitions.put("performance", Arrays.asList("!bloom", "!clouds"));

        OptionSet optionSet = optionSetBuilder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "bloom", "Bloom", false))
            .addBoolean(new BooleanOption(OptionType.DEFINE, "clouds", "Clouds", true))
            .build();

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSet);

        MutableOptionValues values = new MutableOptionValues(optionSet);
        values.setBooleanValue("bloom", true);
        values.setBooleanValue("clouds", true);

        ProfileResult result = profileSet.scan(optionSet, values);
        assertTrue(result.current.isPresent());
        assertEquals("quality", result.current.get().name);
        assertTrue(result.next.isPresent());
        assertEquals("performance", result.next.get().name);
        assertTrue(result.previous.isPresent());
        assertEquals("performance", result.previous.get().name);
    }

    @Test
    public void scanProvidesFallbackWhenNoProfilesMatch() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("balanced", Arrays.asList("mode=balanced"));
        definitions.put("cinematic", Arrays.asList("mode=cinematic"));

        StringOption modeOption = StringOption.create(OptionType.DEFINE, "mode", "Mode [balanced cinematic]", "balanced");
        if (modeOption == null) {
            throw new AssertionError("Expected StringOption to be created");
        }
        OptionSet optionSet = optionSetBuilder()
            .addString(modeOption)
            .build();

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSet);
        MutableOptionValues values = new MutableOptionValues(optionSet);
        values.setStringValue("mode", "experimental");

        ProfileResult result = profileSet.scan(optionSet, values);
        assertFalse(result.current.isPresent());
        assertTrue(result.next.isPresent());
        assertEquals("balanced", result.next.get().name);
        assertTrue(result.previous.isPresent());
        assertEquals("cinematic", result.previous.get().name);
    }

    private OptionSet.Builder optionSetBuilder() {
        return OptionSet.builder();
    }
}
