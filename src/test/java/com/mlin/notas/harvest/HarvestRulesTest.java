package com.mlin.notas.harvest;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarvestRulesTest {
	@Test
	void harvestsTheSixCropsButNotBerries() {
		for (String crop : List.of("minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots",
				"minecraft:nether_wart", "minecraft:cocoa")) {
			assertTrue(HarvestRules.isHarvestable(crop), crop);
		}
		assertFalse(HarvestRules.isHarvestable("minecraft:sweet_berry_bush"));
		assertFalse(HarvestRules.isHarvestable("minecraft:pumpkin_stem"));
		assertFalse(HarvestRules.isHarvestable("minecraft:stone"));
	}

	@Test
	void seedIsTheBlockItem() {
		assertEquals("minecraft:wheat_seeds", HarvestRules.seedFor("minecraft:wheat"));
		assertEquals("minecraft:carrot", HarvestRules.seedFor("minecraft:carrots"));
		assertEquals("minecraft:potato", HarvestRules.seedFor("minecraft:potatoes"));
		assertEquals("minecraft:beetroot_seeds", HarvestRules.seedFor("minecraft:beetroots"));
		assertEquals("minecraft:nether_wart", HarvestRules.seedFor("minecraft:nether_wart"));
		assertEquals("minecraft:cocoa_beans", HarvestRules.seedFor("minecraft:cocoa"));
		assertNull(HarvestRules.seedFor("minecraft:sweet_berry_bush"));
	}

	@Test
	void matureOnlyAtMaxAge() {
		assertTrue(HarvestRules.isMature(7, 7));
		assertFalse(HarvestRules.isMature(6, 7));
		assertTrue(HarvestRules.isMature(2, 2));
		assertFalse(HarvestRules.isMature(0, 0));
	}

	@Test
	void seedIndexFindsFirstSeedDrop() {
		List<String> drops = List.of("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:wheat_seeds");
		assertEquals(1, HarvestRules.seedIndex(drops, "minecraft:wheat_seeds"));
		assertEquals(-1, HarvestRules.seedIndex(List.of("minecraft:wheat"), "minecraft:wheat_seeds"));
		assertEquals(-1, HarvestRules.seedIndex(drops, null));
	}

	@Test
	void areaGrowsWithHoeTier() {
		assertEquals(1, HarvestRules.side(null));
		assertEquals(1, HarvestRules.side(Tier.WOOD));
		assertEquals(1, HarvestRules.side(Tier.STONE));
		assertEquals(3, HarvestRules.side(Tier.COPPER));
		assertEquals(3, HarvestRules.side(Tier.IRON));
		assertEquals(5, HarvestRules.side(Tier.GOLD));
		assertEquals(5, HarvestRules.side(Tier.COBALT));
		assertEquals(5, HarvestRules.side(Tier.DIAMOND));
		assertEquals(7, HarvestRules.side(Tier.NETHERITE));
		assertEquals(7, HarvestRules.side(Tier.CELESTIUM));
	}

	@Test
	void hoeTierFromItemId() {
		assertEquals(Tier.WOOD, HarvestRules.hoeTier("minecraft:wooden_hoe"));
		assertEquals(Tier.GOLD, HarvestRules.hoeTier("minecraft:golden_hoe"));
		assertEquals(Tier.COPPER, HarvestRules.hoeTier("minecraft:copper_hoe"));
		assertEquals(Tier.NETHERITE, HarvestRules.hoeTier("minecraft:netherite_hoe"));
		assertEquals(Tier.COBALT, HarvestRules.hoeTier("mlinnotas:cobalt_hoe"));
		assertEquals(Tier.CELESTIUM, HarvestRules.hoeTier("mlinnotas:celestium_hoe"));
		assertNull(HarvestRules.hoeTier("minecraft:diamond_pickaxe"));
		assertNull(HarvestRules.hoeTier("mlinnotas:iron_hoe"));
	}
}
