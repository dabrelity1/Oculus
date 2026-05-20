package net.oculus.shaderpack.directives;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import net.oculus.vendored.joml.Vector2f;
import net.oculus.vendored.joml.Vector3i;
import net.oculus.vendored.joml.Vector4f;

/**
 * Stripped-down version of the Iris {@code DirectiveHolder}. Implementations
 * register consumers for directive names and invoke them while parsed shader
 * comments or const declarations are dispatched.
 */
public interface DirectiveHolder {
    default void acceptUniformDirective(String name, Runnable onDetected) {
    }

    default void acceptCommentStringDirective(String name, Consumer<String> consumer) {
    }

    default void acceptCommentIntDirective(String name, IntConsumer consumer) {
    }

    default void acceptCommentFloatDirective(String name, Consumer<Float> consumer) {
    }

    default void acceptConstBooleanDirective(String name, Consumer<Boolean> consumer) {
    }

    default void acceptConstStringDirective(String name, Consumer<String> consumer) {
    }

    default void acceptConstIntDirective(String name, IntConsumer consumer) {
    }

    default void acceptConstFloatDirective(String name, Consumer<Float> consumer) {
    }

    default void acceptConstVec2Directive(String name, Consumer<Vector2f> consumer) {
    }

    default void acceptConstIVec3Directive(String name, Consumer<Vector3i> consumer) {
    }

    default void acceptConstVec4Directive(String name, Consumer<Vector4f> consumer) {
    }
}
