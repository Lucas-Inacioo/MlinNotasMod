import java.io.IOException;

/**
 * Textos da fase 8 das Vilas 2.0: Metropole e Cidade da Coroa, muralha externa, centro historico e o anel rural fora da
 * muralha (terraplenagem, cerca viva, postes). Chamado por ContentGen.main.
 */
class CrownContent {
	static void generate() throws IOException {
		text("village.mlinnotas.project.outer_wall", "Muralha externa", "Outer wall");
		text("village.mlinnotas.project.outer_wall.desc",
				"Muralha de pedra de 149x149 em volta da cidade inteira, com adarve e ameias, torres salientes nos cantos e no meio dos lados e portões em arco onde as avenidas passam para o anel rural. Sobe do chão de fora até acima do nível da cidade; onde a linha passa por construção sua ou água funda fica um vão (aparece no contorno). Vêm dois golens de guarda. Exigida para a Cidade da Coroa, que ainda ganha barbacãs na frente dos portões dos eixos.",
				"A 149x149 stone wall around the whole city, with a wall-walk and battlements, towers jutting out at the corners and along the sides, and arched gates where the avenues pass into the rural ring. It rises from the ground outside to above the city level; where the line crosses something you built or deep water it leaves a gap (shown in the outline). Two guard golems arrive. Required for the Crown City, which also adds barbicans in front of the axis gates.");
		text("gui.mlinnotas.village.terrain.rural", "Terraplenagem do anel rural", "Rural ring earthworks");
		text("message.mlinnotas.village.rural_open",
				"%s ganhou o anel rural: campos fora da muralha externa para plantações, moinho, pomar, curral, apiário, vinícola, serraria, pedreira e curtume. Pague a terraplenagem do anel rural no quadro para começar.",
				"%s gained the rural ring: fields outside the outer wall for farms, mill, orchard, pen, apiary, winery, sawmill, quarry and tannery. Pay for the rural ring earthworks at the board to start.");
		text("village.mlinnotas.work.stakes.7", "Estacas e placas dos lotes livres do anel rural", "Stakes and signs of the free lots in the rural ring");
		text("village.mlinnotas.work.bridges.7", "Pontes do anel rural", "Bridges of the rural ring");
		text("village.mlinnotas.work.fields.hedge", "Cerca viva e porteiras do anel rural", "Hedge and gateposts of the rural ring");
		text("village.mlinnotas.work.fields.lamps", "Postes de lanterna nos campos", "Lamp posts in the fields");
		text("village.mlinnotas.work.wall.historic", "Centro histórico: arcos abertos e passeio na muralha", "Historic centre: open arches and a promenade on the wall");
		text("village.mlinnotas.work.wall.barbican", "Barbacãs dos portões da muralha externa", "Barbicans of the outer wall gates");
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
