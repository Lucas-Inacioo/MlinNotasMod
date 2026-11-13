import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Livro de quests para o ContentGen: um JSON por capitulo em {@code data/mlinnotas/quests/<capitulo>.json}, textos
 * (titulo + descricao de cada quest, nomes de capitulos/estruturas/dimensoes) em pt_br/en_us, o item {@code quest_book}
 * (modelo, receita livro + pedregulho) e as tags auxiliares. Chamado por ContentGen.main.
 *
 * <p>Para um capitulo novo: escreva {@code chapter("id", ordem, "icone", "Nome", "Name", ...)} com as quests
 * ({@code q(...)} + tarefas/recompensas) no fim de {@link #generate}, ou largue um JSON em
 * {@code data/<ns>/quests/<id>.json} com as chaves de lang {@code quest.mlinnotas.chapter.<id>},
 * {@code quest.mlinnotas.<id>.<quest>} e {@code quest.mlinnotas.<id>.<quest>.desc}. Formato do JSON em
 * {@code com.mlin.notas.quests.Quest}/{@code QuestTask}.</p>
 */
class QuestContent {
	/** Uma quest em construcao (DSL). */
	static final class Q {
		final String id;
		final String icon;
		final double x;
		final double y;
		final List<String> deps = new ArrayList<>();
		final List<String> tasks = new ArrayList<>();
		final List<String> rewards = new ArrayList<>();
		final String pt;
		final String en;
		final String descPt;
		final String descEn;

		Q(String id, String icon, double x, double y, String pt, String en, String descPt, String descEn) {
			this.id = id;
			this.icon = icon;
			this.x = x;
			this.y = y;
			this.pt = pt;
			this.en = en;
			this.descPt = descPt;
			this.descEn = descEn;
		}

		Q dep(String... keys) {
			deps.addAll(List.of(keys));
			return this;
		}

		Q have(String item, int count) {
			tasks.add("{\"type\":\"item\",\"target\":\"" + item + "\",\"count\":" + count + "}");
			return this;
		}

		/** Item de tag com texto proprio ({@code quest.mlinnotas.task.<titleId>}). */
		Q haveTag(String tag, int count, String icon, String titleId, String pt, String en) {
			text("quest.mlinnotas.task." + titleId, pt, en);
			tasks.add("{\"type\":\"item\",\"target\":\"#" + tag + "\",\"count\":" + count + ",\"icon\":\"" + icon + "\",\"title\":\"quest.mlinnotas.task."
					+ titleId + "\"}");
			return this;
		}

		Q deliver(String item, int count) {
			tasks.add("{\"type\":\"item\",\"target\":\"" + item + "\",\"count\":" + count + ",\"consume\":true}");
			return this;
		}

		Q kill(String entity, int count) {
			tasks.add("{\"type\":\"kill\",\"target\":\"" + entity + "\",\"count\":" + count + "}");
			return this;
		}

		Q structure(String id) {
			tasks.add("{\"type\":\"structure\",\"target\":\"" + id + "\"}");
			return this;
		}

		Q biome(String id) {
			tasks.add("{\"type\":\"biome\",\"target\":\"" + id + "\"}");
			return this;
		}

		Q dimension(String id) {
			tasks.add("{\"type\":\"dimension\",\"target\":\"" + id + "\"}");
			return this;
		}

		Q skill(String branch, int level) {
			tasks.add("{\"type\":\"skill\",\"target\":\"" + branch + "\",\"level\":" + level + "}");
			return this;
		}

		Q advancement(String id, String icon, String titleId, String pt, String en) {
			text("quest.mlinnotas.task." + titleId, pt, en);
			tasks.add("{\"type\":\"advancement\",\"target\":\"" + id + "\",\"icon\":\"" + icon + "\",\"title\":\"quest.mlinnotas.task." + titleId + "\"}");
			return this;
		}

		Q check() {
			tasks.add("{\"type\":\"checkmark\"}");
			return this;
		}

		Q item(String id, int count) {
			rewards.add("{\"item\":\"" + id + "\",\"count\":" + count + "}");
			return this;
		}

		Q xp(int points) {
			rewards.add("{\"xp\":" + points + "}");
			return this;
		}

		String json() {
			StringBuilder out = new StringBuilder("{\"id\":\"").append(id).append("\",\"icon\":\"").append(icon).append("\",\"x\":").append(num(x))
					.append(",\"y\":").append(num(y));
			if (!deps.isEmpty()) {
				out.append(",\"dependencies\":[").append(ContentGen.quoteAll(deps)).append(']');
			}
			out.append(",\"tasks\":[").append(String.join(",", tasks)).append(']');
			out.append(",\"rewards\":[").append(String.join(",", rewards)).append("]}");
			return out.toString();
		}
	}

	static String num(double v) {
		return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
	}

	static Q q(String id, String icon, double x, double y, String pt, String en, String descPt, String descEn) {
		return new Q(id, icon, x, y, pt, en, descPt, descEn);
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}

	static void chapter(String id, int order, String icon, String pt, String en, Q... quests) throws IOException {
		text("quest.mlinnotas.chapter." + id, pt, en);
		List<String> json = new ArrayList<>();
		for (Q q : quests) {
			json.add(q.json());
			text("quest.mlinnotas." + id + "." + q.id, q.pt, q.en);
			text("quest.mlinnotas." + id + "." + q.id + ".desc", q.descPt, q.descEn);
		}
		ContentGen.write("data/mlinnotas/quests/" + id + ".json", "{\"icon\":\"" + icon + "\",\"order\":" + order + ",\"quests\":[" + String.join(",", json) + "]}");
	}

	static void generate() throws IOException {
		// --- item ---
		ContentGen.simpleItem("quest_book", "item/generated");
		ContentGen.name("item", "quest_book", "Livro de Quests", "Quest Book");
		text("item.mlinnotas.quest_book.tooltip", "Botão direito ou tecla Y: objetivos do mundo e recompensas", "Right-click or press Y: world goals and rewards");
		ContentGen.write("data/mlinnotas/recipe/quest_book.json", "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":"
				+ "[\"minecraft:book\",\"minecraft:cobblestone\"],\"result\":{\"count\":1,\"id\":\"mlinnotas:quest_book\"}}");
		// baus: o jogador pode fazer o bau ou o upgrade que converte um bau no lugar
		for (String tier : List.of("stone", "iron", "diamond")) {
			ContentGen.tag("item", "mlinnotas:quest/" + tier + "_chest", List.of("mlinnotas:" + tier + "_chest", "mlinnotas:chest_upgrade_" + tier), false);
		}

		// --- interface ---
		text("key.mlinnotas.quests", "Livro de Quests", "Quest Book");
		text("gui.mlinnotas.quests.title", "Livro de Quests", "Quest Book");
		text("gui.mlinnotas.quests.total", "%s/%s concluídas", "%s/%s done");
		text("gui.mlinnotas.quests.empty", "Nenhuma quest carregada", "No quests loaded");
		text("gui.mlinnotas.quests.rewards_badge", "%s!", "%s!");
		text("gui.mlinnotas.quests.rewards_waiting", "%s recompensa(s) para resgatar", "%s reward(s) to claim");
		text("gui.mlinnotas.quests.status.locked", "Bloqueada", "Locked");
		text("gui.mlinnotas.quests.status.active", "Em andamento", "In progress");
		text("gui.mlinnotas.quests.status.claimable", "Concluída: resgate a recompensa!", "Done: claim your reward!");
		text("gui.mlinnotas.quests.status.done", "Concluída", "Completed");
		text("gui.mlinnotas.quests.requires", "Requer: %s", "Requires: %s");
		text("gui.mlinnotas.quests.in_chapter", "%s (%s)", "%s (%s)");
		text("gui.mlinnotas.quests.tasks", "Tarefas", "Tasks");
		text("gui.mlinnotas.quests.rewards", "Recompensas (cada um resgata a sua)", "Rewards (one per player)");
		text("gui.mlinnotas.quests.reward_xp", "+%s XP", "+%s XP");
		text("gui.mlinnotas.quests.reward_note", "Complete a quest para resgatar.", "Complete the quest to claim.");
		text("gui.mlinnotas.quests.claimed", "Você já resgatou.", "You already claimed this.");
		text("gui.mlinnotas.quests.submit", "Entregar", "Submit");
		text("gui.mlinnotas.quests.check", "Marcar feito", "Mark done");
		text("gui.mlinnotas.quests.claim", "Resgatar", "Claim");
		text("gui.mlinnotas.quests.open_skills", "Árvore (K)", "Skills (K)");
		text("gui.mlinnotas.quests.toast", "Quest concluída!", "Quest complete!");
		text("gui.mlinnotas.quests.task.have", "Tenha %sx %s", "Have %sx %s");
		text("gui.mlinnotas.quests.task.deliver", "Entregue %sx %s", "Submit %sx %s");
		text("gui.mlinnotas.quests.task.kill", "Derrote %sx %s", "Defeat %sx %s");
		text("gui.mlinnotas.quests.task.structure", "Visite: %s", "Visit: %s");
		text("gui.mlinnotas.quests.task.biome", "Visite o bioma: %s", "Visit the biome: %s");
		text("gui.mlinnotas.quests.task.dimension", "Entre em: %s", "Enter: %s");
		text("gui.mlinnotas.quests.task.skill", "Árvore: %s", "Skill tree: %s");
		text("gui.mlinnotas.quests.task.advancement", "Conquista: %s", "Advancement: %s");
		text("gui.mlinnotas.quests.task.checkmark", "Marque como feito", "Mark as done");
		text("message.mlinnotas.quests.completed", "%s concluiu a quest: %s", "%s completed the quest: %s");
		text("message.mlinnotas.quests.completed_world", "Quest concluída: %s", "Quest completed: %s");
		text("message.mlinnotas.quests.claimed", "Recompensa resgatada: %s", "Reward claimed: %s");
		text("message.mlinnotas.quests.nothing_to_submit", "Você não tem nada para entregar", "You have nothing to submit");
		text("commands.mlinnotas.quests.reset", "Livro de quests do mundo zerado", "World quest book reset");
		text("commands.mlinnotas.quests.completed", "%s quests concluídas", "%s quests completed");
		text("commands.mlinnotas.quests.unknown", "Quest ou capítulo desconhecido: %s", "Unknown quest or chapter: %s");

		// nomes de alvos sem texto no jogo
		String[][] structures = {
				{"mlinnotas.ruined_watchtower", "Torre de Vigia Caída", "Fallen Watchtower"},
				{"mlinnotas.abandoned_camp", "Acampamento Abandonado", "Abandoned Camp"},
				{"mlinnotas.old_well", "Poço Antigo", "Old Well"},
				{"mlinnotas.ruined_chapel", "Capela em Ruínas", "Ruined Chapel"},
				{"mlinnotas.disused_mine", "Mina Desativada", "Disused Mine"},
				{"mlinnotas.cobalt_shrine", "Santuário de Cobalto", "Cobalt Shrine"},
				{"mlinnotas.abyssal_sanctum", "Santuário Abissal", "Abyssal Sanctum"},
				{"mlinnotas.colossus_forge", "Forja do Colosso", "Colossus Forge"},
				{"mlinnotas.end_citadel", "Cidadela do End", "End Citadel"},
				{"minecraft.fortress", "Fortaleza do Nether", "Nether Fortress"},
				{"minecraft.bastion_remnant", "Bastião em Ruínas", "Bastion Remnant"},
				{"minecraft.stronghold", "Fortaleza (portal do End)", "Stronghold"},
		};
		for (String[] s : structures) {
			text("quest.mlinnotas.structure." + s[0], s[1], s[2]);
		}
		text("quest.mlinnotas.dimension.minecraft.the_nether", "o Nether", "the Nether");
		text("quest.mlinnotas.dimension.minecraft.the_end", "o End", "the End");
		text("quest.mlinnotas.biome_tag.minecraft.is_jungle", "Selva", "Jungle");
		text("quest.mlinnotas.biome_tag.c.is_snowy", "Bioma nevado", "Snowy biome");

		chapter("basics", 0, "mlinnotas:quest_book", "Primeiros Passos", "First Steps",
				q("welcome", "mlinnotas:quest_book", 0, 2, "Bem-vindos!", "Welcome!",
						"Este livro guia vocês pelo mod. O progresso é do mundo: o que um faz conta para os dois, mas cada um resgata a própria recompensa. Abra com L ou com o livro. Marque esta quest para começar.",
						"This book guides you through the mod. Progress belongs to the world: what one of you does counts for both, but each player claims their own reward. Open it with L or the book. Check this quest to begin.")
						.check().item("minecraft:bread", 8),
				q("wood", "minecraft:oak_log", 1, 1, "Lenhador", "Lumberjack",
						"Um machado num tronco com folhas derruba a árvore inteira (agachado, quebra só um bloco). Junte 16 troncos.",
						"An axe on a log with leaves fells the whole tree (sneak to break a single block). Gather 16 logs.")
						.dep("welcome").haveTag("minecraft:logs", 16, "minecraft:oak_log", "logs", "Tenha 16x troncos", "Have 16x logs").item("minecraft:apple", 4),
				q("stone_tools", "minecraft:stone_pickaxe", 2, 0, "Idade da Pedra", "Stone Age",
						"Faça uma picareta de pedra. Atenção: minérios só quebram com o nível certo de Mineração na árvore de habilidades (tecla K). Veja o capítulo Mineração.",
						"Craft a stone pickaxe. Note: ores only break with the right Mining level in the skill tree (K key). See the Mining chapter.")
						.dep("wood").have("minecraft:stone_pickaxe", 1).item("minecraft:torch", 16).xp(20),
				q("wand", "mlinnotas:wooden_wand", 3, 0, "Varinha de Construção", "Building Wand",
						"Clique numa face com a varinha para estender a parede com blocos do seu inventário: madeira põe 9 de uma vez, e cada nível põe mais (até 1024 no celéstio).",
						"Click a face with the wand to extend it using blocks from your inventory: wood places 9 at once, and each tier places more (up to 1024 with celestium).")
						.dep("stone_tools").have("mlinnotas:wooden_wand", 1).xp(30),
				q("harvest", "minecraft:wheat", 2, 1, "Colheita Fácil", "Easy Harvest",
						"Botão direito numa planta madura colhe e replanta. Com enxada colhe uma área: cobre/ferro 3x3, ouro a diamante 5x5, netherite em diante 7x7.",
						"Right-click a ripe crop to harvest and replant it. With a hoe you harvest an area: copper/iron 3x3, gold to diamond 5x5, netherite and up 7x7.")
						.dep("wood").have("minecraft:wheat", 16).item("minecraft:bone_meal", 16),
				q("crafting_tablet", "mlinnotas:crafting_tablet", 2, 2, "Bancada de Bolso", "Pocket Workbench",
						"O Tablet de Crafting é uma grade 3x3 portátil: botão direito com ele na mão, ou tecla C com ele em qualquer lugar do inventário.",
						"The Crafting Tablet is a portable 3x3 grid: right-click it, or press C with it anywhere in your inventory.")
						.dep("wood").have("mlinnotas:crafting_tablet", 1).xp(30),
				q("backpack", "mlinnotas:backpack", 3, 2, "Mochila", "Backpack",
						"27 espaços extras: abre com botão direito ou tecla V. Upgrades de tamanho, crafting, fornalha e tanques estão no capítulo Tecnologia.",
						"27 extra slots: open it with right-click or the V key. Size, crafting, furnace and tank upgrades are in the Technology chapter.")
						.dep("crafting_tablet").have("mlinnotas:backpack", 1).item("minecraft:leather", 4).xp(30),
				q("hud", "minecraft:spyglass", 1, 3, "Atalhos Úteis", "Handy Keys",
						"O organiza o inventário (Shift+O organiza o seu numa tela de baú; botão Ord nos baús). N mostra o que você mira: se a ferramenta coleta e o que tem nos baús. G liga a visão clara. Marque quando testar.",
						"O sorts your inventory (Shift+O sorts yours in a chest screen; Ord button on chests). N shows what you are looking at: whether your tool harvests it and what is inside chests. G toggles bright vision. Check when you've tried them.")
						.dep("welcome").check().xp(20),
				q("minimap", "minecraft:compass", 2, 3, "Minimapa", "Minimap",
						"No canto: H liga/desliga, - e = mudam o tamanho, [ e ] o zoom, J o modo caverna. M abre o mapa grande (arraste, zoom, botão direito cria waypoint). B marca um waypoint onde você está.",
						"In the corner: H toggles it, - and = change size, [ and ] zoom, J cave mode. M opens the big map (drag, zoom, right-click adds a waypoint). B marks a waypoint where you stand.")
						.dep("hud").check().item("minecraft:map", 1).xp(20),
				q("recipes", "minecraft:crafting_table", 2, 4, "Receitas na Mão", "Recipes at Hand",
						"Com uma tela de inventário aberta, a lista de itens aparece à direita: R mostra as receitas e U os usos do item sob o cursor. Busque com @mod para filtrar por mod.",
						"With an inventory screen open, the item list appears on the right: R shows recipes and U shows uses of the item under the cursor. Search @mod to filter by mod.")
						.dep("hud").check().xp(20),
				q("sleep", "minecraft:red_bed", 0, 4, "Boa Noite", "Good Night",
						"Basta metade dos jogadores dormir para a noite passar: em dupla, um na cama já serve. A barra de ação mostra quantos faltam.",
						"Only half of the players need to sleep for the night to pass: in a pair, one in bed is enough. The action bar shows how many are missing.")
						.dep("welcome").advancement("minecraft:adventure/sleep_in_bed", "minecraft:red_bed", "sleep", "Durma numa cama", "Sleep in a bed")
						.item("minecraft:cooked_beef", 8));

		chapter("mining", 1, "minecraft:iron_pickaxe", "Mineração", "Mining",
				q("skill_stone", "minecraft:stone_pickaxe", 0, 1, "Mineração: Pedra", "Mining: Stone",
						"Abra a árvore de habilidades (K) e deposite 32 pedregulhos em Mineração: Pedra. Sem esse nível nem o carvão quebra. A árvore é do mundo: qualquer um pode depositar.",
						"Open the skill tree (K) and deposit 32 cobblestone into Mining: Stone. Without it not even coal breaks. The tree belongs to the world: anyone can deposit.")
						.dep("basics/stone_tools").skill("mining", 2).item("minecraft:torch", 16).xp(30),
				q("skill_copper", "minecraft:copper_ingot", 1, 1, "Mineração: Cobre", "Mining: Copper",
						"Custa 10 carvões (o minério ou o carvão que cai). Libera o minério de cobre e o primeiro nível dos ramos laterais.",
						"Costs 10 coal (the ore or the dropped coal). Unlocks copper ore and the first level of the side branches.")
						.dep("skill_stone").skill("mining", 3).xp(40),
				q("side_branch", "minecraft:iron_sword", 1, 2, "Ramos Laterais", "Side Branches",
						"Combate, Vigor, Agilidade e Prospecção têm 5 níveis cada, pagos com lingotes e XP, e valem para todos os jogadores. Comece por Combate I (16 lingotes de cobre + 5 níveis).",
						"Combat, Vigor, Agility and Prospecting have 5 levels each, paid with ingots and XP, and apply to every player. Start with Combat I (16 copper ingots + 5 levels).")
						.dep("skill_copper").skill("combat", 1).xp(40),
				q("skill_iron", "minecraft:iron_ingot", 2, 1, "Mineração: Ferro", "Mining: Iron",
						"Custa 10 cobres brutos (ou minério) + 3 níveis de XP. Libera ferro e lápis-lazúli.",
						"Costs 10 raw copper (or ore) + 3 XP levels. Unlocks iron and lapis.")
						.dep("skill_copper").skill("mining", 4).xp(50),
				q("iron_gear", "minecraft:iron_pickaxe", 2, 0, "Ferro na Mão", "Iron in Hand",
						"Picareta de ferro. Com ferro vêm o upgrade de fornalha de ferro, baús maiores e os 4 blocos de ferro da oferenda do Golem de Cobalto.",
						"An iron pickaxe. Iron also makes the iron furnace upgrade, bigger chests and the 4 iron blocks offered to the Cobalt Golem.")
						.dep("skill_iron").have("minecraft:iron_pickaxe", 1).xp(50),
				q("skill_gold", "minecraft:gold_ingot", 3, 1, "Mineração: Ouro", "Mining: Gold",
						"Custa 10 ferros brutos + 5 níveis. O ouro agora é um nível de verdade, entre ferro e cobalto. Libera ouro e redstone.",
						"Costs 10 raw iron + 5 levels. Gold is now a real tier between iron and cobalt. Unlocks gold and redstone.")
						.dep("skill_iron").skill("mining", 5).xp(60),
				q("skill_cobalt", "mlinnotas:cobalt_ingot", 4, 1, "Mineração: Cobalto", "Mining: Cobalt",
						"Precisa do Coração de Cobalto, solto pelo Golem de Cobalto (capítulo Chefes), + 10 ouros brutos + 10 níveis.",
						"Needs the Cobalt Heart dropped by the Cobalt Golem (Bosses chapter), + 10 raw gold + 10 levels.")
						.dep("skill_gold").skill("mining", 6).xp(80),
				q("cobalt", "mlinnotas:raw_cobalt", 4, 0, "Cobalto", "Cobalt",
						"Minério azul do Overworld, em pedra e ardósia abaixo de y=32 (veios ricos bem no fundo). Pede picareta de ferro. Funda em lingotes: a pedra de teleporte usa um.",
						"Blue Overworld ore in stone and deepslate below y=32 (rich veins deep down). Needs an iron pickaxe. Smelt it: the waystone recipe uses an ingot.")
						.dep("skill_cobalt").have("mlinnotas:cobalt_ingot", 4).xp(80),
				q("skill_diamond", "minecraft:diamond", 5, 1, "Mineração: Diamante", "Mining: Diamond",
						"Custa 10 cobaltos brutos + 15 níveis. O diamante agora pede picareta de cobalto.",
						"Costs 10 raw cobalt + 15 levels. Diamond now needs a cobalt pickaxe.")
						.dep("skill_cobalt").skill("mining", 7).item("minecraft:diamond", 1).xp(100),
				q("skill_netherite", "minecraft:netherite_scrap", 6, 1, "Mineração: Netherite", "Mining: Netherite",
						"Precisa do Núcleo Abissal (Guardião das Profundezas) + 5 diamantes + 20 níveis. Libera os detritos ancestrais.",
						"Needs the Abyssal Core (Abyssal Guardian) + 5 diamonds + 20 levels. Unlocks ancient debris.")
						.dep("skill_diamond").skill("mining", 8).xp(150),
				q("skill_infernite", "mlinnotas:raw_infernite", 7, 1, "Mineração: Infernita", "Mining: Infernite",
						"Precisa da Brasa do Colosso (Colosso Ígneo) + 4 sucatas de netherite + 20 níveis. A infernita só existe no Nether e pede picareta de netherite.",
						"Needs the Colossus Ember (Igneous Colossus) + 4 netherite scrap + 20 levels. Infernite only exists in the Nether and needs a netherite pickaxe.")
						.dep("skill_netherite").skill("mining", 9).xp(200),
				q("skill_endite", "mlinnotas:raw_endite", 8, 1, "Mineração: Endita", "Mining: Endite",
						"Precisa de uma Escama de Dragão (Dragão do Ender) + 10 infernitas brutas + 25 níveis. A endita aparece em todo o End (picareta de infernita).",
						"Needs a Dragon Scale (Ender Dragon) + 10 raw infernite + 25 levels. Endite appears all over the End (infernite pickaxe).")
						.dep("skill_infernite").skill("mining", 10).xp(250),
				q("skill_astralite", "mlinnotas:raw_astralite", 9, 1, "Mineração: Astralita", "Mining: Astralite",
						"Precisa do Fragmento Astral (Arauto Astral) + 10 enditas brutas + 30 níveis. A astralita só aparece nas ilhas externas do End (picareta de endita).",
						"Needs the Astral Fragment (Astral Herald) + 10 raw endite + 30 levels. Astralite only appears on the outer End islands (endite pickaxe).")
						.dep("skill_endite").skill("mining", 11).xp(300),
				q("skill_celestium", "minecraft:nether_star", 10, 1, "Mineração: Celéstio", "Mining: Celestium",
						"Precisa da Estrela do Nether (Wither) + 10 astralitas brutas + 40 níveis. Libera a liga de celéstio.",
						"Needs a Nether Star (Wither) + 10 raw astralite + 40 levels. Unlocks the celestium alloy.")
						.dep("skill_astralite").skill("mining", 12).xp(400),
				q("celestium", "mlinnotas:celestium_ingot", 11, 1, "Celéstio", "Celestium",
						"Liga sem forma: 2 astralitas + 2 enditas + 2 infernitas + 1 netherite. O topo da escada: armadura 20, varinha de 1024 blocos, baú de 243.",
						"Shapeless alloy: 2 astralite + 2 endite + 2 infernite + 1 netherite. The top of the ladder: 20 armor, a 1024-block wand, a 243-slot chest.")
						.dep("skill_celestium").have("mlinnotas:celestium_ingot", 1).item("minecraft:enchanted_golden_apple", 1).xp(500));

		chapter("exploration", 2, "minecraft:compass", "Exploração", "Exploration",
				q("explore", "minecraft:filled_map", 0, 2, "Pé na Estrada", "Hit the Road",
						"Ruínas pequenas aparecem pelo Overworld, uma a cada ~380 blocos. Cada tipo prefere certos biomas e guarda um baú. Marque um waypoint em casa (B) antes de sair.",
						"Small ruins dot the Overworld, about one every 380 blocks. Each kind prefers certain biomes and hides a chest. Mark a home waypoint (B) before leaving.")
						.dep("basics/minimap").check().item("minecraft:bread", 8),
				q("watchtower", "minecraft:stone_bricks", 1, 0, "Torre de Vigia Caída", "Fallen Watchtower",
						"Planícies, savanas e colinas. Suba a escada em espiral até o deque: o baú fica lá em cima, e há uma pedra de teleporte na pracinha ao lado.",
						"Plains, savannas and hills. Climb the spiral stairs to the deck: the chest is up there, and a waystone stands in the little square nearby.")
						.dep("explore").structure("mlinnotas:ruined_watchtower").item("mlinnotas:return_scroll", 1).xp(50),
				q("camp", "minecraft:campfire", 1, 1, "Acampamento Abandonado", "Abandoned Camp",
						"Florestas e taigas. Barracas de lã listrada, fogueira apagada e um baú na barraca grande.",
						"Forests and taigas. Striped wool tents, a dead campfire and a chest in the big tent.")
						.dep("explore").structure("mlinnotas:abandoned_camp").item("minecraft:cooked_porkchop", 8).xp(50),
				q("well", "minecraft:bucket", 1, 2, "Poço Antigo", "Old Well",
						"Biomas secos (deserto, savana, badlands). Desça a escada de mão até a cisterna: o baú fica lá embaixo, perto da água.",
						"Dry biomes (desert, savanna, badlands). Climb down the ladder into the cistern: the chest is down there by the water.")
						.dep("explore").structure("mlinnotas:old_well").item("minecraft:bucket", 1).xp(50),
				q("chapel", "minecraft:lectern", 1, 3, "Capela em Ruínas", "Ruined Chapel",
						"Floresta escura e pântano. Nave desabada, vitrais quebrados, sino caído e um altar com atril no presbitério. Há uma pedra de teleporte por perto.",
						"Dark forest and swamp. A collapsed nave, broken stained glass, a fallen bell and an altar with a lectern. A waystone stands nearby.")
						.dep("explore").structure("mlinnotas:ruined_chapel").item("minecraft:golden_apple", 1).xp(50),
				q("mine", "minecraft:rail", 1, 4, "Mina Desativada", "Disused Mine",
						"Montanhas. Siga a rampa com trilhos até o salão com veio de cobre ou ferro; no fundo há uma sala de altar antiga.",
						"Mountains. Follow the railed ramp to the hall with a copper or iron vein; an old altar room waits at the bottom.")
						.dep("explore").structure("mlinnotas:disused_mine").item("minecraft:torch", 16).xp(50),
				q("all_ruins", "mlinnotas:return_scroll", 2, 2, "Arqueólogos", "Archaeologists",
						"As cinco ruínas visitadas. Esta quest completa sozinha.",
						"All five ruins visited. This quest completes by itself.")
						.dep("watchtower", "camp", "well", "chapel", "mine").item("mlinnotas:return_scroll", 2).xp(100),
				q("waystone", "mlinnotas:waystone", 2, 0, "Pedras de Teleporte", "Waystones",
						"Botão direito numa pedra de teleporte a ativa para o mundo todo e abre a lista de destinos. Viajar custa 1 nível a cada 500 blocos (3 entre dimensões). Etiqueta com nome renomeia.",
						"Right-click a waystone to activate it for the whole world and open the destination list. Travel costs 1 level per 500 blocks (3 across dimensions). A name tag renames it.")
						.dep("watchtower").check().item("mlinnotas:return_scroll", 1),
				q("waystone_craft", "mlinnotas:waystone", 3, 0, "Uma Pedra em Casa", "A Stone at Home",
						"Receita: pedra polida + pérola do Ender + lingote de cobalto. Ponha uma na base. O Pergaminho de Retorno leva à última pedra usada.",
						"Recipe: polished stone + ender pearl + cobalt ingot. Put one at your base. The Return Scroll takes you to the last waystone used.")
						.dep("waystone", "mining/cobalt").have("mlinnotas:waystone", 1).xp(80),
				q("biomes", "minecraft:jungle_sapling", 0, 4, "Viajantes", "Travelers",
						"Visitem selva, deserto, pântano e um bioma nevado. Monstros desses lugares podem nascer como variantes: Zumbi Musgoso, Esqueleto Ardente, Aranha da Neve, Afogado do Mangue.",
						"Visit a jungle, a desert, a swamp and a snowy biome. Monsters there may spawn as variants: Mossy Zombie, Blazing Skeleton, Snow Spider, Mangrove Drowned.")
						.dep("explore").biome("#minecraft:is_jungle").biome("minecraft:desert").biome("minecraft:swamp").biome("#c:is_snowy")
						.item("minecraft:golden_carrot", 8).xp(80),
				q("deep_dark", "minecraft:sculk_shrieker", 1, 5, "Escuro Profundo", "Deep Dark",
						"Bem fundo, sob as montanhas. Ande agachado: o sculk chama o Warden. Cidades antigas guardam fragmentos de eco, e o Santuário Abissal também fica aqui.",
						"Deep below the mountains. Sneak: sculk calls the Warden. Ancient cities hold echo shards, and the Abyssal Sanctum lies here too.")
						.dep("biomes").biome("minecraft:deep_dark").xp(80),
				q("biome_compass", "mlinnotas:biome_compass", 0, 0, "Bússola de Biomas", "Biome Compass",
						"Receita: bússola no meio, mudas e troncos em volta. Botão direito abre a lista de biomas desta dimensão (busque pelo nome); a agulha aponta para o mais perto e o tooltip mostra a distância.",
						"Recipe: a compass in the middle, saplings and logs around it. Right-click opens the list of biomes in this dimension (search by name); the needle points to the nearest one and the tooltip shows the distance.")
						.dep("explore").have("mlinnotas:biome_compass", 1).item("minecraft:map", 2).xp(40),
				q("structure_compass", "mlinnotas:structure_compass", 3, 2, "Bússola de Estruturas", "Structure Compass",
						"Receita: bússola no meio, lingotes de cobalto e tijolos de pedra em volta. Aponta para ruínas, masmorras, templos e vilas. Os covis dos chefes só aparecem na lista quando o mundo chega no nível de Mineração anterior ao do chefe.",
						"Recipe: a compass in the middle, cobalt ingots and stone bricks around it. Points to ruins, dungeons, temples and villages. Boss lairs only show up in the list once the world reaches the Mining level before that boss.")
						.dep("biome_compass", "mining/cobalt").have("mlinnotas:structure_compass", 1).item("mlinnotas:return_scroll", 1).xp(80));

		chapter("bosses", 3, "mlinnotas:cobalt_heart", "Chefes", "Bosses",
				q("cobalt_shrine", "mlinnotas:cobalt_altar", 0, 0, "Santuário de Cobalto", "Cobalt Shrine",
						"Planícies, florestas, taigas e savanas: 8 pedras em pé com veios de cobalto em volta de um estrado em degraus, com o altar no meio.",
						"Plains, forests, taigas and savannas: 8 standing stones with cobalt veins around a stepped dais, with the altar in the middle.")
						.dep("mining/skill_iron").structure("mlinnotas:cobalt_shrine").xp(50),
				q("iron_blocks", "minecraft:iron_block", 1, 0, "Oferenda de Ferro", "Iron Offering",
						"O altar de cobalto pede 4 blocos de ferro: botão direito no altar com eles no inventário. Vida do chefe pensada para 2 jogadores.",
						"The cobalt altar asks for 4 iron blocks: right-click it with them in your inventory. Boss health is tuned for 2 players.")
						.dep("cobalt_shrine").have("minecraft:iron_block", 4).xp(30),
				q("cobalt_golem", "mlinnotas:cobalt_heart", 2, 0, "Golem de Cobalto", "Cobalt Golem",
						"Soco forte, pancada no chão em anel de 6 blocos e arremesso de pedra; abaixo da metade da vida ele investe. Solta o Coração de Cobalto (Mineração: Cobalto).",
						"Heavy punches, a ground slam in a 6-block ring and thrown rocks; below half health it charges. Drops the Cobalt Heart (Mining: Cobalt).")
						.dep("iron_blocks").kill("mlinnotas:cobalt_golem", 1).item("minecraft:golden_apple", 2).xp(300),
				q("abyssal_sanctum", "mlinnotas:abyssal_altar", 0, 1, "Santuário Abissal", "Abyssal Sanctum",
						"No Escuro Profundo, entre y -40 e -16: salão de ardósia com colunas, sculk e uma escadaria. O altar abissal fica no centro.",
						"In the Deep Dark, between y -40 and -16: a deepslate hall with pillars, sculk and a staircase. The abyssal altar sits in the center.")
						.dep("exploration/deep_dark").structure("mlinnotas:abyssal_sanctum").xp(80),
				q("echo_shards", "minecraft:echo_shard", 1, 1, "Fragmentos de Eco", "Echo Shards",
						"Vêm dos baús das cidades antigas, também no Escuro Profundo. O altar abissal pede 4.",
						"They come from ancient city chests, also in the Deep Dark. The abyssal altar asks for 4.")
						.dep("abyssal_sanctum").have("minecraft:echo_shard", 4).xp(50),
				q("abyssal_guardian", "mlinnotas:abyssal_core", 2, 1, "Guardião das Profundezas", "Abyssal Guardian",
						"Pulso sônico que ignora armadura e escuridão; na fase 2 tentáculos de sculk prendem. Solta o Núcleo Abissal (Mineração: Netherite).",
						"A sonic pulse that ignores armor, and darkness; in phase 2 sculk tentacles grab you. Drops the Abyssal Core (Mining: Netherite).")
						.dep("echo_shards").kill("mlinnotas:abyssal_guardian", 1).item("minecraft:golden_apple", 2).xp(400),
				q("colossus_forge", "minecraft:blackstone", 0, 2, "Forja do Colosso", "Colossus Forge",
						"Nos Picos de Infernita do Nether: arena de pedra-negra com anel de lava e colunas de basalto. O colosso dorme no trono e acorda com alguém em sobrevivência a 16 blocos.",
						"In the Nether's Infernite Peaks: a blackstone arena with a lava ring and basalt pillars. The colossus sleeps on its throne and wakes when a survival player comes within 16 blocks.")
						.dep("nether/infernite_peaks").structure("mlinnotas:colossus_forge").xp(80),
				q("igneous_colossus", "mlinnotas:colossus_ember", 2, 2, "Colosso Ígneo", "Igneous Colossus",
						"Onda de fogo em anel e chuva de meteoros de magma; na fase 2 abre poças de lava avisadas por partículas. Leve resistência ao fogo. Solta a Brasa do Colosso (Mineração: Infernita).",
						"A ring of fire and a magma meteor shower; in phase 2 it opens lava pools announced by particles. Bring fire resistance. Drops the Colossus Ember (Mining: Infernite).")
						.dep("colossus_forge").kill("mlinnotas:igneous_colossus", 1).item("minecraft:golden_apple", 3).xp(500),
				q("ender_dragon", "minecraft:dragon_head", 2, 3, "Dragão do Ender", "Ender Dragon",
						"Fase 1: meteoros do vazio e ácaros. Fase 2: dois cristais renascem, mergulhos e sentinelas. Fase 3: rajadas de sopro. Cada morte deixa Escamas de Dragão e um baú do tesouro no estrado norte.",
						"Phase 1: void meteors and mites. Phase 2: two crystals respawn, dives and sentinels. Phase 3: breath barrages. Every kill leaves Dragon Scales and a treasure chest on the north dais.")
						.dep("end/enter_end").kill("minecraft:ender_dragon", 1).item("minecraft:golden_apple", 3).xp(500),
				q("sentinel_cores", "mlinnotas:sentinel_core", 1, 4, "Núcleos de Sentinela", "Sentinel Cores",
						"As Sentinelas de Púrpura da Cidadela soltam núcleos. O altar astral, no cofre do topo, pede 4.",
						"The Citadel's Purpur Sentinels drop cores. The astral altar in the top vault asks for 4.")
						.dep("end/citadel").have("mlinnotas:sentinel_core", 4).xp(80),
				q("astral_herald", "mlinnotas:astral_fragment", 2, 4, "Arauto Astral", "Astral Herald",
						"Voa, dispara raios estelares teleguiados e se teleporta; na fase 2 invoca sentinelas. Solta o Fragmento Astral (Mineração: Astralita).",
						"It flies, fires homing star bolts and teleports; in phase 2 it summons sentinels. Drops the Astral Fragment (Mining: Astralite).")
						.dep("sentinel_cores").kill("mlinnotas:astral_herald", 1).item("minecraft:enchanted_golden_apple", 1).xp(800),
				q("wither", "minecraft:nether_star", 2, 5, "Wither", "Wither",
						"3 crânios de esqueleto wither sobre 4 areias das almas em T. Lute longe da base! Solta a Estrela do Nether (Mineração: Celéstio e o sinalizador).",
						"3 wither skeleton skulls on 4 soul sand in a T. Fight far from home! Drops the Nether Star (Mining: Celestium and the beacon).")
						.dep("nether/wither_skulls").kill("minecraft:wither", 1).item("minecraft:golden_apple", 3).xp(500),
				q("legends", "minecraft:totem_of_undying", 4, 2.5, "Lendas do Mundo", "Legends of the World",
						"Todos os chefes derrotados. Esta quest completa sozinha.",
						"Every boss defeated. This quest completes by itself.")
						.dep("cobalt_golem", "abyssal_guardian", "igneous_colossus", "ender_dragon", "astral_herald", "wither")
						.item("minecraft:enchanted_golden_apple", 2).xp(1000));

		chapter("nether", 4, "minecraft:netherrack", "Nether", "Nether",
				q("enter_nether", "minecraft:obsidian", 0, 2, "Para o Nether", "Into the Nether",
						"Portal de obsidiana (água sobre lava em molde faz obsidiana sem picareta de diamante). O Nether foi refeito para andar a pé: chão contínuo, lava só no fundo dos vales, teto alto.",
						"An obsidian portal (water over lava in a mold makes obsidian without a diamond pickaxe). The Nether is rebuilt for walking: continuous ground, lava only at valley floors, a high ceiling.")
						.dep("mining/skill_iron").dimension("minecraft:the_nether").item("minecraft:golden_carrot", 8).xp(60),
				q("ashen_plains", "mlinnotas:nether_ash", 1, 0, "Planícies de Cinza", "Ashen Plains",
						"Dunas baixas de cinza e solo de almas sob uma chuva de cinzas. Cuidado com o Esqueleto Cinzento, que cega.",
						"Low dunes of ash and soul soil under falling ash. Beware the Ashen Wither Skeleton, which blinds.")
						.dep("enter_nether").biome("mlinnotas:ashen_plains").xp(40),
				q("burning_grove", "mlinnotas:burning_cap", 1, 1, "Bosque Ardente", "Burning Grove",
						"Fungos gigantes de copa baixa, cipós e nílio de brasas. Lesmas de Magma rastejam por aqui.",
						"Giant low-capped fungi, vines and ember nylium. Magma Slugs crawl around here.")
						.dep("enter_nether").biome("mlinnotas:burning_grove").xp(40),
				q("magma_slug", "minecraft:magma_cream", 2, 1, "Lesmas de Magma", "Magma Slugs",
						"Lentas, mas queimam. Soltam creme de magma, bom para poções de resistência ao fogo.",
						"Slow, but they burn. They drop magma cream, handy for fire resistance potions.")
						.dep("burning_grove").kill("mlinnotas:magma_slug", 3).xp(40),
				q("infernite_peaks", "mlinnotas:raw_infernite", 1, 2, "Picos de Infernita", "Infernite Peaks",
						"Planaltos de basalto e pedra-negra com infernita exposta. É aqui que fica a Forja do Colosso (capítulo Chefes).",
						"Basalt and blackstone plateaus with exposed infernite. The Colossus Forge is here (Bosses chapter).")
						.dep("enter_nether").biome("mlinnotas:infernite_peaks").xp(60),
				q("infernite", "mlinnotas:infernite_ingot", 2, 2, "Infernita", "Infernite",
						"Minério só do Nether, com veios ricos nos Picos. Pede picareta de netherite e Mineração: Infernita.",
						"A Nether-only ore with rich veins in the Peaks. Needs a netherite pickaxe and Mining: Infernite.")
						.dep("infernite_peaks", "mining/skill_infernite").have("mlinnotas:infernite_ingot", 4).xp(150),
				q("fortress", "minecraft:nether_bricks", 1, 3, "Fortaleza do Nether", "Nether Fortress",
						"As fortalezas agora ficam assentadas no chão. Blazes e esqueletos wither moram lá.",
						"Fortresses now sit on the ground. Blazes and wither skeletons live there.")
						.dep("enter_nether").structure("minecraft:fortress").xp(60),
				q("blaze_rods", "minecraft:blaze_rod", 2, 3, "Varas de Blaze", "Blaze Rods",
						"Viram pó de blaze para os Olhos do Ender (caminho para o End) e para o suporte de poções.",
						"They become blaze powder for Eyes of Ender (the way to the End) and the brewing stand.")
						.dep("fortress").have("minecraft:blaze_rod", 6).xp(60),
				q("wither_skulls", "minecraft:wither_skeleton_skull", 2, 4, "Crânios de Wither", "Wither Skulls",
						"Esqueletos wither às vezes soltam o crânio (Pilhagem ajuda). Três crânios invocam o Wither.",
						"Wither skeletons sometimes drop their skull (Looting helps). Three skulls summon the Wither.")
						.dep("fortress").have("minecraft:wither_skeleton_skull", 3).xp(100),
				q("bastion", "minecraft:gilded_blackstone", 1, 4, "Bastião", "Bastion",
						"Ruínas dos piglins, cheias de ouro e de piglins brutos. Use uma peça de armadura de ouro para os piglins comuns não atacarem.",
						"Piglin ruins full of gold and brutes. Wear a piece of gold armor so regular piglins don't attack.")
						.dep("enter_nether").structure("minecraft:bastion_remnant").xp(80));

		chapter("end", 5, "minecraft:end_stone", "End", "End",
				q("eyes", "minecraft:ender_eye", 0, 1, "Olhos do Ender", "Eyes of Ender",
						"Pó de blaze + pérola do Ender. Jogue um olho para o alto e siga a direção dele até a fortaleza.",
						"Blaze powder + ender pearl. Throw an eye and follow it to the stronghold.")
						.dep("nether/blaze_rods").have("minecraft:ender_eye", 12).xp(60),
				q("stronghold", "minecraft:end_portal_frame", 1, 1, "Fortaleza", "Stronghold",
						"O portal do End fica numa sala com lava e traças. Complete-o com os olhos.",
						"The End portal lies in a room with lava and silverfish. Fill it with the eyes.")
						.dep("eyes").structure("minecraft:stronghold").xp(60),
				q("enter_end", "minecraft:end_stone", 2, 1, "O Fim", "The End",
						"A ilha central virou um santuário em ruínas, com colunata de púrpura e muralha. O Dragão espera (capítulo Chefes).",
						"The central island is now a ruined sanctuary with a purpur colonnade and walls. The Dragon awaits (Bosses chapter).")
						.dep("stronghold").dimension("minecraft:the_end").xp(100),
				q("outer_islands", "minecraft:ender_pearl", 3, 1, "Ilhas Externas", "Outer Islands",
						"Depois do dragão surge um portal de passagem: jogue uma pérola nele. Mais de 1000 blocos do centro começam os biomas grandes do mod.",
						"After the dragon an end gateway appears: throw a pearl into it. Past 1000 blocks from the center the mod's large biomes begin.")
						.dep("enter_end", "bosses/ender_dragon").biome("minecraft:end_highlands").item("minecraft:ender_pearl", 4).xp(100),
				q("moss_fields", "mlinnotas:end_moss", 4, 0, "Campos de Musgo", "Moss Fields",
						"Musgo, grama do vazio, árvores do vazio, lírios e lagos rasos. Cheio de Águas-vivas do Vazio.",
						"Moss, void grass, void trees, lilies and shallow ponds. Full of Void Jellies.")
						.dep("outer_islands").biome("mlinnotas:end_moss_fields").xp(60),
				q("void_jelly", "mlinnotas:void_jelly", 5, 0, "Águas-vivas do Vazio", "Void Jellies",
						"Sinos translúcidos que flutuam. A Geleia do Vazio dá queda lenta ao comer.",
						"Floating translucent bells. Eating Void Jelly gives slow falling.")
						.dep("moss_fields").kill("mlinnotas:void_jelly", 3).xp(50),
				q("crystal_grove", "mlinnotas:ether_crystal_cluster", 4, 1, "Bosque Cristalino", "Crystal Grove",
						"Espinhos gigantes de cristal, geodos abertos de calcita e cristal etéreo.",
						"Giant crystal spikes and open calcite geodes of ether crystal.")
						.dep("outer_islands").biome("mlinnotas:ether_crystal_grove").xp(60),
				q("crystal_mite", "mlinnotas:ether_crystal_shard", 5, 1, "Ácaros Cristalinos", "Crystal Mites",
						"Caranguejos de seis pernas cobertos de cristal: a mordida dá brilho e lentidão.",
						"Six-legged crabs covered in crystal: their bite causes glowing and slowness.")
						.dep("crystal_grove").kill("mlinnotas:crystal_mite", 3).xp(50),
				q("void_wastes", "mlinnotas:void_ash", 4, 2, "Ermos do Vazio", "Void Wastes",
						"Na beirada das regiões: cinza, crateras, pilares de basalto do vazio e fósseis gigantes.",
						"At the edge of regions: ash, craters, void basalt pillars and giant fossils.")
						.dep("outer_islands").biome("mlinnotas:void_wastes").xp(60),
				q("chorus_forest", "minecraft:chorus_flower", 4, 3, "Floresta de Coro", "Chorus Forest",
						"Coro gigante e denso, flores de coro e cogumelos do End. Os Saltadores de Coro pulam por aí e se teleportam quando apanham.",
						"Dense giant chorus, chorus flowers and End mushrooms. Chorus Hoppers bounce around and teleport when hit.")
						.dep("outer_islands").biome("mlinnotas:chorus_forest").xp(60),
				q("chorus_hopper", "minecraft:chorus_fruit", 5, 3, "Saltadores de Coro", "Chorus Hoppers",
						"Criaturas passivas que soltam fruta do coro.",
						"Passive creatures that drop chorus fruit.")
						.dep("chorus_forest").kill("mlinnotas:chorus_hopper", 2).xp(40),
				q("citadel", "minecraft:purpur_block", 4, 4, "Cidadela do End", "End Citadel",
						"Substitui a End City: ilha própria, fortaleza central de 5 andares e torres ligadas por pontes. Shulkers vigiam os telhados e as Sentinelas de Púrpura patrulham.",
						"Replaces the End City: its own island, a 5-floor central keep and towers joined by bridges. Shulkers guard the roofs and Purpur Sentinels patrol.")
						.dep("outer_islands").structure("mlinnotas:end_citadel").xp(150),
				q("elevator", "mlinnotas:purpur_elevator", 5, 4, "Elevador de Púrpura", "Purpur Elevator",
						"Pule em cima para subir e agache para descer até o próximo elevador da coluna (até 32 blocos). Faça os seus com núcleos de sentinela.",
						"Jump on it to go up and sneak to go down to the next elevator in the column (up to 32 blocks). Craft your own with sentinel cores.")
						.dep("citadel").have("mlinnotas:purpur_elevator", 1).xp(60),
				q("elytra", "minecraft:elytra", 5, 5, "Asas", "Wings",
						"O cofre no topo da fortaleza guarda o élitro, a cabeça de dragão e o altar astral.",
						"The vault atop the keep holds the elytra, a dragon head and the astral altar.")
						.dep("citadel").have("minecraft:elytra", 1).item("minecraft:firework_rocket", 32).xp(200));

		chapter("tech", 6, "mlinnotas:furnace_upgrade_iron", "Tecnologia", "Technology",
				q("furnace_stone", "mlinnotas:furnace_upgrade_stone", 0, 0, "Fornalha Melhor", "Better Furnace",
						"Botão direito numa fornalha com o upgrade. Pedra deixa 1,25x mais rápida; cada nível usa o anterior no centro da receita, até celéstio (32x).",
						"Right-click a furnace with the upgrade. Stone makes it 1.25x faster; each tier uses the previous one in the middle of the recipe, up to celestium (32x).")
						.dep("basics/stone_tools").have("mlinnotas:furnace_upgrade_stone", 1).item("minecraft:charcoal", 8),
				q("furnace_iron", "mlinnotas:furnace_upgrade_iron", 1, 0, "Fornalha de Ferro", "Iron Furnace",
						"O upgrade de ferro usa o de cobre no centro. O nível aparece no HUD (N) ao mirar a fornalha.",
						"The iron upgrade uses the copper one in the middle. The tier shows on the HUD (N) when you look at the furnace.")
						.dep("furnace_stone", "mining/skill_iron").have("mlinnotas:furnace_upgrade_iron", 1).xp(60),
				q("furnace_endite", "mlinnotas:furnace_upgrade_endite", 2, 0, "Fundição Dupla", "Double Smelting",
						"Do upgrade de endita em diante, a fornalha dá 2 lingotes por minério bruto ou bloco de minério.",
						"From the endite upgrade on, the furnace gives 2 ingots per raw ore or ore block.")
						.dep("furnace_iron", "mining/skill_endite").have("mlinnotas:furnace_upgrade_endite", 1).xp(300),
				q("stone_chest", "mlinnotas:stone_chest", 0, 1, "Baús Maiores", "Bigger Chests",
						"Um baú por nível: pedra 36, ferro 54, ouro 81, diamante 108, até celéstio 243. O upgrade de baú converte um baú no lugar (botão direito), mantendo conteúdo e nome.",
						"One chest per tier: stone 36, iron 54, gold 81, diamond 108, up to celestium 243. A chest upgrade converts a placed chest (right-click), keeping contents and name.")
						.dep("basics/stone_tools").haveTag("mlinnotas:quest/stone_chest", 1, "mlinnotas:stone_chest", "stone_chest",
								"Tenha um Baú de Pedra ou o upgrade", "Have a Stone Chest or its upgrade").xp(30),
				q("iron_chest", "mlinnotas:iron_chest", 1, 1, "Baú de Ferro", "Iron Chest",
						"54 espaços. A tela rola com a roda do mouse quando passa de 6 linhas, e o botão Ord ordena o baú inteiro.",
						"54 slots. The screen scrolls with the mouse wheel past 6 rows, and the Ord button sorts the whole chest.")
						.dep("stone_chest", "mining/skill_iron").haveTag("mlinnotas:quest/iron_chest", 1, "mlinnotas:iron_chest", "iron_chest",
								"Tenha um Baú de Ferro ou o upgrade", "Have an Iron Chest or its upgrade").xp(60),
				q("diamond_chest", "mlinnotas:diamond_chest", 2, 1, "Baú de Diamante", "Diamond Chest",
						"108 espaços: espaço de sobra para a base da dupla.",
						"108 slots: plenty of room for the pair's base.")
						.dep("iron_chest", "mining/skill_diamond").haveTag("mlinnotas:quest/diamond_chest", 1, "mlinnotas:diamond_chest", "diamond_chest",
								"Tenha um Baú de Diamante ou o upgrade", "Have a Diamond Chest or its upgrade").xp(150),
				q("backpack_size", "mlinnotas:backpack_upgrade_size", 0, 2, "Mochila Maior", "Bigger Backpack",
						"+27 espaços por upgrade, até 81. Pegue o upgrade no cursor, passe sobre a mochila no inventário e clique com o botão direito.",
						"+27 slots per upgrade, up to 81. Pick up the upgrade, hover it over the backpack in your inventory and right-click.")
						.dep("basics/backpack").have("mlinnotas:backpack_upgrade_size", 1).xp(50),
				q("backpack_tools", "mlinnotas:backpack_upgrade_furnace", 1, 2, "Mochila de Ofícios", "Utility Backpack",
						"O upgrade de fornalha funde o conteúdo usando combustível da própria mochila; o de crafting põe uma grade 3x3 na tela; os tanques de água e lava enchem com Shift+clique numa fonte.",
						"The furnace upgrade smelts the contents using fuel from the backpack; crafting adds a 3x3 grid to its screen; water and lava tanks fill with Shift+click on a source.")
						.dep("backpack_size").have("mlinnotas:backpack_upgrade_furnace", 1).xp(80),
				q("enchanting", "minecraft:enchanting_table", 0, 3, "Encantamento Escolhido", "Chosen Enchantments",
						"A mesa não sorteia mais: escolha o encantamento e o nível. Custa XP e 1 lápis por nível; 15 estantes liberam o máximo. Dá para subir um encantamento pagando só a diferença.",
						"The table no longer rolls dice: pick the enchantment and level. Costs XP and 1 lapis per level; 15 bookshelves unlock the max. You can upgrade an enchantment paying only the difference.")
						.dep("mining/skill_diamond").advancement("minecraft:story/enchant_item", "minecraft:enchanted_book", "enchant",
								"Encante um item", "Enchant an item").item("minecraft:lapis_lazuli", 16).xp(100),
				q("beacon", "minecraft:beacon", 0, 4, "Sinalizador por Nível", "Tiered Beacon",
						"O metal mais fraco da pirâmide define o nível: ferro alcança 50, cobalto 70, netherite 100, até celéstio 200. Infernita em diante libera nível III e Garimpo; celéstio libera Voo dentro do alcance.",
						"The weakest metal in the pyramid sets the tier: iron reaches 50, cobalt 70, netherite 100, up to celestium 200. Infernite and up unlock level III and Prospecting; celestium unlocks Flight within range.")
						.dep("bosses/wither").have("minecraft:beacon", 1).xp(300),
				q("storage_unit", "mlinnotas:storage_unit_copper", 0, 5, "Armazenamento em Massa", "Bulk Storage",
						"A Unidade de Armazenamento guarda poucos tipos, mas muito de cada: a de cobre já leva 16 tipos com 1024 de cada, sem limite de pilha. Botão direito com um item guarda direto; quebrada, leva o conteúdo junto.",
						"The Storage Unit holds few item types but lots of each: the copper one already takes 16 types with 1024 of each, no stack limit. Right-click with an item to store it; when broken it keeps its contents.")
						.dep("stone_chest", "mining/skill_copper").have("mlinnotas:storage_unit_copper", 1).item("minecraft:redstone", 16).xp(50),
				q("storage_terminal", "mlinnotas:storage_terminal", 1, 5, "Rede de Armazenamento", "Storage Network",
						"Cabos ligam unidades, terminais e baús (baús, barris, caixas de shulker e baús do mod) numa rede só. O terminal mostra tudo com a quantidade somada: busque, ordene, tire e guarde de um lugar.",
						"Cables link units, terminals and chests (chests, barrels, shulker boxes and the mod's chests) into one network. The terminal shows everything with summed amounts: search, sort, take and store from one place.")
						.dep("storage_unit").have("mlinnotas:storage_terminal", 1).item("mlinnotas:storage_cable", 16).xp(80),
				q("crafting_terminal", "mlinnotas:crafting_terminal", 2, 5, "Bancada Conectada", "Connected Workbench",
						"O Terminal de Crafting tem uma grade 3x3 que se reabastece da rede: Shift+clique no resultado fabrica enquanto houver ingredientes.",
						"The Crafting Terminal has a 3x3 grid that refills from the network: Shift+click the result to keep crafting while ingredients last.")
						.dep("storage_terminal").have("mlinnotas:crafting_terminal", 1).xp(60),
				q("storage_diamond", "mlinnotas:storage_unit_diamond", 2, 6, "Cofre Sem Fundo", "Bottomless Vault",
						"Cada nível melhora a unidade anterior com o bloco do metal (no crafting, mantendo o conteúdo). A de diamante guarda 48 tipos com 65 mil de cada.",
						"Each tier upgrades the previous unit with the metal's block (in the crafting grid, keeping the contents). The diamond one stores 48 types with 65 thousand of each.")
						.dep("storage_terminal", "mining/skill_diamond").have("mlinnotas:storage_unit_diamond", 1).xp(200));
	}
}
