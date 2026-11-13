import java.io.IOException;
import java.util.Map;

/**
 * Conteudo das bussolas de bioma e de estrutura, da luz dinamica e da queda rapida de folhas para o ContentGen:
 * definicoes de item com a agulha (32 quadros, mesmo esquema da bussola vanilla), receitas, nomes das estruturas
 * mostrados na lista e textos. Chamado por ContentGen.main. Texturas: scripts/CompassTexGen.java.
 */
class CompassContent {
	static void generate() throws IOException {
		for (String id : new String[] {"biome_compass", "structure_compass"}) {
			compassItem(id);
		}
		ContentGen.name("item", "biome_compass", "Bússola de Biomas", "Biome Compass");
		ContentGen.name("item", "structure_compass", "Bússola de Estruturas", "Structure Compass");
		text("item.mlinnotas.biome_compass.tooltip", "Botão direito: escolha um bioma desta dimensão e a agulha aponta para o mais perto",
				"Right-click: pick a biome of this dimension and the needle points to the nearest one");
		text("item.mlinnotas.structure_compass.tooltip", "Botão direito: escolha uma estrutura desta dimensão e a agulha aponta para a mais perto",
				"Right-click: pick a structure of this dimension and the needle points to the nearest one");
		ContentGen.shaped("biome_compass", "LSL|SCS|LSL", Map.of("L", "#minecraft:logs", "S", "#minecraft:saplings", "C", "minecraft:compass"),
				"equipment");
		ContentGen.shaped("structure_compass", "BIB|ICI|BIB", Map.of("B", "#minecraft:stone_bricks", "I", "mlinnotas:cobalt_ingot",
				"C", "minecraft:compass"), "equipment");

		// --- tooltip e mensagens ---
		text("compass.mlinnotas.target", "Alvo: %s", "Target: %s");
		text("compass.mlinnotas.status.searching", "Procurando...", "Searching...");
		text("compass.mlinnotas.status.not_found", "Nada encontrado por perto", "Nothing found nearby");
		text("compass.mlinnotas.tooltip.again", "Agachado + botão direito: procurar de novo a partir daqui",
				"Sneak + right-click: search again from here");
		text("compass.mlinnotas.distance", "A %s blocos, rumo %s", "%s blocks to the %s");
		text("compass.mlinnotas.arrived", "Você chegou!", "You have arrived!");
		text("compass.mlinnotas.other_dimension", "Em outra dimensão", "In another dimension");
		text("compass.mlinnotas.searching", "Procurando: %s...", "Searching: %s...");
		text("compass.mlinnotas.found", "%s encontrado a %s blocos, rumo %s", "Found %s %s blocks to the %s");
		text("compass.mlinnotas.found_here", "%s: você já está nele", "%s: you are already in it");
		text("compass.mlinnotas.not_found", "Nenhum(a) %s encontrado(a) por perto", "No %s found nearby");
		String[][] dirs = {{"n", "norte", "north"}, {"ne", "nordeste", "northeast"}, {"e", "leste", "east"}, {"se", "sudeste", "southeast"},
				{"s", "sul", "south"}, {"sw", "sudoeste", "southwest"}, {"w", "oeste", "west"}, {"nw", "noroeste", "northwest"},
				{"here", "aqui", "here"}};
		for (String[] d : dirs) {
			text("compass.mlinnotas.dir." + d[0], d[1], d[2]);
		}

		// --- tela ---
		text("gui.mlinnotas.compass.biome.title", "Bússola de Biomas: escolha um bioma", "Biome Compass: pick a biome");
		text("gui.mlinnotas.compass.structure.title", "Bússola de Estruturas: escolha uma estrutura", "Structure Compass: pick a structure");
		text("gui.mlinnotas.compass.search", "Buscar (nome, id ou @mod)", "Search (name, id or @mod)");
		text("gui.mlinnotas.compass.none", "Nada para procurar nesta dimensão", "Nothing to look for in this dimension");
		text("gui.mlinnotas.compass.no_match", "Nenhum resultado", "No results");
		text("gui.mlinnotas.compass.count", "%s opções", "%s entries");
		text("gui.mlinnotas.compass.click", "Clique para procurar o mais perto", "Click to find the nearest one");

		// --- nomes das estruturas na lista (variantes vanilla agrupadas, ver CompassCatalog) ---
		String[][] structures = {
				{"minecraft:ancient_city", "Cidade Antiga", "Ancient City"},
				{"minecraft:bastion_remnant", "Bastião em Ruínas", "Bastion Remnant"},
				{"minecraft:buried_treasure", "Tesouro Enterrado", "Buried Treasure"},
				{"minecraft:desert_pyramid", "Templo do Deserto", "Desert Temple"},
				{"minecraft:end_city", "Cidade do End", "End City"},
				{"minecraft:fortress", "Fortaleza do Nether", "Nether Fortress"},
				{"minecraft:igloo", "Iglu", "Igloo"},
				{"minecraft:jungle_pyramid", "Templo da Selva", "Jungle Temple"},
				{"minecraft:mansion", "Mansão da Floresta", "Woodland Mansion"},
				{"minecraft:mineshaft", "Mina Abandonada", "Mineshaft"},
				{"minecraft:monument", "Monumento Oceânico", "Ocean Monument"},
				{"minecraft:nether_fossil", "Fóssil do Nether", "Nether Fossil"},
				{"minecraft:ocean_ruin", "Ruínas Oceânicas", "Ocean Ruins"},
				{"minecraft:pillager_outpost", "Posto de Saqueadores", "Pillager Outpost"},
				{"minecraft:ruined_portal", "Portal em Ruínas", "Ruined Portal"},
				{"minecraft:shipwreck", "Naufrágio", "Shipwreck"},
				{"minecraft:stronghold", "Fortaleza (portal do End)", "Stronghold"},
				{"minecraft:swamp_hut", "Cabana da Bruxa", "Swamp Hut"},
				{"minecraft:trail_ruins", "Ruínas de Trilha", "Trail Ruins"},
				{"minecraft:trial_chambers", "Câmaras de Provação", "Trial Chambers"},
				{"minecraft:village", "Vila", "Village"},
				{"minecraft:abandoned_camp", "Acampamento (Minecraft)", "Camp (Minecraft)"},
				{"mlinnotas:ruined_watchtower", "Torre de Vigia Caída", "Fallen Watchtower"},
				{"mlinnotas:abandoned_camp", "Acampamento Abandonado", "Abandoned Camp"},
				{"mlinnotas:old_well", "Poço Antigo", "Old Well"},
				{"mlinnotas:ruined_chapel", "Capela em Ruínas", "Ruined Chapel"},
				{"mlinnotas:disused_mine", "Mina Desativada", "Disused Mine"},
				{"mlinnotas:cobalt_shrine", "Santuário de Cobalto", "Cobalt Shrine"},
				{"mlinnotas:abyssal_sanctum", "Santuário Abissal", "Abyssal Sanctum"},
				{"mlinnotas:colossus_forge", "Forja do Colosso", "Colossus Forge"},
				{"mlinnotas:end_citadel", "Cidadela do End", "End Citadel"},
				{"mlinnotas:forgotten_crypt", "Cripta Esquecida", "Forgotten Crypt"},
				{"mlinnotas:sunken_hold", "Fortaleza Anã Afundada", "Sunken Dwarven Hold"},
				{"mlinnotas:drowned_temple", "Templo Afogado", "Drowned Temple"},
				{"mlinnotas:sand_catacomb", "Catacumba de Areia", "Sand Catacomb"},
		};
		for (String[] s : structures) {
			text("compass.mlinnotas.structure." + s[0].replace(':', '.'), s[1], s[2]);
		}
	}

