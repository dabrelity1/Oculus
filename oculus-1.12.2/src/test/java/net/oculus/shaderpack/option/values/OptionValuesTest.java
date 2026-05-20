package net.oculus.shaderpack.option.values;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import net.oculus.shaderpack.OptionalBoolean;
import net.oculus.shaderpack.option.BooleanOption;
import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.OptionType;
import net.oculus.shaderpack.option.StringOption;

public class OptionValuesTest {
    @Test
    public void constructorKeepsOnlyKnownNonDefaultValuesLikeReference() {
        Map<String, String> initial = new HashMap<>();
        initial.put("FXAA", "true");
        initial.put("BLOOM", "true");
        initial.put("INVALID_BOOL", "true");
        initial.put("QUALITY", "Low");
        initial.put("UNKNOWN", "value");

        OptionValues values = new OptionValues(optionSet(), initial);

        assertEquals(2, values.getOptionsChanged());
        assertFalse(values.asMap().containsKey("FXAA"));
        assertTrue(values.asMap().containsKey("BLOOM"));
        assertEquals("Low", values.asMap().get("QUALITY"));
        assertFalse(values.asMap().containsKey("UNKNOWN"));
    }

    @Test
    public void invalidBooleanValuesCollapseToDefaultLikeReference() {
        Map<String, String> initial = new HashMap<>();
        initial.put("FXAA", "sometimes");

        OptionValues values = new OptionValues(optionSet(), initial);

        assertEquals(0, values.getOptionsChanged());
        assertTrue(values.getBooleanValueOrDefault("FXAA"));
    }

    @Test
    public void mutableSettersRemoveDefaultsAndIgnoreUnknownOptionsLikeReference() {
        MutableOptionValues values = new MutableOptionValues(optionSet());

        values.setBooleanValue("BLOOM", true);
        values.setBooleanValue("UNKNOWN", true);
        values.setStringValue("QUALITY", "Low");
        assertEquals(2, values.getOptionsChanged());

        values.setBooleanValue("BLOOM", false);
        values.setStringValue("QUALITY", "High");
        assertEquals(0, values.getOptionsChanged());
        assertFalse(values.asMap().containsKey("UNKNOWN"));
    }

    @Test
    public void unknownBooleanValuesDefaultToTrueLikeReference() {
        assertTrue(new OptionValues(optionSet()).getBooleanValueOrDefault("UNKNOWN_OPTION"));
    }

    @Test
    public void rawAccessorsExposeOnlyChangedValuesLikeReference() {
        MutableOptionValues values = new MutableOptionValues(optionSet());

        assertEquals(OptionalBoolean.DEFAULT, values.getBooleanValue("BLOOM"));
        assertFalse(values.getStringValue("QUALITY").isPresent());

        values.setBooleanValue("BLOOM", true);
        values.setStringValue("QUALITY", "Low");

        assertEquals(OptionalBoolean.TRUE, values.getBooleanValue("BLOOM"));
        assertEquals("Low", values.getStringValue("QUALITY").get());

        values.setBooleanValue("BLOOM", false);
        values.setStringValue("QUALITY", "High");

        assertEquals(OptionalBoolean.DEFAULT, values.getBooleanValue("BLOOM"));
        assertFalse(values.getStringValue("QUALITY").isPresent());
    }

    @Test
    public void rawAccessorsDoNotCrossBooleanAndStringOptionTypesLikeReference() {
        MutableOptionValues values = new MutableOptionValues(optionSet());

        values.setBooleanValue("BLOOM", true);
        values.setStringValue("QUALITY", "Low");

        assertFalse(values.getStringValue("BLOOM").isPresent());
        assertEquals(OptionalBoolean.DEFAULT, values.getBooleanValue("QUALITY"));
        assertTrue(values.getBooleanValueOrDefault("QUALITY"));
    }

    private static OptionSet optionSet() {
        return OptionSet.builder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "FXAA", "Fast AA", true))
            .addBoolean(new BooleanOption(OptionType.DEFINE, "BLOOM", "Bloom", false))
            .addString(stringOption("QUALITY", "Quality [Low High]", "High"))
            .build();
    }

    private static StringOption stringOption(String name, String comment, String defaultValue) {
        StringOption option = StringOption.create(OptionType.DEFINE, name, comment, defaultValue);
        if (option == null) {
            throw new AssertionError("Expected valid string option");
        }
        return option;
    }
}
