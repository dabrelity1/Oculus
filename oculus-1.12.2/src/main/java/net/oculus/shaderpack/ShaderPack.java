package net.oculus.shaderpack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.block.state.IBlockState;
import net.oculus.pipeline.NamespacedId;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.menu.OptionMenuContainer;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.texture.CustomTextureData;
import net.oculus.shaderpack.texture.TextureStage;

public final class ShaderPack {
    private static final String INTERNAL_NAME = "(internal)";
    private static final String BASE_PROGRAM_SET_KEY = "base";

    private final String name;
    private final OptionMenuContainer menuContainer;
    private final ShaderProperties properties;
    private final MutableOptionValues optionValues;
    private final LanguageMap languageMap;
    private final AbsolutePackPath programRoot;
    private final Function<AbsolutePackPath, String> sourceProvider;
    private final EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap;
    private final CustomTextureData customNoiseTexture;
    private final IdMap idMap;
    private final Object2IntMap<IBlockState> blockStateIdMap;
    private final ProfileSet profileSet;
    private final Map<NamespacedId, String> dimensionMap;
    private final Set<String> dimensionIds;
    private final Set<String> disabledPrograms;
    private final Map<String, ProgramSet> programSetCache = new HashMap<>();

    private ShaderPack(String name,
                       OptionMenuContainer menuContainer,
                       ShaderProperties properties,
                       MutableOptionValues optionValues,
                       LanguageMap languageMap,
                       AbsolutePackPath programRoot,
                       Function<AbsolutePackPath, String> sourceProvider,
                       EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap,
                       CustomTextureData customNoiseTexture,
                       IdMap idMap,
                       Object2IntMap<IBlockState> blockStateIdMap,
                       ProfileSet profileSet,
                       Map<NamespacedId, String> dimensionMap,
                       Set<String> dimensionIds,
                       Set<String> disabledPrograms) {
        this.name = name;
        this.menuContainer = menuContainer;
        this.properties = properties == null ? ShaderProperties.empty() : properties;
        this.optionValues = optionValues == null ? createEmptyOptionValues() : optionValues;
        this.languageMap = languageMap == null ? LanguageMap.empty() : languageMap;
        this.programRoot = programRoot != null ? programRoot : AbsolutePackPath.fromAbsolutePath("/");
        this.sourceProvider = sourceProvider != null ? sourceProvider : path -> null;
        this.customTextureDataMap = customTextureDataMap == null ? new EnumMap<>(TextureStage.class) : customTextureDataMap;
        this.customNoiseTexture = customNoiseTexture;
        this.idMap = idMap == null ? IdMap.empty() : idMap;
        this.blockStateIdMap = blockStateIdMap == null ? Object2IntMaps.emptyMap() : blockStateIdMap;
        this.profileSet = profileSet == null ? ProfileSet.empty() : profileSet;
        this.dimensionMap = dimensionMap == null ? Collections.emptyMap() : new HashMap<>(dimensionMap);
        this.dimensionIds = dimensionIds == null ? Collections.emptySet() : new HashSet<>(dimensionIds);
        this.disabledPrograms = disabledPrograms == null ? Collections.emptySet() : new HashSet<>(disabledPrograms);
    }

    public static ShaderPack internal() {
        return new ShaderPack(
            INTERNAL_NAME,
            OptionMenuContainer.EMPTY,
            ShaderProperties.empty(),
            createEmptyOptionValues(),
            LanguageMap.empty(),
            AbsolutePackPath.fromAbsolutePath("/"),
            path -> null,
            new EnumMap<>(TextureStage.class),
            null,
            IdMap.empty(),
            Object2IntMaps.emptyMap(),
            ProfileSet.empty(),
            Collections.emptyMap(),
            Collections.emptySet(),
            Collections.emptySet()
        );
    }

    public static ShaderPack of(String name,
                                OptionMenuContainer menuContainer,
                                ShaderProperties properties,
                                MutableOptionValues optionValues,
                                AbsolutePackPath programRoot,
                                Function<AbsolutePackPath, String> sourceProvider,
                                EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap,
                                CustomTextureData customNoiseTexture,
                                IdMap idMap,
                                Object2IntMap<IBlockState> blockStateIdMap,
                                ProfileSet profileSet) {
        return of(name, menuContainer, properties, optionValues, programRoot, sourceProvider,
            customTextureDataMap, customNoiseTexture, idMap, blockStateIdMap, profileSet,
            Collections.emptyMap(), Collections.emptySet(), Collections.emptySet());
    }

