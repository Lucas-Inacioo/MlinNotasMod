package com.mlin.notas.waystone;

/**
 * Uma pedra de teleporte ativada: dimensao, posicao da metade de baixo e nome. Logica pura.
 */
public record WaystoneInfo(String dimension, int x, int y, int z, String name) {
	public static final int MAX_NAME = 32;

	public WaystoneInfo {
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("dimensao vazia");
		}
		String clean = cleanName(name);
		if (clean == null) {
			throw new IllegalArgumentException("nome vazio");
		}
		name = clean;
	}

	/** Chave unica (dimensao + posicao). */
	public String key() {
		return key(dimension, x, y, z);
	}

	public static String key(String dimension, int x, int y, int z) {
		return dimension + "@" + x + "," + y + "," + z;
	}

	public boolean at(String dimension, int x, int y, int z) {
		return this.dimension.equals(dimension) && this.x == x && this.y == y && this.z == z;
	}

	public WaystoneInfo withName(String newName) {
		return new WaystoneInfo(dimension, x, y, z, newName);
	}

	/** Distancia horizontal ate outra pedra (so faz sentido na mesma dimensao). */
	public double horizontalDistance(WaystoneInfo other) {
		double dx = other.x - x;
		double dz = other.z - z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Nome sem espacos sobrando e no maximo {@link #MAX_NAME} caracteres; null se ficar vazio. */
	public static String cleanName(String name) {
		if (name == null) {
			return null;
		}
		String clean = name.strip().replaceAll("\\s+", " ");
		if (clean.isEmpty()) {
			return null;
		}
		return clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME).strip() : clean;
	}
}
