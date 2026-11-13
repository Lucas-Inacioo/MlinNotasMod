package com.mlin.notas.backpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackUpgradesTest {
	@Test
	void defaultHas27Slots() {
		assertEquals(27, BackpackUpgrades.DEFAULT.slotCount());
		assertEquals(3, BackpackUpgrades.DEFAULT.rows());
	}

	@Test
	void sizeUpgradeGrowsUpToMax() {
		BackpackUpgrades u = BackpackUpgrades.DEFAULT.apply(BackpackUpgradeKind.SIZE);
		assertEquals(54, u.slotCount());
		u = u.apply(BackpackUpgradeKind.SIZE);
		assertEquals(81, u.slotCount());
		assertFalse(u.canApply(BackpackUpgradeKind.SIZE));
		assertSame(u, u.apply(BackpackUpgradeKind.SIZE), "no maximo devolve a mesma instancia");
	}

	@Test
	void booleanUpgradesApplyOnce() {
		BackpackUpgrades u = BackpackUpgrades.DEFAULT.apply(BackpackUpgradeKind.CRAFTING);
		assertTrue(u.crafting());
		assertFalse(u.canApply(BackpackUpgradeKind.CRAFTING));
		assertTrue(u.apply(BackpackUpgradeKind.FURNACE).furnace());
		assertTrue(u.apply(BackpackUpgradeKind.WATER).water());
		assertTrue(u.apply(BackpackUpgradeKind.LAVA).lava());
	}

	@Test
	void fluidPlacementRules() {
		BackpackUpgrades water = BackpackUpgrades.DEFAULT.apply(BackpackUpgradeKind.WATER);
		assertFalse(water.canPlaceSelectedFluid(), "agua precisa encher primeiro");
		assertTrue(water.withWaterFilled(true).canPlaceSelectedFluid());
		BackpackUpgrades lava = BackpackUpgrades.DEFAULT.apply(BackpackUpgradeKind.LAVA).withLavaSelected(true);
		assertFalse(lava.canPlaceSelectedFluid());
		assertTrue(lava.withLavaCharges(1).canPlaceSelectedFluid());
		assertEquals(BackpackUpgrades.MAX_LAVA_CHARGES, lava.withLavaCharges(1000).lavaCharges());
	}

	@Test
	void rejectsInvalidValues() {
		assertThrows(IllegalArgumentException.class, () -> new BackpackUpgrades(3, false, false, false, false, false, 0, false, SmeltingProgress.IDLE));
		assertThrows(IllegalArgumentException.class, () -> new BackpackUpgrades(0, false, false, false, false, false, -1, false, SmeltingProgress.IDLE));
	}
}