    public static ShaderPack of(String name,
                                OptionMenuContainer menuContainer,
                                ShaderProperties properties,
                                MutableOptionValues optionValues,
                                LanguageMap languageMap,
                                AbsolutePackPath programRoot,
                                Function<AbsolutePackPath, String> sourceProvider,
                                EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap,
                                CustomTextureData customNoiseTexture,
                                IdMap idMap,
                                Object2IntMap<IBlockState> blockStateIdMap,
                                ProfileSet profileSet) {
        return of(name, menuContainer, properties, optionValues, languageMap, programRoot, sourceProvider,
            customTextureDataMap, customNoiseTexture, idMap, blockStateIdMap, profileSet,
            Collections.emptyMap(), Collections.emptySet(), Collections.emptySet());
    }

    public static ShaderPack of(String name,
                                OptionMenuContainer menuContainer,
                                ShaderProperties properties,
                                MutableOptionValues optionValues,
                                AbsolutePackPath programRoot,
                                Function<AbsolutePackPath, String> sourceProvider,
                                EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap,
                                CustomTextureData customNoiseTexture,
                                IdMap idMap,
                                Object2IntMap<IBlockState> blockStateIdMap,
                                ProfileSet profileSet,
                                Map<NamespacedId, String> dimensionMap,
                                Set<String> dimensionIds,
                                Set<String> disabledPrograms) {
        return new ShaderPack(name, menuContainer, properties, optionValues, LanguageMap.empty(), programRoot, sourceProvider,
            customTextureDataMap, customNoiseTexture, idMap, blockStateIdMap, profileSet,
            dimensionMap, dimensionIds, disabledPrograms);
    }

    public static ShaderPack of(String name,
                                OptionMenuContainer menuContainer,
                                ShaderProperties properties,
                                MutableOptionValues optionValues,
                                LanguageMap languageMap,
                                AbsolutePackPath programRoot,
                                Function<AbsolutePackPath, String> sourceProvider,
                                EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap,
                                CustomTextureData customNoiseTexture,
                                IdMap idMap,
                                Object2IntMap<IBlockState> blockStateIdMap,
                                ProfileSet profileSet,
                                Map<NamespacedId, String> dimensionMap,
                                Set<String> dimensionIds,
                                Set<String> disabledPrograms) {
        return new ShaderPack(name, menuContainer, properties, optionValues, languageMap, programRoot, sourceProvider,
            customTextureDataMap, customNoiseTexture, idMap, blockStateIdMap, profileSet,
            dimensionMap, dimensionIds, disabledPrograms);
    }

    public String getName() {
        return name;
    }

    public OptionMenuContainer getMenuContainer() {
        return menuContainer;
    }

    public ShaderProperties getProperties() {
        return properties;
    }

    public MutableOptionValues getOptionValues() {
        return optionValues;
    }

    public LanguageMap getLanguageMap() {
        return languageMap;
    }

    public AbsolutePackPath getProgramRoot() {
        return programRoot;
    }

    public Function<AbsolutePackPath, String> getSourceProvider() {
        return sourceProvider;
    }

    public EnumMap<TextureStage, Map<String, CustomTextureData>> getCustomTextureDataMap() {
        EnumMap<TextureStage, Map<String, CustomTextureData>> copy = new EnumMap<>(TextureStage.class);
        customTextureDataMap.forEach((stage, map) -> copy.put(stage, Collections.unmodifiableMap(map)));
        return copy;
    }

    public Optional<CustomTextureData> getCustomNoiseTexture() {
        return Optional.ofNullable(customNoiseTexture);
    }

    public IdMap getIdMap() {
        return idMap;
    }

    public Object2IntMap<IBlockState> getBlockStateIdMap() {
        return blockStateIdMap;
    }

    public ProfileSet getProfileSet() {
        return profileSet;
    }

    public synchronized ProgramSet getProgramSet(NamespacedId dimension) {
        if (isInternal()) {
            return programSetCache.computeIfAbsent(BASE_PROGRAM_SET_KEY,
                ignored -> new ProgramSet(programRoot, sourceProvider, properties, this));
        }

        NamespacedId requested = dimension == null ? NamespacedId.overworld() : dimension;
        String dimensionFolder = dimensionMap.get(requested);

        if (dimensionFolder != null && dimensionIds.contains(dimensionFolder)) {
            AbsolutePackPath root = AbsolutePackPath.fromAbsolutePath("/" + dimensionFolder);
            return programSetCache.computeIfAbsent(requested.toString(),
                ignored -> new ProgramSet(root, sourceProvider, properties, this));
        }

        return programSetCache.computeIfAbsent(BASE_PROGRAM_SET_KEY,
            ignored -> new ProgramSet(programRoot, sourceProvider, properties, this));
    }

    public boolean isProgramDisabled(String programName) {
        return disabledPrograms.contains(programName);
    }

    public Set<String> getDisabledPrograms() {
        return Collections.unmodifiableSet(disabledPrograms);
    }

    public boolean isInternal() {
        return INTERNAL_NAME.equals(this.name);
    }

    public static MutableOptionValues createEmptyOptionValues() {
        return new MutableOptionValues(OptionSet.builder().build());
    }
}
