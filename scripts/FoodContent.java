import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cozinha para o ContentGen: modelos/estados das culturas (plantadas e selvagens) e das estacoes, loot, receitas
 * (panela {@code mlinnotas:cooking}, tabua {@code mlinnotas:cutting}, barril {@code mlinnotas:fermenting}, fornos e
 * crafting), worldgen das formas selvagens, tags, trocas do mercador ambulante, capitulo "Cozinha" do livro de quests
 * e todos os textos. A lista de culturas/comidas espelha {@code com.mlin.notas.food.FoodCatalog} (o
 * {@code FoodResourcesTest} confere). Chamado por ContentGen.main depois do VillageContent (mescla as tags da vila).
 */
class FoodContent {
	/** Cultura: nome, nome pt/en do produto, semente separada (ou null), sementes pt/en, habitat (so para o comentario). */
	record Crop(String name, String pt, String en, String seed, String seedPt, String seedEn) {
		String plant() {
			return seed == null ? name : seed;
		}
	}

	static final List<Crop> CROPS = List.of(
			new Crop("lettuce", "Alface", "Lettuce", "lettuce_seeds", "Sementes de Alface", "Lettuce Seeds"),
			new Crop("onion", "Cebola", "Onion", null, null, null),
			new Crop("strawberry", "Morango", "Strawberry", "strawberry_seeds", "Sementes de Morango", "Strawberry Seeds"),
			new Crop("tomato", "Tomate", "Tomato", "tomato_seeds", "Sementes de Tomate", "Tomato Seeds"),
			new Crop("corn", "Milho", "Corn", "corn_seeds", "Sementes de Milho", "Corn Seeds"),
			new Crop("bell_pepper", "Pimentão", "Bell Pepper", "bell_pepper_seeds", "Sementes de Pimentão", "Bell Pepper Seeds"),
			new Crop("rice", "Arroz", "Rice", null, null, null),
			new Crop("beans", "Feijão", "Beans", null, null, null),
			new Crop("garlic", "Alho", "Garlic", null, null, null),
			new Crop("cabbage", "Repolho", "Cabbage", "cabbage_seeds", "Sementes de Repolho", "Cabbage Seeds"));

	/** Comidas e ingredientes que nao sao produto de cultura: id, pt, en. */
	static final String[][] FOODS = {
			{"flour", "Farinha de Trigo", "Wheat Flour"},
			{"cornmeal", "Fubá", "Cornmeal"},
			{"roasted_corn", "Milho Assado", "Roasted Corn"},
			{"popcorn", "Pipoca", "Popcorn"},
			{"corn_bread", "Broa de Milho", "Corn Bread"},
			{"cheese_bread", "Pão de Queijo", "Cheese Bread"},
			{"omelette", "Omelete", "Omelette"},
			{"fresh_cheese", "Queijo Fresco", "Fresh Cheese"},
			{"garden_salad", "Salada da Horta", "Garden Salad"},
			{"fruit_salad", "Salada de Frutas", "Fruit Salad"},
			{"rice_and_beans", "Arroz com Feijão", "Rice and Beans"},
			{"vegetable_soup", "Sopa de Legumes", "Vegetable Soup"},
			{"pumpkin_soup", "Sopa de Abóbora", "Pumpkin Soup"},
			{"beef_stew", "Ensopado de Carne", "Beef Stew"},
			{"galinhada", "Galinhada", "Chicken and Rice"},
			{"pasta", "Macarronada", "Pasta with Tomato Sauce"},
			{"polenta", "Polenta", "Polenta"},
			{"feijoada", "Feijoada", "Feijoada"},
			{"strawberry_pie", "Torta de Morango", "Strawberry Pie"},
			{"corn_cake", "Bolo de Fubá", "Corn Cake"},
			{"strawberry_jam", "Geleia de Morango", "Strawberry Jam"},
			{"tomato_sauce", "Molho de Tomate", "Tomato Sauce"},
			{"pickles", "Conserva de Legumes", "Pickled Vegetables"},
			{"sauerkraut", "Chucrute", "Sauerkraut"},
			{"aged_cheese", "Queijo Curado", "Aged Cheese"},
			{"charque", "Charque", "Jerky"},
			{"dried_fish", "Bacalhau Seco", "Dried Cod"},
	};

	static final List<String> PRESERVES = List.of("strawberry_jam", "tomato_sauce", "pickles", "sauerkraut", "aged_cheese", "charque", "dried_fish");

