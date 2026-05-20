package net.oculus.shaderpack;

import net.oculus.shaderpack.option.values.OptionValues;

public final class ProgramConditionEvaluator {
    private ProgramConditionEvaluator() {
    }

    public static boolean isEnabled(String condition, OptionValues optionValues) {
        if ("true".equals(condition)) {
            return true;
        }

        if ("false".equals(condition)) {
            return false;
        }

        return optionValues != null && optionValues.getBooleanValueOrDefault(condition);
    }
}
