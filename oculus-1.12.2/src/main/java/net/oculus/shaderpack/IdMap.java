package net.oculus.shaderpack;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.oculus.Oculus;
import net.oculus.shaderpack.materialmap.BlockEntry;
import net.oculus.shaderpack.materialmap.BlockRenderType;
import net.oculus.shaderpack.materialmap.NamespacedId;
import net.oculus.shaderpack.option.ShaderPackOptions;
import net.oculus.shaderpack.preprocessor.PropertiesPreprocessor;

/**
 * Parses OptiFine ID maps (item.properties, block.properties, entity.properties).
 */
public final class IdMap {
    private static final IdMap EMPTY = new IdMap(
        emptyIdMap(),
        emptyIdMap(),
        Int2ObjectMaps.emptyMap(),
        Collections.emptyMap()
    );

    private final Object2IntMap<NamespacedId> itemIdMap;
    private final Object2IntMap<NamespacedId> entityIdMap;
    private final Int2ObjectMap<List<BlockEntry>> blockPropertiesMap;
    private final Map<NamespacedId, BlockRenderType> blockRenderTypeMap;

    public IdMap(Path shaderRoot, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
        this.itemIdMap = loadProperties(shaderRoot, "item.properties", shaderPackOptions, environmentDefines)
            .map(IdMap::parseItemIdMap)
            .orElse(Object2IntMaps.emptyMap());

        this.entityIdMap = loadEntityIdMap(shaderRoot, shaderPackOptions, environmentDefines);

        Optional<Properties> blockProperties = loadBlockProperties(shaderRoot, shaderPackOptions, environmentDefines);
        if (blockProperties.isPresent()) {
            Properties properties = blockProperties.get();
            this.blockPropertiesMap = parseBlockEntries(properties);
            this.blockRenderTypeMap = parseRenderTypeOverrides(properties);
        } else {
            this.blockPropertiesMap = defaultBlockEntries();
            this.blockRenderTypeMap = Collections.emptyMap();
        }
    }

    private IdMap(Object2IntMap<NamespacedId> itemIdMap,
                  Object2IntMap<NamespacedId> entityIdMap,
                  Int2ObjectMap<List<BlockEntry>> blockPropertiesMap,
                  Map<NamespacedId, BlockRenderType> blockRenderTypeMap) {
        this.itemIdMap = itemIdMap;
        this.entityIdMap = entityIdMap;
        this.blockPropertiesMap = blockPropertiesMap;
        this.blockRenderTypeMap = blockRenderTypeMap;
    }

    public static IdMap empty() {
        return EMPTY;
    }

