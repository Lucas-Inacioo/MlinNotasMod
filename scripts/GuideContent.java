import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Quests-guia das features que ainda nao tinham entrada no livro (masmorras, vilas, estruturas vanilla refeitas,
 * estacoes, mineracao em veio, ingredientes de encantamento e moldes de ferraria). Penduradas via
 * {@code QuestContent.EXTRA}, por isso roda antes do QuestContent.
 */
class GuideContent {
	static void generate() throws IOException {
		String[][] names = {
				{"minecraft.desert_pyramid", "Templo do Deserto", "Desert Temple"}, {"minecraft.jungle_pyramid", "Templo da Selva", "Jungle Temple"},
				{"minecraft.ruined_portal", "Portal em Ruínas", "Ruined Portal"}, {"minecraft.pillager_outpost", "Posto de Saqueadores", "Pillager Outpost"},
				{"minecraft.mineshaft", "Mina Abandonada", "Abandoned Mineshaft"}, {"minecraft.mansion", "Mansão da Floresta", "Woodland Mansion"},
				{"minecraft.monument", "Monumento Oceânico", "Ocean Monument"}, {"minecraft.shipwreck", "Naufrágio", "Shipwreck"},
				{"minecraft.ocean_ruin", "Ruínas Oceânicas", "Ocean Ruins"}, {"minecraft.igloo", "Iglu", "Igloo"},
				{"minecraft.swamp_hut", "Cabana da Bruxa", "Witch Hut"}, {"minecraft.trail_ruins", "Ruínas de Trilha", "Trail Ruins"},
				{"mlinnotas.quest/dungeons", "Uma masmorra", "A dungeon"}};
		for (String[] n : names) {
			QuestContent.text("quest.mlinnotas.structure." + n[0], n[1], n[2]);
		}
		ContentGen.write("data/mlinnotas/tags/worldgen/structure/quest/dungeons.json",
				"{\"values\":[\"mlinnotas:forgotten_crypt\",\"mlinnotas:sunken_hold\",\"mlinnotas:drowned_temple\",\"mlinnotas:sand_catacomb\"]}");

		add("basics", QuestContent.q("seasons", "minecraft:wheat_seeds", 4, 3, "Estações do Ano", "Seasons",
				"O mundo passa por primavera, verão, outono e inverno (8 dias cada; veja a linha embaixo do minimapa). Cada cultura cresce melhor numa estação e quase nada cresce no inverno ao ar livre: estufa com teto de vidro resolve. Veja a estação de cada semente no tooltip e no HUD (N).",
				"The world goes through spring, summer, autumn and winter (8 days each; see the line under the minimap). Each crop grows best in one season and almost nothing grows outdoors in winter: a glass-roofed greenhouse solves it. See each seed's season in its tooltip and in the HUD (N).")
				.dep("harvest").check().xp(20));

		add("mining", QuestContent.q("vein_mining", "minecraft:raw_iron", 3, 0, "Mineração em Veio", "Vein Mining",
				"Segure a tecla ` (crase) e quebre um minério com picareta: os minérios iguais conectados caem juntos. O tamanho máximo cresce com o nível de Mineração do mundo (8 no começo, até 32).",
				"Hold the ` (grave) key and break an ore with a pickaxe: connected ores of the same kind break together. The maximum grows with the world's Mining level (8 at first, up to 32).")
				.dep("skill_copper").check().xp(20));

		add("tech", QuestContent.q("enchant_reagents", "minecraft:blaze_powder", 1, 3, "Ingredientes de Encantamento", "Enchanting Reagents",
				"Todo encantamento pede, além de XP e lápis, um ingrediente temático no espaço abaixo do lápis: sílex para Afiação, açúcar para Eficiência, pó de blaze para Aspecto Flamejante, pena para Peso-Pena... Quanto maior o nível, mais itens. O nível máximo depende da Mineração do mundo (I até Ferro, II até Cobalto, III até Netherite, IV até Endita).",
				"Every enchantment needs, besides XP and lapis, a themed reagent in the slot below the lapis: flint for Sharpness, sugar for Efficiency, blaze powder for Fire Aspect, feather for Feather Falling... Higher levels need more. The maximum level depends on the world's Mining (I up to Iron, II up to Cobalt, III up to Netherite, IV up to Endite).")
				.dep("enchanting").check().xp(30));

		add("bosses", QuestContent.q("smithing_templates", "mlinnotas:infernal_upgrade_smithing_template", 1, 3, "Moldes de Ferraria", "Smithing Templates",
				"Depois da netherite, ferramentas, armaduras e armas sobem de nível na mesa de ferraria: peça do nível anterior + molde + 1 lingote. Moldes: Infernal (Colosso), do Fim (Dragão), Astral (Arauto) e Celestial (Wither). Duplique o molde na bancada (molde + 7 minérios brutos do nível + bloco base) para os dois jogadores.",
				"After netherite, tools, armor and weapons are upgraded at the smithing table: previous-tier piece + template + 1 ingot. Templates: Infernal (Colossus), End (Dragon), Astral (Herald) and Celestial (Wither). Duplicate a template at the crafting table (template + 7 raw ores of the tier + base block) so both players get one.")
				.dep("igneous_colossus").have("mlinnotas:infernal_upgrade_smithing_template", 1).xp(60));

		add("exploration", List.of(
				QuestContent.q("village_board", "mlinnotas:village_board", 4, 0, "Quadro de Missões", "Mission Board",
						"A vila onde vocês nascem tem um Quadro de Missões na praça. Projetos constroem casas, plantações, muralha e forja; missões do dia pagam esmeraldas; o mercado melhora conforme a vila sobe de Acampamento até Capital. Aldeões fora da sua vila param no nível 3.",
						"Your starting village has a Mission Board on its square. Projects build houses, farms, walls and a forge; daily missions pay emeralds; the market improves as the village grows from Camp to Capital. Villagers outside your village stop at level 3.")
						.dep("explore").check().xp(30),
				QuestContent.q("dungeon", "minecraft:iron_bars", 4, 1, "Masmorra", "Dungeon",
						"Ache uma masmorra (Cripta Esquecida em planícies/florestas, Fortaleza Anã em montanhas, Templo Afogado na praia, Catacumba de Areia no deserto). Desça os andares, cuidado com as placas de pressão e resolva o enigma do nicho.",
						"Find a dungeon (Forgotten Crypt in plains/forests, Sunken Hold in mountains, Drowned Temple on beaches, Sand Catacomb in deserts). Go down the floors, watch out for pressure plates and solve the alcove puzzle.")
						.dep("explore").structure("#mlinnotas:quest/dungeons").xp(60),
				QuestContent.q("dungeon_key", "mlinnotas:dungeon_key", 5, 1, "Chave da Masmorra", "Dungeon Key",
						"O mini-chefe da sala mais funda solta uma chave por jogador. Ela abre o Portão Selado (não gasta) e é gasta no cofre, que dá um prêmio para cada um.",
						"The mini-boss in the deepest room drops one key per player. It opens the Sealed Gate (not consumed) and is spent on the vault, which rewards each player.")
						.dep("dungeon").have("mlinnotas:dungeon_key", 1).xp(80),
				QuestContent.q("desert_temple", "minecraft:chiseled_sandstone", 4, 2, "Templo do Deserto", "Desert Temple",
						"Pirâmide com tumba do faraó e a câmara do tesouro: não pise na placa de pedra do centro (TNT embaixo).",
						"Pyramid with a pharaoh's tomb and the treasure chamber: don't step on the stone plate in the middle (TNT below).")
						.dep("explore").structure("minecraft:desert_pyramid").xp(40),
				QuestContent.q("jungle_temple", "minecraft:mossy_cobblestone", 5, 2, "Templo da Selva", "Jungle Temple",
						"Zigurate com armadilhas de flecha e um quebra-cabeça de 3 alavancas: as certas têm bloco cinzelado em cima.",
						"Ziggurat with arrow traps and a 3-lever puzzle: the right levers have a chiseled block above them.")
						.dep("explore").structure("minecraft:jungle_pyramid").xp(40),
				QuestContent.q("ruined_portal", "minecraft:crying_obsidian", 6, 2, "Portal em Ruínas", "Ruined Portal",
						"Portais quebrados com ouro e um baú. Completar a moldura de obsidiana é um atalho para o Nether.",
						"Broken portals with gold and a chest. Completing the obsidian frame is a shortcut to the Nether.")
						.dep("explore").structure("#minecraft:ruined_portal").xp(30),
				QuestContent.q("outpost", "minecraft:crossbow", 4, 3, "Posto de Saqueadores", "Pillager Outpost",
						"Paliçada com torre de 5 andares, capitão, jaula do golem de ferro e das allays.",
						"Palisade with a 5-floor tower, a captain, and cages with an iron golem and allays.")
						.dep("explore").structure("minecraft:pillager_outpost").xp(40),
				QuestContent.q("mineshaft", "minecraft:rail", 5, 3, "Mina Abandonada", "Abandoned Mineshaft",
						"Rede de túneis com salão central, carrinhos com baú, teias e geradores de aranha.",
						"Tunnel network with a central hall, chest minecarts, cobwebs and spider spawners.")
						.dep("explore").structure("#minecraft:mineshaft").xp(30),
				QuestContent.q("mansion", "minecraft:dark_oak_log", 6, 3, "Mansão da Floresta", "Woodland Mansion",
						"Três andares na floresta escura cheios de vindicadores e evocadores. Procure o cofre atrás das estantes e o sótão.",
						"Three floors in the dark forest full of vindicators and evokers. Look for the vault behind the bookshelves and the attic.")
						.dep("explore").structure("minecraft:mansion").xp(80),
				QuestContent.q("monument", "minecraft:prismarine_bricks", 4, 4, "Monumento Oceânico", "Ocean Monument",
						"Templo submerso com 3 guardiões anciões e 8 blocos de ouro no núcleo. Leve poções de respiração aquática e visão noturna.",
						"Sunken temple with 3 elder guardians and 8 gold blocks in the core. Bring water breathing and night vision potions.")
						.dep("explore").structure("minecraft:monument").xp(80),
				QuestContent.q("shipwreck", "minecraft:oak_boat", 5, 4, "Naufrágio", "Shipwreck",
						"Navios afundados ou encalhados. O baú do mapa na cabine aponta para um tesouro enterrado.",
						"Sunken or beached ships. The map chest in the cabin points to buried treasure.")
						.dep("explore").structure("#minecraft:shipwreck").xp(30),
				QuestContent.q("buried_treasure", "minecraft:heart_of_the_sea", 6, 4, "Tesouro Enterrado", "Buried Treasure",
						"Siga o mapa do tesouro até o X de cascalho e cave 3 blocos.",
						"Follow the treasure map to the gravel X and dig 3 blocks down.")
						.dep("shipwreck").have("minecraft:heart_of_the_sea", 1).xp(40),
				QuestContent.q("ocean_ruins", "minecraft:suspicious_sand", 4, 5, "Ruínas Oceânicas", "Ocean Ruins",
						"Templo submerso com afogados e areia suspeita para escovar.",
						"Underwater temple with drowned and suspicious sand to brush.")
						.dep("explore").structure("#minecraft:ocean_ruin").xp(30),
				QuestContent.q("igloo", "minecraft:snow_block", 5, 5, "Iglu", "Igloo",
						"Na neve. Embaixo do tapete há um alçapão para o laboratório com um aldeão zumbi para curar.",
						"In the snow. Under the carpet there's a trapdoor to the lab with a zombie villager to cure.")
						.dep("explore").structure("minecraft:igloo").xp(30),
				QuestContent.q("swamp_hut", "minecraft:cauldron", 6, 5, "Cabana da Bruxa", "Witch Hut",
						"Palafita no pântano com uma bruxa e um gato preto.",
						"Stilt hut in the swamp with a witch and a black cat.")
						.dep("explore").structure("minecraft:swamp_hut").xp(30),
				QuestContent.q("trail_ruins", "minecraft:brush", 7, 4.5, "Ruínas de Trilha", "Trail Ruins",
						"Vila soterrada: o poço de escavação mostra onde começar a escovar o cascalho suspeito.",
						"Buried village: the dig pit shows where to start brushing the suspicious gravel.")
						.dep("explore").structure("minecraft:trail_ruins").xp(30)));
	}

	static void add(String chapter, QuestContent.Q q) {
		add(chapter, List.of(q));
	}

	static void add(String chapter, List<QuestContent.Q> qs) {
		QuestContent.EXTRA.computeIfAbsent(chapter, k -> new ArrayList<>()).addAll(qs);
	}
}
