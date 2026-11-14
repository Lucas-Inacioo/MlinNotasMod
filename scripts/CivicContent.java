import java.io.IOException;
import java.util.List;

/**
 * Conteudo das construcoes civicas e de defesa (Vilas 2.0, fase 7) para o ContentGen: blocos de balcao (caixa de correio,
 * pedestal do Museu, quadro da Guilda: estado, modelo, item, loot, tags), nome do guarda da vila e todos os textos (projetos,
 * o que cada nivel traz, mensagens, menus, colecoes do Museu e destinos da Guilda). Chamado por ContentGen.main.
 */
class CivicContent {
	static void generate(List<String> pickaxeMineable) throws IOException {
		// --- blocos de balcao: cubo orientavel com frente decorada ---
		block("mailbox", "Caixa de correio", "Mailbox", "minecraft:mineable/axe", pickaxeMineable);
		block("museum_pedestal", "Pedestal do museu", "Museum pedestal", "minecraft:mineable/pickaxe", pickaxeMineable);
		block("guild_board", "Quadro de expedições", "Expedition board", "minecraft:mineable/axe", pickaxeMineable);
		text("entity.mlinnotas.village_guard", "Guarda da vila", "Village guard");

		// --- projetos ---
		project("post_office", "Correio", "Post Office",
				"Agência de pedra com balcão, escaninhos e uma caixa de correio. Ponha itens na saída da caixa (agache e clique para escolher o "
						+ "destino: o Correio de outra vila sua ou o outro jogador) e eles chegam no dia seguinte, na caixa de entrada de qualquer "
						+ "Correio. Precisa de um morador livre como carteiro. Exigido para a Metrópole.",
				"Stone office with a counter, pigeonholes and a mailbox. Put items in the mailbox outbox (sneak-click to pick the destination: "
						+ "another of your villages' post offices or the other player) and they arrive the next day, in the inbox of any post office. "
						+ "Needs a free resident as the postman. Required for the Metropolis.");
		project("school", "Escola", "School",
				"Sala de aula com atril, estantes, carteiras e uma sineta no telhado. Filhotes de aldeão crescem 2x mais rápido na vila (3x no "
						+ "nível II) e, no nível II, os novatos ganham experiência de ofício aos poucos para subir de nível mais cedo. Exigida para a Metrópole.",
				"Classroom with a lectern, shelves, desks and a bell on the roof. Baby villagers grow 2x faster in the village (3x at level II) and, "
						+ "at level II, novices slowly gain trade experience to level up sooner. Required for the Metropolis.");
		project("watchtower", "Torre de vigia", "Watchtower",
				"Torre de pedra junto à muralha com um sino de verdade no topo: toca quando saqueadores atacam a vila, mostra no minimapa os monstros "
						+ "dentro do raio da vila à noite e, no nível III, quatro arqueiros no mata-cão atiram nos monstros por perto. Exigida para a Capital.",
				"Stone tower next to the wall with a real bell on top: it rings when raiders attack the village, shows the monsters inside the "
						+ "village radius on the minimap at night and, at level III, four archers on the machicolation shoot nearby monsters. "
						+ "Required for the Capital.");
		project("barracks", "Quartel", "Barracks",
				"Alojamento com beliches, rancho, armeiro e um pátio de treino. Guardas de armadura patrulham as ruas à noite e atacam monstros: "
						+ "2 no nível I, 4 no II e 6 no III (armadura de malha, ferro e diamante). Precisa de um morador livre como comandante. "
						+ "Exigido para a Metrópole.",
				"Lodging with bunks, mess, armourer and a training yard. Armoured guards patrol the streets at night and attack monsters: 2 at "
						+ "level I, 4 at II and 6 at III (chainmail, iron and diamond armour). Needs a free resident as commander. Required for the "
						+ "Metropolis.");
		project("village_waystone", "Pedra de teleporte da vila", "Village waystone",
				"Pracinha com uma pedra de teleporte já ativada no centro da vila. Viajar entre pedras de vilas suas custa metade dos níveis de "
						+ "experiência.",
				"A little square with a waystone already activated at the village centre. Travelling between waystones of your villages costs "
						+ "half the experience levels.");
		project("beacon", "Farol comunitário", "Community Beacon",
				"Pirâmide de blocos de metal com um sinalizador no topo. O efeito vale para todos dentro da muralha: Velocidade no I, e a cada "
						+ "nível Pressa, Resistência, Força e Regeneração. Sobe com o metal da pirâmide: ferro, ouro, diamante, netherite e celestio. "
						+ "O nível II é exigido para a Cidade da Coroa.",
				"Pyramid of metal blocks topped by a beacon. The effect applies to everyone inside the wall: Speed at I, then Haste, Resistance, "
						+ "Strength and Regeneration with each level. It goes up with the pyramid metal: iron, gold, diamond, netherite and celestium. "
						+ "Level II is required for the Crown City.");
		project("museum", "Museu", "Museum",
				"Salão com pedestal e paredes de molduras. Doe uma peça de cada minério, lingote, gema, peixe, prato, troféu de chefe e artefato "
						+ "(botão direito no pedestal com o item): as doações aparecem nas molduras, e cada coleção completa dá recompensa e "
						+ "prosperidade. Nível I: minérios, lingotes e gemas; II: peixes e pratos; III: chefes e artefatos. Exigido para a Cidade da Coroa.",
				"Hall with a pedestal and frame-covered walls. Donate one of each ore, ingot, gem, fish, dish, boss trophy and artifact (right-click "
						+ "the pedestal with the item): donations show in the frames and every completed collection gives a reward and prosperity. "
						+ "Level I: ores, ingots and gems; II: fish and dishes; III: bosses and artifacts. Required for the Crown City.");
		project("explorers_guild", "Guilda dos Exploradores", "Explorers' Guild",
				"Alojamento com o quadro de expedições. Mande aldeões em viagens (mina, caça, floresta, pesca; Nether e End depois que um jogador "
						+ "os visitar) de 1 a 3 dias: agache e clique no quadro para escolher o destino, clique para abrir, ponha o equipamento "
						+ "(ferramenta, arma, armadura, comida) na primeira fileira e feche para partir. Equipamento melhor traz mais e encurta a viagem "
						+ "e gasta durabilidade; minério acima do nível de Mineração do mundo e itens de chefe nunca voltam. Exploradores ao mesmo "
						+ "tempo = nível da Guilda.",
				"Lodge with the expedition board. Send villagers on trips (mine, hunt, forest, fishing; Nether and End once a player has "
						+ "visited them) lasting 1 to 3 days: sneak-click the board to pick the destination, click to open it, put the gear (tool, "
						+ "weapon, armour, food) in the first row and close it to depart. Better gear brings more, shortens the trip and loses "
						+ "durability; ores above the world's Mining level and boss items never come back. Simultaneous explorers = guild level.");

		// --- o que cada nivel traz ---
		level("post_office", 1, "Balcão, escaninhos e duas caixas de correio (uma na fachada): 9 encaixes de saída, entrega no dia seguinte.",
				"Counter, pigeonholes and two mailboxes (one on the front): 9 outbox slots, delivery the next day.");
		level("post_office", 2, "Sala de triagem no andar de cima: 18 encaixes de saída e entrega duas vezes por dia.",
				"Sorting room upstairs: 18 outbox slots and delivery twice a day.");
		level("school", 1, "Sala de aula com sineta: filhotes crescem 2x mais rápido.", "Classroom with a bell: baby villagers grow 2x faster.");
		level("school", 2, "Biblioteca e berçário no andar de cima: filhotes 3x e novatos ganham experiência de ofício.",
				"Library and nursery upstairs: babies 3x and novices gain trade experience.");
		level("watchtower", 1, "Torre de pedra com sino: avisa dos saqueadores e marca monstros no minimapa à noite.",
				"Stone tower with a bell: warns of raiders and marks monsters on the minimap at night.");
		level("watchtower", 2, "Torre mais larga e alta, com telhadinho sobre o sino.", "Wider, taller tower with a little roof over the bell.");
		level("watchtower", 3, "Mata-cão em balanço com quatro arqueiros que atiram nos monstros.", "Overhanging machicolation with four archers shooting monsters.");
		level("barracks", 1, "Alojamento 11x7: 2 guardas de armadura de malha patrulham à noite.", "11x7 lodging: 2 chainmail guards patrol at night.");
		level("barracks", 2, "Arsenal no andar de cima: 4 guardas de armadura de ferro.", "Armoury upstairs: 4 iron-armoured guards.");
		level("barracks", 3, "Sineta de alarme no telhado: 6 guardas de armadura de diamante.", "Alarm bell on the roof: 6 diamond-armoured guards.");
		level("village_waystone", 1, "Pedra de teleporte da vila: viagem entre pedras de vilas pela metade do preço.",
				"Village waystone: travel between village waystones at half price.");
		level("beacon", 1, "Uma camada de ferro: Velocidade para todos dentro da muralha.", "One iron layer: Speed for everyone inside the wall.");
		level("beacon", 2, "Duas camadas de ouro: Pressa também.", "Two gold layers: Haste too.");
		level("beacon", 3, "Três camadas de diamante: Resistência também.", "Three diamond layers: Resistance too.");
		level("beacon", 4, "Quatro camadas de netherite: Força também.", "Four netherite layers: Strength too.");
		level("beacon", 5, "Quatro camadas de celestio e obelisco: Regeneração também.", "Four celestium layers and obelisk: Regeneration too.");
		level("museum", 1, "Salão com pedestal: coleções de minérios, lingotes e gemas.", "Hall with a pedestal: ore, ingot and gem collections.");
		level("museum", 2, "Galeria no andar de cima: peixes e pratos.", "Upper gallery: fish and dishes.");
		level("museum", 3, "Pórtico de colunas: troféus de chefes e artefatos.", "Columned portico: boss trophies and artifacts.");
		level("explorers_guild", 1, "Alojamento com o quadro: 1 explorador por vez.", "Lodge with the board: 1 explorer at a time.");
		level("explorers_guild", 2, "Armeiro: 2 exploradores ao mesmo tempo.", "Armourer: 2 explorers at once.");
		level("explorers_guild", 3, "Depósito maior: 3 exploradores.", "Bigger storeroom: 3 explorers.");
		level("explorers_guild", 4, "Sala de troféus no andar de cima: 4 exploradores.", "Trophy room upstairs: 4 explorers.");
		level("explorers_guild", 5, "Mirante com sino no telhado: 5 exploradores.", "Lookout with a bell on the roof: 5 explorers.");

		// --- mensagens ---
		text("message.mlinnotas.civic.post.inactive", "Este Correio não está funcionando (precisa estar pronto e com um morador livre como carteiro)",
				"This post office is not working (it must be finished and have a free resident as the postman)");
		text("message.mlinnotas.civic.post.no_destination", "Sem destino: aparecem os Correios funcionando de outras vilas suas e os outros jogadores",
				"No destination: working post offices of your other villages and the other players show up");
		text("message.mlinnotas.civic.post.dest_village", "Destino: Correio de %s (agachado + botão direito troca)",
				"Destination: post office of %s (sneak + right-click to change)");
		text("message.mlinnotas.civic.post.dest_player", "Destino: %s, jogador (agachado + botão direito troca)",
				"Destination: %s, player (sneak + right-click to change)");
		text("message.mlinnotas.civic.post.sent", "%s itens enviados para %s: chegam no próximo dia", "%s items sent to %s: they arrive the next day");
		text("message.mlinnotas.civic.post.arrived", "Chegou correspondência de %s! Abra a caixa de correio de um Correio.",
				"Mail from %s arrived! Open the mailbox of a post office.");
		text("container.mlinnotas.mailbox", "Correio → %s", "Post → %s");
		text("container.mlinnotas.mailbox.none", "Correio (sem destino)", "Post (no destination)");
		text("message.mlinnotas.civic.museum.inactive", "O Museu não está funcionando (precisa estar pronto e com um morador livre como curador)",
				"The museum is not working (it must be finished and have a free resident as curator)");
		text("message.mlinnotas.civic.museum.have", "%s já está no Museu", "%s is already in the museum");
		text("message.mlinnotas.civic.museum.not_wanted", "O Museu não pede %s agora", "The museum does not ask for %s right now");
		text("message.mlinnotas.civic.museum.donated", "Você doou %s ao Museu", "You donated %s to the museum");
		text("message.mlinnotas.civic.museum.complete", "%s completou a coleção %s do Museu de %s! (+%s de prosperidade)",
				"%s completed the %s collection of the %s museum! (+%s prosperity)");
		text("message.mlinnotas.civic.museum.head", "Museu: %s itens doados", "Museum: %s items donated");
		text("message.mlinnotas.civic.museum.line", "  %s: %s/%s", "  %s: %s/%s");
		text("message.mlinnotas.civic.museum.locked", "  +%s coleções com a melhoria do Museu", "  +%s collections with a museum upgrade");
		String[][] sets = {{"ores", "Minérios", "Ores"}, {"ingots", "Lingotes", "Ingots"}, {"gems", "Gemas", "Gems"}, {"fish", "Peixes", "Fish"},
				{"food", "Pratos", "Dishes"}, {"bosses", "Troféus de chefes", "Boss trophies"}, {"artifacts", "Artefatos", "Artifacts"}};
		for (String[] s : sets) {
			text("museum.mlinnotas.set." + s[0], s[1], s[2]);
		}
		text("message.mlinnotas.civic.guild.inactive", "A Guilda não está funcionando (precisa estar pronta e com um morador livre como guia-mestre)",
				"The guild is not working (it must be finished and have a free resident as guildmaster)");
		text("message.mlinnotas.civic.guild.dest", "Destino da expedição: %s. Abra o quadro, equipe o explorador e feche para partir.",
				"Expedition destination: %s. Open the board, equip the explorer and close it to depart.");
		text("message.mlinnotas.civic.guild.full", "Todos os exploradores da Guilda (nível %s) já estão em viagem",
				"Every explorer of the guild (level %s) is already travelling");
		text("message.mlinnotas.civic.guild.no_villager", "Nenhum aldeão adulto e livre por perto para ir", "No free adult villager nearby to go");
		text("message.mlinnotas.civic.guild.departed", "%s partiu em expedição (%s) e volta em %s dia(s)", "%s left on an expedition (%s) and returns in %s day(s)");
		text("message.mlinnotas.civic.guild.returned", "%s voltou à Guilda de %s: o que achou e o equipamento (gasto) estão no quadro",
				"%s is back at the guild of %s: the haul and the (worn) gear are on the board");
		text("container.mlinnotas.guild", "Expedição: %s", "Expedition: %s");
		text("container.mlinnotas.guild.idle", "Guilda (agachado + botão direito escolhe o destino)", "Guild (sneak + right-click picks the destination)");
		String[][] dests = {{"mine", "Mina", "Mine"}, {"hunt", "Caça", "Hunt"}, {"forest", "Floresta", "Forest"}, {"fishing", "Pesca", "Fishing"},
				{"nether", "Nether", "Nether"}, {"end", "End", "End"}};
		for (String[] d : dests) {
			text("guild.mlinnotas.dest." + d[0], d[1], d[2]);
		}
		text("message.mlinnotas.civic.tower.raid", "A torre de vigia de %s toca o sino: saqueadores!", "The watchtower of %s rings its bell: raiders!");
		text("message.mlinnotas.civic.insurance", "Seguro de morte de %s: você renasce na estalagem e seus itens estão numa lápide do achados e perdidos em %s, %s, %s",
				"%s death insurance: you respawn at the inn and your items are in a lost-and-found gravestone at %s, %s, %s");
		text("message.mlinnotas.civic.insurance_respawn", "Seguro de morte de %s: você renasce na estalagem", "%s death insurance: you respawn at the inn");
	}

