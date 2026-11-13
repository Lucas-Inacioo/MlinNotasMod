package com.mlin.notas.food;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodRulesTest {
	@Test
	void varietyIsLenientUntilTheThirdRepeat() {
		assertEquals(1.0F, FoodRules.varietyMultiplier(0));
		assertEquals(1.0F, FoodRules.varietyMultiplier(2));
		assertEquals(0.75F, FoodRules.varietyMultiplier(3));
		assertEquals(0.75F, FoodRules.varietyMultiplier(4));
		assertEquals(0.5F, FoodRules.varietyMultiplier(5));
		assertEquals(0.5F, FoodRules.varietyMultiplier(8));
	}

	@Test
	void historyKeepsOnlyTheWindowAndEatingOtherThingsRecovers() {
		List<String> history = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			history = FoodRules.remember(history, "minecraft:bread");
		}
		assertEquals(FoodRules.VARIETY_WINDOW, history.size());
		assertEquals(0.5F, FoodRules.varietyMultiplier(FoodRules.timesEaten(history, "minecraft:bread")));
		// quatro refeicoes diferentes ja tiram o pao da faixa pior
		for (String other : new String[] {"a", "b", "c", "d"}) {
			history = FoodRules.remember(history, other);
		}
		assertEquals(4, FoodRules.timesEaten(history, "minecraft:bread"));
		assertEquals(0.75F, FoodRules.varietyMultiplier(4));
		for (String other : new String[] {"e", "f"}) {
			history = FoodRules.remember(history, other);
		}
		assertEquals(1.0F, FoodRules.varietyMultiplier(FoodRules.timesEaten(history, "minecraft:bread")));
	}

	@Test
	void freshnessCountsWholeSeasons() {
		assertEquals(FoodRules.Freshness.FRESH, FoodRules.freshness(10, 10));
		assertEquals(FoodRules.Freshness.FRESH, FoodRules.freshness(10, 11), "colhido no verao ainda e fresco no outono");
		assertEquals(FoodRules.Freshness.AGING, FoodRules.freshness(10, 12));
		assertEquals(FoodRules.Freshness.STALE, FoodRules.freshness(10, 13));
		assertEquals(FoodRules.Freshness.STALE, FoodRules.freshness(10, 40));
		assertEquals(FoodRules.Freshness.FRESH, FoodRules.freshness(10, 5), "relogio voltou: nunca pior que fresco");
	}

	@Test
	void portionScalesAndNeverDropsBelowOne() {
		assertEquals(new FoodRules.Portion(10, 14.0F), FoodRules.adjust(10, 14.0F, 1.0F, false));
		assertEquals(new FoodRules.Portion(5, 7.0F), FoodRules.adjust(10, 14.0F, 0.5F, false));
		assertEquals(1, FoodRules.adjust(1, 0.6F, 0.5F, false).nutrition());
		assertEquals(0, FoodRules.adjust(0, 0.0F, 1.0F, false).nutrition());
		FoodRules.Portion bonus = FoodRules.adjust(3, 1.8F, 1.0F, true);
		assertEquals(3 + FoodRules.IN_SEASON_NUTRITION, bonus.nutrition());
		assertEquals(1.8F + FoodRules.IN_SEASON_SATURATION, bonus.saturation(), 1e-6);
	}

	@Test
	void seasonalBonusNeedsPeakAndThisSeasonsHarvest() {
		assertTrue(FoodRules.seasonalBonus(1.4F, 7, 7));
		assertFalse(FoodRules.seasonalBonus(1.4F, 6, 7), "colhido na estacao passada");
		assertFalse(FoodRules.seasonalBonus(1.0F, 7, 7), "fora do auge");
		assertTrue(FoodRules.inSeason(FoodRules.IN_SEASON_THRESHOLD));
	}

	@Test
	void wellFedIsModestAndOnlyForMeals() {
		assertEquals(0, FoodRules.wellFedTicks(FoodCatalog.Kind.SNACK));
		assertEquals(0, FoodRules.wellFedTicks(FoodCatalog.Kind.PRESERVE));
		assertTrue(FoodRules.wellFedTicks(FoodCatalog.Kind.MEAL) > 0);
		assertTrue(FoodRules.wellFedTicks(FoodCatalog.Kind.FEAST) > FoodRules.wellFedTicks(FoodCatalog.Kind.MEAL));
		assertEquals(0, FoodRules.wellFedAmplifier(FoodCatalog.Kind.MEAL));
		assertEquals(1, FoodRules.wellFedAmplifier(FoodCatalog.Kind.FEAST));
		assertTrue(FoodRules.WELL_FED_MAX_HEALTH_PER_LEVEL <= 2.0, "no maximo um coracao por nivel");
		assertTrue(FoodRules.wellFedRegenInterval(1) < FoodRules.wellFedRegenInterval(0));
		assertTrue(FoodRules.wellFedRegenInterval(1) >= 100, "cura lenta (regeneracao vanilla I = 50 ticks)");
		assertTrue(FoodRules.wellFedTicks(FoodCatalog.Kind.FEAST) <= 10 * 60 * 20);
	}

	@Test
	void fermentTicksFollowDays() {
		assertEquals(48000, FoodRules.fermentTicks(2.0F));
		assertEquals(12000, FoodRules.fermentTicks(0.5F));
		assertEquals(1, FoodRules.fermentTicks(0.0F));
	}
}
