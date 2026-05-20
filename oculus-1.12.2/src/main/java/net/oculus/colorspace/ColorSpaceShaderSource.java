package net.oculus.colorspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.oculus.shaderpack.StringPair;
import net.oculus.shaderpack.preprocessor.JcppProcessor;

final class ColorSpaceShaderSource {
    private static final String FRAGMENT_RESOURCE = "/colorSpace.csh";

    private ColorSpaceShaderSource() {
    }

    static String createFragmentVertexSource() {
        return "#version 120\n"
            + "varying vec2 uv;\n"
            + "void main() {\n"
            + "    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);\n"
            + "    uv = gl_MultiTexCoord0.xy;\n"
            + "}\n";
    }

    static String createFragmentSource(ColorSpace colorSpace) {
        String source = JcppProcessor.glslPreprocessSource(readResource(FRAGMENT_RESOURCE),
            createDefines(colorSpace, false));
        return patchForLegacyFragmentPipeline(source);
    }

    static String createComputeSource(ColorSpace colorSpace) {
        return JcppProcessor.glslPreprocessSource(readResource(FRAGMENT_RESOURCE),
            createDefines(colorSpace, true));
    }

    static List<StringPair> createDefines(ColorSpace colorSpace, boolean compute) {
        ColorSpace effective = colorSpace == null ? ColorSpace.SRGB : colorSpace;
        List<StringPair> defines = new ArrayList<>();
        if (compute) {
            defines.add(new StringPair("COMPUTE", ""));
        }
        defines.add(new StringPair("CURRENT_COLOR_SPACE", String.valueOf(effective.ordinal())));
        for (ColorSpace space : ColorSpace.values()) {
            defines.add(new StringPair(space.name(), String.valueOf(space.ordinal())));
        }
        return defines;
    }

    static String patchForLegacyFragmentPipeline(String source) {
        if (source == null) {
            return null;
        }

        String patched = source.replaceAll("(?m)^\\s*#version\\s+330\\s+core\\s*$", "#version 120");
        patched = patched.replaceAll("(?m)^\\s*in\\s+vec2\\s+uv\\s*;\\s*$", "varying vec2 uv;");
        patched = patched.replaceAll("(?m)^\\s*out\\s+vec3\\s+outColor\\s*;\\s*$", "");
        patched = patched.replaceAll("\\btexture\\s*\\(\\s*readImage\\s*,\\s*uv\\s*\\)", "texture2D(readImage, uv)");
        patched = patched.replaceAll("\\boutColor\\s*=\\s*TargetColor\\s*;", "gl_FragColor = vec4(TargetColor, 1.0);");
        return patched;
    }

    private static String readResource(String resourcePath) {
        try (InputStream input = ColorSpaceShaderSource.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing color-space shader resource " + resourcePath);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to read color-space shader resource " + resourcePath, exception);
        }
    }
}
