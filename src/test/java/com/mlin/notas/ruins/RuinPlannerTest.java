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
			assertTrue(chest.y() >= RuinPlanner.TOWER_TOP, "bau no topo da torre, ficou em y=" + chest.y());

			RuinPlan camp = RuinPlanner.plan(RuinKind.CAMP, seed);
			assertEquals(1, camp.count(RuinPart.CAMPFIRE));
			assertEquals(1, camp.count(RuinPart.BARREL));
			assertTrue(camp.count(RuinPart.TENT) >= 10, "barracas de la");

			RuinPlan well = RuinPlanner.plan(RuinKind.WELL, seed);
			assertTrue(well.marker(RuinPlan.MARKER_CHEST).y() < -5, "bau na sala embaixo do poco");
			assertTrue(well.count(RuinPart.LADDER) >= 8, "escada de mao ate o fundo");

			RuinPlan chapel = RuinPlanner.plan(RuinKind.CHAPEL, seed);
			assertNotNull(chapel.marker(RuinPlan.MARKER_WAYSTONE), "capela com pedra de teleporte");
			Pos summon = chapel.marker(RuinPlan.MARKER_BOSS_SUMMON);
			assertEquals(RuinPart.LECTERN, chapel.get(summon.x(), summon.y(), summon.z()), "atril no altar (lugar do item do chefe)");
			assertEquals(RuinPart.ALTAR, chapel.get(summon.x(), summon.y() - 1, summon.z()));

			RuinPlan mine = RuinPlanner.plan(RuinKind.MINE, seed);
			Pos altar = mine.marker(RuinPlan.MARKER_BOSS_ALTAR);
			assertEquals(RuinPart.BOSS_ALTAR, mine.get(altar.x(), altar.y(), altar.z()), "bloco provisorio da sala do chefe");
			assertTrue(mine.count(RuinPart.ORE) >= 8, "veio de minerio: " + mine.count(RuinPart.ORE));
			assertTrue(mine.count(RuinPart.FENCE) >= 8, "escoras");
		}
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
