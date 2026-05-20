package net.oculus.shaderpack.include;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Resolves {@code #include} directives by expanding the include graph.
 */
public final class IncludeProcessor {
    private final IncludeGraph graph;
    private final Map<AbsolutePackPath, ImmutableList<String>> cache;

    public IncludeProcessor(IncludeGraph graph) {
        this.graph = graph;
        this.cache = new HashMap<>();
    }

    public ImmutableList<String> getIncludedFile(AbsolutePackPath path) {
        ImmutableList<String> lines = cache.get(path);

        if (lines == null) {
            lines = process(path);
            cache.put(path, lines);
        }

        return lines;
    }

    private ImmutableList<String> process(AbsolutePackPath path) {
        FileNode fileNode = graph.getNodes().get(path);

        if (fileNode == null) {
            return null;
        }

        ImmutableList.Builder<String> builder = ImmutableList.builder();

        ImmutableList<String> lines = fileNode.getLines();
        ImmutableMap<Integer, AbsolutePackPath> includes = fileNode.getIncludes();

        for (int i = 0; i < lines.size(); i++) {
            AbsolutePackPath include = includes.get(i);

            if (include != null) {
                builder.addAll(Objects.requireNonNull(getIncludedFile(include)));
            } else {
                builder.add(lines.get(i));
            }
        }

        return builder.build();
    }
}
