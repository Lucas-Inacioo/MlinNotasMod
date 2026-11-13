package com.mlin.notas.accessories;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryBudgetTest {
	private static final double EPS = 1.0E-9;

	private static List<AccessoryDef> defs(String... ids) {
		List<AccessoryDef> out = new ArrayList<>();
		for (String id : ids) {
			out.add(id == null ? null : AccessoryCatalog.byId(id));
		}
		return out;
	}

	@Test
	void stageFollowsMiningLevel() {
		assertEquals(AccessoryStage.PRE_GOLEM, AccessoryStage.fromMiningLevel(Tier.WOOD.level()));
		assertEquals(AccessoryStage.PRE_GOLEM, AccessoryStage.fromMiningLevel(Tier.GOLD.level()));
		assertEquals(AccessoryStage.GOLEM_GUARDIAN, AccessoryStage.fromMiningLevel(Tier.COBALT.level()));
		assertEquals(AccessoryStage.GOLEM_GUARDIAN, AccessoryStage.fromMiningLevel(Tier.DIAMOND.level()));
		assertEquals(AccessoryStage.NETHERITE_DRAGON, AccessoryStage.fromMiningLevel(Tier.NETHERITE.level()));
		assertEquals(AccessoryStage.NETHERITE_DRAGON, AccessoryStage.fromMiningLevel(Tier.INFERNITE.level()));
		assertEquals(AccessoryStage.ENDGAME, AccessoryStage.fromMiningLevel(Tier.ENDITE.level()));
		assertEquals(AccessoryStage.ENDGAME, AccessoryStage.fromMiningLevel(Tier.CELESTIUM.level()));
	}

	@Test
	void capsMatchTheProgressionTable() {
		double[][] table = {{0.05, 0.04, 0.05, 0}, {0.10, 0.08, 0.08, 2}, {0.15, 0.12, 0.10, 4}, {0.20, 0.15, 0.15, 6}};
		AccessoryStat[] staged = {AccessoryStat.DAMAGE, AccessoryStat.DAMAGE_REDUCTION, AccessoryStat.SPEED, AccessoryStat.MAX_HEALTH};
		for (AccessoryStage stage : AccessoryStage.values()) {
			for (int i = 0; i < staged.length; i++) {
				assertEquals(table[stage.ordinal()][i], staged[i].cap(stage), EPS, stage + " " + staged[i]);
			}
		}
	}

	@Test
	void hardRulesOnFixedCaps() {
		assertEquals(0.03, AccessoryStat.LIFESTEAL.cap(AccessoryStage.ENDGAME), EPS);
		for (AccessoryStat stat : List.of(AccessoryStat.FIRE_REDUCTION, AccessoryStat.SONIC_REDUCTION, AccessoryStat.MAGIC_REDUCTION,
				AccessoryStat.BLAST_REDUCTION, AccessoryStat.DAMAGE_REDUCTION)) {
			for (AccessoryStage stage : AccessoryStage.values()) {
				assertTrue(stat.cap(stage) <= 0.30 + EPS, "sem imunidade: " + stat);
			}
		}
	}

	@Test
	void sumsAndClampsToTheStage() {
		// 2 aneis de netherite (6% cada) + luva do duelista (4%) = 16% de dano
		List<AccessoryDef> set = defs("netherite_ring", "netherite_ring", null, null, "duelist_glove", null);
		AccessoryBudget.Totals early = AccessoryBudget.compute(set, AccessoryStage.PRE_GOLEM);
		assertEquals(0.16, early.raw(AccessoryStat.DAMAGE), EPS);
		assertEquals(0.05, early.applied(AccessoryStat.DAMAGE), EPS);
		assertTrue(early.capped(AccessoryStat.DAMAGE));
		AccessoryBudget.Totals late = AccessoryBudget.compute(set, AccessoryStage.ENDGAME);
		assertEquals(0.16, late.applied(AccessoryStat.DAMAGE), EPS);
		assertFalse(late.capped(AccessoryStat.DAMAGE));
		// atributo sem teto atingido segue igual
		assertEquals(0.05, early.applied(AccessoryStat.ATTACK_SPEED), EPS);
	}

	@Test
	void healthHasNoBudgetBeforeTheGolem() {
		AccessoryBudget.Totals totals = AccessoryBudget.compute(defs("life_amulet"), AccessoryStage.PRE_GOLEM);
		assertEquals(2.0, totals.raw(AccessoryStat.MAX_HEALTH), EPS);
		assertEquals(0.0, totals.applied(AccessoryStat.MAX_HEALTH), EPS);
		assertEquals(2.0, AccessoryBudget.compute(defs("life_amulet"), AccessoryStage.GOLEM_GUARDIAN).applied(AccessoryStat.MAX_HEALTH), EPS);
	}

	@Test
	void uniqueAccessoriesCountOnceBasicsStack() {
		AccessoryBudget.Totals uniques = AccessoryBudget.compute(defs("vampire_fang", "vampire_fang"), AccessoryStage.ENDGAME);
		assertEquals(0.03, uniques.raw(AccessoryStat.LIFESTEAL), EPS);
		AccessoryBudget.Totals basics = AccessoryBudget.compute(defs("copper_ring", "copper_ring"), AccessoryStage.ENDGAME);
		assertEquals(0.04, basics.raw(AccessoryStat.DAMAGE), EPS);
	}

	@Test
	void negativeValuesAreNeverClamped() {
		AccessoryBudget.Totals totals = AccessoryBudget.compute(defs("anchor_belt"), AccessoryStage.PRE_GOLEM);
		assertEquals(-0.03, totals.applied(AccessoryStat.SPEED), EPS);
		assertFalse(totals.capped(AccessoryStat.SPEED));
	}

	@Test
	void incomingFactorCombinesGeneralAndSpecificReductions() {
		// colar de ferro 2% + concha 30% de fogo
		AccessoryBudget.Totals totals = AccessoryBudget.compute(defs(null, null, "iron_necklace", null, null, "abyssal_shell"),
				AccessoryStage.GOLEM_GUARDIAN);
		assertEquals(0.98, AccessoryBudget.incomingFactor(totals, false, false, false, false), EPS);
		assertEquals(0.98 * 0.70, AccessoryBudget.incomingFactor(totals, true, false, false, false), EPS);
		// tudo no maximo ainda deixa passar dano
		List<AccessoryDef> all = new ArrayList<>(AccessoryCatalog.all());
		AccessoryBudget.Totals max = AccessoryBudget.compute(all, AccessoryStage.ENDGAME);
		assertTrue(AccessoryBudget.incomingFactor(max, true, true, true, true) > 0.2);
		assertEquals(0.15, max.applied(AccessoryStat.DAMAGE_REDUCTION), EPS);
	}

	@Test
	void lifestealIsAtMostThreePercent() {
		AccessoryBudget.Totals totals = AccessoryBudget.compute(AccessoryCatalog.all(), AccessoryStage.ENDGAME);
		assertEquals(0.3, AccessoryBudget.lifesteal(totals, 10.0), EPS);
	}

	@Test
	void abilitiesAreCollected() {
		AccessoryBudget.Totals totals = AccessoryBudget.compute(defs("magnet_ring", null, null, null, "climbing_glove", "rabbit_foot_charm"),
				AccessoryStage.PRE_GOLEM);
		assertTrue(totals.has(AccessoryAbility.MAGNET));
		assertTrue(totals.has(AccessoryAbility.CLIMB));
		assertTrue(totals.has(AccessoryAbility.DOUBLE_JUMP));
		assertFalse(totals.has(AccessoryAbility.THORNS));
	}

	@Test
	void formatsValuesForPlayers() {
		assertEquals("+5%", AccessoryStat.DAMAGE.format(0.05));
		assertEquals("-3%", AccessoryStat.SPEED.format(-0.03));
		assertEquals("+2", AccessoryStat.MAX_HEALTH.format(2));
		assertEquals("+1.5", AccessoryStat.LUCK.format(1.5));
		assertEquals("+2.5%", AccessoryStat.DAMAGE.format(0.025));
	}

	@Test
	void defsFromIdsSkipsUnknown() {
		assertEquals(Arrays.asList(AccessoryCatalog.byId("gold_ring"), null, null), AccessoryBudget.defs(List.of("gold_ring", "", "nope")));
	}
}
