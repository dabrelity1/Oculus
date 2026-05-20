package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShadowCullingModeTest {
    @Test
    public void parsesReversedShadowCulling() {
        ShaderProperties properties = new ShaderProperties("shadow.culling = reversed\n");

        assertEquals(ShadowCullingMode.REVERSED, properties.getShadowCullingMode());
        assertEquals(OptionalBoolean.TRUE, properties.getShadowCulling());
    }

    @Test
    public void parsesBooleanShadowCullingWithReferenceCaseSensitivity() {
        ShaderProperties enabled = new ShaderProperties("shadow.culling = true\n");
        ShaderProperties disabled = new ShaderProperties("shadow.culling = false\n");
        ShaderProperties uppercase = new ShaderProperties("shadow.culling = TRUE\n");
        ShaderProperties uppercaseReversed = new ShaderProperties("shadow.culling = REVERSED\n");

        assertEquals(ShadowCullingMode.ENABLED, enabled.getShadowCullingMode());
        assertEquals(OptionalBoolean.TRUE, enabled.getShadowCulling());
        assertEquals(ShadowCullingMode.DISABLED, disabled.getShadowCullingMode());
        assertEquals(OptionalBoolean.FALSE, disabled.getShadowCulling());
        assertEquals(ShadowCullingMode.DEFAULT, uppercase.getShadowCullingMode());
        assertEquals(OptionalBoolean.DEFAULT, uppercase.getShadowCulling());
        assertEquals(ShadowCullingMode.DEFAULT, uppercaseReversed.getShadowCullingMode());
        assertEquals(OptionalBoolean.DEFAULT, uppercaseReversed.getShadowCulling());
    }
}
