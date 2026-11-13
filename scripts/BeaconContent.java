import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conteudo do sinalizador por nivel e do minerio de fim de jogo para o ContentGen: blocos de metal e de minerio
 * bruto (modelos, receitas 9 <-> 1, loot, requisito de picareta), tags do sinalizador (base e pagamento), tags de
 * convencao (c:ores, c:raw_materials) usadas pelo Garimpo e pela fundicao dupla, veios ricos e textos (lang).
 * Chamado por ContentGen.main (o launcher do Java compila este arquivo junto).
 */
class BeaconContent {
	// metal, nome pt, nome en, tag de requisito de picareta, tem minerio bruto
	static final String[][] METALS = {
			{"cobalt", "Cobalto", "Cobalt", "#minecraft:needs_iron_tool", "raw"},
			{"infernite", "Infernita", "Infernite", "#mlinnotas:needs_netherite_tool", "raw"},
			{"endite", "Endita", "Endite", "#mlinnotas:needs_infernite_tool", "raw"},
			{"astralite", "Astralita", "Astralite", "#mlinnotas:needs_endite_tool", "raw"},
			{"celestium", "Celéstio", "Celestium", "#mlinnotas:needs_astralite_tool", ""},
	};

	static void generate(List<String> pickaxeMineable) throws IOException {
		List<String> beaconBase = new ArrayList<>(List.of("minecraft:iron_block", "minecraft:gold_block", "minecraft:diamond_block",
				"minecraft:emerald_block", "minecraft:netherite_block"));
		List<String> payment = new ArrayList<>();
		List<String> rawItems = new ArrayList<>();
		for (String[] m : METALS) {
			String id = m[0];
			String block = id + "_block";
			cubeBlock(block, "Bloco de " + m[1], "Block of " + m[2]);
			ContentGen.shaped(block, "###|###|###", Map.of("#", "mlinnotas:" + id + "_ingot"), "building");
			shapeless(id + "_ingot_from_" + block, "mlinnotas:" + block, "mlinnotas:" + id + "_ingot", 9);
			pickaxeMineable.add("mlinnotas:" + block);
			needs(m[3], "mlinnotas:" + block);
			beaconBase.add("mlinnotas:" + block);
			payment.add("mlinnotas:" + id + "_ingot");
			if (m[4].equals("raw")) {
				String raw = "raw_" + id + "_block";
				cubeBlock(raw, "Bloco de " + m[1] + " Bruto", "Block of Raw " + m[2]);
				ContentGen.shaped(raw, "###|###|###", Map.of("#", "mlinnotas:raw_" + id), "building");
				shapeless("raw_" + id + "_from_" + raw, "mlinnotas:" + raw, "mlinnotas:raw_" + id, 9);
				pickaxeMineable.add("mlinnotas:" + raw);
				needs(m[3], "mlinnotas:" + raw);
				rawItems.add("mlinnotas:raw_" + id);
			}
		}
		ContentGen.tag("block", "minecraft:beacon_base_blocks", beaconBase, false);
		ContentGen.tag("item", "minecraft:beacon_payment_items", payment, false);

		// convencao: os minerios e brutos do mod entram em c:ores / c:raw_materials (Garimpo e fundicao dupla usam essas tags)
		List<String> ores = List.of("mlinnotas:cobalt_ore", "mlinnotas:deepslate_cobalt_ore", "mlinnotas:infernite_ore", "mlinnotas:endite_ore",
				"mlinnotas:astralite_ore");
		ContentGen.tag("block", "c:ores", ores, false);
		ContentGen.tag("item", "c:ores", ores, false);
		ContentGen.tag("item", "c:raw_materials", rawItems, false);
		// entradas que a fornalha de endita+ dobra
		ContentGen.tag("item", "mlinnotas:doubled_by_furnace", List.of("#c:ores", "#c:raw_materials"), false);
		// minerios que o Garimpo multiplica
		ContentGen.tag("block", "mlinnotas:prospecting_ores", List.of("#c:ores"), false);

		// --- veios ricos: blobs grandes e raros de cada minerio novo na dimensao do nivel ---
		String stone = "{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"}";
		String deepslate = "{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:deepslate_ore_replaceables\"}";
		String netherrack = "{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:netherrack\"}";
		String peaksStone = "{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"mlinnotas:infernite_peaks_stone\"}";
		String endStone = "{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:end_stone\"}";
		richVein("ore_cobalt_rich", 48, "{\"state\":\"mlinnotas:cobalt_ore\",\"target\":" + stone + "},{\"state\":\"mlinnotas:deepslate_cobalt_ore\",\"target\":"
				+ deepslate + "}", 14, uniform("{\"above_bottom\":8}", "{\"absolute\":0}"));
		richVein("ore_infernite_rich", 44, "{\"state\":\"mlinnotas:infernite_ore\",\"target\":" + netherrack + "}", 20,
				uniform("{\"above_bottom\":10}", "{\"below_top\":10}"));
		richVein("ore_infernite_rich_peaks", 52, "{\"state\":\"mlinnotas:infernite_ore\",\"target\":" + peaksStone + "}", 5,
				uniform("{\"absolute\":32}", "{\"absolute\":100}"));
		richVein("ore_endite_rich", 48, "{\"state\":\"mlinnotas:endite_ore\",\"target\":" + endStone + "}", 10,
				uniform("{\"absolute\":8}", "{\"absolute\":96}"));
		richVein("ore_astralite_rich", 40, "{\"state\":\"mlinnotas:astralite_ore\",\"target\":" + endStone + "}", 14,
				uniform("{\"absolute\":8}", "{\"absolute\":96}"));

