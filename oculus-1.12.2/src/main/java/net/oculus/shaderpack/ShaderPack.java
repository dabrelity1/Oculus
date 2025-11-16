package net.oculus.shaderpack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.block.state.IBlockState;
import net.oculus.shaderpack.include.AbsolutePackPath;
import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.menu.OptionMenuContainer;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.texture.CustomTextureData;
import net.oculus.shaderpack.texture.TextureStage;

/**
 * Lightweight placeholder for shader pack metadata. The 1.12.2 port only
 * tracks a human-readable name, deferring option parsing to later steps.
 */
public final class ShaderPack {
    private static final String INTERNAL_NAME = "(internal)";

    private final String name;
    private final OptionMenuContainer menuContainer;
    private final ShaderProperties properties;
    private final MutableOptionValues optionValues;
    private final AbsolutePackPath programRoot;
    private final Function<AbsolutePackPath, String> sourceProvider;
    private final EnumMap<TextureStage, Map<String, CustomTextureData>> customTextureDataMap;
    private final CustomTextureData customNoiseTexture;
    private final IdMap idMap;
    private final Object2IntMap<IBlockState> blockStateIdMap;
    private final ProfileSet profileSet;

    private ShaderPack(String name,
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
        this.name = name;
        this.menuContainer = menuContainer;
        this.properties = properties == null ? ShaderProperties.empty() : properties;
        this.optionValues = optionValues == null ? createEmptyOptionValues() : optionValues;
        this.programRoot = programRoot != null ? programRoot : AbsolutePackPath.fromAbsolutePath("/");
        this.sourceProvider = sourceProvider != null ? sourceProvider : path -> null;
        this.customTextureDataMap = customTextureDataMap == null ? new EnumMap<>(TextureStage.class) : customTextureDataMap;
        this.customNoiseTexture = customNoiseTexture;
        this.idMap = idMap == null ? IdMap.empty() : idMap;
        this.blockStateIdMap = blockStateIdMap == null ? Object2IntMaps.emptyMap() : blockStateIdMap;
        this.profileSet = profileSet == null ? ProfileSet.empty() : profileSet;
    }

    public static ShaderPack placeholder() {
        return new ShaderPack(
            INTERNAL_NAME,
            OptionMenuContainer.EMPTY,
            ShaderProperties.empty(),
            createEmptyOptionValues(),
            AbsolutePackPath.fromAbsolutePath("/"),
            path -> null,
            new EnumMap<>(TextureStage.class),
            null,
            IdMap.empty(),
            Object2IntMaps.emptyMap(),
            ProfileSet.empty()
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
        return new ShaderPack(name, menuContainer, properties, optionValues, programRoot, sourceProvider,
            customTextureDataMap, customNoiseTexture, idMap, blockStateIdMap, profileSet);
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

    public boolean isInternal() {
        return INTERNAL_NAME.equals(this.name);
    }

    public static MutableOptionValues createEmptyOptionValues() {
        return new MutableOptionValues(OptionSet.builder().build());
    }
}
