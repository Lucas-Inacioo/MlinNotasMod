package com.mlin.notas.food;

import com.mlin.notas.harvest.HarvestRules;
import com.mlin.notas.season.CropSeasons;
import com.mlin.notas.season.Season;
import com.mlin.notas.season.SeasonClimate;
import com.mlin.notas.season.SeasonDate;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodCatalogTest {
	private static SeasonDate mid(Season season) {
		return new SeasonDate(season, Season.Phase.MID, 3, 8);
	}

	@Test
	void everyCropIsSeasonalAndHarvestable() {
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			String block = "mlinnotas:" + crop.blockName();
			assertNotNull(CropSeasons.profile(block), block);
			assertTrue(HarvestRules.isHarvestable(block), "colheita com botao direito: " + block);
			assertEquals("mlinnotas:" + crop.seed(), HarvestRules.seedFor(block));
		}
	}

	@Test
	void cropsSpreadOverTheYear() {
		Map<String, Season> expected = new java.util.HashMap<>(Map.of("lettuce", Season.SPRING, "onion", Season.SPRING, "strawberry", Season.SPRING,
				"tomato", Season.SUMMER, "corn", Season.SUMMER, "bell_pepper", Season.SUMMER, "rice", Season.SUMMER,
				"beans", Season.AUTUMN, "garlic", Season.AUTUMN, "cabbage", Season.AUTUMN));
		expected.put("grapes", Season.AUTUMN);
		assertEquals(expected.size(), FoodCatalog.CROPS.size());
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			assertEquals(expected.get(crop.name()), crop.bestSeason(), crop.name());
			float best = CropSeasons.seasonal(crop.profile(), mid(crop.bestSeason()), SeasonClimate.TEMPERATE);
			assertTrue(FoodRules.inSeason(best), crop.name() + " no auge na sua estacao");
		}
	}

	@Test
	void winterOutdoorsOnlyCabbageAndGarlicKeepGoing() {
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			float winter = CropSeasons.seasonal(crop.profile(), mid(Season.WINTER), SeasonClimate.TEMPERATE);
			boolean hardy = crop.name().equals("cabbage") || crop.name().equals("garlic");
			assertEquals(hardy, FoodCatalog.winterHardy(crop.profile()), crop.name());
			assertTrue(hardy ? winter >= 0.3F : winter <= 0.15F, crop.name() + " inverno " + winter);
			// estufa sempre salva
			assertEquals(1.0F, CropSeasons.multiplier(crop.profile(), mid(Season.WINTER), SeasonClimate.TEMPERATE, true), 1e-6);
		}
		// repolho e o mais resistente
		assertTrue(FoodCatalog.CABBAGE.winter() > FoodCatalog.GARLIC.winter());
	}

	@Test
	void foodsAreUniqueFocusedAndConsistent() {
		Set<String> ids = new HashSet<>();
		int edible = 0;
		for (FoodCatalog.Food food : FoodCatalog.FOODS) {
			assertTrue(ids.add(food.id()), "repetido: " + food.id());
			if (food.edible()) {
				edible++;
				assertTrue(food.nutrition() > 0 && food.nutrition() <= 14, food.id());
				assertTrue(food.saturation() > 0 && food.saturation() <= 1.0F, food.id());
			}
			if (food.kind() == FoodCatalog.Kind.MEAL || food.kind() == FoodCatalog.Kind.FEAST || food.kind() == FoodCatalog.Kind.SALAD) {
				assertEquals(FoodCatalog.Container.BOWL, food.container(), food.id());
				assertTrue(food.stack() <= 16, food.id());
			}
			if (food.kind() == FoodCatalog.Kind.PRESERVE) {
				assertTrue(!food.perishable(), "conserva nao estraga: " + food.id());
			}
			if (food.perishable()) {
				assertTrue(List.of(FoodCatalog.Kind.PRODUCE, FoodCatalog.Kind.SALAD, FoodCatalog.Kind.SNACK).contains(food.kind()), food.id());
			}
		}
		assertTrue(edible >= 25 && edible <= 40, "comidas: " + edible);
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			assertNotNull(FoodCatalog.food(crop.produce()), "produto no catalogo: " + crop.produce());
		}
		// banquete e o prato mais farto
		int max = FoodCatalog.FOODS.stream().mapToInt(FoodCatalog.Food::nutrition).max().orElse(0);
		assertEquals(max, FoodCatalog.food("feijoada").nutrition());
	}

	@Test
	void produceMapsToItsCrop() {
		assertEquals("mlinnotas:tomato_crop", FoodCatalog.cropBlockOfProduce("mlinnotas:tomato"));
		assertEquals("minecraft:carrots", FoodCatalog.cropBlockOfProduce("minecraft:carrot"));
		assertEquals("mlinnotas:corn_crop", FoodCatalog.cropBlockOfItem("mlinnotas:corn_seeds"));
		assertEquals("minecraft:wheat", FoodCatalog.cropBlockOfItem("minecraft:wheat_seeds"));
		assertNull(FoodCatalog.cropBlockOfProduce("minecraft:bread"));
		assertNull(FoodCatalog.cropBlockOfItem("mlinnotas:feijoada"));
	}
}
