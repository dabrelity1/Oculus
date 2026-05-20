package net.oculus.shaderpack.option.menu;

import java.util.List;
import java.util.Optional;

import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.ShaderPackOptions;

public class OptionMenuMainElementScreen extends OptionMenuElementScreen {
    public OptionMenuMainElementScreen(OptionMenuContainer container,
                                       ShaderProperties shaderProperties,
                                       ShaderPackOptions shaderPackOptions,
                                       List<String> elementStrings,
                                       Optional<Integer> columnCount) {
        super(container, shaderProperties, shaderPackOptions, elementStrings, columnCount);
    }
}
