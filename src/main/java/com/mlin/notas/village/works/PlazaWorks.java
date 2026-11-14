package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Praca central por nivel (logica pura). O poco vem da obra paga "Praca do Poco"; aqui a praca ganha, a cada nivel:
 * calcamento ate a borda com bancos e canteiros, chafariz, coreto, estatua (do ultimo chefe derrotado, ou generica) e o
 * monumento com torre do relogio. Cada peca ocupa um quadrante da praca (nordeste = chafariz, sudoeste = coreto,
 * noroeste = estatua, sudeste = monumento), centrada em (+-4, +-4) ou, se o chao la nao esta na altura da praca ou algo
 * atrapalha, em (+-3, +-3); os eixos (as entradas das avenidas) ficam livres.
 *
 * <p>Referencias de forma: chafarizes de praca de vila e coretos vitorianos (Planet Minecraft "village fountain",
 * "bandstand"), torre do relogio de praca de cidade pequena; as pecas ficam em {@link PlazaSculpture}.
 */
public final class PlazaWorks {
	private PlazaWorks() {
	}

	/** Quadrantes: nordeste, sudoeste, noroeste, sudeste ({x, z}). */
	static final int[][] QUADRANTS = {{1, -1}, {-1, 1}, {-1, -1}, {1, 1}};

	public static Blueprint plan(WorkContext ctx, int stage) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		int base = plan.plazaY();
		if (!ctx.ready(0, 1) && !ctx.ready(1, 0) && !ctx.ready(0, -1) && !ctx.ready(-1, 0)) {
			// a praca fica no anel 1; sem ele pronto nada acontece
			return bp;
		}
		bp.needRing(1);
		paving(ctx, bp, base, stage);
		List<int[]> used = new ArrayList<>();
		if (stage >= 2) {
			place(ctx, bp, used, QUADRANTS[0], 1, (c, sx, sz) -> PlazaSculpture.fountain(ctx.mats(), bp, c[0], c[1], base));
		}
		if (stage >= 3) {
			place(ctx, bp, used, QUADRANTS[1], 1, (c, sx, sz) -> PlazaSculpture.bandstand(ctx.mats(), bp, c[0], c[1], base, -sz));
		}
		if (stage >= 4) {
			place(ctx, bp, used, QUADRANTS[2], 1, (c, sx, sz) -> PlazaSculpture.statue(ctx.mats(), bp, c[0], c[1], base, ctx.lastBoss(), -sz));
		}
		if (stage >= 5) {
			place(ctx, bp, used, QUADRANTS[3], 1, (c, sx, sz) -> PlazaSculpture.clockTower(ctx.mats(), bp, c[0], c[1], base));
		}
		benchesAndBeds(ctx, bp, base, used);
		return bp;
	}

	interface Feature {
		void build(int[] center, int sx, int sz);
	}

	/** Acha o centro do quadrante (4,4) ou (3,3) onde a peca de raio {@code r} cabe e a constroi. */
	static void place(WorkContext ctx, Blueprint bp, List<int[]> used, int[] q, int r, Feature feature) {
		// o canto (4,4), o (3,3) e vizinhos: poste de lampiao ou banco no caminho nao pode deixar a praca sem a peca
		for (int[] d : new int[][] {{4, 4}, {3, 3}, {4, 3}, {3, 4}, {5, 4}, {4, 5}, {5, 3}, {3, 5}, {5, 5}}) {
			int cx = q[0] * d[0];
			int cz = q[1] * d[1];
			boolean ok = true;
			for (int x = cx - r; x <= cx + r && ok; x++) {
				for (int z = cz - r; z <= cz + r && ok; z++) {
					ok = ctx.h(x, z) == ctx.plan().plazaY() && ctx.free(x, z) && ctx.plan().code(x, z) != MasterPlan.OUT
							&& ctx.plan().code(x, z) != MasterPlan.LAKE;
				}
			}
			if (ok) {
				used.add(new int[] {cx, cz, r});
				feature.build(new int[] {cx, cz}, q[0], q[1]);
				return;
			}
		}
	}

	/** Calcamento ate a borda da praca (a praca e circular: celulas da propria praca). */
	static void paving(WorkContext ctx, Blueprint bp, int base, int stage) {
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (!plan.plaza(x, z) || (x == 0 && z == 0) || ctx.h(x, z) != base) {
					continue;
				}
				int d2 = x * x + z * z;
				String block;
				if (stage >= 3) {
					// pedra desenhada: anel central claro, quadriculado
					block = d2 <= 6 ? m.accent() : Math.floorMod(x + z, 2) == 0 ? m.bricks() : m.smooth();
				} else {
					int pattern = Math.floorMod(x * 5 + z * 3, 9);
					block = d2 <= 2 ? m.bricks() : pattern == 0 ? "minecraft:mossy_stone_bricks" : pattern < 3 ? m.cobble() : m.bricks();
				}
				bp.surface(x, base, z, block);
			}
		}
	}

	/** Bancos virados para o centro nas 8 bocas das entradas e canteiros de flores nos cantos que sobram. */
	static void benchesAndBeds(WorkContext ctx, Blueprint bp, int base, List<int[]> used) {
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		int[][] benches = {{5, 2, 0}, {5, -2, 0}, {-5, 2, 1}, {-5, -2, 1}, {2, 5, 2}, {-2, 5, 2}, {2, -5, 3}, {-2, -5, 3}};
		String[] faces = {"east", "west", "south", "north"};
		for (int[] b : benches) {
			if (plan.plaza(b[0], b[1]) && ctx.h(b[0], b[1]) == base && ctx.free(b[0], b[1]) && !inside(used, b[0], b[1], 1)) {
				bp.above(b[0], base + 1, b[1], m.stairs(faces[b[2]], false));
			}
		}
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				int d2 = x * x + z * z;
				if (!plan.plaza(x, z) || ctx.h(x, z) != base || Math.abs(x) <= 2 || Math.abs(z) <= 2 || d2 < 31 || !ctx.free(x, z) || inside(used, x, z, 1)) {
					continue;
				}
				bp.surface(x, base, z, m.lawn());
				bp.above(x, base + 1, z, ctx.roll(x, z, 11, 3) == 0 ? "minecraft:azure_bluet" : ctx.roll(x, z, 12, 2) == 0 ? "minecraft:cornflower"
						: "minecraft:poppy");
			}
		}
	}

	static boolean inside(List<int[]> used, int x, int z, int grow) {
		for (int[] u : used) {
			if (Math.abs(x - u[0]) <= u[2] + grow && Math.abs(z - u[1]) <= u[2] + grow) {
				return true;
			}
		}
		return false;
	}

}
