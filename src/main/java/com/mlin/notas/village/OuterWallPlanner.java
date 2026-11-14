package com.mlin.notas.village;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Muralha externa da vila (logica pura; Vilas 2.0 fase 8): 149x149 por dentro, na faixa de fora do plano diretor
 * ({@link RuralRing#WALL}, mesmo corte de cantos do plano), com dois blocos de espessura: o de dentro leva o adarve e o
 * de fora o parapeito com ameias. Torres de 5x5 salientes para fora nos 8 cantos e a um quarto de cada lado reto;
 * portoes em arco onde as avenidas atravessam a muralha (todas, inclusive as diagonais), com pilares altos dos dois
 * lados. A muralha segue o chao natural da faixa (alicerce ate o fundo), e onde a linha passa por construcao de jogador
 * ou agua funda fica um vao ({@link Result#breaches}, aviso no contorno da vila): o trecho termina num pilar e a torre
 * que nao cabe inteira vira so o pedaco que cabe.
 *
 * <p>Referencias de forma: as muralhas duplas de Carcassonne e de Avila (torres salientes a cada trecho, portao entre
 * duas torres), o "Medieval City Walls" e o "Kings Landing walls" do Planet Minecraft e o guia de muralhas do
 * minecraft.how (adarve por dentro, ameias alternadas, base de tijolo); escrito do zero.
 */
public final class OuterWallPlanner {
	/** Altura da muralha acima do chao da coluna (topo do adarve). */
	public static final int HEIGHT = 7;
	/** Altura das torres acima da base delas. */
	public static final int TOWER = 11;
	/** Agua ate esta profundidade leva a muralha com alicerce ate o fundo (cais); mais funda fica um vao. */
	public static final int MAX_WATER = 6;

	private OuterWallPlanner() {
	}

	/** Portao: celulas da passagem na linha da muralha, base (y do chao), se e de eixo e a direcao para fora. */
	public record Gate(List<int[]> cells, int base, boolean axis, int dx, int dz, int[] center) {
	}

	/** O que a obra faz: blocos, vaos (colunas da linha onde a muralha nao pode ficar), golens e portoes. */
	public record Result(List<Placement> placements, List<int[]> breaches, List<int[]> golems, List<Gate> gates) {
	}

	/** Camada da muralha: 1 = de dentro (adarve), 2 = de fora (parapeito), 0 = fora da muralha. */
	public static int layer(int x, int z) {
		if (!RuralRing.outsideOld(x, z)) {
			return 0;
		}
		double f = StreetPlanner.Octagon.of(StreetPlanner.OUTER, null).f(x, z);
		return f <= 1.5 ? 1 : f <= 2.5 ? 2 : 0;
	}

	/** Passagens: celulas de rua (avenidas) nas duas camadas da muralha, agrupadas. */
	public static List<Gate> gates(MasterPlan plan, Terrain terrain) {
		List<Gate> out = new ArrayList<>();
		if (!plan.rural()) {
			return out;
		}
		int e = RuralRing.WALL + 3;
		Set<Long> seen = new HashSet<>();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (layer(x, z) == 0 || !plan.street(x, z) || !seen.add(VillageLayout.col(x, z))) {
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
							if (layer(nx, nz) != 0 && plan.street(nx, nz) && seen.add(VillageLayout.col(nx, nz))) {
								queue.add(new int[] {nx, nz});
							}
						}
					}
				}
				cells.sort(java.util.Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
				double cx = 0;
				double cz = 0;
				int base = Integer.MIN_VALUE;
				for (int[] c : cells) {
					cx += c[0];
					cz += c[1];
					base = Math.max(base, terrain.ground(c[0], c[1]));
					if (plan.ground(c[0], c[1])) {
						base = Math.max(base, plan.streetHeight(c[0], c[1]));
					}
				}
				cx /= cells.size();
				cz /= cells.size();
				double ax = Math.abs(cx);
				double az = Math.abs(cz);
				boolean axis = ax > 2 * az || az > 2 * ax;
				int dx = az > 2 * ax ? 0 : (int) Math.signum(cx);
				int dz = ax > 2 * az ? 0 : (int) Math.signum(cz);
				int[] center = cells.get(cells.size() / 2);
				out.add(new Gate(cells, base, axis, dx, dz, new int[] {(int) Math.round(cx), (int) Math.round(cz), center[0], center[1]}));
			}
		}
		return out;
	}

	/** Centros das torres: 8 cantos e um quarto de cada lado reto (saliente para fora, ate a borda da faixa). */
	public static List<int[]> towers() {
		int h = RuralRing.WALL;
		int d = (int) Math.round(StreetPlanner.DIAGONAL * h) - h;
		int o = h + 2;
		List<int[]> out = new ArrayList<>();
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				out.add(new int[] {sx * o, sz * (d - 2)});
				out.add(new int[] {sx * (d - 2), sz * o});
				out.add(new int[] {sx * o, sz * (d / 2)});
				out.add(new int[] {sx * (d / 2), sz * o});
			}
		}
		return out;
	}

	/** Coluna onde a muralha pode ficar: chao natural (agua rasa vale, com alicerce) e fora de area de jogador. */
	static boolean buildable(MasterPlan plan, Terrain terrain, int x, int z) {
		if (plan.insidePlayerArea(x, z)) {
			return false;
		}
		int water = terrain.water(x, z);
		if (water > MAX_WATER) {
			return false;
		}
		return water > 0 || terrain.free(x, z);
	}

	/**
	 * Vaos da muralha externa sem montar a obra (contorno da vila): colunas da linha da muralha fora dos portoes onde
	 * ha construcao de jogador ou agua funda. {x, z, 1 = agua / 0 = jogador}.
	 */
	public static List<int[]> breaches(MasterPlan plan, Terrain terrain) {
		List<int[]> out = new ArrayList<>();
		if (!plan.rural()) {
			return out;
		}
		int e = RuralRing.WALL + 1;
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (layer(x, z) == 1 && !plan.street(x, z) && !buildable(plan, terrain, x, z)) {
					out.add(new int[] {x, z, terrain.water(x, z) > MAX_WATER ? 1 : 0});
				}
			}
		}
		return out;
	}

	/** A obra inteira. Plano sem anel rural: nada. */
	public static Result plan(MasterPlan plan, Terrain terrain) {
		List<Placement> out = new ArrayList<>();
		List<int[]> breaches = new ArrayList<>();
		List<int[]> golems = new ArrayList<>();
		if (!plan.rural()) {
			return new Result(out, breaches, golems, List.of());
		}
		List<Gate> gates = gates(plan, terrain);
		java.util.Map<Long, Integer> gateBase = new java.util.HashMap<>();
		Set<Long> gateCells = new HashSet<>();
		for (Gate g : gates) {
			for (int[] c : g.cells()) {
				gateCells.add(VillageLayout.col(c[0], c[1]));
				gateBase.merge(VillageLayout.col(c[0], c[1]), g.base(), Math::max);
			}
		}
		// torres primeiro (as celulas delas saem da cortina)
		Set<Long> towerCells = new HashSet<>();
		for (int[] t : towers()) {
			tower(out, plan, terrain, t[0], t[1], gateCells, towerCells);
		}
		int e = RuralRing.WALL + 3;
		Set<Long> blocked = new HashSet<>();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				int layer = layer(x, z);
				if (layer == 0 || gateCells.contains(VillageLayout.col(x, z)) || towerCells.contains(VillageLayout.col(x, z))) {
					continue;
				}
				if (!buildable(plan, terrain, x, z)) {
					blocked.add(VillageLayout.col(x, z));
					if (layer == 1) {
						breaches.add(new int[] {x, z, terrain.water(x, z) > MAX_WATER ? 1 : 0});
					}
				}
			}
		}
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				int layer = layer(x, z);
				long key = VillageLayout.col(x, z);
				if (layer == 0 || gateCells.contains(key) || towerCells.contains(key) || blocked.contains(key)) {
					continue;
				}
				int g = terrain.water(x, z) > 0 ? terrain.ground(x, z) + 1 : terrain.ground(x, z);
				boolean pillar = nextTo(gateCells, x, z) || nextTo(blocked, x, z);
				int ref = ref(plan, terrain, x, z);
				for (Facing f : Facing.values()) {
					ref = Math.max(ref, gateBase.getOrDefault(VillageLayout.col(x + f.dx, z + f.dz), Integer.MIN_VALUE));
				}
				int top = ref + HEIGHT + (pillar ? 2 : 0);
				out.add(new Placement(x, g, z, VPart.FOUNDATION, null, true));
				for (int y = g + 1; y <= top; y++) {
					VPart part = pillar || y == g + 1 || y == top ? VPart.BRICK
							: Math.floorMod(x * 7 + z * 11 + y * 3, 6) == 0 ? VPart.STONE_ALT : VPart.STONE;
					out.add(new Placement(x, y, z, part, null, false));
				}
				if (pillar) {
					out.add(new Placement(x, top + 1, z, VPart.STONE_SLAB, null, false));
					if (Math.floorMod(x + z, 2) == 0) {
						out.add(new Placement(x, top + 2, z, VPart.LANTERN, null, false));
					}
				} else if (layer == 2 && Math.floorMod(x + z, 2) == 0) {
					// ameias alternadas no parapeito de fora
					out.add(new Placement(x, top + 1, z, VPart.BRICK, null, false));
				} else if (layer == 1 && Math.floorMod(x * 3 + z * 5, 23) == 0) {
					out.add(new Placement(x, top + 1, z, VPart.LANTERN, null, false));
				}
			}
		}
		for (Gate gate : gates) {
			gate(out, plan, terrain, gate, gateCells);
			int[] in = {gate.center()[0] - gate.dx() * 5, gate.center()[1] - gate.dz() * 5};
			int y = plan.ground(in[0], in[1]) ? plan.streetHeight(in[0], in[1]) : terrain.ground(in[0], in[1]);
			golems.add(new int[] {in[0], y + 1, in[1]});
		}
		return new Result(VillagePlanner.sort(VillagePlanner.dedupe(out)), breaches, golems, gates);
	}

	/**
	 * Altura de referencia da coluna da muralha: o chao natural ou, se for mais alto, o chao planejado logo por dentro
	 * (plato ou rua do anel 6): numa encosta a muralha sobe do chao de fora ate acima do nivel da cidade.
	 */
	static int ref(MasterPlan plan, Terrain terrain, int x, int z) {
		int ref = terrain.ground(x, z);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (!RuralRing.outsideOld(x + dx, z + dz)) {
					int h = plan.plannedHeight(x + dx, z + dz);
					if (h != Integer.MIN_VALUE) {
						ref = Math.max(ref, h);
					}
				}
			}
		}
		return ref;
	}

	private static boolean nextTo(Set<Long> cells, int x, int z) {
		for (Facing f : Facing.values()) {
			if (cells.contains(VillageLayout.col(x + f.dx, z + f.dz))) {
				return true;
			}
		}
		return false;
	}

	/** Torre 5x5 macica com faixa de tijolo, seteiras, ameias e lanterna; so as celulas que podem receber muralha. */
	private static void tower(List<Placement> out, MasterPlan plan, Terrain terrain, int cx, int cz, Set<Long> gateCells, Set<Long> cells) {
		List<int[]> mine = new ArrayList<>();
		int base = Integer.MIN_VALUE;
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				long key = VillageLayout.col(x, z);
				if (!RuralRing.inBand(x, z) || gateCells.contains(key) || plan.street(x, z) || !buildable(plan, terrain, x, z)) {
					continue;
				}
				mine.add(new int[] {x, z});
				base = Math.max(base, ref(plan, terrain, x, z));
			}
		}
		if (mine.size() < 12) {
			return;
		}
		int top = base + TOWER;
		for (int[] c : mine) {
			int x = c[0];
			int z = c[1];
			cells.add(VillageLayout.col(x, z));
			boolean edge = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
			boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
			int g = terrain.water(x, z) > 0 ? terrain.ground(x, z) + 1 : terrain.ground(x, z);
			out.add(new Placement(x, g, z, VPart.FOUNDATION, null, true));
			for (int y = g + 1; y <= top; y++) {
				boolean slit = edge && !corner && (x == cx || z == cz) && (y == base + 5 || y == base + 9);
				VPart part = slit ? VPart.IRON_BARS
						: corner || y == base + 1 || y == top || y == base + 7 ? VPart.BRICK
						: Math.floorMod(x * 3 + y * 5 + z, 7) == 0 ? VPart.STONE_ALT : VPart.STONE;
				out.add(new Placement(x, y, z, part, null, false));
			}
			if (edge && (corner || Math.floorMod(x + z, 2) == 0)) {
				out.add(new Placement(x, top + 1, z, VPart.BRICK, null, false));
			}
		}
		if (cells.contains(VillageLayout.col(cx, cz))) {
			out.add(new Placement(cx, top + 1, cz, VPart.LANTERN, null, false));
		}
	}

	/** Portao: passagem livre de 4 de altura, arco de tijolo ate o topo da muralha, lanterna pendurada no meio. */
	private static void gate(List<Placement> out, MasterPlan plan, Terrain terrain, Gate gate, Set<Long> gateCells) {
		int base = gate.base();
		int top = base + HEIGHT + 2;
		for (int[] c : gate.cells()) {
			int x = c[0];
			int z = c[1];
			int g = plan.ground(x, z) ? plan.streetHeight(x, z) : terrain.ground(x, z);
			for (int y = g + 1; y <= base + 4; y++) {
				out.add(new Placement(x, y, z, VPart.AIR, null, false));
			}
			boolean middle = c[0] == gate.center()[2] && c[1] == gate.center()[3];
			if (middle) {
				out.add(new Placement(x, base + 4, z, VPart.LANTERN_HANGING, null, false));
			}
			for (int y = base + 5; y <= top; y++) {
				out.add(new Placement(x, y, z, y == base + 5 || y == top ? VPart.BRICK : VPart.STONE, null, false));
			}
			if (Math.floorMod(x + z, 2) == 0) {
				out.add(new Placement(x, top + 1, z, VPart.BRICK, null, false));
			}
		}
	}
}
