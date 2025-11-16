package net.oculus.shaderpack;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;

import net.oculus.Oculus;
import net.oculus.blockrendering.BlockMaterialMapping;
import net.oculus.gl.shader.StandardMacros;
import net.oculus.shaderpack.IdMap;
import net.oculus.shaderpack.StringPair;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.include.IncludeGraph;
import net.oculus.shaderpack.include.IncludeProcessor;
import net.oculus.shaderpack.include.ShaderPackSourceNames;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.ShaderPackOptions;
import net.oculus.shaderpack.option.menu.OptionMenuContainer;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.texture.CustomTextureData;
import net.oculus.shaderpack.texture.TextureFilteringData;
import net.oculus.shaderpack.texture.TextureStage;

/**
 * Loads shader packs from disk and prepares their shader sources for compilation.
 */
public final class ShaderPackLoader {
	private static final Gson GSON = new Gson();
	private ShaderPackLoader() {
	}

	public static ShaderPack load(String packName) throws IOException {
		if (packName == null || packName.trim().isEmpty()) {
			return ShaderPack.placeholder();
		}

		Path shaderpacksDir = Minecraft.getMinecraft().gameDir.toPath().resolve("shaderpacks");
		Path packPath = shaderpacksDir.resolve(packName);

		if (!Files.exists(packPath)) {
			throw new IOException("Shader pack " + packName + " does not exist");
		}

		ShaderProperties properties = readShaderProperties(packPath);
		ShaderSourceData shaderData = buildShaderSources(packPath);
		MutableOptionValues optionValues = shaderData.shaderPackOptions.getOptionValues().mutableCopy();
		ProfileSet profiles = ProfileSet.fromMap(properties.getProfiles(), shaderData.shaderPackOptions.getOptionSet());
		CustomTextureBundle textureBundle = loadCustomTextures(packPath, properties);
		IdMap idMap = loadIdMap(packPath, shaderData.shaderPackOptions);
		Object2IntMap<IBlockState> blockStateIds = BlockMaterialMapping.createBlockStateIdMap(idMap.getBlockPropertiesMap());

		return ShaderPack.of(
			packName,
			OptionMenuContainer.EMPTY,
			properties,
			optionValues,
			shaderData.shaderPackSource.programRoot,
			shaderData.shaderPackSource.sourceProvider,
			textureBundle.customTextureDataMap,
			textureBundle.customNoiseTexture,
			idMap,
			blockStateIds,
			profiles
		);
	}

	public static ShaderPack internalPack() {
		return ShaderPack.placeholder();
	}

	private static ShaderSourceData buildShaderSources(Path packPath) {
		try {
			if (Files.isDirectory(packPath)) {
				Path shaderRoot = resolveDirectoryShaderRoot(packPath);
				if (shaderRoot != null) {
					return createShaderSourceData(shaderRoot);
				}
				return ShaderSourceData.empty(packPath);
			}

			if (Files.isRegularFile(packPath)) {
				try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
					Path shaderRoot = resolveZipShaderRoot(zipFs);
					if (shaderRoot != null) {
						return createShaderSourceData(shaderRoot);
					}
					return ShaderSourceData.empty(packPath);
				}
			}
		} catch (IOException | IllegalStateException exception) {
			Oculus.LOGGER.warn("Failed to prepare shader sources for {}", packPath, exception);
		}

