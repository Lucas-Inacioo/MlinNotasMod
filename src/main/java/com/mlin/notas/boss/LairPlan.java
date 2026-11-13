package com.mlin.notas.boss;

import com.mlin.notas.util.Pos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Plano de um covil de chefe: papel de cada bloco relativo a origem (y=0 = chao) e marcadores nomeados. Logica pura;
 * feito por {@link LairPlanner}, construido no mundo por {@code LairPiece}.
 */
public final class LairPlan {
	public static final String MARKER_ALTAR = "altar";
	public static final String MARKER_BOSS = "boss";
	public static final String MARKER_CHEST = "chest";
	public static final String MARKER_ENTRANCE = "entrance";

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private final LairKind kind;
	private final Map<Long, LairPart> blocks = new LinkedHashMap<>();
	private final Map<String, Pos> markers = new LinkedHashMap<>();

	LairPlan(LairKind kind) {
		this.kind = kind;
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

	void set(int x, int y, int z, LairPart part) {
		blocks.put(key(x, y, z), part);
	}

	void mark(String name, int x, int y, int z) {
		markers.put(name, new Pos(x, y, z));
	}

	public LairKind kind() {
		return kind;
	}

	public LairPart get(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	public Map<Long, LairPart> blocks() {
		return blocks;
	}

	public Map<String, Pos> markers() {
		return markers;
	}

	public Pos marker(String name) {
		Pos pos = markers.get(name);
		if (pos == null) {
			throw new IllegalArgumentException("sem marcador " + name);
		}
		return pos;
	}

	public long count(LairPart part) {
		return blocks.values().stream().filter(p -> p == part).count();
	}

	/** {minX, minY, minZ, maxX, maxY, maxZ}. */
	public int[] bounds() {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (long k : blocks.keySet()) {
			b[0] = Math.min(b[0], keyX(k));
			b[1] = Math.min(b[1], keyY(k));
			b[2] = Math.min(b[2], keyZ(k));
			b[3] = Math.max(b[3], keyX(k));
			b[4] = Math.max(b[4], keyY(k));
			b[5] = Math.max(b[5], keyZ(k));
		}
		return b;
	}

	/** Ocupado. Fora do plano: rocha se o covil e cavado; senao chao abaixo de y=0 e ar acima. */
	public boolean solid(int x, int y, int z) {
		LairPart part = get(x, y, z);
		if (part != null) {
			return part.obstacle();
		}
		return kind.enclosed() || y < 0;
	}

	private boolean supportsStanding(int x, int y, int z) {
		LairPart part = get(x, y, z);
		if (part != null) {
			return part.standable();
		}
		return kind.enclosed() || y < 0;
	}

	/** Posicao dos pes valida: 2 blocos livres e chao firme embaixo. */
	public boolean validFeet(int x, int y, int z) {
		return !solid(x, y, z) && !solid(x, y + 1, z) && supportsStanding(x, y - 1, z);
	}

	/** Cabe um corpo de {@code w x h} blocos com os pes em (x, y, z) (centro), sem nada solido. */
	public boolean roomFor(int x, int y, int z, int halfWidth, int height) {
		for (int dx = -halfWidth; dx <= halfWidth; dx++) {
			for (int dz = -halfWidth; dz <= halfWidth; dz++) {
				for (int dy = 0; dy < height; dy++) {
					if (solid(x + dx, y + dy, z + dz)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Posicoes dos pes alcancaveis a pe a partir de (sx, sy, sz): anda, sobe 1 pulando e cai ate 3. Nao constroi. */
	public Set<Long> reachableFrom(int sx, int sy, int sz) {
		int[] b = bounds();
		int minX = b[0] - 3;
		int maxX = b[3] + 3;
		int minZ = b[2] - 3;
		int maxZ = b[5] + 3;
		Set<Long> seen = new HashSet<>();
		Deque<int[]> queue = new ArrayDeque<>();
		if (validFeet(sx, sy, sz)) {
			seen.add(key(sx, sy, sz));
			queue.add(new int[] {sx, sy, sz});
		}
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			for (int[] d : DIRS) {
				int nx = p[0] + d[0];
				int nz = p[2] + d[1];
				int y = p[1];
				if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
					continue;
				}
				if (!solid(nx, y, nz) && !solid(nx, y + 1, nz)) {
					for (int k = 0; k <= 3; k++) {
						if (k > 0 && solid(nx, y - k, nz)) {
							break;
						}
						if (validFeet(nx, y - k, nz)) {
							visit(seen, queue, nx, y - k, nz);
							break;
						}
					}
				} else if (!solid(p[0], y + 2, p[2]) && validFeet(nx, y + 1, nz)) {
					visit(seen, queue, nx, y + 1, nz);
				}
			}
		}
		return seen;
	}

	private static void visit(Set<Long> seen, Deque<int[]> queue, int x, int y, int z) {
		if (seen.add(key(x, y, z))) {
			queue.add(new int[] {x, y, z});
		}
	}

	/** Da para usar o bloco (abrir o bau, clicar no altar) de alguma posicao alcancavel ao lado ou em cima. */
	public static boolean usableFrom(Set<Long> reach, int x, int y, int z) {
		for (int[] d : DIRS) {
			for (int dy = -1; dy <= 1; dy++) {
				if (reach.contains(key(x + d[0], y + dy, z + d[1]))) {
					return true;
				}
			}
		}
		return reach.contains(key(x, y + 1, z));
	}
}
