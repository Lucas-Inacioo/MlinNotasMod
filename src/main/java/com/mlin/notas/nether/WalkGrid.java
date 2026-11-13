package com.mlin.notas.nether;

import java.util.Arrays;

/**
 * Grade de "onde da para ficar de pe" de uma area do mundo, sem nada do Minecraft: para cada coluna (x, z) guarda
 * as alturas em que um jogador cabe em pe (bloco solido embaixo, pelo menos 2 de espaco livre e sem lava), cada uma
 * com o espaco livre acima (limitado a {@link #MAX_CLEARANCE}).
 *
 * <p>Usada por {@link WalkPath} para medir se o Nether da para atravessar a pe: subir no maximo 1 bloco por passo
 * (pulo, precisa de 3 de espaco livre na origem) e descer no maximo {@link WalkPath#MAX_DROP}.</p>
 */
public final class WalkGrid {
	/** Espaco livre maximo guardado: 3 basta para pular, 2 + queda para descer de um degrau alto (queda de 3 pede 5). */
	public static final int MAX_CLEARANCE = 5;

	private final int width;
	private final int depth;
	private final int[][] levels;
	private final byte[][] clearances;
	private static final int[] EMPTY = new int[0];
	private static final byte[] EMPTY_BYTES = new byte[0];

	public WalkGrid(int width, int depth) {
		if (width <= 0 || depth <= 0) {
			throw new IllegalArgumentException("grade vazia");
		}
		this.width = width;
		this.depth = depth;
		this.levels = new int[width * depth][];
		this.clearances = new byte[width * depth][];
		Arrays.fill(levels, EMPTY);
		Arrays.fill(clearances, EMPTY_BYTES);
	}

	public int width() {
		return width;
	}

	public int depth() {
		return depth;
	}

	private int index(int x, int z) {
		if (x < 0 || z < 0 || x >= width || z >= depth) {
			throw new IndexOutOfBoundsException("(" + x + ", " + z + ") fora de " + width + "x" + depth);
		}
		return x + z * width;
	}

	/**
	 * Monta a coluna a partir dos blocos de baixo para cima.
	 *
	 * @param solid  {@code solid[i]} = o bloco na altura {@code minY + i} segura o jogador (tem colisao)
	 * @param hazard {@code hazard[i]} = o bloco na altura {@code minY + i} nao deixa ficar ali (lava, fogo)
	 */
	public void setColumn(int x, int z, int minY, boolean[] solid, boolean[] hazard) {
		int[] found = new int[solid.length];
		byte[] clear = new byte[solid.length];
		int count = 0;
		for (int i = 0; i + 2 < solid.length; i++) {
			int feet = i + 1;
			if (!solid[i] || hazard[i] || solid[feet] || hazard[feet] || solid[feet + 1] || hazard[feet + 1]) {
				continue;
			}
			int space = 2;
			while (space < MAX_CLEARANCE && feet + space < solid.length && !solid[feet + space] && !hazard[feet + space]) {
				space++;
			}
			// acima do topo da amostra conta como livre
			if (feet + space >= solid.length) {
				space = MAX_CLEARANCE;
			}
			found[count] = minY + feet;
			clear[count] = (byte) space;
			count++;
		}
		int idx = index(x, z);
		levels[idx] = Arrays.copyOf(found, count);
		clearances[idx] = Arrays.copyOf(clear, count);
	}

	/** Define as alturas em pe de uma coluna diretamente (todas com o espaco livre maximo). */
	public void setLevels(int x, int z, int... feetHeights) {
		int[] sorted = feetHeights.clone();
		Arrays.sort(sorted);
		byte[] clear = new byte[sorted.length];
		Arrays.fill(clear, (byte) MAX_CLEARANCE);
		int idx = index(x, z);
		levels[idx] = sorted;
		clearances[idx] = clear;
	}

	/** Alturas dos pes (y do bloco de ar onde o jogador fica) nesta coluna, de baixo para cima. */
	public int[] levels(int x, int z) {
		return levels[index(x, z)];
	}

	public int clearance(int x, int z, int levelIndex) {
		return clearances[index(x, z)][levelIndex];
	}

	/** Total de posicoes em pe na grade. */
	public int nodeCount() {
		int total = 0;
		for (int[] column : levels) {
			total += column.length;
		}
		return total;
	}

	/** Posicao mais baixa em pe da coluna (o "chao"), ou {@link Integer#MIN_VALUE} se nao houver. */
	public int floor(int x, int z) {
		int[] column = levels(x, z);
		return column.length == 0 ? Integer.MIN_VALUE : column[0];
	}
}
