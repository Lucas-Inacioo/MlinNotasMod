package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillagePlanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fases da muralha (logica pura), no mesmo octogono de meia-largura 50 da muralha de pedra paga ({@code VillagePlanner}):
 * paliçada de troncos gratis (Povoado), portoes de madeira com guarita sobre a passagem (Vila), e, no Capital, ameias nas
 * paredes de pedra, tampas nas torres dos cantos e grade de ferro nos portoes dos eixos (fecha a noite e em ataque,
 * alavanca abre). Os portoes ficam onde uma avenida cruza a muralha; a muralha externa de 149x149 e fase posterior
 * ({@link #OUTER_HALF}, gancho).
 *
 * <p>Referencias de forma: paliçadas e casas de guarda sobre o portao ("gatehouse") de aldeias medievais do Planet
 * Minecraft e do Minecraft Maps; escrito do zero.
 */
public final class WallWorks {
	/** Meia-largura da muralha externa (fase F8); aqui so o gancho. */
	public static final int OUTER_HALF = 75;

	private WallWorks() {
	}

	/** Passagem na muralha: celulas (x, z) onde uma avenida cruza a linha do octogono. */
	public record Gate(List<int[]> cells, int baseY, boolean axis, boolean alongX, int[] endA, int[] endB) {
	}

	/** Uma grade de ferro: onde ficam as barras (x, y, z) e a alavanca. */
	public record Portcullis(List<int[]> bars, int[] lever, String leverState) {
	}

	static boolean wallStreet(WorkContext ctx, int x, int z) {
		return VillagePlanner.wallCell(x, z) && ctx.plan().ground(x, z);
	}

	public static List<Gate> gates(WorkContext ctx) {
		MasterPlan plan = ctx.plan();
		int h = VillagePlanner.WALL_HALF + 2;
		Set<Long> seen = new HashSet<>();
		List<Gate> out = new ArrayList<>();
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!wallStreet(ctx, x, z) || !seen.add(VillageLayout.col(x, z))) {
					continue;
				}
				List<int[]> cells = new ArrayList<>();
				ArrayDeque<int[]> queue = new ArrayDeque<>();
				queue.add(new int[] {x, z});
				while (!queue.isEmpty()) {
					int[] c = queue.poll();
					cells.add(c);
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							int nx = c[0] + dx;
							int nz = c[1] + dz;
							if ((dx != 0 || dz != 0) && wallStreet(ctx, nx, nz) && seen.add(VillageLayout.col(nx, nz))) {
								queue.add(new int[] {nx, nz});
							}
						}
					}
				}
				cells.sort(java.util.Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
				int minX = Integer.MAX_VALUE;
				int maxX = Integer.MIN_VALUE;
				int minZ = Integer.MAX_VALUE;
				int maxZ = Integer.MIN_VALUE;
				int base = Integer.MIN_VALUE;
				for (int[] c : cells) {
					minX = Math.min(minX, c[0]);
					maxX = Math.max(maxX, c[0]);
					minZ = Math.min(minZ, c[1]);
					maxZ = Math.max(maxZ, c[1]);
					base = Math.max(base, plan.streetHeight(c[0], c[1]));
				}
				boolean alongX = minZ == maxZ;
				boolean alongZ = minX == maxX;
				boolean axis = alongX || alongZ;
				int[] a;
				int[] b;
				if (alongX) {
					a = new int[] {minX - 1, minZ};
					b = new int[] {maxX + 1, minZ};
				} else if (alongZ) {
					a = new int[] {minX, minZ - 1};
					b = new int[] {minX, maxZ + 1};
				} else {
					a = farthestNeighbour(ctx, cells, true);
					b = farthestNeighbour(ctx, cells, false);
				}
				if (a == null || b == null) {
					continue;
				}
				out.add(new Gate(cells, base, axis, alongX, a, b));
			}
		}
		return out;
	}

	/** Celula da muralha encostada na passagem (fora dela) com o menor/maior x - z: as pontas da passagem diagonal. */
	static int[] farthestNeighbour(WorkContext ctx, List<int[]> cells, boolean low) {
		Set<Long> in = new HashSet<>();
		for (int[] c : cells) {
			in.add(VillageLayout.col(c[0], c[1]));
		}
		int[] best = null;
		int bestV = 0;
		for (int[] c : cells) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int nx = c[0] + dx;
					int nz = c[1] + dz;
					if (VillagePlanner.wallCell(nx, nz) && !in.contains(VillageLayout.col(nx, nz))) {
						int v = nx * 3 + nz;
						if (best == null || (low ? v < bestV : v > bestV)) {
							best = new int[] {nx, nz};
							bestV = v;
						}
					}
				}
			}
		}
		return best;
	}

	static boolean isGateCell(List<Gate> gates, int x, int z) {
		for (Gate g : gates) {
			for (int[] c : g.cells()) {
				if (c[0] == x && c[1] == z) {
					return true;
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- paliçada

	/** Paliçada: troncos de 3 a 4 de altura com ponta de cerca, em todas as celulas da muralha fora das passagens. */
	public static Blueprint palisade(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		Mats m = ctx.mats();
		List<Gate> gates = gates(ctx);
		int h = VillagePlanner.WALL_HALF;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!VillagePlanner.wallCell(x, z) || isGateCell(gates, x, z)) {
					continue;
				}
				int g = ctx.h(x, z);
				if (g == Integer.MIN_VALUE || !ctx.ready(x, z) || !ctx.free(x, z)) {
					continue;
				}
				bp.needRing(ctx.plan().ring(x, z));
				int k = 3 + ctx.roll(x, z, 61, 2);
				for (int y = 1; y <= k; y++) {
					bp.above(x, g + y, z, m.log());
				}
				bp.above(x, g + k + 1, z, m.fence());
				if (Math.floorMod(x * 3 + z * 5, 14) == 0) {
					bp.above(x, g + k + 2, z, Mats.lantern(false));
				}
			}
		}
		return bp;
	}

	// ---------------------------------------------------------------- portoes e guarita

	/** Portoes: dois marcos de tronco com lanterna, viga na passagem, folhas abertas e guarita sobre a passagem dos eixos. */
	public static Blueprint woodenGates(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		Mats m = ctx.mats();
		for (Gate gate : gates(ctx)) {
			boolean ready = true;
			for (int[] c : gate.cells()) {
				ready &= ctx.ready(c[0], c[1]);
			}
			if (!ready || ctx.h(gate.endA()[0], gate.endA()[1]) == Integer.MIN_VALUE || ctx.h(gate.endB()[0], gate.endB()[1]) == Integer.MIN_VALUE) {
				continue;
			}
			bp.needRing(ctx.plan().ring(gate.cells().get(0)[0], gate.cells().get(0)[1]));
			int base = gate.baseY();
			int top = base + (gate.axis() ? 6 : 5);
			for (int[] end : new int[][] {gate.endA(), gate.endB()}) {
				int g = ctx.h(end[0], end[1]);
				for (int y = g + 1; y <= top; y++) {
					bp.above(end[0], y, end[1], m.log());
				}
				bp.above(end[0], top + 1, end[1], Mats.lantern(false));
			}
			if (!gate.axis()) {
				continue;
			}
			// viga na passagem (a passagem fica livre ate base + 4: nada pendurado)
			String axisState = gate.alongX() ? "[axis=x]" : "[axis=z]";
			for (int[] c : gate.cells()) {
				bp.above(c[0], base + 5, c[1], m.log() + axisState);
			}
			// folhas abertas nas pontas da passagem
			String facing = gate.alongX() ? "north" : "west";
			int[] first = gate.cells().getFirst();
			int[] last = gate.cells().getLast();
			for (int[] c : new int[][] {first, last}) {
				bp.above(c[0], ctx.plan().streetHeight(c[0], c[1]) + 1, c[1], m.fence().replace("_fence", "_fence_gate") + "[facing=" + facing + ",open=true]");
			}
			guardhouse(ctx, bp, gate, base + 6);
		}
		return bp;
	}

	/** Guarita: piso de tabuas sobre a passagem (3 de fundo), paredes de 3 com janelas, telhado de lajes e lanterna. */
	static void guardhouse(WorkContext ctx, Blueprint bp, Gate gate, int floorY) {
		Mats m = ctx.mats();
		int[] a = gate.endA();
		int[] b = gate.endB();
		int minX = Math.min(a[0], b[0]);
		int maxX = Math.max(a[0], b[0]);
		int minZ = Math.min(a[1], b[1]);
		int maxZ = Math.max(a[1], b[1]);
		if (gate.alongX()) {
			minZ -= 1;
			maxZ += 1;
		} else {
			minX -= 1;
			maxX += 1;
		}
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
				boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
				bp.above(x, floorY, z, m.planks());
				for (int y = 1; y <= 3; y++) {
					if (!edge) {
						continue;
					}
					boolean window = y == 2 && !corner && ((gate.alongX() ? (x - minX) : (z - minZ)) % 2 == 1);
					bp.above(x, floorY + y, z, corner ? m.log() : window ? "minecraft:glass_pane" : m.planks());
				}
				bp.above(x, floorY + 4, z, edge ? m.slabBottom() : m.planks());
			}
		}
		int cx = (minX + maxX) / 2;
		int cz = (minZ + maxZ) / 2;
		bp.above(cx, floorY + 3, cz, Mats.lantern(true));
	}

	// ---------------------------------------------------------------- capital: ameias, torres, grade de ferro

	/** Centros das 8 torres de canto da muralha de pedra. */
	public static List<int[]> towers() {
		int h = VillagePlanner.WALL_HALF;
		int d = VillagePlanner.WALL_DIAGONAL - h;
		int[][] v = {{h, d}, {d, h}, {-h, d}, {-d, h}, {h, -d}, {d, -h}, {-h, -d}, {-d, -h}};
		List<int[]> out = new ArrayList<>();
		for (int[] p : v) {
			out.add(new int[] {p[0] - Integer.signum(p[0]), p[1] - Integer.signum(p[1])});
		}
		return out;
	}

	/** Ameias em cima da muralha de pedra e tampa de lookout nas torres dos cantos (so onde a muralha de pedra existe). */
	public static Blueprint battlements(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		Mats m = ctx.mats();
		int h = VillagePlanner.WALL_HALF;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!VillagePlanner.wallCell(x, z) || Math.floorMod(x + z, 2) != 0) {
					continue;
				}
				int top = ctx.columnTop().apply(x, z);
				if (top == Integer.MIN_VALUE || !ctx.free(x, z)) {
					continue;
				}
				bp.above(x, top + 1, z, m.bricks());
			}
		}
		for (int[] t : towers()) {
			int top = ctx.columnTop().apply(t[0], t[1]);
			if (top == Integer.MIN_VALUE) {
				continue;
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
					boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
					if (edge) {
						for (int y = 0; y <= 2; y++) {
							boolean slit = y == 1 && !corner && (dx == 0 || dz == 0);
							if (!slit) {
								bp.above(t[0] + dx, top + y, t[1] + dz, corner ? m.log() : m.bricks());
							}
						}
					}
					bp.above(t[0] + dx, top + 3, t[1] + dz, edge ? m.brickSlab() : m.bricks());
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					bp.above(t[0] + dx, top + 4, t[1] + dz, dx == 0 && dz == 0 ? m.bricks() : m.brickSlab());
				}
			}
			bp.above(t[0], top + 5, t[1], "minecraft:lightning_rod");
		}
		return bp;
	}

	/** Grades de ferro dos portoes dos eixos (3 de largura, 3 de altura) e a alavanca ao lado de dentro. */
	public static List<Portcullis> portcullises(WorkContext ctx) {
		List<Portcullis> out = new ArrayList<>();
		for (Gate gate : gates(ctx)) {
			if (!gate.axis() || gate.cells().size() < 3) {
				continue;
			}
			List<int[]> bars = new ArrayList<>();
			int mid = gate.cells().size() / 2;
			for (int i = mid - 1; i <= mid + 1; i++) {
				int[] c = gate.cells().get(i);
				int g = ctx.plan().streetHeight(c[0], c[1]);
				for (int y = 1; y <= 3; y++) {
					bars.add(new int[] {c[0], g + y, c[1]});
				}
			}
			// alavanca no marco de dentro (o marco fica a 2 do centro)
			int[] center = gate.cells().get(mid);
			int inwardX = gate.alongX() ? 0 : -Integer.signum(center[0]);
			int inwardZ = gate.alongX() ? -Integer.signum(center[1]) : 0;
			int[] pillar = gate.alongX() ? new int[] {center[0] + 2, center[1]} : new int[] {center[0], center[1] + 2};
			int g = ctx.plan().streetHeight(center[0], center[1]);
			int[] lever = {pillar[0] + inwardX, g + 2, pillar[1] + inwardZ};
			String facing = inwardX > 0 ? "east" : inwardX < 0 ? "west" : inwardZ > 0 ? "south" : "north";
			out.add(new Portcullis(bars, lever, "minecraft:lever[face=wall,facing=" + facing + ",powered=false]"));
		}
		return out;
	}
}
