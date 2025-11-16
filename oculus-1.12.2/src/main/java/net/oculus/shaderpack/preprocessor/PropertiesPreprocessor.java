package net.oculus.shaderpack.preprocessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

import net.oculus.Oculus;
import net.oculus.shaderpack.StringPair;
import net.oculus.shaderpack.option.ShaderPackOptions;

/**
 * Preprocesses OptiFine-style properties files using shader option and environment defines.
 */
public final class PropertiesPreprocessor {
    private PropertiesPreprocessor() {
    }

    public static String preprocessSource(String source, ShaderPackOptions shaderPackOptions,
                                          Iterable<StringPair> environmentDefines) {
        if (source.contains(PropertyCollectingListener.PROPERTY_MARKER)) {
            throw new IllegalStateException("Shader pack attempted to inject property markers unexpectedly");
        }

        List<String> booleanValues = collectBooleanDefines(shaderPackOptions);
        Map<String, String> stringValues = collectStringDefines(shaderPackOptions);

        try (Preprocessor preprocessor = new Preprocessor()) {
            for (String define : booleanValues) {
                preprocessor.addMacro(define);
            }

            for (StringPair envDefine : environmentDefines) {
                preprocessor.addMacro(envDefine.getKey(), envDefine.getValue());
            }

            stringValues.forEach((key, value) -> {
                try {
                    preprocessor.addMacro(key, value);
                } catch (LexerException ignored) {
                    Oculus.LOGGER.warn("Failed to add string define {} for properties preprocessing", key, ignored);
                }
            });

            return process(preprocessor, source);
        } catch (IOException | LexerException exception) {
            throw new IllegalStateException("Unexpected error preprocessing properties", exception);
        }
    }

    public static String preprocessSource(String source, Iterable<StringPair> environmentDefines) {
        if (source.contains(PropertyCollectingListener.PROPERTY_MARKER)) {
            throw new IllegalStateException("Shader pack attempted to inject property markers unexpectedly");
        }

        try (Preprocessor preprocessor = new Preprocessor()) {
            for (StringPair envDefine : environmentDefines) {
                preprocessor.addMacro(envDefine.getKey(), envDefine.getValue());
            }
            return process(preprocessor, source);
        } catch (IOException | LexerException exception) {
            throw new IllegalStateException("Unexpected error preprocessing properties", exception);
        }
    }

    private static String process(Preprocessor preprocessor, String source) throws IOException, LexerException {
        preprocessor.setListener(new PropertiesCommentListener());
        PropertyCollectingListener collector = new PropertyCollectingListener();
        preprocessor.setListener(collector);

        source = Arrays.stream(source.split("\\R"))
            .map(String::trim)
            .map(line -> line.startsWith("#") ? line : PropertyCollectingListener.PROPERTY_MARKER + line)
            .collect(Collectors.joining("\n")) + "\n";

        preprocessor.addInput(new StringLexerSource(source, true));
        preprocessor.addFeature(Feature.KEEPCOMMENTS);

        StringBuilder builder = new StringBuilder();
        for (;;) {
            Token token = preprocessor.token();
            if (token == null || token.getType() == Token.EOF) {
                break;
            }
            builder.append(token.getText());
        }

        return collector.collectLines() + builder;
    }

    private static List<String> collectBooleanDefines(ShaderPackOptions shaderPackOptions) {
        List<String> defines = new ArrayList<>();
        shaderPackOptions.getOptionSet().getBooleanOptions().forEach((name, option) -> {
            if (shaderPackOptions.getOptionValues().getBooleanValueOrDefault(name)) {
                defines.add(name);
            }
        });
        return defines;
    }

    private static Map<String, String> collectStringDefines(ShaderPackOptions shaderPackOptions) {
        Map<String, String> defines = new HashMap<>();
        shaderPackOptions.getOptionSet().getStringOptions().forEach((name, option) ->
            defines.put(name, shaderPackOptions.getOptionValues().getStringValueOrDefault(name))
        );
        return defines;
    }
}