	static void block(String id, String pt, String en, String mineable, List<String> pickaxeMineable) throws IOException {
		StringBuilder variants = new StringBuilder();
		String[][] facings = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};
		for (String[] f : facings) {
			variants.append(variants.length() > 0 ? "," : "").append("\"facing=").append(f[0]).append("\":{\"model\":\"mlinnotas:block/").append(id)
					.append("\"").append(f[1].equals("0") ? "" : ",\"y\":" + f[1]).append("}");
		}
		ContentGen.write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{" + variants + "}}");
		ContentGen.write("assets/mlinnotas/models/block/" + id + ".json", "{\"parent\":\"minecraft:block/orientable\",\"textures\":{\"front\":\"mlinnotas:block/"
				+ id + "_front\",\"side\":\"mlinnotas:block/" + id + "_side\",\"top\":\"mlinnotas:block/" + id + "_top\"}}");
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
		ContentGen.name("block", id, pt, en);
		ContentGen.write("data/mlinnotas/loot_table/blocks/" + id + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"bonus_rolls\":0.0,\"condition\":{\"type\":\"minecraft:survives_explosion\"},\"entries\":["
				+ "{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + id + "\"}],\"rolls\":1.0}],"
				+ "\"random_sequence\":\"mlinnotas:blocks/" + id + "\"}");
		if (mineable.endsWith("pickaxe")) {
			pickaxeMineable.add("mlinnotas:" + id);
		} else {
			ContentGen.tag("block", mineable, List.of("mlinnotas:" + id), false);
		}
	}

	static void project(String id, String pt, String en, String descPt, String descEn) {
		text("village.mlinnotas.project." + id, pt, en);
		text("village.mlinnotas.project." + id + ".desc", descPt, descEn);
	}

	static void level(String kind, int level, String pt, String en) {
		text("village.mlinnotas.building." + kind + ".level." + level, pt, en);
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
