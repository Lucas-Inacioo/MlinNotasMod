package com.mlin.notas.chest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentsTransferTest {
	private static final String EMPTY = "";

	@Test
	void growingKeepsPositionsAndPadsWithEmpty() {
		List<String> out = ContentsTransfer.resize(List.of("a", EMPTY, "b"), 5, String::isEmpty, EMPTY);
		assertEquals(Arrays.asList("a", EMPTY, "b", EMPTY, EMPTY), out);
	}

	@Test
	void shrinkingRelocatesOverflowIntoFreeSlots() {
		List<String> out = ContentsTransfer.resize(List.of(EMPTY, "a", EMPTY, "b", "c"), 3, String::isEmpty, EMPTY);
		assertEquals(Arrays.asList("b", "a", "c"), out);
	}

	@Test
	void shrinkingWithoutRoomThrows() {
		List<String> full = new ArrayList<>(Collections.nCopies(5, "x"));
		assertThrows(IllegalStateException.class, () -> ContentsTransfer.resize(full, 3, String::isEmpty, EMPTY));
	}

	@Test
	void countNonEmpty() {
		assertEquals(2, ContentsTransfer.countNonEmpty(List.of("a", EMPTY, "b"), String::isEmpty));
	}
}