	/** Agulha com alvo (lodestone_tracker) aponta para ele; sem alvo gira a esmo, como a bussola fora do Overworld. */
	static void compassItem(String id) throws IOException {
		String withTarget = dispatch(id, "lodestone");
		String noTarget = dispatch(id, "none");
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:condition\",\"property\":\"minecraft:has_component\","
				+ "\"component\":\"minecraft:lodestone_tracker\",\"on_true\":" + withTarget + ",\"on_false\":" + noTarget + "}}");
		for (int frame = 0; frame < 32; frame++) {
			String name = String.format("%s_%02d", id, frame);
			ContentGen.write("assets/mlinnotas/models/item/" + name + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mlinnotas:item/"
					+ name + "\"}}");
		}
	}

	/** Mesmos limiares da bussola vanilla: 0 -> quadro 16, depois (i - 0,5) -> quadro (16 + i) % 32. */
	static String dispatch(String id, String target) {
		StringBuilder entries = new StringBuilder();
		for (int i = 0; i <= 32; i++) {
			double threshold = i == 0 ? 0.0 : i - 0.5;
			int frame = (16 + i) % 32;
			entries.append(i == 0 ? "" : ",").append("{\"threshold\":").append(threshold).append(",\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:item/")
					.append(String.format("%s_%02d", id, frame)).append("\"}}");
		}
		return "{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:compass\",\"target\":\"" + target + "\",\"scale\":32.0,\"entries\":["
				+ entries + "]}";
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
