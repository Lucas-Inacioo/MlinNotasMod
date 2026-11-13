package com.mlin.notas.dungeon;

import com.mlin.notas.dungeon.DungeonPlan.Doors;
import com.mlin.notas.dungeon.DungeonPlan.Room;
import com.mlin.notas.dungeon.DungeonPlan.RoomType;
import com.mlin.notas.ruins.RuinPlan.Dir;
import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonPlannerTest {
	private static final long[] SEEDS = {0L, 1L, 2L, 3L, 7L, 42L, -7L, 1234L, 99999L, 123456789L, 987654321987L, -55555555L, 31337L, 8L, 9L, 10L};

	private static String what(DungeonTheme theme, long seed) {
		return theme + " seed " + seed;
	}

	@Test
	void sameSeedSamePlan() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			assertEquals(DungeonPlanner.plan(theme, 42).blocks(), DungeonPlanner.plan(theme, 42).blocks());
			assertEquals(DungeonPlanner.plan(theme, 42).chests(), DungeonPlanner.plan(theme, 42).chests());
		}
	}

	@Test
	void seedsVaryTheLayout() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			Set<Integer> shapes = new HashSet<>();
			for (long seed : SEEDS) {
				shapes.add(DungeonPlanner.plan(theme, seed).rooms().stream().mapToInt(r -> r.ci() * 7 + r.cj() * 31 + r.floor() * 101).sum());
			}
			assertTrue(shapes.size() > SEEDS.length / 2, theme + ": layouts repetidos demais");
		}
	}

	@Test
	void roomCountsFloorsAndSpecialRooms() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			for (long seed : SEEDS) {
				DungeonPlan plan = DungeonPlanner.plan(theme, seed);
				String w = what(theme, seed);
				assertTrue(plan.floors() >= 2 && plan.floors() <= 3, w + ": andares " + plan.floors());
				assertTrue(plan.rooms().size() >= 8 && plan.rooms().size() <= 20, w + ": salas " + plan.rooms().size());
				for (RoomType type : new RoomType[] {RoomType.ENTRY, RoomType.BOSS, RoomType.VAULT, RoomType.PUZZLE, RoomType.SPAWNER, RoomType.TRAP}) {
					assertNotNull(plan.room(type), w + ": falta sala " + type);
				}
				assertEquals(1, plan.rooms().stream().filter(r -> r.type() == RoomType.BOSS).count(), w);
				Room boss = plan.room(RoomType.BOSS);
				assertEquals(plan.floors() - 1, boss.floor(), w + ": chefe fora do ultimo andar");
				assertEquals(plan.floors() - 1, plan.rooms().stream().filter(r -> r.type() == RoomType.STAIRS_DOWN).count(), w);
				assertEquals(1, plan.count(DungeonPart.VAULT), w + ": um cofre");
				assertTrue(plan.spawners().size() >= 1, w + ": sem gerador");
				assertNotNull(plan.marker(DungeonPlan.MARKER_GATE), w);
				assertNotNull(plan.marker(DungeonPlan.MARKER_FINAL_CHEST), w);
				assertEquals(DungeonPlan.LOOT_FINAL, plan.chests().get(key(plan.marker(DungeonPlan.MARKER_FINAL_CHEST))), w);
			}
		}
	}

	@Test
	void sizeStaysMedium() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			for (long seed : SEEDS) {
				int[] b = DungeonPlanner.plan(theme, seed).bounds();
				String w = what(theme, seed);
				assertTrue(b[3] - b[0] <= 80 && b[5] - b[2] <= 80, w + ": largo demais " + (b[3] - b[0]) + "x" + (b[5] - b[2]));
				assertTrue(b[1] >= -48 && b[4] <= 16, w + ": alto demais " + b[1] + ".." + b[4]);
			}
		}
	}

	/** Alguma celula do chao da sala (altura dos pes) alcancada. */
	private static boolean reached(Set<Long> reach, Room r) {
		for (int x = r.x0(); x <= r.x1(); x++) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				for (int dy = 0; dy <= 2; dy++) {
					if (reach.contains(DungeonPlan.key(x, r.fy() + 1 + dy, z))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static long key(Pos p) {
		return DungeonPlan.key(p.x(), p.y(), p.z());
	}

	@Test
	void everyRoomReachableOnFootAndFinalChamberOnlyWithTheKey() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			for (long seed : SEEDS) {
				DungeonPlan plan = DungeonPlanner.plan(theme, seed);
				String w = what(theme, seed);
				Pos entrance = plan.marker(DungeonPlan.MARKER_ENTRANCE);
				Set<Long> closed = plan.reachableFrom(entrance, Doors.CLOSED);
				assertTrue(closed.size() > 50, w + ": entrada presa");
				for (Room r : plan.rooms()) {
					if (r.type() == RoomType.VAULT) {
						assertFalse(reached(closed, r), w + ": camara final alcancada sem a chave");
					} else {
						assertTrue(reached(closed, r), w + ": sala " + r.type() + " #" + r.id() + " (andar " + r.floor() + ") inalcancavel");
					}
				}
				// o mini-chefe (quem solta a chave) fica antes do portao
				Pos boss = plan.marker(DungeonPlan.MARKER_BOSS);
				assertTrue(closed.contains(key(boss)), w + ": lugar do mini-chefe inalcancavel em " + boss);
				Set<Long> key = plan.reachableFrom(entrance, Doors.KEY);
				Room vault = plan.room(RoomType.VAULT);
				assertTrue(reached(key, vault), w + ": camara final inalcancavel mesmo com a chave");
				Pos v = plan.marker(DungeonPlan.MARKER_VAULT);
				assertTrue(DungeonPlan.usableFrom(key, v.x(), v.y(), v.z()), w + ": cofre inalcancavel");
				assertFalse(DungeonPlan.usableFrom(closed, v.x(), v.y(), v.z()), w + ": cofre ao alcance sem a chave");

				Set<Long> all = plan.reachableFrom(entrance, Doors.ALL);
				for (Map.Entry<Long, String> chest : plan.chests().entrySet()) {
					long k = chest.getKey();
					int x = DungeonPlan.keyX(k);
					int y = DungeonPlan.keyY(k);
					int z = DungeonPlan.keyZ(k);
					Set<Long> reach = switch (chest.getValue()) {
						case DungeonPlan.LOOT_FINAL -> key;
						case DungeonPlan.LOOT_SECRET -> all;
						default -> closed;
					};
					assertTrue(DungeonPlan.usableFrom(reach, x, y, z), w + ": bau " + chest.getValue() + " em " + x + "," + y + "," + z + " inalcancavel");
				}
			}
		}
	}

	@Test
	void shortcutsOpenOnlyFromTheDeeperSide() {
		int seen = 0;
		for (DungeonTheme theme : DungeonTheme.values()) {
			for (long seed : SEEDS) {
				DungeonPlan plan = DungeonPlanner.plan(theme, seed);
				String w = what(theme, seed);
				Set<Long> closed = plan.reachableFrom(plan.marker(DungeonPlan.MARKER_ENTRANCE), Doors.CLOSED);
				for (DungeonPlan.Shortcut s : plan.shortcuts()) {
					seen++;
					Room inner = plan.rooms().get(s.innerRoom());
					Room outer = plan.rooms().get(s.outerRoom());
					assertTrue(inner.depth() >= outer.depth(), w + ": alavanca do lado raso");
					assertTrue(inner.contains(s.lever().x(), s.lever().z()), w + ": alavanca fora da sala funda");
					assertEquals(DungeonPart.IRON_DOOR, plan.get(s.door().x(), s.door().y(), s.door().z()), w);
					assertEquals(DungeonPart.IRON_DOOR_TOP, plan.get(s.door().x(), s.door().y() + 1, s.door().z()), w);
					assertTrue(DungeonPlan.usableFrom(closed, s.lever().x(), s.lever().y(), s.lever().z()), w + ": alavanca inalcancavel");
					// a alavanca fica presa no bloco ao lado da porta de cima (que recebe o sinal)
					Dir f = plan.facing(s.lever().x(), s.lever().y(), s.lever().z());
					int bx = s.lever().x() - f.dx;
					int bz = s.lever().z() - f.dz;
					assertEquals(1, Math.abs(bx - s.door().x()) + Math.abs(bz - s.door().z()), w + ": alavanca longe da porta");
				}
			}
		}
		assertTrue(seen > 10, "poucos atalhos: " + seen);
	}

	@Test
	void puzzleWireLeadsFromPlateToDoor() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			for (long seed : SEEDS) {
				DungeonPlan plan = DungeonPlanner.plan(theme, seed);
				String w = what(theme, seed);
				DungeonPlan.Puzzle p = plan.puzzle();
				assertNotNull(p, w);
				assertEquals(DungeonPart.PUZZLE_PLATE, plan.get(p.plate().x(), p.plate().y(), p.plate().z()), w);
				assertEquals(DungeonPart.IRON_DOOR, plan.get(p.door().x(), p.door().y(), p.door().z()), w);
				Set<Long> closed = plan.reachableFrom(plan.marker(DungeonPlan.MARKER_ENTRANCE), Doors.CLOSED);
				assertTrue(closed.contains(key(p.plate())), w + ": placa do enigma inalcancavel");
				assertFalse(DungeonPlan.usableFrom(closed, p.chest().x(), p.chest().y(), p.chest().z()), w + ": bau do enigma sem porta");
				// segue a redstone da placa ate a porta: cada passo e um vizinho, no maximo 15 fios
				Set<Long> visited = new HashSet<>();
				Pos at = p.plate();
				int wires = 0;
				boolean door = false;
				while (true) {
					visited.add(key(at));
					Pos next = null;
					for (Dir d : Dir.values()) {
						Pos n = at.offset(d.dx, 0, d.dz);
						if (n.equals(p.door())) {
							door = wires > 0;
						}
						if (!visited.contains(key(n)) && plan.get(n.x(), n.y(), n.z()) == DungeonPart.REDSTONE) {
							next = n;
						}
					}
					if (next == null) {
						break;
					}
					at = next;
					wires++;
				}
				assertTrue(door, w + ": redstone nao chega na porta (" + wires + " fios, parou em " + at + ")");
				assertTrue(wires <= 15, w + ": fio longo demais " + wires);
				// o ultimo fio aponta para a porta (em linha reta com o anterior)
				Pos before = null;
				for (Dir d : Dir.values()) {
					Pos n = at.offset(d.dx, 0, d.dz);
					if (visited.contains(key(n)) && !n.equals(at)) {
						before = n;
					}
				}
				assertNotNull(before, w);
				assertEquals(p.door().x() - at.x(), at.x() - before.x(), w + ": ultimo fio torto");
				assertEquals(p.door().z() - at.z(), at.z() - before.z(), w + ": ultimo fio torto");
			}
		}
	}

	@Test
	void trapsAreFairAndWired() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			for (long seed : SEEDS) {
				DungeonPlan plan = DungeonPlanner.plan(theme, seed);
				String w = what(theme, seed);
				assertFalse(plan.traps().isEmpty(), w + ": sem armadilha");
				for (DungeonPlan.Trap trap : plan.traps()) {
					for (Pos plate : trap.plates()) {
						assertEquals(DungeonPart.TRAP_PLATE, plan.get(plate.x(), plate.y(), plate.z()), w);
						// aviso: ladrilho diferente embaixo da placa
						assertEquals(DungeonPart.TRAP_FLOOR, plan.get(plate.x(), plate.y() - 1, plate.z()), w);
					}
					assertEquals(2, trap.dispensers().size(), w);
					for (Pos d : trap.dispensers()) {
						assertEquals(DungeonPart.DISPENSER, plan.get(d.x(), d.y(), d.z()), w);
						Dir f = plan.facing(d.x(), d.y(), d.z());
						Pos front = d.offset(f.dx, 0, f.dz);
						assertTrue(trap.plates().contains(front), w + ": ejetor " + d + " nao encosta numa placa (aponta para " + front + ")");
					}
				}
			}
		}
	}

	@Test
	void nothingFloats() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			for (long seed : SEEDS) {
				List<Pos> floating = DungeonPlanner.plan(theme, seed).floating();
				assertTrue(floating.isEmpty(), what(theme, seed) + ": soltos " + floating.subList(0, Math.min(8, floating.size()))
						+ " " + floating.stream().limit(8).map(p -> DungeonPlanner.plan(theme, seed).get(p.x(), p.y(), p.z())).toList());
			}
		}
	}

	@Test
	void gateSealsTheFinalChamber() {
		for (DungeonTheme theme : DungeonTheme.values()) {
			for (long seed : SEEDS) {
				DungeonPlan plan = DungeonPlanner.plan(theme, seed);
				String w = what(theme, seed);
				Pos g = plan.marker(DungeonPlan.MARKER_GATE);
				assertEquals(9, plan.count(DungeonPart.GATE), w + ": portao 3x3");
				assertEquals(DungeonPart.GATE, plan.get(g.x(), g.y(), g.z()), w);
				// todo o subsolo fica fechado: nenhum ar do plano encosta em celula fora do plano abaixo do chao
				for (Map.Entry<Long, DungeonPart> e : plan.blocks().entrySet()) {
					if (e.getValue() != DungeonPart.AIR) {
						continue;
					}
					long k = e.getKey();
					int x = DungeonPlan.keyX(k);
					int y = DungeonPlan.keyY(k);
					int z = DungeonPlan.keyZ(k);
					if (y > -2) {
						continue;
					}
					for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}, {0, 1, 0}}) {
						if (y + d[1] <= -2) {
							assertNotNull(plan.get(x + d[0], y + d[1], z + d[2]), w + ": ar aberto para a rocha em " + x + "," + y + "," + z);
						}
					}
				}
			}
		}
	}
}
