import java.io.IOException;

/**
 * Textos do plano da vila para o ContentGen: contorno no mundo, escolha de lugar, aba Obras (cancelar e demolir com
 * devolucao), mensagens e comandos de reparo. Chamado por ContentGen.main depois do VillageContent.
 */
class Village2Content {
	static void generate() throws IOException {
		// --- contorno ---
		text("gui.mlinnotas.village.area_show", "Mostrar área", "Show area");
		text("gui.mlinnotas.village.area_hide", "Esconder área", "Hide area");
		text("gui.mlinnotas.village.area_tooltip",
				"Desenha no mundo o raio da vila, os lotes planejados (verde = livre, amarelo = tem árvores que a obra corta, vermelho = "
						+ "bloqueado por construção sua, azul = pronto), as ruas, os postes que faltam e os blocos que atrapalham.",
				"Draws the village radius in the world, the planned lots (green = free, yellow = has trees the builders will cut, red = "
						+ "blocked by your blocks, blue = built), the roads, missing lamp posts and the blocks in the way.");
		text("gui.mlinnotas.village.status.free", "livre", "free");
		text("gui.mlinnotas.village.status.clear", "precisa limpar (árvores)", "needs clearing (trees)");
		text("gui.mlinnotas.village.status.blocked", "bloqueado por construção", "blocked by buildings");
		text("gui.mlinnotas.village.status.water", "bloqueado: água", "blocked: water");
		text("gui.mlinnotas.village.status.steep", "inclinado demais", "too steep");
		text("gui.mlinnotas.village.status.built", "construído", "built");
		text("gui.mlinnotas.village.status.building", "em obra", "under construction");
		text("village.mlinnotas.project.campfire", "Fogueira", "Campfire");
		text("village.mlinnotas.project.legacy", "Obra antiga", "Old building");

		// --- aba Obras ---
		text("gui.mlinnotas.village.tab.buildings", "Obras", "Buildings");
		text("gui.mlinnotas.village.buildings_hint", "Fila de obras, onde vai cada uma e o que já está pronto.",
				"Build queue, where each one goes and what is already built.");
		text("gui.mlinnotas.village.buildings_empty", "Nada na fila e nenhuma obra pronta ainda.", "Nothing queued and nothing built yet.");
		text("gui.mlinnotas.village.row.queued", "Na fila: %s (#%s)", "Queued: %s (#%s)");
		text("gui.mlinnotas.village.row.waiting", "Esperando lote livre: use Mostrar área para ver o que limpar",
				"Waiting for a free lot: use Show area to see what to clear");
		text("gui.mlinnotas.village.row.free", "%s: %s lote(s) livre(s)", "%s: %s free lot(s)");
		text("gui.mlinnotas.village.row.chosen", "Próxima vai no lugar que você escolheu", "Next one goes where you chose");
		text("gui.mlinnotas.village.row.auto", "Lugar automático (o mais perto da praça)", "Automatic spot (closest to the plaza)");
		text("gui.mlinnotas.village.row.built", "%s pronto", "%s built");
		text("gui.mlinnotas.village.row.where", "%s blocos ao %s do quadro", "%s blocks %s of the board");
		text("gui.mlinnotas.village.dir.n", "norte", "north");
		text("gui.mlinnotas.village.dir.ne", "nordeste", "northeast");
		text("gui.mlinnotas.village.dir.e", "leste", "east");
		text("gui.mlinnotas.village.dir.se", "sudeste", "southeast");
		text("gui.mlinnotas.village.dir.s", "sul", "south");
		text("gui.mlinnotas.village.dir.sw", "sudoeste", "southwest");
		text("gui.mlinnotas.village.dir.w", "oeste", "west");
		text("gui.mlinnotas.village.dir.nw", "noroeste", "northwest");
		text("gui.mlinnotas.village.cancel", "Cancelar", "Cancel");
		text("gui.mlinnotas.village.cancel_tooltip", "Tira a obra da fila e devolve tudo o que foi entregue.",
				"Removes the project from the queue and gives back everything delivered.");
		text("gui.mlinnotas.village.choose", "Escolher local", "Choose spot");
		text("gui.mlinnotas.village.choose_tooltip",
				"Mire no chão: a planta aparece verde onde cabe. R ou a roda do mouse giram, botão direito confirma, botão esquerdo cancela. "
						+ "Mirar um lote já marcado encaixa nele. Se você não escolher, a vila escolhe sozinha.",
				"Aim at the ground: the plan shows green where it fits. R or the mouse wheel rotate, right click confirms, left click cancels. "
						+ "Aiming at a marked lot snaps to it. If you never choose, the village picks a spot itself.");
		text("gui.mlinnotas.village.demolish", "Demolir", "Demolish");
		text("gui.mlinnotas.village.demolish_tooltip", "Desmancha a obra (só os blocos que a vila colocou), devolve o chão e metade do custo.",
				"Takes the building down (only the blocks the village placed), restores the ground and refunds half the cost.");
		text("gui.mlinnotas.village.demolish_legacy", "Obra de antes do registro de blocos: não dá para demolir pelo quadro.",
				"Built before block records existed: cannot be demolished from the board.");
		text("gui.mlinnotas.village.demolish_title", "Demolir %s?", "Demolish %s?");
		text("gui.mlinnotas.village.demolish_message",
				"Os blocos que a vila colocou somem (baús soltam o que tinham), o lote fica livre e você recebe %2$s%% do custo de volta. "
						+ "Aldeões continuam na vila.",
				"The blocks the village placed are removed (chests drop their contents), the lot becomes free and you get %2$s%% of the "
						+ "cost back. Villagers stay.");

		// --- escolha de lugar ---
		text("key.mlinnotas.village_rotate", "Girar obra (escolha de lugar)", "Rotate building (choosing a spot)");
		text("gui.mlinnotas.village.place.title", "Escolhendo lugar: %s", "Choosing a spot: %s");
		text("gui.mlinnotas.village.place.keys", "%s ou roda: girar · botão direito: confirmar · botão esquerdo: cancelar",
				"%s or wheel: rotate · right click: confirm · left click: cancel");
		text("gui.mlinnotas.village.place.ok", "Cabe aqui", "Fits here");
		text("gui.mlinnotas.village.place.snap", "Lote planejado: confirmar escolhe este", "Planned lot: confirm to pick this one");
		text("gui.mlinnotas.village.place.cancelled", "Escolha de lugar cancelada", "Spot selection cancelled");
		problem("outside", "Fora do raio da vila", "Outside the village radius");
		problem("plaza", "Em cima da praça", "On top of the plaza");
		problem("overlap", "Encosta em outro lote, rua ou poste", "Touches another lot, road or lamp post");
		problem("terrain", "Tem água ou construção de jogador no lote", "Water or player blocks on the lot");
		problem("steep", "Desnível maior que 6 blocos", "Height difference over 6 blocks");
		problem("no_access", "Sem caminho a pé da porta até a rua", "No walking path from the door to a road");

		// --- mensagens ---
		text("message.mlinnotas.village.area_shown",
				"Área da vila marcada no mundo por %s s: lotes verdes estão livres, amarelos têm árvores (a obra corta), vermelhos têm "
						+ "blocos seus. Use \"Mostrar área\" no quadro para ver de novo.",
				"Village area marked in the world for %s s: green lots are free, yellow have trees (the builders cut them), red have "
						+ "your blocks. Use \"Show area\" on the board to see it again.");
		text("message.mlinnotas.village.area_grown", "A vila cresceu: lotes novos marcados no mundo por %s s (os antigos não mudam).",
				"The village grew: new lots marked in the world for %s s (old ones stay the same).");
		text("message.mlinnotas.village.place_invalid", "Esse lote não serve mais", "That lot is no longer available");
		text("message.mlinnotas.village.place_picked", "%s: a próxima vai no lote escolhido", "%s: the next one goes on the chosen lot");
		text("message.mlinnotas.village.place_moved", "%s: lote mudado para onde você escolheu", "%s: lot moved to the spot you chose");
		text("message.mlinnotas.village.cancel_building", "Essa obra já começou: espere terminar e demolir, se quiser",
				"That project already started: wait for it to finish and demolish it if you want");
		text("message.mlinnotas.village.cancelled", "%s cancelou %s em %s (%s itens devolvidos)", "%s cancelled %s in %s (%s items refunded)");
		text("message.mlinnotas.village.demolish_legacy", "Essa obra é de antes do registro de blocos e não pode ser demolida pelo quadro",
				"That building predates block records and cannot be demolished from the board");
		text("message.mlinnotas.village.demolished", "%s demoliu %s em %s (%s blocos, %s itens devolvidos)",
				"%s demolished %s in %s (%s blocks, %s items refunded)");
		for (String p : new String[] {"outside", "plaza", "overlap", "terrain", "steep", "no_access"}) {
			text("message.mlinnotas.village.place_problem." + p, ContentGen.PT.get("gui.mlinnotas.village.place.problem." + p),
					ContentGen.EN.get("gui.mlinnotas.village.place.problem." + p));
		}

		// --- comandos ---
		text("commands.mlinnotas.village.entry", "%s em %s, %s, %s (%s) %s", "%s at %s, %s, %s (%s) %s");
		text("commands.mlinnotas.village.unknown", "Nenhuma vila chamada %s", "No village named %s");
		text("commands.mlinnotas.village.removed", "Registro da vila %s apagado (%s)", "Village record %s removed (%s)");
		text("commands.mlinnotas.village.restart_failed", "Não deu para criar a vila inicial", "Could not create the starting village");
		text("commands.mlinnotas.village.restarted", "Vila inicial %s criada em %s, %s, %s", "Starting village %s created at %s, %s, %s");
	}

	static void problem(String id, String pt, String en) {
		text("gui.mlinnotas.village.place.problem." + id, pt, en);
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
