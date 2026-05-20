package net.oculus.shaderpack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
import net.oculus.gl.shader.ShaderType;
import net.oculus.gl.shader.StandardMacros;
import net.oculus.pipeline.NamespacedId;
import net.oculus.shader.IrisFeatureDefines;
import net.oculus.shader.ShaderPreprocessor;
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
import net.oculus.shaderpack.preprocessor.JcppProcessor;
import net.oculus.shaderpack.preprocessor.PropertiesPreprocessor;
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
		return load(packName, Collections.emptyMap());
	}

	public static ShaderPack load(String packName, Map<String, String> changedConfigs) throws IOException {
		if (packName == null || packName.trim().isEmpty()) {
			return ShaderPack.internal();
		}

		Minecraft minecraft = Minecraft.getMinecraft();
		if (minecraft == null || minecraft.gameDir == null) {
			throw new IOException("Minecraft game directory is not available");
		}

		return loadFromShaderpacksDirectory(minecraft.gameDir.toPath().resolve("shaderpacks"), packName, changedConfigs);
	}

	public static ShaderPack loadFromShaderpacksDirectory(Path shaderpacksDir, String packName) throws IOException {
		return loadFromShaderpacksDirectory(shaderpacksDir, packName, Collections.emptyMap());
	}

	public static ShaderPack loadFromShaderpacksDirectory(Path shaderpacksDir, String packName,
														 Map<String, String> changedConfigs) throws IOException {
		if (packName == null || packName.trim().isEmpty()) {
			return ShaderPack.internal();
		}

		if (shaderpacksDir == null) {
			throw new IOException("Shaderpacks directory is not available");
		}

		return loadFromPath(packName, shaderpacksDir.resolve(packName), changedConfigs);
	}

	private static ShaderPack loadFromPath(String packName, Path packPath,
										   Map<String, String> changedConfigs) throws IOException {
		Map<String, String> optionOverrides = changedConfigs == null
			? Collections.emptyMap()
			: new HashMap<>(changedConfigs);

		if (!Files.exists(packPath)) {
			throw new IOException("Shader pack " + packName + " does not exist");
		}

		ensureShaderRootAvailable(packName, packPath);

		Iterable<StringPair> environmentDefines = StandardMacros.createStandardEnvironmentDefines();
		ShaderSourceData shaderData = buildShaderSources(packPath, environmentDefines, optionOverrides);
		MutableOptionValues optionValues = shaderData.shaderPackOptions.getOptionValues().mutableCopy();
		ShaderProperties properties = readShaderProperties(packPath, shaderData.shaderPackOptions, shaderData.environmentDefines);
		ProfileSet profiles = ProfileSet.fromMap(properties.getProfiles(), shaderData.shaderPackOptions.getOptionSet());
		Set<String> disabledPrograms = resolveDisabledPrograms(properties, profiles, optionValues);
		OptionMenuContainer menuContainer = new OptionMenuContainer(properties, shaderData.shaderPackOptions, profiles);
		CustomTextureBundle textureBundle = loadCustomTextures(packPath, properties);
		IdMap idMap = loadIdMap(packPath, shaderData.shaderPackOptions, shaderData.environmentDefines);
		Object2IntMap<IBlockState> blockStateIds = BlockMaterialMapping.createBlockStateIdMap(idMap.getBlockPropertiesMap());

		return ShaderPack.of(
			packName,
			menuContainer,
			properties,
			optionValues,
			shaderData.languageMap,
			shaderData.shaderPackSource.programRoot,
			shaderData.shaderPackSource.sourceProvider,
			textureBundle.customTextureDataMap,
			textureBundle.customNoiseTexture,
			idMap,
			blockStateIds,
			profiles,
			shaderData.dimensionMap,
			shaderData.dimensionIds,
			disabledPrograms
		);
	}

	private static void ensureShaderRootAvailable(String packName, Path packPath) throws IOException {
		if (Files.isDirectory(packPath)) {
			if (resolveDirectoryShaderRoot(packPath) == null) {
				throw new IOException("Shader pack " + packName + " does not contain a shaders directory");
			}
			return;
		}

		if (Files.isRegularFile(packPath)) {
			try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
				if (resolveZipShaderRoot(zipFs) == null) {
					throw new IOException("Shader pack " + packName + " does not contain a shaders directory");
				}
			}
		}
	}

	public static ShaderPack internalPack() {
		return ShaderPack.internal();
	}

	private static Set<String> resolveDisabledPrograms(ShaderProperties properties,
													   ProfileSet profiles,
													   MutableOptionValues optionValues) {
		Set<String> disabled = new HashSet<>();

		if (profiles != null) {
			ProfileSet.ProfileResult profile = profiles.scan(optionValues.getOptionSet(), optionValues);
			profile.current.ifPresent(current -> disabled.addAll(current.disabledPrograms));
		}

		if (properties != null) {
			properties.getConditionallyEnabledPrograms().forEach((program, condition) -> {
				if (!ProgramConditionEvaluator.isEnabled(condition, optionValues)) {
					disabled.add(program);
				}
			});
		}

		return disabled;
	}

	private static ShaderSourceData buildShaderSources(Path packPath, Iterable<StringPair> environmentDefines,
													   Map<String, String> changedConfigs) {
		try {
			if (Files.isDirectory(packPath)) {
				Path shaderRoot = resolveDirectoryShaderRoot(packPath);
				if (shaderRoot != null) {
					return createShaderSourceData(shaderRoot, environmentDefines, changedConfigs);
				}
				return ShaderSourceData.empty(packPath, changedConfigs);
			}

			if (Files.isRegularFile(packPath)) {
				try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
					Path shaderRoot = resolveZipShaderRoot(zipFs);
					if (shaderRoot != null) {
						return createShaderSourceData(shaderRoot, environmentDefines, changedConfigs);
					}
					return ShaderSourceData.empty(packPath, changedConfigs);
				}
			}
		} catch (IOException | IllegalStateException exception) {
			Oculus.LOGGER.warn("Failed to prepare shader sources for {}", packPath, exception);
		}

		return ShaderSourceData.empty(packPath, changedConfigs);
	}

	private static ShaderSourceData createShaderSourceData(Path shaderRoot, Iterable<StringPair> environmentDefines,
														  Map<String, String> changedConfigs) throws IOException {
		DimensionInfo dimensionInfo = loadDimensionInfo(shaderRoot, environmentDefines);
		ImmutableList<AbsolutePackPath> shaderEntries = collectShaderEntries(shaderRoot, dimensionInfo.dimensionIds);
		IncludeGraph graph = new IncludeGraph(shaderRoot, shaderEntries);
		ShaderPackOptions shaderPackOptions = new ShaderPackOptions(graph, changedConfigs);
		ShaderProperties earlyProperties = readShaderPropertiesFromRoot(shaderRoot, shaderPackOptions, environmentDefines);
		Iterable<StringPair> sourceEnvironmentDefines =
			IrisFeatureDefines.extendEnvironmentDefines(environmentDefines, earlyProperties);
		ShaderPackSource source = createSourceBundle(shaderRoot, shaderPackOptions.getIncludes(), sourceEnvironmentDefines);
		AbsolutePackPath programRoot = resolveBaseProgramRoot(dimensionInfo.dimensionMap);
		LanguageMap languageMap = new LanguageMap(shaderRoot.resolve("lang"));
		return new ShaderSourceData(source.withProgramRoot(programRoot), shaderPackOptions, languageMap, dimensionInfo.dimensionMap, dimensionInfo.dimensionIds,
			sourceEnvironmentDefines);
	}

	private static Path resolveDirectoryShaderRoot(Path packPath) {
		if (Files.isDirectory(packPath)) {
			Path shadersFolder = packPath.resolve("shaders");
			if (Files.isDirectory(shadersFolder)) {
				return shadersFolder;
			}
		}
		return null;
	}

	private static Path resolveZipShaderRoot(FileSystem zipFs) throws IOException {
		Path shadersFolder = zipFs.getPath("/shaders");
		if (Files.isDirectory(shadersFolder)) {
			return shadersFolder;
		}

		Path root = zipFs.getRootDirectories().iterator().next();
		try (Stream<Path> stream = Files.walk(root)) {
			return stream
				.filter(Files::isDirectory)
				.map(path -> path.resolve("shaders"))
				.filter(Files::isDirectory)
				.findFirst()
				.orElse(null);
		}
	}

	private static ShaderPackSource createSourceBundle(Path shaderRoot,
													   IncludeGraph graph,
													   Iterable<StringPair> environmentDefines) throws IOException {
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
				return JcppProcessor.glslPreprocessSource(builder.toString(),
					createSourcePreprocessorDefines(key, environmentDefines));
			});
		};

		return new ShaderPackSource(AbsolutePackPath.fromAbsolutePath("/"), provider);
	}

	private static Iterable<StringPair> createSourcePreprocessorDefines(
			AbsolutePackPath path,
			Iterable<StringPair> environmentDefines) {
		List<StringPair> defines = new ArrayList<>();
		boolean suppressIrisEntityMaterialPath = suppressIrisEntityMaterialPath(path);
		if (environmentDefines != null) {
			for (StringPair define : environmentDefines) {
				if (suppressIrisEntityMaterialPath && "IS_IRIS".equals(define.getKey())) {
					continue;
				}
				defines.add(define);
			}
		}

		defines.addAll(ShaderPreprocessor.createStageDefines(shaderTypeForPath(path)));
		defines.addAll(ShaderPreprocessor.createProgramDefines(programNameForPath(path)));
		return defines;
	}

	private static boolean suppressIrisEntityMaterialPath(AbsolutePackPath path) {
		if (shaderTypeForPath(path) != ShaderType.FRAGMENT) {
			return false;
		}

		String programName = programNameForPath(path);
		return programName.startsWith("gbuffers_entities")
			|| "gbuffers_hand".equals(programName)
			|| "gbuffers_hand_water".equals(programName);
	}

	private static ShaderType shaderTypeForPath(AbsolutePackPath path) {
		String pathString = path == null ? "" : path.getPathString();
		if (pathString.endsWith(".vsh")) {
			return ShaderType.VERTEX;
		}
		if (pathString.endsWith(".gsh")) {
			return ShaderType.GEOMETRY;
		}
		if (pathString.endsWith(".fsh")) {
			return ShaderType.FRAGMENT;
		}
		if (pathString.endsWith(".csh")) {
			return ShaderType.COMPUTE;
		}
		return null;
	}

	private static String programNameForPath(AbsolutePackPath path) {
		String pathString = path == null ? "" : path.getPathString();
		int slash = pathString.lastIndexOf('/');
		int dot = pathString.lastIndexOf('.');
		int start = slash >= 0 ? slash + 1 : 0;
		int end = dot > start ? dot : pathString.length();
		return pathString.substring(start, end);
	}

	private static ImmutableList<AbsolutePackPath> collectShaderEntries(Path shaderRoot, Set<String> dimensionIds) throws IOException {
		ImmutableList.Builder<AbsolutePackPath> builder = ImmutableList.builder();

		ShaderPackSourceNames.findPresentSources(
			builder,
			shaderRoot,
			AbsolutePackPath.fromAbsolutePath("/"),
			ShaderPackSourceNames.POTENTIAL_STARTS
		);

		if (dimensionIds != null) {
			for (String dimensionId : dimensionIds) {
				ShaderPackSourceNames.findPresentSources(
					builder,
					shaderRoot,
					AbsolutePackPath.fromAbsolutePath("/" + dimensionId),
					ShaderPackSourceNames.POTENTIAL_STARTS
				);
			}
		}

		return builder.build();
	}

	private static AbsolutePackPath resolveBaseProgramRoot(Map<NamespacedId, String> dimensionMap) {
		if (dimensionMap == null) {
			return AbsolutePackPath.fromAbsolutePath("/");
		}

		String wildcardFolder = dimensionMap.get(NamespacedId.wildcard());
		if (wildcardFolder == null || wildcardFolder.isEmpty()) {
			return AbsolutePackPath.fromAbsolutePath("/");
		}

		return AbsolutePackPath.fromAbsolutePath("/" + wildcardFolder);
	}

	private static DimensionInfo loadDimensionInfo(Path shaderRoot,
												   Iterable<StringPair> environmentDefines) throws IOException {
		Map<NamespacedId, String> dimensionMap = new HashMap<>();
		Set<String> dimensionIds = new LinkedHashSet<>();

		Optional<Properties> dimensionProperties =
			readPreprocessedProperties(shaderRoot, "dimension.properties", null, environmentDefines);
		if (dimensionProperties.isPresent() && !dimensionProperties.get().isEmpty()) {
			Set<String> declaredDimensionIds = new LinkedHashSet<>();
			dimensionProperties.get().forEach((keyObject, valueObject) -> {
				String key = (String) keyObject;
				String value = (String) valueObject;
				if (!key.startsWith("dimension.")) {
					return;
				}

				String folder = key.substring("dimension.".length());
				if (folder.isEmpty()) {
					return;
				}

				declaredDimensionIds.add(folder);
				for (String part : value.split("\\s+")) {
					if ("*".equals(part)) {
						dimensionMap.put(NamespacedId.wildcard(), folder);
					}
					dimensionMap.put(new NamespacedId(part), folder);
				}
			});

			for (String folder : declaredDimensionIds) {
				registerDimensionFolderIfUsable(shaderRoot, dimensionIds, folder);
			}
			return new DimensionInfo(dimensionMap, dimensionIds);
		}

		registerImplicitDimension(shaderRoot, dimensionMap, dimensionIds, "world0", NamespacedId.overworld());
		registerImplicitDimension(shaderRoot, dimensionMap, dimensionIds, "world-1", NamespacedId.nether());
		registerImplicitDimension(shaderRoot, dimensionMap, dimensionIds, "world1", NamespacedId.end());

		return new DimensionInfo(dimensionMap, dimensionIds);
	}

	private static void registerImplicitDimension(Path shaderRoot,
												  Map<NamespacedId, String> dimensionMap,
												  Set<String> dimensionIds,
												  String folder,
												  NamespacedId dimension) throws IOException {
		if (Files.isDirectory(shaderRoot.resolve(folder))) {
			dimensionMap.put(dimension, folder);
			if ("world0".equals(folder)) {
				dimensionMap.put(NamespacedId.wildcard(), folder);
			}
			registerDimensionFolderIfUsable(shaderRoot, dimensionIds, folder);
		}
	}

	private static void registerDimensionFolderIfUsable(Path shaderRoot,
														Set<String> dimensionIds,
														String folder) throws IOException {
		if (folder == null || folder.isEmpty()) {
			return;
		}

		ImmutableList.Builder<AbsolutePackPath> starts = ImmutableList.builder();
		boolean hasPresentSources = ShaderPackSourceNames.findPresentSources(
			starts,
			shaderRoot,
			AbsolutePackPath.fromAbsolutePath("/" + folder),
			ShaderPackSourceNames.POTENTIAL_STARTS
		);
		if (hasPresentSources) {
			dimensionIds.add(folder);
		}
	}

	private static Optional<Properties> readPreprocessedProperties(Path shaderRoot, String fileName,
																  ShaderPackOptions shaderPackOptions,
																  Iterable<StringPair> environmentDefines) {
		Path path = shaderRoot.resolve(fileName);
		if (!Files.exists(path)) {
			return Optional.empty();
		}

		try {
			String contents = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
			String processed = shaderPackOptions != null
				? PropertiesPreprocessor.preprocessSource(contents, shaderPackOptions, environmentDefines)
				: PropertiesPreprocessor.preprocessSource(contents, environmentDefines);
			Properties properties = new OrderBackedProperties();
			properties.load(new StringReader(processed));
			return Optional.of(properties);
		} catch (IOException | RuntimeException exception) {
			Oculus.LOGGER.warn("Failed to read {} from shader pack {}", fileName, shaderRoot, exception);
			return Optional.empty();
		}
	}

	private static ShaderProperties readShaderProperties(Path packPath,
														 ShaderPackOptions shaderPackOptions,
														 Iterable<StringPair> environmentDefines) {
		if (Files.isDirectory(packPath)) {
			Path propertiesPath = resolveDirectoryProperties(packPath);
			if (propertiesPath != null && Files.exists(propertiesPath)) {
				try {
					String contents = new String(Files.readAllBytes(propertiesPath), StandardCharsets.ISO_8859_1);
					String processed = PropertiesPreprocessor.preprocessShaderProperties(contents, shaderPackOptions, environmentDefines);
					return new ShaderProperties(processed, contents, shaderPackOptions);
				} catch (IOException exception) {
					Oculus.LOGGER.warn("Failed to read shaders.properties from directory {}", packPath, exception);
				}
			}
			return ShaderProperties.empty();
		}

		if (Files.isRegularFile(packPath)) {
			try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
				Path shaderRoot = resolveZipShaderRoot(zipFs);
				if (shaderRoot != null) {
					return readShaderPropertiesFromRoot(shaderRoot, shaderPackOptions, environmentDefines);
				}
			} catch (IOException exception) {
				Oculus.LOGGER.warn("Failed to read shaders.properties from archive {}", packPath, exception);
			}
		}

		return ShaderProperties.empty();
	}

	private static ShaderProperties readShaderPropertiesFromRoot(Path shaderRoot,
																 ShaderPackOptions shaderPackOptions,
																 Iterable<StringPair> environmentDefines) {
		if (shaderRoot == null) {
			return ShaderProperties.empty();
		}

		Path propertiesPath = shaderRoot.resolve("shaders.properties");
		if (!Files.exists(propertiesPath)) {
			return ShaderProperties.empty();
		}

		try {
			String contents = new String(Files.readAllBytes(propertiesPath), StandardCharsets.ISO_8859_1);
			String processed = PropertiesPreprocessor.preprocessShaderProperties(contents, shaderPackOptions, environmentDefines);
			return new ShaderProperties(processed, contents, shaderPackOptions);
		} catch (IOException exception) {
			Oculus.LOGGER.warn("Failed to read shaders.properties from {}", shaderRoot, exception);
			return ShaderProperties.empty();
		}
	}

	private static Path resolveDirectoryProperties(Path packPath) {
		Path shadersFolder = packPath.resolve("shaders");
		Path withinShaders = shadersFolder.resolve("shaders.properties");
		if (Files.exists(withinShaders)) {
			return withinShaders;
		}
		return null;
	}

	private static final class ShaderSourceData {
		private final ShaderPackSource shaderPackSource;
		private final ShaderPackOptions shaderPackOptions;
		private final LanguageMap languageMap;
		private final Map<NamespacedId, String> dimensionMap;
		private final Set<String> dimensionIds;
		private final Iterable<StringPair> environmentDefines;

		private ShaderSourceData(ShaderPackSource shaderPackSource,
								 ShaderPackOptions shaderPackOptions,
								 LanguageMap languageMap,
								 Map<NamespacedId, String> dimensionMap,
								 Set<String> dimensionIds,
								 Iterable<StringPair> environmentDefines) {
			this.shaderPackSource = shaderPackSource;
			this.shaderPackOptions = shaderPackOptions;
			this.languageMap = languageMap == null ? LanguageMap.empty() : languageMap;
			this.dimensionMap = dimensionMap;
			this.dimensionIds = dimensionIds;
			this.environmentDefines = environmentDefines;
		}

		private static ShaderSourceData empty(Path packPath, Map<String, String> changedConfigs) {
			IncludeGraph graph = new IncludeGraph(packPath, ImmutableList.of());
			ShaderPackOptions shaderPackOptions = new ShaderPackOptions(graph,
				changedConfigs == null ? Collections.emptyMap() : changedConfigs);
			return new ShaderSourceData(ShaderPackSource.empty(), shaderPackOptions,
				LanguageMap.empty(), Collections.emptyMap(), Collections.emptySet(), StandardMacros.createStandardEnvironmentDefines());
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

		private ShaderPackSource withProgramRoot(AbsolutePackPath programRoot) {
			return new ShaderPackSource(programRoot, sourceProvider);
		}
	}

	private static final class DimensionInfo {
		private final Map<NamespacedId, String> dimensionMap;
		private final Set<String> dimensionIds;

		private DimensionInfo(Map<NamespacedId, String> dimensionMap, Set<String> dimensionIds) {
			this.dimensionMap = dimensionMap;
			this.dimensionIds = dimensionIds;
		}
	}

	private static IdMap loadIdMap(Path packPath, ShaderPackOptions shaderPackOptions,
								   Iterable<StringPair> environmentDefines) {
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
			if (shaderRoot != null) {
				return readCustomTexturesFromRoot(shaderRoot, properties);
			}
		}

		if (Files.isRegularFile(packPath)) {
			try (FileSystem zipFs = FileSystems.newFileSystem(packPath, (ClassLoader) null)) {
				Path shaderRoot = resolveZipShaderRoot(zipFs);
				if (shaderRoot != null) {
					return readCustomTexturesFromRoot(shaderRoot, properties);
				}
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
		Map<String, CustomTextureData> textureReadCache = new HashMap<>();
		properties.getCustomTextures().forEach((stage, samplerMap) -> {
			Map<String, CustomTextureData> resolved = new HashMap<>();
			samplerMap.forEach((samplerName, path) -> {
				CustomTextureData data = textureReadCache.computeIfAbsent(
					path,
					texturePath -> readTextureSafely(shaderRoot, texturePath, samplerName));
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
