package net.oculus.shaderpack.option.menu;

import java.util.List;
import java.util.Optional;

import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.ShaderPackOptions;

public class OptionMenuSubElementScreen extends OptionMenuElementScreen {
    public final String screenId;

    public OptionMenuSubElementScreen(String screenId,
                                      OptionMenuContainer container,
                                      ShaderProperties shaderProperties,
                                      ShaderPackOptions shaderPackOptions,
                                      List<String> elementStrings,
                                      Optional<Integer> columnCount) {
        super(container, shaderProperties, shaderPackOptions, elementStrings, columnCount);
        this.screenId = screenId;
    }

    @Override
    public String getScreenId() {
        return this.screenId;
    }
}
