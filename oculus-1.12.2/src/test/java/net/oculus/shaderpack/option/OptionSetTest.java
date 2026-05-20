package net.oculus.shaderpack.option;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OptionSetTest {
    @Test
    public void duplicateBooleanOptionsWithDifferentDefaultsAreRemovedLikeReference() {
        OptionSet options = OptionSet.builder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "BLOOM", "Bloom", true))
            .addBoolean(new BooleanOption(OptionType.DEFINE, "BLOOM", "Bloom", false))
            .build();

        assertFalse(options.getBooleanOptions().containsKey("BLOOM"));
    }

    @Test
    public void duplicateStringOptionsWithDifferentDefaultsAreRemovedLikeReference() {
        OptionSet options = OptionSet.builder()
            .addString(stringOption("QUALITY", "High [Low High]", "High"))
            .addString(stringOption("QUALITY", "Low [Low High]", "Low"))
            .build();

        assertFalse(options.getStringOptions().containsKey("QUALITY"));
    }

    @Test
    public void duplicateBooleanOptionsPreferExistingCommentLikeReference() {
        OptionSet options = OptionSet.builder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "FXAA", "Fast AA", true))
            .addBoolean(new BooleanOption(OptionType.DEFINE, "FXAA", "Different text", true))
            .build();

        assertTrue(options.getBooleanOptions().containsKey("FXAA"));
        assertEquals("Fast AA", options.getBooleanOptions().get("FXAA").getOption().getComment());
    }

    @Test
    public void duplicateBooleanOptionsAdoptLaterCommentWhenExistingHasNoneLikeReference() {
        OptionSet options = OptionSet.builder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "FXAA", null, true))
            .addBoolean(new BooleanOption(OptionType.DEFINE, "FXAA", "Fast AA", true))
            .build();

        assertEquals("Fast AA", options.getBooleanOptions().get("FXAA").getOption().getComment());
    }

    @Test
    public void duplicateStringOptionsPreferExistingCommentLikeReference() {
        OptionSet options = OptionSet.builder()
            .addString(stringOption("QUALITY", "First [Low High]", "High"))
            .addString(stringOption("QUALITY", "Second [Low High]", "High"))
            .build();

        assertTrue(options.getStringOptions().containsKey("QUALITY"));
        assertEquals("First", options.getStringOptions().get("QUALITY").getOption().getComment());
    }

    private static StringOption stringOption(String name, String comment, String defaultValue) {
        StringOption option = StringOption.create(OptionType.DEFINE, name, comment, defaultValue);
        if (option == null) {
            throw new AssertionError("Expected valid string option");
        }
        return option;
    }
}
