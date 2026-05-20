package net.oculus.shaderpack.include;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import net.oculus.Oculus;
import net.oculus.shaderpack.error.RusticError;
import net.oculus.shaderpack.transform.line.LineTransform;

/**
 * Resolves shader sources into a directed include graph for deterministic
 * processing and validation.
 */
public final class IncludeGraph {
    private final ImmutableMap<AbsolutePackPath, FileNode> nodes;
    private final ImmutableMap<AbsolutePackPath, RusticError> failures;

    private IncludeGraph(ImmutableMap<AbsolutePackPath, FileNode> nodes,
                         ImmutableMap<AbsolutePackPath, RusticError> failures) {
        this.nodes = nodes;
        this.failures = failures;
    }

    public IncludeGraph(Path root, ImmutableList<AbsolutePackPath> startingPaths) {
        Map<AbsolutePackPath, AbsolutePackPath> cameFrom = new HashMap<>();
        Map<AbsolutePackPath, Integer> lineNumberInclude = new HashMap<>();

        Map<AbsolutePackPath, FileNode> nodes = new HashMap<>();
        Map<AbsolutePackPath, RusticError> failures = new HashMap<>();

        List<AbsolutePackPath> queue = new ArrayList<>(startingPaths);
        Set<AbsolutePackPath> seen = new HashSet<>(startingPaths);

        while (!queue.isEmpty()) {
            AbsolutePackPath next = queue.remove(queue.size() - 1);

            String source;

            try {
                source = readFile(next.resolved(root));
            } catch (IOException e) {
                AbsolutePackPath src = cameFrom.get(next);

                if (src == null) {
                    throw new RuntimeException("unexpected error: failed to read " + next.getPathString(), e);
                }

                String topLevelMessage;
                String detailMessage;

                if (e instanceof NoSuchFileException) {
                    topLevelMessage = "failed to resolve #include directive";
                    detailMessage = "file not found";
                } else {
                    topLevelMessage = "unexpected I/O error while resolving #include directive: " + e;
                    detailMessage = "IO error";
                }

                int badLineNumber = lineNumberInclude.get(next);
                String badLine = nodes.get(src).getLines().get(badLineNumber).trim();

                RusticError topLevelError = new RusticError("error", topLevelMessage, detailMessage,
                    src.getPathString(), badLineNumber + 1, badLine);

                failures.put(next, topLevelError);

                continue;
            }

            ImmutableList<String> lines = ImmutableList.copyOf(source.split("\\R"));

            FileNode node = new FileNode(next, lines);
            boolean selfInclude = false;

            for (Map.Entry<Integer, AbsolutePackPath> include : node.getIncludes().entrySet()) {
                int line = include.getKey();
                AbsolutePackPath included = include.getValue();

                if (next.equals(included)) {
                    selfInclude = true;
                    failures.put(next, new RusticError("error", "trivial #include cycle detected",
                        "file includes itself", next.getPathString(), line + 1, lines.get(line)));
                    break;
                } else if (!seen.contains(included)) {
                    queue.add(included);
                    seen.add(included);
                    cameFrom.put(included, next);
                    lineNumberInclude.put(included, line);
                }
            }

            if (!selfInclude) {
                nodes.put(next, node);
            }
        }

        this.nodes = ImmutableMap.copyOf(nodes);
        this.failures = ImmutableMap.copyOf(failures);

        detectCycle();
    }

    private void detectCycle() {
        List<AbsolutePackPath> cycle = new ArrayList<>();
        Set<AbsolutePackPath> visited = new HashSet<>();

        for (AbsolutePackPath start : nodes.keySet()) {
            if (exploreForCycles(start, cycle, visited)) {
                AbsolutePackPath lastFilePath = null;
                StringBuilder error = new StringBuilder();

                for (AbsolutePackPath node : cycle) {
                    if (lastFilePath == null) {
                        lastFilePath = node;
                        continue;
                    }

                    FileNode lastFile = nodes.get(lastFilePath);
                    int lineNumber = -1;

                    for (Map.Entry<Integer, AbsolutePackPath> include : lastFile.getIncludes().entrySet()) {
                        if (include.getValue() == node) {
                            lineNumber = include.getKey() + 1;
                        }
                    }

                    String badLine = lastFile.getLines().get(lineNumber - 1);
                    String detailMessage = node.equals(start) ? "final #include in cycle" : "#include involved in cycle";

                    if (lastFilePath.equals(start)) {
                        error.append(new RusticError("error", "#include cycle detected",
                            detailMessage, lastFilePath.getPathString(), lineNumber, badLine));
                    } else {
                        error.append("\n  = ")
                            .append(new RusticError("note", "cycle involves another file",
                                detailMessage, lastFilePath.getPathString(), lineNumber, badLine));
                    }

                    lastFilePath = node;
                }

                error.append("\n  = note: #include directives are resolved before any other preprocessor directives, any form of #include guard will not work")
                    .append("\n  = note: other cycles may still exist, only the first detected non-trivial cycle will be reported");

                Oculus.LOGGER.error(error.toString());

                throw new IllegalStateException("Cycle detected in #include graph, see previous messages for details");
            }
        }
    }

    private boolean exploreForCycles(AbsolutePackPath frontier, List<AbsolutePackPath> path, Set<AbsolutePackPath> visited) {
        if (visited.contains(frontier)) {
            path.add(frontier);
            return true;
        }

        path.add(frontier);
        visited.add(frontier);

        for (AbsolutePackPath included : nodes.get(frontier).getIncludes().values()) {
            if (!nodes.containsKey(included)) {
                continue;
            }

            if (exploreForCycles(included, path, visited)) {
                return true;
            }
        }

        path.remove(path.size() - 1);
        visited.remove(frontier);

        return false;
    }

    public ImmutableMap<AbsolutePackPath, FileNode> getNodes() {
        return nodes;
    }

    public List<IncludeGraph> computeWeaklyConnectedComponents() {
        return Collections.singletonList(this);
    }

    public IncludeGraph map(Function<AbsolutePackPath, LineTransform> transformProvider) {
        ImmutableMap.Builder<AbsolutePackPath, FileNode> mappedNodes = ImmutableMap.builder();

        nodes.forEach((path, node) -> mappedNodes.put(path, node.map(transformProvider.apply(path))));

        return new IncludeGraph(mappedNodes.build(), failures);
    }

    public ImmutableMap<AbsolutePackPath, RusticError> getFailures() {
        return failures;
    }

    private static String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
