package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * Terraplenagem de uma coluna (logica pura): o chao vai para a altura planejada. O que sobra acima e cortado; o que
 * falta e aterrado com terra (pedra no fundo) e, se um vizinho fica 2 ou mais abaixo, a coluna vira muro de arrimo;
 * agua rasa e aterrada ou drenada; no topo grama (ou caminho, na rua). A ponte com o jogo le a coluna do mundo na
 * hora (jogador que cavou ou aterrou no meio nao estraga a conta) e so troca blocos naturais.
 */
public final class TerrainWork {
	/** Profundidade de terra no aterro (abaixo disso, pedra). */
	public static final int SOIL_DEPTH = 3;

	private TerrainWork() {
	}

	/**
	 * Blocos da coluna (x, z). {@code ground}: topo atual (agua inclusive); {@code water}: blocos de agua em cima do
	 * fundo; {@code target}: altura planejada; {@code street}: topo de caminho; {@code neighbour}: altura final de uma
	 * coluna vizinha (planejada, ou natural fora do plano); {@code sapling}: muda nos arredores.
	 */
	public static List<Placement> column(int x, int z, int ground, int water, int target, boolean street, IntBinaryOperator neighbour,
			boolean sapling) {
		List<Placement> out = new ArrayList<>();
		int bed = ground - water;
		VPart top = street ? VPart.PATH : VPart.TURF;
		// tira tudo acima do alvo (de cima para baixo): terra cortada e agua drenada
		for (int y = ground + 1; y > target; y--) {
			out.add(new Placement(x, y, z, y <= ground && y > bed ? VPart.DRAIN : VPart.AIR, null, false));
		}
		// vizinho 2+ abaixo: a face que fica a vista vira muro de arrimo (no aterro e tambem no corte)
		int low = Integer.MAX_VALUE;
		for (Facing f : Facing.values()) {
			low = Math.min(low, neighbour.applyAsInt(x + f.dx, z + f.dz));
		}
		int wallFrom = low <= target - 2 ? Math.max(low + 1, target - 12) : Integer.MAX_VALUE;
		for (int y = bed + 1; y < target; y++) {
			VPart part = y >= wallFrom ? VPart.RETAIN : target - y <= SOIL_DEPTH ? VPart.EARTH : VPart.ROCK;
			out.add(new Placement(x, y, z, part, null, false));
		}
		for (int y = wallFrom; y < target && y <= bed; y++) {
			out.add(new Placement(x, y, z, VPart.RETAIN, null, false));
		}
		if (bed != target || water > 0 || street) {
			out.add(new Placement(x, target, z, top, null, false));
		}
		if (sapling && !street) {
			out.add(new Placement(x, target + 1, z, VPart.SAPLING, null, false));
		}
		return out;
	}

	/** Muda nos arredores: 1 a cada ~12 colunas de quarteirao fora dos lotes (quintais e cantos), sorteada pela posicao. */
	public static boolean sapling(MasterPlan plan, int x, int z) {
		int ring = plan.ring(x, z);
		if (ring < 5 || plan.street(x, z) || plan.parcelAt(x, z) != null || plan.blockAt(x, z) == null) {
			return false;
		}
		long h = (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ plan.seed();
		h ^= h >>> 31;
		return Math.floorMod(h, 12) == 0;
	}
}
