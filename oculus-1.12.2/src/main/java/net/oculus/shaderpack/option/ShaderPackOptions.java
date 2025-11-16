package net.oculus.shaderpack.option;

import java.util.HashMap;
import java.util.Map;

import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.include.IncludeGraph;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.option.values.OptionValues;

public class ShaderPackOptions {
    private final OptionSet optionSet;
    private final MutableOptionValues optionValues;
    private final IncludeGraph includes;

    public ShaderPackOptions(IncludeGraph graph, Map<String, String> changedConfigs) {
        Map<AbsolutePackPath, OptionAnnotatedSource> annotations = new HashMap<>();
        OptionSet.Builder builder = OptionSet.builder();

        graph.getNodes().forEach((path, node) -> {
            OptionAnnotatedSource annotated = new OptionAnnotatedSource(node.getLines());
            annotations.put(path, annotated);
            builder.addAll(annotated.getOptionSet(path, annotated.getBooleanDefineReferences().keySet()));
        });

        this.optionSet = builder.build();
        this.optionValues = new MutableOptionValues(optionSet, changedConfigs);
        this.includes = graph.map(path -> {
            OptionAnnotatedSource annotated = annotations.get(path);
            if (annotated == null) {
                return (index, line) -> line;
            }
            return annotated.asTransform(optionValues);
        });
    }

    public OptionSet getOptionSet() {
        return optionSet;
    }

    public OptionValues getOptionValues() {
        return optionValues;
    }

    public IncludeGraph getIncludes() {
        return includes;
    }
}
