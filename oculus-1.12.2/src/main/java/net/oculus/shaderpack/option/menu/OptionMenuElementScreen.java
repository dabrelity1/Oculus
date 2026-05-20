package net.oculus.shaderpack.option.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.oculus.Oculus;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.ShaderPackOptions;

public class OptionMenuElementScreen {
    public static final OptionMenuElementScreen EMPTY = new OptionMenuElementScreen("main", Collections.emptyList(), Optional.of(1));

    public final String screenId;
    public final List<OptionMenuElement> elements = new ArrayList<>();
    private final Optional<Integer> columnCount;

    public OptionMenuElementScreen(String screenId, List<OptionMenuElement> elements, Optional<Integer> columnCount) {
        this.screenId = screenId;
        if (elements != null) {
            this.elements.addAll(elements);
        }
        this.columnCount = columnCount == null ? Optional.empty() : columnCount;
    }

    public OptionMenuElementScreen(OptionMenuContainer container,
                                   ShaderProperties shaderProperties,
                                   ShaderPackOptions shaderPackOptions,
                                   List<String> elementStrings,
                                   Optional<Integer> columnCount) {
        this(null, Collections.emptyList(), columnCount);

        if (elementStrings == null) {
            return;
        }

        for (String elementString : elementStrings) {
            if ("*".equals(elementString)) {
                container.queueForUnusedOptionDump(this.elements.size(), this.elements);
                continue;
            }

            try {
                OptionMenuElement element = OptionMenuElement.create(elementString, container, shaderProperties, shaderPackOptions);
                if (element != null) {
                    this.elements.add(element);
                    if (element instanceof OptionMenuOptionElement) {
                        container.notifyOptionAdded(elementString, (OptionMenuOptionElement) element);
                    }
                }
            } catch (IllegalArgumentException error) {
                Oculus.LOGGER.warn(error.getMessage());
                this.elements.add(OptionMenuElement.EMPTY);
            }
        }
    }

    public int getColumnCount() {
        return this.columnCount.orElse(this.elements.size() > 18 ? 3 : 2);
    }

    public String getScreenId() {
        return screenId;
    }
}
