package com.mlin.notas.enchanting;

import com.mlin.notas.enchanting.EnchantRules.Check;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		assertEquals(4 + 6 + 8 + 10 + 12, EnchantRules.xpCost(1, 0, 5), "Afiacao V do zero: 2*custo + 2*nivel por nivel");
		assertEquals(10 + 12, EnchantRules.xpCost(1, 3, 5), "subir de III para V paga so os niveis novos");
		assertEquals(18, EnchantRules.xpCost(8, 0, 1), "toque suave");
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
		assertEquals(Check.NO_ITEM, EnchantRules.check(false, true, true, 0, 1, 5, 1, 15, 12, 99, 64, 1, 99, false));
		assertEquals(Check.NOT_APPLICABLE, EnchantRules.check(true, false, true, 0, 1, 5, 1, 15, 12, 99, 64, 1, 99, false));
		assertEquals(Check.NOT_APPLICABLE, EnchantRules.check(true, true, true, 0, 6, 5, 1, 15, 12, 99, 64, 1, 99, false));
		assertEquals(Check.INCOMPATIBLE, EnchantRules.check(true, true, false, 0, 1, 5, 1, 15, 12, 99, 64, 1, 99, false));
		assertEquals(Check.ALREADY_AT_LEVEL, EnchantRules.check(true, true, true, 3, 3, 5, 1, 15, 12, 99, 64, 1, 99, false));
		assertEquals(Check.NEEDS_BOOKSHELVES, EnchantRules.check(true, true, true, 0, 5, 5, 1, 0, 12, 99, 64, 1, 99, false));
		assertEquals(Check.NOT_ENOUGH_XP, EnchantRules.check(true, true, true, 0, 5, 5, 1, 15, 12, 39, 64, 1, 99, false));
		assertEquals(Check.NOT_ENOUGH_LAPIS, EnchantRules.check(true, true, true, 0, 5, 5, 1, 15, 12, 40, 4, 1, 99, false));
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 0, 5, 5, 1, 15, 12, 40, 5, 1, 99, false));
	}

	@Test
	void worldMiningLevelCapsEnchantments() {
		assertEquals(1, EnchantRules.miningCap(com.mlin.notas.tier.Tier.WOOD.level()));
		assertEquals(1, EnchantRules.miningCap(com.mlin.notas.tier.Tier.IRON.level()));
		assertEquals(2, EnchantRules.miningCap(com.mlin.notas.tier.Tier.GOLD.level()));
		assertEquals(2, EnchantRules.miningCap(com.mlin.notas.tier.Tier.COBALT.level()));
		assertEquals(3, EnchantRules.miningCap(com.mlin.notas.tier.Tier.DIAMOND.level()));
		assertEquals(3, EnchantRules.miningCap(com.mlin.notas.tier.Tier.NETHERITE.level()));
		assertEquals(4, EnchantRules.miningCap(com.mlin.notas.tier.Tier.INFERNITE.level()));
		assertEquals(4, EnchantRules.miningCap(com.mlin.notas.tier.Tier.ENDITE.level()));
		assertEquals(5, EnchantRules.cappedLevel(5, com.mlin.notas.tier.Tier.ASTRALITE.level()), "astralita libera o maximo");
		assertEquals(1, EnchantRules.cappedLevel(1, 1), "encantamento de nivel unico nunca trava");
		assertEquals(com.mlin.notas.tier.Tier.GOLD, EnchantRules.tierForLevel(2));
		assertEquals(com.mlin.notas.tier.Tier.INFERNITE, EnchantRules.tierForLevel(4));
		assertEquals(com.mlin.notas.tier.Tier.ASTRALITE, EnchantRules.tierForLevel(5));
		// na mesa: Ferro so libera I, mesmo com 15 estantes e XP de sobra
		int iron = com.mlin.notas.tier.Tier.IRON.level();
		assertEquals(Check.MINING_CAP, EnchantRules.check(true, true, true, 0, 2, 5, 1, 15, iron, 99, 64, 1, 99, false));
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 0, 1, 5, 1, 15, iron, 99, 64, 1, 99, false));
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 0, 2, 5, 1, 15, iron, 0, 0, 1, 0, true), "criativo ignora o teto");
		// bigorna: nao passa do teto, mas consertar um item achado com nivel alto continua valendo
		assertFalse(EnchantRules.anvilAllows(0, 3, 5, com.mlin.notas.tier.Tier.GOLD.level()));
		assertTrue(EnchantRules.anvilAllows(0, 2, 5, com.mlin.notas.tier.Tier.GOLD.level()));
		assertTrue(EnchantRules.anvilAllows(4, 4, 5, com.mlin.notas.tier.Tier.GOLD.level()), "item ja tinha IV");
		assertFalse(EnchantRules.anvilAllows(4, 5, 5, com.mlin.notas.tier.Tier.GOLD.level()), "juntar IV + IV = V passa do teto");
	}

	@Test
	void creativeSkipsCostsButNotRules() {
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 0, 5, 5, 1, 15, 12, 0, 0, 1, 99, true));
		assertEquals(Check.NEEDS_BOOKSHELVES, EnchantRules.check(true, true, true, 0, 5, 5, 1, 0, 12, 0, 0, 1, 99, true));
	}
}
