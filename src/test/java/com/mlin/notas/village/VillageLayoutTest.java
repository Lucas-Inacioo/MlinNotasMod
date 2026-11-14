package com.mlin.notas.village;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plano da vila: lotes, ruas e postes estaveis entre niveis; preparo do terreno; devolucoes; formato antigo. */
class VillageLayoutTest {
	private static VillageState village(VillageLevel level) {
		VillageState v = new VillageState("minecraft:overworld", 0, 64, 0, 4321L, VillageStyle.PLAINS, "Teste");
		v.setLevel(level);
		return v;
	}

	private record LotKey(int id, String kind, int x, int z, int rot) {
	}

	private static Map<Integer, LotKey> snapshot(VillageLayout layout) {
		Map<Integer, LotKey> out = new LinkedHashMap<>();
		for (VillageLayout.Lot lot : layout.lots()) {
			out.put(lot.id(), new LotKey(lot.id(), lot.kind(), lot.x(), lot.z(), lot.rot()));
		}
		return out;
	}

	private static void assertSane(VillageLayout layout, int radius) {
		List<VillageLayout.Lot> lots = layout.lots();
		for (int i = 0; i < lots.size(); i++) {
			VillageState.Rect a = lots.get(i).rect();
			for (int j = i + 1; j < lots.size(); j++) {
				assertFalse(a.overlaps(lots.get(j).rect(), VillageLayout.LOT_GAP - 1), "lotes " + i + " e " + j + " encostam");
			}
			for (int[] c : new int[][] {{a.x0(), a.z0()}, {a.x1(), a.z0()}, {a.x0(), a.z1()}, {a.x1(), a.z1()}}) {
				assertTrue(Math.abs(c[0]) <= StreetPlanner.OUTER && Math.abs(c[1]) <= StreetPlanner.OUTER, "lote fora do plano");
			}
			for (int x = a.x0() - 1; x <= a.x1() + 1; x++) {
				for (int z = a.z0() - 1; z <= a.z1() + 1; z++) {
					if (a.contains(x, z)) {
						assertFalse(layout.isRoad(x, z), "rua dentro do lote " + lots.get(i).kind());
						assertTrue(x * x + z * z > VillageLayout.LOT_CLEARANCE * VillageLayout.LOT_CLEARANCE, "lote na praca");
					}
				}
			}
			assertFalse(lots.get(i).access().isEmpty(), "lote sem acesso");
		}
		// postes: espacados, fora dos lotes e das ruas, encostados numa rua
		List<VillageLayout.Lamp> lamps = layout.lamps();
		for (int i = 0; i < lamps.size(); i++) {
			VillageLayout.Lamp a = lamps.get(i);
			assertNull(layout.lotAt(a.x(), a.z()), "poste dentro de lote");
			assertFalse(layout.isRoad(a.x(), a.z()), "poste no meio da rua");
			boolean byRoad = false;
			for (Facing f : Facing.values()) {
				byRoad |= layout.isRoad(a.x() + f.dx, a.z() + f.dz);
			}
			assertTrue(byRoad, "poste longe da rua");
			for (int j = i + 1; j < lamps.size(); j++) {
				VillageLayout.Lamp b = lamps.get(j);
				int dx = a.x() - b.x();
				int dz = a.z() - b.z();
				assertTrue(dx * dx + dz * dz >= VillageLayout.LAMP_SPACING * VillageLayout.LAMP_SPACING, "postes colados");
			}
			for (int[] p : VillageLayout.PLAZA_LAMPS) {
				int dx = a.x() - p[0];
				int dz = a.z() - p[1];
				assertTrue(dx * dx + dz * dz >= VillageLayout.LAMP_SPACING * VillageLayout.LAMP_SPACING, "poste colado nos da praca");
			}
		}
	}

