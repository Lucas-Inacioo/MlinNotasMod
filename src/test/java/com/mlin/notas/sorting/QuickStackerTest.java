package com.mlin.notas.sorting;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuickStackerTest {
	private static SlotItem s(String id, int count) {
		return new SlotItem(id, "", count, 64);
	}

	@Test
	void movesOnlyKindsAlreadyInContainerMergingFirst() {
		List<SlotItem> box = Arrays.asList(s("stone", 60), null, s("dirt", 10), null);
		List<SlotItem> inv = Arrays.asList(s("stone", 20), s("sand", 5), s("dirt", 3));
		QuickStacker.Result r = QuickStacker.plan(box, inv);
		// pedra: 4 completam a pilha de 60, 16 vao para o primeiro vazio
		assertEquals(64, r.container().get(0).count());
		assertEquals(16, r.container().get(1).count());
		assertEquals("stone", r.container().get(1).id());
		assertEquals(13, r.container().get(2).count());
		assertNull(r.container().get(3));
		assertNull(r.player().get(0));
		assertEquals(s("sand", 5), r.player().get(1)); // areia nao existe no bau
		assertNull(r.player().get(2));
		assertEquals(23, r.moved());
	}

	@Test
	void leftoverStaysWhenFull() {
		List<SlotItem> box = Arrays.asList(s("stone", 63));
		List<SlotItem> inv = Arrays.asList(s("stone", 10));
		QuickStacker.Result r = QuickStacker.plan(box, inv);
		assertEquals(64, r.container().getFirst().count());
		assertEquals(9, r.player().getFirst().count());
		assertEquals(1, r.moved());
	}

	@Test
	void variantsAreDifferentKinds() {
		List<SlotItem> box = Arrays.asList(new SlotItem("sword", "a", 1, 1), null);
		List<SlotItem> inv = Arrays.asList(new SlotItem("sword", "b", 1, 1), new SlotItem("sword", "a", 1, 1));
		QuickStacker.Result r = QuickStacker.plan(box, inv);
		assertEquals(new SlotItem("sword", "b", 1, 1), r.player().get(0));
		assertNull(r.player().get(1));
		assertEquals(1, r.moved());
	}
}