		// --- efeitos novos e tela do sinalizador ---
		ContentGen.PT.put("effect.mlinnotas.prospecting", "Garimpo");
		ContentGen.EN.put("effect.mlinnotas.prospecting", "Prospecting");
		ContentGen.PT.put("effect.mlinnotas.beacon_flight", "Voo");
		ContentGen.EN.put("effect.mlinnotas.beacon_flight", "Flight");
		ContentGen.PT.put("gui.mlinnotas.beacon.tier", "Pirâmide de %s · alcance %s blocos");
		ContentGen.EN.put("gui.mlinnotas.beacon.tier", "%s pyramid · range %s blocks");
		ContentGen.PT.put("gui.mlinnotas.beacon.no_pyramid", "Sem pirâmide");
		ContentGen.EN.put("gui.mlinnotas.beacon.no_pyramid", "No pyramid");
		ContentGen.PT.put("gui.mlinnotas.beacon.level_three", "Nível III: %s");
		ContentGen.EN.put("gui.mlinnotas.beacon.level_three", "Level III: %s");
		ContentGen.PT.put("gui.mlinnotas.beacon.prospecting", "+50%% de chance de um minério extra ao minerar");
		ContentGen.EN.put("gui.mlinnotas.beacon.prospecting", "+50%% chance of an extra ore drop when mining");
		ContentGen.PT.put("gui.mlinnotas.beacon.flight", "Voar como no criativo dentro do alcance");
		ContentGen.EN.put("gui.mlinnotas.beacon.flight", "Creative-style flight inside the range");
		ContentGen.PT.put("gui.mlinnotas.beacon.requires", "Requer pirâmide de %s (4 camadas)");
		ContentGen.EN.put("gui.mlinnotas.beacon.requires", "Requires a %s pyramid (4 levels)");
		String[][] metals = {{"iron", "Ferro", "Iron"}, {"gold", "Ouro", "Gold"}, {"cobalt", "Cobalto", "Cobalt"}, {"diamond", "Diamante", "Diamond"},
				{"netherite", "Netherite", "Netherite"}, {"infernite", "Infernita", "Infernite"}, {"endite", "Endita", "Endite"},
				{"astralite", "Astralita", "Astralite"}, {"celestium", "Celéstio", "Celestium"}};
		for (String[] metal : metals) {
			ContentGen.PT.put("gui.mlinnotas.beacon.metal." + metal[0], metal[1]);
			ContentGen.EN.put("gui.mlinnotas.beacon.metal." + metal[0], metal[2]);
		}
		ContentGen.PT.put("message.mlinnotas.beacon_flight.lost", "Voo do sinalizador perdido");
		ContentGen.EN.put("message.mlinnotas.beacon_flight.lost", "Beacon flight lost");
	}

	static String uniform(String min, String max) {
		return "{\"type\":\"minecraft:uniform\",\"max_inclusive\":" + max + ",\"min_inclusive\":" + min + "}";
	}

	static void richVein(String id, int size, String targets, int rarity, String height) throws IOException {
		ContentGen.write("data/mlinnotas/worldgen/feature/" + id + ".json", "{\"type\":\"minecraft:ore\",\"discard_chance_on_air_exposure\":0.0,"
				+ "\"size\":" + size + ",\"targets\":[" + targets + "]}");
		ContentGen.write("data/mlinnotas/worldgen/placed_feature/" + id + ".json", "{\"feature\":\"mlinnotas:" + id + "\",\"placement\":["
				+ "{\"type\":\"minecraft:rarity_filter\",\"chance\":" + rarity + "},{\"type\":\"minecraft:in_square\"},"
				+ "{\"type\":\"minecraft:height_range\",\"height\":" + height + "},{\"type\":\"minecraft:biome\"}]}");
	}

	static void needs(String tag, String block) throws IOException {
		ContentGen.tag("block", tag.substring(1), List.of(block), false);
	}

	static void cubeBlock(String id, String pt, String en) throws IOException {
		ContentGen.write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/" + id + "\"}}}");
		ContentGen.write("assets/mlinnotas/models/block/" + id + ".json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mlinnotas:block/"
				+ id + "\"}}");
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
		ContentGen.name("block", id, pt, en);
		ContentGen.write("data/mlinnotas/loot_table/blocks/" + id + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"bonus_rolls\":0.0,"
				+ "\"condition\":{\"type\":\"minecraft:survives_explosion\"},\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + id + "\"}],"
				+ "\"rolls\":1.0}],\"random_sequence\":\"mlinnotas:blocks/" + id + "\"}");
	}

	static void shapeless(String recipeId, String input, String result, int count) throws IOException {
		ContentGen.write("data/mlinnotas/recipe/" + recipeId + ".json", "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":[\""
				+ input + "\"],\"result\":{\"count\":" + count + ",\"id\":\"" + result + "\"}}");
	}
}