	@Test
	void lotsAreReservedPerProjectAndStayPutAcrossLevelUps() {
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 64 + Math.floorDiv(x, 9));
		VillageState v = village(VillageLevel.CAMP);
		VillageLayout layout = VillageLayout.create(v, terrain);
		v.setLayout(layout);
		assertSane(layout, VillageLevel.CAMP.radius());
		for (VillageLevel level : VillageLevel.values()) {
			if (level != VillageLevel.CAMP) {
				Map<Integer, LotKey> before = snapshot(layout);
				Set<Long> roads = Set.copyOf(layout.roads());
				int lamps = layout.lamps().size();
				v.setLevel(level);
				layout.extend(v, terrain, level);
				Map<Integer, LotKey> after = snapshot(layout);
				before.forEach((id, lot) -> assertEquals(lot, after.get(id), level + ": lote antigo mudou"));
				assertTrue(layout.roads().containsAll(roads), level + ": rua antiga sumiu");
				assertTrue(layout.lamps().size() >= lamps, level + ": poste antigo sumiu");
				assertSane(layout, level.radius());
			}
			for (VillageProject project : VillageLayout.LOT_PROJECTS) {
				assertEquals(project.maxBuilds(level), layout.lotsOf(project.id()).size(), level + ": lotes de " + project.id());
			}
		}
	}

	@Test
	void playerColumnsAndProtectedAreasAreNeverPlanned() {
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 70);
		for (int x = 10; x <= 20; x++) {
			for (int z = -20; z <= -10; z++) {
				terrain.blocked.add(VillagePlannerTest.FakeTerrain.col(x, z));
			}
		}
		VillageState v = village(VillageLevel.CAPITAL);
		VillageLayout layout = new VillageLayout();
		layout.addProtected(new VillageState.Rect(-40, -40, -20, -20));
		v.setLayout(layout);
		layout.extend(v, terrain, VillageLevel.CAPITAL);
		for (VillageLayout.Lot lot : layout.lots()) {
			VillageState.Rect r = lot.rect();
			assertFalse(r.overlaps(new VillageState.Rect(10, -20, 20, -10), 0), "lote em cima da casa do jogador");
			assertFalse(r.overlaps(new VillageState.Rect(-40, -40, -20, -20), 0), "lote na area protegida");
		}
		for (long k : layout.roads()) {
			assertFalse(new VillageState.Rect(-40, -40, -20, -20).contains(VillageLayout.colX(k), VillageLayout.colZ(k)), "rua na area protegida");
		}
		assertTrue(layout.lots().size() >= 12, "mesmo com obstaculos cabem varios lotes: " + layout.lots().size());
	}

	@Test
	void lampUpgradeOnlyAddsPostsAtNewSpots() {
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		VillageState v = village(VillageLevel.CAMP);
		VillagePlannerTest.prepareAll(v, terrain);
		VillagePlanner.Planned first = VillagePlanner.plan(VillageProject.LAMPS, v, terrain, 1);
		assertNotNull(first);
		Set<Long> posts = new java.util.HashSet<>();
		for (Placement p : first.placements()) {
			if (p.part() == VPart.LANTERN) {
				posts.add(VillageLayout.col(p.x(), p.z()));
			}
		}
		terrain.apply(first.placements());
		assertNull(VillagePlanner.plan(VillageProject.LAMPS, v, terrain, 2), "sem rua nova nao ha poste novo");
		v.setLevel(VillageLevel.VILLAGE);
		v.layout().extend(v, terrain, VillageLevel.VILLAGE);
		VillagePlannerTest.prepareAll(v, terrain);
		VillagePlanner.Planned second = VillagePlanner.plan(VillageProject.LAMPS, v, terrain, 3);
		assertNotNull(second, "o nivel novo traz lugares de poste novos");
		for (Placement p : second.placements()) {
			if (p.part() == VPart.LANTERN) {
				long k = VillageLayout.col(p.x(), p.z());
				assertFalse(posts.contains(k), "poste repetido no mesmo lugar");
				for (long old : posts) {
					int dx = VillageLayout.colX(old) - p.x();
					int dz = VillageLayout.colZ(old) - p.z();
					assertTrue(dx * dx + dz * dz >= VillageLayout.LAMP_SPACING * VillageLayout.LAMP_SPACING, "poste novo colado num antigo");
				}
			}
		}
	}

	// ---------------------------------------------------------------- preparo do terreno

	/** Topo solido final da coluna no mundo de voxels (terreno + obras). */
	private static int top(VillagePlannerTest.Voxels voxels, int x, int z, int from) {
		for (int y = from + 30; y >= from - 30; y--) {
			VPart part = voxels.part(x, y, z);
			if (part == null ? y <= voxels.terrain.ground(x, z) : part.solid()) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	@Test
	void slopesArePreparedSoNothingFloatsOrIsEmbeddedAndTheDoorIsWalkable() {
		List<IntBinaryOperator> slopes = List.of((x, z) -> 64 + Math.floorDiv(x, 2), (x, z) -> 64 + Math.floorDiv(x + z, 3),
				(x, z) -> 64 + (int) Math.round(3 * Math.sin(x / 5.0) + 2 * Math.cos(z / 4.0)), (x, z) -> 64 - Math.floorDiv(z, 2));
		int checked = 0;
		for (IntBinaryOperator slope : slopes) {
			VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain(slope);
			VillagePlannerTest.Voxels voxels = new VillagePlannerTest.Voxels(terrain);
			VillageState v = village(VillageLevel.TOWN);
			for (VillageProject project : List.of(VillageProject.HOUSE, VillageProject.FARM, VillageProject.HOUSE, VillageProject.SMITHY,
					VillageProject.MARKET)) {
				VillagePlannerTest.prepareAll(v, terrain);
				VillagePlanner.Planned planned = VillagePlanner.plan(project, v, terrain, 5);
				if (planned == null) {
					continue;
				}
				checked++;
				VillageLayout.Lot lot = v.layout().lot(planned.lotId());
				lot.setBuilt(true);
				v.addLot(lot.rect());
				voxels.addWithFacing(planned.placements());
				terrain.apply(planned.placements());
				int target = planned.lot().groundY();
				VillageState.Rect r = lot.rect();
				// o lote inteiro fica na altura alvo e nada da obra fica enterrado
				for (int x = r.x0(); x <= r.x1(); x++) {
					for (int z = r.z0(); z <= r.z1(); z++) {
						VPart layer = voxels.part(x, target, z);
						assertTrue(layer != null && layer != VPart.AIR || top(voxels, x, z, target) >= target,
								project + ": chao do lote abaixo do alvo em " + x + "," + z);
						for (int y = target + 1; y <= target + 3; y++) {
							VPart part = voxels.part(x, y, z);
							assertFalse(part == null && y <= terrain.ground(x, z), project + ": terra natural dentro da obra em " + x + "," + y + "," + z);
						}
					}
				}
				// da porta ate o fim do acesso, a pe, sem degrau maior que 1
				int prev = target;
				for (int[] c : lot.access()) {
					int h = top(voxels, c[0], c[1], prev);
					assertTrue(Math.abs(h - prev) <= 1, project + ": degrau de " + Math.abs(h - prev) + " no acesso em " + c[0] + "," + c[1]);
					prev = h;
				}
				int[] door = lot.front();
				int[] end = lot.access().getLast();
				var reach = voxels.reach(door[0], top(voxels, door[0], door[1], target) + 1, door[1], 40);
				assertTrue(reach.contains(Placement.key(end[0], top(voxels, end[0], end[1], target) + 1, end[1])), project + ": acesso nao chega na rua");
			}
			assertEquals(List.of(), voxels.floating(), "blocos soltos no ar");
		}
		assertTrue(checked >= 10, "poucas obras couberam nas encostas: " + checked);
	}

	@Test
	void prepTargetsTheMedianAndLeavesPlayerColumnsAlone() {
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 60 + x);
		terrain.blocked.add(VillagePlannerTest.FakeTerrain.col(-2, 3));
		VillageState.Rect lot = new VillageState.Rect(0, 0, 6, 6);
		TerrainPrep.Result result = TerrainPrep.prepare(lot, List.of(), terrain, k -> false);
		assertEquals(63, result.target());
		for (Placement p : result.placements()) {
			assertFalse(p.x() == -2 && p.z() == 3, "mexeu na coluna do jogador");
		}
		// muro de arrimo na borda que ficou 2+ acima do chao natural
		assertTrue(result.placements().stream().anyMatch(p -> p.part() == VPart.RETAIN), "aterro alto sem muro de arrimo");
	}

	// ---------------------------------------------------------------- devolucoes

	@Test
	void refundsGiveBackPaidItemsOrTheCost() {
		Map<String, Integer> paid = Map.of("minecraft:spruce_planks", 64, "minecraft:oak_log", 13, "minecraft:red_bed", 1);
		assertEquals(paid, Refunds.of(VillageProject.HOUSE, paid, 1.0, id -> "x"));
		Map<String, Integer> half = Refunds.of(VillageProject.HOUSE, paid, Refunds.DEMOLISH_FRACTION, id -> "x");
		assertEquals(Map.of("minecraft:spruce_planks", 32, "minecraft:oak_log", 6), half);
		Map<String, Integer> fromCost = Refunds.of(VillageProject.FARM, Map.of(), 0.5, id -> id.equals("#mlinnotas:village/food") ? "minecraft:bread"
				: "minecraft:wheat_seeds");
		assertEquals(Map.of("minecraft:bread", 16, "minecraft:wheat_seeds", 8), fromCost);
		assertEquals(38, Refunds.total(half));
	}

	@Test
	void paidItemsFollowTheQueueAndCancelGivesThemBack() {
		VillageState v = village(VillageLevel.CAMP);
		Map<String, Integer> half = Map.of("#minecraft:planks", 30);
		VillageState.Deposit d1 = v.deposit(VillageProject.HOUSE, half);
		v.recordPaid(VillageProject.HOUSE, Map.of("minecraft:oak_planks", 30), d1.outcome() == VillageState.Outcome.COMPLETE);
		Map<String, Integer> rest = new HashMap<>();
		for (VillageCost cost : VillageProject.HOUSE.costs()) {
			rest.put(cost.id(), 999);
		}
		VillageState.Deposit d2 = v.deposit(VillageProject.HOUSE, rest);
		assertEquals(VillageState.Outcome.COMPLETE, d2.outcome());
		v.recordPaid(VillageProject.HOUSE, Map.of("minecraft:spruce_planks", 34, "minecraft:glass", 6), true);
		assertEquals(Map.of("minecraft:oak_planks", 30, "minecraft:spruce_planks", 34, "minecraft:glass", 6), v.queuedPaid(VillageProject.HOUSE));
		assertTrue(v.paid(VillageProject.HOUSE).isEmpty());
		var cancelled = v.cancelQueued(0);
		assertEquals(VillageProject.HOUSE, cancelled.getKey());
		assertEquals(70, Refunds.total(cancelled.getValue()));
		assertTrue(v.queue().isEmpty());
		// demolir tira a obra da conta e a prosperidade que ela deu
		v.enqueue(VillageProject.FARM);
		v.finishBuild(VillageProject.FARM);
		int prosperity = v.prosperity();
		v.unbuild(VillageProject.FARM);
		assertEquals(0, v.built(VillageProject.FARM));
		assertEquals(prosperity - VillageProject.FARM.prosperity(), v.prosperity());
	}

	// ---------------------------------------------------------------- formato antigo

	private static final String OLD_FORMAT = """
			{"villages":[{"core":{"dimension":"minecraft:overworld","x":-40,"y":93,"z":112,"seed":99,"style":"plains","name":"Vila Velha",
			"starter":true},"progress":{"level":1,"prosperity":120,"built":{"plaza":1,"house":2,"farm":1,"lamps":1},
			"deposits":{"smithy":{"minecraft:iron_ingot":10}},"queue":["house"],"lots":[8,8,16,16,-20,8,-10,18,8,-20,18,-12],
			"bounty_day":5,"bounties":[],"bounty_progress":[0,0,0],"bounty_done":[false,false,false]}}],
			"jobs":[{"village":"minecraft:overworld@-40,93,112","project":"house","positions":[1,2],"parts":[16,16],"index":1}],
			"starter_checked":true}
			""";

	@Test
	void oldVillagesFileLoadsAndGetsALayoutAroundTheOldBuildings() {
		VillageData data = VillageData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(OLD_FORMAT)).getOrThrow();
		assertTrue(data.starterChecked());
		assertNull(data.starterSite());
		VillageState v = data.villages().iterator().next();
		assertEquals("Vila Velha", v.name());
		assertEquals(VillageLevel.HAMLET, v.level());
		assertEquals(2, v.built(VillageProject.HOUSE));
		assertEquals(List.of(VillageProject.HOUSE), v.queue());
		assertEquals(Map.of(), v.queuedPaid(VillageProject.HOUSE));
		assertEquals(3, v.lots().size());
		assertNull(v.layout(), "vila antiga carrega sem plano");
		VillageData.Job job = data.job(v.key());
		assertNotNull(job);
		assertEquals(-1, job.lot);
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 93);
		VillageLayout layout = VillageLayout.create(v, terrain);
		long legacy = layout.lots().stream().filter(VillageLayout.Lot::legacy).count();
		assertEquals(3, legacy);
		// as 2 casas e a plantacao antigas contam: o plano so reserva o que falta no nivel
		int houses = layout.lotsOf("house").size();
		assertTrue(houses >= 1 && houses <= VillageProject.HOUSE.maxBuilds(VillageLevel.HAMLET) - 2, "lotes de casa: " + houses);
		for (VillageLayout.Lot lot : layout.lots()) {
			if (!lot.legacy()) {
				for (VillageState.Rect old : v.lots()) {
					assertFalse(lot.rect().overlaps(old, 0), "lote novo em cima de obra antiga");
				}
			}
		}
		// e o formato novo volta igual
		v.setLayout(layout);
		var json = VillageData.VILLAGE_CODEC.encodeStart(JsonOps.INSTANCE, v).getOrThrow();
		VillageState again = VillageData.VILLAGE_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(layout.lots().size(), again.layout().lots().size());
		assertEquals(layout.roads(), again.layout().roads());
		List<String> kinds = new ArrayList<>();
		again.layout().lots().forEach(l -> kinds.add(l.kind()));
		assertTrue(kinds.contains("farm"));
	}
}
