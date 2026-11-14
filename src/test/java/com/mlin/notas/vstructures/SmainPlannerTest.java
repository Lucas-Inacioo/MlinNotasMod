package com.mlin.notas.vstructures;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fortaleza e mansao refeitas: o que torna cada uma reconhecivel e jogavel. */
class SmainPlannerTest {
	private static final long[] SEEDS = {0L, 1L, 2L, 3L, 42L, -7L, 1234L, 99999L, 123456789L, 987654321987L, -55555555L, 31337L};

	/** Abre as portas (de madeira e de ferro) e as paredes secretas marcadas, como o jogador faria. */
	static void openDoors(VPlan plan) {
		List<Long> doors = new ArrayList<>();
		for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
			if (VPlan.id(e.getValue()).endsWith("_door")) {
				doors.add(e.getKey());
			}
		}
		String air = plan.underground() ? VPlan.CAVE_AIR : VPlan.AIR;
		for (long k : doors) {
			plan.set(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k), air);
		}
		for (Map.Entry<String, Pos> m : plan.markers().entrySet()) {
			if (m.getKey().startsWith(MansionPlanner.MARKER_SECRET_WALL)) {
				Pos s = m.getValue();
				plan.set(s.x(), s.y(), s.z(), air);
				plan.set(s.x(), s.y() + 1, s.z(), air);
			}
		}
	}

	@Test
	void strongholdHasAWorkingPortalFrame() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.STRONGHOLD.plan(seed, 0);
			Pos c = plan.marker(StrongholdPlanner.MARKER_PORTAL);
			assertNotNull(c, "sem sala do portal");
			int frames = 0;
			for (int k = -1; k <= 1; k++) {
				frames += frame(plan, c.x() + k, c.y(), c.z() - 2, "south");
				frames += frame(plan, c.x() + k, c.y(), c.z() + 2, "north");
				frames += frame(plan, c.x() - 2, c.y(), c.z() + k, "east");
				frames += frame(plan, c.x() + 2, c.y(), c.z() + k, "west");
				for (int j = -1; j <= 1; j++) {
					assertTrue(BlockTraits.isAir(plan.get(c.x() + k, c.y(), c.z() + j)), "o meio do portal tem de estar livre");
					assertEquals("lava", VPlan.id(plan.get(c.x() + k, c.y() - 1, c.z() + j)), "lava embaixo do portal");
				}
			}
			assertEquals(12, frames);
			assertEquals(12, plan.count("end_portal_frame"), "molduras a mais");
			Pos spawner = plan.marker(StrongholdPlanner.MARKER_PORTAL_SPAWNER);
			assertEquals("minecraft:silverfish", plan.spawnerMap().get(VPlan.key(spawner.x(), spawner.y(), spawner.z())));
			// a sala do portal e alcancavel a pe com as portas abertas: o estrado inteiro
			openDoors(plan);
			Pos start = plan.marker(VPlan.MARKER_ENTRANCE);
			Set<Long> reach = plan.reachableFrom(start.x(), start.y(), start.z(), false);
			for (int k = -1; k <= 1; k++) {
				assertTrue(VPlan.usableFrom(reach, c.x() + k, c.y(), c.z() - 2), "seed " + seed + ": moldura inalcancavel");
			}
		}
	}

	private static int frame(VPlan plan, int x, int y, int z, String facing) {
		String s = plan.get(x, y, z);
		assertNotNull(s, "falta moldura em " + x + "," + y + "," + z);
		assertEquals("end_portal_frame", VPlan.id(s));
		assertEquals(facing, VPlan.prop(s, "facing"), "moldura virada para fora em " + x + "," + y + "," + z);
		return 1;
	}

	@Test
	void strongholdIsALabyrinthOnTwoLevelsWithTheVanillaRooms() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.STRONGHOLD.plan(seed, 0);
			for (String marker : new String[] {StrongholdPlanner.MARKER_LIBRARY, StrongholdPlanner.MARKER_STAIRS, StrongholdPlanner.MARKER_CRYPT,
					StrongholdPlanner.MARKER_PRISON, StrongholdPlanner.MARKER_FOUNTAIN}) {
				assertNotNull(plan.marker(marker), "seed " + seed + ": falta " + marker);
			}
			Map<Long, String> loot = plan.lootTables();
			assertTrue(loot.values().stream().filter(StrongholdPlanner.LIBRARY::equals).count() >= 4, "baus de biblioteca");
			assertTrue(loot.values().stream().filter(StrongholdPlanner.CORRIDOR::equals).count() >= 3, "baus de corredor");
			assertTrue(loot.values().stream().filter(StrongholdPlanner.CROSSING::equals).count() >= 2, "baus de cruzamento");
			assertTrue(plan.count("iron_door") >= 4, "portas de ferro");
			int[] b = plan.bounds();
			assertTrue(b[3] - b[0] >= 60 && b[5] - b[2] >= 60, "pequena demais: " + java.util.Arrays.toString(b));
			long infested = plan.blocks().values().stream().filter(s -> VPlan.id(s).startsWith("infested_")).count();
			assertTrue(infested > 100, "poucos blocos infestados: " + infested);
			// os dois andares sao alcancados
			openDoors(plan);
			Pos start = plan.marker(VPlan.MARKER_ENTRANCE);
			Set<Long> reach = plan.reachableFrom(start.x(), start.y(), start.z(), false);
			Pos stairs = plan.marker(StrongholdPlanner.MARKER_STAIRS);
			assertTrue(reach.contains(VPlan.key(stairs.x(), stairs.y(), stairs.z())), "seed " + seed + ": pe da escadaria inalcancavel");
		}
	}

	@Test
	void strongholdIronDoorsHaveButtonsOnTheBrickBesideThem() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.STRONGHOLD.plan(seed, 0);
			for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
				String s = e.getValue();
				if (!VPlan.id(s).equals("iron_door") || !"upper".equals(VPlan.prop(s, "half"))) {
					continue;
				}
				int x = VPlan.keyX(e.getKey());
				int y = VPlan.keyY(e.getKey());
				int z = VPlan.keyZ(e.getKey());
				// algum botao encosta num bloco vizinho da metade de cima (ou em cima dela)
				boolean powered = false;
				int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
				for (int[] a : around) {
					int bx = x + a[0];
					int by = y + a[1];
					int bz = z + a[2];
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						String button = plan.get(bx + d[0], by, bz + d[1]);
						if (button != null && VPlan.id(button).equals("stone_button")) {
							int[] back = BlockTraits.supportOffset(button);
							powered |= back[0] == -d[0] && back[2] == -d[1];
						}
					}
				}
				assertTrue(powered, "seed " + seed + ": porta de ferro sem botao em " + x + "," + y + "," + z);
			}
		}
	}

	@Test
	void strongholdTrapsAreWired() {
		int traps = 0;
		for (long seed : SEEDS) {
			VPlan plan = VKind.STRONGHOLD.plan(seed, 0);
			for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
				if (!VPlan.id(e.getValue()).equals("tripwire")) {
					continue;
				}
				traps++;
				int x = VPlan.keyX(e.getKey());
				int y = VPlan.keyY(e.getKey());
				int z = VPlan.keyZ(e.getKey());
				boolean xAxis = "true".equals(VPlan.prop(e.getValue(), "east"));
				int ax = xAxis ? 1 : 0;
				int az = xAxis ? 0 : 1;
				for (int side : new int[] {-1, 1}) {
					assertEquals("tripwire_hook", VPlan.id(plan.get(x + side * ax, y, z + side * az)));
					String dispenser = plan.get(x + 2 * side * ax, y + 1, z + 2 * side * az);
					assertEquals("dispenser", VPlan.id(dispenser));
					assertEquals(StrongholdPlanner.TRAP, plan.lootTables().get(VPlan.key(x + 2 * side * ax, y + 1, z + 2 * side * az)));
				}
			}
		}
		assertTrue(traps >= SEEDS.length, "poucas armadilhas: " + traps);
	}

	@Test
	void mansionHasThreeFloorsSecretsMobsAndVanillaLoot() {
		for (int variant : new int[] {0, MansionPlanner.PALE}) {
			for (long seed : SEEDS) {
				VPlan plan = VKind.WOODLAND_MANSION.plan(seed, variant);
				for (int f = 0; f <= 2; f++) {
					assertNotNull(plan.marker(MansionPlanner.MARKER_HALL + f), "falta o salao do andar " + f);
				}
				assertNotNull(plan.marker(MansionPlanner.MARKER_ARENA), "seed " + seed + ": sem arena");
				assertNotNull(plan.marker(MansionPlanner.MARKER_VAULT), "sem cofre");
				assertNotNull(plan.marker(MansionPlanner.MARKER_ATTIC), "sem sotao");
				long chests = plan.lootTables().values().stream().filter(MansionPlanner.LOOT::equals).count();
				assertTrue(chests >= 8, "poucos baus: " + chests);
				long vindicators = plan.entities().stream().filter(s -> s.type().equals("minecraft:vindicator")).count();
				long evokers = plan.entities().stream().filter(s -> s.type().equals("minecraft:evoker")).count();
				long allays = plan.entities().stream().filter(s -> s.type().equals("minecraft:allay")).count();
				assertTrue(vindicators >= 7, "poucos vindicadores: " + vindicators);
				assertTrue(evokers >= 1, "sem evocador");
				assertTrue(allays >= 1, "sem allay presa");
				if (variant == MansionPlanner.PALE) {
					assertTrue(plan.blocks().values().stream().noneMatch(s -> s.contains("dark_oak")), "sobrou carvalho escuro");
				}
				// o cofre so abre quebrando as estantes; o sotao e alcancavel pela escada
				Pos start = plan.marker(VPlan.MARKER_ENTRANCE);
				Pos vault = plan.marker(MansionPlanner.MARKER_VAULT);
				VPlan closed = VKind.WOODLAND_MANSION.plan(seed, variant);
				Pos wall = closed.marker(MansionPlanner.MARKER_SECRET_WALL + 0);
				assertEquals("bookshelf", VPlan.id(closed.get(wall.x(), wall.y(), wall.z())));
				for (Map.Entry<Long, String> e : closed.blocks().entrySet()) {
					if (VPlan.id(e.getValue()).endsWith("_door")) {
						closed.set(VPlan.keyX(e.getKey()), VPlan.keyY(e.getKey()), VPlan.keyZ(e.getKey()), VPlan.AIR);
					}
				}
				Set<Long> reach = closed.reachableFrom(start.x(), start.y(), start.z(), false);
				assertTrue(!VPlan.usableFrom(reach, vault.x(), vault.y(), vault.z()), "seed " + seed + ": o cofre esta aberto");
				Pos attic = closed.marker(MansionPlanner.MARKER_ATTIC);
				assertTrue(reach.contains(VPlan.key(attic.x(), attic.y(), attic.z())), "seed " + seed + ": sotao inalcancavel");
			}
		}
	}
}
