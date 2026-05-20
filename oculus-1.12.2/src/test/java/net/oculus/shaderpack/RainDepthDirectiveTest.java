package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RainDepthDirectiveTest {
    @Test
    public void parsesRainDepthDirective() {
        ShaderProperties enabled = new ShaderProperties("rain.depth=true\n");
        ShaderProperties disabled = new ShaderProperties("rain.depth=false\n");

        assertEquals(OptionalBoolean.TRUE, enabled.getRainDepth());
        assertTrue(new PackDirectives(enabled).rainDepth());

        assertEquals(OptionalBoolean.FALSE, disabled.getRainDepth());
        assertFalse(new PackDirectives(disabled).rainDepth());
    }

    @Test
    public void rainDepthDefaultsDisabled() {
        assertFalse(new PackDirectives(ShaderProperties.empty()).rainDepth());
    }
}
