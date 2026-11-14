package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Alturas do plano diretor (logica pura). Cada quarteirao comeca na mediana do proprio chao; quarteiroes vizinhos
 * ficam a no maximo {@link MasterPlan#MAX_STEP} um do outro (a rua entre eles leva a rampa) e, onde a rua e estreita
 * demais para a rampa, mais perto ainda. A praca fica na altura do quadro.
 *
 * <p>Ruas: cada celula de rua encostada num plato fica a no maximo 1 dele (a frente do lote sempre e andavel) e duas
 * celulas de rua vizinhas diferem no maximo 1 (rampa de 1 por bloco). Com essas restricoes a rua segue o chao natural
 * o mais perto possivel: limites de baixo/cima propagados pela rede de ruas e, no fim, o envelope inferior (a rua
 * corta um pouco o morro em vez de aterrar).
 */
final class PlateauGrading {
	private static final int INF = 1 << 20;

	private PlateauGrading() {
	}

	private record Limit(int a, int b, int max) {
	}

	static void grade(MasterPlan plan, int[] bed) {
		int extent = plan.extent();
		int size = 2 * extent + 1;
		int[] grid = plan.grid;
		List<MasterPlan.Block> blocks = plan.blocks();
		int plaza = blocks.size();
		int owners = plaza + 1;
		int[] target = new int[owners];
		for (MasterPlan.Block b : blocks) {
			target[b.id()] = b.target();
		}
		target[plaza] = plan.plazaY();
		// donos (quarteiroes e praca) encostados em cada celula de rua
		int[][] owner = new int[grid.length][];
		Map<Long, Integer> limits = new HashMap<>();
		for (int i = 0; i < grid.length; i++) {
			int x = i / size - extent;
			int z = i % size - extent;
			int self = ownerOf(grid[i], plaza);
			if (isGround(grid[i])) {
				int[] list = new int[0];
				for (Facing f : Facing.values()) {
					int o = ownerAt(grid, x + f.dx, z + f.dz, extent, plaza);
					if (o >= 0 && !contains(list, o)) {
						list = Arrays.copyOf(list, list.length + 1);
						list[list.length - 1] = o;
					}
				}
				owner[i] = list;
			} else if (self >= 0) {
				// contato direto entre dois platos (sem rua no meio): muro de arrimo, ate MAX_STEP
				for (Facing f : Facing.values()) {
					int o = ownerAt(grid, x + f.dx, z + f.dz, extent, plaza);
					if (o >= 0 && o != self) {
						limit(limits, self, o, o == plaza || self == plaza ? 2 : MasterPlan.MAX_STEP);
					}
				}
			}
		}
		// distancia pela rua entre as frentes de dois donos
		for (int o = 0; o < owners; o++) {
			Map<Integer, Integer> depth = new HashMap<>();
			java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
			for (int i = 0; i < grid.length; i++) {
				if (owner[i] != null && contains(owner[i], o)) {
					depth.put(i, 0);
					queue.add(i);
				}
			}
			while (!queue.isEmpty()) {
				int i = queue.poll();
				int d = depth.get(i);
				for (int other : owner[i]) {
					if (other != o) {
						// vizinhos (ate 6 pela rua): no maximo MAX_STEP; longe: a rampa de 1 por bloco ainda tem que caber
						limit(limits, o, other, d <= 6 ? Math.min(MasterPlan.MAX_STEP, d + 2) : d + 2);
					}
				}
				int x = i / size - extent;
				int z = i % size - extent;
				for (Facing f : Facing.values()) {
					int nx = x + f.dx;
					int nz = z + f.dz;
					if (Math.abs(nx) > extent || Math.abs(nz) > extent) {
						continue;
					}
					int j = (nx + extent) * size + (nz + extent);
					if (owner[j] != null && !depth.containsKey(j)) {
						depth.put(j, d + 1);
						queue.add(j);
					}
				}
			}
		}
		List<Limit> list = new ArrayList<>();
		limits.forEach((k, max) -> list.add(new Limit((int) (k >> 32), (int) (long) k, max)));
		envelope(target, plaza, list);
		for (MasterPlan.Block b : blocks) {
			b.setTarget(target[b.id()]);
		}
		streets(plan, bed, owner, target);
	}

	private static boolean isGround(int code) {
		return code >= MasterPlan.STREET && code < MasterPlan.BRIDGE;
	}

	private static int ownerOf(int code, int plaza) {
		if (code >= MasterPlan.BLOCK) {
			return code - MasterPlan.BLOCK;
		}
		return code == MasterPlan.PLAZA ? plaza : -1;
	}

	private static int ownerAt(int[] grid, int x, int z, int extent, int plaza) {
		if (Math.abs(x) > extent || Math.abs(z) > extent) {
			return -1;
		}
		return ownerOf(grid[(x + extent) * (2 * extent + 1) + (z + extent)], plaza);
	}

	private static boolean contains(int[] list, int v) {
		for (int x : list) {
			if (x == v) {
				return true;
			}
		}
		return false;
	}

	private static void limit(Map<Long, Integer> limits, int a, int b, int max) {
		long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
		limits.merge(key, max, Math::min);
	}

	/**
	 * Alturas dos platos: o mais perto possivel da mediana do chao de cada um, respeitando todos os limites entre pares
	 * e com a praca fixa. Distancias minimas pelos limites (Floyd-Warshall); cada mediana e presa ao que a praca
	 * permite; a media entre o maior envelope abaixo e o menor acima das medianas cumpre todos os limites (os dois
	 * cumprem, e a media arredondada para baixo tambem).
	 */
	static void envelope(int[] target, int fixed, List<Limit> limits) {
		int n = target.length;
		long inf = Long.MAX_VALUE / 4;
		long[][] d = new long[n][n];
		for (long[] row : d) {
			Arrays.fill(row, inf);
		}
		for (int i = 0; i < n; i++) {
			d[i][i] = 0;
		}
		for (Limit l : limits) {
			d[l.a][l.b] = Math.min(d[l.a][l.b], l.max);
			d[l.b][l.a] = Math.min(d[l.b][l.a], l.max);
		}
		for (int k = 0; k < n; k++) {
			for (int i = 0; i < n; i++) {
				if (d[i][k] == inf) {
					continue;
				}
				for (int j = 0; j < n; j++) {
					long via = d[i][k] + d[k][j];
					if (via < d[i][j]) {
						d[i][j] = via;
					}
				}
			}
		}
		long[] m = new long[n];
		for (int i = 0; i < n; i++) {
			m[i] = target[i];
			if (d[i][fixed] < inf) {
				m[i] = Math.max(target[fixed] - d[i][fixed], Math.min(target[fixed] + d[i][fixed], m[i]));
			}
		}
		m[fixed] = target[fixed];
		for (int i = 0; i < n; i++) {
			long low = Long.MAX_VALUE;
			long high = Long.MIN_VALUE;
			for (int j = 0; j < n; j++) {
				if (d[i][j] < inf) {
					low = Math.min(low, m[j] + d[i][j]);
					high = Math.max(high, m[j] - d[i][j]);
				}
			}
			target[i] = (int) Math.floorDiv(low + high, 2);
		}
		target[fixed] = (int) m[fixed];
	}

	/** Alturas das ruas: dentro de 1 dos platos encostados, 1 por bloco entre vizinhas, o mais perto do chao. */
	private static void streets(MasterPlan plan, int[] bed, int[][] owner, int[] target) {
		int extent = plan.extent();
		int size = 2 * extent + 1;
		int n = owner.length;
		int[] lo = new int[n];
		int[] hi = new int[n];
		Arrays.fill(lo, -INF);
		Arrays.fill(hi, INF);
		for (int i = 0; i < n; i++) {
			if (owner[i] == null) {
				continue;
			}
			for (int o : owner[i]) {
				lo[i] = Math.max(lo[i], target[o] - 1);
				hi[i] = Math.min(hi[i], target[o] + 1);
			}
		}
		// L: maior funcao 1-Lipschitz abaixo das restricoes de baixo; U: menor acima das de cima
		int[] low = propagate(lo, owner, extent, true);
		int[] high = propagate(hi, owner, extent, false);
		int[] h = new int[n];
		for (int i = 0; i < n; i++) {
			if (owner[i] == null) {
				continue;
			}
			int v = bed[i];
			if (low[i] > -INF / 2) {
				v = Math.max(v, low[i]);
			}
			if (high[i] < INF / 2) {
				v = Math.min(v, high[i]);
			}
			h[i] = v;
		}
		// envelope inferior: rampa de no maximo 1 por bloco
		PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
		for (int i = 0; i < n; i++) {
			if (owner[i] != null) {
				queue.add(new int[] {i, h[i]});
			}
		}
		while (!queue.isEmpty()) {
			int[] top = queue.poll();
			int i = top[0];
			if (top[1] != h[i]) {
				continue;
			}
			int x = i / size - extent;
			int z = i % size - extent;
			for (Facing f : Facing.values()) {
				int nx = x + f.dx;
				int nz = z + f.dz;
				if (Math.abs(nx) > extent || Math.abs(nz) > extent) {
					continue;
				}
				int j = (nx + extent) * size + (nz + extent);
				if (owner[j] != null && h[i] + 1 < h[j]) {
					h[j] = h[i] + 1;
					queue.add(new int[] {j, h[j]});
				}
			}
		}
		for (int i = 0; i < n; i++) {
			if (owner[i] != null) {
				plan.heights[i] = h[i];
			}
		}
	}

	/** {@code max}: maior funcao com passo 1 abaixo de {@code bound} (propaga para baixo); senao a menor acima. */
	private static int[] propagate(int[] bound, int[][] owner, int extent, boolean max) {
		int size = 2 * extent + 1;
		int n = bound.length;
		int[] out = bound.clone();
		PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> max ? Integer.compare(b[1], a[1]) : Integer.compare(a[1], b[1]));
		for (int i = 0; i < n; i++) {
			if (owner[i] != null && Math.abs(out[i]) < INF / 2) {
				queue.add(new int[] {i, out[i]});
			}
		}
		while (!queue.isEmpty()) {
			int[] top = queue.poll();
			int i = top[0];
			if (top[1] != out[i]) {
				continue;
			}
			int x = i / size - extent;
			int z = i % size - extent;
			int next = max ? out[i] - 1 : out[i] + 1;
			for (Facing f : Facing.values()) {
				int nx = x + f.dx;
				int nz = z + f.dz;
				if (Math.abs(nx) > extent || Math.abs(nz) > extent) {
					continue;
				}
				int j = (nx + extent) * size + (nz + extent);
				if (owner[j] == null) {
					continue;
				}
				if (max ? next > out[j] : next < out[j]) {
					out[j] = next;
					queue.add(new int[] {j, next});
				}
			}
		}
		return out;
	}
}
