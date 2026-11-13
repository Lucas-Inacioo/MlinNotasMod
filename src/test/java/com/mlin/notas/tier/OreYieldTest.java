package com.mlin.notas.tier;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreYieldTest {
	private static final OreYield.Source GARIMPO = new OreYield.Source("beacon_prospecting", 0.5);
	private static final OreYield.Source SKILL = new OreYield.Source("skill_prospecting", 0.75);

	@Test
	void chancesAddUp() {
		assertEquals(0.0, OreYield.totalChance(List.of()));
		assertEquals(1.25, OreYield.totalChance(List.of(GARIMPO, SKILL)), 1e-9);
		assertEquals(0.5, OreYield.expectedExtra(1, List.of(GARIMPO)), 1e-9);
		assertEquals(0.0, OreYield.expectedExtra(0, List.of(GARIMPO)), 1e-9);
	}

	@Test
	void fractionIsRolledAndWholePartGuaranteed() {
		assertEquals(1, OreYield.rollExtra(1, List.of(GARIMPO), () -> 0.49));
		assertEquals(0, OreYield.rollExtra(1, List.of(GARIMPO), () -> 0.5));
		assertEquals(1, OreYield.rollExtra(1, List.of(GARIMPO, SKILL), () -> 0.99));
		assertEquals(2, OreYield.rollExtra(1, List.of(GARIMPO, SKILL), () -> 0.1));
		assertEquals(0, OreYield.rollExtra(1, List.of(), () -> 0.0));
	}

	@Test
	void noExtraWithoutBaseDrop() {
		assertEquals(0, OreYield.rollExtra(0, List.of(GARIMPO, SKILL), () -> 0.0));
	}

	@Test
	void averageMatchesExpected() {
		Random random = new Random(42);
		int total = 0;
		int rolls = 20000;
		for (int i = 0; i < rolls; i++) {
			total += OreYield.rollExtra(1, List.of(GARIMPO), random::nextDouble);
		}
		double average = total / (double) rolls;
		assertTrue(Math.abs(average - 0.5) < 0.02, "media " + average);
	}

	@Test
	void rejectsNegativeChance() {
		assertThrows(IllegalArgumentException.class, () -> new OreYield.Source("x", -0.1));
	}
}
