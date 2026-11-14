import java.io.IOException;
import java.util.List;

/**
 * Textos e tag do plano diretor (Vilas 2.0, fases 1 e 2) para o ContentGen: niveis novos, terraplenagem por anel,
 * contorno com ruas/quarteiroes/zonas, obra parada por terreno mexido e a tag do aterro. Chamado por ContentGen.main
 * depois do Village2Content.
 */
class Village3Content {
	static void generate() throws IOException {
		// --- niveis novos ---
		text("village.mlinnotas.level.metropolis", "Metrópole", "Metropolis");
		text("village.mlinnotas.level.crown_city", "Cidade da Coroa", "Crown City");
		text("gui.mlinnotas.village.need.ring", "Terraplenagem do anel %s pronta", "Ring %s earthworks done");

		// --- terraplenagem ---
		ContentGen.tag("item", "mlinnotas:village/fill", List.of("#minecraft:dirt", "minecraft:cobblestone", "minecraft:cobbled_deepslate",
				"minecraft:gravel"), true);
		text("village.mlinnotas.cost.mlinnotas.village.fill", "Terra ou pedregulho", "Dirt or cobblestone");
		text("gui.mlinnotas.village.terrain.name", "Terraplenagem do anel %s", "Ring %s earthworks");
		text("gui.mlinnotas.village.terrain.done", "Pronta: quarteirões no plato", "Done: blocks levelled");
		text("gui.mlinnotas.village.terrain.working", "Aplainando: %s%%", "Levelling: %s%%");
		text("gui.mlinnotas.village.terrain.next_level", "Abre o próximo nível da vila", "Opens the next village level");
		text("gui.mlinnotas.village.terrain.ready_count", "%s de %s colunas já na altura", "%s of %s columns already at height");
		text("gui.mlinnotas.village.terrain.desc",
				"Aplaina os quarteirões e as ruas do anel %s: corta o que sobra, aterra o que falta (pedra no fundo, muro de arrimo na borda "
						+ "alta), seca poças rasas, corta árvores e põe estacas nos cantos dos lotes. Nenhuma obra é feita em terreno cru, e o "
						+ "nível da vila só sobe com o anel pronto. O que você já aplainou a mão baixa o preço.",
				"Levels the blocks and streets of ring %s: cuts what is too high, fills what is too low (stone at the bottom, retaining wall "
						+ "on tall edges), drains shallow puddles, fells trees and stakes the lot corners. Nothing is ever built on raw ground, "
						+ "and the village only levels up once the ring is done. Whatever you already levelled by hand lowers the price.");
		text("gui.mlinnotas.village.terrain.earthwork", "Corte: %s blocos · aterro: %s · estoque de terra da vila: %s",
				"Cut: %s blocks · fill: %s · village earth stockpile: %s");
		text("message.mlinnotas.village.terrain.locked", "O anel %s ainda não abriu nesta vila", "Ring %s is not open in this village yet");
		text("message.mlinnotas.village.terrain.already", "A terraplenagem do anel %s já está paga", "Ring %s earthworks are already paid");
		text("message.mlinnotas.village.terrain.ready_by_hand", "O anel %s já está todo na altura: pronto sem custo!",
				"Ring %s is already at height everywhere: done for free!");
		text("message.mlinnotas.village.terrain.funded", "%s pagou a terraplenagem do anel %s de %s", "%s paid for ring %s earthworks in %s");
		text("message.mlinnotas.village.terrain.nothing", "Você não tem nada do que falta para o anel %s",
				"You have nothing of what ring %s still needs");
		text("message.mlinnotas.village.terrain.deposited", "Entrega aceita para o anel %s", "Delivery accepted for ring %s");
		text("message.mlinnotas.village.terrain.started", "%s começou a aplainar o anel %s", "%s started levelling ring %s");
		text("message.mlinnotas.village.terrain.done", "%s terminou a terraplenagem do anel %s: os quarteirões estão prontos para obras",
				"%s finished ring %s earthworks: the blocks are ready for buildings");

		// --- obra parada pelo terreno ---
		text("message.mlinnotas.village.paused",
				"A obra de %2$s em %1$s parou: o chão do lote foi mexido em %3$s, %4$s, %5$s. Acerte o chão nessa altura e ela continua sozinha.",
				"The %2$s in %1$s stopped: the lot ground was changed at %3$s, %4$s, %5$s. Put the ground back at that height and it resumes.");
		text("message.mlinnotas.village.resumed", "A obra de %2$s em %1$s voltou a andar", "The %2$s in %1$s is moving again");
		text("gui.mlinnotas.village.status.paused", "parada: chão mexido", "stopped: ground changed");
		text("gui.mlinnotas.village.status.unprepared", "falta terraplenagem", "needs earthworks");
		text("gui.mlinnotas.village.status.player", "lote do jogador", "player lot");

		// --- contorno do plano diretor ---
		text("gui.mlinnotas.village.plan.block", "Anel %s · plato em %s", "Ring %s · plateau at %s");
		text("gui.mlinnotas.village.plan.block_ready", "Anel %s · pronto (%s)", "Ring %s · ready (%s)");
		text("gui.mlinnotas.village.plan.player", "Lote do jogador", "Player lot");

		// --- escolha de lugar no plano diretor ---
		Village2Content.problem("zone", "Lote de outra zona", "Lot of another zone");
		Village2Content.problem("closed", "Anel ainda fechado", "Ring not open yet");
		Village2Content.problem("player", "Lote do jogador", "Player lot");
		for (String p : new String[] {"zone", "closed", "player"}) {
			text("message.mlinnotas.village.place_problem." + p, ContentGen.PT.get("gui.mlinnotas.village.place.problem." + p),
					ContentGen.EN.get("gui.mlinnotas.village.place.problem." + p));
		}
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
