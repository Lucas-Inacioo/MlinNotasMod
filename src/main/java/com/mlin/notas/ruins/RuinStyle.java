package com.mlin.notas.ruins;

import java.util.Set;

/**
 * Paleta da ruina conforme o bioma (a peca troca cada {@link RuinPart} pelo bloco do estilo). Logica pura:
 * a escolha usa so o id do bioma.
 */
public enum RuinStyle {
	/** Pedregulho com musgo e carvalho (padrao: planicies, florestas). */
	MOSSY,
	/** Pedregulho e abeto (taigas, neve). */
	SPRUCE,
	/** Arenito (deserto). */
	SANDSTONE,
	/** Arenito vermelho (badlands). */
	RED_SANDSTONE,
	/** Pedregulho e acacia (savana). */
	ACACIA,
	/** Tijolos de pedra musgosos e carvalho escuro (floresta escura, pantano). */
	DARK,
	/** Ardosia e abeto (montanhas, colinas varridas pelo vento). */
	DEEPSLATE;

	private static final Set<String> DARK_BIOMES = Set.of("minecraft:dark_forest", "minecraft:pale_garden", "minecraft:swamp", "minecraft:mangrove_swamp");
	private static final Set<String> MOUNTAINS = Set.of("minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest",
			"minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:snowy_slopes", "minecraft:grove", "minecraft:meadow",
			"minecraft:cherry_grove", "minecraft:stony_shore");

	public static RuinStyle forBiome(String biomeId) {
		if (biomeId == null) {
			return MOSSY;
		}
		if (DARK_BIOMES.contains(biomeId)) {
			return DARK;
		}
		if (MOUNTAINS.contains(biomeId)) {
			return DEEPSLATE;
		}
		if (biomeId.contains("badlands")) {
			return RED_SANDSTONE;
		}
		if (biomeId.contains("desert")) {
			return SANDSTONE;
		}
		if (biomeId.contains("savanna")) {
			return ACACIA;
		}
		if (biomeId.contains("taiga") || biomeId.contains("snowy")) {
			return SPRUCE;
		}
		return MOSSY;
	}

	public static RuinStyle byOrdinal(int ordinal) {
		RuinStyle[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MOSSY;
	}
}
