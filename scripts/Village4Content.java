import java.io.IOException;

/**
 * Textos das construcoes com nivel (Vilas 2.0, fase 4) para o ContentGen: Prefeitura, numerais dos niveis, o que cada
 * nivel traz, botoes e mensagens de melhoria, aba Necessidades, trabalhadores parados, conserto na forja, quarto na
 * estalagem e as ofertas novas do mercado. Chamado por ContentGen.main depois do Village3Content.
 */
class Village4Content {
	static void generate() throws IOException {
		String[] roman = {"I", "II", "III", "IV", "V"};
		for (int i = 0; i < 5; i++) {
			text("village.mlinnotas.building.level." + (i + 1), roman[i], roman[i]);
		}

		// --- prefeitura ---
		text("village.mlinnotas.project.town_hall", "Prefeitura", "Town Hall");
		text("village.mlinnotas.project.town_hall.desc",
				"Salão do conselho com atril, mesa de mapas e estantes. Limita o nível de todas as outras construções (sem ela, tudo fica no "
						+ "nível I) e abre o painel de necessidades da vila. Ela mesma sobe até o nível que a vila deixa: Aldeia I, Vila II, "
						+ "Cidade III, Capital IV, Metrópole V.",
				"Council hall with a lectern, map table and bookshelves. Caps the level of every other building (without it, everything "
						+ "stays at level I) and opens the village needs panel. It can itself go up to what the village allows: Hamlet I, "
						+ "Village II, Town III, Capital IV, Metropolis V.");

		// --- o que cada nivel traz ---
		level("house", 2, "Sobrado de dois andares: 3 moradores, cozinha e varanda.", "Two-storey house: 3 residents, kitchen and porch.");
		level("house", 3, "Casarão de três andares: 4 moradores, lareira, escritório e jardim.",
				"Three-storey manor: 4 residents, fireplace, study and garden.");
		level("farm", 2, "Três canteiros e dois canais: alimenta 8.", "Three beds and two channels: feeds 8.");
		level("farm", 3, "Galpão com baú, composteiras e feno: alimenta 12.", "Shed with chest, composters and hay: feeds 12.");
		level("smithy", 2, "Oficina maior: o mercado vende ferramentas e lingotes de cobalto; conserto mais barato.",
				"Bigger workshop: the market sells cobalt tools and ingots; cheaper repairs.");
		level("smithy", 3, "Sala de armas: conserto com menos material.", "Armoury: repairs need less material.");
		level("smithy", 4, "Andar de depósito: o ferreiro chega a especialista.", "Storage floor: the smith can reach expert.");
		level("smithy", 5, "Chaminé alta e lava: o ferreiro chega a mestre; conserto mais barato de todos.",
				"Tall chimney and lava: the smith can reach master; cheapest repairs.");
		level("market", 2, "Quatro barracas: mais comércio.", "Four stalls: more commerce.");
		level("market", 3, "Mercado de duas fileiras com livreiro: vende livros raros.", "Two-row market with a bookseller: sells rare books.");
		level("inn", 2, "Dois andares de quartos: hospede-se no mercado para renascer aqui.",
				"Two floors of rooms: buy lodging at the market to respawn here.");
		level("inn", 3, "Três andares: mais lazer e moradia.", "Three floors: more leisure and housing.");
		level("library", 2, "Galeria no andar de cima: mais lazer.", "Upper gallery: more leisure.");
		level("library", 3, "Sala dos copistas: o bibliotecário chega a especialista com a vila.",
				"Copyists' room: the librarian can reach expert with the village.");
		level("town_hall", 2, "Andar de escritórios: as outras construções sobem até o II.", "Office floor: other buildings can reach II.");
		level("town_hall", 3, "Pórtico de colunas: construções até o III.", "Columned portico: buildings up to III.");
		level("town_hall", 4, "Torre do relógio: construções até o IV.", "Clock tower: buildings up to IV.");
		level("town_hall", 5, "Arquivo no terceiro andar: construções até o V (mestres na vila).",
				"Archive on the third floor: buildings up to V (masters in the village).");

		// --- aba Obras ---
		text("gui.mlinnotas.village.row.built_level", "%s %s", "%s %s");
		text("gui.mlinnotas.village.upgrade", "Melhorar → %s", "Upgrade → %s");
		text("gui.mlinnotas.village.upgrade_max", "Nível máximo", "Max level");
		text("gui.mlinnotas.village.upgrade_tooltip", "%s %s", "%s %s");
		text("gui.mlinnotas.village.upgrade_capped", "A Prefeitura precisa estar no %s", "The Town Hall must be at %s");
		text("gui.mlinnotas.village.upgrade_capped_hall", "A vila precisa subir de nível para a Prefeitura chegar ao %s",
				"The village must level up before the Town Hall can reach %s");
		text("gui.mlinnotas.village.upgrade_cancel", "Cancelar", "Cancel");
		text("gui.mlinnotas.village.upgrade_cancel_tooltip", "Cancela a melhoria (ainda não começada) e devolve tudo o que foi entregue",
				"Cancels the upgrade (not started yet) and gives back everything delivered");
		text("gui.mlinnotas.village.upgrade_queued", "melhoria para %s na fila", "upgrade to %s queued");
		text("gui.mlinnotas.village.upgrade_building", "melhorando: %s%%", "upgrading: %s%%");
		text("gui.mlinnotas.village.idle", "parada: falta morador", "idle: no free resident");

		// --- nivel da vila ---
		text("gui.mlinnotas.village.need.building_level", "%s no nível %s", "%s at level %s");
		text("gui.mlinnotas.village.need.needs", "Todas as necessidades acima de %s%%", "Every need above %s%%");

		// --- aba Necessidades ---
		text("gui.mlinnotas.village.tab.needs", "Necessidades", "Needs");
		text("gui.mlinnotas.village.needs.head", "Moradores: %s · casas para %s · trabalhando %s de %s",
				"Residents: %s · homes for %s · working %s of %s");
		text("gui.mlinnotas.village.needs.idle", "%s parada(s)", "%s idle");
		text("gui.mlinnotas.village.needs.supply", "Oferta %s de %s", "Supply %s of %s");
		text("gui.mlinnotas.village.needs.daily", "Por dia: %s de prosperidade. %s", "Per day: %s prosperity. %s");
		text("gui.mlinnotas.village.needs.town_rule", "Da Cidade em diante, subir exige todas acima de %s%%.",
				"From Town on, levelling up needs all above %s%%.");
		need("housing", "Moradia", "Housing", "Camas das casas e da estalagem (e casas de jogador com cama e porta) para os aldeões que moram na vila.",
				"Beds in houses and the inn (and player houses with a bed and door) for the villagers living here.");
		need("food", "Comida", "Food", "Plantações alimentam a vila; quanto maior o nível, mais gente. Celeiro e moinho virão depois.",
				"Farms feed the village; higher levels feed more people. Granary and mill come later.");
		need("safety", "Segurança", "Safety", "Muralha, golens de ferro, lampiões, forja e Prefeitura.",
				"Wall, iron golems, lamps, smithy and town hall.");
		need("light", "Luz", "Light", "Lampiões, postes acesos nas ruas e a praça.", "Lamps, lit street posts and the plaza.");
		need("leisure", "Lazer e fé", "Leisure & faith", "Praça, estalagem, biblioteca e Prefeitura. A igreja virá depois.",
				"Plaza, inn, library and town hall. The church comes later.");
		need("commerce", "Comércio", "Commerce", "Mercado (e um pouco da forja).", "Market (and a little from the smithy).");

		// --- mensagens ---
		text("message.mlinnotas.village.upgrade_name", "%s %s", "%s %s");
		text("message.mlinnotas.village.upgrade_no_space", "A planta maior de %s não cabe neste lote antigo",
				"The bigger plan of the %s does not fit this old lot");
		text("message.mlinnotas.village.upgrade_capped", "%s: a Prefeitura ainda não deixa esse nível", "%s: the Town Hall does not allow that level yet");
		text("message.mlinnotas.village.upgrade_capped_hall", "%s: a vila ainda não deixa esse nível", "%s: the village does not allow that level yet");
		text("message.mlinnotas.village.upgrade_funded", "%s pagou a melhoria %s em %s", "%s paid for the upgrade %s in %s");
		text("message.mlinnotas.village.upgrade_cancelled", "%s cancelou a melhoria de %s em %s (%s itens devolvidos)",
				"%s cancelled the %s upgrade in %s (%s items returned)");
		text("message.mlinnotas.village.upgrading", "%s começou a melhorar %s para o nível %s", "%s started upgrading the %s to level %s");
		text("message.mlinnotas.village.upgraded", "%s: %s chegou ao nível %s (+%s de prosperidade)", "%s: the %s reached level %s (+%s prosperity)");
		text("message.mlinnotas.village.needs_building", "Precisa de %s %s funcionando na vila", "Needs a working %s %s in the village");
		text("message.mlinnotas.village.repair_cost", "O conserto custa %s esmeraldas e %s do material do item",
				"The repair costs %s emeralds and %s of the item's material");
		text("message.mlinnotas.village.repaired", "Consertado na forja (%s esmeraldas, %s de material)",
				"Repaired at the smithy (%s emeralds, %s material)");
		text("message.mlinnotas.village.lodged", "Quarto reservado: você renasce na estalagem (%s, %s, %s)",
				"Room booked: you will respawn at the inn (%s, %s, %s)");

		// --- mercado ---
		text("gui.mlinnotas.village.building_short", "%s %s", "%s %s");
		text("gui.mlinnotas.village.forge_repair_price", "Forja %s: esmeraldas + material", "Smithy %s: emeralds + material");
		text("village.mlinnotas.shop.repair", "Conserto na forja", "Smithy repair");
		text("village.mlinnotas.shop.repair.desc",
				"O ferreiro conserta o item da sua mão principal sem bigorna: esmeraldas e um pouco do material de conserto do item "
						+ "(lingotes, diamantes, tábuas...). Quanto maior o nível da forja, mais barato.",
				"The smith repairs the item in your main hand without an anvil: emeralds and a little of the item's repair material "
						+ "(ingots, diamonds, planks...). The higher the smithy level, the cheaper.");
		shop("cobalt_pickaxe", "Picareta de cobalto", "Cobalt pickaxe", "Forjada aqui (forja II).", "Forged here (smithy II).");
		shop("cobalt_sword", "Espada de cobalto", "Cobalt sword", "Forjada aqui (forja II).", "Forged here (smithy II).");
		shop("cobalt_ingot", "Lingote de cobalto", "Cobalt ingot", "Do estoque da forja (forja II).", "From the smithy stock (smithy II).");
		shop("lodging", "Quarto na estalagem", "Inn room",
				"Seu ponto de renascer passa a ser o andar dos quartos da estalagem (estalagem II).",
				"Your respawn point becomes the inn's room floor (inn II).");
	}

	static void level(String kind, int level, String pt, String en) {
		text("village.mlinnotas.building." + kind + ".level." + level, pt, en);
	}

	static void need(String id, String pt, String en, String descPt, String descEn) {
		text("gui.mlinnotas.village.needs." + id, pt, en);
		text("gui.mlinnotas.village.needs." + id + ".desc", descPt, descEn);
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
