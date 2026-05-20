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
import static org.junit.Assert.fail;

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
    public void unknownProfileAssignmentsDoNotPreventMatchingKnownOptionsLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("quality", Arrays.asList("bloom", "UNKNOWN_OPTION=ignored"));
        definitions.put("performance", Arrays.asList("!bloom"));

        OptionSet optionSet = optionSetBuilder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "bloom", "Bloom", false))
            .build();

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSet);
        MutableOptionValues values = new MutableOptionValues(optionSet);
        values.setBooleanValue("bloom", true);

        ProfileResult result = profileSet.scan(optionSet, values);
        assertTrue(result.current.isPresent());
        assertEquals("quality", result.current.get().name);
    }

    @Test
    public void negatedUnknownOptionsStillAffectProfilePrecedenceLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("fallback", Arrays.asList("bloom"));
        definitions.put("specific", Arrays.asList("bloom", "!UNKNOWN_OPTION"));

        OptionSet optionSet = optionSetBuilder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "bloom", "Bloom", false))
            .build();

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSet);
        MutableOptionValues values = new MutableOptionValues(optionSet);
        values.setBooleanValue("bloom", true);

        ProfileResult result = profileSet.scan(optionSet, values);
        assertTrue(result.current.isPresent());
        assertEquals("specific", result.current.get().name);
    }

    @Test
    public void negatedStringOptionRequiresLiteralFalseLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("disabledMode", Arrays.asList("!mode"));

        StringOption modeOption = StringOption.create(OptionType.DEFINE, "mode", "Mode [fast false]", "fast");
        if (modeOption == null) {
            throw new AssertionError("Expected StringOption to be created");
        }
        OptionSet optionSet = optionSetBuilder()
            .addString(modeOption)
            .build();

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSet);
        Profile profile = profileSet.get("disabledMode").orElseThrow(() -> new AssertionError("Profile missing"));
        assertEquals("false", profile.optionValues.get("mode"));

        MutableOptionValues values = new MutableOptionValues(optionSet);
        values.setStringValue("mode", "fast");
        assertFalse(profileSet.scan(optionSet, values).current.isPresent());

        values.setStringValue("mode", "false");
        ProfileResult result = profileSet.scan(optionSet, values);
        assertTrue(result.current.isPresent());
        assertEquals("disabledMode", result.current.get().name);
    }

    @Test
    public void profileAssignmentUsesEqualsBeforeColonLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("compound", Arrays.asList("MODE:QUALITY=high"));

        OptionSet optionSet = optionSetBuilder().build();

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSet);
        Profile profile = profileSet.get("compound").orElseThrow(() -> new AssertionError("Profile missing"));

        assertEquals("high", profile.optionValues.get("MODE:QUALITY"));
        assertFalse(profile.optionValues.containsKey("MODE"));
    }

    @Test
    public void missingProfileDependencyThrowsLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("main", Arrays.asList("profile.missing"));

        try {
            ProfileSet.fromMap(definitions, optionSetBuilder().build());
            fail("Expected missing profile dependency to throw");
        } catch (IllegalArgumentException e) {
            assertEquals("Profile \"missing\" does not exist!", e.getMessage());
        }
    }

    @Test
    public void recursiveProfileDependencyThrowsLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("a", Arrays.asList("profile.b"));
        definitions.put("b", Arrays.asList("profile.a"));

        try {
            ProfileSet.fromMap(definitions, optionSetBuilder().build());
            fail("Expected recursive profile dependency to throw");
        } catch (IllegalArgumentException e) {
            assertEquals("Error parsing profile \"a\", recursively included by: b, a", e.getMessage());
        }
    }

    @Test
    public void emptyProfileDependencyThrowsLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("main", Arrays.asList("profile."));

        try {
            ProfileSet.fromMap(definitions, optionSetBuilder().build());
            fail("Expected empty profile dependency to throw");
        } catch (IllegalArgumentException e) {
            assertEquals("Profile \"\" does not exist!", e.getMessage());
        }
    }

    @Test
    public void emptyProgramDisableAndMalformedAssignmentsAreStoredLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("weird", Arrays.asList("!program.", "MODE=", "=value"));

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSetBuilder().build());
        Profile profile = profileSet.get("weird").orElseThrow(() -> new AssertionError("Profile missing"));

        assertTrue(profile.disabledPrograms.contains(""));
        assertEquals("", profile.optionValues.get("MODE"));
        assertEquals("value", profile.optionValues.get(""));
    }

    @Test
    public void profileTokensAreNotTrimmedBeforeParsingLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("tabbed", Arrays.asList("bloom\t", "!program.shadow\t", "!clouds\t", "MODE=fast\t"));

        OptionSet optionSet = optionSetBuilder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "bloom", "Bloom", false))
            .addBoolean(new BooleanOption(OptionType.DEFINE, "clouds", "Clouds", true))
            .build();

        ProfileSet profileSet = ProfileSet.fromMap(definitions, optionSet);
        Profile profile = profileSet.get("tabbed").orElseThrow(() -> new AssertionError("Profile missing"));

        assertFalse(profile.optionValues.containsKey("bloom"));
        assertFalse(profile.optionValues.containsKey("clouds"));
        assertEquals("fast\t", profile.optionValues.get("MODE"));
        assertTrue(profile.disabledPrograms.contains("shadow\t"));
        assertFalse(profile.disabledPrograms.contains("shadow"));
    }

    @Test
    public void profileDependencyNamesAreNotTrimmedLikeReference() {
        Map<String, List<String>> definitions = new LinkedHashMap<>();
        definitions.put("base", Arrays.asList("bloom"));
        definitions.put("main", Arrays.asList("profile.base\t"));

        try {
            ProfileSet.fromMap(definitions, optionSetBuilder().build());
            fail("Expected tab-suffixed profile dependency to use the raw dependency name");
        } catch (IllegalArgumentException e) {
            assertEquals("Profile \"base\t\" does not exist!", e.getMessage());
        }
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
