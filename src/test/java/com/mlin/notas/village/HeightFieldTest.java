package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Campo de alturas da vila inicial (Vilas 3.0 fase 1): platos e rampas do plano, portoes planos, pinos exatos, lote do
 * jogador ligado a rua, transicao suave ate o natural, determinismo e formato salvo.
 */
class HeightFieldTest {
	static final int MIN_Y = 63;
	static final int E = HeightFieldBuilder.EXTENT;

	/** Terrenos sinteticos (coordenadas relativas ao centro da vila). */
	static Map<String, HeightFieldBuilder.Natural> terrains(long seed) {
		Random r = new Random(seed);
		double p1 = r.nextDouble() * 6.28;
		double p2 = r.nextDouble() * 6.28;
		double p3 = r.nextDouble() * 6.28;
		double ang = r.nextDouble() * 6.28;
		Map<String, HeightFieldBuilder.Natural> out = new LinkedHashMap<>();
		out.put("flat", (x, z) -> 70);
		out.put("slope", (x, z) -> 70 + (int) Math.floor((x * Math.cos(ang) + z * Math.sin(ang)) / 5.0));
		// montanha de um lado: sobe de 70 para ~150 na faixa de transicao
		out.put("mountain_side", (x, z) -> {
			double d = x * Math.cos(ang) + z * Math.sin(ang);
			return 70 + (int) Math.round(80 / (1 + Math.exp(-(d - 150) / 14.0)));
		});
		// vale de rio atravessando a vila
		out.put("river", (x, z) -> {
			double d = -x * Math.sin(ang) + z * Math.cos(ang) - 40;
			return 72 - (int) Math.round(11 * Math.exp(-d * d / 120.0));
		});
		// montanha de 45 blocos dentro da muralha de pedra
		out.put("mountain_inside", (x, z) -> {
			double dx = x - 28;
			double dz = z + 22;
			return 70 + (int) Math.round(45 * Math.exp(-(dx * dx + dz * dz) / (2 * 13.0 * 13.0)));
		});
		out.put("noisy", (x, z) -> 72 + (int) Math.round(6 * Math.sin(x / 13.0 + p1) + 5 * Math.cos(z / 11.0 + p2) + 3 * Math.sin((x + z) / 7.0 + p3)
				+ 2 * Math.sin((x - 2 * z) / 4.3)));
		return out;
	}

	static HeightFieldBuilder.Result build(HeightFieldBuilder.Natural natural, long seed) {
		int plaza = HeightFieldBuilder.plazaHeight(natural, MIN_Y);
		return HeightFieldBuilder.forNewVillage(seed, 0, 0, plaza, natural, MIN_Y, List.of(), FieldPins.none());
	}

	private static final Map<String, HeightFieldBuilder.Result> CACHE = new LinkedHashMap<>();

	static synchronized HeightFieldBuilder.Result cached(String name, long seed) {
		return CACHE.computeIfAbsent(name + "#" + seed, k -> build(terrains(seed).get(name), seed));
	}

	static final long[] SEEDS = {11, 2024, 77};

	// ---------------------------------------------------------------- dentro do plano

	@Test
	void newPlanHasTheRuralRingAndMatchesTheField() {
		for (long seed : SEEDS) {
			for (String name : terrains(seed).keySet()) {
				HeightFieldBuilder.Result r = cached(name, seed);
				MasterPlan plan = r.plan();
				HeightField f = r.field();
				assertTrue(plan.rural(), "plano novo ja vem com o anel rural");
				assertEquals(RuralRing.EXTENT, f.planExtent());
				int pe = plan.extent();
				for (int x = -pe; x <= pe; x++) {
					for (int z = -pe; z <= pe; z++) {
						int h = plan.plannedHeight(x, z);
						if (h != Integer.MIN_VALUE) {
							assertEquals(h, f.heightRel(x, z), name + " plano != campo em " + x + "," + z);
						}
					}
				}
				assertEquals(plan.plazaY(), f.heightRel(0, 0));
			}
		}
	}

