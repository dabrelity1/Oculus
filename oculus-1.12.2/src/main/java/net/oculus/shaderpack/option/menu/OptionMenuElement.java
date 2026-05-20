package net.oculus.shaderpack.option.menu;

import java.util.Map;

import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.MergedBooleanOption;
import net.oculus.shaderpack.option.MergedStringOption;
import net.oculus.shaderpack.option.ShaderPackOptions;

public abstract class OptionMenuElement {
    public static final OptionMenuElement EMPTY = new OptionMenuElement() {
    };

    private static final String ELEMENT_EMPTY = "<empty>";
    private static final String ELEMENT_PROFILE = "<profile>";

    public static OptionMenuElement create(String elementString,
                                           OptionMenuContainer container,
                                           ShaderProperties shaderProperties,
                                           ShaderPackOptions shaderPackOptions) {
        if (ELEMENT_EMPTY.equals(elementString)) {
            return EMPTY;
        }

        if (ELEMENT_PROFILE.equals(elementString)) {
            return container.getProfiles().size() > 0
                ? new OptionMenuProfileElement(container.getProfiles(), shaderPackOptions.getOptionSet(), shaderPackOptions.getOptionValues())
                : null;
        }

        if (elementString != null && elementString.startsWith("[") && elementString.endsWith("]")) {
            return new OptionMenuLinkElement(elementString.substring(1, elementString.length() - 1));
        }

        if (shaderPackOptions == null || shaderPackOptions.getOptionSet() == null) {
            throw new IllegalArgumentException("Unable to resolve shader pack option menu element \"" + elementString + "\" defined in shaders.properties");
        }

        Map<String, MergedBooleanOption> booleanOptions = shaderPackOptions.getOptionSet().getBooleanOptions();
        Map<String, MergedStringOption> stringOptions = shaderPackOptions.getOptionSet().getStringOptions();

        if (booleanOptions.containsKey(elementString)) {
            return new OptionMenuBooleanOptionElement(
                elementString,
                container,
                shaderProperties,
                shaderPackOptions.getOptionValues(),
                booleanOptions.get(elementString).getOption()
            );
        }

        if (stringOptions.containsKey(elementString)) {
            return new OptionMenuStringOptionElement(
                elementString,
                container,
                shaderProperties,
                shaderPackOptions.getOptionValues(),
                stringOptions.get(elementString).getOption()
            );
        }

        throw new IllegalArgumentException("Unable to resolve shader pack option menu element \"" + elementString + "\" defined in shaders.properties");
    }
}
