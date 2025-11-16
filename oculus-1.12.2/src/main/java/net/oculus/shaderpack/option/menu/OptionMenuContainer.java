package net.oculus.shaderpack.option.menu;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Placeholder option menu container used while the full shader pack option
 * system is being ported. Provides enough structure for the GUI to build rows
 * and headers.
 */
public class OptionMenuContainer {
    public static final OptionMenuContainer EMPTY = new OptionMenuContainer(OptionMenuElementScreen.EMPTY, Collections.emptyMap());

    public final OptionMenuElementScreen mainScreen;
    public final Map<String, OptionMenuElementScreen> subScreens = new HashMap<>();

    public OptionMenuContainer(OptionMenuElementScreen mainScreen, Map<String, OptionMenuElementScreen> subScreens) {
        this.mainScreen = mainScreen;
        if (subScreens != null) {
            this.subScreens.putAll(subScreens);
        }
    }
}
