package com.mlin.notas.waila;

import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.Tier;

import java.util.List;
import java.util.function.Predicate;

/**
 * Regras do HUD estilo WAILA (logica pura): picareta minima pelo conjunto de tags do bloco, tipo de ferramenta,
 * porcentagem de crescimento e alcance da consulta de conteudo.
 */
public final class WailaRules {
	/** Distancia maxima (blocos) para o servidor revelar o conteudo de um container. */
	public static final double MAX_DISTANCE = 8.0;
	/** Intervalo (ticks) entre pedidos de conteudo enquanto o jogador mira o container. */
	public static final int REQUEST_INTERVAL = 10;

	/** Tag -> nivel exigido, do mais exigente para o menos (o primeiro que o bloco tiver vale). */
	private static final List<Requirement> REQUIREMENTS = List.of(
			new Requirement("mlinnotas:needs_astralite_tool", Tier.ASTRALITE),
			new Requirement("mlinnotas:needs_endite_tool", Tier.ENDITE),
			new Requirement("mlinnotas:needs_infernite_tool", Tier.INFERNITE),
			new Requirement("mlinnotas:needs_netherite_tool", Tier.NETHERITE),
			new Requirement("minecraft:needs_diamond_tool", Tier.DIAMOND),
			new Requirement("mlinnotas:needs_cobalt_tool", Tier.COBALT),
			new Requirement("minecraft:needs_iron_tool", Tier.IRON),
			new Requirement("minecraft:needs_stone_tool", Tier.STONE));

	private record Requirement(String tag, Tier tier) {
	}

	private WailaRules() {
	}

	/** Todas as tags consultadas por {@link #requiredTier}. */
	public static List<String> requirementTags() {
		return REQUIREMENTS.stream().map(Requirement::tag).toList();
	}

	/** Nivel minimo da ferramenta para um bloco que exige ferramenta certa (madeira se nenhuma tag de nivel). */
	public static Tier requiredTier(Predicate<String> hasTag) {
		for (Requirement requirement : REQUIREMENTS) {
			if (hasTag.test(requirement.tag())) {
				return requirement.tier();
			}
		}
		return Tier.WOOD;
	}

	/** Ferramenta que minera o bloco pelas tags {@code minecraft:mineable/*}, ou null. */
	public static GearKind toolKind(Predicate<String> hasTag) {
		if (hasTag.test("minecraft:mineable/pickaxe")) {
			return GearKind.PICKAXE;
		}
		if (hasTag.test("minecraft:mineable/axe")) {
			return GearKind.AXE;
		}
		if (hasTag.test("minecraft:mineable/shovel")) {
			return GearKind.SHOVEL;
		}
		if (hasTag.test("minecraft:mineable/hoe")) {
			return GearKind.HOE;
		}
		return null;
	}

	/** Crescimento 0..100 a partir da idade. */
	public static int growthPercent(int age, int maxAge) {
		if (maxAge <= 0) {
			return 100;
		}
		int clamped = Math.max(0, Math.min(age, maxAge));
		return clamped * 100 / maxAge;
	}

	/** O jogador esta perto o bastante (distancia ao quadrado do olho ate o centro do bloco). */
	public static boolean withinReach(double distanceSquared) {
		return distanceSquared <= MAX_DISTANCE * MAX_DISTANCE;
	}

	/** Coracoes cheios e meio coracao para uma vida (1 coracao = 2 pontos), limitado a {@code maxHearts}. */
	public static int[] hearts(float health, int maxHearts) {
		int points = Math.max(0, (int) Math.ceil(health));
		int full = Math.min(points / 2, maxHearts);
		int half = full < maxHearts && points % 2 == 1 ? 1 : 0;
		return new int[] {full, half};
	}
}
