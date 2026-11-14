package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plantas das construcoes de oficios e servicos (Vilas 2.0, fase 6): todo nivel cabe na classe de lote do tipo, nada
 * solto no ar, detalhes com apoio, moradores e trabalhadores alcancaveis a pe, cada construcao com o seu posto de
 * trabalho e as pecas que dao a funcao (bau da pedreira, tina, mesa, altar, sino...).
 */
class CraftsPlansTest {
	private static final List<VillageProject> CRAFTS = List.of(VillageProject.QUARRY, VillageProject.TAILOR, VillageProject.TANNERY,
			VillageProject.FLETCHER, VillageProject.ENCHANTER, VillageProject.TAVERN, VillageProject.GARDEN, VillageProject.CHURCH,
			VillageProject.EMPTY_LOT);

	@Test
	void everyLevelFitsTheLotClassOfItsType() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : CRAFTS) {
				SizeClass size = LotRules.of(project.id()).size();
				for (int level = 1; level <= BuildingTypes.maxLevel(project.id()); level++) {
					Canvas c = BuildingPlans.plan(project, style, 3, level);
					assertTrue(size.fits(c.w(), c.d()), project + " " + level + " " + style + " " + c.w() + "x" + c.d() + " nao cabe no lote " + size);
					for (long k : c.cells().keySet()) {
						int x = Canvas.keyX(k);
						int z = Canvas.keyZ(k);
						assertTrue(x >= 0 && x < c.w() && z >= 0 && z < c.d(), project + " " + level + " fora da planta: " + x + "," + z + " "
								+ c.cells().get(k).part());
					}
				}
			}
		}
	}

	@Test
	void everyLevelStandsAndResidentsAreReachable() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : CRAFTS) {
				for (int level = 1; level <= BuildingTypes.maxLevel(project.id()); level++) {
					Canvas canvas = BuildingPlans.plan(project, style, 5, level);
					String what = project + " " + level + " " + style;
					VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
					VillagePlannerTest.Voxels voxels = new VillagePlannerTest.Voxels(terrain);
					VillagePlanner.Lot lot = new VillagePlanner.Lot(10, 10, canvas.w(), canvas.d(), 0, 64);
					voxels.addWithFacing(VillagePlanner.place(canvas, lot));
					assertEquals(List.of(), voxels.floating(), what + ": blocos soltos");
					assertEquals(List.of(), voxels.unsupported(), what + ": detalhes sem apoio");
					int[] start = {10 + canvas.doorX(), 65, 10 + canvas.d()};
					Set<Long> reach = voxels.reach(start[0], start[1], start[2], 30);
					assertFalse(reach.isEmpty(), what + ": frente da porta bloqueada");
					List<String> missing = new ArrayList<>();
					for (int[] s : canvas.spawns()) {
						if (!reach.contains(Placement.key(10 + s[0], 64 + s[1], 10 + s[2]))) {
							missing.add(s[0] + "," + s[1] + "," + s[2]);
						}
					}
					assertEquals(List.of(), missing, what + ": moradores inalcancaveis");
					assertTrue(canvas.spawns().size() >= project.villagers(), what + ": pontos de nascer para os " + project.villagers() + " aldeoes");
				}
			}
		}
	}

	@Test
	void levelOneIsTheBaseOfEveryUpgrade() {
		for (VillageProject project : CRAFTS) {
			for (int level = 2; level <= BuildingTypes.maxLevel(project.id()); level++) {
				Canvas a = BuildingPlans.plan(project, VillageStyle.PLAINS, 4, level - 1);
				Canvas b = BuildingPlans.plan(project, VillageStyle.PLAINS, 4, level);
				assertTrue(b.cells().size() > a.cells().size(), project + ": o nivel " + level + " tem que ter mais blocos que o " + (level - 1));
			}
		}
	}

	private static boolean has(Canvas c, VPart part) {
		return c.cells().values().stream().anyMatch(cell -> cell.part() == part);
	}

	private static long count(Canvas c, VPart part) {
		return c.cells().values().stream().filter(cell -> cell.part() == part).count();
	}

	@Test
	void everyBuildingHasItsWorkstation() {
		for (int level = 1; level <= 3; level++) {
			assertTrue(has(BuildingPlans.plan(VillageProject.QUARRY, VillageStyle.PLAINS, 1, level), VPart.STONECUTTER), "pedreira: cortador");
			assertTrue(has(BuildingPlans.plan(VillageProject.QUARRY, VillageStyle.PLAINS, 1, level), VPart.CHEST), "pedreira: bau que enche");
			assertTrue(has(BuildingPlans.plan(VillageProject.FLETCHER, VillageStyle.PLAINS, 1, level), VPart.FLETCHING), "flecharia: mesa");
			assertTrue(count(BuildingPlans.plan(VillageProject.FLETCHER, VillageStyle.PLAINS, 1, level), VPart.TARGET) >= 2, "flecharia: campo de tiro");
			assertTrue(has(BuildingPlans.plan(VillageProject.CHURCH, VillageStyle.PLAINS, 1, level), VPart.BELL_FLOOR), "igreja: sino");
			assertTrue(has(BuildingPlans.plan(VillageProject.CHURCH, VillageStyle.PLAINS, 1, level), VPart.BREWING), "igreja: altar e posto do clerigo");
		}
		for (int level = 1; level <= 2; level++) {
			assertTrue(has(BuildingPlans.plan(VillageProject.TAILOR, VillageStyle.PLAINS, 1, level), VPart.LOOM), "alfaiataria: tear");
			assertTrue(has(BuildingPlans.plan(VillageProject.TANNERY, VillageStyle.PLAINS, 1, level), VPart.TANNING_VAT), "curtume: tina");
			assertTrue(has(BuildingPlans.plan(VillageProject.TANNERY, VillageStyle.PLAINS, 1, level), VPart.CAULDRON), "curtume: caldeirao do curtidor");
		}
		for (int level = 1; level <= 5; level++) {
			Canvas c = BuildingPlans.plan(VillageProject.ENCHANTER, VillageStyle.PLAINS, 1, level);
			assertEquals(1, count(c, VPart.ENCHANTING_TABLE), "encantador: uma mesa");
			assertTrue(has(c, VPart.LECTERN), "encantador: atril do bibliotecario");
		}
		assertTrue(has(BuildingPlans.plan(VillageProject.TAVERN, VillageStyle.PLAINS, 1, 1), VPart.BARREL), "taverna: barris");
		assertTrue(has(BuildingPlans.plan(VillageProject.TAVERN, VillageStyle.PLAINS, 1, 1), VPart.CARTOGRAPHY), "taverna: mural de boatos");
	}

	@Test
	void tanneryGetsASecondVatAtLevelTwo() {
		assertEquals(1, count(BuildingPlans.plan(VillageProject.TANNERY, VillageStyle.PLAINS, 1, 1), VPart.TANNING_VAT));
		assertEquals(2, count(BuildingPlans.plan(VillageProject.TANNERY, VillageStyle.PLAINS, 1, 2), VPart.TANNING_VAT));
	}

	@Test
	void enchanterBookshelvesGrowWithTheLevel() {
		long last = 0;
		for (int level = 1; level <= 3; level++) {
			long shelves = count(BuildingPlans.plan(VillageProject.ENCHANTER, VillageStyle.PLAINS, 1, level), VPart.BOOKSHELF);
			assertTrue(shelves > last, "estantes crescem no nivel " + level);
			last = shelves;
		}
		// a partir do III o anel em volta da mesa tem as 15 estantes que liberam o maximo
		Canvas c = BuildingPlans.plan(VillageProject.ENCHANTER, VillageStyle.PLAINS, 1, 3);
		int ring = 0;
		for (long k : c.cells().keySet()) {
			if (c.cells().get(k).part() != VPart.BOOKSHELF || Canvas.keyY(k) > 2) {
				continue;
			}
			int dx = Math.abs(Canvas.keyX(k) - 9);
			int dz = Math.abs(Canvas.keyZ(k) - 5);
			if (Math.max(dx, dz) == 2) {
				ring++;
			}
		}
		assertTrue(ring >= 15, "anel de estantes no III: " + ring);
	}

	@Test
	void emptyLotHasNoBuildingJustStakesAndNoDoorOrBed() {
		Canvas c = BuildingPlans.plan(VillageProject.EMPTY_LOT, VillageStyle.PLAINS, 1, 1);
		assertFalse(has(c, VPart.DOOR) || has(c, VPart.BED), "lote vazio nao vem com casa");
		assertTrue(count(c, VPart.FENCE) >= 4, "estacas nos cantos");
		assertEquals(0, project(VillageProject.EMPTY_LOT).villagers());
	}

	private static VillageProject project(VillageProject p) {
		return p;
	}

	@Test
	void churchGetsAGraveyardAtLevelThree() {
		assertFalse(has(BuildingPlans.plan(VillageProject.CHURCH, VillageStyle.PLAINS, 1, 2), VPart.TOMBSTONE));
		assertTrue(has(BuildingPlans.plan(VillageProject.CHURCH, VillageStyle.PLAINS, 1, 3), VPart.TOMBSTONE));
		assertTrue(CraftsRules.hasGraveyard(3) && !CraftsRules.hasGraveyard(2));
	}

	@Test
	void registrationsAreConsistent() {
		for (VillageProject p : CRAFTS) {
			assertTrue(BuildingTypes.of(p.id()) != null, p + " sem tipo com nivel");
			assertTrue(LotRules.all().containsKey(p.id()), p + " sem regra de lote");
			assertTrue(VillageLayout.LOT_PROJECTS.contains(p), p + " fora da lista de projetos com lote");
			assertTrue(p.maxBuilds(VillageLevel.CROWN_CITY) >= 1, p + " nunca pode ser construido");
			assertEquals(p.minLevel(), firstLevelWithBuilds(p), p + ": nivel de desbloqueio e o da primeira obra");
		}
		assertEquals(5, BuildingTypes.maxLevel("enchanter"));
		assertEquals(3, BuildingTypes.maxLevel("quarry"));
		assertEquals(2, BuildingTypes.maxLevel("tailor"));
		assertEquals(2, BuildingTypes.maxLevel("tannery"));
		assertEquals(3, BuildingTypes.maxLevel("fletcher"));
		assertEquals(3, BuildingTypes.maxLevel("tavern"));
		assertEquals(3, BuildingTypes.maxLevel("garden"));
		assertEquals(3, BuildingTypes.maxLevel("church"));
		// quarry e lote vazio abrem na Cidade (arredores abertos; capacidade do plano, ver VillageCapacityTest)
		assertEquals(VillageLevel.TOWN, VillageProject.QUARRY.minLevel());
		assertEquals(VillageLevel.TOWN, VillageProject.ENCHANTER.minLevel());
		assertEquals(VillageLevel.HAMLET, VillageProject.GARDEN.minLevel());
		assertEquals(VillageLevel.TOWN, VillageProject.EMPTY_LOT.minLevel());
		assertTrue(LotRules.of("garden").zones().containsAll(List.of(Zone.CENTER, Zone.DISTRICT, Zone.OUTSKIRTS)), "jardim em qualquer zona");
		assertEquals(SizeClass.T, LotRules.of("garden").size());
		assertEquals(Zone.CENTER, LotRules.of("church").zones().get(0));
		assertEquals(Zone.CENTER, LotRules.of("tavern").zones().get(0));
		assertEquals(Zone.CENTER, LotRules.of("enchanter").zones().get(0));
		assertEquals(Zone.OUTSKIRTS, LotRules.of("quarry").zones().get(0));
		assertEquals(Zone.OUTSKIRTS, LotRules.of("tannery").zones().get(0));
	}

	private static VillageLevel firstLevelWithBuilds(VillageProject p) {
		for (VillageLevel l : VillageLevel.values()) {
			if (p.maxBuilds(l) > 0) {
				return l;
			}
		}
		return null;
	}
}
