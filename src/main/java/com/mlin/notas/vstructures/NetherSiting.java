package com.mlin.notas.vstructures;

import java.util.Arrays;

/**
 * Altura das estruturas do Nether refeitas no terreno novo (logica pura). O plano tem o chao em y=0 (pes) e a
 * estrutura e posta com o y=0 na mediana do chao amostrado: o que fica abaixo vira pilar/alicerce (desce ate o chao
 * ou o mar de lava) e o que fica acima e cortado pelo ar do plano (tunel). Nunca abaixo do mar de lava nem com o topo
 * dentro do teto de bedrock.
 */
public final class NetherSiting {
	/** Ultima altura livre embaixo do teto de bedrock do Nether. */
	public static final int CEILING = 120;

	private NetherSiting() {
	}

	/** Pontos (x, z do plano) para amostrar o chao: grade 5x5 sobre a area da estrutura. */
	public static int[][] samplePoints(int[] area) {
		int[][] out = new int[25][];
		int i = 0;
		for (int a = 0; a < 5; a++) {
			for (int c = 0; c < 5; c++) {
				out[i++] = new int[] {area[0] + (area[2] - area[0]) * a / 4, area[1] + (area[3] - area[1]) * c / 4};
			}
		}
		return out;
	}

	/**
	 * y do mundo para o y=0 do plano.
	 *
	 * @param floors   chao (primeiro ar sobre rocha) em cada ponto amostrado
	 * @param planMinY menor y do plano
	 * @param planMaxY maior y do plano
	 * @param seaLevel mar de lava
	 */
	public static int originY(VKind kind, int[] floors, int planMinY, int planMaxY, int seaLevel) {
		int[] sorted = floors.clone();
		Arrays.sort(sorted);
		// fortaleza: um pouco abaixo da mediana (as pontes ja ficam 8 acima do y=0); bastiao: mediana
		int pick = kind == VKind.NETHER_FORTRESS ? sorted[(sorted.length - 1) * 2 / 5] : sorted[(sorted.length - 1) / 2];
		int y = Math.max(pick, seaLevel + 1);
		return Math.min(y, CEILING - planMaxY);
	}
}
