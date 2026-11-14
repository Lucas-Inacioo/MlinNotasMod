package com.mlin.notas.waystone;

import java.util.Set;

/**
 * Custo da viagem entre pedras de teleporte, em niveis de experiencia: 1 nivel a cada 500 blocos (minimo 1) na mesma
 * dimensao, 3 niveis entre dimensoes, gratis no criativo. Viajar entre duas pedras de vilas (Pedra de teleporte da vila,
 * Vilas 2.0 fase 7) custa metade, arredondado para cima (minimo 1). Logica pura.
 */
public final class WaystoneCost {
	public static final int BLOCKS_PER_LEVEL = 500;
	public static final int CROSS_DIMENSION = 3;

	/** Chaves das pedras que pertencem a vilas (servidor e cliente mantem a lista igual). */
	private static volatile Set<String> villageKeys = Set.of();

	private WaystoneCost() {
	}

	public static void setVillageKeys(Set<String> keys) {
		villageKeys = Set.copyOf(keys);
	}

	public static Set<String> villageKeys() {
		return villageKeys;
	}

	public static int levels(WaystoneInfo from, WaystoneInfo to) {
		int base = baseLevels(from, to);
		if (villageKeys.contains(from.key()) && villageKeys.contains(to.key())) {
			return com.mlin.notas.village.civic.CivicRules.halfCost(base);
		}
		return base;
	}

	private static int baseLevels(WaystoneInfo from, WaystoneInfo to) {
		if (!from.dimension().equals(to.dimension())) {
			return CROSS_DIMENSION;
		}
		return levelsForDistance(from.horizontalDistance(to));
	}

	public static int levelsForDistance(double blocks) {
		return Math.max(1, (int) Math.ceil(blocks / BLOCKS_PER_LEVEL));
	}

	/** Custo efetivo (0 no criativo). */
	public static int charged(WaystoneInfo from, WaystoneInfo to, boolean creative) {
		return creative ? 0 : levels(from, to);
	}

	public static boolean canAfford(int playerLevels, int cost) {
		return playerLevels >= cost;
	}
}
