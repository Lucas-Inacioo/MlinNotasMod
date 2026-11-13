package com.mlin.notas.nether;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Forma das arvores do Bosque Ardente (logica pura, coordenadas relativas a origem = primeiro bloco de ar acima do
 * chao). Fungo gigante de caule alto e copa larga e baixa: um guarda-chuva no topo cuja borda cai em cortina, um
 * segundo andar menor no meio do caule (arvores altas) e cipos pendurados por baixo. A copa mais baixa fica sempre a
 * pelo menos {@link #MIN_CANOPY_CLEARANCE} blocos do chao, entao da para andar por baixo.
 */
public final class BurningTreeShape {
	/** Altura minima da copa (y relativo) para passar por baixo. */
	public static final int MIN_CANOPY_CLEARANCE = 3;

	public enum Part {
		STEM, CAP, LIGHT, VINE
	}

	public record Cell(int x, int y, int z, Part part) {
	}

	private BurningTreeShape() {
	}

	/** Arvore com caule de {@code height} blocos (>= 3; abaixo de 7 vira arbusto: so uma bola de copa). */
	public static List<Cell> tree(int height, Random random) {
		if (height < 3) {
			throw new IllegalArgumentException("altura minima 3: " + height);
		}
		Map<Long, Cell> cells = new LinkedHashMap<>();
		for (int y = 0; y < height; y++) {
			put(cells, 0, y, 0, Part.STEM);
		}
		if (height < 7) {
			int r = height <= 3 ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dy = -1; dy <= 1; dy++) {
						double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 2.0);
						if (d <= r + 0.4 && height - 1 + dy >= MIN_CANOPY_CLEARANCE - 1) {
							putIfFree(cells, dx, height - 1 + dy, dz, random.nextInt(9) == 0 ? Part.LIGHT : Part.CAP);
						}
					}
				}
			}
			return new ArrayList<>(cells.values());
		}
		// raizes: tocos em volta da base
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			if (random.nextInt(3) > 0) {
				put(cells, d[0], 0, d[1], Part.STEM);
			}
		}
		int radius = 3 + height / 5;
		umbrella(cells, height, radius, random);
		if (height >= 13) {
			umbrella(cells, height * 3 / 5, Math.max(2, radius - 2), random);
		}
		// o caule atravessa a copa e termina num chapeu
		put(cells, 0, height, 0, Part.CAP);
		return new ArrayList<>(cells.values());
	}

	/** Guarda-chuva: disco no topo, anel na borda um bloco abaixo e cortina caindo 1-3 blocos, cipos por baixo. */
	private static void umbrella(Map<Long, Cell> cells, int top, int radius, Random random) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius + 0.3) {
					continue;
				}
				boolean rim = d > radius - 1.2;
				int y = rim ? top - 1 : top;
				putIfFree(cells, dx, y, dz, random.nextInt(11) == 0 ? Part.LIGHT : Part.CAP);
				int lowest = y;
				if (!rim && d > radius - 2.5) {
					putIfFree(cells, dx, top - 1, dz, Part.CAP);
					lowest = top - 1;
				}
				if (rim) {
					int curtain = random.nextInt(3);
					for (int c = 1; c <= curtain && top - 1 - c >= MIN_CANOPY_CLEARANCE; c++) {
						putIfFree(cells, dx, top - 1 - c, dz, Part.CAP);
					}
				} else if (random.nextInt(4) == 0 && (dx != 0 || dz != 0)) {
					// cipo pendurado sob o miolo da copa
					int length = 2 + random.nextInt(5);
					for (int c = 1; c <= length && lowest - c >= 1; c++) {
						putIfFree(cells, dx, lowest - c, dz, Part.VINE);
					}
				}
			}
		}
	}

	private static long key(int x, int y, int z) {
		return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
	}

	private static void put(Map<Long, Cell> cells, int x, int y, int z, Part part) {
		cells.put(key(x, y, z), new Cell(x, y, z, part));
	}

	private static void putIfFree(Map<Long, Cell> cells, int x, int y, int z, Part part) {
		cells.putIfAbsent(key(x, y, z), new Cell(x, y, z, part));
	}
}