	static void generate() throws IOException {
		List<String> axe = new ArrayList<>();
		List<String> cropBlocks = new ArrayList<>();
		List<String> seeds = new ArrayList<>();
		List<String> foods = new ArrayList<>();
		for (Crop c : CROPS) {
			crop(c);
			cropBlocks.add("mlinnotas:" + c.name + "_crop");
			seeds.add("mlinnotas:" + c.plant());
			if (c.seed != null) {
				ContentGen.simpleItem(c.seed, "item/generated");
				ContentGen.name("item", c.seed, c.seedPt, c.seedEn);
				// do produto tira-se semente (como melancia), para comecar uma horta com o que veio do mercado
				shapeless(c.seed, List.of("mlinnotas:" + c.name), 2);
			}
			ContentGen.simpleItem(c.name, "item/generated");
			ContentGen.name("item", c.name, c.pt, c.en);
			foods.add("mlinnotas:" + c.name);
		}
		for (String[] f : FOODS) {
			ContentGen.simpleItem(f[0], "item/generated");
			ContentGen.name("item", f[0], f[1], f[2]);
			if (!f[0].equals("flour") && !f[0].equals("cornmeal")) {
				foods.add("mlinnotas:" + f[0]);
			}
		}
		foods.removeAll(List.of("mlinnotas:rice", "mlinnotas:beans", "mlinnotas:garlic"));

		stations(axe);
		recipes();

		// --- tags ---
		ContentGen.tag("block", "minecraft:crops", cropBlocks, false);
		ContentGen.tag("block", "minecraft:maintains_farmland", cropBlocks, false);
		ContentGen.tag("block", "minecraft:bee_attractive", List.of("mlinnotas:strawberry_crop"), false);
		ContentGen.tag("item", "minecraft:villager_plantable_seeds", seeds, false);
		ContentGen.tag("item", "c:seeds", CROPS.stream().filter(c -> c.seed != null).map(c -> "mlinnotas:" + c.seed).toList(), false);
		ContentGen.tag("item", "c:crops", CROPS.stream().map(c -> "mlinnotas:" + c.name).toList(), false);
		ContentGen.tag("item", "c:foods", foods, false);
		ContentGen.tag("item", "mlinnotas:cheese", List.of("mlinnotas:fresh_cheese", "mlinnotas:aged_cheese"), false);
		ContentGen.tag("item", "mlinnotas:cuttable", List.of("minecraft:wheat", "mlinnotas:corn", "minecraft:melon", "minecraft:sugar_cane",
				"minecraft:bone"), false);
		ContentGen.tag("block", "mlinnotas:heat_sources", List.of("minecraft:campfire", "minecraft:soul_campfire", "minecraft:fire",
				"minecraft:soul_fire", "minecraft:magma_block", "minecraft:lava", "minecraft:furnace", "minecraft:smoker",
				"minecraft:blast_furnace"), false);
		ContentGen.tag("block", "minecraft:mineable/axe", axe, false);
		ContentGen.tag("item", "mlinnotas:quest/food_seeds", seeds, false);
		ContentGen.tag("item", "mlinnotas:quest/preserves", PRESERVES.stream().map(p -> "mlinnotas:" + p).toList(), false);
		// vila: comida do projeto Casa/Estalagem e sementes da Plantacao
		ContentGen.tag("item", "mlinnotas:village/food", List.of("mlinnotas:rice_and_beans", "mlinnotas:feijoada", "mlinnotas:vegetable_soup",
				"mlinnotas:corn_bread", "mlinnotas:cheese_bread", "mlinnotas:roasted_corn", "mlinnotas:charque", "mlinnotas:tomato",
				"mlinnotas:corn"), false);
		ContentGen.tag("item", "mlinnotas:village/seeds", seeds, false);

		// --- mercador ambulante: sementes por 1 esmeralda ---
		List<String> trades = new ArrayList<>();
		for (Crop c : CROPS) {
			String id = "emerald_" + c.plant();
			ContentGen.write("data/mlinnotas/villager_trade/wandering_trader/" + id + ".json", "{\"gives\":{\"id\":\"mlinnotas:" + c.plant()
					+ "\",\"count\":" + (c.seed == null ? 3 : 2) + "},\"max_uses\":12,\"reputation_discount\":0.05,\"wants\":{\"id\":\"minecraft:emerald\"}}");
			trades.add("mlinnotas:wandering_trader/" + id);
		}
		ContentGen.tag("villager_trade", "minecraft:wandering_trader/common", trades, false);

		texts();
		quests();
	}

	// --- culturas ---