		return ShaderSourceData.empty(packPath);
	}

	private static ShaderSourceData createShaderSourceData(Path shaderRoot) throws IOException {
		ImmutableList<AbsolutePackPath> shaderEntries = collectShaderEntries(shaderRoot);
		IncludeGraph graph = new IncludeGraph(shaderRoot, shaderEntries);
		ShaderPackOptions shaderPackOptions = new ShaderPackOptions(graph, Collections.emptyMap());
		ShaderPackSource source = createSourceBundle(shaderRoot, shaderPackOptions.getIncludes());
		return new ShaderSourceData(source, shaderPackOptions);
	}

	private static Path resolveDirectoryShaderRoot(Path packPath) {
		if (Files.isDirectory(packPath)) {
			Path shadersFolder = packPath.resolve("shaders");
			if (Files.exists(shadersFolder)) {
				return shadersFolder;
			}
			return packPath;
		}
		return null;
	}

	private static Path resolveZipShaderRoot(FileSystem zipFs) throws IOException {
		Path shadersFolder = zipFs.getPath("/shaders");
		if (Files.exists(shadersFolder)) {
			return shadersFolder;
		}

		Path root = zipFs.getPath("/");
		if (Files.exists(root)) {
			return root;
		}

		return null;
	}

	private static ShaderPackSource createSourceBundle(Path shaderRoot, IncludeGraph graph) throws IOException {
		if (graph.getNodes().isEmpty()) {
			return ShaderPackSource.empty();
		}

		IncludeProcessor processor = new IncludeProcessor(graph);
		Map<AbsolutePackPath, String> cache = new ConcurrentHashMap<>();

		Function<AbsolutePackPath, String> provider = path -> {
			if (path == null) {
				return null;
			}

			return cache.computeIfAbsent(path, key -> {
				ImmutableList<String> lines = processor.getIncludedFile(key);
				if (lines == null) {
					return null;
				}

				StringBuilder builder = new StringBuilder();
				for (String line : lines) {
					builder.append(line).append('\n');
				}
				return builder.toString();
			});
		};

		AbsolutePackPath programRoot = detectProgramRoot(shaderRoot);
		return new ShaderPackSource(programRoot, provider);
	}

	private static ImmutableList<AbsolutePackPath> collectShaderEntries(Path shaderRoot) throws IOException {
		ImmutableList.Builder<AbsolutePackPath> builder = ImmutableList.builder();

		try (Stream<Path> stream = Files.walk(shaderRoot)) {
			stream.filter(Files::isRegularFile)
				.filter(path -> hasShaderExtension(path.getFileName().toString()))
				.forEach(path -> builder.add(toAbsolute(shaderRoot, path)));
		}

		ShaderPackSourceNames.findPresentSources(
			builder,
			shaderRoot,
			AbsolutePackPath.fromAbsolutePath("/"),
			ShaderPackSourceNames.POTENTIAL_STARTS
		);

		return builder.build();
	}

	private static boolean hasShaderExtension(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.endsWith(".vsh") || lower.endsWith(".fsh") || lower.endsWith(".gsh") || lower.endsWith(".csh") || lower.endsWith(".glsl");
	}

	private static AbsolutePackPath toAbsolute(Path shaderRoot, Path file) {
		Path relative = shaderRoot.relativize(file);
		String normalised = relative.toString().replace('\\', '/');
		if (!normalised.startsWith("/")) {
			normalised = "/" + normalised;
		}
		return AbsolutePackPath.fromAbsolutePath(normalised);
	}

	private static AbsolutePackPath detectProgramRoot(Path shaderRoot) {
		if (Files.isDirectory(shaderRoot.resolve("world0"))) {
			return AbsolutePackPath.fromAbsolutePath("/world0");
		}
		return AbsolutePackPath.fromAbsolutePath("/");
	}

	private static ShaderProperties readShaderProperties(Path packPath) {
		if (Files.isDirectory(packPath)) {
			Path propertiesPath = resolveDirectoryProperties(packPath);
			if (propertiesPath != null && Files.exists(propertiesPath)) {
				try {
					String contents = new String(Files.readAllBytes(propertiesPath), StandardCharsets.UTF_8);
					return new ShaderProperties(contents);
				} catch (IOException exception) {
					Oculus.LOGGER.warn("Failed to read shaders.properties from directory {}", packPath, exception);
				}
			}
			return ShaderProperties.empty();
		}

		if (Files.isRegularFile(packPath)) {
			try (ZipFile zipFile = new ZipFile(packPath.toFile())) {
				ZipEntry entry = resolveZipProperties(zipFile);
				if (entry != null) {
					try (InputStream stream = zipFile.getInputStream(entry)) {
						String contents = readAll(stream);
						return new ShaderProperties(contents);
					}
				}
			} catch (IOException exception) {
				Oculus.LOGGER.warn("Failed to read shaders.properties from archive {}", packPath, exception);
			}
		}

		return ShaderProperties.empty();
	}

	private static Path resolveDirectoryProperties(Path packPath) {
		Path shadersFolder = packPath.resolve("shaders");
		Path withinShaders = shadersFolder.resolve("shaders.properties");
		if (Files.exists(withinShaders)) {
			return withinShaders;
		}

		Path rootFile = packPath.resolve("shaders.properties");
		if (Files.exists(rootFile)) {
			return rootFile;
		}

		return null;
	}

	private static ZipEntry resolveZipProperties(ZipFile zipFile) {
		ZipEntry entry = zipFile.getEntry("shaders/shaders.properties");
		if (entry != null) {
			return entry;
		}
		return zipFile.getEntry("shaders.properties");
	}

	private static String readAll(InputStream stream) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[4096];
		int read;

		while ((read = stream.read(chunk)) != -1) {
			buffer.write(chunk, 0, read);
		}

		return buffer.toString(StandardCharsets.UTF_8.name());
	}

	private static final class ShaderSourceData {
		private final ShaderPackSource shaderPackSource;
		private final ShaderPackOptions shaderPackOptions;

		private ShaderSourceData(ShaderPackSource shaderPackSource, ShaderPackOptions shaderPackOptions) {
			this.shaderPackSource = shaderPackSource;
			this.shaderPackOptions = shaderPackOptions;
		}

		private static ShaderSourceData empty(Path packPath) {
			IncludeGraph graph = new IncludeGraph(packPath, ImmutableList.of());
			ShaderPackOptions shaderPackOptions = new ShaderPackOptions(graph, Collections.emptyMap());
			return new ShaderSourceData(ShaderPackSource.empty(), shaderPackOptions);
		}
	}

	private static final class ShaderPackSource {
		private final AbsolutePackPath programRoot;
		private final Function<AbsolutePackPath, String> sourceProvider;

		private ShaderPackSource(AbsolutePackPath programRoot, Function<AbsolutePackPath, String> sourceProvider) {
			this.programRoot = programRoot;
			this.sourceProvider = sourceProvider;
		}

		private static ShaderPackSource empty() {
			return new ShaderPackSource(AbsolutePackPath.fromAbsolutePath("/"), path -> null);
		}
	}

	private static IdMap loadIdMap(Path packPath, ShaderPackOptions shaderPackOptions) {
		Iterable<StringPair> environmentDefines = StandardMacros.createStandardEnvironmentDefines();

		if (Files.isDirectory(packPath)) {
			Path shaderRoot = resolveDirectoryShaderRoot(packPath);
			if (shaderRoot != null) {
				return new IdMap(shaderRoot, shaderPackOptions, environmentDefines);
			}
		}

		if (Files.isRegularFile(packPath)) {
			try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
				Path shaderRoot = resolveZipShaderRoot(zipFs);
				if (shaderRoot != null) {
					return new IdMap(shaderRoot, shaderPackOptions, environmentDefines);
				}
			} catch (IOException exception) {
				Oculus.LOGGER.warn("Failed to read block properties from {}", packPath, exception);
			}
		}

		return IdMap.empty();
	}

	private static CustomTextureBundle loadCustomTextures(Path packPath, ShaderProperties properties) {
		if (properties == null) {
			return CustomTextureBundle.empty();
		}

		if (Files.isDirectory(packPath)) {
			Path shaderRoot = resolveDirectoryShaderRoot(packPath);
			return readCustomTexturesFromRoot(shaderRoot != null ? shaderRoot : packPath, properties);
		}

		if (Files.isRegularFile(packPath)) {
			try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
				Path shaderRoot = resolveZipShaderRoot(zipFs);
				return readCustomTexturesFromRoot(shaderRoot != null ? shaderRoot : zipFs.getPath("/"), properties);
			} catch (IOException exception) {
				Oculus.LOGGER.warn("Failed to read custom textures from {}", packPath, exception);
			}
		}

		return CustomTextureBundle.empty();
	}

	private static CustomTextureBundle readCustomTexturesFromRoot(Path shaderRoot, ShaderProperties properties) {
		if (shaderRoot == null) {
			return CustomTextureBundle.empty();
		}

		EnumMap<TextureStage, Map<String, CustomTextureData>> textureDataMap = new EnumMap<>(TextureStage.class);
		properties.getCustomTextures().forEach((stage, samplerMap) -> {
			Map<String, CustomTextureData> resolved = new HashMap<>();
			samplerMap.forEach((samplerName, path) -> {
				CustomTextureData data = readTextureSafely(shaderRoot, path, samplerName);
				if (data != null) {
					resolved.put(samplerName, data);
				}
			});
			if (!resolved.isEmpty()) {
				textureDataMap.put(stage, resolved);
			}
		});

		CustomTextureData noiseTexture = properties.getNoiseTexturePath()
			.map(path -> readTextureSafely(shaderRoot, path, "texture.noise"))
			.orElse(null);

		return new CustomTextureBundle(textureDataMap, noiseTexture);
	}

	private static CustomTextureData readTextureSafely(Path shaderRoot, String path, String identifier) {
		if (path == null || path.trim().isEmpty()) {
			return null;
		}

		try {
			return readTexture(shaderRoot, path.trim());
		} catch (IOException exception) {
			Oculus.LOGGER.warn("Failed to read custom texture '{}' at '{}'", identifier, path, exception);
			return null;
		}
	}

	private static CustomTextureData readTexture(Path shaderRoot, String path) throws IOException {
		if (path.contains(":")) {
			String[] parts = path.split(":", 2);
			String namespace = parts[0];
			String location = parts.length > 1 ? parts[1] : "";

			if ("minecraft".equals(namespace)
				&& ("dynamic/lightmap_1".equals(location) || "dynamic/light_map_1".equals(location))) {
				return new CustomTextureData.LightmapMarker();
			}

			return new CustomTextureData.ResourceData(namespace, location);
		}

		String normalisedPath = path.startsWith("/") ? path.substring(1) : path;
		Path resolvedPath = shaderRoot.resolve(normalisedPath);

		boolean blur = false;
		boolean clamp = false;

		Path mcMetaPath = shaderRoot.resolve(normalisedPath + ".mcmeta");
		if (Files.exists(mcMetaPath)) {
			try {
				JsonObject meta = loadMcMeta(mcMetaPath);
				JsonObject textureSection = meta.getAsJsonObject("texture");
				if (textureSection != null) {
					if (textureSection.has("blur")) {
						blur = textureSection.get("blur").getAsBoolean();
					}
					if (textureSection.has("clamp")) {
						clamp = textureSection.get("clamp").getAsBoolean();
					}
				}
			} catch (IOException | JsonParseException exception) {
				Oculus.LOGGER.warn("Failed to parse mcmeta for custom texture {}", mcMetaPath, exception);
			}
		}

		byte[] content = Files.readAllBytes(resolvedPath);
		return new CustomTextureData.PngData(new TextureFilteringData(blur, clamp), content);
	}

	private static JsonObject loadMcMeta(Path mcMetaPath) throws IOException, JsonParseException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(mcMetaPath), StandardCharsets.UTF_8))) {
			JsonReader jsonReader = new JsonReader(reader);
			return GSON.getAdapter(JsonObject.class).read(jsonReader);
		}
	}

	private static final class CustomTextureBundle {
		private final EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap;
		private final CustomTextureData customNoiseTexture;

		private CustomTextureBundle(EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap,
									CustomTextureData customNoiseTexture) {
			this.customTextureDataMap = customTextureDataMap;
			this.customNoiseTexture = customNoiseTexture;
		}

		private static CustomTextureBundle empty() {
			return new CustomTextureBundle(new EnumMap<>(TextureStage.class), null);
		}
	}
}


