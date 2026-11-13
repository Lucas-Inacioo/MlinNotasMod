import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Acessorios para o ContentGen: modelos dos itens, receitas dos aneis e colares de metal, tags das quests, todos os
 * textos (nomes, encaixes, atributos, efeitos, estagios, tela) e as quests penduradas nos capitulos Exploracao e Chefes
 * (via {@code QuestContent.EXTRA}, por isso roda antes do QuestContent). Os pools de loot dos artefatos nao sao JSON:
 * ficam em {@code accessories/ArtifactLoot} e entram pelo evento de loot da Fabric. Texturas: scripts/AccessoryTexGen.java.
 */
class AccessoryContent {
	static final String[][] METALS = {
			{"copper", "Cobre", "Copper", "minecraft:copper_ingot"},
			{"iron", "Ferro", "Iron", "minecraft:iron_ingot"},
			{"gold", "Ouro", "Gold", "minecraft:gold_ingot"},
			{"cobalt", "Cobalto", "Cobalt", "mlinnotas:cobalt_ingot"},
			{"diamond", "Diamante", "Diamond", "minecraft:diamond"},
			{"netherite", "Netherite", "Netherite", "minecraft:netherite_ingot"}};

	/** id, pt, en */
	static final String[][] ARTIFACTS = {
			{"rabbit_foot_charm", "Pata de Coelho Saltitante", "Bouncy Rabbit Foot"},
			{"climbing_glove", "Luva de Escalada", "Climbing Glove"},
			{"miner_lantern", "Lanterna de Mineiro", "Miner's Lantern"},
			{"life_amulet", "Amuleto de Vida", "Amulet of Life"},
			{"feather_charm", "Pena Leve", "Featherweight Charm"},
			{"thorn_necklace", "Colar de Espinhos", "Thorn Necklace"},
			{"magnet_ring", "Anel de Ímã", "Magnet Ring"},
			{"pocket_watch", "Relógio de Bolso", "Pocket Watch"},
			{"anchor_belt", "Cinto de Âncora", "Anchor Belt"},
			{"vampire_fang", "Presa Vampírica", "Vampire Fang"},
			{"diver_ring", "Anel do Mergulhador", "Diver's Ring"},
			{"prospector_glove", "Luva de Garimpeiro", "Prospector's Glove"},
			{"lucky_horseshoe", "Ferradura da Sorte", "Lucky Horseshoe"},
			{"traveler_belt", "Cinto do Viajante", "Traveler's Belt"},
			{"duelist_glove", "Luva do Duelista", "Duelist's Glove"},
			{"frost_ring", "Anel de Geada", "Frost Ring"},
			{"sun_pendant", "Pingente do Sol", "Sun Pendant"}};

	/** id, pt, en, chefe, texto pt, texto en */
	static final String[][] RELICS = {
			{"resonant_pendant", "Pingente Ressonante", "Resonant Pendant", "cobalt_golem",
					"Deixada pelo Golem de Cobalto. Abafa o pulso do Guardião das Profundezas.",
					"Left by the Cobalt Golem. Dampens the Abyssal Guardian's pulse."},
			{"abyssal_shell", "Concha Abissal", "Abyssal Shell", "abyssal_guardian",
					"Deixada pelo Guardião das Profundezas. Fria contra as chamas do Colosso.",
					"Left by the Abyssal Guardian. Cool against the Colossus' flames."},
			{"colossus_girdle", "Cinturão do Colosso", "Colossus Girdle", "igneous_colossus",
					"Deixada pelo Colosso Ígneo. Firme contra as investidas do Dragão.",
					"Left by the Igneous Colossus. Steady against the Dragon's charges."},
			{"dragonscale_charm", "Amuleto de Escama de Dragão", "Dragonscale Charm", "ender_dragon",
					"Deixada pelo Dragão do Ender. Desvia parte da magia do Arauto Astral.",
					"Left by the Ender Dragon. Turns aside some of the Astral Herald's magic."},
			{"herald_star", "Estrela do Arauto", "Herald's Star", "astral_herald",
					"Deixada pelo Arauto Astral. Protege um pouco do Wither.",
					"Left by the Astral Herald. Shields a little from the Wither."},
			{"wither_ring", "Anel do Wither", "Wither Ring", "wither",
					"Deixada pelo Wither. O troféu do fim do caminho.",
					"Left by the Wither. The trophy at the end of the road."},
			{"champion_seal", "Selo do Campeão", "Champion's Seal", "dungeon",
					"Às vezes deixado pelos chefes das masmorras.",
					"Sometimes left by dungeon bosses."}};

