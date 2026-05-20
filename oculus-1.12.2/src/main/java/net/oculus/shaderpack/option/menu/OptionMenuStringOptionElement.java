package net.oculus.shaderpack.option.menu;

import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.StringOption;
import net.oculus.shaderpack.option.values.OptionValues;

public class OptionMenuStringOptionElement extends OptionMenuOptionElement {
    public final StringOption option;

    public OptionMenuStringOptionElement(String elementString,
                                         OptionMenuContainer container,
                                         ShaderProperties shaderProperties,
                                         OptionValues values,
                                         StringOption option) {
        super(elementString, container, shaderProperties, values);
        this.option = option;
    }
}
