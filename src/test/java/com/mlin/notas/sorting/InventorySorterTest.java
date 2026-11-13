package com.mlin.notas.sorting;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySorterTest {
	private static SlotItem item(String id, int count) {
		return new SlotItem(id, "", count, 64);
	}

	@Test
	void mergesStacksOfSameKind() {
		List<SlotItem> sorted = InventorySorter.sort(Arrays.asList(item("minecraft:stone", 30), null, item("minecraft:stone", 40)));
		assertEquals(item("minecraft:stone", 64), sorted.get(0));
		assertEquals(item("minecraft:stone", 6), sorted.get(1));
		assertNull(sorted.get(2));
	}

	@Test
	void ordersAlphabeticallyById() {
		List<SlotItem> sorted = InventorySorter.sort(Arrays.asList(item("minecraft:stone", 1), null, item("minecraft:apple", 1), item("mlinnotas:backpack", 1)));
		assertEquals("minecraft:apple", sorted.get(0).id());
		assertEquals("minecraft:stone", sorted.get(1).id());
		assertEquals("mlinnotas:backpack", sorted.get(2).id());
		assertNull(sorted.get(3));
	}

	@Test
	void keepsVariantsSeparate() {
		SlotItem plain = new SlotItem("minecraft:diamond_sword", "", 1, 1);
		SlotItem enchanted = new SlotItem("minecraft:diamond_sword", "sharpness", 1, 1);
		List<SlotItem> sorted = InventorySorter.sort(Arrays.asList(enchanted, plain));
		assertEquals(plain, sorted.get(0));
		assertEquals(enchanted, sorted.get(1));
	}

	@Test
	void respectsMaxStackSize() {
		SlotItem pearl = new SlotItem("minecraft:ender_pearl", "", 16, 16);
		List<SlotItem> sorted = InventorySorter.sort(Arrays.asList(pearl, pearl.withCount(10), null));
		assertEquals(16, sorted.get(0).count());
		assertEquals(10, sorted.get(1).count());
	}

	@Test
	void keepsSizeAndFillsWithNull() {
		List<SlotItem> sorted = InventorySorter.sort(Arrays.asList(null, null, item("a", 1)));
		assertEquals(3, sorted.size());
		assertNull(sorted.get(1));
		assertNull(sorted.get(2));
	}

	@Test
	void isSortedDetectsNoop() {
		assertTrue(InventorySorter.isSorted(Arrays.asList(item("a", 64), item("b", 1), null)));
	}

	@Test
	void rejectsInvalidCounts() {
		assertThrows(IllegalArgumentException.class, () -> new SlotItem("a", "", 0, 64));
	}
}
