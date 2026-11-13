import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Conteudo das ruinas de superficie e das pedras de teleporte para o ContentGen: estruturas, structure set, tags de
 * bioma, loot dos baus, bloco/itens/receitas da pedra de teleporte e do Pergaminho de Retorno, e textos (lang).
 * Chamado por ContentGen.main (o launcher do Java compila este arquivo junto).
 */
class RuinsContent {
	// id da estrutura, nome do tipo (RuinKind), biomas
	static final String[][] RUINS = {
			{"ruined_watchtower", "watchtower", "minecraft:plains,minecraft:sunflower_plains,minecraft:meadow,#minecraft:is_savanna,#minecraft:is_hill"},
			{"abandoned_camp", "camp", "#minecraft:is_forest,#minecraft:is_taiga"},
			{"old_well", "well", "minecraft:desert,#minecraft:is_badlands,#minecraft:is_savanna"},
			{"ruined_chapel", "chapel", "minecraft:dark_forest,minecraft:pale_garden,minecraft:swamp,minecraft:mangrove_swamp"},
			{"disused_mine", "mine", "#minecraft:is_mountain,#minecraft:is_hill,minecraft:stony_shore"},
	};

	static void generate(List<String> pickaxeMineable) throws IOException {
		StringBuilder set = new StringBuilder();
		for (String[] ruin : RUINS) {
			String id = ruin[0];
			ContentGen.write("data/mlinnotas/worldgen/structure/" + id + ".json", "{\"type\":\"mlinnotas:ruin\",\"kind\":\"" + ruin[1]
					+ "\",\"biomes\":\"#mlinnotas:has_structure/" + id + "\",\"spawn_overrides\":{},\"step\":\"surface_structures\"}");
			ContentGen.tag("worldgen/biome", "mlinnotas:has_structure/" + id, List.of(ruin[2].split(",")), false);
			set.append(set.length() > 0 ? "," : "").append("{\"structure\":\"mlinnotas:").append(id).append("\",\"weight\":1}");
		}
		// uma ruina a cada ~24 chunks (~380 blocos); se o tipo sorteado nao cabe no bioma o jogo tenta os outros
		ContentGen.write("data/mlinnotas/worldgen/structure_set/ruins.json", "{\"placement\":{\"type\":\"minecraft:random_spread\",\"salt\":739215,"
				+ "\"separation\":8,\"spacing\":24},\"structures\":[" + set + "]}");

		// loot: suprimentos de inicio de jogo; a mina tem mais minerio
		String rare = "{\"entries\":[{\"type\":\"minecraft:empty\",\"weight\":12},{\"type\":\"minecraft:item\",\"modifier\":{\"type\":"
				+ "\"minecraft:enchant_randomly\",\"options\":\"#minecraft:on_random_loot\"},\"name\":\"minecraft:book\",\"weight\":2},"
				+ "{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:return_scroll\",\"weight\":3},{\"type\":\"minecraft:item\","
				+ "\"name\":\"minecraft:ender_pearl\",\"weight\":1}],\"rolls\":1}";
		ContentGen.write("data/mlinnotas/loot_table/chests/ruin_supplies.json", "{\"type\":\"minecraft:chest\",\"pools\":[{\"entries\":["
				+ item("minecraft:bread", 15, 1, 3) + "," + item("minecraft:apple", 10, 1, 3) + "," + item("minecraft:baked_potato", 10, 1, 4) + ","
				+ item("minecraft:cooked_beef", 6, 1, 2) + "," + item("minecraft:carrot", 8, 1, 4) + "," + item("minecraft:stone_pickaxe", 5, 1, 1) + ","
				+ item("minecraft:stone_axe", 5, 1, 1) + "," + item("minecraft:stone_sword", 5, 1, 1) + "," + item("minecraft:copper_pickaxe", 4, 1, 1) + ","
				+ item("minecraft:copper_sword", 4, 1, 1) + "," + item("minecraft:copper_axe", 3, 1, 1) + "," + item("minecraft:copper_ingot", 12, 1, 5) + ","
				+ item("minecraft:iron_ingot", 6, 1, 3) + "," + item("minecraft:iron_nugget", 8, 2, 6) + "," + item("minecraft:torch", 10, 2, 8) + ","
				+ item("minecraft:string", 6, 1, 4) + "," + item("minecraft:leather", 5, 1, 3) + "],\"rolls\":{\"type\":\"minecraft:uniform\",\"max\":6,"
				+ "\"min\":3}}," + rare + "],\"random_sequence\":\"mlinnotas:chests/ruin_supplies\"}");
		ContentGen.write("data/mlinnotas/loot_table/chests/ruin_mine.json", "{\"type\":\"minecraft:chest\",\"pools\":[{\"entries\":["
				+ item("minecraft:raw_copper", 12, 2, 6) + "," + item("minecraft:raw_iron", 10, 1, 4) + "," + item("minecraft:copper_ingot", 8, 1, 4) + ","
				+ item("minecraft:iron_ingot", 6, 1, 3) + "," + item("minecraft:coal", 12, 2, 8) + "," + item("minecraft:torch", 10, 2, 8) + ","
				+ item("minecraft:rail", 6, 2, 8) + "," + item("minecraft:stone_pickaxe", 5, 1, 1) + "," + item("minecraft:copper_pickaxe", 5, 1, 1) + ","
				+ item("minecraft:iron_pickaxe", 1, 1, 1) + "," + item("minecraft:bread", 8, 1, 3) + "," + item("mlinnotas:raw_cobalt", 2, 1, 2)
				+ "],\"rolls\":{\"type\":\"minecraft:uniform\",\"max\":6,\"min\":3}}," + rare + "],\"random_sequence\":\"mlinnotas:chests/ruin_mine\"}");

		// --- pedra de teleporte ---
		ContentGen.write("assets/mlinnotas/blockstates/waystone.json", "{\"variants\":{\"half=lower\":{\"model\":\"mlinnotas:block/waystone_lower\"},"
				+ "\"half=upper\":{\"model\":\"mlinnotas:block/waystone_upper\"}}}");
		String faces = "\"faces\":{\"north\":{\"texture\":\"#side\"},\"south\":{\"texture\":\"#side\"},\"east\":{\"texture\":\"#side\"},"
				+ "\"west\":{\"texture\":\"#side\"},\"up\":{\"texture\":\"#top\"},\"down\":{\"texture\":\"#base\"}}";
		String baseFaces = faces.replace("#side", "#base").replace("#top", "#base");
		String capFaces = faces.replace("#side", "#base").replace("#top", "#base");
		ContentGen.write("assets/mlinnotas/models/block/waystone_lower.json", "{\"parent\":\"minecraft:block/block\",\"textures\":{\"particle\":"
				+ "\"mlinnotas:block/waystone_side\",\"side\":\"mlinnotas:block/waystone_side\",\"top\":\"mlinnotas:block/waystone_top\",\"base\":"
				+ "\"minecraft:block/smooth_stone\"},\"elements\":[{\"from\":[1,0,1],\"to\":[15,3,15]," + baseFaces + "},{\"from\":[3,3,3],\"to\":[13,16,13],"
				+ faces + "}]}");
		ContentGen.write("assets/mlinnotas/models/block/waystone_upper.json", "{\"parent\":\"minecraft:block/block\",\"textures\":{\"particle\":"
				+ "\"mlinnotas:block/waystone_side\",\"side\":\"mlinnotas:block/waystone_side_upper\",\"top\":\"mlinnotas:block/waystone_top\",\"base\":"
				+ "\"minecraft:block/smooth_stone\"},\"elements\":[{\"from\":[3,0,3],\"to\":[13,10,13]," + faces + "},{\"from\":[2,10,2],\"to\":[14,12,14],"
				+ capFaces + "},{\"from\":[5,12,5],\"to\":[11,16,11]," + faces.replace("#side", "#top") + "}]}");
		ContentGen.simpleItem("waystone", "item/generated");
		ContentGen.name("block", "waystone", "Pedra de Teleporte", "Waystone");
		ContentGen.PT.put("block.mlinnotas.waystone.tooltip", "Ative com o botão direito (vale para o mundo todo); depois, escolha o destino");
		ContentGen.EN.put("block.mlinnotas.waystone.tooltip", "Right-click to activate it for the whole world; then pick a destination");
		ContentGen.write("data/mlinnotas/loot_table/blocks/waystone.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"condition\":{\"type\":"
				+ "\"minecraft:survives_explosion\"},\"entries\":[{\"type\":\"minecraft:item\",\"condition\":{\"type\":\"minecraft:match_block\","
				+ "\"blocks\":\"mlinnotas:waystone\",\"state\":{\"half\":\"lower\"}},\"name\":\"mlinnotas:waystone\"}],\"rolls\":1}],"
				+ "\"random_sequence\":\"mlinnotas:blocks/waystone\"}");
		pickaxeMineable.add("mlinnotas:waystone");
		ContentGen.tag("item", "mlinnotas:polished_stones", List.of("minecraft:smooth_stone", "minecraft:polished_andesite", "minecraft:polished_diorite",
				"minecraft:polished_granite", "minecraft:polished_deepslate", "minecraft:polished_tuff", "minecraft:polished_blackstone"), false);
		ContentGen.shaped("waystone", "SES|SCS|SSS", Map.of("S", "#mlinnotas:polished_stones", "E", "minecraft:ender_pearl",
				"C", "mlinnotas:cobalt_ingot"), "misc");

		ContentGen.simpleItem("return_scroll", "item/generated");
		ContentGen.name("item", "return_scroll", "Pergaminho de Retorno", "Return Scroll");
		ContentGen.PT.put("item.mlinnotas.return_scroll.tooltip", "Leva de volta à última pedra de teleporte usada (é gasto)");
		ContentGen.EN.put("item.mlinnotas.return_scroll.tooltip", "Takes you back to the last waystone you used (consumed)");
		ContentGen.shaped("return_scroll", " P |PEP| P ", Map.of("P", "minecraft:paper", "E", "minecraft:ender_pearl"), "misc", 2);

		// --- textos da tela e mensagens ---
		text("gui.mlinnotas.waystone.title", "Pedras de Teleporte", "Waystones");
		text("gui.mlinnotas.waystone.here", "Pedra atual:", "This waystone:");
		text("gui.mlinnotas.waystone.rename", "Renomear", "Rename");
		text("gui.mlinnotas.waystone.activated", "Pedra ativada para todos no mundo! Dê um nome a ela:", "Waystone activated for everyone in this world! Name it:");
		text("gui.mlinnotas.waystone.none", "Nenhuma outra pedra ativada ainda", "No other waystone activated yet");
		text("gui.mlinnotas.waystone.cost", "%s níveis", "%s levels");
		text("gui.mlinnotas.waystone.cost_one", "1 nível", "1 level");
		text("gui.mlinnotas.waystone.free", "Grátis", "Free");
		text("gui.mlinnotas.waystone.other_dimension", "outra dimensão", "other dimension");
		text("gui.mlinnotas.waystone.distance", "%s blocos", "%s blocks");
		text("gui.mlinnotas.waystone.your_levels", "Seus níveis: %s", "Your levels: %s");
		text("message.mlinnotas.waystone.activated", "Pedra de teleporte \"%s\" ativada para todos", "Waystone \"%s\" activated for everyone");
		text("message.mlinnotas.waystone.renamed", "Pedra de teleporte renomeada para \"%s\"", "Waystone renamed to \"%s\"");
		text("message.mlinnotas.waystone.no_xp", "Você precisa de %s níveis de experiência para essa viagem", "You need %s experience levels for this trip");
		text("message.mlinnotas.waystone.traveled", "Viajou para %s (-%s níveis)", "Traveled to %s (-%s levels)");
		text("message.mlinnotas.waystone.traveled_free", "Viajou para %s", "Traveled to %s");
		text("message.mlinnotas.waystone.missing", "Essa pedra de teleporte não existe mais", "That waystone no longer exists");
		text("message.mlinnotas.waystone.scroll_none", "Use uma pedra de teleporte antes de ler o pergaminho", "Use a waystone before reading the scroll");
		text("message.mlinnotas.waystone.scroll_used", "O pergaminho levou você de volta a %s", "The scroll took you back to %s");
	}

	static String item(String id, int weight, int min, int max) {
		String count = min == max && min == 1 ? "" : "\"modifier\":{\"type\":\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"max\":"
				+ max + ",\"min\":" + min + "}},";
		return "{\"type\":\"minecraft:item\"," + count + "\"name\":\"" + id + "\",\"weight\":" + weight + "}";
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
