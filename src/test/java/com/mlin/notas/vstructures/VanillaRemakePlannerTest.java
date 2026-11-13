package com.mlin.notas.vstructures;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaRemakePlannerTest {
	private static final long[] SEEDS = {0L, 1L, 2L, 3L, 42L, -7L, 1234L, 99999L, 123456789L, 987654321987L, -55555555L, 31337L};

	private static int[] variants(VKind kind) {
		return switch (kind) {
			case PILLAGER_OUTPOST -> new int[] {0, 1, 2, 3, 4, 5};
			case MINESHAFT -> new int[] {0, 1, 2, 3, 4, 5, 6};
			case JUNGLE_TEMPLE -> new int[] {0, 1};
			case WOODLAND_MANSION -> new int[] {0, 1};
			default -> new int[] {0};
		};
	}

	@Test
	void sameSeedSamePlanAndSeedsVary() {
		for (VKind kind : VKind.values()) {
			assertEquals(kind.plan(42, 0).blocks(), kind.plan(42, 0).blocks(), kind + " nao e deterministico");
			boolean differs = false;
			for (long seed : SEEDS) {
				differs |= !kind.plan(seed, 0).blocks().equals(kind.plan(0, 0).blocks());
			}
			assertTrue(differs, kind + " sempre igual");
		}
	}

	@Test
	void fitsInTheStructureReferenceRadius() {
		for (VKind kind : VKind.values()) {
			for (long seed : SEEDS) {
				int[] b = kind.plan(seed, 0).bounds();
				for (int i : new int[] {0, 2, 3, 5}) {
					assertTrue(Math.abs(b[i]) <= 112, kind + " seed " + seed + ": grande demais " + java.util.Arrays.toString(b));
				}
			}
		}
	}

	@Test
	void nothingFloatsAndAttachedBlocksHaveSupport() {
		for (VKind kind : VKind.values()) {
			for (int variant : variants(kind)) {
				for (long seed : SEEDS) {
					VPlan plan = kind.plan(seed, variant);
					Set<Long> floating = plan.floating();
					assertTrue(floating.isEmpty(), kind + "/" + variant + " seed " + seed + ": " + floating.size() + " blocos soltos, ex. "
							+ describe(plan, floating.iterator().hasNext() ? floating.iterator().next() : 0));
					List<String> unsupported = plan.unsupported();
					assertTrue(unsupported.isEmpty(), kind + "/" + variant + " seed " + seed + ": sem apoio " + unsupported.subList(0, Math.min(5, unsupported.size())));
				}
			}
		}
	}

	private static String describe(VPlan plan, long k) {
		return plan.blocks().get(k) + " em " + VPlan.keyX(k) + "," + VPlan.keyY(k) + "," + VPlan.keyZ(k);
	}

	@Test
	void everyContainerAndSpawnerCanBeReachedOnFoot() {
		for (VKind kind : VKind.values()) {
			for (int variant : variants(kind)) {
				for (long seed : SEEDS) {
					VPlan plan = kind.plan(seed, variant);
					if (kind == VKind.JUNGLE_TEMPLE) {
						// a porta secreta aberta (o teste de jogo confere que as alavancas abrem)
						Pos door = plan.marker(JungleTemplePlanner.MARKER_SECRET_DOOR);
						plan.set(door.x(), door.y(), door.z(), VPlan.AIR);
						plan.set(door.x(), door.y() + 1, door.z(), VPlan.AIR);
					}
					if (kind == VKind.STRONGHOLD || kind == VKind.WOODLAND_MANSION) {
						// portas fechadas abrem com a mao ou botao (o teste de jogo confere as portas de ferro)
						SmainPlannerTest.openDoors(plan);
					}
					Pos start = plan.marker(VPlan.MARKER_ENTRANCE);
					assertNotNull(start, kind + " sem entrada");
					Set<Long> reach = plan.reachableFrom(start.x(), start.y(), start.z(), kind == VKind.DESERT_PYRAMID);
					assertTrue(reach.size() > 50, kind + " seed " + seed + ": entrada presa");
					assertTrue(!plan.lootTables().isEmpty(), kind + " sem loot");
					for (long k : plan.lootTables().keySet()) {
						assertTrue(VPlan.usableFrom(reach, VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k)),
								kind + "/" + variant + " seed " + seed + ": " + describe(plan, k) + " inalcancavel");
					}
					for (long k : plan.spawnerMap().keySet()) {
						assertTrue(VPlan.usableFrom(reach, VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k)),
								kind + " seed " + seed + ": gerador " + describe(plan, k) + " inalcancavel");
					}
					for (VPlan.Spawn spawn : plan.entities()) {
						if (spawn.tag().startsWith("loot:")) {
							int x = (int) Math.floor(spawn.x());
							int y = (int) Math.floor(spawn.y());
							int z = (int) Math.floor(spawn.z());
							assertTrue(reach.contains(VPlan.key(x, y, z)) || VPlan.usableFrom(reach, x, y, z),
									kind + " seed " + seed + ": carrinho com bau em " + x + "," + y + "," + z + " inalcancavel");
						}
					}
				}
			}
		}
	}

	@Test
	void spawnersAndMobsStandInValidPlaces() {
		for (VKind kind : VKind.values()) {
			for (long seed : SEEDS) {
				VPlan plan = kind.plan(seed, 0);
				for (long k : plan.spawnerMap().keySet()) {
					int x = VPlan.keyX(k);
					int y = VPlan.keyY(k);
					int z = VPlan.keyZ(k);
					boolean room = !plan.solid(x + 1, y, z) || !plan.solid(x - 1, y, z) || !plan.solid(x, y, z + 1) || !plan.solid(x, y, z - 1);
					assertTrue(room, kind + " seed " + seed + ": gerador emparedado em " + x + "," + y + "," + z);
				}
				for (VPlan.Spawn spawn : plan.entities()) {
					int x = (int) Math.floor(spawn.x());
					int y = (int) Math.floor(spawn.y());
					int z = (int) Math.floor(spawn.z());
					assertTrue(!plan.solid(x, y, z) && !plan.solid(x, y + 1, z) || spawn.type().contains("minecart") || spawn.type().contains("allay"),
							kind + " seed " + seed + ": " + spawn.type() + " dentro de bloco em " + x + "," + y + "," + z);
					if (spawn.type().contains("minecart")) {
						String rail = plan.get(x, y, z);
						assertTrue(rail != null && VPlan.id(rail).equals("rail"), kind + " seed " + seed + ": carrinho fora do trilho em " + x + "," + y + "," + z);
					}
				}
			}
		}
	}

	@Test
	void desertTrapIsWiredLikeVanilla() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.DESERT_PYRAMID.plan(seed, 0);
			Pos plate = plan.marker(DesertPyramidPlanner.MARKER_PLATE);
			assertEquals("stone_pressure_plate", VPlan.id(plan.get(plate.x(), plate.y(), plate.z())));
			String under = plan.get(plate.x(), plate.y() - 1, plate.z());
			assertTrue(BlockTraits.sturdy(under), "placa sobre bloco que nao conduz: " + under);
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					assertEquals("tnt", VPlan.id(plan.get(plate.x() + x, plate.y() - 2, plate.z() + z)), "faltou TNT sob a placa");
				}
			}
			long treasure = plan.lootTables().values().stream().filter(DesertPyramidPlanner.TREASURE::equals).count();
			assertEquals(4, treasure, "a camara tem 4 baus");
			long dig = plan.archaeologyMap().size();
			assertTrue(dig >= 6, "pouca areia suspeita: " + dig);
			// o centro da rosa-dos-ventos e azul e fica em cima da camara
			assertEquals("blue_terracotta", VPlan.id(plan.get(0, -1, 0)));
		}
	}

	@Test
	void jungleTrapsAndPuzzleAreWellFormed() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.JUNGLE_TEMPLE.plan(seed, 0);
			for (String trap : new String[] {JungleTemplePlanner.MARKER_TRAP_1, JungleTemplePlanner.MARKER_TRAP_2}) {
				Pos wire = plan.marker(trap);
				assertEquals("tripwire", VPlan.id(plan.get(wire.x(), wire.y(), wire.z())));
				for (int side : new int[] {-1, 1}) {
					String hook = plan.get(wire.x() + side, wire.y(), wire.z());
					assertEquals("tripwire_hook", VPlan.id(hook));
					assertEquals("true", VPlan.prop(hook, "attached"));
					// o despenser fica em cima do bloco que segura o gancho (recebe sinal forte dele) e mira o corredor
					String dispenser = plan.get(wire.x() + 2 * side, wire.y() + 1, wire.z());
					assertEquals("dispenser", VPlan.id(dispenser));
					assertEquals(side < 0 ? "east" : "west", VPlan.prop(dispenser, "facing"));
					assertTrue(BlockTraits.sturdy(plan.get(wire.x() + 2 * side, wire.y(), wire.z())));
					assertEquals(JungleTemplePlanner.DISPENSER, plan.lootTables().get(VPlan.key(wire.x() + 2 * side, wire.y() + 1, wire.z())));
				}
			}
			Pos door = plan.marker(JungleTemplePlanner.MARKER_SECRET_DOOR);
			for (int y = 0; y <= 1; y++) {
				assertEquals("sticky_piston", VPlan.id(plan.get(door.x(), door.y() + y, door.z() + 2)));
				assertEquals("piston_head", VPlan.id(plan.get(door.x(), door.y() + y, door.z() + 1)));
				assertTrue(BlockTraits.sturdy(plan.get(door.x(), door.y() + y, door.z())));
			}
			int levers = 0;
			for (int i = 0; i < 3; i++) {
				Pos lever = plan.marker(JungleTemplePlanner.MARKER_LEVER + i);
				assertEquals("lever", VPlan.id(plan.get(lever.x(), lever.y(), lever.z())));
				levers++;
			}
			assertEquals(3, levers);
			assertNotNull(plan.marker(JungleTemplePlanner.MARKER_WRONG_LEVER));
			// sem a porta aberta o bau escondido nao e alcancavel
			Pos start = plan.marker(VPlan.MARKER_ENTRANCE);
			Set<Long> closed = plan.reachableFrom(start.x(), start.y(), start.z(), false);
			Pos hidden = plan.marker(JungleTemplePlanner.MARKER_HIDDEN_CHEST);
			assertTrue(!VPlan.usableFrom(closed, hidden.x(), hidden.y(), hidden.z()), "a sala escondida esta aberta");
		}
	}

	@Test
	void outpostKeepsVanillaIdentity() {
		for (int variant : variants(VKind.PILLAGER_OUTPOST)) {
			for (long seed : SEEDS) {
				VPlan plan = VKind.PILLAGER_OUTPOST.plan(seed, variant);
				assertEquals(OutpostPlanner.TOP_CHEST, plan.lootTables().get(key(plan.marker(OutpostPlanner.MARKER_TOP_CHEST))));
				assertTrue(plan.marker(OutpostPlanner.MARKER_GOLEM_CAGE) != null, "sem jaula do golem");
				assertEquals(1, plan.entities().stream().filter(s -> s.tag().equals("captain")).count(), "um capitao");
				long golems = plan.entities().stream().filter(s -> s.type().contains("golem")).count();
				assertEquals(1, golems);
				long banners = plan.tagMap().values().stream().filter("ominous"::equals).count();
				assertTrue(banners >= 8, "poucos estandartes: " + banners);
				long pillagers = plan.entities().stream().filter(s -> s.type().equals("minecraft:pillager")).count();
				assertTrue(pillagers >= 5, "poucos saqueadores");
			}
		}
	}

	private static long key(Pos p) {
		return VPlan.key(p.x(), p.y(), p.z());
	}

	@Test
	void mineshaftIsALargeNetworkWithChestCarts() {
		for (VKind kind : new VKind[] {VKind.MINESHAFT, VKind.MINESHAFT_MESA}) {
			int totalCarts = 0;
			for (long seed : SEEDS) {
				VPlan plan = kind.plan(seed, 0);
				int[] b = plan.bounds();
				assertTrue(b[3] - b[0] >= 40 || b[5] - b[2] >= 40, kind + " seed " + seed + ": rede pequena " + java.util.Arrays.toString(b));
				assertTrue(plan.groupMap().values().stream().distinct().count() >= 8, kind + " seed " + seed + ": poucos trechos");
				totalCarts += (int) plan.entities().stream().filter(s -> s.tag().startsWith("loot:")).count();
				assertTrue(plan.count("rail") > 40, "poucos trilhos");
			}
			assertTrue(totalCarts >= SEEDS.length * 2, kind + ": poucos carrinhos com bau");
		}
	}

	@Test
	void everyStateTextIsWellFormed() {
		for (VKind kind : VKind.values()) {
			for (int variant : variants(kind)) {
				VPlan plan = kind.plan(5, variant);
				for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
					String s = e.getValue();
					assertTrue(s.startsWith("minecraft:") || s.startsWith("mlinnotas:"), s);
					int open = s.indexOf('[');
					if (open >= 0) {
						assertTrue(s.endsWith("]") && !s.contains("[]") && !s.substring(open + 1).contains("["), s);
					}
					assertNotEquals("", VPlan.id(s));
				}
			}
		}
	}
}
