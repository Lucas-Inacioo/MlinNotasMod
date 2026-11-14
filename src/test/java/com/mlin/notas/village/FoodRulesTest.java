package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Numeros das construcoes de producao e comida: bonus do celeiro, moinho, receitas automaticas, curral, apiario, serraria. */
class FoodRulesTest {
	private static Map<String, Integer> stock(Object... kv) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			out.put((String) kv[i], (Integer) kv[i + 1]);
		}
		return out;
	}

	@Test
	void granaryBonusIsTenPercentPerLevel() {
		assertEquals(0, FoodRules.granaryBonusPercent(0));
		assertEquals(10, FoodRules.granaryBonusPercent(1));
		assertEquals(30, FoodRules.granaryBonusPercent(3));
		assertEquals(30, FoodRules.granaryBonusPercent(9), "teto no III");
		// 8 itens a 10%: 0,8 -> 1 item com 80% de chance
		assertEquals(1, FoodRules.bonusItems(8, 10, 0.5));
		assertEquals(0, FoodRules.bonusItems(8, 10, 0.9));
		assertEquals(3, FoodRules.bonusItems(10, 30, 0.1), "10 x 30% = 3 exato");
		assertTrue(FoodRules.farmOutput(2, 3) > FoodRules.farmOutput(2, 0), "celeiro aumenta o que a plantacao entrega");
	}

	@Test
	void millGivesBetterThanTheCuttingBoardAndMoreWithLevels() {
		for (int level = 1; level <= 3; level++) {
			Map<String, Integer> s = stock("minecraft:wheat", 16);
			FoodRules.Result r = FoodRules.run(s, FoodRules.mill(level), level, FoodRules.batches("mill", level));
			int flour = s.getOrDefault("mlinnotas:flour", 0);
			assertTrue(flour > 16, "nivel " + level + " rende mais que 1 para 1: " + flour);
			assertEquals(4, r.batches());
			assertFalse(s.containsKey("minecraft:wheat"));
		}
		assertEquals(5, FoodRules.flourPer4(1));
		assertEquals(7, FoodRules.flourPer4(3));
		assertEquals(16, FoodRules.millCapacity(1));
		assertEquals(48, FoodRules.millCapacity(3));
	}

	@Test
	void bakeryBakesWhatTheLevelAllows() {
		Map<String, Integer> s = stock("mlinnotas:flour", 12, "minecraft:cocoa_beans", 2, "minecraft:sugar", 2);
		FoodRules.Result r = FoodRules.run(s, FoodRules.BAKERY, 1, 5);
		assertEquals(4, r.batches(), "nivel I so faz pao: 12 farinhas = 4 lotes");
		assertEquals(16, s.get("minecraft:bread"));
		assertFalse(s.containsKey("minecraft:cookie"), "biscoito e do nivel II");
		Map<String, Integer> s2 = stock("mlinnotas:flour", 2, "minecraft:cocoa_beans", 1, "minecraft:sugar", 1);
		FoodRules.run(s2, FoodRules.BAKERY, 2, 2);
		assertEquals(8, s2.get("minecraft:cookie"));
	}

	@Test
	void cakeReturnsTheBuckets() {
		Map<String, Integer> s = stock("mlinnotas:flour", 3, "minecraft:sugar", 2, "minecraft:egg", 1, "minecraft:milk_bucket", 3);
		FoodRules.Result r = FoodRules.run(s, FoodRules.BAKERY, 3, 1);
		assertEquals(1, r.batches());
		assertEquals(1, s.get("minecraft:cake"));
		assertEquals(3, s.get("minecraft:bucket"));
		assertFalse(s.containsKey("minecraft:milk_bucket"));
		Map<String, Integer> low = stock("mlinnotas:flour", 3, "minecraft:sugar", 2, "minecraft:egg", 1, "minecraft:milk_bucket", 3);
		assertEquals(1, FoodRules.run(low, FoodRules.BAKERY, 1, 5).batches(), "no nivel I sobra so o pao (3 farinhas)");
		assertFalse(low.containsKey("minecraft:cake"));
	}

	@Test
	void wineryAndButcherOnlyAtTheirLevels() {
		Map<String, Integer> s = stock("mlinnotas:grapes", 8, "minecraft:wheat", 8, "minecraft:apple", 8);
		FoodRules.run(s, FoodRules.WINERY, 1, 5);
		assertEquals(6, s.get("mlinnotas:wine"), "2 lotes de 4 uvas, 3 vinhos cada");
		assertFalse(s.containsKey("mlinnotas:beer"));
		assertFalse(s.containsKey("mlinnotas:cider"));
		Map<String, Integer> all = stock("mlinnotas:grapes", 4, "minecraft:wheat", 4, "minecraft:apple", 4);
		FoodRules.run(all, FoodRules.WINERY, 3, 3);
		assertTrue(all.containsKey("mlinnotas:wine") && all.containsKey("mlinnotas:beer") && all.containsKey("mlinnotas:cider"));
		Map<String, Integer> meat = stock("minecraft:cooked_beef", 4);
		assertEquals(0, FoodRules.run(meat, FoodRules.BUTCHER, 1, 4).batches(), "o acougue I nao faz charque");
		FoodRules.run(meat, FoodRules.BUTCHER, 2, 4);
		assertEquals(6, meat.get("mlinnotas:charque"), "2 carnes viram 3 charques");
	}

	@Test
	void runStopsAtTheBatchLimitAndNeverGoesNegative() {
		Map<String, Integer> s = stock("mlinnotas:flour", 30);
		FoodRules.Result r = FoodRules.run(s, FoodRules.BAKERY, 1, 2);
		assertEquals(2, r.batches());
		assertEquals(24, s.get("mlinnotas:flour"));
		Map<String, Integer> empty = new LinkedHashMap<>();
		assertEquals(0, FoodRules.run(empty, FoodRules.BAKERY, 3, 9).batches());
		assertTrue(empty.isEmpty());
	}

	@Test
	void penCapsBreedingAndProduction() {
		assertEquals(4, FoodRules.penCap(1));
		assertEquals(8, FoodRules.penCap(3));
		assertFalse(FoodRules.canBreed(1, 1, 4), "precisa de dois adultos");
		assertTrue(FoodRules.canBreed(2, 2, 4));
		assertFalse(FoodRules.canBreed(4, 4, 4), "no limite nao nasce");
		assertEquals(3, FoodRules.woolOutput(3));
		assertEquals(2, FoodRules.milkOutput(3), "3 vacas = 2 baldes (arredonda para cima)");
		assertEquals(0, FoodRules.milkOutput(0));
	}

	@Test
	void apiaryProducesLessInAutumnAndNothingInWinter() {
		int spring = FoodRules.honeycomb(2, 0);
		int autumn = FoodRules.honeycomb(2, 2);
		assertTrue(spring > autumn);
		assertEquals(0, FoodRules.honeycomb(2, 3), "as abelhas dormem no inverno");
		assertEquals(FoodRules.hives(2) / 2, autumn);
		assertEquals(autumn, FoodRules.honeycomb(2, null), "estacoes desligadas = producao normal");
		assertTrue(FoodRules.hives(2) > FoodRules.hives(1));
	}

	@Test
	void sawmillGivesSixPlanksPerLog() {
		assertEquals(6, FoodRules.planks(1));
		assertEquals(96, FoodRules.planks(16));
		assertTrue(FoodRules.PLANKS_PER_LOG > 4, "mais que o vanilla");
		assertEquals(16, FoodRules.benchLimit(1));
		assertEquals(32, FoodRules.benchLimit(2));
		assertEquals(64, FoodRules.benchLimit(3));
	}

	@Test
	void orchardFruitFollowsTheSeasonAndFarmCropsRotate() {
		assertEquals(2, FoodRules.orchardFruit(1, 0).size());
		assertEquals(4, FoodRules.orchardFruit(3, 3).size());
		assertTrue(FoodRules.orchardFruit(1, 1).contains("minecraft:sweet_berries"));
		assertTrue(FoodRules.orchardFruit(1, 2).contains("mlinnotas:grapes"));
		assertTrue(FoodRules.orchardRegrow(3) < FoodRules.orchardRegrow(1));
		assertEquals(FoodRules.FARM_CROPS.size(), java.util.stream.LongStream.range(0, 4).mapToObj(FoodRules::farmCrop).distinct().count());
		assertEquals(0.5, FoodRules.seasonFactor(3));
		assertEquals(1.0, FoodRules.seasonFactor(null));
		assertEquals(List.of(2, 4, 6), List.of(FoodRules.bakeryTransfer(1), FoodRules.bakeryTransfer(2), FoodRules.bakeryTransfer(3)));
	}
}
