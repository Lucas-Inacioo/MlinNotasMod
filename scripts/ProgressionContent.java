import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Conteudo do ritmo de progressao para o ContentGen: cadeia de ferraria pos-netherite (moldes, receitas de ferraria e
 * de duplicacao), Chave da Forja, tipo de dano das habilidades de chefe, loot do Wither e os textos que mudaram com os
 * numeros novos (dicas da arvore de habilidades, XP em pontos, teto de encantamento). Chamado por ContentGen.main.
 *
 * <p><b>Cadeia de ferraria</b>: as pecas de infernita, endita, astralita e celestio nao tem receita de mesa de
 * trabalho; saem da mesa de ferraria a partir da peca do nivel anterior + 1 lingote do nivel novo + o molde do nivel
 * (solto pelo chefe que libera o nivel). {@link #smithingUpgrade(String, String)} e chamado pelo ContentGen para cada
 * ferramenta/arma ({@code GEAR}) e armadura ({@code ARMOR}) dos niveis do mod: para uma arma nova por nivel basta
 * chamar {@code smithingUpgrade(tier, sufixo)} no lugar do {@code shaped(...)} dela (devolve false para os niveis que
 * continuam na mesa de trabalho).</p>
 */
class ProgressionContent {
	// id do molde, nivel de origem, nivel novo, material da duplicacao (x7), bloco base, nome pt, nome en, origem pt, origem en
	static final String[][] CHAIN = {
			{"infernal", "netherite", "infernite", "mlinnotas:raw_infernite", "minecraft:blackstone", "Molde Infernal", "Infernal Smithing Template",
					"Solto pelo Colosso Ígneo", "Dropped by the Igneous Colossus"},
			{"end", "infernite", "endite", "mlinnotas:raw_endite", "minecraft:end_stone", "Molde do Fim", "End Smithing Template",
					"Solto pelo Dragão do Ender", "Dropped by the Ender Dragon"},
			{"astral", "endite", "astralite", "mlinnotas:raw_astralite", "minecraft:purpur_block", "Molde Astral", "Astral Smithing Template",
					"Solto pelo Arauto Astral", "Dropped by the Astral Herald"},
			{"celestial", "astralite", "celestium", "mlinnotas:astralite_ingot", "minecraft:crying_obsidian", "Molde Celestial",
					"Celestial Smithing Template", "Solto pelo Wither", "Dropped by the Wither"},
	};
	/** Nomes dos niveis (pt, en) usados nos textos dos moldes. */
	static final String[][] TIER_NAMES = {
			{"netherite", "Netherite", "Netherite"}, {"infernite", "Infernita", "Infernite"}, {"endite", "Endita", "Endite"},
			{"astralite", "Astralita", "Astralite"}, {"celestium", "Celéstio", "Celestium"}};
	/** Pecas que existem no vanilla com prefixo netherite_ (o resto vem do mod). */
	static final Set<String> VANILLA_NETHERITE = Set.of("sword", "shovel", "pickaxe", "axe", "hoe", "spear", "helmet", "chestplate", "leggings", "boots");

	static String[] step(String tier) {
		for (String[] s : CHAIN) {
			if (s[2].equals(tier)) {
				return s;
			}
		}
		return null;
	}

	/** O nivel {@code tier} e feito na mesa de ferraria (infernita, endita, astralita, celestio). */
	static boolean bySmithing(String tier) {
		return step(tier) != null;
	}

	/** Id da peca {@code suffix} do nivel {@code tier} (netherite vanilla quando existe). */
	static String gear(String tier, String suffix) {
		return tier.equals("netherite") && VANILLA_NETHERITE.contains(suffix) ? "minecraft:netherite_" + suffix : "mlinnotas:" + tier + "_" + suffix;
	}

	/**
	 * Escreve a receita de ferraria de {@code <tier>_<suffix>} (peca do nivel anterior + lingote do nivel + molde) no
	 * lugar da receita de mesa de trabalho. Devolve false (nada escrito) se o nivel nao e da cadeia de ferraria.
	 */
	static boolean smithingUpgrade(String tier, String suffix) throws IOException {
		String[] s = step(tier);
		if (s == null) {
			return false;
		}
		String id = tier + "_" + suffix;
		ContentGen.write("data/mlinnotas/recipe/" + id + ".json", "{\"type\":\"minecraft:smithing_transform\",\"addition\":\"mlinnotas:" + tier
				+ "_ingot\",\"base\":\"" + gear(s[1], suffix) + "\",\"result\":{\"id\":\"mlinnotas:" + id + "\"},\"template\":\"mlinnotas:" + s[0]
				+ "_upgrade_smithing_template\"}");
		return true;
	}

	static String tierName(String id, int lang) {
		for (String[] t : TIER_NAMES) {
			if (t[0].equals(id)) {
				return t[lang];
			}
		}
		return id;
	}

	static void generate() throws IOException {
		// --- moldes de ferraria ---
		for (String[] s : CHAIN) {
			String id = s[0] + "_upgrade_smithing_template";
			ContentGen.simpleItem(id, "item/generated");
			ContentGen.name("item", id, s[5], s[6]);
			text("item.mlinnotas." + id + ".tooltip", s[7] + "; duplica na mesa de trabalho", s[8] + "; duplicate it on a crafting table");
			String key = "item.mlinnotas.smithing_template." + s[0];
			String fromPt = tierName(s[1], 1);
			String fromEn = tierName(s[1], 2);
			String toPt = tierName(s[2], 1);
			String toEn = tierName(s[2], 2);
			text(key + ".applies_to", "Equipamento de " + fromPt, fromEn + " Equipment");
			text(key + ".ingredients", "Lingote de " + toPt, toEn + " Ingot");
			text(key + ".base_slot_description", "Coloque arma, ferramenta ou armadura de " + fromPt, "Add " + fromEn + " weapon, tool or armor");
			text(key + ".additions_slot_description", "Coloque um Lingote de " + toPt, "Add a " + toEn + " Ingot");
			// duplicacao como a do netherite: molde + 7 do material + bloco base = 2 moldes
			ContentGen.write("data/mlinnotas/recipe/" + id + ".json", "{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"key\":{\"#\":\""
					+ s[3] + "\",\"C\":\"" + s[4] + "\",\"S\":\"mlinnotas:" + id + "\"},\"pattern\":[\"#S#\",\"#C#\",\"###\"],"
					+ "\"result\":{\"count\":2,\"id\":\"mlinnotas:" + id + "\"}}");
		}
		// o Wither (loot vanilla vazio; a estrela sai pelo codigo) solta o Molde Celestial
		ContentGen.write("data/minecraft/loot_table/entities/wither.json", "{\"type\":\"minecraft:entity\",\"pools\":[{\"entries\":[{\"type\":"
				+ "\"minecraft:item\",\"name\":\"mlinnotas:celestial_upgrade_smithing_template\"}],\"rolls\":1}],\"random_sequence\":\"minecraft:entities/wither\"}");

		// --- Chave da Forja: acorda o Colosso Igneo ---
		ContentGen.simpleItem("forge_key", "item/generated");
		ContentGen.name("item", "forge_key", "Chave da Forja", "Forge Key");
		text("item.mlinnotas.forge_key.tooltip", "Botão direito no Colosso Ígneo adormecido (ou no trono dele) para acordá-lo; é gasta",
				"Right-click the sleeping Igneous Colossus (or its throne) to wake it; it is used up");
		ContentGen.write("data/mlinnotas/recipe/forge_key.json", "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":["
				+ ContentGen.quoteAll(List.of("mlinnotas:abyssal_core", "minecraft:blaze_rod", "minecraft:blaze_rod", "minecraft:blaze_rod",
				"minecraft:blaze_rod", "minecraft:blaze_rod", "minecraft:blaze_rod", "minecraft:blaze_rod", "minecraft:blaze_rod"))
				+ "],\"result\":{\"id\":\"mlinnotas:forge_key\"}}");
		text("message.mlinnotas.forge_key.nothing", "Nenhum colosso adormecido por perto", "No sleeping colossus nearby");
		text("message.mlinnotas.forge_key.asleep", "%s dorme um sono de pedra: só a Chave da Forja o desperta",
				"%s sleeps a stone sleep: only the Forge Key can wake it");
		text("message.mlinnotas.altar.missing_two", "O altar pede %s × %s e %s × %s", "The altar asks for %s × %s and %s × %s");

		// --- dano das habilidades de chefe: armadura vale, Protecao nao (bypasses_enchantments) ---
		ContentGen.write("data/mlinnotas/damage_type/boss_ability.json", "{\"exhaustion\":0.1,\"message_id\":\"mlinnotas.boss_ability\","
				+ "\"scaling\":\"when_caused_by_living_non_player\"}");
		ContentGen.tag("damage_type", "minecraft:bypasses_enchantments", List.of("mlinnotas:boss_ability"), false);
		text("death.attack.mlinnotas.boss_ability", "%1$s caiu diante do ataque especial de %2$s", "%1$s fell to %2$s's special attack");
		text("death.attack.mlinnotas.boss_ability.item", "%1$s caiu diante do ataque especial de %2$s", "%1$s fell to %2$s's special attack");
		text("death.attack.mlinnotas.boss_ability.player", "%1$s caiu diante do ataque especial de %2$s", "%1$s fell to %2$s's special attack");

		// --- teto de encantamento pelo nivel de Mineracao do mundo ---
		text("gui.mlinnotas.enchant.mining_cap", "Nível %s precisa de Mineração: %s na árvore do mundo (tecla K)",
				"Level %s needs Mining: %s in the world skill tree (K)");
		text("message.mlinnotas.enchant.mining_cap", "A Mineração do mundo ainda não libera esse nível (árvore de habilidades, tecla K)",
				"The world's Mining level does not unlock that level yet (skill tree, K)");
		text("gui.mlinnotas.enchant.cap", "Teto: %s", "Cap: %s");
		text("gui.mlinnotas.enchant.cap_none", "livre", "none");
		text("gui.mlinnotas.recipes.kind.smithing", "Mesa de ferraria", "Smithing Table");
		// reagente tematico por encantamento (slot abaixo do lapis)
		text("gui.mlinnotas.enchant.reagent", "%s ×%s (tem %s)", "%s ×%s (have %s)");
		text("gui.mlinnotas.enchant.needs_reagent", "Precisa de %s × %s no slot de reagente (abaixo do lápis)",
				"Needs %s × %s in the reagent slot (below the lapis)");
		text("message.mlinnotas.enchant.not_enough_reagent", "Reagente insuficiente no slot abaixo do lápis", "Not enough reagent in the slot below the lapis");

		// --- arvore de habilidades: XP em pontos e dicas dos custos novos ---
		text("gui.mlinnotas.skills.your_levels", "Seu XP: %s níveis (%s pontos)", "Your XP: %s levels (%s points)");
		text("gui.mlinnotas.skills.cost_xp", "XP: %s/%s pontos (faltam ~%s níveis)", "XP: %s/%s points (~%s levels to go)");
		text("gui.mlinnotas.skills.remaining", "Falta depositar: %s itens e %s pontos de XP (~%s níveis). Selecione o nó e clique em Depositar.",
				"Still to deposit: %s items and %s XP points (~%s levels). Select the node and click Deposit.");
		hint("minecraft.iron_block", "9 lingotes de ferro na mesa de trabalho.", "9 iron ingots on a crafting table.");
		hint("mlinnotas.cobalt_block", "9 lingotes de cobalto na mesa de trabalho.", "9 cobalt ingots on a crafting table.");
		hint("mlinnotas.sentinel_core", "solto pelas Sentinelas de Púrpura da Cidadela do End, nas ilhas externas (a mais de 1000 blocos do centro).",
				"dropped by the Purpur Sentinels of the End Citadel, on the outer islands (over 1000 blocks from the center).");
		hint("mlinnotas.raw_cobalt", "minério de cobalto, no Overworld abaixo de y 16 (veios ricos, raros, abaixo de y 0). Picareta de ferro ou melhor.",
				"cobalt ore, in the Overworld below y 16 (rare rich veins below y 0). Iron pickaxe or better.");
		hint("mlinnotas.colossus_ember", "derrote o Colosso Ígneo, que dorme no trono da Forja do Colosso, nos Picos de Infernita do Nether (planaltos de basalto). "
						+ "Ele só acorda com a Chave da Forja (Núcleo Abissal + 8 varas de blaze) usada nele ou no trono.",
				"defeat the Igneous Colossus, asleep on the throne of the Colossus Forge in the Nether's Infernite Peaks (basalt plateaus). "
						+ "It only wakes when the Forge Key (Abyssal Core + 8 blaze rods) is used on it or its throne.");
		hint("mlinnotas.abyssal_core", "derrote o Guardião das Profundezas no Santuário Abissal, no Deep Dark entre y -40 e -16. Oferenda: 4 fragmentos de eco "
						+ "(baús das cidades ancestrais). Precisa de 2: um para a Mineração e outro para a Chave da Forja do Colosso.",
				"defeat the Abyssal Guardian in the Abyssal Sanctum, in the Deep Dark between y -40 and -16. Offering: 4 echo shards (ancient city chests). "
						+ "You need 2: one for Mining and one for the Colossus' Forge Key.");
		hint("mlinnotas.raw_endite", "minério de endita, só nas ilhas externas do End (fora da ilha central). Picareta de infernita.",
				"endite ore, only on the End's outer islands (not on the main island). Infernite pickaxe.");
		hint("mlinnotas.dragon_scale", "derrote o Dragão do Ender na ilha central do End. As escamas caem sobre o portal de saída: 2 a 4 na primeira vez, 1 a 2 "
						+ "quando ele é reinvocado.",
				"defeat the Ender Dragon on the End's main island. The scales drop on the exit portal: 2 to 4 the first time, 1 to 2 when it is respawned.");
		hint("mlinnotas.astral_fragment", "derrote o Arauto Astral no altar do cofre, no topo da Cidadela do End (ilhas externas; suba pelos elevadores de "
						+ "purpur). Oferenda: 4 Núcleos de Sentinela e 1 Escama de Dragão.",
				"defeat the Astral Herald at the vault altar on top of the End Citadel (outer islands; ride the purpur elevators up). "
						+ "Offering: 4 Sentinel Cores and 1 Dragon Scale.");
	}

	static void hint(String id, String pt, String en) {
		text("skill.mlinnotas.hint." + id, pt, en);
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
