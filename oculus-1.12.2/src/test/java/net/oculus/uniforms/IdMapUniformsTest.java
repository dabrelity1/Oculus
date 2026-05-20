package net.oculus.uniforms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.oculus.shaderpack.materialmap.NamespacedId;
import org.junit.Test;

public class IdMapUniformsTest {
    @Test
    public void itemLookupUsesExactRegistryNameFirst() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "lit_pumpkin"), 10);
        map.put(new NamespacedId("minecraft", "jack_o_lantern"), 20);

        assertEquals(10, IdMapUniforms.resolveItemMappedId(map, new ResourceLocation("minecraft", "lit_pumpkin")));
    }

    @Test
    public void itemLookupFallsBackToModernNameForLegacyPumpkin() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "jack_o_lantern"), 44011);

        assertEquals(44011, IdMapUniforms.resolveItemMappedId(map, new ResourceLocation("minecraft", "lit_pumpkin")));
    }

    @Test
    public void itemLookupFallsBackToModernNameForLegacyMagmaBlock() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "magma_block"), 44037);

        assertEquals(44037, IdMapUniforms.resolveItemMappedId(map, new ResourceLocation("minecraft", "magma")));
    }

    @Test
    public void itemLookupUsesExactTargetPackNamesWithoutAliases() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "filled_map"), 40004);
        map.put(new NamespacedId("minecraft", "sea_lantern"), 44018);
        map.put(new NamespacedId("minecraft", "experience_bottle"), 45044);
        map.put(new NamespacedId("minecraft", "end_crystal"), 45092);

        assertEquals(40004, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "filled_map")));
        assertEquals(44018, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "sea_lantern")));
        assertEquals(45044, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "experience_bottle")));
        assertEquals(45092, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "end_crystal")));
    }

    @Test
    public void itemLookupDoesNotAliasModernOnlyItemsToDifferent112Items() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "lantern"), 44012);
        map.put(new NamespacedId("minecraft", "campfire"), 44015);
        map.put(new NamespacedId("minecraft", "shroomlight"), 44019);
        map.put(new NamespacedId("minecraft", "crying_obsidian"), 44026);
        map.put(new NamespacedId("minecraft", "enchanted_golden_apple"), 45016);

        assertEquals(-1, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "torch")));
        assertEquals(-1, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "fire")));
        assertEquals(-1, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "glowstone")));
        assertEquals(-1, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "obsidian")));
        assertEquals(-1, IdMapUniforms.resolveItemMappedId(map,
            new ResourceLocation("minecraft", "golden_apple")));
    }

    @Test
    public void itemLookupHandlesMissingMapOrLocationAsUnmapped() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);

        assertEquals(-1, IdMapUniforms.resolveItemMappedId(null,
            new ResourceLocation("minecraft", "torch")));
        assertEquals(-1, IdMapUniforms.resolveItemMappedId(map, null));
    }

    @Test
    public void heldItemLookupCanUseCapturedItemMap() {
        Bootstrap.register();
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "torch"), 77);
        map.put(new NamespacedId("minecraft", "air"), 13);

        assertEquals(77, IdMapUniforms.resolveItemId(new ItemStack(Blocks.TORCH), true, map));
        assertEquals(13, IdMapUniforms.resolveItemId(ItemStack.EMPTY, true, map));
        assertEquals(-1, IdMapUniforms.resolveItemId(ItemStack.EMPTY, false, map));
    }

    @Test
    public void currentRenderedItemLookupUsesProvidedItemMap() {
        Bootstrap.register();
        Object2IntOpenHashMap<NamespacedId> firstMap = new Object2IntOpenHashMap<>();
        firstMap.defaultReturnValue(-1);
        firstMap.put(new NamespacedId("minecraft", "torch"), 77);
        Object2IntOpenHashMap<NamespacedId> secondMap = new Object2IntOpenHashMap<>();
        secondMap.defaultReturnValue(-1);
        secondMap.put(new NamespacedId("minecraft", "torch"), 88);

        IdMapUniforms.clearCurrentRenderedItem();
        IdMapUniforms.setCurrentRenderedItem(new ItemStack(Blocks.TORCH));

        assertEquals(77, IdMapUniforms.getCurrentRenderedItemId(firstMap));
        assertEquals(88, IdMapUniforms.getCurrentRenderedItemId(secondMap));

        IdMapUniforms.clearCurrentRenderedItem();
        assertEquals(-1, IdMapUniforms.getCurrentRenderedItemId(firstMap));
    }

    @Test
    public void genericLookupDoesNotUseItemAliases() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "magma_block"), 44037);

        assertEquals(-1, IdMapUniforms.resolveMappedId(map, new ResourceLocation("minecraft", "magma")));
    }

    @Test
    public void entityLookupUsesExactRegistryNameFirst() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "zombie_pigman"), 101);
        map.put(new NamespacedId("minecraft", "zombified_piglin"), 50104);

        assertEquals(101, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "zombie_pigman")));
    }

    @Test
    public void entityLookupFallsBackToModernNameForLegacyComplementaryAliases() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "end_crystal"), 50000);
        map.put(new NamespacedId("minecraft", "iron_golem"), 50012);
        map.put(new NamespacedId("minecraft", "experience_orb"), 50072);
        map.put(new NamespacedId("minecraft", "command_block_minecart"), 50096);
        map.put(new NamespacedId("minecraft", "evoker_fangs"), 50102);
        map.put(new NamespacedId("minecraft", "zombified_piglin"), 50104);

        assertEquals(50000, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "ender_crystal")));
        assertEquals(50012, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "villager_golem")));
        assertEquals(50072, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "xp_orb")));
        assertEquals(50096, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "commandblock_minecart")));
        assertEquals(50102, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "evocation_fangs")));
        assertEquals(50104, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "zombie_pigman")));
    }

    @Test
    public void entityLookupDoesNotAliasModernOnlyEntitiesToDifferent112Entities() {
        Object2IntOpenHashMap<NamespacedId> map = new Object2IntOpenHashMap<>();
        map.defaultReturnValue(-1);
        map.put(new NamespacedId("minecraft", "glow_squid"), 50048);
        map.put(new NamespacedId("minecraft", "piglin"), 50104);

        assertEquals(-1, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "squid")));
        assertEquals(-1, IdMapUniforms.resolveEntityMappedId(map,
            new ResourceLocation("minecraft", "zombie")));
    }

    @Test
    public void lightningEntityUsesSpecial112ResourceLocation() {
        assertEquals(EntityList.LIGHTNING_BOLT, IdMapUniforms.fallbackEntityLocation(EntityLightningBolt.class));
        assertEquals(null, IdMapUniforms.fallbackEntityLocation(Entity.class));
    }

    @Test
    public void heldBlockLightMainUsesOffhandOnlyWhenOldHandLightIsEnabled() {
        Bootstrap.register();

        ItemStack emptyMain = ItemStack.EMPTY;
        ItemStack torchOffhand = new ItemStack(Blocks.TORCH);

        assertEquals(0, IdMapUniforms.resolveHeldBlockLightValueMain(emptyMain, torchOffhand, false));
        assertEquals(14, IdMapUniforms.resolveHeldBlockLightValueMain(emptyMain, torchOffhand, true));

        ItemStack glowstoneMain = new ItemStack(Blocks.GLOWSTONE);
        assertEquals(15, IdMapUniforms.resolveHeldBlockLightValueMain(glowstoneMain, torchOffhand, false));
        assertEquals(15, IdMapUniforms.resolveHeldBlockLightValueMain(glowstoneMain, torchOffhand, true));
    }

    @Test
    public void entityLookupUsesActiveBlockRenderingSettings() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/uniforms/IdMapUniforms.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.getEntityIds()"));
        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.getBlockStateIds()"));
        assertFalse(source.contains("getActivePack().getIdMap().getEntityIdMap()"));
        assertFalse(source.contains("getActivePack().getBlockStateIdMap()"));
    }

    @Test
    public void currentRenderedItemNotifierFansOutToAllDynamicUniformBindings() throws Exception {
        Bootstrap.register();

        IdMapUniforms.getCurrentRenderedItemIdNotifier().setListener(null);
        IdMapUniforms.clearCurrentRenderedItem();

        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        IdMapUniforms.getCurrentRenderedItemIdNotifier().setListener(first::incrementAndGet);
        IdMapUniforms.getCurrentRenderedItemIdNotifier().setListener(second::incrementAndGet);
        IdMapUniforms.setCurrentRenderedItem(new ItemStack(Blocks.TORCH));

        assertEquals(1, first.get());
        assertEquals(1, second.get());

        IdMapUniforms.getCurrentRenderedItemIdNotifier().setListener(null);
        IdMapUniforms.clearCurrentRenderedItem();
    }
}
