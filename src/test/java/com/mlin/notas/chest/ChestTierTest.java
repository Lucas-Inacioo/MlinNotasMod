package com.mlin.notas.chest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChestTierTest {
	@Test
	void sizesMatchSpec() {
		assertEquals(54, ChestTier.IRON.size());
		assertEquals(81, ChestTier.GOLD.size());
		assertEquals(108, ChestTier.DIAMOND.size());
	}

	@Test
	void chainOfTiers() {
		assertEquals(ChestTier.GOLD, ChestTier.IRON.next());
		assertEquals(ChestTier.DIAMOND, ChestTier.GOLD.next());
		assertNull(ChestTier.DIAMOND.next());
		assertNull(ChestTier.IRON.previous());
		assertEquals(ChestTier.GOLD, ChestTier.DIAMOND.previous());
	}

	@Test
	void namesAndScreenWidth() {
		assertEquals("iron_chest", ChestTier.IRON.blockName());
		assertEquals("chest_upgrade_gold", ChestTier.GOLD.upgradeName());
		assertEquals(176, ChestTier.IRON.screenWidth());
		assertEquals(230, ChestTier.DIAMOND.screenWidth());
	}
}
