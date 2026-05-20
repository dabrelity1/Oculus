package net.oculus.shaderpack.option.menu;

import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.BooleanOption;
import net.oculus.shaderpack.option.values.OptionValues;

public class OptionMenuBooleanOptionElement extends OptionMenuOptionElement {
    public final BooleanOption option;

    public OptionMenuBooleanOptionElement(String elementString,
                                          OptionMenuContainer container,
                                          ShaderProperties shaderProperties,
                                          OptionValues values,
                                          BooleanOption option) {
        super(elementString, container, shaderProperties, values);
        this.option = option;
    }
}
