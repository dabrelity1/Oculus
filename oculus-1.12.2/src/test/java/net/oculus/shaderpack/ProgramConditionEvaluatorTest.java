package net.oculus.shaderpack;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.oculus.shaderpack.option.BooleanOption;
import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.OptionType;
import net.oculus.shaderpack.option.values.MutableOptionValues;

public class ProgramConditionEvaluatorTest {
    @Test
    public void evaluatesReferenceBooleanLiteralsAndSingleOptionNames() {
        MutableOptionValues values = values();
        values.setBooleanValue("FXAA", true);
        values.setBooleanValue("BLOOM", false);

        assertTrue(ProgramConditionEvaluator.isEnabled("FXAA", values));
        assertFalse(ProgramConditionEvaluator.isEnabled("BLOOM", values));
        assertTrue(ProgramConditionEvaluator.isEnabled("true", values));
        assertFalse(ProgramConditionEvaluator.isEnabled("false", values));
    }

    @Test
    public void treatsOperatorsAndCaseVariantsAsSingleOptionNamesLikeReference() {
        MutableOptionValues values = values();
        values.setBooleanValue("FXAA", true);

        assertTrue(ProgramConditionEvaluator.isEnabled("FXAA && !BLOOM", values));
        assertTrue(ProgramConditionEvaluator.isEnabled("BLOOM || FXAA", values));
        assertTrue(ProgramConditionEvaluator.isEnabled("TRUE", values));
        assertTrue(ProgramConditionEvaluator.isEnabled(null, values));
    }

    private static MutableOptionValues values() {
        OptionSet optionSet = OptionSet.builder()
            .addBoolean(new BooleanOption(OptionType.DEFINE, "FXAA", "FXAA", true))
            .addBoolean(new BooleanOption(OptionType.DEFINE, "BLOOM", "Bloom", false))
            .build();
        return new MutableOptionValues(optionSet);
    }
}
