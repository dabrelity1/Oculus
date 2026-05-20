package net.oculus.shaderpack.preprocessor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;

import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.StringPair;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.include.IncludeGraph;
import net.oculus.shaderpack.option.ShaderPackOptions;
import net.oculus.vendored.joml.Vector2i;

public class PropertiesPreprocessorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void changedConfigsAreVisibleToPropertiesConditionals() throws Exception {
        Path shaderRoot = temporaryFolder.newFolder("shaders").toPath();
        AbsolutePackPath sourcePath = AbsolutePackPath.fromAbsolutePath("/composite.fsh");

        Files.write(shaderRoot.resolve("composite.fsh"), ImmutableList.of(
            "#version 120",
            "#define QUALITY 1 //[1 2]"
        ), StandardCharsets.UTF_8);

        IncludeGraph graph = new IncludeGraph(shaderRoot, ImmutableList.of(sourcePath));
        Map<String, String> changedConfigs = new HashMap<>();
        changedConfigs.put("QUALITY", "2");

        ShaderPackOptions shaderPackOptions = new ShaderPackOptions(graph, changedConfigs);
        String processed = PropertiesPreprocessor.preprocessSource(
            "#if QUALITY == 2\n" +
            "program.composite.enabled=false\n" +
            "#endif\n" +
            "#if QUALITY == 1\n" +
            "program.final.enabled=false\n" +
            "#endif\n",
            shaderPackOptions,
            Collections.emptyList()
        );

        assertTrue(processed.contains("program.composite.enabled=false"));
        assertFalse(processed.contains("program.final.enabled=false"));
    }

    @Test
    public void sizeBufferCanUseStringOptionValues() throws Exception {
        Path shaderRoot = temporaryFolder.newFolder("shaders-options").toPath();
        AbsolutePackPath sourcePath = AbsolutePackPath.fromAbsolutePath("/lib/common.glsl");

        Files.createDirectories(shaderRoot.resolve("lib"));
        Files.write(shaderRoot.resolve("lib/common.glsl"), ImmutableList.of(
            "#version 120",
            "#define REFLECTION_RES 0.5 //[1.0 0.5]"
        ), StandardCharsets.UTF_8);

        IncludeGraph graph = new IncludeGraph(shaderRoot, ImmutableList.of(sourcePath));
        ShaderPackOptions shaderPackOptions = new ShaderPackOptions(graph, Collections.emptyMap());
        String processed = PropertiesPreprocessor.preprocessSource(
            "#if defined IS_IRIS && defined IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE\n" +
            "size.buffer.colortex1 = REFLECTION_RES REFLECTION_RES\n" +
            "#endif\n",
            shaderPackOptions,
            ImmutableList.of(
                new StringPair("IS_IRIS", ""),
                new StringPair("IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE", "")
            )
        );

        ShaderProperties properties = new ShaderProperties(processed, shaderPackOptions);
        Vector2i dimensions = new PackDirectives(properties).getTextureScaleOverride(1, 1920, 1080);

        assertTrue(processed.contains("size.buffer.colortex1 = REFLECTION_RES REFLECTION_RES"));
        assertTrue(dimensions.x() == 960);
        assertTrue(dimensions.y() == 540);
    }

    @Test
    public void shaderPropertiesCanGateDirectivesOnSelfDeclaredIrisFeatures() throws Exception {
        Path shaderRoot = temporaryFolder.newFolder("shaders-feature-defines").toPath();
        AbsolutePackPath sourcePath = AbsolutePackPath.fromAbsolutePath("/lib/common.glsl");

        Files.createDirectories(shaderRoot.resolve("lib"));
        Files.write(shaderRoot.resolve("lib/common.glsl"), ImmutableList.of(
            "#version 120",
            "#define REFLECTION_RES 0.5 //[1.0 0.5]"
        ), StandardCharsets.UTF_8);

        IncludeGraph graph = new IncludeGraph(shaderRoot, ImmutableList.of(sourcePath));
        ShaderPackOptions shaderPackOptions = new ShaderPackOptions(graph, Collections.emptyMap());
        String processed = PropertiesPreprocessor.preprocessShaderProperties(
            "iris.features.optional = BLOCK_EMISSION_ATTRIBUTE\n" +
            "#if defined IS_IRIS && defined IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE\n" +
            "size.buffer.colortex1 = REFLECTION_RES REFLECTION_RES\n" +
            "#endif\n",
            shaderPackOptions,
            ImmutableList.of(new StringPair("IS_IRIS", ""))
        );

        ShaderProperties properties = new ShaderProperties(processed, shaderPackOptions);
        Vector2i dimensions = new PackDirectives(properties).getTextureScaleOverride(1, 1920, 1080);

        assertTrue(processed.contains("size.buffer.colortex1 = REFLECTION_RES REFLECTION_RES"));
        assertTrue(dimensions.x() == 960);
        assertTrue(dimensions.y() == 540);
    }
}
