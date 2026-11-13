package com.mlin.notas.enchanting;

import com.mlin.notas.enchanting.EnchantRules.Check;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantReagentsTest {
	/** Tag vanilla {@code #minecraft:non_treasure} do 26.3 (= {@code in_enchanting_table}) com o nivel maximo de cada um. */
	private static final Map<String, Integer> TABLE = Map.ofEntries(
			Map.entry("protection", 4), Map.entry("fire_protection", 4), Map.entry("feather_falling", 4), Map.entry("blast_protection", 4),
			Map.entry("projectile_protection", 4), Map.entry("respiration", 3), Map.entry("aqua_affinity", 1), Map.entry("thorns", 3),
			Map.entry("depth_strider", 3), Map.entry("sharpness", 5), Map.entry("smite", 5), Map.entry("bane_of_arthropods", 5),
			Map.entry("knockback", 2), Map.entry("fire_aspect", 2), Map.entry("looting", 3), Map.entry("sweeping_edge", 3),
			Map.entry("efficiency", 5), Map.entry("silk_touch", 1), Map.entry("unbreaking", 3), Map.entry("fortune", 3), Map.entry("power", 5),
			Map.entry("punch", 2), Map.entry("flame", 1), Map.entry("infinity", 1), Map.entry("luck_of_the_sea", 3), Map.entry("lure", 3),
			Map.entry("loyalty", 3), Map.entry("impaling", 5), Map.entry("riptide", 3), Map.entry("channeling", 1), Map.entry("multishot", 1),
			Map.entry("quick_charge", 3), Map.entry("piercing", 4), Map.entry("density", 5), Map.entry("breach", 4), Map.entry("lunge", 3));

	@Test
	void everyTableEnchantmentHasAThematicReagentThatFitsAStack() {
		for (Map.Entry<String, Integer> e : TABLE.entrySet()) {
			String id = "minecraft:" + e.getKey();
			assertTrue(EnchantReagents.mapped(id), id + " sem reagente");
			EnchantReagents.Reagent reagent = EnchantReagents.of(id);
			int max = EnchantReagents.total(reagent.base(), e.getValue());
			assertTrue(max <= 64, id + " no maximo pede " + max + " (tem que caber numa pilha)");
			assertTrue(max >= 4, id + " no maximo pede so " + max);
			assertFalse(reagent.item().startsWith("#"), "reagente e item, nao tag: " + id);
		}
		assertEquals("minecraft:sugar", EnchantReagents.of("minecraft:efficiency").item(), "exemplo do usuario");
		assertEquals("minecraft:blaze_powder", EnchantReagents.of("minecraft:fire_aspect").item(), "exemplo do usuario");
		assertEquals(EnchantReagents.FALLBACK, EnchantReagents.of("outromod:qualquer"));
	}

	@Test
	void costGrowsWithLevelAndUpgradesPayTheDifference() {
		assertEquals(3, EnchantReagents.total(3, 1));
		assertEquals(45, EnchantReagents.total(3, 5), "Afiacao V: 15 x base");
		for (int level = 1; level < 6; level++) {
			assertTrue(EnchantReagents.cost(3, level, level + 1) > EnchantReagents.cost(3, level - 1, level), "cada nivel custa mais que o anterior");
		}
		for (int from = 0; from <= 5; from++) {
			for (int to = from; to <= 5; to++) {
				assertEquals(EnchantReagents.total(3, to) - EnchantReagents.total(3, from), EnchantReagents.cost(3, from, to));
			}
		}
		assertEquals(EnchantReagents.cost(3, 0, 5), EnchantReagents.cost(3, 0, 3) + EnchantReagents.cost(3, 3, 5), "subir aos poucos custa o mesmo");
		assertEquals(0, EnchantReagents.cost(3, 5, 3));
	}

	@Test
	void tableRefusesWithoutEnoughReagent() {
		// Afiacao III do zero (base 3): 18 pederneiras
		assertEquals(Check.NOT_ENOUGH_REAGENT, EnchantRules.check(true, true, true, 0, 3, 5, 1, 15, 12, 99, 64, 3, 17, false));
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 0, 3, 5, 1, 15, 12, 99, 64, 3, 18, false));
		// de II para III: so 9
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 2, 3, 5, 1, 15, 12, 99, 64, 3, 9, false));
		assertEquals(Check.OK, EnchantRules.check(true, true, true, 0, 3, 5, 1, 15, 12, 0, 0, 3, 0, true), "criativo nao paga");
		assertTrue(List.of(Check.values()).indexOf(Check.NOT_ENOUGH_REAGENT) > List.of(Check.values()).indexOf(Check.NOT_ENOUGH_LAPIS));
	}
}
