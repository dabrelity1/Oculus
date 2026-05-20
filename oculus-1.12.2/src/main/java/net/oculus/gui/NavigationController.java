package net.oculus.gui;

import java.util.ArrayDeque;
import java.util.Deque;

import net.oculus.gui.element.ShaderPackOptionList;
import net.oculus.shaderpack.option.menu.OptionMenuContainer;

/**
 * Legacy-compatible navigation helper used by the shader pack option menu.
 * Mirrors the behaviour of the modern NavigationController while avoiding
 * dependencies on 1.16+ UI classes.
 */
public class NavigationController {
    private final OptionMenuContainer container;
    private ShaderPackOptionList optionList;

    private String currentScreen;
    private final Deque<String> history = new ArrayDeque<>();

    public NavigationController(OptionMenuContainer container) {
        this.container = container;
    }

    public void back() {
        if (!history.isEmpty()) {
            history.removeLast();
            currentScreen = history.peekLast();
        } else {
            currentScreen = null;
        }

        rebuild();
    }

    public void open(String screen) {
        currentScreen = screen;
        history.addLast(screen);
        rebuild();
    }

    public void rebuild() {
        if (optionList != null) {
            optionList.rebuild();
        }
    }

    public void refresh() {
        if (optionList != null) {
            optionList.refresh();
        }
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    public void setActiveOptionList(ShaderPackOptionList optionList) {
        this.optionList = optionList;
    }

    public String getCurrentScreen() {
        return currentScreen;
    }

    public OptionMenuContainer getContainer() {
        return container;
    }
}
