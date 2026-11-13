package com.mlin.notas.compass;

/**
 * Percorre colunas em espiral quadrada a partir do centro (anel 0, anel 1, ...), em passos de {@code step} blocos, ate
 * o raio {@code radius} (logica pura). Guarda o estado para a busca de bioma continuar no tick seguinte.
 */
public final class SpiralSearch {
	private final int centerX;
	private final int centerZ;
	private final int step;
	private final int rings;
	private int ring;
	private int index;
	private boolean started;
	private int lastX;
	private int lastZ;

	public SpiralSearch(int centerX, int centerZ, int radius, int step) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.step = Math.max(1, step);
		this.rings = Math.max(0, radius / this.step);
	}

	/** Avanca para a proxima coluna; false quando acabou o raio. Leia a coluna com {@link #x()} e {@link #z()}. */
	public boolean next() {
		if (!started) {
			started = true;
			lastX = centerX;
			lastZ = centerZ;
			ring = 1;
			index = 0;
			return true;
		}
		if (ring > rings) {
			return false;
		}
		int side = ring * 2;
		int i = index;
		int dx;
		int dz;
		if (i < side) {
			dx = -ring + i;
			dz = -ring;
		} else if (i < side * 2) {
			dx = ring;
			dz = -ring + (i - side);
		} else if (i < side * 3) {
			dx = ring - (i - side * 2);
			dz = ring;
		} else {
			dx = -ring;
			dz = ring - (i - side * 3);
		}
		lastX = centerX + dx * step;
		lastZ = centerZ + dz * step;
		index++;
		if (index >= side * 4) {
			ring++;
			index = 0;
		}
		return true;
	}

	public int x() {
		return lastX;
	}

	public int z() {
		return lastZ;
	}

	/** Anel atual (0 = centro). */
	public int ring() {
		return started ? ring : 0;
	}

	public int rings() {
		return rings;
	}
}
