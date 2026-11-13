package com.mlin.notas.end;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitadelPlannerTest {
	private static final long[] SEEDS = {0L, 1L, 42L, -7L, 123456789L, 987654321987L};

	@Test
	void sameSeedSamePlan() {
		assertEquals(CitadelPlanner.plan(42).blocks(), CitadelPlanner.plan(42).blocks());
		assertEquals(CitadelPlanner.plan(42).spawns(), CitadelPlanner.plan(42).spawns());
	}

	@Test
	void towersAreShortAndAllStandOnTheIsland() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			assertTrue(plan.maxY() <= 30, "a mais alta deve ficar abaixo de 30 blocos, ficou " + plan.maxY());
			assertTrue(plan.towers().size() >= 4 && plan.towers().size() <= 5, "fortaleza + 3 ou 4 torres");
			for (CitadelPlan.Tower tower : plan.towers()) {
				for (int dx = -tower.half(); dx <= tower.half(); dx++) {
					for (int dz = -tower.half(); dz <= tower.half(); dz++) {
						// tudo da torre fica sobre a ilha da propria cidadela (nada de construir ponte entre ilhas)
						CitadelPart below = plan.get(tower.x() + dx, -1, tower.z() + dz);
						assertEquals(CitadelPart.ISLAND, below, "torre em (" + tower.x() + "," + tower.z() + ") sem ilha embaixo");
					}
				}
			}
		}
	}

	@Test
	void everyFloorHasAnElevatorInTheSameColumn() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			for (CitadelPlan.Tower tower : plan.towers()) {
				for (int f = 0; f <= tower.floors(); f++) {
					assertEquals(CitadelPart.ELEVATOR, plan.get(tower.x(), f * CitadelPlanner.FLOOR_HEIGHT, tower.z()));
				}
			}
		}
	}

	@Test
	void guardsTreasureAndElytra() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			long shulkers = plan.count(CitadelPlan.Kind.SHULKER);
			assertTrue(shulkers >= 12 && shulkers <= 30, "shulkers: " + shulkers);
			assertTrue(plan.count(CitadelPlan.Kind.SENTINEL) >= 4, "sentinelas no hall e nas torres");
			assertEquals(1, plan.count(CitadelPlan.Kind.ELYTRA_FRAME));
			assertTrue(plan.count(CitadelPart.CHEST) >= 6, "baus: " + plan.count(CitadelPart.CHEST));
			assertEquals(1, plan.count(CitadelPart.DRAGON_HEAD));
		}
	}

	@Test
	void spawnsAreInsideEmptySpace() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			for (CitadelPlan.Spawn spawn : plan.spawns()) {
				CitadelPart at = plan.get(spawn.x(), spawn.y(), spawn.z());
				assertTrue(at == null || !at.obstacle(), spawn + " dentro de " + at);
			}
		}
	}

	// --- alcancabilidade: andar (subindo 1 bloco por vez) + elevadores, sem construir nada ---

	private static boolean obstacle(CitadelPlan plan, int x, int y, int z) {
		CitadelPart part = plan.get(x, y, z);
		return part != null && part.obstacle();
	}

	private static boolean standable(CitadelPlan plan, int x, int y, int z) {
		CitadelPart below = plan.get(x, y - 1, z);
		return below != null && below.standable() && !obstacle(plan, x, y, z) && !obstacle(plan, x, y + 1, z);
	}

	private static Set<Long> reachable(CitadelPlan plan) {
		Set<Long> seen = new HashSet<>();
		Deque<int[]> queue = new ArrayDeque<>();
		// chega pela beira da ilha
		int startZ = plan.radius() - 2;
		queue.add(new int[] {0, 1, startZ});
		seen.add(CitadelPlan.key(0, 1, startZ));
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			int x = p[0];
			int y = p[1];
			int z = p[2];
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				for (int dy = 1; dy >= -3; dy--) {
					int nx = x + d[0];
					int ny = y + dy;
					int nz = z + d[1];
					if (dy == 1 && obstacle(plan, x, y + 2, z)) {
						continue; // sem espaco para pular
					}
					if (standable(plan, nx, ny, nz)) {
						if (seen.add(CitadelPlan.key(nx, ny, nz))) {
							queue.add(new int[] {nx, ny, nz});
						}
						break;
					}
				}
			}
			if (plan.get(x, y - 1, z) == CitadelPart.ELEVATOR) {
				for (boolean up : new boolean[] {true, false}) {
					OptionalInt target = ElevatorLogic.target(y - 1, up, ty -> plan.get(x, ty, z) == CitadelPart.ELEVATOR,
							ty -> !obstacle(plan, x, ty, z), CitadelPlanner.ELEVATOR_RANGE);
					if (target.isPresent() && seen.add(CitadelPlan.key(x, target.getAsInt() + 1, z))) {
						queue.add(new int[] {x, target.getAsInt() + 1, z});
					}
				}
			}
		}
		return seen;
	}

	private static boolean nextToReachable(Set<Long> reach, int x, int y, int z) {
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			for (int dy = -1; dy <= 1; dy++) {
				if (reach.contains(CitadelPlan.key(x + d[0], y + dy, z + d[1]))) {
					return true;
				}
			}
		}
		return false;
	}

	@Test
	void everyChestTheElytraAndEveryRoofCanBeReachedWithoutBuilding() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			Set<Long> reach = reachable(plan);
			for (Map.Entry<Long, CitadelPart> entry : plan.blocks().entrySet()) {
				if (entry.getValue() == CitadelPart.CHEST) {
					long k = entry.getKey();
					assertTrue(nextToReachable(reach, CitadelPlan.keyX(k), CitadelPlan.keyY(k), CitadelPlan.keyZ(k)),
							"seed " + seed + ": bau em " + CitadelPlan.keyX(k) + "," + CitadelPlan.keyY(k) + "," + CitadelPlan.keyZ(k) + " inalcancavel");
				}
			}
			for (CitadelPlan.Spawn spawn : plan.spawns()) {
				if (spawn.kind() == CitadelPlan.Kind.ELYTRA_FRAME) {
					assertTrue(reach.contains(CitadelPlan.key(spawn.x(), spawn.y() - 1, spawn.z())) || nextToReachable(reach, spawn.x(), spawn.y(), spawn.z()),
							"seed " + seed + ": elitro inalcancavel");
				}
			}
			for (CitadelPlan.Tower tower : plan.towers()) {
				assertTrue(reach.contains(CitadelPlan.key(tower.x(), tower.roofY() + 1, tower.z())),
						"seed " + seed + ": telhado da torre em " + tower.x() + "," + tower.z() + " inalcancavel");
				for (int f = 0; f < tower.floors(); f++) {
					assertTrue(reach.contains(CitadelPlan.key(tower.x(), f * CitadelPlanner.FLOOR_HEIGHT + 1, tower.z())),
							"seed " + seed + ": andar " + f + " da torre em " + tower.x() + "," + tower.z() + " inalcancavel");
				}
			}
		}
	}

	@Test
	void elevatorFindsNextFreeFloor() {
		// elevadores em 0, 5 e 10; o de 5 esta bloqueado (bloco solido logo acima)
		OptionalInt up = ElevatorLogic.target(0, true, y -> y % 5 == 0, y -> y != 6, 32);
		assertEquals(10, up.getAsInt(), "pula o andar sem espaco");
		assertEquals(0, ElevatorLogic.target(10, false, y -> y % 5 == 0, y -> y != 6, 32).getAsInt());
		assertFalse(ElevatorLogic.target(0, true, y -> y == 40, y -> true, 32).isPresent(), "fora do alcance");
		assertFalse(ElevatorLogic.target(0, false, y -> y == 0, y -> true, 32).isPresent(), "nada abaixo");
	}
}
