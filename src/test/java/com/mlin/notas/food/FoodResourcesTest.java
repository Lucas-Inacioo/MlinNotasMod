package com.mlin.notas.food;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere que o catalogo da cozinha tem tudo nos recursos: modelo, textura e nome (pt_br/en_us) de cada item, estados
 * e texturas dos 4 estagios de cada cultura, forma selvagem com worldgen e loot, e uma receita para cada prato. Se
 * falhar: rode {@code java scripts/ContentGen.java src/main/resources} e o {@code scripts/FoodTexGen.java}.
 */
class FoodResourcesTest {
	private static final Path RES = Path.of("src/main/resources");
	private static final Path ASSETS = RES.resolve("assets/mlinnotas");
	private static final Path DATA = RES.resolve("data/mlinnotas");

	private static List<String> items() {
		List<String> ids = new ArrayList<>();
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			if (crop.hasSeparateSeed()) {
				ids.add(crop.seed());
			}
		}
		FoodCatalog.FOODS.forEach(f -> ids.add(f.id()));
		return ids;
	}

	@Test
	void everyItemHasModelTextureAndNames() throws IOException {
		String pt = Files.readString(ASSETS.resolve("lang/pt_br.json"), StandardCharsets.UTF_8);
		String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8);
		List<String> missing = new ArrayList<>();
		for (String id : items()) {
			for (Path p : List.of(ASSETS.resolve("items/" + id + ".json"), ASSETS.resolve("models/item/" + id + ".json"),
					ASSETS.resolve("textures/item/" + id + ".png"))) {
				if (!Files.exists(p)) {
					missing.add(p.toString());
				}
			}
			if (!pt.contains("\"item.mlinnotas." + id + "\"") || !en.contains("\"item.mlinnotas." + id + "\"")) {
				missing.add("lang " + id);
			}
		}
		for (String station : List.of("cooking_pot", "cutting_board", "preserving_barrel")) {
			for (Path p : List.of(ASSETS.resolve("blockstates/" + station + ".json"), ASSETS.resolve("models/block/" + station + ".json"),
					ASSETS.resolve("items/" + station + ".json"), DATA.resolve("loot_table/blocks/" + station + ".json"),
					DATA.resolve("recipe/" + station + ".json"))) {
				if (!Files.exists(p)) {
					missing.add(p.toString());
				}
			}
			if (!pt.contains("\"block.mlinnotas." + station + ".tooltip\"")) {
				missing.add("dica " + station);
			}
		}
		assertTrue(pt.contains("\"effect.mlinnotas.well_fed\"") && en.contains("\"effect.mlinnotas.well_fed\""));
		assertTrue(Files.exists(ASSETS.resolve("textures/mob_effect/well_fed.png")));
		assertEquals(List.of(), missing);
	}

	@Test
	void everyCropHasStagesWildFormAndWorldgen() {
		List<String> missing = new ArrayList<>();
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			List<Path> paths = new ArrayList<>(List.of(ASSETS.resolve("blockstates/" + crop.blockName() + ".json"),
					DATA.resolve("loot_table/blocks/" + crop.blockName() + ".json"),
					ASSETS.resolve("blockstates/" + crop.wildName() + ".json"), ASSETS.resolve("textures/block/" + crop.wildName() + ".png"),
					DATA.resolve("loot_table/blocks/" + crop.wildName() + ".json"),
					DATA.resolve("worldgen/feature/" + crop.wildName() + ".json"),
					DATA.resolve("worldgen/placed_feature/" + crop.wildName() + ".json")));
			for (int stage = 0; stage < 4; stage++) {
				paths.add(ASSETS.resolve("textures/block/" + crop.blockName() + "_stage" + stage + ".png"));
				paths.add(ASSETS.resolve("models/block/" + crop.blockName() + "_stage" + stage + ".json"));
			}
			paths.stream().filter(p -> !Files.exists(p)).forEach(p -> missing.add(p.toString()));
		}
		assertEquals(List.of(), missing);
	}

	@Test
	void everyDishHasARecipe() throws IOException {
		StringBuilder all = new StringBuilder();
		try (Stream<Path> files = Files.walk(DATA.resolve("recipe"))) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				all.append(Files.readString(p, StandardCharsets.UTF_8));
			}
		}
		String recipes = all.toString().replaceAll("\\s", "");
		List<String> missing = new ArrayList<>();
		for (FoodCatalog.Food food : FoodCatalog.FOODS) {
			boolean fromCrop = FoodCatalog.CROPS.stream().anyMatch(c -> c.produce().equals(food.id()));
			if (!fromCrop && !recipes.contains("\"result\":{\"id\":\"mlinnotas:" + food.id() + "\"")) {
				missing.add(food.id());
			}
		}
		assertEquals(List.of(), missing);
		assertTrue(recipes.contains("\"type\":\"mlinnotas:cooking\""));
		assertTrue(recipes.contains("\"type\":\"mlinnotas:cutting\""));
		assertTrue(recipes.contains("\"type\":\"mlinnotas:fermenting\""));
	}
}
