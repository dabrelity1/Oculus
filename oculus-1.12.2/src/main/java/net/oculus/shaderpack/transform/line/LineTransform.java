package net.oculus.shaderpack.transform.line;

import com.google.common.collect.ImmutableList;

/**
 * Functional interface for simple per-line shader source transformations.
 */
public interface LineTransform {
    String transform(int index, String line);

    static ImmutableList<String> apply(ImmutableList<String> lines, LineTransform transform) {
        ImmutableList.Builder<String> newLines = ImmutableList.builder();
        int index = 0;

        for (String line : lines) {
            newLines.add(transform.transform(index, line));
            index += 1;
        }

        return newLines.build();
    }
}
