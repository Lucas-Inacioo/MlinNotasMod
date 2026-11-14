package com.mlin.notas.village.works;

import com.mlin.notas.village.Facing;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.StreetPlanner;
import com.mlin.notas.village.VillageLayout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pontes sobre agua funda nas ruas do plano diretor (logica pura): cada trecho de celulas de ponte (agua com mais de 2
 * de fundo no meio da rua) ganha tabuleiro de pedra (avenidas) ou tabuas (aneis e travessas) na altura das duas pontas,
 * com degraus de no maximo 1 entre celulas vizinhas, guarda-corpo dos lados da agua, arco por baixo e pilares ate o
 * fundo quando o terreno e conhecido.
 *
 * <p>Referencias de forma: pontes de pedra em arco de vilas medievais (Planet Minecraft "stone arch bridge"); escrito
 * do zero.
 */
public final class BridgeWorks {
	private BridgeWorks() {
	}

	/** Um trecho de ponte: celulas (x, z) com a altura do tabuleiro calculada. */
	public record Span(List<int[]> cells, int[] heights, int kind, int ring) {
	}

	public static boolean bridge(MasterPlan plan, int x, int z) {
		return plan.street(x, z) && !plan.ground(x, z);
	}

	/** Trechos de ponte com alturas que sobem no maximo 1 por celula entre as duas pontas. */
	public static List<Span> spans(MasterPlan plan) {
		return spans(plan, null);
	}

