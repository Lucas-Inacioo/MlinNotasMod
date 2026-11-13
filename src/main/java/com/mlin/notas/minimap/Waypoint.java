package com.mlin.notas.minimap;

/**
 * Ponto marcado no mapa. Logica pura (sem Minecraft).
 *
 * @param name      nome mostrado
 * @param x         bloco X
 * @param y         bloco Y
 * @param z         bloco Z
 * @param color     cor ARGB (alpha ignorado)
 * @param dimension id da dimensao (ex.: "minecraft:overworld")
 */
public record Waypoint(String name, int x, int y, int z, int color, String dimension) {
	/** Paleta padrao oferecida ao jogador (ARGB opaco). */
	public static final int[] PALETTE = {
			0xFFFFD83A, 0xFFFF5555, 0xFF55FF55, 0xFF5599FF, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFFFFF, 0xFFFF9933
	};

	public Waypoint {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("nome do waypoint nao pode ser vazio");
		}
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("dimensao do waypoint nao pode ser vazia");
		}
		name = name.strip();
	}

	public Waypoint withName(String newName) {
		return new Waypoint(newName, x, y, z, color, dimension);
	}

	public Waypoint withColor(int newColor) {
		return new Waypoint(name, x, y, z, newColor, dimension);
	}

	public Waypoint withPosition(int nx, int ny, int nz) {
		return new Waypoint(name, nx, ny, nz, color, dimension);
	}

	/** Distancia horizontal (em blocos) ate um ponto. */
	public double horizontalDistance(double px, double pz) {
		double dx = x + 0.5 - px;
		double dz = z + 0.5 - pz;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Proxima cor da paleta (cicla). */
	public static int nextColor(int current) {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == current) {
				return PALETTE[(i + 1) % PALETTE.length];
			}
		}
		return PALETTE[0];
	}
}
