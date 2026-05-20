package net.oculus.uniforms;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.oculus.gl.state.FanOutValueUpdateNotifier;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.pipeline.BlockRenderingSettings;
import net.oculus.pipeline.PipelineManager;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.materialmap.NamespacedId;

/**
 * OptiFine/Iris ID-map backed uniforms translated onto the 1.12.2 registries.
 */
public final class IdMapUniforms {
    private static final Map<String, String> LEGACY_ENTITY_ALIASES = createLegacyEntityAliases();
    private static final Map<String, String> LEGACY_ITEM_ALIASES = createLegacyItemAliases();
    private static final NamespacedId PLAYER_ID = new NamespacedId("minecraft", "player");
    private static final FanOutValueUpdateNotifier CURRENT_RENDERED_ITEM_ID_NOTIFIER =
        new FanOutValueUpdateNotifier();

    private static ItemStack currentRenderedItemStack = ItemStack.EMPTY;

    private IdMapUniforms() {
    }

    public static int getHeldItemIdMain() {
        return getHeldItemIdMain(getActiveItemIdMap());
    }

    public static int getHeldItemIdMain(Object2IntFunction<NamespacedId> itemIdMap) {
        EntityPlayerSP player = getPlayer();
        return player != null
            ? resolveItemId(player.getHeldItemMainhand(), true, itemIdMap != null ? itemIdMap : getActiveItemIdMap())
            : -1;
    }

    public static int getHeldItemIdOff() {
        return getHeldItemIdOff(getActiveItemIdMap());
    }

    public static int getHeldItemIdOff(Object2IntFunction<NamespacedId> itemIdMap) {
        EntityPlayerSP player = getPlayer();
        return player != null
            ? resolveItemId(player.getHeldItemOffhand(), true, itemIdMap != null ? itemIdMap : getActiveItemIdMap())
            : -1;
    }

    public static int getHeldBlockLightValueMain() {
        return getHeldBlockLightValueMain(isOldHandLight());
    }

    public static int getHeldBlockLightValueMain(boolean oldHandLight) {
        EntityPlayerSP player = getPlayer();
        if (player == null) {
            return 0;
        }

        return resolveHeldBlockLightValueMain(player.getHeldItemMainhand(), player.getHeldItemOffhand(), oldHandLight);
    }

    public static int getHeldBlockLightValueOff() {
        EntityPlayerSP player = getPlayer();
        return player != null ? resolveItemLight(player.getHeldItemOffhand()) : 0;
    }

    public static int getCurrentRenderedItemId() {
        return getCurrentRenderedItemId(getActiveItemIdMap());
    }

    public static int getCurrentRenderedItemId(Object2IntFunction<NamespacedId> itemIdMap) {
        return resolveItemId(currentRenderedItemStack, false, itemIdMap != null ? itemIdMap : getActiveItemIdMap());
    }

    public static void setCurrentRenderedItem(ItemStack stack) {
        ItemStack next = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (sameResolvedItem(currentRenderedItemStack, next)) {
            return;
        }

        currentRenderedItemStack = next;
        CURRENT_RENDERED_ITEM_ID_NOTIFIER.notifyListeners();
    }

    public static void clearCurrentRenderedItem() {
        setCurrentRenderedItem(ItemStack.EMPTY);
    }

    public static ValueUpdateNotifier getCurrentRenderedItemIdNotifier() {
        return CURRENT_RENDERED_ITEM_ID_NOTIFIER;
    }

    public static int resolveEntityId(Entity entity) {
        if (entity == null) {
            return -1;
        }

        Object2IntFunction<NamespacedId> entityIdMap = BlockRenderingSettings.INSTANCE.getEntityIds();
        if (entityIdMap == null) {
            return -1;
        }

        if (entity instanceof EntityPlayer) {
            return entityIdMap.getInt(PLAYER_ID);
        }

        ResourceLocation location = resolveEntityLocation(entity);
        if (location == null) {
            return -1;
        }

        return resolveEntityMappedId(entityIdMap, location);
    }

    public static int resolveBlockEntityId(TileEntity tileEntity) {
        Object2IntMap<IBlockState> blockStateIds = BlockRenderingSettings.INSTANCE.getBlockStateIds();
        if (blockStateIds == null) {
            return -1;
        }

        IBlockState state = resolveBlockEntityState(tileEntity);
        if (state == null) {
            return -1;
        }

        return blockStateIds.getOrDefault(state, -1);
    }

    private static int resolveItemId(ItemStack stack, boolean mapEmptyToAir) {
        return resolveItemId(stack, mapEmptyToAir, getActiveItemIdMap());
    }

    static int resolveItemId(ItemStack stack, boolean mapEmptyToAir,
            Object2IntFunction<NamespacedId> itemIdMap) {
        Item item = resolveItem(stack, mapEmptyToAir);
        if (item == null) {
            return -1;
        }

        ResourceLocation location = Item.REGISTRY.getNameForObject(item);
        return location != null ? resolveItemMappedId(itemIdMap, location) : -1;
    }

