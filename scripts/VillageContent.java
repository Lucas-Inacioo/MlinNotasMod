import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Conteudo das vilas que crescem para o ContentGen: bloco/item/receita/loot do quadro de missoes, feature que poe o
 * quadro nas vilas vanilla, tags de custo dos projetos, tags de estrutura dos mapas do mercado e todos os textos
 * (projetos, niveis, missoes, mercado, tela e mensagens). Chamado por ContentGen.main.
 */
class VillageContent {
	static void generate() throws IOException {
		// --- bloco do quadro (2 de altura, virado para FACING) ---
		StringBuilder variants = new StringBuilder();
		String[][] facings = {{"south", "0"}, {"west", "90"}, {"north", "180"}, {"east", "270"}};
		for (String half : new String[] {"lower", "upper"}) {
			for (String[] f : facings) {
				variants.append(variants.length() > 0 ? "," : "").append("\"facing=").append(f[0]).append(",half=").append(half)
						.append("\":{\"model\":\"mlinnotas:block/village_board_").append(half).append("\"")
						.append(f[1].equals("0") ? "" : ",\"y\":" + f[1]).append("}");
			}
		}
		ContentGen.write("assets/mlinnotas/blockstates/village_board.json", "{\"variants\":{" + variants + "}}");
		String textures = "\"textures\":{\"particle\":\"minecraft:block/spruce_planks\",\"post\":\"minecraft:block/spruce_log\",\"post_top\":"
				+ "\"minecraft:block/spruce_log_top\",\"board\":\"mlinnotas:block/village_board\",\"back\":\"minecraft:block/spruce_planks\","
				+ "\"roof\":\"minecraft:block/dark_oak_planks\"}";
		String post = "\"faces\":{\"north\":{\"texture\":\"#post\"},\"south\":{\"texture\":\"#post\"},\"east\":{\"texture\":\"#post\"},"
				+ "\"west\":{\"texture\":\"#post\"},\"up\":{\"texture\":\"#post_top\"},\"down\":{\"texture\":\"#post_top\"}}";
		String board = "\"faces\":{\"south\":{\"texture\":\"#board\"},\"north\":{\"texture\":\"#back\"},\"east\":{\"texture\":\"#back\"},"
				+ "\"west\":{\"texture\":\"#back\"},\"up\":{\"texture\":\"#back\"},\"down\":{\"texture\":\"#back\"}}";
		String roof = "\"faces\":{\"north\":{\"texture\":\"#roof\"},\"south\":{\"texture\":\"#roof\"},\"east\":{\"texture\":\"#roof\"},"
				+ "\"west\":{\"texture\":\"#roof\"},\"up\":{\"texture\":\"#roof\"},\"down\":{\"texture\":\"#roof\"}}";
		ContentGen.write("assets/mlinnotas/models/block/village_board_lower.json", "{\"parent\":\"minecraft:block/block\"," + textures
				+ ",\"elements\":[{\"from\":[1,0,6],\"to\":[3,16,10]," + post + "},{\"from\":[13,0,6],\"to\":[15,16,10]," + post + "},"
				+ "{\"from\":[3,9,7],\"to\":[13,16,9]," + board + "},{\"from\":[3,8,6.5],\"to\":[13,9,9.5]," + roof + "}]}");
		ContentGen.write("assets/mlinnotas/models/block/village_board_upper.json", "{\"parent\":\"minecraft:block/block\"," + textures
				+ ",\"elements\":[{\"from\":[1,0,6],\"to\":[3,11,10]," + post + "},{\"from\":[13,0,6],\"to\":[15,11,10]," + post + "},"
				+ "{\"from\":[3,0,7],\"to\":[13,10,9]," + board + "},{\"from\":[0,11,4],\"to\":[16,13,12]," + roof + "},"
				+ "{\"from\":[2,13,6],\"to\":[14,15,10]," + roof + "}]}");
		ContentGen.simpleItem("village_board", "item/generated");
		ContentGen.name("block", "village_board", "Quadro de Missões", "Village Quest Board");
		text("block.mlinnotas.village_board.tooltip", "Funda uma vila (ou abre a vila perto): projetos, missões do dia e mercado",
				"Founds a village (or opens the nearby one): projects, daily bounties and market");
		ContentGen.write("data/mlinnotas/loot_table/blocks/village_board.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"condition\":{\"type\":"
				+ "\"minecraft:survives_explosion\"},\"entries\":[{\"type\":\"minecraft:item\",\"condition\":{\"type\":\"minecraft:match_block\","
				+ "\"blocks\":\"mlinnotas:village_board\",\"state\":{\"half\":\"lower\"}},\"name\":\"mlinnotas:village_board\"}],\"rolls\":1}],"
				+ "\"random_sequence\":\"mlinnotas:blocks/village_board\"}");
		ContentGen.tag("block", "minecraft:mineable/axe", List.of("mlinnotas:village_board"), false);
		ContentGen.shaped("village_board", "SSS|PBP|F F", Map.of("S", "#minecraft:wooden_slabs", "P", "minecraft:paper", "B", "#minecraft:planks",
				"F", "#minecraft:wooden_fences"), "misc");

		// --- quadro nas vilas vanilla (feature no fim da geracao, ver VillageBoardFeature) ---
		ContentGen.write("data/mlinnotas/worldgen/feature/village_board.json", "{\"type\":\"mlinnotas:village_board\"}");
		ContentGen.write("data/mlinnotas/worldgen/placed_feature/village_board.json", "{\"feature\":\"mlinnotas:village_board\",\"placement\":[]}");

		// --- tags de custo ---
		ContentGen.tag("item", "mlinnotas:village/stone", List.of("minecraft:cobblestone", "minecraft:stone", "minecraft:stone_bricks",
				"minecraft:mossy_cobblestone", "minecraft:cobbled_deepslate", "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
				"minecraft:tuff", "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:blackstone"), false);
		ContentGen.tag("item", "mlinnotas:village/glass", List.of("minecraft:glass", "minecraft:glass_pane"), false);
		ContentGen.tag("item", "mlinnotas:village/food", List.of("minecraft:bread", "minecraft:baked_potato", "minecraft:potato",
				"minecraft:carrot", "minecraft:beetroot", "minecraft:apple", "minecraft:melon_slice", "minecraft:sweet_berries",
				"minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken", "minecraft:cooked_mutton",
				"minecraft:cooked_rabbit", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:pumpkin_pie", "minecraft:cookie"), false);
		ContentGen.tag("item", "mlinnotas:village/seeds", List.of("minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:pumpkin_seeds",
				"minecraft:melon_seeds", "minecraft:torchflower_seeds", "minecraft:pitcher_pod"), false);

		// --- estruturas dos mapas do mercado e das missoes de exploracao ---
		ContentGen.tag("worldgen/structure", "mlinnotas:on_village_maps/ruins", List.of("mlinnotas:ruined_watchtower", "mlinnotas:abandoned_camp",
				"mlinnotas:old_well", "mlinnotas:ruined_chapel", "mlinnotas:disused_mine"), false);
		ContentGen.tag("worldgen/structure", "mlinnotas:on_village_maps/cobalt_shrine", List.of("mlinnotas:cobalt_shrine"), false);
		ContentGen.tag("worldgen/structure", "mlinnotas:on_village_maps/abyssal_sanctum", List.of("mlinnotas:abyssal_sanctum"), false);
		ContentGen.tag("worldgen/structure", "mlinnotas:village_bounty/outpost", List.of("minecraft:pillager_outpost"), false);

		// --- gamerule ---
		text("gamerule.mlinnotas.starting_village", "Nascer num acampamento (Mlin Notas)", "Start in a camp (Mlin Notas)");
		text("gamerule.mlinnotas.starting_village.description", "Mundo novo nasce numa pequena vila inicial com quadro de missões",
				"A new world starts in a small starting village with a quest board");

		// --- niveis ---
		text("village.mlinnotas.level.camp", "Acampamento", "Camp");
		text("village.mlinnotas.level.hamlet", "Aldeia", "Hamlet");
		text("village.mlinnotas.level.village", "Vila", "Village");
		text("village.mlinnotas.level.town", "Cidade", "Town");
		text("village.mlinnotas.level.capital", "Capital", "Capital");

		// --- projetos ---
		project("plaza", "Praça do Poço", "Town Square", "Calça a praça em volta do quadro e ergue um poço com telhado, bancos, canteiros e postes.",
				"Paves the square around the board and builds a roofed well, benches, flower beds and lamp posts.");
		project("house", "Casa", "House", "Uma casa nova (chalé ou sobrado) com cama e oficina; chega um aldeão para morar.",
				"A new house (cottage or townhouse) with a bed and a workstation; a villager moves in.");
		project("farm", "Plantação", "Farm", "Canteiros irrigados com espantalho e composteira; chega um fazendeiro.",
				"Irrigated fields with a scarecrow and a composter; a farmer moves in.");
		project("lamps", "Lampiões", "Street Lamps", "Postes com lanterna num anel em volta da praça (cada vez, um anel mais longe).",
				"Lantern posts in a ring around the square (each time, a farther ring).");
		project("smithy", "Forja", "Smithy", "Forja com alto-forno, bigorna e mesa de ferraria; chega um ferreiro e um golem de ferro.",
				"A smithy with a blast furnace, anvil and smithing table; a smith and an iron golem arrive.");
		project("market", "Mercado", "Market", "Três barracas com toldo, cortador de pedras e tear; chegam dois comerciantes.",
				"Three canopied stalls with a stonecutter and a loom; two merchants arrive.");
		project("wall", "Muralha", "Town Wall", "Muralha octogonal com torres nos cantos e quatro portões em arco, com ruas até a praça. Vem um golem de guarda.",
				"An octagonal wall with corner towers and four arched gates, with roads to the square. A guard golem arrives.");
		project("inn", "Estalagem", "Inn", "Estalagem de dois andares: balcão, mesas e lareira embaixo, quartos em cima. Chegam dois aldeões.",
				"A two-storey inn: bar, tables and fireplace downstairs, bedrooms upstairs. Two villagers arrive.");
		project("library", "Biblioteca", "Library", "Biblioteca de pedra com janelas altas, estantes e atris; chega um bibliotecário.",
				"A stone library with tall windows, bookshelves and lecterns; a librarian arrives.");

		// --- custos com tag ---
		text("village.mlinnotas.cost.mlinnotas.village.stone", "Pedra (pedregulho, pedra, tijolos...)", "Stone (cobblestone, stone, bricks...)");
		text("village.mlinnotas.cost.mlinnotas.village.glass", "Vidro ou vidraça", "Glass or glass pane");
		text("village.mlinnotas.cost.mlinnotas.village.food", "Comida (pão, batata, carne cozida...)", "Food (bread, potato, cooked meat...)");
		text("village.mlinnotas.cost.mlinnotas.village.seeds", "Sementes", "Seeds");
		text("village.mlinnotas.cost.minecraft.planks", "Tábuas (qualquer madeira)", "Planks (any wood)");
		text("village.mlinnotas.cost.minecraft.logs", "Troncos (qualquer madeira)", "Logs (any wood)");
		text("village.mlinnotas.cost.minecraft.beds", "Cama (qualquer cor)", "Bed (any color)");
		text("village.mlinnotas.cost.minecraft.wool", "Lã (qualquer cor)", "Wool (any color)");
		text("village.mlinnotas.cost.minecraft.wooden_fences", "Cercas de madeira", "Wooden fences");
		text("village.mlinnotas.cost.minecraft.coals", "Carvão ou carvão vegetal", "Coal or charcoal");

		// --- exploracao ---
		String[][] explore = {{"desert", "um deserto", "a desert"}, {"jungle", "uma selva", "a jungle"}, {"snowy", "um bioma nevado", "a snowy biome"},
				{"badlands", "as terras áridas", "the badlands"}, {"mushroom", "os campos de cogumelos", "the mushroom fields"},
				{"cherry", "um bosque de cerejeiras", "a cherry grove"}, {"swamp", "um pântano", "a swamp"},
				{"deep_dark", "a Escuridão Profunda", "the Deep Dark"}, {"ruin", "uma ruína (torre, capela, mina...)", "a ruin (tower, chapel, mine...)"},
				{"outpost", "um posto avançado de saqueadores", "a pillager outpost"}, {"nether", "o Nether", "the Nether"},
				{"end", "o End", "the End"}, {"shrine", "o Santuário de Cobalto", "the Cobalt Shrine"}};
		for (String[] e : explore) {
			text("village.mlinnotas.explore." + e[0], e[1], e[2]);
		}

		// --- mercado ---
		shop("bread", "6 Pães", "6 Bread", "Comida para a viagem.", "Food for the road.");
		shop("torches", "16 Tochas", "16 Torches", "Luz para as minas.", "Light for the mines.");
		shop("bed", "Cama", "Bed", "Uma cama branca (serve para o projeto Casa).", "A white bed (works for the House project).");
		shop("iron", "3 Lingotes de Ferro", "3 Iron Ingots", "Ferro da forja da vila.", "Iron from the village forge.");
		shop("return_scroll", "Pergaminho de Retorno", "Return Scroll", "Volta à última pedra de teleporte usada.",
				"Takes you back to the last waystone you used.");
		shop("ruin_map", "Mapa de Ruínas", "Ruins Map", "Mapa até a ruína mais perto (torre, acampamento, poço, capela ou mina).",
				"Map to the nearest ruin (tower, camp, well, chapel or mine).");
		shop("raw_cobalt", "2 Cobaltos Brutos", "2 Raw Cobalt", "Minério de cobalto trazido pelos mineiros.", "Cobalt ore brought by the miners.");
		shop("book", "Livro Encantado", "Enchanted Book", "Um encantamento aleatório da mesa, até o nível III.",
				"A random enchanting-table enchantment, up to level III.");
		shop("repair", "Reparo", "Repair", "O ferreiro conserta o item da sua mão principal (1 a 5 esmeraldas, conforme o desgaste).",
				"The smith repairs the item in your main hand (1 to 5 emeralds, depending on wear).");
		shop("shrine_map", "Mapa do Santuário de Cobalto", "Cobalt Shrine Map", "Mapa até o covil do Golem de Cobalto.",
				"Map to the Cobalt Golem's lair.");
		shop("diamond", "Diamante", "Diamond", "Um diamante lapidado.", "A cut diamond.");
		shop("golden_carrot", "8 Cenouras Douradas", "8 Golden Carrots", "A melhor comida da feira.", "The best food at the fair.");
		shop("rare_book", "Livro Raro", "Rare Book", "Um encantamento de tesouro no nível máximo (Remendo, Passos Gelados...).",
				"A treasure enchantment at max level (Mending, Frost Walker...).");
		shop("abyss_map", "Mapa do Santuário Abissal", "Abyssal Sanctum Map", "Mapa até o covil do Guardião das Profundezas.",
				"Map to the Deep Guardian's lair.");
		shop("xp", "8 Frascos de Experiência", "8 Bottles o' Enchanting", "Experiência engarrafada.", "Bottled experience.");
		shop("netherite_scrap", "Fragmento de Netherita", "Netherite Scrap", "Raridade trazida por mercadores do Nether.",
				"A rarity brought by Nether traders.");
		shop("totem", "Totem da Imortalidade", "Totem of Undying", "Salva da morte uma vez.", "Saves you from death once.");

		// --- tela ---
		text("gui.mlinnotas.village.title", "Quadro de Missões", "Quest Board");
		text("gui.mlinnotas.village.tab.projects", "Projetos", "Projects");
		text("gui.mlinnotas.village.tab.bounties", "Missões do dia", "Daily bounties");
		text("gui.mlinnotas.village.tab.shop", "Mercado", "Market");
		text("gui.mlinnotas.village.prosperity", "Prosperidade %s / %s  (%s)", "Prosperity %s / %s  (%s)");
		text("gui.mlinnotas.village.prosperity_max", "Prosperidade %s  (nível máximo)", "Prosperity %s  (max level)");
		text("gui.mlinnotas.village.perk.trade_cap", "Aldeões chegam até: %s", "Villagers can reach: %s");
		text("gui.mlinnotas.village.perk.discount", "Desconto: %s esmeralda(s) por troca", "Discount: %s emerald(s) per trade");
		text("gui.mlinnotas.village.perk.radius", "Obras até %s blocos do quadro", "Buildings up to %s blocks from the board");
		text("gui.mlinnotas.village.next_level", "Para virar %s:", "To become a %s:");
		text("gui.mlinnotas.village.need.prosperity", "prosperidade %s / %s", "prosperity %s / %s");
		text("gui.mlinnotas.village.need.projects", "obras prontas %s / %s", "finished projects %s / %s");
		text("gui.mlinnotas.village.need.project", "construir: %s", "build: %s");
		text("gui.mlinnotas.village.next_perks", "Libera: aldeões até %s, desconto %s, mais obras e itens no mercado",
				"Unlocks: villagers up to %s, discount %s, more projects and market items");
		text("gui.mlinnotas.village.deposit", "Entregar", "Deliver");
		text("gui.mlinnotas.village.deliver", "Entregar", "Deliver");
		text("gui.mlinnotas.village.claim", "Receber", "Claim");
		text("gui.mlinnotas.village.bounty_paid", "Feita", "Done");
		text("gui.mlinnotas.village.buy", "Comprar", "Buy");
		text("gui.mlinnotas.village.building", "Construindo... %s%%", "Building... %s%%");
		text("gui.mlinnotas.village.queued", "Na fila de obras", "Waiting to be built");
		text("gui.mlinnotas.village.no_space", "Sem espaço livre", "No free space");
		text("gui.mlinnotas.village.requires", "Requer %s", "Requires %s");
		text("gui.mlinnotas.village.maxed", "Completo %s/%s", "Complete %s/%s");
		text("gui.mlinnotas.village.built_count", "Feitas %s/%s", "Built %s/%s");
		text("gui.mlinnotas.village.cost_tooltip", "%s: %s / %s", "%s: %s / %s");
		text("gui.mlinnotas.village.reward.villagers", "+%s aldeão(ões)", "+%s villager(s)");
		text("gui.mlinnotas.village.reward.golems", "+%s golem de ferro", "+%s iron golem");
		text("gui.mlinnotas.village.reward.prosperity", "+%s prosperidade", "+%s prosperity");
		text("gui.mlinnotas.village.reward.prosperity_short", "+%s prosp.", "+%s prosp.");
		text("gui.mlinnotas.village.kind.deliver", "Entrega", "Delivery");
		text("gui.mlinnotas.village.kind.hunt", "Caçada", "Hunt");
		text("gui.mlinnotas.village.kind.explore", "Exploração", "Exploration");
		text("gui.mlinnotas.village.bounty.deliver", "%s × %s", "%s × %s");
		text("gui.mlinnotas.village.bounty.hunt", "%s × %s", "%s × %s");
		text("gui.mlinnotas.village.bounty.explore", "visitar %s", "visit %s");
		text("gui.mlinnotas.village.bounty_done", "Recompensa entregue", "Reward claimed");
		text("gui.mlinnotas.village.progress", "Progresso %s / %s", "Progress %s / %s");
		text("gui.mlinnotas.village.rewards", "Recompensa:", "Reward:");
		text("gui.mlinnotas.village.price", "%s esmeralda(s)", "%s emerald(s)");
		text("gui.mlinnotas.village.repair_price", "1-5 esmeraldas", "1-5 emeralds");
		text("gui.mlinnotas.village.footer.building", "Obra em andamento: %s (%s%%)", "Under construction: %s (%s%%)");
		text("gui.mlinnotas.village.footer.no_space", "Próxima obra parada: falta lugar livre perto do quadro",
				"Next project on hold: no free space near the board");
		text("gui.mlinnotas.village.footer.new_bounties", "Novas missões em %s min", "New bounties in %s min");
		text("gui.mlinnotas.village.footer.idle", "%s obras prontas: entregue materiais!", "%s projects done: deliver materials!");

		// --- mensagens ---
		text("message.mlinnotas.village.founded", "Vila %s fundada! Use o quadro para fazê-la crescer.", "Village %s founded! Use the board to grow it.");
		text("message.mlinnotas.village.welcome", "Bem-vindo ao acampamento %s! Abra o quadro de missões para fazê-lo crescer.",
				"Welcome to the %s camp! Open the quest board to make it grow.");
		text("message.mlinnotas.village.deposited", "%s: entrega registrada", "%s: delivery recorded");
		text("message.mlinnotas.village.nothing", "%s: você não tem nada do que falta", "%s: you have none of what is missing");
		text("message.mlinnotas.village.locked", "%s: a vila precisa ser %s", "%s: the village must be a %s");
		text("message.mlinnotas.village.maxed", "%s: já feito o máximo para o nível da vila", "%s: already built the maximum for this level");
		text("message.mlinnotas.village.project_funded", "%s completou a entrega de %s em %s! A obra vai começar.",
				"%s finished delivering %s in %s! Construction will start.");
		text("message.mlinnotas.village.building", "%s: começou a obra de %s", "%s: construction of %s started");
		text("message.mlinnotas.village.built", "%s: %s pronto! (+%s prosperidade)", "%s: %s finished! (+%s prosperity)");
		text("message.mlinnotas.village.level_up", "%s agora é %s!", "%s is now a %s!");
		text("message.mlinnotas.village.bounty_done", "%s cumpriu uma missão de %s (+%s prosperidade)", "%s completed a bounty for %s (+%s prosperity)");
		text("message.mlinnotas.village.bounty_partial", "Entregou %s; ainda falta um pouco", "Delivered %s; a bit more is needed");
		text("message.mlinnotas.village.bounty_not_ready", "A missão ainda não está completa", "The bounty is not complete yet");
		text("message.mlinnotas.village.bounty_ready", "Missão de %s completa! Volte ao quadro para receber.",
				"Bounty for %s complete! Go back to the board to claim it.");
		text("message.mlinnotas.village.no_emeralds", "Faltam esmeraldas (custa %s)", "Not enough emeralds (costs %s)");
		text("message.mlinnotas.village.bought", "Comprou %s por %s esmeraldas", "Bought %s for %s emeralds");
		text("message.mlinnotas.village.nothing_to_repair", "Segure na mão principal um item gasto para consertar",
				"Hold a worn item in your main hand to repair it");
		text("message.mlinnotas.village.map_none", "Os cartógrafos não acharam esse lugar por perto", "The cartographers found no such place nearby");
	}

	static void project(String id, String pt, String en, String descPt, String descEn) {
		text("village.mlinnotas.project." + id, pt, en);
		text("village.mlinnotas.project." + id + ".desc", descPt, descEn);
	}

	static void shop(String id, String pt, String en, String descPt, String descEn) {
		text("village.mlinnotas.shop." + id, pt, en);
		text("village.mlinnotas.shop." + id + ".desc", descPt, descEn);
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