	static void generate() throws IOException {
		List<String> basics = new ArrayList<>();
		List<String> artifacts = new ArrayList<>();
		List<String> relics = new ArrayList<>();

		// --- aneis e colares de metal ---
		for (String[] m : METALS) {
			String ring = m[0] + "_ring";
			String necklace = m[0] + "_necklace";
			for (String id : List.of(ring, necklace)) {
				ContentGen.simpleItem(id, "item/generated");
				basics.add("mlinnotas:" + id);
			}
			ContentGen.name("item", ring, "Anel de " + m[1], m[2] + " Ring");
			ContentGen.name("item", necklace, "Colar de " + m[1], m[2] + " Necklace");
			if (m[0].equals("netherite")) {
				// netherite: o de diamante + 1 lingote
				for (String kind : List.of("ring", "necklace")) {
					ContentGen.write("data/mlinnotas/recipe/netherite_" + kind + ".json", "{\"type\":\"minecraft:crafting_shapeless\",\"category\":"
							+ "\"equipment\",\"ingredients\":[\"mlinnotas:diamond_" + kind + "\",\"minecraft:netherite_ingot\"],\"result\":{\"id\":"
							+ "\"mlinnotas:netherite_" + kind + "\"}}");
				}
			} else {
				ContentGen.shaped(ring, " M |M M| M ", Map.of("M", m[3]), "equipment");
				ContentGen.shaped(necklace, " S |S S| M ", Map.of("S", "minecraft:string", "M", m[3]), "equipment");
			}
		}
		for (String[] a : ARTIFACTS) {
			ContentGen.simpleItem(a[0], "item/generated");
			ContentGen.name("item", a[0], a[1], a[2]);
			artifacts.add("mlinnotas:" + a[0]);
		}
		for (String[] r : RELICS) {
			ContentGen.simpleItem(r[0], "item/generated");
			ContentGen.name("item", r[0], r[1], r[2]);
			text("accessory.mlinnotas.relic_of." + r[3], r[4], r[5]);
			relics.add("mlinnotas:" + r[0]);
		}
		ContentGen.tag("item", "mlinnotas:accessories", concat(basics, artifacts, relics), false);
		ContentGen.tag("item", "mlinnotas:quest/basic_accessories", basics, false);
		ContentGen.tag("item", "mlinnotas:quest/artifacts", artifacts, false);
		ContentGen.tag("item", "mlinnotas:quest/relics", relics, false);

		// --- textos ---
		text("accessory.mlinnotas.slot.ring", "Anel", "Ring");
		text("accessory.mlinnotas.slot.necklace", "Colar", "Necklace");
		text("accessory.mlinnotas.slot.belt", "Cinto", "Belt");
		text("accessory.mlinnotas.slot.gloves", "Luvas", "Gloves");
		text("accessory.mlinnotas.slot.charm", "Amuleto", "Charm");
		text("accessory.mlinnotas.kind.basic", "Acessório · %s", "Accessory · %s");
		text("accessory.mlinnotas.kind.artifact", "Artefato · %s", "Artifact · %s");
		text("accessory.mlinnotas.kind.relic", "Relíquia · %s", "Relic · %s");
		text("accessory.mlinnotas.unique", "Único: dois iguais não somam", "Unique: two of the same don't stack");
		text("accessory.mlinnotas.equip_hint", "Botão direito ou aba Acessórios do inventário para equipar",
				"Right-click or use the inventory's Accessories tab to equip");

		String[][] stats = {
				{"damage", "%s de dano corpo a corpo", "%s melee damage", "Dano", "Damage", "Dano", "Dmg"},
				{"damage_reduction", "%s de redução de dano", "%s damage reduction", "Redução de dano", "Damage reduction", "Def.", "Def"},
				{"speed", "%s de velocidade", "%s movement speed", "Velocidade", "Speed", "Vel.", "Spd"},
				{"max_health", "%s de vida máxima", "%s max health", "Vida máxima", "Max health", "Vida", "HP"},
				{"knockback_resistance", "%s de resistência a empurrão", "%s knockback resistance", "Resistência a empurrão",
						"Knockback resistance", "", ""},
				{"fall_reduction", "%s de redução de dano de queda", "%s fall damage reduction", "Redução de queda", "Fall reduction", "", ""},
				{"mining_speed", "%s de velocidade de mineração", "%s mining speed", "Velocidade de mineração", "Mining speed", "", ""},
				{"attack_speed", "%s de velocidade de ataque", "%s attack speed", "Velocidade de ataque", "Attack speed", "", ""},
				{"luck", "%s de sorte", "%s luck", "Sorte", "Luck", "", ""},
				{"lifesteal", "%s do dano causado vira vida (1x/s)", "%s of damage dealt heals you (1/s)", "Roubo de vida", "Lifesteal", "", ""},
				{"fire_reduction", "%s de redução de fogo e queimadura", "%s fire damage and burn time reduction", "Redução de fogo",
						"Fire reduction", "", ""},
				{"sonic_reduction", "%s de redução do pulso sônico", "%s sonic pulse reduction", "Redução sônica", "Sonic reduction", "", ""},
				{"magic_reduction", "%s de redução de dano mágico", "%s magic damage reduction", "Redução mágica", "Magic reduction", "", ""},
				{"blast_reduction", "%s de redução de explosões e murchar", "%s explosion and wither reduction", "Redução de explosão",
						"Blast reduction", "", ""},
				{"step_height", "%s bloco de altura de degrau", "%s block step height", "Degrau", "Step height", "", ""},
				{"oxygen", "%s de fôlego embaixo d'água", "%s underwater breath", "Fôlego", "Breath", "", ""},
				{"swim_speed", "%s de velocidade nadando", "%s swim speed", "Nado", "Swim speed", "", ""}};
		for (String[] s : stats) {
			text("accessory.mlinnotas.stat." + s[0], s[1], s[2]);
			text("accessory.mlinnotas.stat_name." + s[0], s[3], s[4]);
			if (!s[5].isEmpty()) {
				text("gui.mlinnotas.accessories.bar." + s[0], s[5], s[6]);
			}
		}
		text("accessory.mlinnotas.ability.double_jump", "Pulo duplo no ar", "Double jump in mid-air");
		text("accessory.mlinnotas.ability.climb", "Escala paredes encostando nelas (agachar segura)", "Climbs walls you push against (sneak to hold)");
		text("accessory.mlinnotas.ability.lantern", "No escuro das cavernas: Visão Noturna por 15 s (recarga de 60 s)",
				"In dark caves: Night Vision for 15 s (60 s recharge)");
		text("accessory.mlinnotas.ability.magnet", "Puxa itens e XP a até 6 blocos (agachar desliga)", "Pulls items and XP within 6 blocks (sneak to stop)");
		text("accessory.mlinnotas.ability.watch", "Mostra dia, hora e estação no canto da tela", "Shows day, time and season in the corner of the screen");
		text("accessory.mlinnotas.ability.thorns", "Quem bate corpo a corpo leva 1 coração de dano (1x/s)", "Melee attackers take 1 heart of damage (1/s)");
		text("accessory.mlinnotas.ability.frost", "Golpes deixam o alvo lento por 2 s (1x/s)", "Hits slow the target for 2 s (1/s)");
		text("accessory.mlinnotas.ability.sunlight", "Sob o sol: cura meio coração a cada 5 s", "Under the sun: heals half a heart every 5 s");
		text("accessory.mlinnotas.ability.wither_touch", "Golpes aplicam Murchar por 3 s (1x/s)", "Hits apply Wither for 3 s (1/s)");

		text("accessory.mlinnotas.stage.pre_golem", "Antes do Golem", "Before the Golem");
		text("accessory.mlinnotas.stage.golem_guardian", "Golem a Guardião", "Golem to Guardian");
		text("accessory.mlinnotas.stage.netherite_dragon", "Netherite a Dragão", "Netherite to Dragon");
		text("accessory.mlinnotas.stage.endgame", "Endita em diante", "Endite and beyond");
		text("accessory.mlinnotas.capped", "%s limitado a %s no estágio %s", "%s capped at %s at stage %s");
		text("accessory.mlinnotas.capped_hint", "O teto sobe com a Mineração do mundo (tecla K)", "The cap rises with the world's Mining level (K)");

		text("container.mlinnotas.accessories", "Acessórios", "Accessories");
		text("button.mlinnotas.accessories", "Acessórios", "Accessories");
		text("button.mlinnotas.accessories.back", "Voltar ao inventário", "Back to inventory");
		text("gui.mlinnotas.accessories.stage", "Estágio", "Stage");
		text("gui.mlinnotas.accessories.summary", "Bônus no estágio: %s", "Bonuses at stage: %s");
		text("gui.mlinnotas.accessories.line", "%s (teto %s)", "%s (cap %s)");
		text("gui.mlinnotas.accessories.line_capped", "%s: cortado, somaria %s", "%s: capped, would be %s");
		text("gui.mlinnotas.accessories.none", "Nenhum acessório equipado", "No accessories equipped");
		text("gui.mlinnotas.accessories.stage_hint", "O teto sobe com a Mineração do mundo (K)", "The cap rises with the world's Mining level (K)");
		text("message.mlinnotas.accessories.no_slot", "Nenhum encaixe livre para este acessório", "No free slot for this accessory");
		text("hud.mlinnotas.pocket_watch.time", "Dia %s · %s", "Day %s · %s");

		// --- quests nos capitulos existentes ---
		QuestContent.EXTRA.computeIfAbsent("exploration", k -> new ArrayList<>()).addAll(List.of(
				QuestContent.q("trinkets", "mlinnotas:copper_ring", 0, 0, "Bugigangas", "Trinkets",
								"Anéis (dano) e colares (defesa) de metal saem da bancada. Equipe pela aba Acessórios do inventário: 2 anéis, colar, cinto, luvas e amuleto. A soma de tudo tem um teto que sobe com a Mineração do mundo.",
								"Metal rings (damage) and necklaces (defense) come from the crafting table. Equip them through the inventory's Accessories tab: 2 rings, necklace, belt, gloves and charm. Everything adds up to a cap that rises with the world's Mining level.")
						.dep("basics/welcome").haveTag("mlinnotas:quest/basic_accessories", 1, "mlinnotas:copper_ring", "basic_accessory", "Um anel ou colar de metal",
								"A metal ring or necklace")
						.xp(30),
				QuestContent.q("artifact", "mlinnotas:pocket_watch", 2, 4, "Achado Raro", "Rare Find",
						"Artefatos não têm receita: aparecem em baús de ruínas, masmorras, templos, minas, postos de saqueadores, naufrágios e na Cidadela do End. Pata de coelho com pulo duplo, luva de escalada, anel de ímã...",
						"Artifacts have no recipe: they turn up in chests of ruins, dungeons, temples, mines, outposts, shipwrecks and the End Citadel. A double-jump rabbit foot, a climbing glove, a magnet ring...")
						.dep("explore")
						.haveTag("mlinnotas:quest/artifacts", 1, "mlinnotas:pocket_watch", "artifact", "Qualquer artefato", "Any artifact")
						.item("mlinnotas:return_scroll", 1).xp(80)));
		QuestContent.EXTRA.computeIfAbsent("bosses", k -> new ArrayList<>()).addAll(List.of(
				QuestContent.q("first_relic", "mlinnotas:resonant_pendant", 3, 0, "Relíquia", "Relic",
						"Cada chefe deixa uma relíquia que ajuda contra o próximo, sem anular a mecânica dele: o Pingente Ressonante do Golem abafa parte do pulso sônico do Guardião.",
						"Each boss leaves a relic that helps against the next one without cancelling its mechanic: the Golem's Resonant Pendant dampens part of the Guardian's sonic pulse.")
						.dep("cobalt_golem").have("mlinnotas:resonant_pendant", 1).xp(100),
				QuestContent.q("all_relics", "mlinnotas:wither_ring", 5, 2.5, "Colecionador de Relíquias", "Relic Collector",
						"As seis relíquias dos chefes: Pingente Ressonante, Concha Abissal, Cinturão do Colosso, Amuleto de Escama de Dragão, Estrela do Arauto e Anel do Wither.",
						"All six boss relics: Resonant Pendant, Abyssal Shell, Colossus Girdle, Dragonscale Charm, Herald's Star and Wither Ring.")
						.dep("legends").have("mlinnotas:resonant_pendant", 1).have("mlinnotas:abyssal_shell", 1).have("mlinnotas:colossus_girdle", 1)
						.have("mlinnotas:dragonscale_charm", 1).have("mlinnotas:herald_star", 1).have("mlinnotas:wither_ring", 1).xp(300)));
	}

	@SafeVarargs
	static List<String> concat(List<String>... lists) {
		List<String> out = new ArrayList<>();
		for (List<String> list : lists) {
			out.addAll(list);
		}
		return out;
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