    private static Item resolveItem(ItemStack stack, boolean mapEmptyToAir) {
        if (stack == null || stack.isEmpty()) {
            return mapEmptyToAir ? Items.AIR : null;
        }

        Item item = stack.getItem();
        return item != null ? item : (mapEmptyToAir ? Items.AIR : null);
    }

    private static int resolveItemLight(ItemStack stack) {
        Item item = resolveItem(stack, false);
        if (item == null) {
            return 0;
        }

        Block block = Block.getBlockFromItem(item);
        if (block == null || block == Blocks.AIR) {
            return 0;
        }

        IBlockState state = block.getDefaultState();
        try {
            state = block.getStateFromMeta(stack.getMetadata());
        } catch (RuntimeException ignored) {
            // Some modded blocks reject item damage values; their default state is the closest safe equivalent.
        }

        return Math.max(0, block.getLightValue(state));
    }

    static int resolveHeldBlockLightValueMain(ItemStack mainHandStack, ItemStack offHandStack, boolean oldHandLight) {
        int light = resolveItemLight(mainHandStack);
        return oldHandLight ? Math.max(light, resolveItemLight(offHandStack)) : light;
    }

    private static IBlockState resolveBlockEntityState(TileEntity tileEntity) {
        if (tileEntity == null) {
            return null;
        }

        World world = tileEntity.getWorld();
        BlockPos pos = tileEntity.getPos();
        if (world != null && pos != null) {
            try {
                IBlockState state = world.getBlockState(pos);
                if (state != null) {
                    return state;
                }
            } catch (RuntimeException ignored) {
                // Fall back to the tile entity's cached block below.
            }
        }

        Block block = tileEntity.getBlockType();
        if (block == null || block == Blocks.AIR) {
            return null;
        }

        try {
            return block.getStateFromMeta(tileEntity.getBlockMetadata());
        } catch (RuntimeException ignored) {
            return block.getDefaultState();
        }
    }

    static int resolveMappedId(Object2IntFunction<NamespacedId> map, ResourceLocation location) {
        return map == null || location == null
            ? -1
            : map.getInt(new NamespacedId(location.getNamespace(), location.getPath()));
    }

    static int resolveEntityMappedId(Object2IntFunction<NamespacedId> map, ResourceLocation location) {
        return resolveAliasedMappedId(map, location, LEGACY_ENTITY_ALIASES);
    }

    static ResourceLocation resolveEntityLocation(Entity entity) {
        ResourceLocation location = EntityList.getKey(entity);
        if (location != null) {
            return location;
        }

        return fallbackEntityLocation(entity.getClass());
    }

    static ResourceLocation fallbackEntityLocation(Class<? extends Entity> entityClass) {
        if (EntityLightningBolt.class.isAssignableFrom(entityClass)) {
            return EntityList.LIGHTNING_BOLT;
        }

        return null;
    }

    static int resolveItemMappedId(Object2IntFunction<NamespacedId> map, ResourceLocation location) {
        return resolveAliasedMappedId(map, location, LEGACY_ITEM_ALIASES);
    }

    private static int resolveAliasedMappedId(Object2IntFunction<NamespacedId> map, ResourceLocation location,
                                              Map<String, String> aliases) {
        int id = resolveMappedId(map, location);
        if (id != -1 || map == null || location == null) {
            return id;
        }

        String modernName = aliases.get(location.getPath());
        return modernName != null
            ? map.getInt(new NamespacedId(location.getNamespace(), modernName))
            : -1;
    }

    private static boolean isOldHandLight() {
        return getActivePack().getProperties().getOldHandLight().orElse(true);
    }

    private static ShaderPack getActivePack() {
        return PipelineManager.INSTANCE.getActivePack();
    }

    private static Object2IntFunction<NamespacedId> getActiveItemIdMap() {
        return getActivePack().getIdMap().getItemIdMap();
    }

    private static EntityPlayerSP getPlayer() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null ? minecraft.player : null;
    }

    private static boolean sameResolvedItem(ItemStack first, ItemStack second) {
        return resolveItem(first, false) == resolveItem(second, false);
    }

    private static Map<String, String> createLegacyEntityAliases() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("xp_orb", "experience_orb");
        aliases.put("ender_crystal", "end_crystal");
        aliases.put("villager_golem", "iron_golem");
        aliases.put("snowman", "snow_golem");
        aliases.put("commandblock_minecart", "command_block_minecart");
        aliases.put("fireworks_rocket", "firework_rocket");
        aliases.put("evocation_fangs", "evoker_fangs");
        aliases.put("evocation_illager", "evoker");
        aliases.put("vindication_illager", "vindicator");
        aliases.put("illusion_illager", "illusioner");
        aliases.put("zombie_pigman", "zombified_piglin");
        return Collections.unmodifiableMap(aliases);
    }

    private static Map<String, String> createLegacyItemAliases() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("lit_pumpkin", "jack_o_lantern");
        aliases.put("magma", "magma_block");
        return Collections.unmodifiableMap(aliases);
    }
}
