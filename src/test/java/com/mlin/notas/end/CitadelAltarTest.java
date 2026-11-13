package com.mlin.notas.end;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O altar do Arauto Astral no cofre da cidadela: um so, no ultimo andar da fortaleza, alcancavel sem construir. */
class CitadelAltarTest {
	private static final long[] SEEDS = {0L, 1L, 42L, -7L, 123456789L, 987654321987L};

	@Test
	void vaultHasOneReachableAstralAltar() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			assertEquals(1, plan.count(CitadelPart.ASTRAL_ALTAR), "seed " + seed);
			Set<Long> reach = reachable(plan);
			for (Map.Entry<Long, CitadelPart> entry : plan.blocks().entrySet()) {
				if (entry.getValue() != CitadelPart.ASTRAL_ALTAR) {
					continue;
				}
				long k = entry.getKey();
				int x = CitadelPlan.keyX(k);
				int y = CitadelPlan.keyY(k);
				int z = CitadelPlan.keyZ(k);
				assertEquals((CitadelPlanner.KEEP_FLOORS - 1) * CitadelPlanner.FLOOR_HEIGHT + 1, y, "no andar do cofre");
				assertTrue(Math.abs(x) < CitadelPlanner.KEEP_HALF && Math.abs(z) < CitadelPlanner.KEEP_HALF, "dentro da fortaleza");
				boolean usable = reach.contains(CitadelPlan.key(x, y + 1, z));
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					for (int dy = -1; dy <= 1; dy++) {
						usable |= reach.contains(CitadelPlan.key(x + d[0], y + dy, z + d[1]));
					}
				}
				assertTrue(usable, "seed " + seed + ": altar inalcancavel");
				// cabe o arauto (1 x 2,4) em cima do altar
				for (int dy = 1; dy <= 3; dy++) {
					CitadelPart above = plan.get(x, y + dy, z);
					assertTrue(above == null || !above.obstacle(), "seed " + seed + ": sem espaco em cima do altar");
				}
			}
		}
	}

	private static boolean obstacle(CitadelPlan plan, int x, int y, int z) {
		CitadelPart part = plan.get(x, y, z);
		return part != null && part.obstacle();
	}

	private static boolean standable(CitadelPlan plan, int x, int y, int z) {
		CitadelPart below = plan.get(x, y - 1, z);
		return below != null && below.standable() && !obstacle(plan, x, y, z) && !obstacle(plan, x, y + 1, z);
	}

	/** Mesma regra do CitadelPlannerTest: anda, sobe 1, cai ate 3 e usa os elevadores. */
	private static Set<Long> reachable(CitadelPlan plan) {
		Set<Long> seen = new HashSet<>();
		Deque<int[]> queue = new ArrayDeque<>();
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
						continue;
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
}
