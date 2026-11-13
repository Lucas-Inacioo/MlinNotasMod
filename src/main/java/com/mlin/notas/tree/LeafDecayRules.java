package com.mlin.notas.tree;

/**
 * Regras puras da queda rapida de folhas: folha natural (nao persistente) que ficou a 7 blocos de qualquer tronco cai
 * em {@link #MIN_DELAY}..{@link #MAX_DELAY} ticks (1 a 3,5 s, sorteado por folha para a copa cair aos poucos) em vez
 * de esperar o random tick vanilla (minutos).
 */
public final class LeafDecayRules {
	/** Distancia vanilla em que a folha esta solta ({@code LeavesBlock.DECAY_DISTANCE}). */
	public static final int DECAY_DISTANCE = 7;
	public static final int MIN_DELAY = 20;
	public static final int MAX_DELAY = 70;

	private LeafDecayRules() {
	}

	/** Folha solta: natural e sem tronco a menos de 7 blocos. Folha colocada por jogador (persistente) nunca cai. */
	public static boolean orphaned(int distance, boolean persistent) {
		return !persistent && distance >= DECAY_DISTANCE;
	}

	/**
	 * Distancia recalculada a partir das distancias dos 6 vizinhos (tronco = 0, bloco sem distancia = 7), igual ao
	 * {@code LeavesBlock.updateDistance} vanilla.
	 */
	public static int distanceFromNeighbors(int... neighborDistances) {
		int best = DECAY_DISTANCE;
		for (int d : neighborDistances) {
			best = Math.min(best, d + 1);
		}
		return best;
	}

	/** Atraso em ticks para um sorteio {@code roll} qualquer (fica entre MIN_DELAY e MAX_DELAY). */
	public static int delay(int roll) {
		return MIN_DELAY + Math.floorMod(roll, MAX_DELAY - MIN_DELAY + 1);
	}
}
