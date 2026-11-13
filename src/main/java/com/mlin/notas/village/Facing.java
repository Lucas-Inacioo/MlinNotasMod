package com.mlin.notas.village;

/** Direcao horizontal (dx, dz) sem depender do Minecraft. Ordem horaria a partir do norte. */
public enum Facing {
	NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

	public final int dx;
	public final int dz;

	Facing(int dx, int dz) {
		this.dx = dx;
		this.dz = dz;
	}

	/** Gira 90 graus no sentido horario {@code times} vezes. */
	public Facing rotate(int times) {
		return values()[Math.floorMod(ordinal() + times, 4)];
	}

	public Facing opposite() {
		return rotate(2);
	}

	public boolean alongX() {
		return dx != 0;
	}

	/** Direcao mais parecida com o vetor (dx, dz). */
	public static Facing toward(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? EAST : WEST;
		}
		return dz >= 0 ? SOUTH : NORTH;
	}
}
