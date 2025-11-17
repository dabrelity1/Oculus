package net.oculus.uniforms;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for CapturedRenderingState to ensure proper tracking of matrices,
 * camera positions, and entity IDs across frames.
 */
public class CapturedRenderingStateTest {
    private CapturedRenderingState state;

    @Before
    public void setUp() {
        state = CapturedRenderingState.INSTANCE;
    }

    @Test
    public void testInitialState() {
        assertNotNull("CapturedRenderingState instance should exist", state);
        assertNotNull("Gbuffer ModelView matrix should be initialized", state.getGbufferModelView());
        assertNotNull("Gbuffer Projection matrix should be initialized", state.getGbufferProjection());
        assertEquals("Initial gbuffer ModelView should have 16 elements", 16, state.getGbufferModelView().length);
        assertEquals("Initial gbuffer Projection should have 16 elements", 16, state.getGbufferProjection().length);
    }

    @Test
    public void testMatrixArraySizes() {
        assertEquals("ModelView matrix size", 16, state.getGbufferModelView().length);
        assertEquals("Projection matrix size", 16, state.getGbufferProjection().length);
        assertEquals("Previous ModelView matrix size", 16, state.getPreviousModelView().length);
        assertEquals("Previous Projection matrix size", 16, state.getPreviousProjection().length);
        assertEquals("ModelView Inverse matrix size", 16, state.getModelViewInverse().length);
        assertEquals("Projection Inverse matrix size", 16, state.getProjectionInverse().length);
        assertEquals("ModelViewProjection matrix size", 16, state.getModelViewProjection().length);
    }

    @Test
    public void testCameraPositionArraySizes() {
        assertEquals("Camera position size", 3, state.getCameraPosition().length);
        assertEquals("Previous camera position size", 3, state.getPreviousCameraPosition().length);
        assertEquals("Camera position vec size", 3, state.getCameraPositionVec().length);
        assertEquals("Previous camera position vec size", 3, state.getPreviousCameraPositionVec().length);
        assertEquals("Unshifted camera position size", 3, state.getUnshiftedCameraPosition().length);
    }

    @Test
    public void testFogColorArraySizes() {
        assertEquals("Fog color size", 3, state.getFogColor().length);
        assertEquals("Fog color vec4 size", 4, state.getFogColorVec4().length);
    }

    @Test
    public void testEntityIdInitialization() {
        assertEquals("Initial entity ID should be -1", -1, state.getCurrentEntity());
        assertEquals("Initial block entity ID should be -1", -1, state.getCurrentBlockEntity());
    }

    @Test
    public void testSetCurrentEntity() {
        state.setCurrentEntity(42);
        assertEquals("Entity ID should be set", 42, state.getCurrentEntity());
        
        state.setCurrentEntity(-1);
        assertEquals("Entity ID should reset to -1", -1, state.getCurrentEntity());
    }

    @Test
    public void testSetCurrentBlockEntity() {
        state.setCurrentBlockEntity(123);
        assertEquals("Block entity ID should be set", 123, state.getCurrentBlockEntity());
        
        state.setCurrentBlockEntity(-1);
        assertEquals("Block entity ID should reset to -1", -1, state.getCurrentBlockEntity());
    }

    @Test
    public void testSetFogColor() {
        state.setFogColor(0.5f, 0.7f, 0.9f);
        
        float[] fogColor = state.getFogColor();
        assertEquals("Fog red component", 0.5f, fogColor[0], 0.001f);
        assertEquals("Fog green component", 0.7f, fogColor[1], 0.001f);
        assertEquals("Fog blue component", 0.9f, fogColor[2], 0.001f);
        
        float[] fogColorVec4 = state.getFogColorVec4();
        assertEquals("Fog vec4 red component", 0.5f, fogColorVec4[0], 0.001f);
        assertEquals("Fog vec4 green component", 0.7f, fogColorVec4[1], 0.001f);
        assertEquals("Fog vec4 blue component", 0.9f, fogColorVec4[2], 0.001f);
        assertEquals("Fog vec4 alpha component", 1.0f, fogColorVec4[3], 0.001f);
    }

    @Test
    public void testNearFarPlanes() {
        float nearPlane = state.getNearPlane();
        float farPlane = state.getFarPlane();
        
        assertTrue("Near plane should be positive", nearPlane > 0);
        assertTrue("Far plane should be non-negative", farPlane >= 0);
        
        // Near plane should be less than far plane (unless far is 0, which is valid initially)
        if (farPlane > 0) {
            assertTrue("Near plane should be less than far plane", nearPlane < farPlane);
        }
    }

    @Test
    public void testTickDelta() {
        // Tick delta should be a reasonable value for partial ticks (0.0 to 1.0 typically)
        float tickDelta = state.getTickDelta();
        assertTrue("Tick delta should be non-negative", tickDelta >= 0);
    }

    @Test
    public void testEyeAltitude() {
        float eyeAltitude = state.getEyeAltitude();
        // Eye altitude can be any value, just verify it's accessible
        assertNotNull("Eye altitude should be accessible", eyeAltitude);
    }

    @Test
    public void testMatrixNotNull() {
        assertNotNull("Gbuffer ModelView should not be null", state.getGbufferModelView());
        assertNotNull("Gbuffer Projection should not be null", state.getGbufferProjection());
        assertNotNull("Previous ModelView should not be null", state.getPreviousModelView());
        assertNotNull("Previous Projection should not be null", state.getPreviousProjection());
        assertNotNull("ModelView Inverse should not be null", state.getModelViewInverse());
        assertNotNull("Projection Inverse should not be null", state.getProjectionInverse());
        assertNotNull("ModelViewProjection should not be null", state.getModelViewProjection());
    }

    @Test
    public void testCameraPositionsNotNull() {
        assertNotNull("Camera position should not be null", state.getCameraPosition());
        assertNotNull("Previous camera position should not be null", state.getPreviousCameraPosition());
        assertNotNull("Camera position vec should not be null", state.getCameraPositionVec());
        assertNotNull("Previous camera position vec should not be null", state.getPreviousCameraPositionVec());
        assertNotNull("Unshifted camera position should not be null", state.getUnshiftedCameraPosition());
    }

    @Test
    public void testFogColorsNotNull() {
        assertNotNull("Fog color should not be null", state.getFogColor());
        assertNotNull("Fog color vec4 should not be null", state.getFogColorVec4());
    }

    @Test
    public void testEntityIdRange() {
        // Test that entity IDs can be set to various values
        int[] testIds = {-1, 0, 1, 100, 1000, Integer.MAX_VALUE};
        
        for (int id : testIds) {
            state.setCurrentEntity(id);
            assertEquals("Entity ID should be set correctly", id, state.getCurrentEntity());
        }
        
        for (int id : testIds) {
            state.setCurrentBlockEntity(id);
            assertEquals("Block entity ID should be set correctly", id, state.getCurrentBlockEntity());
        }
    }

    @Test
    public void testFogColorBoundaries() {
        // Test boundary values for fog color
        state.setFogColor(0.0f, 0.0f, 0.0f);
        float[] black = state.getFogColor();
        assertEquals(0.0f, black[0], 0.001f);
        assertEquals(0.0f, black[1], 0.001f);
        assertEquals(0.0f, black[2], 0.001f);
        
        state.setFogColor(1.0f, 1.0f, 1.0f);
        float[] white = state.getFogColor();
        assertEquals(1.0f, white[0], 0.001f);
        assertEquals(1.0f, white[1], 0.001f);
        assertEquals(1.0f, white[2], 0.001f);
    }
}