    private static Object2IntMap<NamespacedId> emptyIdMap() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        return Object2IntMaps.unmodifiable(map);
    }

    private static Optional<Properties> loadProperties(Path shaderPath, String name, ShaderPackOptions shaderPackOptions,
                                                       Iterable<StringPair> environmentDefines) {
        String fileContents = readProperties(shaderPath, name);
        if (fileContents == null) {
            return Optional.empty();
        }

        String processed = PropertiesPreprocessor.preprocessSource(fileContents, shaderPackOptions, environmentDefines);
        Properties properties = new OrderBackedProperties();
        try {
            properties.load(new StringReader(processed));
        } catch (IOException exception) {
            Oculus.LOGGER.error("Failed to parse {} in shader pack", name, exception);
            return Optional.empty();
        }
        return Optional.of(properties);
    }

    private static Optional<Properties> loadRawProperties(Path shaderPath, String name) {
        String fileContents = readProperties(shaderPath, name);
        if (fileContents == null) {
            return Optional.empty();
        }

        Properties properties = new OrderBackedProperties();
        try {
            properties.load(new StringReader(fileContents));
        } catch (IOException exception) {
            Oculus.LOGGER.error("Failed to parse raw {} in shader pack", name, exception);
            return Optional.empty();
        }
        return Optional.of(properties);
    }

    private static Optional<Properties> loadBlockProperties(Path shaderRoot, ShaderPackOptions shaderPackOptions,
                                                            Iterable<StringPair> environmentDefines) {
        return loadProperties(shaderRoot, "block.properties", shaderPackOptions, environmentDefines);
    }

    private static Object2IntMap<NamespacedId> loadEntityIdMap(Path shaderRoot,
                                                               ShaderPackOptions shaderPackOptions,
                                                               Iterable<StringPair> environmentDefines) {
        Object2IntMap<NamespacedId> active = loadProperties(
            shaderRoot,
            "entity.properties",
            shaderPackOptions,
            environmentDefines
        ).map(IdMap::parseEntityIdMap).orElse(emptyIdMap());

        Object2IntMap<NamespacedId> raw = loadRawProperties(shaderRoot, "entity.properties")
            .map(IdMap::parseEntityIdMap)
            .orElse(emptyIdMap());
        if (raw.isEmpty()) {
            return active;
        }

        Object2IntOpenHashMap<NamespacedId> merged = new Object2IntOpenHashMap<>();
        merged.defaultReturnValue(-1);
        merged.putAll(raw);
        merged.putAll(active);
        return Object2IntMaps.unmodifiable(merged);
    }

    private static String readProperties(Path shaderPath, String name) {
        try {
            return new String(Files.readAllBytes(shaderPath.resolve(name)), StandardCharsets.ISO_8859_1);
        } catch (NoSuchFileException ignored) {
            return null;
        } catch (IOException exception) {
            Oculus.LOGGER.error("Failed to read {} from shader pack", name, exception);
            return null;
        }
    }

    private static Object2IntMap<NamespacedId> parseItemIdMap(Properties properties) {
        return parseIdMap(properties, "item.", "item.properties");
    }

    private static Object2IntMap<NamespacedId> parseEntityIdMap(Properties properties) {
        return parseIdMap(properties, "entity.", "entity.properties");
    }

    private static Object2IntMap<NamespacedId> parseIdMap(Properties properties, String keyPrefix, String fileName) {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);

        properties.forEach((keyObj, valueObj) -> {
            String key = (String) keyObj;
            String value = (String) valueObj;

            if (!key.startsWith(keyPrefix)) {
                return;
            }

            int numericId;
            try {
                numericId = Integer.parseInt(key.substring(keyPrefix.length()));
            } catch (NumberFormatException exception) {
                Oculus.LOGGER.warn("Invalid entry '{}' in {}", key, fileName);
                return;
            }

            for (String part : value.split("\\s+")) {
                if (part.contains("=")) {
                    Oculus.LOGGER.warn("Ignoring stateful ID '{}' in {}", part, fileName);
                    continue;
                }
                map.put(new NamespacedId(part), numericId);
            }
        });

        return Object2IntMaps.unmodifiable(map);
    }

    private static Int2ObjectMap<List<BlockEntry>> parseBlockEntries(Properties properties) {
        return parseBlockMap(properties, "block.", "block.properties");
    }

    private static Map<NamespacedId, BlockRenderType> parseRenderTypeOverrides(Properties properties) {
        return parseRenderTypeMap(properties, "layer.", "block.properties");
    }

    private static Int2ObjectMap<List<BlockEntry>> parseBlockMap(Properties properties, String keyPrefix, String fileName) {
        Int2ObjectOpenHashMap<List<BlockEntry>> map = new Int2ObjectOpenHashMap<>();

        properties.forEach((keyObj, valueObj) -> {
            String key = (String) keyObj;
            String value = (String) valueObj;

            if (!key.startsWith(keyPrefix)) {
                return;
            }

            int numericId;
            try {
                numericId = Integer.parseInt(key.substring(keyPrefix.length()));
            } catch (NumberFormatException exception) {
                Oculus.LOGGER.warn("Invalid block entry '{}' in {}", key, fileName);
                return;
            }

            List<BlockEntry> entries = new ArrayList<>();
            for (String part : value.split("\\s+")) {
                if (part.isEmpty()) {
                    continue;
                }
                try {
                    entries.add(BlockEntry.parse(part));
                } catch (Exception exception) {
                    Oculus.LOGGER.warn("Failed to parse block entry '{}' in {}", part, fileName, exception);
                }
            }

            map.put(numericId, Collections.unmodifiableList(entries));
        });

        return Int2ObjectMaps.unmodifiable(map);
    }

    private static Int2ObjectMap<List<BlockEntry>> defaultBlockEntries() {
        Int2ObjectOpenHashMap<List<BlockEntry>> defaults = new Int2ObjectOpenHashMap<>();
        LegacyIdMap.addLegacyValues(defaults);
        return Int2ObjectMaps.unmodifiable(defaults);
    }

    private static Map<NamespacedId, BlockRenderType> parseRenderTypeMap(Properties properties, String keyPrefix, String fileName) {
        Map<NamespacedId, BlockRenderType> overrides = new HashMap<>();
        properties.forEach((keyObj, valueObj) -> {
            String key = (String) keyObj;
            String value = (String) valueObj;

            if (!key.startsWith(keyPrefix)) {
                return;
            }

            String suffix = key.substring(keyPrefix.length());
            BlockRenderType renderType = BlockRenderType.fromString(suffix).orElse(null);
            if (renderType == null) {
                Oculus.LOGGER.warn("Unknown render type '{}' in {}", key, fileName);
                return;
            }

            for (String part : value.split("\\s+")) {
                overrides.put(new NamespacedId(part), renderType);
            }
        });
        return overrides;
    }

    public Object2IntMap<NamespacedId> getItemIdMap() {
        return itemIdMap;
    }

    public Object2IntMap<NamespacedId> getEntityIdMap() {
        return entityIdMap;
    }

    public Int2ObjectMap<List<BlockEntry>> getBlockPropertiesMap() {
        return blockPropertiesMap;
    }

    public Map<NamespacedId, BlockRenderType> getBlockRenderTypeMap() {
        return blockRenderTypeMap;
    }
}
