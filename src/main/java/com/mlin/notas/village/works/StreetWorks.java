package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.StreetPlanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Ruas por nivel (logica pura). A avenida sobe de material a cada nivel: terra batida, cascalho com meio-fio,
 * pedregulho, tijolo de pedra com sarjeta, canteiro central com arvores e calcamento desenhado com poste duplo; aneis e
 * travessas ficam um passo atras. Tudo plano (troca so o bloco do chao, sem degrau novo) para a rampa do plano diretor
 * continuar valendo; o canteiro e os postes ficam so no eixo da avenida, longe de cruzamentos e da praca.
 *
 * <p>Referencias de forma: boulevards com canteiro central e sarjeta de paralelepipedo (ruas de cidades europeias),
 * construcoes de "medieval street" do Planet Minecraft; escrito do zero.
 */
public final class StreetWorks {
	public static final int MEDIAN_SPACING = 6;

	private StreetWorks() {
	}

	/** Estagio do material (0 = terra) da rua de tipo {@code kind} quando a avenida esta no estagio {@code stage}. */
	public static int stageOf(int kind, int stage) {
		return kind == StreetPlanner.AVENUE ? stage : Math.max(0, stage - 1);
	}

	/** Distancia (4 vizinhos) da celula ate a celula que nao e rua nem praca. */
	static int[] edgeDistance(MasterPlan plan) {
		int e = plan.extent();
		int size = 2 * e + 1;
		int[] dist = new int[size * size];
		java.util.Arrays.fill(dist, Integer.MAX_VALUE);
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				boolean street = plan.street(x, z) || plan.plaza(x, z);
				if (!street) {
					dist[(x + e) * size + (z + e)] = 0;
					queue.add(new int[] {x, z});
				}
			}
		}
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			int d = dist[(c[0] + e) * size + (c[1] + e)];
			for (int[] dir : dirs) {
				int nx = c[0] + dir[0];
				int nz = c[1] + dir[1];
				if (Math.abs(nx) > e || Math.abs(nz) > e) {
					continue;
				}
				int i = (nx + e) * size + (nz + e);
				if (dist[i] == Integer.MAX_VALUE) {
					dist[i] = d + 1;
					queue.add(new int[] {nx, nz});
				}
			}
		}
		return dist;
	}

	/** Bloco do chao da rua para o estagio (0 = deixa como esta) na celula, pela distancia da borda. */
	static String surface(Mats m, int stage, int d, int x, int z, boolean avenue) {
		return switch (stage) {
			case 0 -> null;
			case 1 -> d <= 1 ? m.cobble() : m.gravel();
			case 2 -> d <= 1 ? m.bricks() : Math.floorMod(x * 7 + z * 13, 11) == 0 ? "minecraft:andesite" : m.cobble();
			case 3, 4 -> d <= 1 ? m.bricks() : d == 2 && avenue ? m.smooth() : Math.floorMod(x * 5 + z * 3, 13) == 0 ? "minecraft:cracked_stone_bricks"
					: m.bricks();
			default -> d <= 1 ? m.smooth() : d == 2 && avenue ? m.bricks() : Math.floorMod(x + z, 2) == 0 ? m.bricks() : m.smooth();
		};
	}

	/**
	 * Obra "ruas N": troca o chao das ruas de chao dos aneis ja aplainados para o material do estagio e, nas avenidas
	 * do estagio 4+, faz o canteiro central (arvore ou poste duplo de lanterna).
	 */
	public static Blueprint plan(WorkContext ctx, int stage) {
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		Blueprint bp = new Blueprint();
		int e = plan.extent();
		int size = 2 * e + 1;
		int[] dist = edgeDistance(plan);
		List<int[]> median = new ArrayList<>();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (!plan.ground(x, z) || !ctx.ready(x, z)) {
					continue;
				}
				int kind = plan.streetKind(x, z);
				int s = stageOf(kind, stage);
				int d = dist[(x + e) * size + (z + e)];
				int h = plan.streetHeight(x, z);
				boolean avenue = kind == StreetPlanner.AVENUE;
				String block = surface(m, s, d, x, z, avenue);
				if (block == null) {
					continue;
				}
				bp.needRing(plan.ring(x, z));
				if (avenue && stage >= 4 && d == 3 && medianCandidate(plan, x, z)) {
					median.add(new int[] {x, z});
					continue;
				}
				bp.surface(x, h, z, block);
			}
		}
		if (!median.isEmpty()) {
			median(ctx, bp, median, stage);
		}
		return bp;
	}

	/** Eixo da avenida: sem rua de outro tipo, nem ponte, nem praca, por perto. */
	static boolean medianCandidate(MasterPlan plan, int x, int z) {
		if (x * x + z * z < 10 * 10) {
			return false;
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int kind = plan.streetKind(x + dx, z + dz);
				if (plan.street(x + dx, z + dz) && (kind != StreetPlanner.AVENUE || !plan.ground(x + dx, z + dz))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Canteiro (grama e flores) no eixo, com arvore ou poste duplo de lanterna espacados. */
	static void median(WorkContext ctx, Blueprint bp, List<int[]> cells, int stage) {
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		cells.sort(java.util.Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
		List<int[]> picked = new ArrayList<>();
		int n = 0;
		for (int[] c : cells) {
			int h = plan.streetHeight(c[0], c[1]);
			bp.surface(c[0], h, c[1], m.lawn());
			boolean spot = ctx.free(c[0], c[1]);
			for (int[] p : picked) {
				int dx = p[0] - c[0];
				int dz = p[1] - c[1];
				if (dx * dx + dz * dz < MEDIAN_SPACING * MEDIAN_SPACING) {
					spot = false;
					break;
				}
			}
			if (spot) {
				picked.add(c);
				boolean lamp = (n++ % 2 == 1) && stage >= 5;
				if (lamp) {
					doublePost(plan, bp, m, c[0], c[1], h);
				} else {
					tree(bp, m, c[0], c[1], h, ctx.roll(c[0], c[1], 3, 3));
				}
			} else if (ctx.roll(c[0], c[1], 5, 4) == 0) {
				flowers(bp, c[0], c[1], h);
			}
		}
	}

	static void flowers(Blueprint bp, int x, int z, int h) {
		bp.above(x, h + 1, z, Math.floorMod(x * 3 + z, 3) == 0 ? "minecraft:cornflower" : Math.floorMod(x + z, 2) == 0 ? "minecraft:poppy"
				: "minecraft:short_grass");
	}

	/** Arvore de rua: tronco de 4 a 5 e copa rala de folhas que nao caem (a faixa de passar fica livre embaixo). */
	public static void tree(Blueprint bp, Mats m, int x, int z, int h, int variant) {
		int trunk = 4 + (variant == 2 ? 1 : 0);
		for (int y = 1; y <= trunk; y++) {
			bp.above(x, h + y, z, m.log());
		}
		int top = h + trunk;
		for (int dy = -1; dy <= 1; dy++) {
			int r = dy == 1 ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx == 0 && dz == 0 && dy <= 0) {
						continue;
					}
					if (Math.abs(dx) == r && Math.abs(dz) == r && (r == 2 || variant == 0)) {
						continue;
					}
					bp.above(x + dx, top + dy, z + dz, m.leavesKeep());
				}
			}
		}
	}

	/** Poste duplo: base de muro, haste de cerca e travessa com uma lanterna para cada lado (travessa pela largura da rua). */
	static void doublePost(MasterPlan plan, Blueprint bp, Mats m, int x, int z, int h) {
		int alongX = 0;
		int alongZ = 0;
		for (int k = 1; k <= 3; k++) {
			alongX += (plan.street(x + k, z) ? 1 : 0) + (plan.street(x - k, z) ? 1 : 0);
			alongZ += (plan.street(x, z + k) ? 1 : 0) + (plan.street(x, z - k) ? 1 : 0);
		}
		boolean armsX = alongX < alongZ;
		bp.above(x, h + 1, z, m.wall());
		for (int y = 2; y <= 5; y++) {
			bp.above(x, h + y, z, m.fence());
		}
		int ax = armsX ? 1 : 0;
		int az = armsX ? 0 : 1;
		for (int s : new int[] {-1, 1}) {
			bp.above(x + s * ax, h + 5, z + s * az, m.fence());
			bp.above(x + s * ax, h + 4, z + s * az, Mats.lantern(true));
		}
		bp.above(x, h + 6, z, Mats.lantern(false));
	}
}
