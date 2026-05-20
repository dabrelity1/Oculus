package com.github.zsoltmolnarr.oculus.client.render.gl.program;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.oculus.shaderpack.ProgramLoadException;

/**
 * Simple data holder describing the source code for a shader program. The sources are loaded
 * eagerly so that repeated program creation does not re-open resource streams.
 */
public final class ProgramDefinition {
    private final String name;
    private final String vertexSource;
    private final String fragmentSource;

    private ProgramDefinition(String name, String vertexSource, String fragmentSource) {
        this.name = name;
        this.vertexSource = vertexSource;
        this.fragmentSource = fragmentSource;
    }

    public static ProgramDefinition fromStrings(String name, String vertexSource, String fragmentSource) {
        if (vertexSource == null || fragmentSource == null) {
            throw new ProgramLoadException("Program sources cannot be null for " + name);
        }
        return new ProgramDefinition(name, vertexSource, fragmentSource);
    }

    public static ProgramDefinition fromResources(String name, ResourceLocation vertex, ResourceLocation fragment) {
        try {
            String vertexSource = readResource(vertex);
            String fragmentSource = readResource(fragment);
            return new ProgramDefinition(name, vertexSource, fragmentSource);
        } catch (IOException ex) {
            throw new ProgramLoadException("Failed to load shader sources for " + name, ex);
        }
    }

    private static String readResource(ResourceLocation location) throws IOException {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            throw new ProgramLoadException("Minecraft client is not available when loading " + location);
        }

        IResource resource = minecraft.getResourceManager().getResource(location);
        try (InputStream stream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public String getName() {
        return name;
    }

    public String getVertexSource() {
        return vertexSource;
    }

    public String getFragmentSource() {
        return fragmentSource;
    }
}