	static void crop(Crop c) throws IOException {
		String block = c.name + "_crop";
		StringBuilder variants = new StringBuilder();
		for (int age = 0; age <= 7; age++) {
			int stage = age <= 1 ? 0 : age <= 3 ? 1 : age <= 6 ? 2 : 3;
			variants.append(age == 0 ? "" : ",").append("\"age=").append(age).append("\":{\"model\":\"mlinnotas:block/").append(block)
					.append("_stage").append(stage).append("\"}");
		}
		ContentGen.write("assets/mlinnotas/blockstates/" + block + ".json", "{\"variants\":{" + variants + "}}");
		for (int stage = 0; stage < 4; stage++) {
			ContentGen.write("assets/mlinnotas/models/block/" + block + "_stage" + stage + ".json", "{\"parent\":\"minecraft:block/crop\","
					+ "\"textures\":{\"crop\":\"mlinnotas:block/" + block + "_stage" + stage + "\"}}");
		}
		ContentGen.name("block", block, "Plantação de " + c.pt, c.en + " Crop");
		String mature = "{\"type\":\"minecraft:match_block\",\"blocks\":\"mlinnotas:" + block + "\",\"state\":{\"age\":\"7\"}}";
		String fortune = "{\"type\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:fortune\",\"formula\":\"minecraft:binomial_with_bonus_count\","
				+ "\"parameters\":{\"extra\":3,\"probability\":0.5714286}}";
		String loot;
		if (c.seed == null) {
			// planta o proprio produto (como cenoura): 1 sempre, mais 0..3 madura
			loot = "{\"type\":\"minecraft:block\",\"modifier\":{\"type\":\"minecraft:explosion_decay\"},\"pools\":["
					+ "{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + c.name + "\"}]},"
					+ "{\"rolls\":1,\"condition\":" + mature + ",\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + c.name
					+ "\",\"modifier\":[" + fortune + "]}]}],\"random_sequence\":\"mlinnotas:blocks/" + block + "\"}";
		} else {
			// como o trigo: madura da o produto (2 para frutas/folhas) + 0..3 sementes; verde da so a semente
			loot = "{\"type\":\"minecraft:block\",\"modifier\":{\"type\":\"minecraft:explosion_decay\"},\"pools\":["
					+ "{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":["
					+ "{\"type\":\"minecraft:item\",\"condition\":" + mature + ",\"name\":\"mlinnotas:" + c.name + "\",\"modifier\":["
					+ "{\"type\":\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":3}}]},"
					+ "{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + c.seed + "\"}]}]},"
					+ "{\"rolls\":1,\"condition\":" + mature + ",\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + c.seed
					+ "\",\"modifier\":[" + fortune + "]}]}],\"random_sequence\":\"mlinnotas:blocks/" + block + "\"}";
		}
		ContentGen.write("data/mlinnotas/loot_table/blocks/" + block + ".json", loot);

		// forma selvagem
		String wild = "wild_" + c.name;
		ContentGen.write("assets/mlinnotas/blockstates/" + wild + ".json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/" + wild + "\"}}}");
		ContentGen.write("assets/mlinnotas/models/block/" + wild + ".json", "{\"parent\":\"minecraft:block/cross\",\"textures\":{\"cross\":\"mlinnotas:block/"
				+ wild + "\"}}");
		ContentGen.name("block", wild, c.pt + " Selvagem", "Wild " + c.en);
		String drop = c.seed == null
				? "{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + c.name + "\",\"modifier\":[{\"type\":\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":3}}]}"
				: "{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + c.seed + "\",\"modifier\":[{\"type\":\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":2}}]}";
		String extra = c.seed == null ? "" : ",{\"rolls\":1,\"condition\":{\"type\":\"minecraft:random_chance\",\"chance\":0.5},"
				+ "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + c.name + "\"}]}";
		ContentGen.write("data/mlinnotas/loot_table/blocks/" + wild + ".json", "{\"type\":\"minecraft:block\",\"modifier\":{\"type\":"
				+ "\"minecraft:explosion_decay\"},\"pools\":[{\"rolls\":1,\"entries\":[" + drop + "]}" + extra + "],\"random_sequence\":\"mlinnotas:blocks/"
				+ wild + "\"}");
		ContentGen.write("data/mlinnotas/worldgen/feature/" + wild + ".json", "{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"mlinnotas:" + wild + "\"}}");
		ContentGen.write("data/mlinnotas/worldgen/placed_feature/" + wild + ".json", "{\"feature\":\"mlinnotas:" + wild + "\",\"placement\":["
				+ "{\"type\":\"minecraft:rarity_filter\",\"chance\":20},{\"type\":\"minecraft:in_square\"},"
				+ "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},{\"type\":\"minecraft:biome\"},"
				+ "{\"type\":\"minecraft:count\",\"count\":24},"
				+ "{\"type\":\"minecraft:offset\",\"x\":{\"type\":\"minecraft:trapezoid\",\"max\":4,\"min\":-4,\"plateau\":0},"
				+ "\"y\":{\"type\":\"minecraft:trapezoid\",\"max\":2,\"min\":-2,\"plateau\":0},"
				+ "\"z\":{\"type\":\"minecraft:trapezoid\",\"max\":4,\"min\":-4,\"plateau\":0}},"
				+ "{\"type\":\"minecraft:block_predicate_filter\",\"predicate\":{\"type\":\"minecraft:all_of\",\"predicates\":["
				+ "{\"type\":\"minecraft:matching_block_tag\",\"tag\":\"minecraft:air\"},"
				+ "{\"type\":\"minecraft:matching_blocks\",\"blocks\":[\"minecraft:grass_block\",\"minecraft:podzol\",\"minecraft:mud\"],\"offset\":[0,-1,0]}]}}]}");
	}

	// --- estacoes ---

	static void stations(List<String> axe) throws IOException {
		// panela: corpo 10x8x10 com borda, alcas dos lados e tampa entreaberta de cabo
		String pot = "{\"parent\":\"minecraft:block/block\",\"textures\":{\"particle\":\"mlinnotas:block/cooking_pot_side\","
				+ "\"side\":\"mlinnotas:block/cooking_pot_side\",\"top\":\"mlinnotas:block/cooking_pot_top\",\"bottom\":\"mlinnotas:block/cooking_pot_bottom\","
				+ "\"handle\":\"mlinnotas:block/cooking_pot_handle\"},\"elements\":["
				+ box(3, 0, 3, 13, 8, 13, "side", "top", "bottom")
				+ "," + box(1, 6, 7, 3, 7, 9, "handle", "handle", "handle")
				+ "," + box(13, 6, 7, 15, 7, 9, "handle", "handle", "handle")
				+ "]}";
		ContentGen.write("assets/mlinnotas/models/block/cooking_pot.json", pot);
		facingState("cooking_pot", "mlinnotas:block/cooking_pot");
		blockItem("cooking_pot");
		ContentGen.name("block", "cooking_pot", "Panela", "Cooking Pot");
		dropSelf("cooking_pot");
		ContentGen.tag("block", "minecraft:mineable/pickaxe", List.of("mlinnotas:cooking_pot"), false);

		String board = "{\"parent\":\"minecraft:block/block\",\"textures\":{\"particle\":\"mlinnotas:block/cutting_board_top\","
				+ "\"top\":\"mlinnotas:block/cutting_board_top\",\"side\":\"mlinnotas:block/cutting_board_side\"},\"elements\":["
				+ box(2, 0, 1, 14, 1, 15, "side", "top", "top") + "]}";
		ContentGen.write("assets/mlinnotas/models/block/cutting_board.json", board);
		facingState("cutting_board", "mlinnotas:block/cutting_board");
		blockItem("cutting_board");
		ContentGen.name("block", "cutting_board", "Tábua de Corte", "Cutting Board");
		dropSelf("cutting_board");
		axe.add("mlinnotas:cutting_board");

		ContentGen.write("assets/mlinnotas/models/block/preserving_barrel.json", "{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{"
				+ "\"side\":\"mlinnotas:block/preserving_barrel_side\",\"top\":\"mlinnotas:block/preserving_barrel_top\","
				+ "\"bottom\":\"mlinnotas:block/preserving_barrel_bottom\"}}");
		ContentGen.write("assets/mlinnotas/blockstates/preserving_barrel.json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/preserving_barrel\"}}}");
		blockItem("preserving_barrel");
		ContentGen.name("block", "preserving_barrel", "Barril de Conserva", "Preserving Barrel");
		dropSelf("preserving_barrel");
		axe.add("mlinnotas:preserving_barrel");

		ContentGen.shaped("cooking_pot", "B B|I I|III", Map.of("B", "minecraft:brick", "I", "minecraft:iron_ingot"), "misc");
		ContentGen.shaped("cutting_board", "SPP", Map.of("S", "minecraft:stick", "P", "#minecraft:wooden_slabs"), "misc");
		shapeless("preserving_barrel", List.of("minecraft:barrel", "minecraft:copper_ingot", "minecraft:copper_ingot"), 1);
	}

	/** Elemento de modelo com faces: lados, cima e baixo. */
	static String box(int x0, int y0, int z0, int x1, int y1, int z1, String side, String top, String bottom) {
		String sideUv = "[" + x0 + "," + (16 - y1) + "," + x1 + "," + (16 - y0) + "]";
		String sideUvZ = "[" + z0 + "," + (16 - y1) + "," + z1 + "," + (16 - y0) + "]";
		String topUv = "[" + x0 + "," + z0 + "," + x1 + "," + z1 + "]";
		return "{\"from\":[" + x0 + "," + y0 + "," + z0 + "],\"to\":[" + x1 + "," + y1 + "," + z1 + "],\"faces\":{"
				+ "\"north\":{\"uv\":" + sideUv + ",\"texture\":\"#" + side + "\"},"
				+ "\"south\":{\"uv\":" + sideUv + ",\"texture\":\"#" + side + "\"},"
				+ "\"east\":{\"uv\":" + sideUvZ + ",\"texture\":\"#" + side + "\"},"
				+ "\"west\":{\"uv\":" + sideUvZ + ",\"texture\":\"#" + side + "\"},"
				+ "\"up\":{\"uv\":" + topUv + ",\"texture\":\"#" + top + "\"},"
				+ "\"down\":{\"uv\":" + topUv + ",\"texture\":\"#" + bottom + "\",\"cullface\":\"down\"}}}";
	}

	static void facingState(String id, String model) throws IOException {
		ContentGen.write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{"
				+ "\"facing=north\":{\"model\":\"" + model + "\"},"
				+ "\"facing=east\":{\"model\":\"" + model + "\",\"y\":90},"
				+ "\"facing=south\":{\"model\":\"" + model + "\",\"y\":180},"
				+ "\"facing=west\":{\"model\":\"" + model + "\",\"y\":270}}}");
	}

	static void blockItem(String id) throws IOException {
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
	}

	static void dropSelf(String id) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/blocks/" + id + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"condition\":"
				+ "{\"type\":\"minecraft:survives_explosion\"},\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + id + "\"}]}],"
				+ "\"random_sequence\":\"mlinnotas:blocks/" + id + "\"}");
	}

	// --- receitas ---

	static void recipes() throws IOException {
		String bowl = "minecraft:bowl";
		String bottle = "minecraft:glass_bottle";
		// panela: refeicoes fartas
		pot("rice_and_beans", 1, bowl, 0.35, "mlinnotas:rice", "mlinnotas:beans", "mlinnotas:onion", "mlinnotas:garlic");
		pot("feijoada", 1, bowl, 0.5, "mlinnotas:beans", "mlinnotas:beans", "minecraft:porkchop", "mlinnotas:garlic", "mlinnotas:onion");
		pot("vegetable_soup", 1, bowl, 0.35, "mlinnotas:cabbage", "minecraft:carrot", "minecraft:potato", "mlinnotas:onion");
		pot("pumpkin_soup", 1, bowl, 0.35, "minecraft:pumpkin", "mlinnotas:onion", "mlinnotas:garlic");
		pot("beef_stew", 1, bowl, 0.35, "minecraft:beef", "minecraft:potato", "minecraft:carrot", "mlinnotas:onion");
		pot("galinhada", 1, bowl, 0.35, "minecraft:chicken", "mlinnotas:rice", "mlinnotas:bell_pepper", "mlinnotas:garlic");
		pot("pasta", 1, bowl, 0.35, "mlinnotas:flour", "#minecraft:eggs", "mlinnotas:tomato_sauce", "mlinnotas:garlic");
		pot("polenta", 1, bowl, 0.35, "mlinnotas:cornmeal", "mlinnotas:cornmeal", "#mlinnotas:cheese");
		// panela: pratos e assados
		pot("omelette", 1, null, 0.2, "#minecraft:eggs", "#minecraft:eggs", "mlinnotas:onion", "mlinnotas:bell_pepper");
		pot("popcorn", 3, null, 0.1, "mlinnotas:corn");
		pot("cheese_bread", 4, null, 0.2, "mlinnotas:flour", "#mlinnotas:cheese", "#minecraft:eggs", "minecraft:milk_bucket");
		pot("corn_cake", 2, null, 0.2, "mlinnotas:cornmeal", "mlinnotas:cornmeal", "#minecraft:eggs", "minecraft:sugar", "minecraft:milk_bucket");
		pot("fresh_cheese", 2, null, 0.1, "minecraft:milk_bucket");
		// panela: conservas em garrafa
		pot("strawberry_jam", 1, bottle, 0.3, "mlinnotas:strawberry", "mlinnotas:strawberry", "minecraft:sugar");
		pot("tomato_sauce", 1, bottle, 0.3, "mlinnotas:tomato", "mlinnotas:tomato", "mlinnotas:garlic", "mlinnotas:onion");
		pot("pickles", 1, bottle, 0.3, "mlinnotas:bell_pepper", "mlinnotas:onion", "mlinnotas:garlic");

		// crafting
		shapeless("garden_salad", List.of("mlinnotas:lettuce", "mlinnotas:tomato", "mlinnotas:onion", "minecraft:bowl"), 1);
		shapeless("fruit_salad", List.of("mlinnotas:strawberry", "minecraft:apple", "minecraft:melon_slice", "minecraft:sweet_berries", "minecraft:bowl"), 1);
		shapeless("strawberry_pie", List.of("mlinnotas:strawberry", "mlinnotas:strawberry", "minecraft:sugar", "#minecraft:eggs", "mlinnotas:flour"), 1);

		// fornos
		cook("smelting", "corn", "roasted_corn", 200, 0.35);
		cook("smoking", "corn", "roasted_corn", 100, 0.35);
		cook("campfire_cooking", "corn", "roasted_corn", 600, 0.35);
		cook("smelting", "cornmeal", "corn_bread", 200, 0.35);
		cook("smoking", "cornmeal", "corn_bread", 100, 0.35);
		// defumar (so no defumador): carne cozida -> charque, bacalhau cozido -> seco
		ContentGen.write("data/mlinnotas/recipe/charque.json", "{\"type\":\"minecraft:smoking\",\"category\":\"food\",\"cookingtime\":400,"
				+ "\"experience\":0.35,\"ingredient\":\"minecraft:cooked_beef\",\"result\":{\"id\":\"mlinnotas:charque\"}}");
		ContentGen.write("data/mlinnotas/recipe/dried_fish.json", "{\"type\":\"minecraft:smoking\",\"category\":\"food\",\"cookingtime\":400,"
				+ "\"experience\":0.35,\"ingredient\":\"minecraft:cooked_cod\",\"result\":{\"id\":\"mlinnotas:dried_fish\"}}");

		// tabua de corte
		cut("flour", "minecraft:wheat", "mlinnotas:flour", 1);
		cut("cornmeal", "mlinnotas:corn", "mlinnotas:cornmeal", 2);
		cut("melon_slices", "minecraft:melon", "minecraft:melon_slice", 9);
		cut("sugar", "minecraft:sugar_cane", "minecraft:sugar", 2);
		cut("bone_meal", "minecraft:bone", "minecraft:bone_meal", 4);

		// barril de conserva
		ferment("aged_cheese", "mlinnotas:fresh_cheese", "mlinnotas:aged_cheese", 2.0);
		ferment("sauerkraut", "mlinnotas:cabbage", "mlinnotas:sauerkraut", 1.0);
	}

	static String ingredient(String id) {
		return "\"" + id + "\"";
	}

	static void pot(String result, int count, String container, double xp, String... ingredients) throws IOException {
		StringBuilder list = new StringBuilder();
		for (String i : ingredients) {
			list.append(list.isEmpty() ? "" : ",").append(ingredient(i));
		}
		ContentGen.write("data/mlinnotas/recipe/cooking/" + result + ".json", "{\"type\":\"mlinnotas:cooking\",\"ingredients\":[" + list + "],"
				+ (container == null ? "" : "\"container\":\"" + container + "\",") + "\"result\":{\"id\":\"mlinnotas:" + result + "\",\"count\":" + count
				+ "},\"cookingtime\":200,\"experience\":" + xp + "}");
	}

	static void cut(String name, String input, String output, int count) throws IOException {
		ContentGen.write("data/mlinnotas/recipe/cutting/" + name + ".json", "{\"type\":\"mlinnotas:cutting\",\"ingredient\":\"" + input
				+ "\",\"result\":{\"id\":\"" + output + "\",\"count\":" + count + "}}");
	}

	static void ferment(String name, String input, String output, double days) throws IOException {
		ContentGen.write("data/mlinnotas/recipe/fermenting/" + name + ".json", "{\"type\":\"mlinnotas:fermenting\",\"ingredient\":\"" + input
				+ "\",\"result\":{\"id\":\"" + output + "\"},\"days\":" + days + "}");
	}

	static void cook(String type, String input, String output, int time, double xp) throws IOException {
		ContentGen.write("data/mlinnotas/recipe/" + output + "_from_" + type + ".json", "{\"type\":\"minecraft:" + type + "\",\"category\":\"food\","
				+ "\"cookingtime\":" + time + ",\"experience\":" + xp + ",\"ingredient\":\"mlinnotas:" + input + "\",\"result\":{\"id\":\"mlinnotas:"
				+ output + "\"}}");
	}

	static void shapeless(String result, List<String> ingredients, int count) throws IOException {
		ContentGen.write("data/mlinnotas/recipe/" + result + ".json", "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":["
				+ ContentGen.quoteAll(ingredients) + "],\"result\":{\"id\":\"mlinnotas:" + result + "\",\"count\":" + count + "}}");
	}

	// --- textos ---

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}

	static void texts() {
		text("effect.mlinnotas.well_fed", "Bem Alimentado", "Well Fed");
		text("container.mlinnotas.cooking_pot", "Panela", "Cooking Pot");
		text("container.mlinnotas.preserving_barrel", "Barril de Conserva", "Preserving Barrel");
		text("gui.mlinnotas.cooking_pot.heated", "Fogo embaixo: cozinhando", "Heat below: cooking");
		text("gui.mlinnotas.cooking_pot.cold", "Sem calor: ponha a panela sobre fogueira, fogo, magma ou fornalha acesa",
				"No heat: place the pot over a campfire, fire, magma or a lit furnace");
		text("block.mlinnotas.cooking_pot.tooltip", "Sobre uma fonte de calor, cozinha até 6 ingredientes", "Over a heat source, cooks up to 6 ingredients");
		text("block.mlinnotas.cutting_board.tooltip", "Botão direito com o ingrediente para cortar (agachado: a pilha toda)",
				"Right-click with an ingredient to cut it (sneak: the whole stack)");
		text("block.mlinnotas.preserving_barrel.tooltip", "Guarda comida sem estragar e cura queijo e chucrute",
				"Keeps food from spoiling and cures cheese and sauerkraut");
		text("gui.mlinnotas.recipes.kind.cooking_pot", "Panela", "Cooking Pot");
		text("gui.mlinnotas.recipes.kind.cutting", "Tábua de Corte", "Cutting Board");
		text("gui.mlinnotas.recipes.kind.fermenting", "Barril de Conserva", "Preserving Barrel");
		text("gui.mlinnotas.recipes.container", "Recipiente", "Container");
		text("gui.mlinnotas.recipes.days", "%s dias", "%s days");
		text("tooltip.mlinnotas.food.best_season", "Melhor estação: %s", "Best season: %s");
		text("tooltip.mlinnotas.food.winter_hardy", "Resiste ao inverno ao ar livre", "Hardy: keeps growing outdoors in winter");
		text("tooltip.mlinnotas.food.well_fed", "Refeição farta: Bem Alimentado %s (%s)", "Hearty meal: Well Fed %s (%s)");
		text("tooltip.mlinnotas.food.preserve", "Conserva: não estraga", "Preserved: never spoils");
		text("tooltip.mlinnotas.food.perishable", "Perecível: no Barril de Conserva não estraga", "Perishable: keeps fresh in a Preserving Barrel");
		text("tooltip.mlinnotas.food.freshness.fresh", "Fresco", "Fresh");
		text("tooltip.mlinnotas.food.freshness.aging", "Murchando: rende 75%", "Wilting: 75% nourishment");
		text("tooltip.mlinnotas.food.freshness.stale", "Passado: rende 50%", "Stale: 50% nourishment");
		text("tooltip.mlinnotas.food.in_season", "Da estação: +%s de fome", "In season: +%s food");
		text("tooltip.mlinnotas.food.repetitive", "Repetitivo: %s das últimas %s refeições (-%s%%)", "Repetitive: %s of your last %s meals (-%s%%)");

		// mercado da vila
		shop("tomato_seeds", "4 Sementes de Tomate", "4 Tomato Seeds", "Para a horta de verão.", "For the summer garden.");
		shop("cabbage_seeds", "4 Sementes de Repolho", "4 Cabbage Seeds", "O repolho aguenta até o inverno.", "Cabbage holds out even in winter.");
		shop("cooking_pot", "Panela", "Cooking Pot", "Ponha sobre uma fogueira e cozinhe pratos fartos.", "Set it over a campfire and cook hearty meals.");
		shop("feijoada", "2 Feijoadas", "2 Feijoadas", "Banquete da estalagem: Bem Alimentado II.", "The inn's feast: Well Fed II.");
	}

	static void shop(String id, String pt, String en, String descPt, String descEn) {
		text("village.mlinnotas.shop." + id, pt, en);
		text("village.mlinnotas.shop." + id + ".desc", descPt, descEn);
	}

	// --- livro de quests: capitulo Cozinha ---

	static void quests() throws IOException {
		QuestContent.chapter("kitchen", 7, "mlinnotas:cooking_pot", "Cozinha", "Kitchen",
				QuestContent.q("garden", "mlinnotas:tomato_seeds", 0, 0, "Horta Nova", "New Garden",
						"Sementes da horta caem da grama, aparecem nos baús das vilas e com o mercador ambulante, e as culturas crescem selvagens pelos biomas. Cada uma tem sua estação: veja na dica do item.",
						"Garden seeds drop from grass, show up in village chests and with the wandering trader, and crops grow wild across biomes. Each has its season: check the item tooltip.")
						.dep("basics/welcome").haveTag("mlinnotas:quest/food_seeds", 1, "mlinnotas:tomato_seeds", "food_seeds", "Tenha uma semente da horta",
								"Have a garden seed").item("minecraft:bone_meal", 8).xp(20),
				QuestContent.q("cooking_pot", "mlinnotas:cooking_pot", 1, 0, "Panela no Fogo", "Pot on the Fire",
						"A panela cozinha sobre fogueira, fogo, magma ou fornalha acesa. Ponha até 6 ingredientes (um por espaço) e a tigela ou garrafa no espaço do recipiente.",
						"The pot cooks over a campfire, fire, magma or a lit furnace. Put up to 6 ingredients (one per slot) and the bowl or bottle in the container slot.")
						.dep("garden").have("mlinnotas:cooking_pot", 1).item("minecraft:bowl", 8).xp(30),
				QuestContent.q("flour", "mlinnotas:flour", 1, 1, "Tábua de Corte", "Cutting Board",
						"Botão direito na tábua com trigo dá farinha; milho vira fubá. Agachado corta a pilha inteira.",
						"Right-click the board with wheat for flour; corn becomes cornmeal. Sneak to cut the whole stack.")
						.dep("garden").have("mlinnotas:flour", 8).item("minecraft:egg", 4).xp(20),
				QuestContent.q("rice_and_beans", "mlinnotas:rice_and_beans", 2, 0, "Arroz com Feijão", "Rice and Beans",
						"Refeição farta: dá Bem Alimentado (um coração a mais e cura lenta). Arroz é de verão; feijão rende mais no outono.",
						"A hearty meal: gives Well Fed (an extra heart and slow healing). Rice is a summer crop; beans do best in autumn.")
						.dep("cooking_pot").have("mlinnotas:rice_and_beans", 1).item("minecraft:emerald", 2).xp(40),
				QuestContent.q("feijoada", "mlinnotas:feijoada", 3, 0, "Feijoada Completa", "Full Feijoada",
						"O banquete da casa: Bem Alimentado II por 5 minutos. Variar o cardápio rende mais: repetir o mesmo prato muitas vezes sacia menos.",
						"The house feast: Well Fed II for 5 minutes. A varied menu goes further: eating the same dish over and over fills you less.")
						.dep("rice_and_beans").have("mlinnotas:feijoada", 1).item("minecraft:emerald", 4).xp(60),
				QuestContent.q("preserves", "mlinnotas:strawberry_jam", 2, 1, "Despensa", "Pantry",
						"Horta fresca murcha depois de duas estações. Conservas (geleia, molho de tomate, picles, charque, bacalhau seco) nunca estragam: faça na colheita farta para o inverno.",
						"Fresh produce wilts after two seasons. Preserves (jam, tomato sauce, pickles, jerky, dried cod) never spoil: make them at harvest time for winter.")
						.dep("cooking_pot").haveTag("mlinnotas:quest/preserves", 4, "mlinnotas:strawberry_jam", "preserves", "Tenha 4 conservas",
								"Have 4 preserves").item("minecraft:glass_bottle", 8).xp(40),
				QuestContent.q("barrel", "mlinnotas:preserving_barrel", 3, 1, "Barril de Conserva", "Preserving Barrel",
						"Dentro do barril a horta não envelhece. E com o tempo ele cura: queijo fresco vira curado (2 dias) e repolho vira chucrute (1 dia).",
						"Produce doesn't age inside the barrel. Given time it also cures: fresh cheese becomes aged (2 days) and cabbage becomes sauerkraut (1 day).")
						.dep("preserves").have("mlinnotas:preserving_barrel", 1).xp(40),
				QuestContent.q("aged_cheese", "mlinnotas:aged_cheese", 4, 1, "Queijo Curado", "Aged Cheese",
						"Leite na panela dá queijo fresco; dois dias no barril e ele vira queijo curado, que não estraga.",
						"Milk in the pot makes fresh cheese; two days in the barrel turn it into aged cheese, which never spoils.")
						.dep("barrel").have("mlinnotas:aged_cheese", 1).item("minecraft:emerald", 3).xp(60),
				QuestContent.q("winter_pantry", "mlinnotas:pickles", 5, 0.5, "Inverno Farto", "Bountiful Winter",
						"No inverno quase nada cresce ao ar livre (só o repolho, devagar; estufas de vidro seguem normais). Chegue lá com a despensa cheia.",
						"In winter almost nothing grows outdoors (only cabbage, slowly; glass greenhouses grow normally). Get there with a full pantry.")
						.dep("feijoada", "aged_cheese").haveTag("mlinnotas:quest/preserves", 16, "mlinnotas:pickles", "preserves_16",
								"Tenha 16 conservas", "Have 16 preserves").item("minecraft:golden_carrot", 8).xp(120));
	}
}
