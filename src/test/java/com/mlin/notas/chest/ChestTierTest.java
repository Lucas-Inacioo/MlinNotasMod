package com.mlin.notas.chest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestTierTest {
	@Test
	void elevenModChestsPlusVanillaMakeTwelveTiers() {
		assertEquals(11, ChestTier.values().length);
	}

	@Test
	void existingChestsKeepTheirCapacity() {
		// baus ja colocados em mundos antigos nao podem perder slots
		assertEquals(54, ChestTier.IRON.size());
		assertEquals(81, ChestTier.GOLD.size());
		assertEquals(108, ChestTier.DIAMOND.size());
	}

	@Test
	void capacityGrowsEveryTierAndIsWholeRows() {
		int previous = 27; // bau vanilla
		for (ChestTier tier : ChestTier.values()) {
			assertTrue(tier.size() > previous, tier + " deveria ser maior que o anterior");
			assertEquals(0, tier.size() % ChestTier.COLUMNS);
			previous = tier.size();
		}
	}

	@Test
	void chainOfTiers() {
		assertEquals(ChestTier.COPPER, ChestTier.STONE.next());
		assertEquals(ChestTier.GOLD, ChestTier.IRON.next());
		assertNull(ChestTier.CELESTIUM.next());
		assertNull(ChestTier.STONE.previous());
		assertEquals(ChestTier.GOLD, ChestTier.COBALT.previous());
	}

	@Test
	void namesAndScreenWidth() {
		assertEquals("iron_chest", ChestTier.IRON.blockName());
		assertEquals("chest_upgrade_gold", ChestTier.GOLD.upgradeName());
		assertEquals(176, ChestTier.IRON.screenWidth());
		assertEquals(190, ChestTier.DIAMOND.screenWidth());
	}

	@Test
	void scrolling() {
		assertEquals(0, ChestTier.IRON.maxScroll());
		assertEquals(4, ChestTier.STONE.visibleRows());
		assertEquals(6, ChestTier.DIAMOND.visibleRows());
		assertEquals(6, ChestTier.DIAMOND.maxScroll());
		assertEquals(0, ChestTier.DIAMOND.clampScroll(0, -3));
		assertEquals(6, ChestTier.DIAMOND.clampScroll(4, 10));
		assertEquals(5, ChestTier.DIAMOND.clampScroll(4, 1));
	}
}
