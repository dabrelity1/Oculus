package net.oculus.shaderpack.preprocessor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import net.oculus.shaderpack.StringPair;

public class JcppProcessorTest {
    @Test
    public void hoistsVersionAndOnlyEnabledExtensionsLikeReference() {
        String processed = JcppProcessor.glslPreprocessSource(
            "#version 120\n" +
            "#if ENABLE_LOD\n" +
            "#extension GL_ARB_shader_texture_lod : require\n" +
            "#endif\n" +
            "#if 0\n" +
            "#extension GL_EXT_gpu_shader4 : enable\n" +
            "#endif\n" +
            "float value = PACK_VALUE;\n",
            ImmutableList.of(
                new StringPair("ENABLE_LOD", "1"),
                new StringPair("PACK_VALUE", "3.0")));

        int version = processed.indexOf("#version");
        int lodExtension = processed.indexOf("#extension");
        int body = processed.indexOf("float value = 3.0;");

        assertTrue("Version directive should be restored at the top", version == 0);
        assertTrue("Enabled extension should be hoisted before shader body", lodExtension > version && lodExtension < body);
        assertTrue(processed.contains("GL_ARB_shader_texture_lod"));
        assertFalse("Inactive extension directives should not be hoisted", processed.contains("GL_EXT_gpu_shader4"));
    }

    @Test
    public void rejectsReservedCollectionMarkersBeforeJcpp() {
        assertReservedMarkerRejected(GlslCollectingListener.VERSION_MARKER);
        assertReservedMarkerRejected(GlslCollectingListener.EXTENSION_MARKER);
    }

    @Test
    public void sourceTokenFailuresUsePreprocessingFailureMessageLikeReference() {
        try {
            JcppProcessor.glslPreprocessSource(
                "#version 120\n" +
                (char) 0 +
                "\n" +
                "void main() {}\n",
                Collections.emptyList());
            fail("Expected JCPP source preprocessing to fail");
        } catch (RuntimeException exception) {
            assertTrue(exception.getMessage().contains("GLSL source pre-processing failed"));
        }
    }

    @Test
    public void macroSetupFailureMessageMatchesReferenceSource() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/shaderpack/preprocessor/JcppProcessor.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("Unexpected LexerException processing macros"));
        assertFalse(source.contains("Unexpected LexerException processing GLSL macros"));
    }

    private static void assertReservedMarkerRejected(String marker) {
        try {
            JcppProcessor.glslPreprocessSource(marker + " 120\nvoid main() {}\n", Collections.emptyList());
            fail("Expected reserved marker rejection");
        } catch (RuntimeException exception) {
            assertTrue(exception.getMessage().contains("reserved Oculus preprocessing markers"));
        }
    }
}
