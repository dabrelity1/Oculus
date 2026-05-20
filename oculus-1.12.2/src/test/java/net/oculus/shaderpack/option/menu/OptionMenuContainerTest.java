package net.oculus.shaderpack.option.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;

import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.include.IncludeGraph;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.ShaderPackOptions;

public class OptionMenuContainerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void buildsReferenceStyleScreensLinksProfilesAndOptionElements() throws Exception {
        Path shaderRoot = temporaryFolder.newFolder("shaders").toPath();
        Files.write(shaderRoot.resolve("composite.fsh"), ImmutableList.of(
            "#version 120",
            "#define BLOOM",
            "#ifdef BLOOM",
            "#endif",
            "#define QUALITY 1 //[1 2]"
        ), StandardCharsets.UTF_8);

        IncludeGraph graph = new IncludeGraph(shaderRoot, ImmutableList.of(AbsolutePackPath.fromAbsolutePath("/composite.fsh")));
        ShaderPackOptions options = new ShaderPackOptions(graph, java.util.Collections.emptyMap());
        ShaderProperties properties = new ShaderProperties(
            "screen=[lighting] <profile> *\n" +
            "screen.columns=2\n" +
            "screen.lighting=BLOOM QUALITY\n" +
            "screen.lighting.columns=1\n" +
            "sliders=QUALITY\n" +
            "profile.high=BLOOM QUALITY=2\n"
        );
        ProfileSet profiles = ProfileSet.fromMap(properties.getProfiles(), options.getOptionSet());

        OptionMenuContainer container = new OptionMenuContainer(properties, options, profiles);

        assertEquals(2, container.mainScreen.getColumnCount());
        assertEquals(2, container.mainScreen.elements.size());
        assertTrue(container.mainScreen.elements.get(0) instanceof OptionMenuLinkElement);
        assertTrue(container.mainScreen.elements.get(1) instanceof OptionMenuProfileElement);

        OptionMenuElementScreen lighting = container.subScreens.get("lighting");
        assertEquals(1, lighting.getColumnCount());
        assertEquals(2, lighting.elements.size());
        assertTrue(lighting.elements.get(0) instanceof OptionMenuBooleanOptionElement);
        assertTrue(lighting.elements.get(1) instanceof OptionMenuStringOptionElement);
        assertTrue(((OptionMenuStringOptionElement) lighting.elements.get(1)).slider);
    }

    @Test
    public void menuScreenColumnCountReturnsRawParsedIntegerLikeReference() throws Exception {
        Path shaderRoot = temporaryFolder.newFolder("raw-columns-shaders").toPath();
        Files.write(shaderRoot.resolve("composite.fsh"), ImmutableList.of(
            "#version 120",
            "#define BLOOM",
            "#ifdef BLOOM",
            "#endif"
        ), StandardCharsets.UTF_8);

        IncludeGraph graph = new IncludeGraph(shaderRoot, ImmutableList.of(AbsolutePackPath.fromAbsolutePath("/composite.fsh")));
        ShaderPackOptions options = new ShaderPackOptions(graph, java.util.Collections.emptyMap());
        ShaderProperties properties = new ShaderProperties(
            "screen=BLOOM\n" +
            "screen.columns=0\n" +
            "screen.raw=BLOOM\n" +
            "screen.raw.columns=-2\n"
        );

        OptionMenuContainer container = new OptionMenuContainer(properties, options, ProfileSet.empty());

        assertEquals(0, container.mainScreen.getColumnCount());
        assertEquals(-2, container.subScreens.get("raw").getColumnCount());
    }
}
