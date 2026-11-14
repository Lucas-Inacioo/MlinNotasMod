import java.io.IOException;

/**
 * Textos das obras publicas gratuitas da vila (Vilas 2.0, fase 3) para o ContentGen: nome de cada obra, categorias do quadro,
 * tela de obras, placas e a regra de jogo. Chamado por ContentGen.main depois do Village3Content.
 */
class WorksContent {
	static void generate() throws IOException {
		text("gamerule.mlinnotas.village_works", "Obras públicas da vila (Mlin Notas)", "Village public works (Mlin Notas)");
		text("gamerule.mlinnotas.village_works.description", "A vila faz sozinha ruas, praça, árvores, pontes, estradas e muralha a cada nível",
				"The village builds streets, plaza, trees, bridges, roads and walls by itself at each level");

		// --- nomes das obras ---
		text("village.mlinnotas.work.streets.1", "Ruas: avenidas de cascalho com meio-fio", "Streets: gravel avenues with curbs");
		text("village.mlinnotas.work.streets.2", "Ruas: avenidas de pedregulho, anéis de cascalho", "Streets: cobblestone avenues, gravel rings");
		text("village.mlinnotas.work.streets.3", "Ruas: tijolo de pedra com sarjeta", "Streets: stone bricks with gutters");
		text("village.mlinnotas.work.streets.4", "Ruas: canteiro central com árvores", "Streets: tree-lined median");
		text("village.mlinnotas.work.streets.5", "Ruas: calçamento desenhado e postes duplos", "Streets: patterned paving and double lamp posts");
		text("village.mlinnotas.work.plaza.1", "Praça: calçamento, bancos e canteiros", "Plaza: paving, benches and flower beds");
		text("village.mlinnotas.work.plaza.2", "Praça: chafariz", "Plaza: fountain");
		text("village.mlinnotas.work.plaza.3", "Praça: coreto e calçamento de pedra", "Plaza: bandstand and stone paving");
		text("village.mlinnotas.work.plaza.4", "Praça: estátua do último chefe derrotado", "Plaza: statue of the last boss defeated");
		text("village.mlinnotas.work.plaza.5", "Praça: monumento com torre do relógio", "Plaza: monument with clock tower");
		text("village.mlinnotas.work.trees.1", "Árvores de rua", "Street trees");
		text("village.mlinnotas.work.gardens.1", "Jardins nos lotes minúsculos", "Gardens on the tiny lots");
		text("village.mlinnotas.work.decor.1", "Decoração de rua: bancos, barris e barracas", "Street decor: benches, barrels and stalls");
		for (int ring = 1; ring <= 6; ring++) {
			text("village.mlinnotas.work.stakes." + ring, "Estacas e placas dos lotes livres do anel " + ring, "Stakes and signs on the free lots of ring " + ring);
			text("village.mlinnotas.work.bridges." + ring, "Pontes do anel " + ring, "Bridges of ring " + ring);
		}
		text("village.mlinnotas.work.rural.1", "Hortas, pomares e pastos dos arredores", "Vegetable plots, orchards and pastures in the outskirts");
		text("village.mlinnotas.work.wall.palisade", "Paliçada de troncos na linha da muralha", "Log palisade on the wall line");
		text("village.mlinnotas.work.wall.gates", "Portões de madeira com guarita", "Wooden gates with a guardhouse");
		text("village.mlinnotas.work.wall.battlements", "Ameias, torres e grade de ferro dos portões", "Battlements, towers and iron portcullis gates");
		text("village.mlinnotas.work.roads.1", "Estradas até pedras de teleporte e outras vilas", "Roads to waystones and other villages");
		text("message.mlinnotas.village.work_done", "%s: obra pública pronta: %s", "%s: public work finished: %s");

		// --- placas ---
		text("sign.mlinnotas.free_lot", "Lote livre", "Free lot");
		text("sign.mlinnotas.road.waystone", "Pedra de teleporte", "Waystone");
		text("sign.mlinnotas.road.village", "Outra vila", "Another village");

		// --- tela de obras do quadro ---
		text("gui.mlinnotas.village.works.title", "Obras públicas", "Public works");
		text("gui.mlinnotas.village.works.open", "Obras públicas", "Public works");
		text("gui.mlinnotas.village.works.open_tooltip",
				"Obras gratuitas que a vila faz sozinha a cada nível: ruas, praça, árvores, pontes, estradas e muralha. Aqui você liga e desliga cada tipo.",
				"Free works the village does by itself at each level: streets, plaza, trees, bridges, roads and walls. Switch each kind on or off here.");
		text("gui.mlinnotas.village.works.hint", "Só depois das obras pagas e só em chão aplainado", "Only after paid works, only on levelled ground");
		text("gui.mlinnotas.village.works.empty", "Nenhuma obra pública ainda", "No public works yet");
		text("gui.mlinnotas.village.works.paid_running", "Obra paga em andamento: as públicas esperam", "A paid work is running: public works wait");
		text("gui.mlinnotas.village.works.on", "sim", "on");
		text("gui.mlinnotas.village.works.off", "não", "off");
		text("gui.mlinnotas.village.works.state.running", "%s%%", "%s%%");
		text("gui.mlinnotas.village.works.state.done", "pronta", "done");
		text("gui.mlinnotas.village.works.state.replaced", "trocada", "replaced");
		text("gui.mlinnotas.village.works.state.queued", "na fila", "queued");
		text("gui.mlinnotas.village.works.state.off", "desligada", "switched off");
		category("streets", "Ruas", "Streets", "Material das avenidas, anéis e travessas: cascalho, pedra, canteiro central e postes.",
				"Material of avenues, rings and lanes: gravel, stone, median and lamp posts.");
		category("plaza", "Praça", "Plaza", "Calçamento, bancos, chafariz, coreto, estátua e monumento da praça central.",
				"Paving, benches, fountain, bandstand, statue and monument of the central plaza.");
		category("green", "Verde", "Green", "Árvores de rua e jardins nos lotes minúsculos.", "Street trees and gardens on the tiny lots.");
		category("decor", "Decoração", "Decor", "Bancos, barris e barracas nas calçadas.", "Benches, barrels and stalls on the sidewalks.");
		category("subdivision", "Lotes livres", "Free lots", "Estacas e placas de Lote livre nos lotes vagos dos anéis novos.",
				"Stakes and Free lot signs on the vacant lots of new rings.");
		category("rural", "Zona rural", "Rural", "Hortas, pomares e pastos cercados nos lotes vagos dos arredores.",
				"Fenced vegetable plots, orchards and pastures on the vacant lots of the outskirts.");
		category("bridges", "Pontes", "Bridges", "Pontes de pedra ou tábuas sobre água funda nas ruas do plano.", "Stone or plank bridges over deep water on the planned streets.");
		category("roads", "Estradas", "Roads", "Estradas de cada avenida até pedras de teleporte e outras vilas a até 300 blocos.",
				"Roads from each avenue to waystones and other villages up to 300 blocks away.");
		category("wall", "Muralha", "Walls", "Paliçada, portões, ameias e grade de ferro dos portões.", "Palisade, gates, battlements and iron portcullis gates.");
	}

	private static void category(String id, String pt, String en, String tipPt, String tipEn) {
		text("gui.mlinnotas.village.works.cat." + id, pt, en);
		text("gui.mlinnotas.village.works.cat." + id + ".tooltip", tipPt, tipEn);
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
