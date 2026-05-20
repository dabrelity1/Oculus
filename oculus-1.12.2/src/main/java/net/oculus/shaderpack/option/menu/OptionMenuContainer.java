package net.oculus.shaderpack.option.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.oculus.Oculus;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.ShaderPackOptions;

public class OptionMenuContainer {
    public static final OptionMenuContainer EMPTY = new OptionMenuContainer(OptionMenuElementScreen.EMPTY, Collections.emptyMap());

    public final OptionMenuElementScreen mainScreen;
    public final Map<String, OptionMenuElementScreen> subScreens = new HashMap<>();

    private final List<OptionMenuOptionElement> usedOptionElements = new ArrayList<>();
    private final List<String> usedOptions = new ArrayList<>();
    private final List<String> unusedOptions = new ArrayList<>();
    private final Map<List<OptionMenuElement>, Integer> unusedOptionDumpQueue = new HashMap<>();
    private final ProfileSet profiles;

    public OptionMenuContainer(OptionMenuElementScreen mainScreen, Map<String, OptionMenuElementScreen> subScreens) {
        this.mainScreen = mainScreen;
        this.profiles = ProfileSet.empty();
        if (subScreens != null) {
            this.subScreens.putAll(subScreens);
        }
    }

    public OptionMenuContainer(ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, ProfileSet profiles) {
        this.profiles = profiles == null ? ProfileSet.empty() : profiles;

        ShaderProperties properties = shaderProperties == null ? ShaderProperties.empty() : shaderProperties;
        ShaderPackOptions options = shaderPackOptions;
        List<String> mainOptions = properties.getMainScreenOptions().orElseGet(() -> Collections.singletonList("*"));

        this.mainScreen = new OptionMenuMainElementScreen(this, properties, options, mainOptions, properties.getMainScreenColumnCount());

        if (options != null && options.getOptionSet() != null) {
            this.unusedOptions.addAll(options.getOptionSet().getBooleanOptions().keySet());
            this.unusedOptions.addAll(options.getOptionSet().getStringOptions().keySet());
        }

        Map<String, Integer> subScreenColumnCounts = properties.getSubScreenColumnCount();
        properties.getSubScreenOptions().forEach((screenKey, optionList) ->
            this.subScreens.put(screenKey, new OptionMenuSubElementScreen(
                screenKey,
                this,
                properties,
                options,
                optionList,
                java.util.Optional.ofNullable(subScreenColumnCounts.get(screenKey))
            ))
        );

        for (Map.Entry<List<OptionMenuElement>, Integer> entry : this.unusedOptionDumpQueue.entrySet()) {
            List<OptionMenuElement> elementsToInsert = new ArrayList<>();
            List<String> unusedOptionsCopy = new ArrayList<>(this.unusedOptions);

            for (String optionId : unusedOptionsCopy) {
                try {
                    OptionMenuElement element = OptionMenuElement.create(optionId, this, properties, options);
                    if (element != null) {
                        elementsToInsert.add(element);
                        if (element instanceof OptionMenuOptionElement) {
                            notifyOptionAdded(optionId, (OptionMenuOptionElement) element);
                        }
                    }
                } catch (IllegalArgumentException error) {
                    Oculus.LOGGER.warn(error.getMessage());
                    elementsToInsert.add(OptionMenuElement.EMPTY);
                }
            }

            entry.getKey().addAll(entry.getValue(), elementsToInsert);
        }
    }

    public ProfileSet getProfiles() {
        return this.profiles;
    }

    public void queueForUnusedOptionDump(int index, List<OptionMenuElement> elementList) {
        this.unusedOptionDumpQueue.put(elementList, index);
    }

    public void notifyOptionAdded(String optionId, OptionMenuOptionElement option) {
        if (!this.usedOptions.contains(optionId)) {
            this.usedOptionElements.add(option);
            this.usedOptions.add(optionId);
        }

        this.unusedOptions.remove(optionId);
    }
}
