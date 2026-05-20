package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FrustumCullingDirectiveTest {
    @Test
    public void parsesFrustumCullingDirective() {
        ShaderProperties disabled = new ShaderProperties("frustum.culling=false\n");
        ShaderProperties enabled = new ShaderProperties("frustum.culling=true\n");

        assertEquals(OptionalBoolean.FALSE, disabled.getFrustumCulling());
        assertFalse(new PackDirectives(disabled).shouldUseFrustumCulling());

        assertEquals(OptionalBoolean.TRUE, enabled.getFrustumCulling());
        assertTrue(new PackDirectives(enabled).shouldUseFrustumCulling());
    }
}
