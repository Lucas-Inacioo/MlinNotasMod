import java.io.IOException;
import java.util.List;

/**
 * Textos e arquivos das construcoes de oficios e servicos (Vilas 2.0, fase 6) para o ContentGen: nomes e descricoes das
 * nove obras, o que cada nivel traz, ofertas novas do mercado (mochila do alfaiate, flechas com pocao, boatos da
 * taverna), mensagens da igreja, da taverna, do encantador e da feira, a tina de curtir (modelo, estado, item, loot,
 * nome) e a receita da mesa de encantamento desligada (so se encanta no Encantador). Chamado por ContentGen.main
 * depois do Village4Content.
 */
class CraftsContent {
	static void generate() throws IOException {
		// --- obras ---
		project("quarry", "Pedreira", "Quarry",
				"Frente de rocha em terraços, cabana do pedreiro e um baú que enche aos poucos de pedra, pedregulho e tijolos. Também baixa o preço da terraplenagem dos anéis. Chega um pedreiro.",
				"A rock face in terraces, the mason's hut and a chest that slowly fills with stone, cobblestone and bricks. It also lowers the ring terrain price. A mason arrives.");
		project("tailor", "Alfaiataria", "Tailor",
				"Casa de enxaimel com teares, toldo listrado e varal de lã tingida. O mercado passa a vender a mochila (mais barata no nível II). Chega um tecelão.",
				"A half-timbered house with looms, a striped awning and a line of dyed wool. The market starts selling the backpack (cheaper at level II). A weaver arrives.");
		project("tannery", "Curtume", "Tannery",
				"Cabana do curtidor e pátio com tinas: o único lugar onde a carne podre vira couro. Chega um curtidor.",
				"A tanner's hut and a yard with vats: the only place where rotten flesh becomes leather. A leatherworker arrives.");
		project("fletcher", "Flecharia", "Fletcher",
				"Oficina do flecheiro e um campo de tiro com alvos. O mercado passa a vender flechas com poção. Chega um flecheiro.",
				"The fletcher's workshop and a target range. The market starts selling tipped arrows. A fletcher arrives.");
		project("enchanter", "Encantador", "Enchanter",
				"Salão de pedra com a mesa de encantamento no centro de um anel de estantes: o único lugar onde se encanta. O nível da casa limita o nível dos encantamentos (I a V). Chega um bibliotecário.",
				"A stone hall with the enchanting table inside a ring of bookshelves: the only place to enchant. The house level caps enchantment levels (I to V). A librarian arrives.");
		project("tavern", "Taverna", "Tavern",
				"Salão com balcão, barris e lareira. Vende boatos sobre estruturas que ninguém achou ainda e traz uma missão extra por dia. Chega um taverneiro.",
				"A hall with a bar, barrels and a fireplace. Sells rumors about structures nobody has found yet and brings an extra bounty every day. A tavern keeper arrives.");
		project("garden", "Jardim", "Garden",
				"Lago, ponte, flores da estação e bancos. Pequeno o bastante para qualquer lote. Dá lazer à vila.",
				"A pond, a bridge, seasonal flowers and benches. Small enough for any lot. Gives the village leisure.");
		project("church", "Igreja", "Church",
				"Nave alta com altar, bancos e campanário. O sino reúne os aldeões ao anoitecer e o altar abençoa quem o usa (uma vez por dia). No nível III ganha cemitério. A maior fonte de lazer e fé. Chega um clérigo.",
				"A tall nave with an altar, pews and a bell tower. The bell gathers villagers at dusk and the altar blesses whoever uses it (once a day). Level III adds a graveyard. The biggest source of leisure and faith. A cleric arrives.");
		project("empty_lot", "Lote vazio", "Empty Lot",
				"Um patamar preparado com estacas para você construir a sua casa. A vila nunca mexe nele; com cama e porta lá dentro, conta como uma casa da vila.",
				"A prepared plateau with stakes for you to build your own house. The village never touches it; with a bed and a door inside it counts as a village house.");

		// --- o que cada nivel traz ---
		level("quarry", 1, "Frente de rocha, cabana e baú que enche: 60 itens por dia.", "Rock face, hut and a filling chest: 60 items a day.");
		level("quarry", 2, "Guindaste, telheiro e terraços mais altos: 120 itens por dia e mais desconto na terraplenagem.",
				"Crane, shed and taller terraces: 120 items a day and a bigger terrain discount.");
		level("quarry", 3, "Arco de pedra e cortadores ao ar livre: 240 itens por dia (com ardósia) e o maior desconto.",
				"Stone arch and open-air cutters: 240 items a day (with deepslate) and the biggest discount.");
		level("tailor", 1, "Dois teares, balcão e varal: o mercado vende a mochila.", "Two looms, a counter and a clothesline: the market sells the backpack.");
		level("tailor", 2, "Sobrado com loft de teares: a mochila fica mais barata.", "Two-storey house with a loom loft: the backpack gets cheaper.");
		level("tannery", 1, "Uma tina de curtir: 8 espaços, 1 couro para cada 2 carnes podres em meio dia.",
				"One tanning vat: 8 slots, 1 leather per 2 rotten flesh in half a day.");
		level("tannery", 2, "Segunda tina, cobertura e fervura: 1 couro para cada carne podre.", "A second vat, a cover and a boiling pit: 1 leather per rotten flesh.");
		level("fletcher", 1, "Oficina e campo de tiro com dois alvos: flechas com poção à venda.", "Workshop and a two-target range: tipped arrows for sale.");
		level("fletcher", 2, "Sobrado, quatro alvos e cobertura na linha de tiro: flechas de dano.", "Two storeys, four targets and a covered firing line: harming arrows.");
		level("fletcher", 3, "Cinco alvos com muro de contenção e lanternas: flechas mais baratas.", "Five targets with a backstop and lanterns: cheaper arrows.");
		level("enchanter", 1, "Mesa com estantes atrás: encantamentos até o nível I.", "A table with shelves behind it: enchantments up to level I.");
		level("enchanter", 2, "Anel de estantes e pavilhões de alquimia: até o nível II (se a Mineração deixar).",
				"A bookshelf ring and alchemy pavilions: up to level II (if Mining allows).");
		level("enchanter", 3, "Andar de cima com galeria e anel de duas alturas: até o nível III.", "An upper gallery and a two-high ring: up to level III.");
		level("enchanter", 4, "Torre de estudo com cúpula de vidro: até o nível IV.", "A study tower with a glass dome: up to level IV.");
		level("enchanter", 5, "Segunda torre e postes de lanterna: sem limite da casa.", "A second tower and lamp posts: no house limit.");
		level("tavern", 1, "Salão, balcão e mural de boatos: 1 boato por dia por jogador e uma missão extra.",
				"Hall, bar and a rumor board: 1 rumor a day per player and an extra bounty.");
		level("tavern", 2, "Quartos de hóspedes em cima: 2 boatos por dia.", "Guest rooms upstairs: 2 rumors a day.");
		level("tavern", 3, "Jardim de mesas e barris: 3 boatos por dia e duas missões extras.", "A table garden and barrels: 3 rumors a day and two extra bounties.");
		level("garden", 1, "Lago e flores da estação.", "A pond and seasonal flowers.");
		level("garden", 2, "Ponte sobre o lago e lanternas: mais lazer.", "A bridge over the pond and lanterns: more leisure.");
		level("garden", 3, "Caramanchão de entrada: o máximo de lazer.", "An entrance arbor: the most leisure.");
		level("church", 1, "Nave, altar e campanário com sino: bênção diária.", "Nave, altar and bell tower: a daily blessing.");
		level("church", 2, "Mais bancos, candelabros e campanário mais alto: mais lazer e fé.", "More pews, candelabra and a taller bell tower: more leisure and faith.");
		level("church", 3, "Cemitério cercado e pórtico: o máximo de lazer e fé.", "A fenced graveyard and a portico: the most leisure and faith.");

		// --- necessidades: a igreja chegou ---
		text("gui.mlinnotas.village.needs.leisure.desc", "Praça, jardim, estalagem, taverna, biblioteca, Prefeitura e, sobretudo, a igreja.",
				"Plaza, garden, inn, tavern, library, town hall and, above all, the church.");

		// --- mercado ---
		VillageContent.shop("backpack", "Mochila", "Backpack", "Costurada na alfaiataria (alfaiataria II: mais barata).",
				"Sewn at the tailor (tailor II: cheaper).");
		VillageContent.shop("tipped_poison", "Flechas de veneno", "Poison arrows", "Oito flechas com veneno, da flecharia (nível II: mais baratas).",
				"Eight poison-tipped arrows from the fletcher (level II: cheaper).");
		VillageContent.shop("tipped_slowness", "Flechas de lentidão", "Slowness arrows", "Oito flechas com lentidão, da flecharia (nível II: mais baratas).",
				"Eight slowness-tipped arrows from the fletcher (level II: cheaper).");
		VillageContent.shop("tipped_harming", "Flechas de dano", "Harming arrows", "Oito flechas de dano instantâneo, da flecharia nível II.",
				"Eight instant-damage arrows from a level II fletcher.");
		VillageContent.shop("rumor_ruin", "Boato: ruína", "Rumor: ruin", "O taverneiro conta onde fica uma ruína de superfície que ninguém achou. Limite diário pelo nível da taverna.",
				"The tavern keeper tells where an unfound surface ruin is. Daily limit by tavern level.");
		VillageContent.shop("rumor_vanilla", "Boato: construção antiga", "Rumor: old structure", "Templo, posto de saqueadores, mina, mansão, monumento... a posição de uma que ninguém achou.",
				"Temple, outpost, mine, mansion, monument... the position of one nobody has found.");
		VillageContent.shop("rumor_dungeon", "Boato: masmorra", "Rumor: dungeon", "A posição de uma masmorra que ninguém achou.", "The position of a dungeon nobody has found.");
		VillageContent.shop("rumor_lair", "Boato: covil de chefe", "Rumor: boss lair", "A posição de um covil de chefe que a Mineração do mundo já permite enfrentar.",
				"The position of a boss lair the world's Mining level already allows.");
		VillageContent.text("village.mlinnotas.cost.minecraft.flowers", "Flores (quaisquer)", "Flowers (any)");

		// --- mensagens ---
		VillageContent.text("message.mlinnotas.church.blessed", "O altar abençoa você: regeneração por alguns segundos", "The altar blesses you: regeneration for a few seconds");
		VillageContent.text("message.mlinnotas.church.already", "O altar já abençoou você hoje", "The altar has already blessed you today");
		VillageContent.text("message.mlinnotas.tavern.no_more", "O taverneiro só conta %s boato(s) por dia a você", "The tavern keeper tells you only %s rumor(s) a day");
		VillageContent.text("message.mlinnotas.tavern.nothing", "O taverneiro não ouviu nada de novo (nada que você ainda possa enfrentar ou que ninguém tenha achado)",
				"The tavern keeper has heard nothing new (nothing you can face yet or that nobody has found)");
		VillageContent.text("message.mlinnotas.tavern.rumor", "%s ouviu na taverna de %s: há %s perto de X %s, Z %s", "%s heard at the %s tavern: there is %s near X %s, Z %s");
		VillageContent.text("message.mlinnotas.enchanter.only", "Só se encanta no Encantador da vila (Cidade ou maior)", "You can only enchant at the village Enchanter (Town or bigger)");
		VillageContent.text("message.mlinnotas.fair.open", "A feira da semana chegou à praça de %s: mercadores itinerantes com raridades!",
				"The weekly fair has arrived at the %s plaza: traveling merchants with rarities!");
		VillageContent.text("entity.mlinnotas.fair_merchant", "Mercador da feira", "Fair merchant");
		VillageContent.text("gui.mlinnotas.quests.rumors", "Boatos da taverna", "Tavern rumors");
		VillageContent.text("gui.mlinnotas.quests.rumor_line", "%s: X %s, Z %s", "%s: X %s, Z %s");
		VillageContent.text("message.mlinnotas.rumor.waypoint", "Boato guardado no mapa: %s", "Rumor saved on the map: %s");

		// --- tina de curtir ---
		ContentGen.write("assets/mlinnotas/models/block/tanning_vat.json", "{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{"
				+ "\"side\":\"mlinnotas:block/tanning_vat_side\",\"top\":\"mlinnotas:block/tanning_vat_top\",\"bottom\":\"mlinnotas:block/tanning_vat_bottom\"}}");
		ContentGen.write("assets/mlinnotas/blockstates/tanning_vat.json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/tanning_vat\"}}}");
		FoodContent.blockItem("tanning_vat");
		ContentGen.name("block", "tanning_vat", "Tina de Curtir", "Tanning Vat");
		FoodContent.dropSelf("tanning_vat");
		ContentGen.tag("block", "minecraft:mineable/axe", List.of("mlinnotas:tanning_vat"), false);
		VillageContent.text("container.mlinnotas.tanning_vat", "Tina de Curtir", "Tanning Vat");
		VillageContent.text("block.mlinnotas.tanning_vat.tooltip", "Curte carne podre em couro dentro de um curtume (8 espaços, meio dia)",
				"Tans rotten flesh into leather inside a tannery (8 slots, half a day)");

		// --- so se encanta no Encantador: a mesa vanilla vira uma receita impossivel (nivel 99 de Mineracao + barreira); o id
		// continua existindo porque o jogo exige a receita (conquistas e livro de receitas apontam para ela)
		ContentGen.write("data/minecraft/recipe/enchanting_table.json", "{\"type\":\"mlinnotas:skill_gated_shapeless\",\"category\":\"misc\","
				+ "\"skill\":\"mining\",\"level\":99,\"ingredients\":[\"minecraft:barrier\",\"minecraft:book\"],"
				+ "\"result\":{\"id\":\"minecraft:enchanting_table\"}}");
	}

	static void project(String id, String pt, String en, String descPt, String descEn) {
		VillageContent.project(id, pt, en, descPt, descEn);
	}

	static void level(String kind, int level, String pt, String en) {
		VillageContent.text("village.mlinnotas.building." + kind + ".level." + level, pt, en);
	}

	static void text(String key, String pt, String en) {
		VillageContent.text(key, pt, en);
	}
}