	@Test
	void plateausRampsAndNeighbours() {
		for (long seed : SEEDS) {
			for (String name : terrains(seed).keySet()) {
				HeightField f = cached(name, seed).field();
				MasterPlan plan = cached(name, seed).plan();
				for (int x = -E; x < E; x++) {
					for (int z = -E; z < E; z++) {
						for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
							int nx = x + d[0];
							int nz = z + d[1];
							HeightField.Kind a = f.kindRel(x, z);
							HeightField.Kind b = f.kindRel(nx, nz);
							if (!a.inside() || !b.inside() || a == HeightField.Kind.WATER || b == HeightField.Kind.WATER || a == HeightField.Kind.KEEP
									|| b == HeightField.Kind.KEEP) {
								continue;
							}
							int diff = Math.abs(f.heightRel(x, z) - f.heightRel(nx, nz));
							MasterPlan.Block ba = plan.blockAt(x, z);
							MasterPlan.Block bb = plan.blockAt(nx, nz);
							if (ba != null && ba == bb) {
								assertEquals(0, diff, name + " plato nao e plano em " + x + "," + z);
							} else if (ba != null && bb != null) {
								assertTrue(diff <= MasterPlan.MAX_STEP, name + " quarteiroes vizinhos " + diff + " em " + x + "," + z);
							} else if ((a == HeightField.Kind.PLAZA) != (b == HeightField.Kind.PLAZA) && (ba != null || bb != null)) {
								assertTrue(diff <= 2, name + " praca/plato " + diff);
							} else {
								assertTrue(diff <= 1, name + " rampa " + diff + " em " + x + "," + z + " (" + a + "/" + b + ")");
							}
						}
					}
				}
				// quarteiroes ligados por pouca rua tambem ficam a no maximo 4
				for (MasterPlan.Block a : plan.blocks()) {
					for (MasterPlan.Block b : plan.blocks()) {
						if (a.id() < b.id() && closeByStreet(plan, a, b)) {
							assertTrue(Math.abs(a.target() - b.target()) <= MasterPlan.MAX_STEP, name + " quarteiroes " + a.id() + "/" + b.id());
						}
					}
				}
			}
		}
	}

	/** Maior degrau do chao natural numa janela de raio {@code r} (onde o natural ja e ingreme, o campo pode seguir). */
	static int naturalNear(int[] n, int x, int z, int r) {
		int max = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int ax = x + dx;
				int az = z + dz;
				if (Math.abs(ax) >= E || Math.abs(az) >= E) {
					continue;
				}
				int h = n[HeightFieldBuilder.fidx(ax, az)];
				max = Math.max(max, Math.max(Math.abs(h - n[HeightFieldBuilder.fidx(ax + 1, az)]), Math.abs(h - n[HeightFieldBuilder.fidx(ax, az + 1)])));
			}
		}
		return max;
	}

	/** Dois quarteiroes com frentes a ate 3 celulas de rua (a avenida tem 5). */
	private static boolean closeByStreet(MasterPlan plan, MasterPlan.Block a, MasterPlan.Block b) {
		int e = plan.extent();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (plan.blockAt(x, z) != a) {
					continue;
				}
				for (Facing f : Facing.values()) {
					for (int k = 1; k <= 6; k++) {
						int nx = x + f.dx * k;
						int nz = z + f.dz * k;
						if (plan.blockAt(nx, nz) == b) {
							boolean street = true;
							for (int m = 1; m < k; m++) {
								street &= plan.ground(x + f.dx * m, z + f.dz * m);
							}
							if (street) {
								return true;
							}
						}
						if (!plan.ground(nx, nz)) {
							break;
						}
					}
				}
			}
		}
		return false;
	}

	@Test
	void aMountainInsideTheWallBecomesALowHill() {
		for (long seed : SEEDS) {
			HeightField f = cached("mountain_inside", seed).field();
			int max = Integer.MIN_VALUE;
			for (int x = -50; x <= 50; x++) {
				for (int z = -50; z <= 50; z++) {
					if (VillagePlanner.insideWall(x, z)) {
						max = Math.max(max, f.heightRel(x, z));
					}
				}
			}
			int rise = max - f.plazaY();
			assertTrue(rise <= HeightFieldBuilder.AMP_INNER + 2, "morro de " + rise + " (natural sobe 45)");
			assertTrue(rise >= 2, "o relevo ainda aparece (" + rise + ")");
		}
	}

	@Test
	void theVillageFollowsTheMacroTilt() {
		HeightFieldBuilder.Natural slope = (x, z) -> 70 + Math.floorDiv(x, 5);
		HeightField f = build(slope, 5).field();
		assertTrue(f.heightRel(60, 0) > f.heightRel(-60, 0) + 6, "a vila acompanha a inclinacao");
	}

	@Test
	void neverBelowSeaLevelInside() {
		HeightFieldBuilder.Natural low = (x, z) -> 60 + Math.floorDiv(x, 4);
		HeightField f = build(low, 9).field();
		for (int x = -E; x <= E; x++) {
			for (int z = -E; z <= E; z++) {
				if (f.kindRel(x, z).inside()) {
					assertTrue(f.heightRel(x, z) >= MIN_Y, "abaixo do mar em " + x + "," + z);
				}
			}
		}
	}

	// ---------------------------------------------------------------- portoes

	@Test
	void gatesAreFlatThreeBlocksEachSideAndLeaveHeadroom() {
		for (long seed : SEEDS) {
			for (String name : terrains(seed).keySet()) {
				HeightField f = cached(name, seed).field();
				int stone = 0;
				int outer = 0;
				for (HeightField.Gate g : f.gates()) {
					if (g.wall() == HeightField.Wall.STONE) {
						stone++;
					} else {
						outer++;
					}
					for (int x = g.x0(); x <= g.x1(); x++) {
						for (int z = g.z0(); z <= g.z1(); z++) {
							if (f.kindRel(x, z).gate()) {
								assertEquals(g.level(), f.heightRel(x, z), name + " portao nao e plano");
							}
						}
					}
				}
				assertTrue(stone >= 4, name + " portoes na muralha de pedra: " + stone);
				assertTrue(outer >= 4, name + " portoes na muralha externa: " + outer);
				for (HeightField.Wall wall : HeightField.Wall.values()) {
					StreetPlanner.Octagon oct = HeightField.wallOctagon(wall);
					for (HeightField.WallPoint p : f.wallPoints(wall)) {
						if (!p.gate()) {
							continue;
						}
						// 3 blocos para dentro e para fora pela normal: rua do portao na mesma altura
						int[] n = StreetPlanner.SIDES[oct.side(p.x(), p.z())];
						for (int k = -HeightFieldBuilder.GATE_HALF; k <= HeightFieldBuilder.GATE_HALF; k++) {
							int x = p.x() + n[0] * k;
							int z = p.z() + n[1] * k;
							if (n[0] != 0 && n[1] != 0 && Math.abs(k) == HeightFieldBuilder.GATE_HALF) {
								continue; // na diagonal 3 passos sao 4,2 blocos
							}
							assertTrue(f.kindRel(x, z).gate(), name + " " + wall + " portao curto em " + x + "," + z);
							assertEquals(p.ground(), f.heightRel(x, z), name + " portao com degrau em " + x + "," + z);
						}
						// os pilares (celulas da muralha encostadas no portao) ficam a no maximo 1: o arco em level + 5
						// deixa 4 livres em cima da rua e ainda fica 4 acima do chao do pilar
						for (Facing fc : Facing.values()) {
							int x = p.x() + fc.dx;
							int z = p.z() + fc.dz;
							if (HeightField.onWallLine(wall, x, z) && !f.kindRel(x, z).gate()) {
								assertTrue(Math.abs(f.heightRel(x, z) - p.ground()) <= 1, name + " pilar " + x + "," + z);
							}
						}
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- transicao

	@Test
	void transitionIsSmoothAndEndsOnTheNaturalGround() {
		for (long seed : SEEDS) {
			for (Map.Entry<String, HeightFieldBuilder.Natural> e : terrains(seed).entrySet()) {
				String name = e.getKey();
				HeightField f = cached(name, seed).field();
				int[] n = HeightFieldBuilder.grid(e.getValue());
				boolean steep = name.startsWith("mountain_side");
				int bandCells = 0;
				int bad = 0;
				int worse = 0;
				for (int x = -E; x <= E; x++) {
					for (int z = -E; z <= E; z++) {
						HeightField.Kind k = f.kindRel(x, z);
						if (k == HeightField.Kind.NATURAL) {
							assertEquals(n[HeightFieldBuilder.fidx(x, z)], f.heightRel(x, z), name + " fora da faixa != natural");
							assertEquals(0.0F, f.weightRel(x, z));
							assertEquals(0, f.depthRel(x, z));
						} else if (k.inside()) {
							assertEquals(1.0F, f.weightRel(x, z));
						}
						if (k != HeightField.Kind.BAND) {
							continue;
						}
						bandCells++;
						for (int[] d : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
							int nx = x + d[0];
							int nz = z + d[1];
							if (Math.abs(nx) > E || Math.abs(nz) > E) {
								continue;
							}
							int step = Math.abs(f.heightRel(x, z) - f.heightRel(nx, nz));
							int natural = Math.abs(n[HeightFieldBuilder.fidx(x, z)] - n[HeightFieldBuilder.fidx(nx, nz)]);
							int allowed = Math.max(steep ? 2 : 1, naturalNear(n, x, z, 2));
							if (step > allowed) {
								bad++;
								System.out.println("[faixa] " + name + "#" + seed + " " + x + "," + z + ": " + f.heightRel(x, z) + "/" + f.heightRel(nx, nz));
							}
							if (step > Math.max(1, natural)) {
								worse++;
							}
						}
					}
				}
				System.out.println("[faixa] " + name + "#" + seed + " degraus acima do permitido: " + bad + " (" + worse + " acima de max(1, natural))");
				assertTrue(bandCells > 10000, name + " faixa de transicao existe");
				assertEquals(0, bad, name + "#" + seed + " degraus de transicao acima de 1 (ou do natural)");
				if (!steep) {
					assertTrue(worse <= 4, name + "#" + seed + " degraus acima do natural: " + worse);
				}
			}
		}
	}

	@Test
	void transitionIsMonotoneTowardsAMountain() {
		for (long seed : SEEDS) {
			HeightField f = cached("mountain_side", seed).field();
			Random r = new Random(seed);
			r.nextDouble();
			r.nextDouble();
			r.nextDouble();
			double ang = r.nextDouble() * 6.28;
			// raio em direcao a montanha, da borda do plano ate o fim da faixa
			int prev = Integer.MIN_VALUE;
			for (int d = 100; d <= 168; d++) {
				int x = (int) Math.round(d * Math.cos(ang));
				int z = (int) Math.round(d * Math.sin(ang));
				if (Math.abs(x) > E || Math.abs(z) > E || f.kindRel(x, z) != HeightField.Kind.BAND) {
					continue;
				}
				int h = f.heightRel(x, z);
				assertTrue(prev == Integer.MIN_VALUE || h >= prev - 1, "transicao desce no caminho da montanha em " + d + ": " + prev + " -> " + h);
				prev = Math.max(prev, h);
			}
		}
	}

	@Test
	void weightFallsMonotonicallyAlongRays() {
		HeightField f = cached("noisy", 11).field();
		for (int a = 0; a < 32; a++) {
			double ang = a * Math.PI / 16;
			float prev = 2;
			int depthPrev = 99;
			for (int d = 0; d <= E; d++) {
				int x = (int) Math.round(d * Math.cos(ang));
				int z = (int) Math.round(d * Math.sin(ang));
				if (Math.abs(x) > E || Math.abs(z) > E) {
					break;
				}
				float w = f.weightRel(x, z);
				assertTrue(w <= prev + 1e-3, "peso subiu em " + x + "," + z);
				prev = w;
				int depth = f.depthRel(x, z);
				if (f.kindRel(x, z) != HeightField.Kind.WATER && f.kindRel(x, z) != HeightField.Kind.KEEP) {
					assertTrue(depth <= depthPrev, "profundidade subiu");
					depthPrev = depth;
				}
			}
			if (a % 8 == 0) {
				assertEquals(0.0F, prev, "fim do raio (nos eixos) fora da faixa");
			}
		}
		assertEquals(HeightFieldBuilder.DEPTH_INSIDE, f.depthRel(0, 0));
	}

	// ---------------------------------------------------------------- pinos e lote do jogador

	@Test
	void pinsAreHonouredWithGentleRampsAndThePlotReachesAStreet() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(2));
		MasterPlan plan = RuralRingTest.expanded(t, 41);
		HeightFieldBuilder.Natural natural = (x, z) -> MasterPlanTest.TERRAINS.get(2).applyAsInt(x, z);
		// lote do jogador no meio de um quarteirao do anel 3 (longe da rua) e um pino solto no anel rural
		MasterPlan.Block block = null;
		for (MasterPlan.Block b : plan.blocks()) {
			if (b.ring() == 3 && b.cells() > 300) {
				block = b;
				break;
			}
		}
		assertNotNull(block);
		int[] interior = interior(plan, block, 3);
		assertNotNull(interior, "quarteirao com miolo longe da rua");
		VillageState.Rect plotRect = new VillageState.Rect(interior[0] - 1, interior[1] - 1, interior[0] + 1, interior[1] + 1);
		int plotY = block.target() + 3;
		FieldPins pins = new FieldPins().plot(plotRect, plotY);
		pins.pinRect(new VillageState.Rect(90, -4, 92, 4), plan.plazaY() - 2);
		HeightField f = HeightFieldBuilder.forPlan(plan, 0, 0, natural, MIN_Y, pins);
		for (Map.Entry<Long, Integer> e : pins.all().entrySet()) {
			int x = VillageLayout.colX(e.getKey());
			int z = VillageLayout.colZ(e.getKey());
			assertEquals(e.getValue(), f.heightRel(x, z), "pino em " + x + "," + z);
			assertTrue(f.pinnedRel(x, z));
			for (Facing fc : Facing.values()) {
				int nx = x + fc.dx;
				int nz = z + fc.dz;
				if (pins.at(nx, nz) == null && f.kindRel(nx, nz) != HeightField.Kind.WATER) {
					assertTrue(Math.abs(f.heightRel(nx, nz) - e.getValue()) <= 1, "rampa ate o pino em " + nx + "," + nz);
				}
			}
		}
		assertTrue(walkable(f, plotRect), "lote do jogador sem caminho andavel ate a rua");
	}

	/** Celula do quarteirao a pelo menos {@code margin} de qualquer celula que nao seja dele. */
	private static int[] interior(MasterPlan plan, MasterPlan.Block block, int margin) {
		int e = plan.extent();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				boolean ok = true;
				for (int dx = -margin; dx <= margin && ok; dx++) {
					for (int dz = -margin; dz <= margin && ok; dz++) {
						ok = plan.blockAt(x + dx, z + dz) == block;
					}
				}
				if (ok) {
					return new int[] {x, z};
				}
			}
		}
		return null;
	}

	/** Do lote ate uma rua/portao/praca com degraus de no maximo 1. */
	static boolean walkable(HeightField f, VillageState.Rect rect) {
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (int x = rect.x0(); x <= rect.x1(); x++) {
			for (int z = rect.z0(); z <= rect.z1(); z++) {
				queue.add(new int[] {x, z});
				seen.add(VillageLayout.col(x, z));
			}
		}
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			HeightField.Kind k = f.kindRel(c[0], c[1]);
			if (k == HeightField.Kind.STREET || k.gate() || k == HeightField.Kind.PLAZA) {
				return true;
			}
			for (Facing fc : Facing.values()) {
				int nx = c[0] + fc.dx;
				int nz = c[1] + fc.dz;
				if (!f.inRel(nx, nz) || !f.kindRel(nx, nz).inside() || Math.abs(f.heightRel(nx, nz) - f.heightRel(c[0], c[1])) > 1
						|| !seen.add(VillageLayout.col(nx, nz))) {
					continue;
				}
				queue.add(new int[] {nx, nz});
			}
		}
		return false;
	}

	@Test
	void existingPlanKeepsLotsAndHeightsAndPinsPreparedRings() {
		for (long seed : new long[] {3, 19}) {
			VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(3));
			// como Kalanros: plano de antes ja expandido com o anel rural, aneis 1..5 aplainados
			MasterPlan plan = RuralRingTest.expanded(t, seed);
			for (int ring = 1; ring <= 5; ring++) {
				plan.setRingPrepared(ring);
			}
			List<String> parcels = new ArrayList<>();
			for (MasterPlan.Parcel p : plan.parcels()) {
				parcels.add(p.id() + ":" + p.block() + ":" + p.rect() + ":" + p.front() + ":" + p.size());
			}
			List<Integer> targets = plan.blocks().stream().map(MasterPlan.Block::target).toList();
			int[] streets = plan.streetHeights();
			FieldPins pins = new FieldPins();
			int e = plan.extent();
			for (int x = -e; x <= e; x++) {
				for (int z = -e; z <= e; z++) {
					MasterPlan.Block b = plan.blockAt(x, z);
					if (b != null && b.prepared()) {
						pins.pin(x, z, b.target());
					} else if (plan.ground(x, z) && plan.ring(x, z) <= 5 && HeightFieldBuilder.gateWall(plan, x, z) == null) {
						pins.pin(x, z, plan.streetHeight(x, z));
					}
				}
			}
			HeightFieldBuilder.Natural natural = (x, z) -> MasterPlanTest.TERRAINS.get(3).applyAsInt(x, z);
			HeightField f = HeightFieldBuilder.forPlan(plan, 0, 0, natural, MIN_Y, pins);
			List<String> after = new ArrayList<>();
			for (MasterPlan.Parcel p : plan.parcels()) {
				after.add(p.id() + ":" + p.block() + ":" + p.rect() + ":" + p.front() + ":" + p.size());
			}
			assertEquals(parcels, after, "lotes e ids nao mudam");
			assertEquals(targets, plan.blocks().stream().map(MasterPlan.Block::target).toList(), "alturas do plano salvo nao mudam");
			assertTrue(java.util.Arrays.equals(streets, plan.streetHeights()));
			for (Map.Entry<Long, Integer> pin : pins.all().entrySet()) {
				assertEquals(pin.getValue(), f.heightRel(VillageLayout.colX(pin.getKey()), VillageLayout.colZ(pin.getKey())));
			}
			// o que nao estava pronto segue as regras (portoes planos, rampas)
			for (HeightField.Gate g : f.gates()) {
				for (int x = g.x0(); x <= g.x1(); x++) {
					for (int z = g.z0(); z <= g.z1(); z++) {
						if (f.kindRel(x, z).gate()) {
							assertEquals(g.level(), f.heightRel(x, z));
						}
					}
				}
			}
		}
	}

	@Test
	void oldPlanWithoutRuralRingGetsABandAroundIt() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(1));
		MasterPlan plan = MasterPlanTest.plan(t, 8);
		HeightField f = HeightFieldBuilder.forPlan(plan, 100, -50, (x, z) -> MasterPlanTest.TERRAINS.get(1).applyAsInt(x, z), MIN_Y, FieldPins.none());
		assertEquals(plan.extent(), f.planExtent());
		assertEquals(HeightField.Kind.BAND, f.kindRel(StreetPlanner.OUTER + 10, 0));
		assertTrue(f.wallPoints(HeightField.Wall.OUTER).isEmpty());
		assertEquals(f.heightRel(3, 4), f.height(103, -46), "consulta em coordenadas do mundo");
	}

	// ---------------------------------------------------------------- determinismo e formato

	@Test
	void sameInputsGiveTheSameField() {
		for (String name : List.of("noisy", "mountain_inside")) {
			HeightFieldBuilder.Result a = cached(name, 2024);
			HeightFieldBuilder.Result b = build(terrains(2024).get(name), 2024);
			assertTrue(a.field().sameAs(b.field()), name + " deterministico");
			assertEquals(a.plan().parcels().size(), b.plan().parcels().size());
		}
	}

	@Test
	void encodingRoundTripsAndIsCompact() {
		for (long seed : SEEDS) {
			for (String name : terrains(seed).keySet()) {
				HeightField f = cached(name, seed).field();
				byte[] heights = f.encodeHeights();
				byte[] kinds = f.encodeKinds();
				HeightField back = HeightField.decode(f.header(), heights, kinds);
				assertTrue(f.sameAs(back), name + " ida e volta");
				for (int x = -E; x <= E; x += 7) {
					for (int z = -E; z <= E; z += 7) {
						assertEquals(f.weightRel(x, z), back.weightRel(x, z));
						assertEquals(f.depthRel(x, z), back.depthRel(x, z));
					}
				}
				int bytes = heights.length + kinds.length;
				System.out.println("[campo] " + name + "#" + seed + ": " + heights.length + " + " + kinds.length + " bytes");
				assertTrue(bytes < 160_000, name + " grande demais: " + bytes);
			}
		}
		assertEquals(null, HeightField.decode(new int[] {99, 0, 0, 1, 1, 1, 1, 1}, new byte[0], new byte[0]), "formato desconhecido");
	}

	@Test
	void villagesFileKeepsTheFieldAndOldFilesLoadWithout() {
		String old = """
				{"villages":[{"core":{"dimension":"minecraft:overworld","x":100,"y":70,"z":-40,"seed":77,"style":"plains","name":"Kalanros",
				"starter":true},"progress":{"level":3,"prosperity":1014,"built":{"plaza":1},"queue":[],"lots":[]}}],"starter_checked":true}
				""";
		VillageData data = VillageData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(old)).getOrThrow();
		VillageState v = data.villages().iterator().next();
		assertEquals(null, data.field(v.key()), "arquivo antigo sem campo: o servidor calcula na primeira carga");
		HeightField f = cached("noisy", 11).field();
		data.putField(v.key(), f);
		data.putField(VillageData.PENDING_FIELD, cached("slope", 11).field());
		data.putField("minecraft:overworld@1,2,3", f);
		data.remove("minecraft:overworld@1,2,3");
		var json = VillageData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data).getOrThrow();
		VillageData back = VillageData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
		assertTrue(f.sameAs(back.field(v.key())), "campo volta igual");
		assertTrue(cached("slope", 11).field().sameAs(back.field(VillageData.PENDING_FIELD)), "campo da vila ainda nao criada fica");
		assertEquals(null, back.field("minecraft:overworld@1,2,3"));
		// o campo nao vai no pacote da tela (VILLAGE_CODEC)
		String village = VillageData.VILLAGE_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, v).getOrThrow().toString();
		assertFalse(village.contains("heights"), "campo fora do pacote da vila");
	}

	@Test
	void coarseSamplingInterpolatesBetweenNodes() {
		int[] calls = {0};
		HeightFieldBuilder.Natural n = HeightFieldBuilder.coarse((x, z) -> {
			calls[0]++;
			return 64 + x / 4;
		}, 20, 4);
		assertEquals(64 + 2, n.ground(8, 3));
		assertEquals(64 + 2, n.ground(9, 0), "8 -> 66, 12 -> 67: 9 fica em 66,25");
		assertEquals(11 * 11, calls[0]);
	}

	// ---------------------------------------------------------------- figura

	@Test
	void heatMaps() throws IOException {
		File dir = new File("build/heightfield");
		dir.mkdirs();
		for (long seed : new long[] {11, 77}) {
			for (String name : List.of("mountain_side", "mountain_inside", "river", "noisy", "slope")) {
				HeightFieldBuilder.Natural natural = terrains(seed).get(name);
				HeightField f = cached(name, seed).field();
				int[] n = HeightFieldBuilder.grid(natural);
				int s = 2 * E + 1;
				int lo = Integer.MAX_VALUE;
				int hi = Integer.MIN_VALUE;
				for (int i = 0; i < n.length; i++) {
					lo = Math.min(lo, n[i]);
					hi = Math.max(hi, n[i]);
				}
				int scale = 2;
				int profileH = 220;
				BufferedImage img = new BufferedImage(2 * s * scale + 10, s * scale + profileH, BufferedImage.TYPE_INT_RGB);
				for (int px = 0; px < img.getWidth(); px++) {
					for (int py = 0; py < img.getHeight(); py++) {
						img.setRGB(px, py, 0x202020);
					}
				}
				for (int x = -E; x <= E; x++) {
					for (int z = -E; z <= E; z++) {
						int a = n[HeightFieldBuilder.fidx(x, z)];
						int an = n[HeightFieldBuilder.fidx(Math.max(-E, x - 1), Math.max(-E, z - 1))];
						int cn = contour(n, x, z) ? 1 : 0;
						int c1 = shade(a, an, lo, hi, HeightField.Kind.NATURAL, cn == 1, false);
						int b = f.heightRel(x, z);
						int bn = f.heightRel(Math.max(-E, x - 1), Math.max(-E, z - 1));
						boolean edge = f.kindRel(x, z).inside() != f.kindRel(Math.min(E, x + 1), z).inside()
								|| (f.kindRel(x, z) == HeightField.Kind.BAND) != (f.kindRel(Math.min(E, x + 1), z) == HeightField.Kind.BAND)
								|| f.kindRel(x, z).inside() != f.kindRel(x, Math.min(E, z + 1)).inside()
								|| (f.kindRel(x, z) == HeightField.Kind.BAND) != (f.kindRel(x, Math.min(E, z + 1)) == HeightField.Kind.BAND);
						int c2 = shade(b, bn, lo, hi, f.kindRel(x, z), fieldContour(f, x, z), edge);
						for (int dx = 0; dx < scale; dx++) {
							for (int dz = 0; dz < scale; dz++) {
								img.setRGB((x + E) * scale + dx, (z + E) * scale + dz, c1);
								img.setRGB(s * scale + 10 + (x + E) * scale + dx, (z + E) * scale + dz, c2);
							}
						}
					}
				}
				// perfil ao longo do eixo x (z = 0) e da diagonal: natural cinza, campo vermelho/laranja
				int top = s * scale;
				double vy = (profileH - 20.0) / Math.max(20, hi - lo + 10);
				for (int x = -E; x <= E; x++) {
					int[][] lines = {{n[HeightFieldBuilder.fidx(x, 0)], 0x909090}, {f.heightRel(x, 0), 0xFF5040},
							{f.heightRel(x, clampE(x / 2)), 0xFFB040}};
					for (int[] l : lines) {
						int y = top + profileH - 10 - (int) Math.round((l[0] - lo + 5) * vy);
						for (int dx = 0; dx < 4; dx++) {
							int px = (x + E) * 4 + dx;
							if (px < img.getWidth() && y >= top && y < img.getHeight()) {
								img.setRGB(px, y, l[1]);
							}
						}
					}
				}
				ImageIO.write(img, "png", new File(dir, name + "_" + seed + ".png"));
			}
		}
	}

	private static int clampE(int v) {
		return Math.max(-E, Math.min(E, v));
	}

	private static boolean contour(int[] n, int x, int z) {
		int h = Math.floorDiv(n[HeightFieldBuilder.fidx(x, z)], 2);
		return x < E && Math.floorDiv(n[HeightFieldBuilder.fidx(x + 1, z)], 2) != h || z < E && Math.floorDiv(n[HeightFieldBuilder.fidx(x, z + 1)], 2) != h;
	}

	private static boolean fieldContour(HeightField f, int x, int z) {
		int h = Math.floorDiv(f.heightRel(x, z), 2);
		return x < E && Math.floorDiv(f.heightRel(x + 1, z), 2) != h || z < E && Math.floorDiv(f.heightRel(x, z + 1), 2) != h;
	}

	/** Cor por altura (azul baixo, verde, marrom, branco alto), sombra de relevo, curvas de nivel a cada 2 e bordas. */
	private static int shade(int h, int nw, int lo, int hi, HeightField.Kind kind, boolean contour, boolean edge) {
		double t = Math.max(0, Math.min(1, (h - lo) / (double) Math.max(1, hi - lo)));
		int r;
		int g;
		int b;
		if (t < 0.33) {
			double u = t / 0.33;
			r = (int) (40 + 40 * u);
			g = (int) (90 + 90 * u);
			b = (int) (160 - 80 * u);
		} else if (t < 0.66) {
			double u = (t - 0.33) / 0.33;
			r = (int) (80 + 100 * u);
			g = (int) (180 - 50 * u);
			b = (int) (80 - 20 * u);
		} else {
			double u = (t - 0.66) / 0.34;
			r = (int) (180 + 75 * u);
			g = (int) (130 + 125 * u);
			b = (int) (60 + 195 * u);
		}
		double sh = 1 + Math.max(-0.45, Math.min(0.45, (h - nw) * 0.18));
		if (kind.walkway()) {
			r = (r + 240) / 2;
			g = (g + 220) / 2;
			b = (b + 160) / 2;
		}
		if (contour) {
			sh *= 0.8;
		}
		r = Math.max(0, Math.min(255, (int) (r * sh)));
		g = Math.max(0, Math.min(255, (int) (g * sh)));
		b = Math.max(0, Math.min(255, (int) (b * sh)));
		if (edge) {
			return 0xF0F0F0;
		}
		return (r << 16) | (g << 8) | b;
	}

	@Test
	void fieldPinsKeepTheLastValue() {
		FieldPins p = new FieldPins().pin(1, 2, 70).pin(1, 2, 72);
		assertEquals(72, p.at(1, 2));
		assertFalse(p.isEmpty());
	}
}
