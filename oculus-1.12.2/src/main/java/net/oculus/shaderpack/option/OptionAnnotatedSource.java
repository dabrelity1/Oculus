package net.oculus.shaderpack.option;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.oculus.shaderpack.OptionalBoolean;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.option.values.OptionValues;
import net.oculus.shaderpack.parsing.ParsedString;
import net.oculus.shaderpack.transform.line.LineTransform;

public final class OptionAnnotatedSource {
    private final ImmutableList<String> lines;
    private final ImmutableMap<Integer, BooleanOption> booleanOptions;
    private final ImmutableMap<Integer, StringOption> stringOptions;
    private final ImmutableMap<Integer, String> diagnostics;
    private final ImmutableMap<String, IntList> booleanDefineReferences;

    private static final ImmutableSet<String> VALID_CONST_OPTION_NAMES = ImmutableSet.of(
        "shadowMapResolution",
        "shadowDistance",
        "shadowDistanceRenderMul",
        "entityShadowDistanceMul",
        "shadowIntervalSize",
        "generateShadowMipmap",
        "generateShadowColorMipmap",
        "shadowHardwareFiltering",
        "shadowHardwareFiltering0",
        "shadowHardwareFiltering1",
        "shadowtex0Mipmap",
        "shadowtexMipmap",
        "shadowtex1Mipmap",
        "shadowcolor0Mipmap",
        "shadowColor0Mipmap",
        "shadowcolor1Mipmap",
        "shadowColor1Mipmap",
        "shadowtex0Nearest",
        "shadowtexNearest",
        "shadow0MinMagNearest",
        "shadowtex1Nearest",
        "shadow1MinMagNearest",
        "shadowcolor0Nearest",
        "shadowColor0Nearest",
        "shadowColor0MinMagNearest",
        "shadowcolor1Nearest",
        "shadowColor1Nearest",
        "shadowColor1MinMagNearest",
        "wetnessHalflife",
        "drynessHalflife",
        "eyeBrightnessHalflife",
        "centerDepthHalflife",
        "sunPathRotation",
        "ambientOcclusionLevel",
        "superSamplingLevel",
        "noiseTextureResolution"
    );

    public OptionAnnotatedSource(String source) {
        this(ImmutableList.copyOf(source.split("\\R")));
    }

    public OptionAnnotatedSource(ImmutableList<String> lines) {
        this.lines = lines;

        AnnotationsBuilder builder = new AnnotationsBuilder();

        for (int index = 0; index < lines.size(); index++) {
            parseLine(builder, index, lines.get(index));
        }

        this.booleanOptions = builder.booleanOptions.build();
        this.stringOptions = builder.stringOptions.build();
        this.diagnostics = builder.diagnostics.build();
        this.booleanDefineReferences = ImmutableMap.copyOf(builder.booleanDefineReferences);
    }

    private static void parseLine(AnnotationsBuilder builder, int index, String lineText) {
        if (!lineText.contains("#define")
            && !lineText.contains("const")
            && !lineText.contains("#ifdef")
            && !lineText.contains("#ifndef")) {
            return;
        }

        ParsedString line = new ParsedString(lineText.trim());

        if (line.takeLiteral("#ifdef") || line.takeLiteral("#ifndef")) {
            parseIfdef(builder, index, line);
        } else if (line.takeLiteral("const")) {
            parseConst(builder, index, line);
        } else if (line.currentlyContains("#define")) {
            parseDefineOption(builder, index, line);
        }
    }

    private static void parseIfdef(AnnotationsBuilder builder, int index, ParsedString line) {
        if (!line.takeSomeWhitespace()) {
            return;
        }

        String name = line.takeWord();
        line.takeSomeWhitespace();

        if (name == null || !line.isEnd()) {
            return;
        }

        builder.booleanDefineReferences.computeIfAbsent(name, n -> new IntArrayList()).add(index);
    }

    private static void parseConst(AnnotationsBuilder builder, int index, ParsedString line) {
        if (!line.takeSomeWhitespace()) {
            builder.diagnostics.put(index, "Expected whitespace after const keyword");
            return;
        }

        boolean isString;

        if (line.takeLiteral("int") || line.takeLiteral("float")) {
            isString = true;
        } else if (line.takeLiteral("bool")) {
            isString = false;
        } else {
            builder.diagnostics.put(index, "Unexpected type declaration after const keyword");
            return;
        }

        if (!line.takeSomeWhitespace()) {
            builder.diagnostics.put(index, "Expected whitespace after type declaration");
            return;
        }

        String name = line.takeWord();

        if (name == null) {
            builder.diagnostics.put(index, "Expected option name after type declaration");
            return;
        }

        line.takeSomeWhitespace();

        if (!line.takeLiteral("=")) {
            builder.diagnostics.put(index, "Expected equals sign in const declaration");
            return;
        }

        line.takeSomeWhitespace();

        String value = line.takeWordOrNumber();

        if (value == null) {
            builder.diagnostics.put(index, "Expected value after equals sign");
            return;
        }

        line.takeSomeWhitespace();

        if (!line.takeLiteral(";")) {
            builder.diagnostics.put(index, "Expected semicolon at end of const declaration");
            return;
        }

        line.takeSomeWhitespace();

        String comment;
        if (line.takeComments()) {
            comment = line.takeRest().trim();
        } else if (!line.isEnd()) {
            builder.diagnostics.put(index, "Unexpected characters after const declaration");
            return;
        } else {
            comment = null;
        }

        if (!isString) {
            boolean boolValue;

            if ("true".equals(value)) {
                boolValue = true;
            } else if ("false".equals(value)) {
                boolValue = false;
            } else {
                builder.diagnostics.put(index, "Const boolean option must be true or false");
                return;
            }

            if (!VALID_CONST_OPTION_NAMES.contains(name)) {
                builder.diagnostics.put(index, "Const boolean option " + name + " is not configurable");
                return;
            }

            builder.booleanOptions.put(index, new BooleanOption(OptionType.CONST, name, comment, boolValue));
            return;
        }

        if (!VALID_CONST_OPTION_NAMES.contains(name)) {
            builder.diagnostics.put(index, "Const option " + name + " is not configurable");
            return;
        }

        StringOption option = StringOption.create(OptionType.CONST, name, comment, value);

        if (option != null) {
            builder.stringOptions.put(index, option);
        } else {
            builder.diagnostics.put(index, "Const option lacks allowed values comment");
        }
    }

