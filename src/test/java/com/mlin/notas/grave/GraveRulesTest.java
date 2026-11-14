package com.mlin.notas.grave;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveRulesTest {
	@Test
	void keepsHalfTheExperiencePoints() {
		assertEquals(50, GraveRules.XP_KEEP_PERCENT);
		assertEquals(0, GraveRules.storedXp(0));
		assertEquals(0, GraveRules.storedXp(1));
		assertEquals(50, GraveRules.storedXp(100));
		assertEquals(697, GraveRules.storedXp(1395)); // nivel 30
		assertEquals(0, GraveRules.storedXp(-5));
		assertEquals(Integer.MAX_VALUE / 2, GraveRules.storedXp(Integer.MAX_VALUE));
	}

	@Test
	void keepInventoryDefaultAppliesOnlyOnce() {
		assertFalse(GraveRules.MOD_DEFAULT_KEEP_INVENTORY);
		assertTrue(GraveRules.shouldApplyKeepInventoryDefault(false));
		assertFalse(GraveRules.shouldApplyKeepInventoryDefault(true));
	}

	@Test
	void menuShowsEverySlotExactlyOnce() {
		Set<Integer> seen = new HashSet<>();
		for (int slot = 0; slot < GraveRules.SIZE; slot++) {
			assertTrue(seen.add(GraveRules.graveIndexForMenuSlot(slot)), "repetido no slot " + slot);
		}
		assertEquals(GraveRules.SIZE, seen.size());
	}

	@Test
	void menuLayoutLooksLikeTheInventory() {
		// linha 1: capacete, peitoral, calca, botas, mao secundaria
		assertEquals(List.of(39, 38, 37, 36, 40), List.of(GraveRules.graveIndexForMenuSlot(0), GraveRules.graveIndexForMenuSlot(1),
				GraveRules.graveIndexForMenuSlot(2), GraveRules.graveIndexForMenuSlot(3), GraveRules.graveIndexForMenuSlot(4)));
		// barra de atalhos na ultima linha, na ordem
		for (int i = 0; i < 9; i++) {
			assertEquals(i, GraveRules.graveIndexForMenuSlot(45 + i));
		}
		// inventario principal nas linhas 3 a 5
		assertEquals(9, GraveRules.graveIndexForMenuSlot(18));
		assertEquals(35, GraveRules.graveIndexForMenuSlot(44));
	}

	@Test
	void indexesSplitIntoInventoryAccessoriesAndExtras() {
		assertEquals(0, GraveRules.inventoryIndex(0));
		assertEquals(42, GraveRules.inventoryIndex(42));
		assertEquals(-1, GraveRules.inventoryIndex(43));
		assertEquals(0, GraveRules.accessoryIndex(43));
		assertEquals(5, GraveRules.accessoryIndex(48));
		assertEquals(-1, GraveRules.accessoryIndex(49));
		assertEquals(-1, GraveRules.accessoryIndex(42));
		assertEquals(GraveRules.SIZE, GraveRules.EXTRA_START + 5);
	}

	@Test
	void indexTracksGravesByPosition() {
		GraveIndex index = new GraveIndex();
		GraveInfo a = new GraveInfo("u1", "Ana", "minecraft:overworld", 1, 64, 2, 10, 50, 3);
		GraveInfo b = new GraveInfo("u1", "Ana", "minecraft:the_nether", 1, 64, 2, 4, 0, 5);
		GraveInfo c = new GraveInfo("u2", "Bia", "minecraft:overworld", 9, 70, 9, 1, 0, 6);
		index.put(a);
		index.put(b);
		index.put(c);
		assertEquals(3, index.size());
		assertEquals(List.of(b, a), index.ofOwner("u1"));
		assertTrue(index.get("minecraft:overworld", 1, 64, 2).isPresent());
		index.put(a.withContents(2, 0));
		assertEquals(2, index.get("minecraft:overworld", 1, 64, 2).orElseThrow().items());
		assertTrue(index.remove("minecraft:overworld", 1, 64, 2));
		assertFalse(index.remove("minecraft:overworld", 1, 64, 2));
		assertEquals(2, index.size());
	}
}
