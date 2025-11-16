package net.oculus.shaderpack.option.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight container describing the widgets that should appear on a single
 * option screen. The full 1.16 implementation exposes richer metadata but this
 * covers the fields currently consumed by the UI layer.
 */
public class OptionMenuElementScreen {
    public static final OptionMenuElementScreen EMPTY = new OptionMenuElementScreen("main", Collections.emptyList(), 1);

    public final String screenId;
    public final List<OptionMenuElement> elements;
    private final int columnCount;

    public OptionMenuElementScreen(String screenId, List<OptionMenuElement> elements, int columnCount) {
        this.screenId = screenId;
        this.elements = new ArrayList<>(elements);
        this.columnCount = Math.max(1, columnCount);
    }

    public int getColumnCount() {
        return columnCount;
    }

    public String getScreenId() {
        return screenId;
    }
}
