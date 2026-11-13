package com.mlin.notas.waila;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemGridTest {
	/** Pilha falsa: "id:quantidade", vazio = "". */
	private static List<ItemGrid.Group<String>> group(String... stacks) {
		return ItemGrid.group(Arrays.asList(stacks), String::isEmpty,
				(a, b) -> a.split(":")[0].equals(b.split(":")[0]),
				s -> Integer.parseInt(s.split(":")[1]));
	}

	@Test
	void groupsIdenticalStacksInFirstSeenOrder() {
		List<ItemGrid.Group<String>> groups = group("dirt:64", "", "stone:10", "dirt:30", null, "stone:5", "diamond:1");
		assertEquals(3, groups.size());
		assertEquals("dirt:64", groups.get(0).sample());
		assertEquals(94, groups.get(0).count());
		assertEquals(15, groups.get(1).count());
		assertEquals(1, groups.get(2).count());
	}

	@Test
	void emptyContainerHasNoGroups() {
		assertEquals(0, group("", "", "").size());
	}

	@Test
	void rowsOfNine() {
		assertEquals(0, ItemGrid.rows(0));
		assertEquals(1, ItemGrid.rows(9));
		assertEquals(2, ItemGrid.rows(10));
		assertEquals(4 * ItemGrid.CELL, ItemGrid.width(4));
		assertEquals(9 * ItemGrid.CELL, ItemGrid.width(20));
		assertEquals(3 * ItemGrid.CELL, ItemGrid.height(20));
		assertEquals(ItemGrid.CELL, ItemGrid.cellX(10));
		assertEquals(ItemGrid.CELL, ItemGrid.cellY(10));
		assertEquals(0, ItemGrid.cellX(9));
	}

	@Test
	void visibleCellsAndOverflow() {
		assertEquals(27, ItemGrid.visible(40, 3));
		assertEquals(13, ItemGrid.overflow(40, 3));
		assertEquals(5, ItemGrid.visible(5, 3));
		assertEquals(0, ItemGrid.overflow(5, 3));
	}

	@Test
	void countLabels() {
		assertEquals("", ItemGrid.countLabel(1));
		assertEquals("64", ItemGrid.countLabel(64));
		assertEquals("999", ItemGrid.countLabel(999));
		assertEquals("1k", ItemGrid.countLabel(1000));
		assertEquals("1.2k", ItemGrid.countLabel(1234));
		assertEquals("15k", ItemGrid.countLabel(15999));
	}
}
