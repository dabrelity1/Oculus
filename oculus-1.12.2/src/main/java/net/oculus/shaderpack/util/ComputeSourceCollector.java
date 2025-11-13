package net.oculus.shaderpack.util;

import java.util.List;

import net.oculus.shaderpack.ComputeSource;

/**
 * Utility methods for flattening collections of compute shader sources. This mirrors the
 * traversal logic used by the Iris shader metadata pipeline while keeping the current
 * backport free of real directive parsing.
 */
public final class ComputeSourceCollector {
    private ComputeSourceCollector() {
    }

    public static void collect(List<ComputeSource> target, ComputeSource[] sources) {
        if (target == null || sources == null) {
            return;
        }

        for (ComputeSource source : sources) {
            if (source != null) {
                target.add(source);
            }
        }
    }

    public static void collect(List<ComputeSource> target, ComputeSource[][] matrices) {
        if (target == null || matrices == null) {
            return;
        }

        for (ComputeSource[] row : matrices) {
            collect(target, row);
        }
    }
}
