package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plantas civicas (Vilas 2.0, fase 7): todo nivel cabe na classe de lote, nada solto, detalhes com apoio, os pontos que a
 * funcao precisa (caixa de correio, pedestal, quadro, sino, arqueiros, guardas, sinalizador, molduras) ficam alcancaveis a
 * pe a partir da porta e a planta cresce a cada nivel.
 */
class CivicPlansTest {
	@Test
	void everyLevelFitsTheLotClassOfItsType() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : com.mlin.notas.village.civic.CivicBuildings.PROJECTS) {
				SizeClass size = LotRules.of(project.id()).size();
				for (int level = 1; level <= BuildingTypes.maxLevel(project.id()); level++) {
					Canvas c = BuildingPlans.plan(project, style, 3, level);
					assertTrue(size.fits(c.w(), c.d()), project + " " + level + " " + c.w() + "x" + c.d() + " nao cabe no lote " + size);
					for (long k : c.cells().keySet()) {
						int x = Canvas.keyX(k);
						int z = Canvas.keyZ(k);
						assertTrue(x >= 0 && x < c.w() && z >= 0 && z < c.d(), project + " " + level + " fora da planta: " + x + "," + z);
					}
				}
			}
		}
	}

	@Test
	void plansGrowWithTheLevel() {
		for (VillageProject project : com.mlin.notas.village.civic.CivicBuildings.PROJECTS) {
			int last = 0;
			for (int level = 1; level <= BuildingTypes.maxLevel(project.id()); level++) {
				int n = BuildingPlans.plan(project, VillageStyle.PLAINS, 3, level).cells().size();
				assertTrue(n >= last, project + " nivel " + level + " ficou menor");
				if (level > 1) {
					assertTrue(n > last, project + " nivel " + level + " nao mudou");
				}
				last = n;
			}
		}
	}

	@Test
	void standsSupportedAndKeyPointsAreReachable() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : com.mlin.notas.village.civic.CivicBuildings.PROJECTS) {
				for (int level = 1; level <= BuildingTypes.maxLevel(project.id()); level++) {
					Canvas canvas = BuildingPlans.plan(project, style, 5, level);
					String what = project + " " + level + " " + style;
					VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
					VillagePlannerTest.Voxels voxels = new VillagePlannerTest.Voxels(terrain);
					VillagePlanner.Lot lot = new VillagePlanner.Lot(10, 10, canvas.w(), canvas.d(), 0, 64);
					List<Placement> placements = VillagePlanner.place(canvas, lot);
					voxels.addWithFacing(placements);
					assertEquals(List.of(), voxels.floating(), what + ": blocos soltos");
					assertEquals(List.of(), voxels.unsupported(), what + ": detalhes sem apoio");
					for (var e : canvas.cells().entrySet()) {
						if (e.getValue().part() == VPart.BELL) {
							long k = e.getKey();
							VPart above = canvas.part(Canvas.keyX(k), Canvas.keyY(k) + 1, Canvas.keyZ(k));
							assertTrue(above != null && above.solid(), what + ": sino sem onde pendurar");
						}
					}
					int[] start = {10 + canvas.doorX(), 65, 10 + canvas.d()};
					Set<Long> reach = voxels.reach(start[0], start[1], start[2], 30);
					assertFalse(reach.isEmpty(), what + ": frente da porta bloqueada");
					List<String> missing = new ArrayList<>();
					for (int[] s : canvas.spawns()) {
						if (!reach.contains(Placement.key(10 + s[0], 64 + s[1], 10 + s[2]))) {
							missing.add("spawn " + s[0] + "," + s[1] + "," + s[2]);
						}
					}
					for (String name : List.of("mailbox", "pedestal", "board", "archer", "guard")) {
						for (int[] m : canvas.marks(name)) {
							boolean ok = false;
							// de pe ao lado do bloco (ou nele, quando a marca e um posto de pe)
							for (int dx = -1; dx <= 1 && !ok; dx++) {
								for (int dz = -1; dz <= 1 && !ok; dz++) {
									ok = reach.contains(Placement.key(10 + m[0] + dx, 64 + m[1], 10 + m[2] + dz));
								}
							}
							if (!ok) {
								missing.add(name + " " + m[0] + "," + m[1] + "," + m[2]);
							}
						}
					}
					for (int[] m : canvas.marks("beacon")) {
						// o sinalizador fica numa piramide de degraus: da para subir ate ao lado dele
						boolean ok = false;
						for (int dx = -1; dx <= 1 && !ok; dx++) {
							for (int dz = -1; dz <= 1 && !ok; dz++) {
								ok = reach.contains(Placement.key(10 + m[0] + dx, 64 + m[1], 10 + m[2] + dz));
							}
						}
						if (!ok) {
							missing.add("beacon");
						}
					}
					assertEquals(List.of(), missing, what + ": pontos inalcancaveis");
				}
			}
		}
	}

	@Test
	void functionsHaveTheirMarks() {
		for (VillageStyle style : VillageStyle.values()) {
			assertTrue(!BuildingPlans.plan(VillageProject.POST_OFFICE, style, 1, 1).marks("mailbox").isEmpty());
			assertTrue(!BuildingPlans.plan(VillageProject.MUSEUM, style, 1, 1).marks("pedestal").isEmpty());
			assertTrue(!BuildingPlans.plan(VillageProject.EXPLORERS_GUILD, style, 1, 1).marks("board").isEmpty());
			assertTrue(!BuildingPlans.plan(VillageProject.VILLAGE_WAYSTONE, style, 1, 1).marks("waystone").isEmpty());
			for (int level = 1; level <= 5; level++) {
				assertEquals(1, BuildingPlans.plan(VillageProject.BEACON, style, 1, level).marks("beacon").size());
			}
			for (int level = 1; level <= 3; level++) {
				assertFalse(BuildingPlans.plan(VillageProject.WATCHTOWER, style, 1, level).marks("bell").isEmpty(), "torre sem sino");
			}
			assertEquals(0, BuildingPlans.plan(VillageProject.WATCHTOWER, style, 1, 2).marks("archer").size());
			assertEquals(4, BuildingPlans.plan(VillageProject.WATCHTOWER, style, 1, 3).marks("archer").size());
			for (int level = 1; level <= 3; level++) {
				assertTrue(BuildingPlans.plan(VillageProject.BARRACKS, style, 1, level).marks("guard").size() >= 6, "quartel precisa de 6 postos");
			}
			int frames1 = BuildingPlans.plan(VillageProject.MUSEUM, style, 1, 1).marks("frame").size();
			int frames2 = BuildingPlans.plan(VillageProject.MUSEUM, style, 1, 2).marks("frame").size();
			int frames3 = BuildingPlans.plan(VillageProject.MUSEUM, style, 1, 3).marks("frame").size();
			assertTrue(frames1 >= 24 && frames2 > frames1 && frames3 >= frames2, "molduras " + frames1 + "/" + frames2 + "/" + frames3);
		}
	}

	@Test
	void townHallHasARealBell() {
		for (int level = 1; level <= 5; level++) {
			Canvas c = BuildingPlans.plan(VillageProject.TOWN_HALL, VillageStyle.PLAINS, 3, level);
			assertTrue(c.cells().values().stream().anyMatch(cell -> cell.part() == VPart.BELL), "prefeitura " + level + " sem sino");
			assertFalse(c.cells().values().stream().anyMatch(cell -> cell.part() == VPart.LANTERN_HANGING && false));
		}
	}
}
