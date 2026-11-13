package com.mlin.notas.skills;

import com.mlin.notas.tier.Tier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arvore de habilidades do mundo (logica pura): nos, custos e de onde vem cada item do custo.
 *
 * <p>Tronco Mineracao (12 niveis = {@link Tier}): sem o nivel de um tier, os minerios dele nao quebram
 * ({@link OreTiers}). Os custos de cada nivel sao minerados no nivel anterior ou dropados por um chefe.
 * "Minerio" de carvao/cobre/ferro/ouro aceita o bloco (toque suave) ou o drop (carvao, cobre bruto...) pelas tags
 * {@code mlinnotas:skill_cost/*}.</p>
 *
 * <p>Ramos laterais (Combate, Vigor, Agilidade, Prospeccao), 5 niveis cada, pagos com lingotes/gemas; o nivel n
 * exige Mineracao {@link #SIDE_REQUIREMENTS}[n-1].</p>
 */
public final class SkillTree {
	public static final String COAL = "#mlinnotas:skill_cost/coal";
	public static final String COPPER = "#mlinnotas:skill_cost/copper";
	public static final String IRON = "#mlinnotas:skill_cost/iron";
	public static final String GOLD = "#mlinnotas:skill_cost/gold";

	/** Mineracao minima de cada nivel dos ramos laterais (nivel 1..5). */
	public static final Tier[] SIDE_REQUIREMENTS = {Tier.COPPER, Tier.IRON, Tier.COBALT, Tier.DIAMOND, Tier.NETHERITE};

	/** De onde sai um item de custo: minerio de um tier (precisa de Mineracao nesse tier) ou chefe. */
	public record Source(Tier miningTier, String boss) {
		public static Source mined(Tier tier) {
			return new Source(tier, null);
		}

		public static Source boss(String boss) {
			return new Source(null, boss);
		}

		public boolean isBoss() {
			return boss != null;
		}
	}

	private static final Map<String, Source> SOURCES = new LinkedHashMap<>();
	private static final List<SkillNode> NODES = new ArrayList<>();

	static {
		SOURCES.put("minecraft:cobblestone", Source.mined(Tier.WOOD));
		SOURCES.put(COAL, Source.mined(Tier.STONE));
		SOURCES.put(COPPER, Source.mined(Tier.COPPER));
		SOURCES.put(IRON, Source.mined(Tier.IRON));
		SOURCES.put(GOLD, Source.mined(Tier.GOLD));
		SOURCES.put("mlinnotas:raw_cobalt", Source.mined(Tier.COBALT));
		SOURCES.put("minecraft:diamond", Source.mined(Tier.DIAMOND));
		SOURCES.put("minecraft:netherite_scrap", Source.mined(Tier.NETHERITE));
		SOURCES.put("mlinnotas:raw_infernite", Source.mined(Tier.INFERNITE));
		SOURCES.put("mlinnotas:raw_endite", Source.mined(Tier.ENDITE));
		SOURCES.put("mlinnotas:raw_astralite", Source.mined(Tier.ASTRALITE));
		// lingotes: fundidos do minerio do proprio tier (netherite = sucata + ouro)
		SOURCES.put("minecraft:copper_ingot", Source.mined(Tier.COPPER));
		SOURCES.put("minecraft:iron_ingot", Source.mined(Tier.IRON));
		SOURCES.put("mlinnotas:cobalt_ingot", Source.mined(Tier.COBALT));
		SOURCES.put("minecraft:netherite_ingot", Source.mined(Tier.NETHERITE));
		// chefes
		SOURCES.put("mlinnotas:cobalt_heart", Source.boss("cobalt_golem"));
		SOURCES.put("mlinnotas:abyssal_core", Source.boss("abyssal_guardian"));
		SOURCES.put("mlinnotas:colossus_ember", Source.boss("igneous_colossus"));
		SOURCES.put("mlinnotas:dragon_scale", Source.boss("ender_dragon"));
		SOURCES.put("mlinnotas:astral_fragment", Source.boss("astral_herald"));
		SOURCES.put("minecraft:nether_star", Source.boss("wither"));

		mining(Tier.STONE, 0, cost("minecraft:cobblestone", 32));
		mining(Tier.COPPER, 0, cost(COAL, 10, "minecraft:coal_ore"));
		mining(Tier.IRON, 3, cost(COPPER, 10, "minecraft:copper_ore"));
		mining(Tier.GOLD, 5, cost(IRON, 10, "minecraft:iron_ore"));
		mining(Tier.COBALT, 10, cost("mlinnotas:cobalt_heart", 1), cost(GOLD, 10, "minecraft:gold_ore"));
		mining(Tier.DIAMOND, 15, cost("mlinnotas:raw_cobalt", 10));
		mining(Tier.NETHERITE, 20, cost("mlinnotas:abyssal_core", 1), cost("minecraft:diamond", 5));
		mining(Tier.INFERNITE, 20, cost("mlinnotas:colossus_ember", 1), cost("minecraft:netherite_scrap", 4));
		mining(Tier.ENDITE, 25, cost("mlinnotas:dragon_scale", 1), cost("mlinnotas:raw_infernite", 10));
		mining(Tier.ASTRALITE, 30, cost("mlinnotas:astral_fragment", 1), cost("mlinnotas:raw_endite", 10));
		mining(Tier.CELESTIUM, 40, cost("minecraft:nether_star", 1), cost("mlinnotas:raw_astralite", 10));

		for (SkillBranch branch : SkillBranch.values()) {
			if (branch != SkillBranch.MINING) {
				side(branch, 1, 5, cost("minecraft:copper_ingot", 16));
				side(branch, 2, 10, cost("minecraft:iron_ingot", 12));
				side(branch, 3, 15, cost("mlinnotas:cobalt_ingot", 10));
				side(branch, 4, 20, cost("minecraft:diamond", 8));
				side(branch, 5, 25, cost("minecraft:netherite_ingot", 2));
			}
		}
	}

	private SkillTree() {
	}

	private static SkillNode.Cost cost(String id, int count) {
		return new SkillNode.Cost(id, count, id);
	}

	private static SkillNode.Cost cost(String id, int count, String display) {
		return new SkillNode.Cost(id, count, display);
	}

	private static void mining(Tier tier, int xp, SkillNode.Cost... items) {
		NODES.add(new SkillNode(SkillBranch.MINING, tier.level(), List.of(items), xp, null, pickaxe(tier)));
	}

	private static void side(SkillBranch branch, int level, int xp, SkillNode.Cost... items) {
		NODES.add(new SkillNode(branch, level, List.of(items), xp, SIDE_REQUIREMENTS[level - 1], branch.icon()));
	}

	/** Picareta que representa o tier na tela. */
	public static String pickaxe(Tier tier) {
		return switch (tier) {
			case WOOD -> "minecraft:wooden_pickaxe";
			case GOLD -> "minecraft:golden_pickaxe";
			case STONE, COPPER, IRON, DIAMOND, NETHERITE -> "minecraft:" + tier.id() + "_pickaxe";
			default -> "mlinnotas:" + tier.id() + "_pickaxe";
		};
	}

	/** Todos os nos compraveis (Mineracao 2..12, depois cada ramo lateral 1..5). */
	public static List<SkillNode> nodes() {
		return Collections.unmodifiableList(NODES);
	}

	public static List<SkillNode> nodes(SkillBranch branch) {
		return NODES.stream().filter(n -> n.branch() == branch).toList();
	}

	/** No que libera {@code level} em {@code branch}, ou null (nivel inicial ou fora da faixa). */
	public static SkillNode node(SkillBranch branch, int level) {
		for (SkillNode node : NODES) {
			if (node.branch() == branch && node.level() == level) {
				return node;
			}
		}
		return null;
	}

	public static SkillNode byKey(String key) {
		for (SkillNode node : NODES) {
			if (node.key().equals(key)) {
				return node;
			}
		}
		return null;
	}

	/** Origem de um item de custo (null se desconhecido). */
	public static Source source(String costId) {
		return SOURCES.get(costId);
	}

	public static Map<String, Source> sources() {
		return Collections.unmodifiableMap(SOURCES);
	}

	/**
	 * Chave de lang da dica "onde achar" de um item de custo, estilo mapa de quests
	 * ({@code #mlinnotas:skill_cost/coal} -> {@code skill.mlinnotas.hint.mlinnotas.skill_cost.coal}).
	 */
	public static String hintKey(String costId) {
		String id = costId.startsWith("#") ? costId.substring(1) : costId;
		return "skill.mlinnotas.hint." + id.replace(':', '.').replace('/', '.');
	}

	/**
	 * Proximo objetivo do mundo: o primeiro no de Mineracao ainda nao liberado (o tronco guia a progressao); null quando
	 * a Mineracao esta completa.
	 */
	public static SkillNode nextObjective(java.util.function.ToIntFunction<SkillBranch> levels) {
		for (SkillNode node : nodes(SkillBranch.MINING)) {
			if (levels.applyAsInt(SkillBranch.MINING) < node.level()) {
				return node;
			}
		}
		return null;
	}

	/** Pre-requisitos que faltam para comprar {@code node} (vazio = pode comprar se ainda nao comprou). */
	public static List<String> missingPrerequisites(SkillNode node, java.util.function.ToIntFunction<SkillBranch> levels) {
		List<String> missing = new ArrayList<>();
		if (levels.applyAsInt(node.branch()) < node.level() - 1) {
			missing.add(SkillNode.key(node.branch(), node.level() - 1));
		}
		if (node.miningRequired() != null && levels.applyAsInt(SkillBranch.MINING) < node.miningRequired().level()) {
			missing.add(SkillNode.key(SkillBranch.MINING, node.miningRequired().level()));
		}
		return missing;
	}
}
