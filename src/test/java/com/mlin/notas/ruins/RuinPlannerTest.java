package com.mlin.notas.ruins;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinPlannerTest {
	private static final long[] SEEDS = {0L, 1L, 2L, 3L, 42L, -7L, 1234L, 99999L, 123456789L, 987654321987L, -55555555L, 31337L};

	@Test
	void sameSeedSamePlan() {
		for (RuinKind kind : RuinKind.values()) {
			assertEquals(RuinPlanner.plan(kind, 42).blocks(), RuinPlanner.plan(kind, 42).blocks());
			assertEquals(RuinPlanner.plan(kind, 42).markers(), RuinPlanner.plan(kind, 42).markers());
		}
	}

	@Test
	void seedsVaryTheRuin() {
		for (RuinKind kind : RuinKind.values()) {
			boolean differs = false;
			for (long seed : SEEDS) {
				differs |= !RuinPlanner.plan(kind, seed).blocks().equals(RuinPlanner.plan(kind, 0).blocks());
			}
			assertTrue(differs, kind + " sempre igual");
		}
	}

	@Test
	void everyChestCanBeReachedOnFootFromOutside() {
		for (RuinKind kind : RuinKind.values()) {
			for (long seed : SEEDS) {
				RuinPlan plan = RuinPlanner.plan(kind, seed);
				Pos entrance = plan.marker(RuinPlan.MARKER_ENTRANCE);
				assertNotNull(entrance);
				assertEquals(null, plan.get(entrance.x(), entrance.y(), entrance.z()), kind + ": entrada dentro da ruina");
				Set<Long> reach = plan.reachableFrom(entrance.x(), entrance.y(), entrance.z());
				assertTrue(reach.size() > 1, kind + " seed " + seed + ": entrada presa");
				assertTrue(plan.count(RuinPart.CHEST) >= 1, kind + " sem bau");
				for (Pos chest : plan.positionsOf(RuinPart.CHEST)) {
					assertTrue(RuinPlan.usableFrom(reach, chest.x(), chest.y(), chest.z()),
							kind + " seed " + seed + ": bau em " + chest + " inalcancavel a pe");
				}
				for (Pos barrel : plan.positionsOf(RuinPart.BARREL)) {
					assertTrue(RuinPlan.usableFrom(reach, barrel.x(), barrel.y(), barrel.z()), kind + " seed " + seed + ": barril inalcancavel");
				}
			}
		}
	}

	@Test
	void nothingFloats() {
		for (RuinKind kind : RuinKind.values()) {
			for (long seed : SEEDS) {
				RuinPlan plan = RuinPlanner.plan(kind, seed);
				assertTrue(plan.floating().isEmpty(), kind + " seed " + seed + ": blocos soltos " + plan.floating().size());
			}
		}
	}

	@Test
	void ladderAlwaysLeansOnAWallAndChestsAreNotBuried() {
		for (RuinKind kind : RuinKind.values()) {
			for (long seed : SEEDS) {
				RuinPlan plan = RuinPlanner.plan(kind, seed);
				for (Pos ladder : plan.positionsOf(RuinPart.LADDER)) {
					RuinPlan.Dir facing = plan.facing(ladder.x(), ladder.y(), ladder.z());
					assertNotNull(facing);
					RuinPart behind = plan.get(ladder.x() - facing.dx, ladder.y(), ladder.z() - facing.dz);
					boolean wall = behind != null ? behind.obstacle() && behind.standable() : ladder.y() < 0;
					assertTrue(wall, kind + " seed " + seed + ": escada de mao em " + ladder + " sem parede atras (" + behind + ")");
				}
			}
		}
	}

	@Test
	void eachRuinHasItsLandmarks() {
		for (long seed : SEEDS) {
			RuinPlan tower = RuinPlanner.plan(RuinKind.WATCHTOWER, seed);
			assertNotNull(tower.marker(RuinPlan.MARKER_WAYSTONE), "torre com pedra de teleporte");
			assertEquals(1, tower.count(RuinPart.WAYSTONE));
			Pos chest = tower.marker(RuinPlan.MARKER_CHEST);
			assertTrue(chest.y() > RuinPlanner.TOWER_MIN_TOP, "bau no deque do topo da torre, ficou em y=" + chest.y());
			assertTrue(tower.count(RuinPart.WOOD_STAIRS) >= 10, "escada de degraus por dentro da torre");

			RuinPlan camp = RuinPlanner.plan(RuinKind.CAMP, seed);
			assertEquals(1, camp.count(RuinPart.CAMPFIRE));
			assertTrue(camp.count(RuinPart.BARREL) >= 1, "barril com loot");
			assertTrue(camp.count(RuinPart.TENT) + camp.count(RuinPart.TENT_ALT) >= 30, "barracas de la listrada");
			assertTrue(camp.count(RuinPart.TRAPDOOR) >= 2, "rodas da carroca");
			assertTrue(camp.count(RuinPart.FENCE) >= 10, "curral e postes");

			RuinPlan well = RuinPlanner.plan(RuinKind.WELL, seed);
			assertTrue(well.marker(RuinPlan.MARKER_CHEST).y() < -5, "bau na cisterna embaixo do poco");
			assertTrue(well.count(RuinPart.LADDER) >= 12, "escada de mao ate o fundo");
			assertTrue(well.count(RuinPart.WOOD_STAIRS) >= 30, "telhado de duas aguas");
			assertTrue(well.count(RuinPart.WATER) >= 6, "agua no fundo do poco");

			RuinPlan chapel = RuinPlanner.plan(RuinKind.CHAPEL, seed);
			assertNotNull(chapel.marker(RuinPlan.MARKER_WAYSTONE), "capela com pedra de teleporte");
			Pos summon = chapel.marker(RuinPlan.MARKER_BOSS_SUMMON);
			assertEquals(RuinPart.LECTERN, chapel.get(summon.x(), summon.y(), summon.z()), "atril no altar (lugar do item do chefe)");
			assertEquals(RuinPart.ALTAR, chapel.get(summon.x(), summon.y() - 1, summon.z()));

			RuinPlan mine = RuinPlanner.plan(RuinKind.MINE, seed);
			Pos altar = mine.marker(RuinPlan.MARKER_BOSS_ALTAR);
			assertEquals(RuinPart.BOSS_ALTAR, mine.get(altar.x(), altar.y(), altar.z()), "bloco provisorio da sala do chefe");
			assertTrue(mine.count(RuinPart.ORE) >= 8, "veio de minerio: " + mine.count(RuinPart.ORE));
			assertTrue(mine.count(RuinPart.BEAM) >= 20, "escoras de tronco");
			assertTrue(mine.count(RuinPart.RAIL) + mine.count(RuinPart.RAIL_UP) >= 15, "trilhos");
			assertEquals(1, mine.count(RuinPart.MINECART), "carrinho de mina");
			assertTrue(chapel.count(RuinPart.GLASS_PANE) + chapel.count(RuinPart.IRON_BARS) >= 3, "vitrais quebrados");
			assertEquals(1, chapel.count(RuinPart.BELL), "sino caido na torre");
			assertTrue(chapel.count(RuinPart.WOOD_STAIRS) >= 40, "bancos e telhado");
		}
	}

	/** Tamanhos pedidos: torre 9x9 com 14-18 de altura, capela ~11x17. */
	@Test
	void sizesFollowTheBrief() {
		for (long seed : SEEDS) {
			RuinPlan tower = RuinPlanner.plan(RuinKind.WATCHTOWER, seed);
			int[] b = tower.bounds();
			assertTrue(b[4] >= 14 && b[4] <= 19, "altura da torre " + b[4]);
			RuinPlan chapel = RuinPlanner.plan(RuinKind.CHAPEL, seed);
			// nave de 11x17: o perimetro de parede no chao passa de 50 blocos (menos a porta)
			long walls = chapel.blocks().entrySet().stream().filter(e -> RuinPlan.keyY(e.getKey()) == 0).map(Map.Entry::getValue)
					.filter(p -> p == RuinPart.WALL || p == RuinPart.WALL_ALT || p == RuinPart.WALL_MOSSY || p == RuinPart.WALL_CRACKED
							|| p == RuinPart.ACCENT).count();
			assertTrue(walls >= 50, "paredes da nave: " + walls);
			int[] c = chapel.surfaceBounds();
			assertTrue(Math.max(c[2] - c[0], c[3] - c[1]) >= 17, "capela comprida");
		}
	}

	/** Paredes com textura: as quatro pedras da mistura aparecem, e cada ruina usa muitos tipos de bloco de detalhe. */
	@Test
	void wallsAreTexturedAndDetailed() {
		for (RuinKind kind : RuinKind.values()) {
			for (long seed : SEEDS) {
				RuinPlan plan = RuinPlanner.plan(kind, seed);
				for (RuinPart stone : new RuinPart[] {RuinPart.WALL, RuinPart.WALL_ALT, RuinPart.WALL_MOSSY, RuinPart.WALL_CRACKED}) {
					assertTrue(plan.count(stone) > 0, kind + " seed " + seed + " sem " + stone);
				}
				long kinds = plan.blocks().values().stream().distinct().count();
				assertTrue(kinds >= 22, kind + " seed " + seed + ": so " + kinds + " tipos de bloco");
				assertTrue(plan.count(RuinPart.PLANT) + plan.count(RuinPart.VINE) + plan.count(RuinPart.LEAVES) + plan.count(RuinPart.MOSS_CARPET) >= 10,
						kind + " seed " + seed + ": pouca vegetacao");
			}
		}
	}

	/** Entulho solto tem que estar apoiado logo embaixo (nao basta encostar de lado). */
	@Test
	void looseRubbleRestsOnSomething() {
		for (RuinKind kind : RuinKind.values()) {
			for (long seed : SEEDS) {
				RuinPlan plan = RuinPlanner.plan(kind, seed);
				for (long k : plan.loose()) {
					int x = RuinPlan.keyX(k);
					int y = RuinPlan.keyY(k);
					int z = RuinPlan.keyZ(k);
					assertTrue(plan.get(x, y, z) != RuinPart.AIR, kind + " seed " + seed + ": entulho marcado e vazio em " + x + "," + y + "," + z);
					RuinPart below = plan.get(x, y - 1, z);
					boolean rests = below != null ? below.obstacle() && below.standable() : y - 1 < 0;
					assertTrue(rests, kind + " seed " + seed + ": entulho sem apoio em " + x + "," + y + "," + z + " (embaixo " + below + ")");
				}
				assertTrue(kind == RuinKind.CAMP || kind == RuinKind.WELL || plan.loose().size() >= 3, kind + " seed " + seed + ": sem entulho");
			}
		}
	}

	/** Plantas, tapetes, velas e trilhos tem chao; trepadeiras tem parede; lanternas penduradas tem onde pendurar. */
	@Test
	void decorationsAreAttached() {
		for (RuinKind kind : RuinKind.values()) {
			for (long seed : SEEDS) {
				RuinPlan plan = RuinPlanner.plan(kind, seed);
				for (Map.Entry<Long, RuinPart> e : plan.blocks().entrySet()) {
					int x = RuinPlan.keyX(e.getKey());
					int y = RuinPlan.keyY(e.getKey());
					int z = RuinPlan.keyZ(e.getKey());
					RuinPart part = e.getValue();
					String where = kind + " seed " + seed + ": " + part + " em " + x + "," + y + "," + z;
					if (part.needsFloor()) {
						RuinPart below = plan.get(x, y - 1, z);
						assertTrue(below != null ? below.obstacle() : y - 1 < 0, where + " sem chao (" + below + ")");
					}
					if (part == RuinPart.VINE) {
						RuinPlan.Dir d = plan.facing(x, y, z);
						RuinPart wall = plan.get(x + d.dx, y, z + d.dz);
						assertTrue(wall != null && wall.obstacle() && wall.standable(), where + " sem parede");
					}
					if (part == RuinPart.LANTERN_HANGING || part == RuinPart.CHAIN && plan.facing(x, y, z) == null) {
						RuinPart above = plan.get(x, y + 1, z);
						assertTrue(above != null && above.obstacle(), where + " sem nada em cima (" + above + ")");
					}
				}
			}
		}
	}

	@Test
	void mirrorTwiceIsTheSamePlan() {
		RuinPlan plan = RuinPlanner.plan(RuinKind.WATCHTOWER, 9);
		assertEquals(plan.blocks(), plan.mirrored().mirrored().blocks());
		assertEquals(plan.markers(), plan.mirrored().mirrored().markers());
		assertEquals(plan.loose(), plan.mirrored().mirrored().loose());
	}

	@Test
	void bossSpotsAndWaystonesAreReachable() {
		for (long seed : SEEDS) {
			for (RuinKind kind : RuinKind.values()) {
				RuinPlan plan = RuinPlanner.plan(kind, seed);
				Pos entrance = plan.marker(RuinPlan.MARKER_ENTRANCE);
				Set<Long> reach = plan.reachableFrom(entrance.x(), entrance.y(), entrance.z());
				for (Map.Entry<String, Pos> marker : plan.markers().entrySet()) {
					if (marker.getKey().equals(RuinPlan.MARKER_ENTRANCE)) {
						continue;
					}
					Pos p = marker.getValue();
					assertTrue(RuinPlan.usableFrom(reach, p.x(), p.y(), p.z()), kind + " seed " + seed + ": " + marker.getKey() + " inalcancavel");
				}
			}
		}
	}

	@Test
	void reachabilityRespectsWallsAndLadders() {
		RuinPlan plan = new RuinPlan(RuinKind.WELL);
		// caixa fechada de 3 de altura: nao da para entrar sem escada
		for (int y = 0; y <= 2; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					if (x != 0 || z != 0) {
						plan.set(x, y, z, RuinPart.WALL);
					}
				}
			}
		}
		plan.set(0, -1, 0, RuinPart.PAVING);
		plan.set(0, 0, 0, RuinPart.CHEST);
		Set<Long> reach = plan.reachableFrom(0, 0, 4);
		assertTrue(!RuinPlan.usableFrom(reach, 0, 0, 0), "bau trancado numa caixa de 3 de altura");
		plan.set(0, 0, 2, RuinPart.LADDER, RuinPlan.Dir.SOUTH);
		plan.set(0, 1, 2, RuinPart.LADDER, RuinPlan.Dir.SOUTH);
		plan.set(0, 2, 2, RuinPart.LADDER, RuinPlan.Dir.SOUTH);
		plan.set(0, 0, 1, RuinPart.WALL);
		reach = plan.reachableFrom(0, 0, 4);
		assertTrue(reach.contains(RuinPlan.key(0, 3, 1)), "a escada de mao leva ao topo da parede");
	}

	@Test
	void rotationKeepsShapeAndMarkers() {
		RuinPlan plan = RuinPlanner.plan(RuinKind.CHAPEL, 5);
		RuinPlan back = plan.rotated(1).rotated(3);
		assertEquals(plan.blocks(), back.blocks());
		assertEquals(plan.markers(), back.markers());
		Pos chest = plan.marker(RuinPlan.MARKER_CHEST);
		Pos turned = plan.rotated(1).marker(RuinPlan.MARKER_CHEST);
		assertEquals(new Pos(-chest.z(), chest.y(), chest.x()), turned);
	}

	@Test
	void styleFollowsTheBiome() {
		assertEquals(RuinStyle.SANDSTONE, RuinStyle.forBiome("minecraft:desert"));
		assertEquals(RuinStyle.RED_SANDSTONE, RuinStyle.forBiome("minecraft:eroded_badlands"));
		assertEquals(RuinStyle.SPRUCE, RuinStyle.forBiome("minecraft:old_growth_spruce_taiga"));
		assertEquals(RuinStyle.DARK, RuinStyle.forBiome("minecraft:swamp"));
		assertEquals(RuinStyle.DEEPSLATE, RuinStyle.forBiome("minecraft:windswept_hills"));
		assertEquals(RuinStyle.ACACIA, RuinStyle.forBiome("minecraft:savanna_plateau"));
		assertEquals(RuinStyle.MOSSY, RuinStyle.forBiome("minecraft:plains"));
		assertEquals(RuinStyle.MOSSY, RuinStyle.forBiome(null));
		assertEquals(RuinKind.MINE, RuinKind.byName("mine"));
		assertEquals(RuinKind.MINE, RuinKind.byName("disused_mine"));
	}
}
