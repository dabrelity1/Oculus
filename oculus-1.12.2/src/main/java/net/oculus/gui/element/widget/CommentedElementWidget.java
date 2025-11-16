package net.oculus.gui.element.widget;

import java.util.Optional;

import net.minecraft.util.text.ITextComponent;
import net.oculus.shaderpack.option.menu.OptionMenuElement;

/**
 * Adds comment metadata hooks to option widgets so the shader pack screen can
 * show contextual descriptions when the user highlights an element.
 */
public abstract class CommentedElementWidget<T extends OptionMenuElement> extends AbstractElementWidget<T> {
    protected CommentedElementWidget(T element) {
        super(element);
    }

    public abstract Optional<ITextComponent> getCommentTitle();

    public abstract Optional<ITextComponent> getCommentBody();
}