    private static void parseDefineOption(AnnotationsBuilder builder, int index, ParsedString line) {
        boolean hasLeadingComment = line.takeComments();
        line.takeSomeWhitespace();

        if (!line.takeLiteral("#define")) {
            builder.diagnostics.put(index, "Malformed #define directive");
            return;
        }

        if (!line.takeSomeWhitespace()) {
            builder.diagnostics.put(index, "Expected whitespace after #define");
            return;
        }

        String name = line.takeWord();

        if (name == null) {
            builder.diagnostics.put(index, "Expected option name after #define");
            return;
        }

        boolean tookWhitespace = line.takeSomeWhitespace();

        if (line.isEnd()) {
            builder.booleanOptions.put(index, new BooleanOption(OptionType.DEFINE, name, null, !hasLeadingComment));
            return;
        }

        if (line.takeComments()) {
            String comment = line.takeRest().trim();
            builder.booleanOptions.put(index, new BooleanOption(OptionType.DEFINE, name, comment, !hasLeadingComment));
            return;
        } else if (!tookWhitespace) {
            builder.diagnostics.put(index, "Invalid characters after boolean #define");
            return;
        }

        if (hasLeadingComment) {
            builder.diagnostics.put(index, "Non-boolean #define cannot have leading comment");
            return;
        }

        String value = line.takeWordOrNumber();

        if (value == null) {
            builder.diagnostics.put(index, "Expected value after #define");
            return;
        }

        tookWhitespace = line.takeSomeWhitespace();

        if (line.isEnd()) {
            builder.diagnostics.put(index, "Value #define lacks allowed values comment");
            return;
        } else if (!tookWhitespace) {
            if (!line.takeComments()) {
                builder.diagnostics.put(index, "Invalid characters after value in #define");
                return;
            }
        } else if (!line.takeComments()) {
            builder.diagnostics.put(index, "Expected comment after value in #define");
            return;
        }

        String comment = line.takeRest().trim();
        StringOption option = StringOption.create(OptionType.DEFINE, name, comment, value);

        if (option != null) {
            builder.stringOptions.put(index, option);
        } else {
            builder.diagnostics.put(index, "Ignoring value #define without allowed value list");
        }
    }

    public ImmutableList<String> getLines() {
        return lines;
    }

    public ImmutableMap<Integer, BooleanOption> getBooleanOptions() {
        return booleanOptions;
    }

    public ImmutableMap<Integer, StringOption> getStringOptions() {
        return stringOptions;
    }

    public ImmutableMap<Integer, String> getDiagnostics() {
        return diagnostics;
    }

    public ImmutableMap<String, IntList> getBooleanDefineReferences() {
        return booleanDefineReferences;
    }

    public OptionSet getOptionSet(AbsolutePackPath path, Set<String> referencedBooleanDefines) {
        OptionSet.Builder builder = OptionSet.builder();

        booleanOptions.forEach((line, option) -> {
            if (option.getType() == OptionType.DEFINE) {
                if (referencedBooleanDefines.contains(option.getName())) {
                    builder.addBoolean(option);
                }
            } else {
                builder.addBoolean(option);
            }
        });

        stringOptions.forEach((line, option) -> builder.addString(option));

        return builder.build();
    }

    public LineTransform asTransform(OptionValues values) {
        return (index, original) -> {
            BooleanOption booleanOption = booleanOptions.get(index);
            if (booleanOption != null) {
                if (booleanOption.getType() == OptionType.DEFINE) {
                    boolean enabled = values.getBooleanValueOrDefault(booleanOption.getName());
                    if (enabled && original.startsWith("//#define")) {
                        return original.replaceFirst("//#define", "#define");
                    } else if (!enabled && original.startsWith("#define")) {
                        return original.replaceFirst("#define", "//#define");
                    }
                } else {
                    boolean enabled = values.getBooleanValueOrDefault(booleanOption.getName());
                    return original.replaceFirst(booleanOption.getDefaultValue() ? "true" : "false", enabled ? "true" : "false");
                }
                return original;
            }

            StringOption stringOption = stringOptions.get(index);
            if (stringOption != null) {
                String current = values.getStringValueOrDefault(stringOption.getName());
                return original.replaceFirst(stringOption.getDefaultValue(), current);
            }

            return original;
        };
    }

    private static final class AnnotationsBuilder {
        private final ImmutableMap.Builder<Integer, BooleanOption> booleanOptions = ImmutableMap.builder();
        private final ImmutableMap.Builder<Integer, StringOption> stringOptions = ImmutableMap.builder();
        private final ImmutableMap.Builder<Integer, String> diagnostics = ImmutableMap.builder();
        private final Map<String, IntList> booleanDefineReferences = new HashMap<>();
    }
}
