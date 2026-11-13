package com.mlin.notas.waystone;

/**
 * Custo da viagem entre pedras de teleporte, em niveis de experiencia: 1 nivel a cada 500 blocos (minimo 1) na mesma
 * dimensao, 3 niveis entre dimensoes, gratis no criativo. Logica pura.
 */
public final class WaystoneCost {
	public static final int BLOCKS_PER_LEVEL = 500;
	public static final int CROSS_DIMENSION = 3;

	private WaystoneCost() {
	}

	public static int levels(WaystoneInfo from, WaystoneInfo to) {
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
