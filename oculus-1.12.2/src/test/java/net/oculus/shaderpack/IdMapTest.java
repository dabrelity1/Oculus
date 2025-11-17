package net.oculus.shaderpack;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for IdMap to ensure proper parsing and lookup of block, entity, and item IDs.
 */
public class IdMapTest {

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
        materialmap.NamespacedId testId = new materialmap.NamespacedId("minecraft:stone");
        
        int result = emptyMap.getItemIdMap().getInt(testId);
        assertEquals("Empty item map should return -1 for unknown IDs", -1, result);
    }

    @Test
    public void testEmptyEntityIdMapReturnsDefaultValue() {
        IdMap emptyMap = IdMap.empty();
        materialmap.NamespacedId testId = new materialmap.NamespacedId("minecraft:zombie");
        
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
        materialmap.NamespacedId withNamespace = new materialmap.NamespacedId("minecraft:stone");
        assertNotNull("NamespacedId with namespace should be created", withNamespace);
        
        materialmap.NamespacedId withoutNamespace = new materialmap.NamespacedId("stone");
        assertNotNull("NamespacedId without namespace should be created", withoutNamespace);
    }
}
