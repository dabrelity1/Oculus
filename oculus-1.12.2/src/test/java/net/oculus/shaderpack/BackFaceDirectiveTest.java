package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackFaceDirectiveTest {
    @Test
    public void parsesBackFaceDirectives() {
        ShaderProperties properties = new ShaderProperties(
            "backFace.solid=true\n"
                + "backFace.cutout=false\n"
                + "backFace.cutoutMipped=true\n"
                + "backFace.translucent=false\n");

        assertEquals(OptionalBoolean.TRUE, properties.getBackFaceSolid());
        assertEquals(OptionalBoolean.FALSE, properties.getBackFaceCutout());
        assertEquals(OptionalBoolean.TRUE, properties.getBackFaceCutoutMipped());
        assertEquals(OptionalBoolean.FALSE, properties.getBackFaceTranslucent());
    }

    @Test
    public void defaultsToNotRenderingBackFaces() {
        PackDirectives directives = new PackDirectives(ShaderProperties.empty());

        assertFalse(directives.shouldRenderSolidBackFaces());
        assertFalse(directives.shouldRenderCutoutBackFaces());
        assertFalse(directives.shouldRenderCutoutMippedBackFaces());
        assertFalse(directives.shouldRenderTranslucentBackFaces());
    }

    @Test
    public void exposesBackFaceRenderingByLayer() {
        PackDirectives directives = new PackDirectives(new ShaderProperties(
            "backFace.solid=true\n"
                + "backFace.cutout=true\n"
                + "backFace.cutoutMipped=false\n"
                + "backFace.translucent=true\n"));

        assertTrue(directives.shouldRenderSolidBackFaces());
        assertTrue(directives.shouldRenderCutoutBackFaces());
        assertFalse(directives.shouldRenderCutoutMippedBackFaces());
        assertTrue(directives.shouldRenderTranslucentBackFaces());
    }
}
