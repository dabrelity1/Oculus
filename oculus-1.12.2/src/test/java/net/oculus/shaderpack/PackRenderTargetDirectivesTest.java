package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.function.Consumer;

import org.junit.Test;

import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.shaderpack.directives.DirectiveHolder;

public class PackRenderTargetDirectivesTest {
    @Test
    public void legacyGaux4FormatAcceptsOnlyReferenceAllowlist() {
        PackRenderTargetDirectives directives = new PackRenderTargetDirectives(Collections.singleton(7));
        RecordingDirectiveHolder holder = new RecordingDirectiveHolder();
        directives.acceptDirectives(holder);

        holder.applyGaux4Format("RGBA32F");
        assertEquals(InternalTextureFormat.RGBA32F, directives.getRenderTargetSettings().get(7).getInternalFormat());

        holder.applyGaux4Format("RGB32F");
        assertEquals(InternalTextureFormat.RGB32F, directives.getRenderTargetSettings().get(7).getInternalFormat());

        holder.applyGaux4Format("RGB16");
        assertEquals(InternalTextureFormat.RGB16, directives.getRenderTargetSettings().get(7).getInternalFormat());

        holder.applyGaux4Format("RGBA16");
        assertEquals(InternalTextureFormat.RGB16, directives.getRenderTargetSettings().get(7).getInternalFormat());
    }

    @Test
    public void colortexFormatConstStillAcceptsGeneralInternalFormats() {
        PackRenderTargetDirectives directives = new PackRenderTargetDirectives(Collections.singleton(7));
        RecordingDirectiveHolder holder = new RecordingDirectiveHolder();
        directives.acceptDirectives(holder);

        holder.applyConstString("colortex7Format", "RGBA16");

        assertEquals(InternalTextureFormat.RGBA16, directives.getRenderTargetSettings().get(7).getInternalFormat());
    }

    private static final class RecordingDirectiveHolder implements DirectiveHolder {
        private Consumer<String> gaux4Format;
        private Consumer<String> colortex7Format;

        void applyGaux4Format(String format) {
            if (gaux4Format == null) {
                fail("GAUX4FORMAT handler was not registered");
            }
            gaux4Format.accept(format);
        }

        void applyConstString(String name, String value) {
            if (!"colortex7Format".equals(name) || colortex7Format == null) {
                fail(name + " handler was not registered");
            }
            colortex7Format.accept(value);
        }

        @Override
        public void acceptCommentStringDirective(String name, Consumer<String> consumer) {
            if ("GAUX4FORMAT".equals(name)) {
                gaux4Format = consumer;
            }
        }

        @Override
        public void acceptConstStringDirective(String name, Consumer<String> consumer) {
            if ("colortex7Format".equals(name)) {
                colortex7Format = consumer;
            }
        }
    }
}
