package com.mlin.notas.end;

import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.VPlan;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitadelPlannerTest {
	static final long[] SEEDS = {0L, 1L, 42L, -7L, 123456789L, 987654321987L, 20261113L, 5L};

	static Set<Long> reach(CitadelPlan plan) {
		Pos start = plan.marker("entrance");
		assertNotNull(start, "marcador de entrada");
		return plan.reachableFrom(start.x(), start.y(), start.z());
	}

	@Test
	void sameSeedSamePlan() {
		assertEquals(CitadelPlanner.plan(42).blocks(), CitadelPlanner.plan(42).blocks());
		assertEquals(CitadelPlanner.plan(42).spawns(), CitadelPlanner.plan(42).spawns());
	}

	@Test
	void silhouetteHasVariedTowersAndTheKeepIsTallest() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			assertTrue(plan.maxY() >= 44 && plan.maxY() <= 64, "seed " + seed + ": altura " + plan.maxY());
			assertEquals(5, plan.towers().size(), "fortaleza + 3 torres + mastro do cais");
			Set<Integer> heights = new HashSet<>();
			for (CitadelPlan.Tower t : plan.towers()) {
				if (t.name().startsWith("tower_")) {
					int top = 0;
					for (int y = 0; y < 80; y++) {
						if (!CitadelPlan.isAir(plan.get(t.x(), y, t.z()))) {
							top = y;
						}
					}
					heights.add(top);
				}
			}
			assertEquals(3, heights.size(), "seed " + seed + ": torres de alturas diferentes");
			// a agulha mais alta e a da cupula da fortaleza (coluna central)
			int keepTop = Integer.MIN_VALUE;
			for (int y = 0; y < 80; y++) {
				if (plan.get(0, y, 0) != null && !CitadelPlan.isAir(plan.get(0, y, 0))) {
					keepTop = y;
				}
			}
			assertEquals(plan.maxY(), keepTop, "seed " + seed + ": a fortaleza e o ponto mais alto");
		}
	}

	@Test
	void towersStandOnTheIsland() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			for (CitadelPlan.Tower tower : plan.towers()) {
				for (int dx = -tower.half(); dx <= tower.half(); dx++) {
					for (int dz = -tower.half(); dz <= tower.half(); dz++) {
						assertEquals("end_stone", plan.idAt(tower.x() + dx, -1, tower.z() + dz) == null ? null
								: plan.idAt(tower.x() + dx, -1, tower.z() + dz).replace("end_stone_bricks", "end_stone"),
								"torre " + tower.name() + " sem ilha embaixo");
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
				for (int f = 0; f < tower.floors(); f++) {
					assertEquals("purpur_elevator", plan.idAt(tower.x(), tower.floorY(f), tower.z()), tower.name() + " andar " + f);
					assertTrue(CitadelPlan.isAir(plan.get(tower.x(), tower.floorY(f) + 1, tower.z()))
							&& CitadelPlan.isAir(plan.get(tower.x(), tower.floorY(f) + 2, tower.z())), tower.name() + " andar " + f + " sem espaco");
				}
			}
		}
	}

	@Test
	void guardsTreasureAndTheShip() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			long shulkers = plan.count(CitadelPlan.Kind.SHULKER);
			assertTrue(shulkers >= 15 && shulkers <= 45, "shulkers: " + shulkers);
			assertTrue(plan.count(CitadelPlan.Kind.SENTINEL) >= 6, "sentinelas no hall, nas torres, no cais e na nave");
			assertEquals(1, plan.count(CitadelPlan.Kind.ELYTRA_FRAME), "um elitro por cidadela");
			long chests = plan.lootTables().size();
			assertTrue(chests >= 10, "baus: " + chests);
			assertTrue(plan.lootTables().values().stream().allMatch(CitadelPlan.CHEST_LOOT::equals), "baus com end_city_treasure");
			assertEquals(1, plan.count("dragon_head"), "cabeca de dragao no cofre");
			assertEquals(1, plan.count("dragon_wall_head"), "cabeca de dragao na proa da nave");
			// o elitro fica na cabine da nave, fora da ilha
			Pos elytra = plan.marker("elytra");
			assertTrue(Math.sqrt(elytra.x() * elytra.x() + elytra.z() * elytra.z()) > CitadelPlanner.RADIUS, "elitro na nave");
			assertEquals(CitadelPlanner.DECK_Y + 2, elytra.y());
		}
	}

	@Test
	void spawnsAreInsideEmptySpace() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			for (CitadelPlan.Spawn spawn : plan.spawns()) {
				assertFalse(plan.solidAt(spawn.x(), spawn.y(), spawn.z()), "seed " + seed + ": " + spawn + " dentro de " + plan.get(spawn.x(), spawn.y(), spawn.z()));
				if (spawn.kind() != CitadelPlan.Kind.ELYTRA_FRAME) {
					assertTrue(CitadelPlan.standable(plan.get(spawn.x(), spawn.y() - 1, spawn.z())), "seed " + seed + ": " + spawn + " sem chao");
				}
			}
		}
	}

	@Test
	void everyChestFloorRoofAndTheElytraCanBeReachedWithoutBuilding() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			Set<Long> reach = reach(plan);
			for (long k : plan.lootTables().keySet()) {
				assertTrue(CitadelPlan.usableFrom(reach, CitadelPlan.keyX(k), CitadelPlan.keyY(k), CitadelPlan.keyZ(k)),
						"seed " + seed + ": bau em " + CitadelPlan.keyX(k) + "," + CitadelPlan.keyY(k) + "," + CitadelPlan.keyZ(k) + " inalcancavel");
			}
			for (CitadelPlan.Spawn spawn : plan.spawns()) {
				if (spawn.kind() == CitadelPlan.Kind.ELYTRA_FRAME) {
					assertTrue(reach.contains(CitadelPlan.key(spawn.x(), spawn.y() - 1, spawn.z())), "seed " + seed + ": elitro inalcancavel");
				}
			}
			for (CitadelPlan.Tower tower : plan.towers()) {
				for (int f = 0; f < tower.floors(); f++) {
					assertTrue(reach.contains(CitadelPlan.key(tower.x(), tower.floorY(f) + 1, tower.z())),
							"seed " + seed + ": andar " + f + " de " + tower.name() + " inalcancavel");
				}
			}
			Pos deck = plan.marker("ship_deck");
			assertTrue(reach.contains(CitadelPlan.key(deck.x(), deck.y(), deck.z())), "seed " + seed + ": convés da nave inalcancavel");
		}
	}

	@Test
	void nothingFloatsExceptTheIntendedDebris() {
		for (long seed : SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			List<Set<Long>> parts = plan.components();
			assertTrue(parts.getFirst().contains(CitadelPlan.key(0, -1, 0)), "o primeiro componente e a ilha");
			assertEquals(1 + plan.floatingBodies().size(), parts.size(), "seed " + seed + ": pedacos soltos " + describe(plan, parts));
			for (Pos body : plan.floatingBodies()) {
				Set<Long> part = parts.stream().filter(s -> s.contains(CitadelPlan.key(body.x(), body.y(), body.z()))).findFirst().orElseThrow();
				assertTrue(part.size() >= 10, "seed " + seed + ": rocha flutuante pequena demais (" + part.size() + ")");
				long stone = part.stream().filter(k -> VPlan.id(plan.blocks().get(k)).equals("end_stone")).count();
				assertTrue(stone * 3 >= part.size(), "rocha flutuante e uma massa de pedra do End");
			}
			assertTrue(plan.floatingBodies().size() >= 3, "algumas rochas soltas em volta");
		}
	}

	private static String describe(CitadelPlan plan, List<Set<Long>> parts) {
		StringBuilder out = new StringBuilder();
		for (int i = 1; i < parts.size() && out.length() < 800; i++) {
			Set<Long> part = parts.get(i);
			if (plan.floatingBodies().stream().anyMatch(b -> part.contains(CitadelPlan.key(b.x(), b.y(), b.z())))) {
				continue;
			}
			long k = part.iterator().next();
			out.append(parts.get(i).size()).append("x ").append(plan.blocks().get(k)).append(" em ")
					.append(CitadelPlan.keyX(k)).append(',').append(CitadelPlan.keyY(k)).append(',').append(CitadelPlan.keyZ(k)).append("; ");
		}
		return out.toString();
	}

	@Test
	void attachedBlocksHaveSomethingToHangOn() {
		for (long seed : SEEDS) {
			List<String> bad = CitadelPlanner.plan(seed).unsupported();
			assertTrue(bad.isEmpty(), "seed " + seed + ": " + bad.subList(0, Math.min(10, bad.size())));
		}
	}

	@Test
	void themedRoomsArePresent() {
		CitadelPlan plan = CitadelPlanner.plan(42);
		assertTrue(plan.count("bookshelf") >= 40, "biblioteca");
		assertTrue(plan.count("brewing_stand") >= 4, "alquimia e nave");
		assertTrue(plan.count("lectern") >= 2, "biblioteca e observatorio");
		assertTrue(plan.count("magenta_stained_glass") >= 20, "cupula do observatorio");
		assertTrue(plan.count("magenta_wool") + plan.count("purple_wool") >= 60, "velas da nave");
		assertTrue(plan.count("chorus_flower") >= 5, "jardins de coro");
		assertTrue(plan.count("void_lily") >= 20, "lirios do vazio");
		assertTrue(plan.count("end_rod") >= 60, "varas do End iluminando");
		assertTrue(plan.count("magenta_wall_banner") + plan.count("purple_wall_banner") >= 12, "estandartes");
	}

	@Test
	void elevatorFindsNextFreeFloor() {
		OptionalInt up = ElevatorLogic.target(0, true, y -> y % 5 == 0, y -> y != 6, 32);
		assertEquals(10, up.getAsInt(), "pula o andar sem espaco");
		assertEquals(0, ElevatorLogic.target(10, false, y -> y % 5 == 0, y -> y != 6, 32).getAsInt());
		assertFalse(ElevatorLogic.target(0, true, y -> y == 40, y -> true, 32).isPresent(), "fora do alcance");
		assertFalse(ElevatorLogic.target(0, false, y -> y == 0, y -> true, 32).isPresent(), "nada abaixo");
	}

	@Test
	void planIsCheapEnough() {
		long start = System.nanoTime();
		CitadelPlan plan = CitadelPlanner.plan(99);
		long ms = (System.nanoTime() - start) / 1_000_000;
		int size = plan.blocks().size();
		assertTrue(size < 250_000, "blocos: " + size);
		assertTrue(ms < 3000, "planejar levou " + ms + " ms");
		Map<Long, String> blocks = plan.blocks();
		assertFalse(blocks.isEmpty());
	}
}
