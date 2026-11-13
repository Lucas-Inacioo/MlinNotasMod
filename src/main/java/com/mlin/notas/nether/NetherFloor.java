package com.mlin.notas.nether;

/**
 * Acha o chao numa coluna do Nether (logica pura): a primeira altura, de baixo para cima a partir do mar de lava,
 * com bloco solido embaixo e ar em cima. Usado para assentar fortalezas e bastioes no terreno novo (o vanilla os
 * coloca em alturas fixas, pensadas para o Nether de cavernas).
 */
public final class NetherFloor {
	private NetherFloor() {
	}

	/**
	 * @param solid    {@code solid[i]} = bloco solido na altura {@code minY + i}
	 * @param fluid    {@code fluid[i]} = lava (ou outro fluido) na altura {@code minY + i}
	 * @param seaLevel altura do mar de lava; acima dele procura o chao seco
	 * @return y do primeiro bloco de ar sobre chao solido acima do mar, ou {@code seaLevel} se a coluna for lago de
	 *         lava ate o teto (estrutura fica boiando na lava, como no vanilla)
	 */
	public static int find(boolean[] solid, boolean[] fluid, int minY, int seaLevel) {
		int start = Math.max(1, seaLevel - minY);
		for (int i = start; i < solid.length; i++) {
			if (!solid[i] && !fluid[i] && solid[i - 1] && !fluid[i - 1]) {
				return minY + i;
			}
		}
		return seaLevel;
	}

	/**
	 * Quanto mover uma estrutura na vertical para o fundo da caixa ({@code minY}) ficar em {@code floor + floorOffset}, sem o
	 * topo ({@code maxY}) passar de {@code maxTop} (use {@link Integer#MAX_VALUE} para sem limite). Se nao couber, prefere
	 * enterrar o pe a furar o teto.
	 */
	public static int shift(int minY, int maxY, int floor, int floorOffset, int maxTop) {
		int dy = floor + floorOffset - minY;
		return maxTop == Integer.MAX_VALUE ? dy : Math.min(dy, maxTop - maxY);
	}

	/** Media inteira das alturas de chao (amostras em volta do centro de uma estrutura). */
	public static int average(int... floors) {
		if (floors.length == 0) {
			throw new IllegalArgumentException("sem amostras");
		}
		long sum = 0;
		for (int f : floors) {
			sum += f;
		}
		return (int) Math.round((double) sum / floors.length);
	}
}