	/** Como {@link #spans(MasterPlan)}; com o terreno conhecido o tabuleiro sobe (1 por celula) ate ficar acima da agua. */
	public static List<Span> spans(MasterPlan plan, com.mlin.notas.village.Terrain terrain) {
		int e = plan.extent();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		List<Span> out = new ArrayList<>();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (!bridge(plan, x, z) || !seen.add(VillageLayout.col(x, z))) {
					continue;
				}
				List<int[]> cells = new ArrayList<>();
				ArrayDeque<int[]> queue = new ArrayDeque<>();
				queue.add(new int[] {x, z});
				while (!queue.isEmpty()) {
					int[] c = queue.poll();
					cells.add(c);
					for (Facing f : Facing.values()) {
						int nx = c[0] + f.dx;
						int nz = c[1] + f.dz;
						if (bridge(plan, nx, nz) && seen.add(VillageLayout.col(nx, nz))) {
							queue.add(new int[] {nx, nz});
						}
					}
				}
				Span span = heights(plan, cells, terrain);
				if (span != null) {
					out.add(span);
				}
			}
		}
		return out;
	}

	/** Altura de cada celula: a da ponta mais perto, depois alisada (degrau de 1) entre as pontas. */
	static Span heights(MasterPlan plan, List<int[]> cells, com.mlin.notas.village.Terrain terrain) {
		Map<Long, Integer> idx = new HashMap<>();
		for (int i = 0; i < cells.size(); i++) {
			idx.put(VillageLayout.col(cells.get(i)[0], cells.get(i)[1]), i);
		}
		int n = cells.size();
		int[] h = new int[n];
		boolean[] set = new boolean[n];
		int[] ramp = new int[n];
		int[] endH = new int[n];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		boolean anyEnd = false;
		for (int i = 0; i < n; i++) {
			int best = Integer.MIN_VALUE;
			int count = 0;
			for (Facing f : Facing.values()) {
				int ex = cells.get(i)[0] + f.dx;
				int ez = cells.get(i)[1] + f.dz;
				if (plan.ground(ex, ez)) {
					best = Math.max(best, plan.streetHeight(ex, ez));
					count++;
				}
			}
			if (count > 0) {
				h[i] = best;
				endH[i] = best;
				set[i] = true;
				queue.add(i);
				anyEnd = true;
			}
		}
		if (!anyEnd) {
			return null;
		}
		while (!queue.isEmpty()) {
			int i = queue.poll();
			for (Facing f : Facing.values()) {
				Integer j = idx.get(VillageLayout.col(cells.get(i)[0] + f.dx, cells.get(i)[1] + f.dz));
				if (j != null && !set[j]) {
					h[j] = h[i];
					endH[j] = endH[i];
					ramp[j] = ramp[i] + 1;
					set[j] = true;
					queue.add(j);
				}
			}
		}
		// agua mais alta que as pontas: o tabuleiro sobe 1 por celula ate o nivel da agua
		if (terrain != null) {
			int top = Integer.MIN_VALUE;
			for (int[] c : cells) {
				if (terrain.water(c[0], c[1]) > 0) {
					top = Math.max(top, terrain.ground(c[0], c[1]));
				}
			}
			if (top != Integer.MIN_VALUE) {
				for (int i = 0; i < n; i++) {
					h[i] = Math.max(h[i], Math.min(top, endH[i] + ramp[i]));
				}
			}
		}
		// alisa: vizinhos diferem no maximo 1 (as pontas ficam fixas)
		boolean[] fixed = new boolean[n];
		for (int i = 0; i < n; i++) {
			for (Facing f : Facing.values()) {
				fixed[i] |= plan.ground(cells.get(i)[0] + f.dx, cells.get(i)[1] + f.dz);
			}
		}
		for (int pass = 0; pass < 200; pass++) {
			boolean changed = false;
			for (int i = 0; i < n; i++) {
				if (fixed[i]) {
					continue;
				}
				int lo = Integer.MIN_VALUE;
				int hi = Integer.MAX_VALUE;
				for (Facing f : Facing.values()) {
					Integer j = idx.get(VillageLayout.col(cells.get(i)[0] + f.dx, cells.get(i)[1] + f.dz));
					if (j != null) {
						lo = Math.max(lo, h[j] - 1);
						hi = Math.min(hi, h[j] + 1);
					}
				}
				int v = lo > hi ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, h[i]));
				if (v != h[i]) {
					h[i] = v;
					changed = true;
				}
			}
			if (!changed) {
				break;
			}
		}
		int kind = plan.streetKind(cells.get(0)[0], cells.get(0)[1]);
		return new Span(cells, h, kind, plan.ring(cells.get(0)[0], cells.get(0)[1]));
	}

	/** Pontes do anel: todo trecho cujo primeiro anel e {@code ring}. */
	public static Blueprint plan(WorkContext ctx, int ring) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		for (Span span : spans(plan, ctx.terrain())) {
			if (span.ring() != ring || !ctx.ready(span.cells().get(0)[0], span.cells().get(0)[1])) {
				continue;
			}
			bp.needRing(ring);
			boolean stone = span.kind() == StreetPlanner.AVENUE;
			Map<Long, Integer> byCell = new HashMap<>();
			for (int i = 0; i < span.cells().size(); i++) {
				byCell.put(VillageLayout.col(span.cells().get(i)[0], span.cells().get(i)[1]), span.heights()[i]);
			}
			for (int i = 0; i < span.cells().size(); i++) {
				int x = span.cells().get(i)[0];
				int z = span.cells().get(i)[1];
				int h = span.heights()[i];
				bp.surface(x, h, z, stone ? (Math.floorMod(x + z, 5) == 0 ? "minecraft:cracked_stone_bricks" : m.bricks()) : m.planks());
				// arco: uma camada de pedra por baixo, mais grossa perto das pontas
				bp.surface(x, h - 1, z, stone ? m.bricks() : m.log().replace("_log", "_planks"));
				boolean waterSide = false;
				for (Facing f : Facing.values()) {
					int nx = x + f.dx;
					int nz = z + f.dz;
					if (!byCell.containsKey(VillageLayout.col(nx, nz)) && !plan.ground(nx, nz) && !plan.plaza(nx, nz)) {
						waterSide = true;
					}
				}
				if (waterSide) {
					bp.above(x, h + 1, z, stone ? m.wall() : m.fence());
				}
				if (waterSide && stone && Math.floorMod(x * 3 + z, 6) == 0) {
					bp.above(x, h + 2, z, Mats.lantern(false));
				}
				// pilares ate o fundo a cada 5 celulas, nas bordas
				if (ctx.terrain() != null && waterSide && Math.floorMod(x + z, 5) == 0) {
					int bed = ctx.terrain().bed(x, z);
					for (int y = h - 2; y >= bed; y--) {
						bp.surface(x, y, z, stone ? m.bricks() : m.log());
					}
				}
			}
		}
		return bp;
	}
}
