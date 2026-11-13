package com.mlin.notas.boss;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LairPlannerTest {
	private static final long[] SEEDS = {0L, 1L, 42L, 9001L, -77L, 123456789L};

	@Test
	void sameSeedSamePlan() {
		for (LairKind kind : LairKind.values()) {
			assertEquals(LairPlanner.plan(kind, 7L).blocks(), LairPlanner.plan(kind, 7L).blocks());
		}
	}

	@Test
	void everyLairHasItsMarkers() {
		for (LairKind kind : LairKind.values()) {
			LairPlan plan = LairPlanner.plan(kind, 3L);
			plan.marker(LairPlan.MARKER_BOSS);
			plan.marker(LairPlan.MARKER_CHEST);
			plan.marker(LairPlan.MARKER_ENTRANCE);
			assertEquals(LairPart.CHEST, at(plan, plan.marker(LairPlan.MARKER_CHEST)));
			if (kind.boss().hasAltar()) {
				assertEquals(LairPart.ALTAR, at(plan, plan.marker(LairPlan.MARKER_ALTAR)));
				assertEquals(1, plan.count(LairPart.ALTAR));
			} else {
				assertEquals(0, plan.count(LairPart.ALTAR), "o colosso dorme, nao tem altar");
			}
		}
	}

	private static LairPart at(LairPlan plan, Pos p) {
		return plan.get(p.x(), p.y(), p.z());
	}

	@Test
	void altarChestsAndBossAreReachableOnFootFromTheEntrance() {
		for (LairKind kind : LairKind.values()) {
			for (long seed : SEEDS) {
				LairPlan plan = LairPlanner.plan(kind, seed);
				Pos entrance = plan.marker(LairPlan.MARKER_ENTRANCE);
				Set<Long> reach = plan.reachableFrom(entrance.x(), entrance.y(), entrance.z());
				assertTrue(reach.size() > 50, kind + " entrada presa: " + reach.size());
				for (Map.Entry<Long, LairPart> e : plan.blocks().entrySet()) {
					if (e.getValue() == LairPart.CHEST || e.getValue() == LairPart.ALTAR) {
						long k = e.getKey();
						assertTrue(LairPlan.usableFrom(reach, LairPlan.keyX(k), LairPlan.keyY(k), LairPlan.keyZ(k)),
								kind + "/" + seed + ": " + e.getValue() + " em " + LairPlan.keyX(k) + "," + LairPlan.keyY(k) + "," + LairPlan.keyZ(k));
					}
				}
				Pos boss = plan.marker(LairPlan.MARKER_BOSS);
				boolean nearBoss = false;
				for (int dx = -3; dx <= 3 && !nearBoss; dx++) {
					for (int dz = -3; dz <= 3 && !nearBoss; dz++) {
						for (int dy = -2; dy <= 1; dy++) {
							nearBoss |= reach.contains(LairPlan.key(boss.x() + dx, boss.y() + dy, boss.z() + dz));
						}
					}
				}
				assertTrue(nearBoss, kind + "/" + seed + ": da para chegar perto do chefe");
			}
		}
	}

	@Test
	void bossFitsWhereItAppears() {
		// meia largura 1 (3x3) e altura 5 cobre o maior chefe (colosso 2,6 x 4,4)
		for (LairKind kind : LairKind.values()) {
			for (long seed : SEEDS) {
				LairPlan plan = LairPlanner.plan(kind, seed);
				Pos boss = plan.marker(LairPlan.MARKER_BOSS);
				assertTrue(plan.roomFor(boss.x(), boss.y(), boss.z(), 1, 5), kind + "/" + seed + " sem espaco para o chefe");
			}
		}
	}

	@Test
	void shrineHasEightStandingStonesOneBroken() {
		LairPlan plan = LairPlanner.plan(LairKind.COBALT_SHRINE, 11L);
		// 7 pedras inteiras de 2x2 com topo
		assertEquals(7 * 4, plan.count(LairPart.PILLAR_CAP));
		assertTrue(plan.count(LairPart.WALL_ACCENT) >= 8, "veio de cobalto em toda pedra");
		assertEquals(4, plan.count(LairPart.LIGHT));
	}

	@Test
	void forgeLavaRingIsCrossedByFourBridges() {
		LairPlan plan = LairPlanner.plan(LairKind.COLOSSUS_FORGE, 5L);
		assertTrue(plan.count(LairPart.LIQUID) > 30);
		for (int[] d : new int[][] {{0, 9}, {0, -9}, {9, 0}, {-9, 0}}) {
			assertEquals(LairPart.FLOOR_ACCENT, plan.get(d[0], 0, d[1]));
		}
		assertEquals(LairPart.LIQUID, plan.get(6, 0, 7));
	}

	@Test
	void sanctumIsEnclosedAndBelowItsOwnStairs() {
		LairPlan plan = LairPlanner.plan(LairKind.ABYSSAL_SANCTUM, 5L);
		int[] b = plan.bounds();
		assertTrue(b[4] <= 17, "salao + escada cabem abaixo de y=0 com folga: " + b[4]);
		assertEquals(LairPart.FRAME, plan.get(4, 5, -LairPlanner.SANCTUM_HALF));
		assertTrue(plan.count(LairPart.DECOR) > 40, "sculk espalhado");
		assertEquals(2, plan.count(LairPart.SHRIEKER));
	}
}
