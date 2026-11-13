package com.mlin.notas.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeafDecayRulesTest {
	@Test
	void onlyNaturalLeavesFarFromLogsAreOrphaned() {
		assertTrue(LeafDecayRules.orphaned(7, false));
		assertFalse(LeafDecayRules.orphaned(6, false));
		assertFalse(LeafDecayRules.orphaned(1, false));
		assertFalse(LeafDecayRules.orphaned(7, true), "folha colocada pelo jogador nunca cai");
	}

	@Test
	void distanceComesFromTheClosestNeighbor() {
		assertEquals(1, LeafDecayRules.distanceFromNeighbors(7, 7, 0, 7, 7, 7), "encostada no tronco");
		assertEquals(4, LeafDecayRules.distanceFromNeighbors(7, 3, 5, 6, 7, 7));
		assertEquals(7, LeafDecayRules.distanceFromNeighbors(7, 7, 7, 7, 7, 7), "sem tronco perto");
		assertEquals(7, LeafDecayRules.distanceFromNeighbors(6, 7, 7, 7, 7, 7), "vizinha a 6 deixa esta solta");
	}

	@Test
	void delayStaysWithinOneToFourSeconds() {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int roll = -5000; roll <= 5000; roll += 7) {
			int d = LeafDecayRules.delay(roll);
			min = Math.min(min, d);
			max = Math.max(max, d);
		}
		assertTrue(min >= LeafDecayRules.MIN_DELAY && max <= LeafDecayRules.MAX_DELAY);
		assertTrue(min >= 20 && max <= 80, "entre 1 e 4 segundos");
		assertTrue(max - min > 30, "atrasos espalhados para a copa cair aos poucos");
	}
}
