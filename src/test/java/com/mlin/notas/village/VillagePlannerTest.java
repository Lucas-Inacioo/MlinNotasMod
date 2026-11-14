package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagePlannerTest {
	/** Terreno falso: altura por funcao, colunas "de jogador" bloqueadas, e o que ja foi construido ocupa a coluna. */
	static final class FakeTerrain implements Terrain {
		final IntBinaryOperator height;
		final Set<Long> blocked = new HashSet<>();
		final Set<Long> built = new HashSet<>();

		FakeTerrain(IntBinaryOperator height) {
			this.height = height;
		}

		static long col(int x, int z) {
			return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
		}

		@Override
		public int ground(int x, int z) {
			return height.applyAsInt(x, z);
		}

		@Override
		public boolean free(int x, int z) {
			return !blocked.contains(col(x, z)) && !built.contains(col(x, z)) && !(x == 0 && z == 0);
		}

		void apply(List<Placement> placements) {
			for (Placement p : placements) {
				if (p.part() != VPart.AIR) {
					built.add(col(p.x(), p.z()));
				}
			}
		}
	}

	/** Mundo de voxels para conferir alcance a pe e apoio: terreno + todas as obras. */
	static final class Voxels {
		final Terrain terrain;
		final Map<Long, VPart> blocks = new HashMap<>();

		Voxels(Terrain terrain) {
			this.terrain = terrain;
		}

		void add(List<Placement> placements) {
			for (Placement p : placements) {
				if (p.part() == VPart.AIR && blocks.containsKey(Placement.key(p.x(), p.y(), p.z()))) {
					continue;
				}
				blocks.put(p.key(), p.part());
				if (p.fill()) {
					for (int y = p.y() - 1; y > terrain.ground(p.x(), p.z()); y--) {
						blocks.putIfAbsent(Placement.key(p.x(), y, p.z()), VPart.FOUNDATION);
					}
				}
			}
		}

		VPart part(int x, int y, int z) {
			return blocks.get(Placement.key(x, y, z));
		}

		boolean obstacle(int x, int y, int z) {
			VPart p = part(x, y, z);
			if (p == null) {
				return y <= terrain.ground(x, z);
			}
			return (p.solid() && !p.passable()) || p == VPart.BED || p == VPart.BED_HEAD || p == VPart.LANTERN || p == VPart.FLOWER_POT;
		}

		boolean standable(int x, int y, int z) {
			VPart p = part(x, y, z);
			if (p == null) {
				return y <= terrain.ground(x, z);
			}
			return p.standable();
		}

		boolean feet(int x, int y, int z) {
			if (obstacle(x, y, z) || obstacle(x, y + 1, z)) {
				return false;
			}
			return standable(x, y - 1, z) || part(x, y, z) == VPart.LADDER || part(x, y - 1, z) == VPart.LADDER;
		}

		Set<Long> reach(int sx, int sy, int sz, int limit) {
			Set<Long> seen = new HashSet<>();
			Deque<int[]> queue = new ArrayDeque<>();
			if (feet(sx, sy, sz)) {
				seen.add(Placement.key(sx, sy, sz));
				queue.add(new int[] {sx, sy, sz});
			}
			while (!queue.isEmpty()) {
				int[] p = queue.poll();
				int x = p[0];
				int y = p[1];
				int z = p[2];
				for (Facing f : Facing.values()) {
					int nx = x + f.dx;
					int nz = z + f.dz;
					if (Math.abs(nx - sx) > limit || Math.abs(nz - sz) > limit) {
						continue;
					}
					if (feet(nx, y, nz)) {
						visit(seen, queue, nx, y, nz);
					} else if (!obstacle(x, y + 2, z) && feet(nx, y + 1, nz)) {
						visit(seen, queue, nx, y + 1, nz);
					} else if (!obstacle(nx, y, nz) && !obstacle(nx, y + 1, nz)) {
						for (int k = 1; k <= 3; k++) {
							if (feet(nx, y - k, nz)) {
								visit(seen, queue, nx, y - k, nz);
								break;
							}
							if (obstacle(nx, y - k, nz)) {
								break;
							}
						}
					}
				}
				if (part(x, y, z) == VPart.LADDER || part(x, y - 1, z) == VPart.LADDER) {
					if (feet(x, y + 1, z)) {
						visit(seen, queue, x, y + 1, z);
					}
				}
				if (part(x, y - 1, z) == VPart.LADDER && feet(x, y - 1, z)) {
					visit(seen, queue, x, y - 1, z);
				}
			}
			return seen;
		}

		private static void visit(Set<Long> seen, Deque<int[]> queue, int x, int y, int z) {
			if (seen.add(Placement.key(x, y, z))) {
				queue.add(new int[] {x, y, z});
			}
		}

		/** Blocos (nao-ar) sem ligacao com o chao pelos 6 vizinhos. */
		List<String> floating() {
			Set<Long> supported = new HashSet<>();
			Deque<Long> queue = new ArrayDeque<>();
			for (Map.Entry<Long, VPart> e : blocks.entrySet()) {
				if (e.getValue() == VPart.AIR) {
					continue;
				}
				int[] p = decode(e.getKey());
				if (p[1] - 1 <= terrain.ground(p[0], p[2]) && part(p[0], p[1] - 1, p[2]) == null) {
					supported.add(e.getKey());
					queue.add(e.getKey());
				}
			}
			int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
			while (!queue.isEmpty()) {
				int[] p = decode(queue.poll());
				for (int[] d : dirs) {
					long n = Placement.key(p[0] + d[0], p[1] + d[1], p[2] + d[2]);
					VPart part = blocks.get(n);
					if (part != null && part != VPart.AIR && supported.add(n)) {
						queue.add(n);
					}
				}
			}
			List<String> out = new ArrayList<>();
			blocks.forEach((k, v) -> {
				if (v != VPart.AIR && !supported.contains(k)) {
					int[] p = decode(k);
					out.add(v + "@" + p[0] + "," + p[1] + "," + p[2]);
				}
			});
			return out;
		}

		/** Detalhes que precisam de apoio especifico (lanterna, planta, escada...). */
		List<String> unsupported() {
			List<String> out = new ArrayList<>();
			blocks.forEach((k, v) -> {
				int[] p = decode(k);
				int x = p[0];
				int y = p[1];
				int z = p[2];
				boolean ok = switch (v) {
					case LANTERN, FLOWER_POT, CARPET, CARPET_ALT, BED, DOOR, CAMPFIRE, CHEST, WOOD_STAIRS, STONE_WALL -> standable(x, y - 1, z);
					case LANTERN_HANGING -> {
						VPart above = part(x, y + 1, z);
						yield above != null && above.solid();
					}
					case FLOWER, PLANT -> part(x, y - 1, z) == VPart.SOIL || (part(x, y - 1, z) == null && y - 1 == terrain.ground(x, z));
					case CROP_WHEAT, CROP_CARROT, CROP_POTATO, CROP_BEET -> part(x, y - 1, z) == VPart.FARMLAND;
					case LADDER -> {
						Facing f = facingOf(k);
						VPart behind = f == null ? null : part(x - f.dx, y, z - f.dz);
						yield behind != null && behind.solid();
					}
					default -> true;
				};
				if (!ok) {
					out.add(v + "@" + x + "," + y + "," + z);
				}
			});
			return out;
		}

		final Map<Long, Facing> facing = new HashMap<>();

		Facing facingOf(long key) {
			return facing.get(key);
		}

		void addWithFacing(List<Placement> placements) {
			add(placements);
			for (Placement p : placements) {
				if (p.dir() != null) {
					facing.put(p.key(), p.dir());
				}
			}
		}

		static int[] decode(long key) {
			return new int[] {(int) (key >>> 40) - 2048, (int) ((key >>> 20) & 0xFFFFF) - 2048, (int) (key & 0xFFFFF) - 2048};
		}
	}

	private static VillageState village(VillageLevel level, VillageStyle style) {
		VillageState v = new VillageState("minecraft:overworld", 0, 64, 0, 1234L, style, "Teste");
		v.setLevel(level);
		return v;
	}

	/** Planeja e "constroi" no terreno falso, guardando o lote. */
	private static VillagePlanner.Planned build(VillageProject project, VillageState v, FakeTerrain terrain, Voxels voxels, long seed) {
		VillagePlanner.Planned planned = VillagePlanner.plan(project, v, terrain, seed);
		if (planned != null) {
			if (planned.lot() != null) {
				v.addLot(planned.lot().rect());
			}
			if (planned.lotId() >= 0) {
				v.layout().lot(planned.lotId()).setBuilt(true);
			}
			terrain.apply(planned.placements());
			voxels.addWithFacing(planned.placements());
			v.addBuilt(project);
		}
		return planned;
	}

	@Test
	void rotationKeepsFootprintAndFacesTheCenter() {
		for (Facing f : Facing.values()) {
			int rot = VillagePlanner.rotationFor(f);
			assertEquals(f, Facing.SOUTH.rotate(rot));
		}
		// os 4 cantos de uma planta 9x11 caem dentro do retangulo girado
		for (int rot = 0; rot < 4; rot++) {
			int w = rot % 2 == 0 ? 9 : 11;
			int d = rot % 2 == 0 ? 11 : 9;
			for (int[] c : new int[][] {{0, 0}, {8, 0}, {0, 10}, {8, 10}}) {
				int[] r = VillagePlanner.rotate(c[0], c[1], 9, 11, rot);
				assertTrue(r[0] >= 0 && r[0] < w && r[1] >= 0 && r[1] < d, "giro " + rot);
			}
		}
	}

	@Test
	void everyPlanStaysInsideItsFootprint() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : List.of(VillageProject.HOUSE, VillageProject.FARM, VillageProject.MARKET, VillageProject.SMITHY,
					VillageProject.INN, VillageProject.LIBRARY, VillageProject.PLAZA)) {
				for (Canvas c : Buildings.variants(project, style)) {
					for (long k : c.cells().keySet()) {
						int x = Canvas.keyX(k);
						int z = Canvas.keyZ(k);
						assertTrue(x >= 0 && x < c.w() && z >= 0 && z < c.d(), project + " " + style + " fora do lote: " + x + "," + z);
					}
					assertTrue(c.doorX() >= 0 && c.doorX() < c.w());
				}
			}
		}
	}

	@Test
	void grownVillageHasNoOverlapsAndEverythingIsReachableAndSupported() {
		for (VillageStyle style : VillageStyle.values()) {
			FakeTerrain terrain = new FakeTerrain((x, z) -> 64);
			Voxels voxels = new Voxels(terrain);
			VillageState v = village(VillageLevel.CAPITAL, style);
			List<VillagePlanner.Planned> plans = new ArrayList<>();
			List<VillageProject> order = List.of(VillageProject.PLAZA, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.FARM,
					VillageProject.HOUSE, VillageProject.SMITHY, VillageProject.MARKET, VillageProject.HOUSE, VillageProject.INN,
					VillageProject.LIBRARY, VillageProject.FARM, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.LAMPS,
					VillageProject.WALL);
			long seed = 7;
			for (VillageProject project : order) {
				VillagePlanner.Planned planned = build(project, v, terrain, voxels, seed++);
				assertNotNull(planned, style + ": nao coube " + project + " (obra " + plans.size() + ")");
				plans.add(planned);
			}
			// lotes sem sobreposicao (com folga para o caminho), longe da praca e dentro do raio
			List<VillageState.Rect> lots = v.lots();
			for (int i = 0; i < lots.size(); i++) {
				for (int j = i + 1; j < lots.size(); j++) {
					assertFalse(lots.get(i).overlaps(lots.get(j), 1), style + ": lotes " + i + " e " + j + " encostam");
				}
				VillageState.Rect r = lots.get(i);
				assertFalse(r.contains(0, 0), "lote em cima do quadro");
				for (int[] c : new int[][] {{r.x0(), r.z0()}, {r.x1(), r.z0()}, {r.x0(), r.z1()}, {r.x1(), r.z1()}}) {
					assertTrue(c[0] * c[0] + c[1] * c[1] <= VillageLevel.CAPITAL.radius() * VillageLevel.CAPITAL.radius());
				}
			}
			// cada morador nasce num lugar alcancavel a pe a partir da porta
			for (VillagePlanner.Planned planned : plans) {
				if (planned.lot() == null || planned.spawns().isEmpty()) {
					continue;
				}
				VillagePlanner.Lot lot = planned.lot();
				int[] start = doorOutside(planned, terrain);
				Set<Long> reach = voxels.reach(start[0], start[1], start[2], 30);
				assertFalse(reach.isEmpty(), style + " " + planned.project() + ": saida da porta bloqueada em " + start[0] + "," + start[1] + "," + start[2]);
				for (int[] s : planned.spawns()) {
					assertTrue(reach.contains(Placement.key(s[0], s[1], s[2])), style + " " + planned.project() + " lote " + lot
							+ ": morador em " + s[0] + "," + s[1] + "," + s[2] + " inalcancavel");
				}
			}
			assertEquals(List.of(), voxels.floating(), style + ": blocos soltos no ar");
			assertEquals(List.of(), voxels.unsupported(), style + ": detalhes sem apoio");
		}
	}

	/** Pes na saida da porta (fora do lote, na frente). */
	private static int[] doorOutside(VillagePlanner.Planned planned, Terrain terrain) {
		VillagePlanner.Lot lot = planned.lot();
		Canvas canvas = Buildings.plan(planned.project(), VillageStyle.PLAINS, 0);
		// a planta exata depende da semente; o caminho comeca na celula logo fora do lote, na frente
		for (Placement p : planned.placements()) {
			if (p.part() == VPart.PATH && !lot.rect().contains(p.x(), p.z())) {
				boolean adjacent = Math.max(Math.abs(Math.max(lot.x(), Math.min(p.x(), lot.x() + lot.w() - 1)) - p.x()),
						Math.abs(Math.max(lot.z(), Math.min(p.z(), lot.z() + lot.d() - 1)) - p.z())) == 1;
				if (adjacent) {
					return new int[] {p.x(), terrain.ground(p.x(), p.z()) + 1, p.z()};
				}
			}
		}
		return new int[] {lot.x() + canvas.doorX(), lot.groundY() + 1, lot.z() + lot.d()};
	}

	@Test
	void neverTouchesPlayerColumns() {
		FakeTerrain terrain = new FakeTerrain((x, z) -> 64);
		Random random = new Random(3);
		// "construcoes de jogador" espalhadas
		for (int i = 0; i < 400; i++) {
			terrain.blocked.add(FakeTerrain.col(random.nextInt(100) - 50, random.nextInt(100) - 50));
		}
		Set<Long> blocked = Set.copyOf(terrain.blocked);
		Voxels voxels = new Voxels(terrain);
		VillageState v = village(VillageLevel.CAPITAL, VillageStyle.PLAINS);
		int built = 0;
		long seed = 1;
		for (VillageProject project : List.of(VillageProject.PLAZA, VillageProject.HOUSE, VillageProject.FARM, VillageProject.HOUSE,
				VillageProject.SMITHY, VillageProject.MARKET, VillageProject.LAMPS, VillageProject.WALL)) {
			VillagePlanner.Planned planned = build(project, v, terrain, voxels, seed++);
			if (planned == null) {
				continue;
			}
			built++;
			for (Placement p : planned.placements()) {
				assertFalse(blocked.contains(FakeTerrain.col(p.x(), p.z())), project + " mexeu na coluna de jogador " + p.x() + "," + p.z());
			}
		}
		assertTrue(built >= 6, "mesmo com obstaculos a vila deve crescer (" + built + ")");
	}

	@Test
	void slopedGroundIsLeveledALittleAndFoundationsReachTheGround() {
		FakeTerrain terrain = new FakeTerrain((x, z) -> 64 + Math.floorDiv(x, 6) + Math.floorDiv(z, 9));
		Voxels voxels = new Voxels(terrain);
		VillageState v = village(VillageLevel.TOWN, VillageStyle.TAIGA);
		for (VillageProject project : List.of(VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.FARM, VillageProject.INN)) {
			VillagePlanner.Planned planned = build(project, v, terrain, voxels, project.ordinal() * 31L + v.lots().size());
			assertNotNull(planned, "nao coube " + project + " na encosta");
			VillagePlanner.Lot lot = planned.lot();
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			for (int x = lot.x(); x < lot.x() + lot.w(); x++) {
				for (int z = lot.z(); z < lot.z() + lot.d(); z++) {
					min = Math.min(min, terrain.ground(x, z));
					max = Math.max(max, terrain.ground(x, z));
				}
			}
			assertTrue(max - min <= VillageLayout.MAX_SPREAD, "desnivel " + (max - min));
		}
		assertEquals(List.of(), voxels.floating());
	}

	@Test
	void waterOrFullGroundGivesNoLot() {
		FakeTerrain terrain = new FakeTerrain((x, z) -> 64);
		for (int x = -60; x <= 60; x++) {
			for (int z = -60; z <= 60; z++) {
				if (x * x + z * z > 4) {
					terrain.blocked.add(FakeTerrain.col(x, z));
				}
			}
		}
		VillageState v = village(VillageLevel.CAPITAL, VillageStyle.PLAINS);
		assertNull(VillagePlanner.plan(VillageProject.HOUSE, v, terrain, 1));
		assertNull(VillagePlanner.plan(VillageProject.LAMPS, v, terrain, 1));
	}

	@Test
	void wallIsAClosedRingOutsideEveryLot() {
		int cells = 0;
		for (int x = -60; x <= 60; x++) {
			for (int z = -60; z <= 60; z++) {
				if (VillagePlanner.wallCell(x, z)) {
					cells++;
					assertTrue(x * x + z * z > VillageLevel.CAPITAL.radius() * VillageLevel.CAPITAL.radius());
					// anel ligado: cada celula tem pelo menos 2 vizinhas do anel (8 direcoes)
					int n = 0;
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if ((dx != 0 || dz != 0) && VillagePlanner.wallCell(x + dx, z + dz)) {
								n++;
							}
						}
					}
					assertTrue(n >= 2, "furo no anel em " + x + "," + z);
				}
			}
		}
		assertTrue(cells > 300);
	}
}
