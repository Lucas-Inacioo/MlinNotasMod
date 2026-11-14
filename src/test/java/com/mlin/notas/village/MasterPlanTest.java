package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plano diretor: ruas curvas de canto chanfrado, quarteiroes, lotes, zonas, alturas e areas de jogador. */
class MasterPlanTest {
	static final List<IntBinaryOperator> TERRAINS = List.of(
			(x, z) -> 64,
			(x, z) -> 64 + Math.floorDiv(x, 3),
			(x, z) -> 64 + (int) Math.round(6 * Math.sin(x / 13.0) + 5 * Math.cos(z / 11.0)),
			(x, z) -> 64 + Math.floorDiv(x + z, 5) + (int) Math.round(3 * Math.sin(z / 7.0)));

	static MasterPlan plan(VillagePlannerTest.FakeTerrain terrain, long seed) {
		return MasterPlan.create(seed, terrain, terrain.ground(0, 0), List.of(), List.of(), Set.of(), 0);
	}

	/** Celulas de rua de chao alcancaveis a pe a partir da praca (degrau de no maximo 1). */
	static Set<Long> walk(MasterPlan plan) {
		Set<Long> seen = new java.util.HashSet<>();
		ArrayDeque<long[]> queue = new ArrayDeque<>();
		int e = plan.extent();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (plan.ground(x, z) && Math.abs(plan.streetHeight(x, z) - plan.plazaY()) <= 1) {
					for (Facing f : Facing.values()) {
						if (plan.plaza(x + f.dx, z + f.dz)) {
							seen.add(VillageLayout.col(x, z));
							queue.add(new long[] {x, z});
							break;
						}
					}
				}
			}
		}
		while (!queue.isEmpty()) {
			long[] p = queue.poll();
			int x = (int) p[0];
			int z = (int) p[1];
			for (Facing f : Facing.values()) {
				int nx = x + f.dx;
				int nz = z + f.dz;
				if (plan.ground(nx, nz) && Math.abs(plan.streetHeight(nx, nz) - plan.streetHeight(x, z)) <= 1 && seen.add(VillageLayout.col(nx, nz))) {
					queue.add(new long[] {nx, nz});
				}
			}
		}
		return seen;
	}

	static void dump(MasterPlan plan, String name) {
		StringBuilder sb = new StringBuilder();
		int e = plan.extent();
		for (int z = -e; z <= e; z++) {
			for (int x = -e; x <= e; x++) {
				int c = plan.code(x, z);
				MasterPlan.Parcel p = plan.parcelAt(x, z);
				char ch;
				if (c == MasterPlan.OUT) {
					ch = ' ';
				} else if (c == MasterPlan.PLAZA) {
					ch = '@';
				} else if (c >= MasterPlan.STREET && c < MasterPlan.BLOCK) {
					int k = plan.streetKind(x, z);
					ch = c >= MasterPlan.BRIDGE ? '~' : k == StreetPlanner.AVENUE ? '#' : k == StreetPlanner.RING ? '=' : k == StreetPlanner.LANE ? '+' : 'o';
				} else if (c == MasterPlan.CARVED) {
					ch = 'X';
				} else if (c == MasterPlan.LAKE) {
					ch = 'w';
				} else if (p != null) {
					VillageState.Rect r = p.rect();
					boolean edge = x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1();
					ch = edge ? Character.toLowerCase(p.size().name().charAt(0)) : '.';
				} else {
					ch = ',';
				}
				sb.append(ch);
			}
			sb.append('\n');
		}
		try {
			Path out = Path.of("build", "vilas2-" + name + ".txt");
			Files.createDirectories(out.getParent());
			Files.writeString(out, sb.toString());
		} catch (IOException ignored) {
			// so para olhar
		}
	}

	@Test
	void samePlanForTheSameSeed() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(TERRAINS.get(2));
		MasterPlan a = plan(t, 77L);
		MasterPlan b = plan(t, 77L);
		assertArrayEquals(a.gridRle(), b.gridRle());
		assertArrayEquals(a.streetHeights(), b.streetHeights());
		assertEquals(a.parcels().size(), b.parcels().size());
		for (int i = 0; i < a.parcels().size(); i++) {
			assertEquals(a.parcels().get(i).rect(), b.parcels().get(i).rect());
		}
		// e o formato salvo volta igual
		MasterPlan c = MasterPlan.restore(a.seed(), a.extent(), a.plazaY(), a.gridRle(), a.ringRle(), a.streetHeights(), a.blocks(), a.parcels(),
				a.playerAreas(), a.fixedAreas());
		assertArrayEquals(a.gridRle(), c.gridRle());
		assertArrayEquals(a.streetHeights(), c.streetHeights());
	}

	@Test
	void blocksLotsAndStreetsDoNotOverlapAndEveryLotFacesAWalkableStreet() {
		long seed = 11;
		for (IntBinaryOperator h : TERRAINS) {
			VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(h);
			long start = System.nanoTime();
			MasterPlan plan = plan(t, seed);
			long ms = (System.nanoTime() - start) / 1_000_000;
			dump(plan, "terrain" + TERRAINS.indexOf(h));
			assertTrue(ms < 8000, "plano lento: " + ms + " ms");
			Set<Long> walk = walk(plan);
			int[] perRing = new int[7];
			for (MasterPlan.Parcel p : plan.parcels()) {
				VillageState.Rect r = p.rect();
				MasterPlan.Block block = plan.block(p.block());
				perRing[block.ring()]++;
				assertEquals(Zone.ofRing(block.ring()), p.zone());
				for (int x = r.x0(); x <= r.x1(); x++) {
					for (int z = r.z0(); z <= r.z1(); z++) {
						assertEquals(MasterPlan.BLOCK + block.id(), plan.code(x, z), "lote fora do quarteirao em " + x + "," + z);
					}
				}
				for (MasterPlan.Parcel q : plan.parcels()) {
					if (q != p) {
						assertFalse(r.overlaps(q.rect(), 0), "lotes encostados " + p.id() + " e " + q.id());
					}
				}
				// porta no meio da frente: rua de chao a ate 2, alcancavel a pe da praca e a no maximo 1 do plato
				int[] door = doorCell(p);
				boolean ok = false;
				for (int k = 1; k <= BlockSubdivider.ACCESS_MAX && !ok; k++) {
					int sx = door[0] + p.front().dx * k;
					int sz = door[1] + p.front().dz * k;
					if (plan.ground(sx, sz)) {
						assertTrue(walk.contains(VillageLayout.col(sx, sz)), "rua do lote " + p.id() + " nao chega na praca a pe");
						assertTrue(Math.abs(plan.streetHeight(sx, sz) - block.target()) <= 1, "degrau entre o lote " + p.id() + " e a rua");
						ok = true;
					}
				}
				assertTrue(ok, "lote " + p.id() + " sem rua na frente");
			}
			System.out.println("lotes por anel: " + java.util.Arrays.toString(perRing));
			assertTrue(perRing[2] >= 6, "poucos lotes no centro: " + perRing[2]);
			assertTrue(perRing[3] >= 10, "poucos lotes nos bairros: " + perRing[3]);
			assertTrue(perRing[5] >= 8, "poucos lotes nos arredores: " + perRing[5]);
			seed += 7;
		}
	}

	static int[] doorCell(MasterPlan.Parcel p) {
		VillageState.Rect r = p.rect();
		int along = p.along();
		return switch (p.front()) {
			case SOUTH -> new int[] {r.x0() + along / 2, r.z1()};
			case NORTH -> new int[] {r.x0() + along / 2, r.z0()};
			case EAST -> new int[] {r.x1(), r.z0() + along / 2};
			case WEST -> new int[] {r.x0(), r.z0() + along / 2};
		};
	}

	@Test
	void streetsRampAtMostOnePerBlockAndBlocksStayWithinFourOfEachOther() {
		for (IntBinaryOperator h : TERRAINS) {
			VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(h);
			MasterPlan plan = plan(t, 5);
			int e = plan.extent();
			Map<Long, Integer> pairs = new HashMap<>();
			for (int x = -e; x <= e; x++) {
				for (int z = -e; z <= e; z++) {
					if (!plan.ground(x, z)) {
						continue;
					}
					int sh = plan.streetHeight(x, z);
					for (Facing f : Facing.values()) {
						int nx = x + f.dx;
						int nz = z + f.dz;
						if (plan.ground(nx, nz)) {
							assertTrue(Math.abs(plan.streetHeight(nx, nz) - sh) <= 1, "rampa de " + Math.abs(plan.streetHeight(nx, nz) - sh) + " na rua em "
									+ x + "," + z);
						}
						MasterPlan.Block b = plan.blockAt(nx, nz);
						if (b != null) {
							assertTrue(Math.abs(b.target() - sh) <= 1, "rua a " + Math.abs(b.target() - sh) + " do plato em " + x + "," + z);
						}
						if (plan.plaza(nx, nz)) {
							assertTrue(Math.abs(plan.plazaY() - sh) <= 1, "degrau na praca");
						}
					}
				}
			}
			// quarteiroes vizinhos (encostados ou do outro lado da rua): no maximo 4 de diferenca
			for (int x = -e; x <= e; x++) {
				for (int z = -e; z <= e; z++) {
					MasterPlan.Block a = plan.blockAt(x, z);
					if (a == null) {
						continue;
					}
					// encostado, ou do outro lado da rua (so rua no meio, ate 6)
					for (Facing f : Facing.values()) {
						for (int k = 1; k <= 7; k++) {
							int nx = x + f.dx * k;
							int nz = z + f.dz * k;
							MasterPlan.Block b = plan.blockAt(nx, nz);
							if (b != null) {
								if (b != a) {
									pairs.merge(((long) a.id() << 32) | b.id(), Math.abs(a.target() - b.target()), Math::max);
								}
								break;
							}
							if (!plan.ground(nx, nz)) {
								break;
							}
						}
					}
				}
			}
			pairs.forEach((k, d) -> assertTrue(d <= MasterPlan.MAX_STEP, "quarteiroes vizinhos com " + d + " de diferenca"));
		}
	}

	@Test
	void streetsAreStraightRunsWithChamferedCornersAndAvenuesCurveLittle() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(TERRAINS.get(2));
		MasterPlan plan = plan(t, 3);
		int e = plan.extent();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (!plan.street(x, z)) {
					continue;
				}
				int n = 0;
				for (Facing f : Facing.values()) {
					n += plan.street(x + f.dx, z + f.dz) || plan.plaza(x + f.dx, z + f.dz) ? 1 : 0;
				}
				assertTrue(n >= 2 || plan.code(x + 1, z) == MasterPlan.OUT || plan.code(x - 1, z) == MasterPlan.OUT
						|| plan.code(x, z + 1) == MasterPlan.OUT || plan.code(x, z - 1) == MasterPlan.OUT, "rua serrilhada (celula solta) em " + x + "," + z);
			}
		}
		// avenidas dos eixos: o desvio lateral nunca passa de 30 graus
		StreetPlanner.Field field = new StreetPlanner.Field() {
			@Override
			public int bed(int x, int z) {
				return t.bed(x, z);
			}

			@Override
			public int water(int x, int z) {
				return 0;
			}

			@Override
			public boolean blocked(int x, int z) {
				return false;
			}
		};
		StreetPlanner.Result r = StreetPlanner.plan(3, field, 2, 0);
		for (double[][] line : r.avenues) {
			double[] a = line[0];
			double[] b = line[line.length - 1];
			double cos = (a[0] * b[0] + a[1] * b[1]) / (Math.hypot(a[0], a[1]) * Math.hypot(b[0], b[1]));
			double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
			assertTrue(angle <= 30, "avenida desvia " + angle + " graus");
			for (int i = 0; i + 1 < line.length; i++) {
				double dx = Math.abs(line[i + 1][0] - line[i][0]);
				double dz = Math.abs(line[i + 1][1] - line[i][1]);
				assertTrue(dx < 1e-6 || dz < 1e-6 || Math.abs(dx - dz) < 1e-6, "trecho de avenida fora de 0/45 graus");
			}
		}
		assertTrue(r.avenues.size() >= 4 && r.avenues.size() <= 6);
	}

	@Test
	void playerAreasAreCutOutAndStreetsGoAroundThem() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		// casa do jogador em cima da avenida leste, no anel 3, e outra no meio de um quarteirao do anel 5
		for (int x = 36; x <= 42; x++) {
			for (int z = -3; z <= 3; z++) {
				t.blocked.add(VillagePlannerTest.FakeTerrain.col(x, z));
			}
		}
		for (int x = -60; x <= -55; x++) {
			for (int z = 20; z <= 25; z++) {
				t.blocked.add(VillagePlannerTest.FakeTerrain.col(x, z));
			}
		}
		MasterPlan plan = plan(t, 9);
		dump(plan, "player");
		assertEquals(2, plan.playerAreas().size(), "areas de jogador: " + plan.playerAreas());
		for (long c : t.blocked) {
			int x = VillageLayout.colX(c);
			int z = VillageLayout.colZ(c);
			assertEquals(MasterPlan.CARVED, plan.code(x, z), "coluna de jogador no plano em " + x + "," + z);
			assertTrue(plan.parcelAt(x, z) == null);
		}
		// a avenida leste continua passando: rua alcancavel a pe dos dois lados da casa
		Set<Long> walk = walk(plan);
		boolean inner = false;
		boolean outer = false;
		for (long k : walk) {
			int x = VillageLayout.colX(k);
			int z = VillageLayout.colZ(k);
			if (Math.abs(z) <= 2 && x > 30 && x < 34) {
				inner = true;
			}
			if (Math.abs(z) <= 2 && x > 46 && x < 52) {
				outer = true;
			}
		}
		assertTrue(inner && outer, "a avenida nao contorna a casa do jogador");
		// nenhuma coluna de jogador entra na terraplenagem
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			for (long c : plan.columns(ring)) {
				assertFalse(t.blocked.contains(VillagePlannerTest.FakeTerrain.col(VillageLayout.colX(c), VillageLayout.colZ(c))), "terraplenagem em coluna de jogador");
			}
		}
	}

	@Test
	void deepWaterBecomesLakeAndShallowWaterIsGraded() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		for (int x = 20; x <= 30; x++) {
			for (int z = 20; z <= 30; z++) {
				t.water.put(VillagePlannerTest.FakeTerrain.col(x, z), 2);
			}
		}
		for (int x = -60; x <= -40; x++) {
			for (int z = -60; z <= -40; z++) {
				t.water.put(VillagePlannerTest.FakeTerrain.col(x, z), 6);
			}
		}
		MasterPlan plan = plan(t, 2);
		for (int x = 20; x <= 30; x++) {
			for (int z = 20; z <= 30; z++) {
				assertTrue(plan.code(x, z) != MasterPlan.LAKE, "agua rasa virou lago");
			}
		}
		int lake = 0;
		for (int x = -58; x <= -42; x++) {
			for (int z = -58; z <= -42; z++) {
				lake += plan.code(x, z) == MasterPlan.LAKE || plan.code(x, z) >= MasterPlan.BRIDGE && plan.code(x, z) < MasterPlan.BLOCK ? 1 : 0;
				assertTrue(plan.parcelAt(x, z) == null, "lote em agua funda");
			}
		}
		assertTrue(lake > 100, "agua funda deveria virar lago/ponte: " + lake);
	}

	@Test
	void outskirtsGetSaplingsOutsideTheLots() {
		MasterPlan plan = plan(new VillagePlannerTest.FakeTerrain((x, z) -> 64), 4);
		int saplings = 0;
		for (long c : plan.columns(5)) {
			int x = VillageLayout.colX(c);
			int z = VillageLayout.colZ(c);
			if (TerrainWork.sapling(plan, x, z)) {
				saplings++;
				assertTrue(plan.parcelAt(x, z) == null && !plan.street(x, z));
			}
		}
		assertTrue(saplings >= 5, "mudas nos arredores: " + saplings);
	}

	@Test
	void everyBuildingTypeFitsItsLotClass() {
		for (VillageProject project : VillageLayout.LOT_PROJECTS) {
			LotRules.Rule rule = LotRules.of(project.id());
			for (VillageStyle style : VillageStyle.values()) {
				for (Canvas c : Buildings.variants(project, style)) {
					assertTrue(rule.size().fits(c.w(), c.d()), project + " " + style + " " + c.w() + "x" + c.d() + " nao cabe no lote " + rule.size());
				}
			}
		}
		assertTrue(SizeClass.L.holds(SizeClass.M));
		assertFalse(SizeClass.S.holds(SizeClass.M));
		assertTrue(LotRules.accepts(LotRules.of("farm"), Zone.CENTER, false, false), "plantacao no centro enquanto nao ha bairro");
		assertFalse(LotRules.accepts(LotRules.of("farm"), Zone.CENTER, true, true));
		assertFalse(LotRules.accepts(LotRules.of("market"), Zone.OUTSKIRTS, true, true));
	}
}
