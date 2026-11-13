package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planta de uma construcao em coordenadas locais (logica pura): x em [0, w), z em [0, d), frente (porta) no lado
 * +z. y=0 e a camada do chao (assoalho, quintal), os pes ficam em y=1; y negativo fica abaixo do chao (poco). O
 * planejador gira a planta para a frente olhar para a praca.
 */
public final class Canvas {
	public record Cell(VPart part, Facing dir) {
	}

	private final int w;
	private final int d;
	private final Map<Long, Cell> cells = new LinkedHashMap<>();
	private final List<int[]> spawns = new ArrayList<>();
	private final List<int[]> golems = new ArrayList<>();
	private int doorX;

	public Canvas(int w, int d) {
		this.w = w;
		this.d = d;
		this.doorX = w / 2;
	}

	public static long key(int x, int y, int z) {
		return ((long) (x + 512) << 32) | ((long) (y + 512) << 16) | (z + 512);
	}

	public static int keyX(long key) {
		return (int) (key >>> 32) - 512;
	}

	public static int keyY(long key) {
		return (int) ((key >>> 16) & 0xFFFF) - 512;
	}

	public static int keyZ(long key) {
		return (int) (key & 0xFFFF) - 512;
	}

	public int w() {
		return w;
	}

	public int d() {
		return d;
	}

	public void set(int x, int y, int z, VPart part) {
		set(x, y, z, part, null);
	}

	public void set(int x, int y, int z, VPart part, Facing dir) {
		cells.put(key(x, y, z), new Cell(part, dir));
	}

	public void setIfAbsent(int x, int y, int z, VPart part) {
		cells.putIfAbsent(key(x, y, z), new Cell(part, null));
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, VPart part) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, part);
				}
			}
		}
	}

	public Cell get(int x, int y, int z) {
		return cells.get(key(x, y, z));
	}

	public VPart part(int x, int y, int z) {
		Cell cell = get(x, y, z);
		return cell == null ? null : cell.part();
	}

	public void remove(int x, int y, int z) {
		cells.remove(key(x, y, z));
	}

	public Map<Long, Cell> cells() {
		return Collections.unmodifiableMap(cells);
	}

	/** Maior y usado. */
	public int height() {
		int max = 0;
		for (long k : cells.keySet()) {
			max = Math.max(max, keyY(k));
		}
		return max;
	}

	/** Onde aparecem aldeoes quando a obra fica pronta (pes). */
	public void spawn(int x, int y, int z) {
		spawns.add(new int[] {x, y, z});
	}

	public void golem(int x, int y, int z) {
		golems.add(new int[] {x, y, z});
	}

	public List<int[]> spawns() {
		return spawns;
	}

	public List<int[]> golems() {
		return golems;
	}

	/** Coluna x da porta (o caminho sai de (doorX, d) para fora do lote). */
	public int doorX() {
		return doorX;
	}

	public void doorX(int x) {
		this.doorX = x;
	}
}
