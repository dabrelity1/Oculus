package net.oculus.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class OculusChunkShaderBindingPointsTest {
    @Test
    public void relictiumTerrainAttributesUse1165SodiumBindingPoints() {
        assertEquals(5, OculusChunkShaderBindingPoints.NORMAL.getGenericAttributeIndex());
        assertEquals(6, OculusChunkShaderBindingPoints.TANGENT.getGenericAttributeIndex());
        assertEquals(7, OculusChunkShaderBindingPoints.MID_UV.getGenericAttributeIndex());
        assertEquals(8, OculusChunkShaderBindingPoints.BLOCK_ID.getGenericAttributeIndex());
        assertEquals(9, OculusChunkShaderBindingPoints.MID_BLOCK.getGenericAttributeIndex());
    }

    @Test
    public void legacyEntityAliasStillNamesTheBlockIdBinding() {
        assertSame(OculusChunkShaderBindingPoints.BLOCK_ID, OculusChunkShaderBindingPoints.ENTITY);
    }
}
