package net.oculus.shaderpack;

import net.oculus.shaderpack.materialmap.NamespacedId;
import net.oculus.shaderpack.materialmap.BlockRenderType;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.include.IncludeGraph;
import net.oculus.shaderpack.option.ShaderPackOptions;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/**
 * Tests for IdMap to ensure proper parsing and lookup of block, entity, and item IDs.
 */
public class IdMapTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testEmptyIdMap() {
        IdMap emptyMap = IdMap.empty();
        assertNotNull("Empty IdMap should not be null", emptyMap);
        assertNotNull("Empty item map should not be null", emptyMap.getItemIdMap());
        assertNotNull("Empty entity map should not be null", emptyMap.getEntityIdMap());
        assertNotNull("Empty block properties map should not be null", emptyMap.getBlockPropertiesMap());
        assertNotNull("Empty block render type map should not be null", emptyMap.getBlockRenderTypeMap());
    }

    @Test
    public void testEmptyItemIdMapReturnsDefaultValue() {
        IdMap emptyMap = IdMap.empty();
        NamespacedId testId = new NamespacedId("minecraft:stone");

        int result = emptyMap.getItemIdMap().getInt(testId);
        assertEquals("Empty item map should return -1 for unknown IDs", -1, result);
    }

    @Test
    public void testEmptyEntityIdMapReturnsDefaultValue() {
        IdMap emptyMap = IdMap.empty();
        NamespacedId testId = new NamespacedId("minecraft:zombie");

        int result = emptyMap.getEntityIdMap().getInt(testId);
        assertEquals("Empty entity map should return -1 for unknown IDs", -1, result);
    }

    @Test
    public void testEmptyBlockPropertiesMapIsEmpty() {
        IdMap emptyMap = IdMap.empty();
        assertTrue("Empty block properties map should be empty",
            emptyMap.getBlockPropertiesMap().isEmpty());
    }

    @Test
    public void testEmptyBlockRenderTypeMapIsEmpty() {
        IdMap emptyMap = IdMap.empty();
        assertTrue("Empty block render type map should be empty",
            emptyMap.getBlockRenderTypeMap().isEmpty());
    }

    @Test
    public void testIdMapAccessorsNotNull() {
        IdMap emptyMap = IdMap.empty();

        assertNotNull("getItemIdMap should not return null", emptyMap.getItemIdMap());
        assertNotNull("getEntityIdMap should not return null", emptyMap.getEntityIdMap());
        assertNotNull("getBlockPropertiesMap should not return null", emptyMap.getBlockPropertiesMap());
        assertNotNull("getBlockRenderTypeMap should not return null", emptyMap.getBlockRenderTypeMap());
    }

    @Test
    public void testNamespacedIdCreation() {
        // Test that NamespacedId can be created with various formats
        NamespacedId withNamespace = new NamespacedId("minecraft:stone");
        assertNotNull("NamespacedId with namespace should be created", withNamespace);

        NamespacedId withoutNamespace = new NamespacedId("stone");
        assertNotNull("NamespacedId without namespace should be created", withoutNamespace);
    }

    @Test
    public void parsesBlockPropertiesLayerOverridesUsedByComplementary() throws Exception {
        Path shaderRoot = temporaryFolder.newFolder("shaders-layer").toPath();
        Files.write(shaderRoot.resolve("dummy.vsh"), ImmutableList.of("#version 120"), StandardCharsets.UTF_8);
        Files.write(shaderRoot.resolve("block.properties"), ImmutableList.of(
            "layer.translucent=glass glass_pane beacon",
            "layer.cutout=minecraft:oak_leaves"
        ), StandardCharsets.ISO_8859_1);

        IncludeGraph graph = new IncludeGraph(shaderRoot, ImmutableList.of(AbsolutePackPath.fromAbsolutePath("/dummy.vsh")));
        ShaderPackOptions options = new ShaderPackOptions(graph, Collections.emptyMap());
        IdMap idMap = new IdMap(shaderRoot, options, Collections.emptyList());

        assertEquals(BlockRenderType.TRANSLUCENT, idMap.getBlockRenderTypeMap().get(new NamespacedId("glass")));
        assertEquals(BlockRenderType.TRANSLUCENT, idMap.getBlockRenderTypeMap().get(new NamespacedId("glass_pane")));
        assertEquals(BlockRenderType.TRANSLUCENT, idMap.getBlockRenderTypeMap().get(new NamespacedId("beacon")));
        assertEquals(BlockRenderType.CUTOUT, idMap.getBlockRenderTypeMap().get(new NamespacedId("minecraft:oak_leaves")));
    }

    @Test
    public void entityPropertiesKeepInactiveRowsAsFallbackOnlyMappings() throws Exception {
        Path shaderRoot = temporaryFolder.newFolder("shaders-entity-fallback").toPath();
        Files.write(shaderRoot.resolve("dummy.vsh"), ImmutableList.of("#version 120"), StandardCharsets.UTF_8);
        Files.write(shaderRoot.resolve("entity.properties"), ImmutableList.of(
            "#if MC_VERSION >= 11300",
            "entity.50016=player mannequin",
            "#else",
            "# empty 1.12 section",
            "#endif"
        ), StandardCharsets.ISO_8859_1);

        IncludeGraph graph = new IncludeGraph(shaderRoot, ImmutableList.of(AbsolutePackPath.fromAbsolutePath("/dummy.vsh")));
        ShaderPackOptions options = new ShaderPackOptions(graph, Collections.emptyMap());
        IdMap idMap = new IdMap(shaderRoot, options, Collections.singletonList(new StringPair("MC_VERSION", "11202")));

        assertEquals(50016, idMap.getEntityIdMap().getInt(new NamespacedId("player")));
        assertEquals(50016, idMap.getEntityIdMap().getInt(new NamespacedId("mannequin")));
    }

    @Test
    public void activeEntityPropertiesWinOverInactiveFallbackMappings() throws Exception {
        Path shaderRoot = temporaryFolder.newFolder("shaders-entity-active").toPath();
        Files.write(shaderRoot.resolve("dummy.vsh"), ImmutableList.of("#version 120"), StandardCharsets.UTF_8);
        Files.write(shaderRoot.resolve("entity.properties"), ImmutableList.of(
            "#if MC_VERSION >= 11300",
            "entity.50016=player mannequin",
            "#else",
            "entity.12=player",
            "#endif"
        ), StandardCharsets.ISO_8859_1);

        IncludeGraph graph = new IncludeGraph(shaderRoot, ImmutableList.of(AbsolutePackPath.fromAbsolutePath("/dummy.vsh")));
        ShaderPackOptions options = new ShaderPackOptions(graph, Collections.emptyMap());
        IdMap idMap = new IdMap(shaderRoot, options, Collections.singletonList(new StringPair("MC_VERSION", "11202")));

        assertEquals(12, idMap.getEntityIdMap().getInt(new NamespacedId("player")));
        assertEquals(50016, idMap.getEntityIdMap().getInt(new NamespacedId("mannequin")));
    }
}
