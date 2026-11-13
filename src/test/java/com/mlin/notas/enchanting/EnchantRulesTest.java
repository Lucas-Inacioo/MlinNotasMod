package com.mlin.notas.enchanting;

import com.mlin.notas.enchanting.EnchantRules.Check;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnchantRulesTest {
	@Test
	void bookshelvesUnlockLevels() {
		assertEquals(1, EnchantRules.maxAllowedLevel(5, 0), "sem estantes so nivel 1");
		assertEquals(5, EnchantRules.maxAllowedLevel(5, 15), "15 estantes liberam o maximo");
		assertEquals(5, EnchantRules.maxAllowedLevel(5, 40), "estantes alem de 15 nao contam a mais");
		assertEquals(3, EnchantRules.maxAllowedLevel(5, 8));
		assertEquals(1, EnchantRules.maxAllowedLevel(1, 0), "encantamento de nivel unico sempre disponivel");
		assertEquals(0, EnchantRules.maxAllowedLevel(0, 15));
	}

	@Test
	void levelsGrowMonotonicallyWithBookshelves() {
		for (int max = 1; max <= 5; max++) {
			int previous = 0;
			for (int shelves = 0; shelves <= 15; shelves++) {
				int allowed = EnchantRules.maxAllowedLevel(max, shelves);
				assertEquals(true, allowed >= previous, "nunca diminui ao adicionar estantes");
				previous = allowed;
			}
		}
	}

	@Test
	void bookshelvesNeededIsInverseOfMaxAllowed() {
		assertEquals(0, EnchantRules.bookshelvesNeeded(5, 1));
		assertEquals(15, EnchantRules.bookshelvesNeeded(5, 5));
		int needed = EnchantRules.bookshelvesNeeded(4, 3);
		assertEquals(true, EnchantRules.maxAllowedLevel(4, needed) >= 3);
		assertEquals(true, EnchantRules.maxAllowedLevel(4, needed - 1) < 3);
	}

	@Test
	void xpCostSumsEachNewLevel() {
		assertEquals(2 + 3 + 4 + 5 + 6, EnchantRules.xpCost(1, 0, 5), "Afiacao V do zero");
		assertEquals(5 + 6, EnchantRules.xpCost(1, 3, 5), "subir de III para V paga so os niveis novos");
		assertEquals(9, EnchantRules.xpCost(8, 0, 1), "toque suave");
		assertEquals(0, EnchantRules.xpCost(1, 3, 3));
	}

	@Test
	void lapisCostIsOnePerNewLevel() {
		assertEquals(5, EnchantRules.lapisCost(0, 5));
		assertEquals(2, EnchantRules.lapisCost(3, 5));
		assertEquals(0, EnchantRules.lapisCost(5, 3));
	}

	@Test
	void checkReportsFirstProblem() {
		assertEquals(Check.NO_ITEM, EnchantRules.check(false, true, true, 0, 1, 5, 1, 15, 99, 64, false));
		assertEquals(Check.NOT_APPLICABLE, EnchantRules.check(true, false, true, 0, 1, 5, 1, 15, 99, 64, false));
		assertEquals(Check.NOT_APPLICABLE, EnchantRules.check(true, true, true, 0, 6, 5, 1, 15, 99, 64, false));
		assertEquals(Check.INCOMPATIBLE, EnchantRules.check(true, true, false, 0, 1, 5, 1, 15, 99, 64, false));
		assertEquals(Check.ALREADY_AT_LEVEL, EnchantRules.check(true, true, true, 3, 3, 5, 1, 15, 99, 64, false));
		assertEquals(Check.NEEDS_BOOKSHELVES, EnchantRules.check(true, true, true, 0, 5, 5, 1, 0, 99, 64, false));
		assertEquals(Check.NOT_ENOUGH_XP, EnchantRules.check(true, true, true, 0, 5, 5, 1, 15, 19, 64, false));
		assertEquals(Check.NOT_ENOUGH_LAPIS, EnchantRules.check(true, true, true, 0, 5, 5, 1, 15, 20, 4, false));
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 0, 5, 5, 1, 15, 20, 5, false));
	}

	@Test
	void creativeSkipsCostsButNotRules() {
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 0, 5, 5, 1, 15, 0, 0, true));
		assertEquals(Check.NEEDS_BOOKSHELVES, EnchantRules.check(true, true, true, 0, 5, 5, 1, 0, 0, 0, true));
	}
}
