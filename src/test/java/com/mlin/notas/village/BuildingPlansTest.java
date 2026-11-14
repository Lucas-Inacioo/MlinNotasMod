package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plantas de todos os niveis: cabem na classe de lote do tipo (melhoria nunca transborda), ficam dentro da propria
 * planta, nada solto no ar, detalhes com apoio e todo morador alcancavel a pe a partir da porta. A Prefeitura tem
 * atril e cresce a cada nivel.
 */
class BuildingPlansTest {
	private static final List<VillageProject> LEVELED = List.of(VillageProject.HOUSE, VillageProject.FARM, VillageProject.SMITHY,
			VillageProject.MARKET, VillageProject.INN, VillageProject.LIBRARY, VillageProject.TOWN_HALL);

	@Test
	void everyLevelFitsTheLotClassOfItsType() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : LEVELED) {
				SizeClass size = LotRules.of(project.id()).size();
				for (Canvas c : BuildingPlans.levels(project, style)) {
					assertTrue(size.fits(c.w(), c.d()), project + " " + style + " " + c.w() + "x" + c.d() + " nao cabe no lote " + size);
					for (long k : c.cells().keySet()) {
						int x = Canvas.keyX(k);
						int z = Canvas.keyZ(k);
						assertTrue(x >= 0 && x < c.w() && z >= 0 && z < c.d(), project + " fora da planta: " + x + "," + z);
					}
				}
			}
		}
	}

	@Test
	void levelOneIsTheOldPlan() {
		for (VillageProject project : List.of(VillageProject.HOUSE, VillageProject.SMITHY, VillageProject.MARKET)) {
			for (long seed = 1; seed < 6; seed++) {
				Canvas a = Buildings.plan(project, VillageStyle.PLAINS, seed);
				Canvas b = BuildingPlans.plan(project, VillageStyle.PLAINS, seed, 1);
				assertEquals(a.cells(), b.cells(), project + ": o nivel I nao pode mudar (lotes ja reservados)");
			}
		}
	}

	@Test
	void townHallGrowsAndHasALectern() {
		int last = 0;
		for (int level = 1; level <= 5; level++) {
			Canvas c = BuildingPlans.plan(VillageProject.TOWN_HALL, VillageStyle.PLAINS, 3, level);
			int blocks = c.cells().size();
			assertTrue(blocks > last, "nivel " + level + " nao cresceu");
			last = blocks;
			assertTrue(c.cells().values().stream().anyMatch(cell -> cell.part() == VPart.LECTERN), "prefeitura sem atril");
		}
		assertTrue(BuildingPlans.plan(VillageProject.TOWN_HALL, VillageStyle.PLAINS, 3, 4).height()
				> BuildingPlans.plan(VillageProject.TOWN_HALL, VillageStyle.PLAINS, 3, 3).height() + 1, "IV tem a torre do relogio");
	}

	@Test
	void everyLevelStandsAndResidentsAreReachable() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : LEVELED) {
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
					// pes na frente da porta, fora da planta
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
				}
			}
		}
	}

	@Test
	void housesGrowInResidentsAndSize() {
		Canvas one = BuildingPlans.plan(VillageProject.HOUSE, VillageStyle.PLAINS, 2, 1);
		Canvas three = BuildingPlans.plan(VillageProject.HOUSE, VillageStyle.PLAINS, 2, 3);
		long beds1 = one.cells().values().stream().filter(c -> c.part() == VPart.BED).count();
		long beds3 = three.cells().values().stream().filter(c -> c.part() == VPart.BED).count();
		assertTrue(beds3 >= 4 && beds3 > beds1, "casa III com 4 camas: " + beds3);
		assertTrue(three.height() > one.height() + 3, "casa III mais alta");
		assertEquals(4, BuildingTypes.residents("house", 3));
	}
}
