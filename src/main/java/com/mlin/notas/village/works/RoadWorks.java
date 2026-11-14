package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.StreetPlanner;
import com.mlin.notas.village.Terrain;
import com.mlin.notas.village.VillageLayout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Estradas externas (logica pura): de cada ponta de avenida (onde o plano diretor termina) ate o destino mais perto, a
 * ate {@link #MAX_DISTANCE} blocos e dentro de 60 graus da direcao em que a avenida sai: pedra de teleporte, outra vila,
 * portal do Nether. O caminho segue o terreno (A* com degrau maximo 1, sem agua, desviando de arvore e construcao),
 * com 3 de largura, poste de lanterna a cada 32 blocos e uma placa de direcao na saida. So troca o chao natural.
 *
 * <p>Referencias de forma: estradas de terra de trilhas medievais entre aldeias, marcos e placas de cruzamento (Minecraft
 * Maps "village roads", mods de estradas entre vilas); escrito do zero.
 */
public final class RoadWorks {
	public static final int MAX_DISTANCE = 300;
	public static final int POST_SPACING = 32;
	private static final int MAX_NODES = 40000;

	private RoadWorks() {
	}

	/** Ponta de avenida: centro (x, z) e direcao de saida (unitaria). */
	public record Exit(int x, int z, double dx, double dz) {
	}

	/** Uma estrada planejada: caminho (centro), destino e distancia. */
	public record Road(List<int[]> path, WorkContext.Destination destination, int length) {
	}

	/** Pontas das avenidas nas bordas do plano (agrupadas por proximidade). */
	public static List<Exit> exits(MasterPlan plan) {
		int e = plan.extent();
		Set<Long> seen = new HashSet<>();
		List<Exit> out = new ArrayList<>();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (!far(plan, x, z) || !seen.add(VillageLayout.col(x, z))) {
					continue;
				}
				List<int[]> cluster = new ArrayList<>();
				ArrayDeque<int[]> queue = new ArrayDeque<>();
				queue.add(new int[] {x, z});
				while (!queue.isEmpty()) {
					int[] c = queue.poll();
					cluster.add(c);
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if ((dx != 0 || dz != 0) && far(plan, c[0] + dx, c[1] + dz) && seen.add(VillageLayout.col(c[0] + dx, c[1] + dz))) {
								queue.add(new int[] {c[0] + dx, c[1] + dz});
							}
						}
					}
				}
				double sx = 0;
				double sz = 0;
				for (int[] c : cluster) {
					sx += c[0];
					sz += c[1];
				}
				sx /= cluster.size();
				sz /= cluster.size();
				double len = Math.hypot(sx, sz);
				int[] nearest = cluster.get(0);
				double best = Double.MAX_VALUE;
				for (int[] c : cluster) {
					double d = Math.hypot(c[0] - sx, c[1] - sz);
					if (d < best) {
						best = d;
						nearest = c;
					}
				}
				out.add(new Exit(nearest[0], nearest[1], sx / len, sz / len));
			}
		}
		return out;
	}

	static boolean far(MasterPlan plan, int x, int z) {
		return plan.ground(x, z) && plan.streetKind(x, z) == StreetPlanner.AVENUE && x * x + z * z >= 68 * 68;
	}

	/** Destino mais perto de cada saida (no maximo 300 blocos, dentro de 60 graus), sem repetir o par. */
	static WorkContext.Destination pick(Exit exit, List<WorkContext.Destination> destinations) {
		WorkContext.Destination best = null;
		double bestD = Double.MAX_VALUE;
		for (WorkContext.Destination d : destinations) {
			double dx = d.x() - exit.x();
			double dz = d.z() - exit.z();
			double dist = Math.hypot(dx, dz);
			if (dist > MAX_DISTANCE || dist < 24) {
				continue;
			}
			double cos = (dx * exit.dx() + dz * exit.dz()) / dist;
			if (cos < 0.5) {
				continue;
			}
			if (dist < bestD) {
				bestD = dist;
				best = d;
			}
		}
		return best;
	}

	/** A* de {@code from} ate perto de {@code to}; null se nao achar (agua, relevo, mata fechada). */
	public static List<int[]> route(Terrain t, int fx, int fz, int tx, int tz, int arriveRadius) {
		record Node(int x, int z, double g, double f) {
		}
		PriorityQueue<Node> open = new PriorityQueue<>(java.util.Comparator.comparingDouble(Node::f));
		Map<Long, Double> cost = new HashMap<>();
		Map<Long, Long> from = new HashMap<>();
		open.add(new Node(fx, fz, 0, Math.hypot(tx - fx, tz - fz)));
		cost.put(VillageLayout.col(fx, fz), 0.0);
		int expanded = 0;
		while (!open.isEmpty() && expanded++ < MAX_NODES) {
			Node n = open.poll();
			if (n.g > cost.getOrDefault(VillageLayout.col(n.x, n.z), Double.MAX_VALUE) + 1e-9) {
				continue;
			}
			if (Math.hypot(tx - n.x, tz - n.z) <= arriveRadius) {
				List<int[]> path = new ArrayList<>();
				long key = VillageLayout.col(n.x, n.z);
				path.add(new int[] {n.x, n.z});
				while (from.containsKey(key)) {
					key = from.get(key);
					path.add(new int[] {VillageLayout.colX(key), VillageLayout.colZ(key)});
				}
				java.util.Collections.reverse(path);
				return path;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					int nx = n.x + dx;
					int nz = n.z + dz;
					if (!t.free(nx, nz) || t.water(nx, nz) > 0) {
						continue;
					}
					int dh = Math.abs(t.ground(nx, nz) - t.ground(n.x, n.z));
					if (dh > 1) {
						continue;
					}
					// diagonal sem esquina: as duas celulas do lado tambem tem que servir
					if (dx != 0 && dz != 0 && (!t.free(n.x + dx, n.z) || !t.free(n.x, n.z + dz))) {
						continue;
					}
					double step = (dx != 0 && dz != 0 ? 1.414 : 1.0) * (1 + 0.6 * dh);
					double g = n.g + step;
					long key = VillageLayout.col(nx, nz);
					if (g < cost.getOrDefault(key, Double.MAX_VALUE)) {
						cost.put(key, g);
						from.put(key, VillageLayout.col(n.x, n.z));
						open.add(new Node(nx, nz, g, g + Math.hypot(tx - nx, tz - nz)));
					}
				}
			}
		}
		return null;
	}

	/** Estradas planejadas: uma por ponta de avenida que tem destino e caminho. */
	public static List<Road> roads(WorkContext ctx) {
		List<Road> out = new ArrayList<>();
		if (ctx.terrain() == null) {
			return out;
		}
		for (Exit exit : exits(ctx.plan())) {
			WorkContext.Destination dest = pick(exit, ctx.destinations());
			if (dest == null) {
				continue;
			}
			int steps = 1;
			while (steps < 12 && ctx.plan().code((int) Math.round(exit.x() + exit.dx() * steps), (int) Math.round(exit.z() + exit.dz() * steps)) != MasterPlan.OUT) {
				steps++;
			}
			int sx = (int) Math.round(exit.x() + exit.dx() * (steps + 1));
			int sz = (int) Math.round(exit.z() + exit.dz() * (steps + 1));
			List<int[]> path = route(ctx.terrain(), sx, sz, dest.x(), dest.z(), 4);
			if (path != null) {
				out.add(new Road(path, dest, path.size()));
			}
		}
		return out;
	}

	public static Blueprint plan(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		Mats m = ctx.mats();
		Terrain t = ctx.terrain();
		for (Road road : roads(ctx)) {
			Set<Long> cells = new HashSet<>();
			for (int i = 0; i < road.path().size(); i++) {
				int[] c = road.path().get(i);
				for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int x = c[0] + d[0];
					int z = c[1] + d[1];
					boolean center = d[0] == 0 && d[1] == 0;
					if (!center && (!t.free(x, z) || t.water(x, z) > 0 || Math.abs(t.ground(x, z) - t.ground(c[0], c[1])) > 1 || ctx.plan().code(x, z) != MasterPlan.OUT)) {
						continue;
					}
					if (cells.add(VillageLayout.col(x, z))) {
						bp.surface(x, t.ground(x, z), z, center || Math.floorMod(x * 3 + z * 5, 4) != 0 ? m.gravel() : "minecraft:dirt_path");
					}
				}
				if (i % POST_SPACING == POST_SPACING / 2 && i + 3 < road.path().size()) {
					post(ctx, bp, cells, road.path().get(i), road.path().get(i + 1));
				}
			}
			sign(ctx, bp, road);
		}
		return bp;
	}

	/** Poste de lanterna ao lado da estrada (na direcao perpendicular), onde o chao serve. */
	static void post(WorkContext ctx, Blueprint bp, Set<Long> cells, int[] at, int[] next) {
		Terrain t = ctx.terrain();
		Mats m = ctx.mats();
		int dx = Integer.signum(next[0] - at[0]);
		int dz = Integer.signum(next[1] - at[1]);
		int px = at[0] - dz * 2;
		int pz = at[1] + dx * 2;
		if (!t.free(px, pz) || t.water(px, pz) > 0 || cells.contains(VillageLayout.col(px, pz))) {
			return;
		}
		int g = t.ground(px, pz);
		bp.above(px, g + 1, pz, m.wall());
		bp.above(px, g + 2, pz, m.fence());
		bp.above(px, g + 3, pz, m.fence());
		bp.above(px, g + 4, pz, Mats.lantern(false));
	}

	/** Placa de direcao logo na saida, ao lado do caminho: nome do destino e distancia. */
	static void sign(WorkContext ctx, Blueprint bp, Road road) {
		Terrain t = ctx.terrain();
		Mats m = ctx.mats();
		if (road.path().size() < 3) {
			return;
		}
		int[] at = road.path().get(1);
		int[] next = road.path().get(2);
		int dx = Integer.signum(next[0] - at[0]);
		int dz = Integer.signum(next[1] - at[1]);
		int px = at[0] + dz * 2;
		int pz = at[1] - dx * 2;
		if (!t.free(px, pz) || t.water(px, pz) > 0) {
			return;
		}
		int g = t.ground(px, pz);
		int rot = Math.floorMod((int) Math.round(Math.atan2(dx, -dz) / (Math.PI / 8)) + 8, 16);
		bp.above(px, g + 1, pz, m.fence());
		bp.add(WBlock.sign(px, g + 2, pz, m.sign(rot), road.destination().name(), "~" + (int) Math.hypot(road.destination().x() - at[0], road.destination().z() - at[1]) + " m",
				"tr:sign.mlinnotas.road." + road.destination().kind(), ""));
	}
}
