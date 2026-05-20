package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OcclusionCullingDirectiveTest {
    @Test
    public void parsesOcclusionCullingDirective() {
        ShaderProperties disabled = new ShaderProperties("occlusion.culling=false\n");
        ShaderProperties enabled = new ShaderProperties("occlusion.culling=true\n");

        assertEquals(OptionalBoolean.FALSE, disabled.getOcclusionCulling());
        assertFalse(new PackDirectives(disabled).shouldUseOcclusionCulling());

        assertEquals(OptionalBoolean.TRUE, enabled.getOcclusionCulling());
        assertTrue(new PackDirectives(enabled).shouldUseOcclusionCulling());
    }

    @Test
    public void occlusionCullingDefaultsEnabled() {
        assertTrue(new PackDirectives(ShaderProperties.empty()).shouldUseOcclusionCulling());
    }
}
