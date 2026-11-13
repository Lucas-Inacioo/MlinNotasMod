package com.mlin.notas.util;

/**
 * Posicao inteira 3D sem dependencia do Minecraft, usada pela logica pura das features.
 */
public record Pos(int x, int y, int z) {
	public Pos offset(int dx, int dy, int dz) {
		return new Pos(x + dx, y + dy, z + dz);
	}

	public Pos plus(Pos other) {
		return new Pos(x + other.x, y + other.y, z + other.z);
	}

	public int distanceChebyshev(Pos other) {
		return Math.max(Math.abs(x - other.x), Math.max(Math.abs(y - other.y), Math.abs(z - other.z)));
	}
}
