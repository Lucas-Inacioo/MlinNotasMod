package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plantas das construcoes de producao e comida (Vilas 2.0 fase 5): cabem na classe de lote, nada solto, detalhes com
 * apoio, morador e todo bau alcancaveis a pe a partir da porta, nivel maior = mais blocos e cada planta tem bau.
 */
class FoodPlansTest {
	static final List<VillageProject> FOOD = List.of(VillageProject.GRANARY, VillageProject.MILL, VillageProject.BAKERY, VillageProject.ORCHARD,
			VillageProject.PEN, VillageProject.APIARY, VillageProject.BUTCHER, VillageProject.WINERY, VillageProject.SAWMILL);

	@Test
	void everyLevelFitsTheLotClassAndStaysInsideTheCanvas() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : FOOD) {
				SizeClass size = LotRules.of(project.id()).size();
				for (Canvas c : BuildingPlans.levels(project, style)) {
					assertTrue(size.fits(c.w(), c.d()), project + " " + style + " " + c.w() + "x" + c.d() + " nao cabe no lote " + size);
					for (long k : c.cells().keySet()) {
						int x = Canvas.keyX(k);
						int z = Canvas.keyZ(k);
						assertTrue(x >= 0 && x < c.w() && z >= 0 && z < c.d(), project + " " + style + " fora da planta: " + x + "," + z);
					}
				}
			}
		}
	}

	@Test
	void typesAreRegisteredWithLevelsWorkersAndNeeds() {
		for (VillageProject project : FOOD) {
			BuildingTypes.Type type = BuildingTypes.of(project.id());
			assertTrue(type != null, project + " sem tipo registrado");
			assertTrue(type.maxLevel() >= 2, project + " com niveis");
			assertEquals(1, type.workers(), project + " precisa de um trabalhador");
			assertTrue(VillageLayout.LOT_PROJECTS.contains(project), project + " reserva lote");
			assertTrue(type.needs().values().stream().anyMatch(v -> v[0] > 0), project + " contribui para alguma necessidade");
		}
		assertEquals(3, BuildingTypes.maxLevel("granary"));
		assertEquals(2, BuildingTypes.maxLevel("apiary"));
		assertEquals(2, BuildingTypes.maxLevel("butcher"));
		assertTrue(VillageLevel.TOWN.requiredIds().contains("granary"));
		assertTrue(VillageLevel.TOWN.required().contains(VillageProject.GRANARY), "o celeiro agora e exigencia real da Cidade");
	}

	@Test
	void unlockLevelsAndZonesFollowTheCatalog() {
		assertEquals(VillageLevel.HAMLET, VillageProject.GRANARY.minLevel());
		assertEquals(VillageLevel.HAMLET, VillageProject.MILL.minLevel());
		assertEquals(VillageLevel.HAMLET, VillageProject.ORCHARD.minLevel());
		assertEquals(VillageLevel.HAMLET, VillageProject.PEN.minLevel());
		assertEquals(VillageLevel.VILLAGE, VillageProject.BAKERY.minLevel());
		assertEquals(VillageLevel.VILLAGE, VillageProject.APIARY.minLevel());
		assertEquals(VillageLevel.VILLAGE, VillageProject.BUTCHER.minLevel());
		assertEquals(VillageLevel.VILLAGE, VillageProject.SAWMILL.minLevel());
		assertEquals(VillageLevel.TOWN, VillageProject.WINERY.minLevel());
		assertTrue(LotRules.of("bakery").allows(Zone.CENTER) && LotRules.of("bakery").allows(Zone.DISTRICT));
		assertTrue(LotRules.of("mill").allows(Zone.OUTSKIRTS) && !LotRules.of("mill").allows(Zone.CENTER));
		assertTrue(LotRules.of("butcher").allows(Zone.DISTRICT) && !LotRules.of("butcher").allows(Zone.OUTSKIRTS));
		assertTrue(LotRules.of("winery").allows(Zone.OUTSKIRTS));
		assertTrue(LotRules.of("sawmill").allows(Zone.DISTRICT));
		for (VillageProject project : FOOD) {
			assertEquals(0, project.maxBuilds(VillageLevel.CAMP), project + " nao existe no Acampamento");
			assertTrue(project.maxBuilds(VillageLevel.CROWN_CITY) >= 1);
		}
	}

	@Test
	void higherLevelsAreBiggerAndEveryPlanHasAWorkChest() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : FOOD) {
				int last = 0;
				for (int level = 1; level <= BuildingTypes.maxLevel(project.id()); level++) {
					Canvas c = BuildingPlans.plan(project, style, 7, level);
					assertTrue(c.cells().size() > last, project + " " + style + " nivel " + level + " nao cresceu");
					last = c.cells().size();
					assertTrue(c.cells().values().stream().anyMatch(cell -> cell.part() == VPart.CHEST), project + " " + level + " sem bau de trabalho");
					assertFalse(c.spawns().isEmpty(), project + " sem lugar para o trabalhador");
				}
			}
		}
	}

	@Test
	void levelOnePlanIsWhatTheLotReservationUses() {
		for (VillageProject project : FOOD) {
			for (long seed = 1; seed < 4; seed++) {
				assertEquals(Buildings.plan(project, VillageStyle.PLAINS, seed).cells(), BuildingPlans.plan(project, VillageStyle.PLAINS, seed, 1).cells(),
						project + ": a planta do nivel I e a de sempre");
				assertEquals(VillageLayout.canvas(project.id(), VillageStyle.PLAINS, seed).cells(),
						BuildingPlans.plan(project, VillageStyle.PLAINS, seed, 1).cells());
			}
		}
	}

	@Test
	void everyLevelStandsAndWorkersAndChestsAreReachable() {
		for (VillageStyle style : VillageStyle.values()) {
			for (VillageProject project : FOOD) {
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
					int[] start = {10 + canvas.doorX(), 65, 10 + canvas.d()};
					Set<Long> reach = voxels.reach(start[0], start[1], start[2], 40);
					assertFalse(reach.isEmpty(), what + ": frente da porta bloqueada");
					List<String> missing = new ArrayList<>();
					for (int[] s : canvas.spawns()) {
						if (!reach.contains(Placement.key(10 + s[0], 64 + s[1], 10 + s[2]))) {
							missing.add("morador " + s[0] + "," + s[1] + "," + s[2]);
						}
					}
					for (var e : canvas.cells().entrySet()) {
						if (e.getValue().part() != VPart.CHEST) {
							continue;
						}
						int cx = Canvas.keyX(e.getKey());
						int cy = Canvas.keyY(e.getKey());
						int cz = Canvas.keyZ(e.getKey());
						boolean ok = false;
						for (Facing f : Facing.values()) {
							ok |= reach.contains(Placement.key(10 + cx + f.dx, 64 + cy, 10 + cz + f.dz));
						}
						if (!ok) {
							missing.add("bau " + cx + "," + cy + "," + cz);
						}
					}
					assertEquals(List.of(), missing, what + ": inalcancaveis");
				}
			}
		}
	}

	@Test
	void foodLotsAreReservedOnFlatGroundAtEveryLevel() {
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		VillageState v = new VillageState("minecraft:overworld", 0, 64, 0, 4321L, VillageStyle.PLAINS, "Teste");
		v.setLevel(VillageLevel.CAMP);
		VillageLayout layout = VillageLayout.create(v, terrain);
		v.setLayout(layout);
		List<String> short_ = new ArrayList<>();
		for (VillageLevel level : VillageLevel.values()) {
			v.setLevel(level);
			layout.extend(v, terrain, level);
			for (VillageProject project : FOOD) {
				int got = layout.lotsOf(project.id()).size();
				// no Vila so ha bairros e centro (os arredores abrem na Cidade): apiario e serraria esperam o proximo anel
				if (got < project.maxBuilds(level) && level.ordinal() >= VillageLevel.TOWN.ordinal()) {
					short_.add(level + ":" + project.id() + " " + got + "/" + project.maxBuilds(level));
				}
			}
		}
		assertEquals(List.of(), short_, "lotes que faltaram em terreno plano");
	}

	@Test
	void newEnumValuesAreAppendedSoSavedOrdinalsStayValid() {
		// os saves guardam VPart por ordinal e projeto por id: os valores velhos nao podem mudar de lugar
		assertEquals(VPart.DRAIN.ordinal() + 1, VPart.BEEHIVE.ordinal());
		assertEquals(VPart.BEEHIVE.ordinal() + 5, VPart.SACK.ordinal());
		assertEquals(VillageProject.TOWN_HALL.ordinal() + 1, VillageProject.GRANARY.ordinal());
		assertEquals(VillageProject.GRANARY, VillageProject.byId("granary"));
		assertEquals(VillageProject.SAWMILL, VillageProject.byId("sawmill"));
		assertEquals(VillageProject.GRANARY.ordinal() + 8, VillageProject.SAWMILL.ordinal());
		// vila salva sem nenhuma construcao nova: continua com os mesmos tipos antigos funcionando
		assertEquals(1, BuildingTypes.maxLevel("unknown_old_kind"));
		assertEquals(0, FoodRules.honeycomb(1, 3));
	}

	@Test
	void millHasGrindstonesAndSailsAndOrchardHasTrees() {
		Canvas mill = BuildingPlans.plan(VillageProject.MILL, VillageStyle.PLAINS, 3, 3);
		assertEquals(3, mill.cells().values().stream().filter(c -> c.part() == VPart.GRINDSTONE).count());
		assertTrue(mill.cells().values().stream().anyMatch(c -> c.part() == VPart.WOOL), "velas de la");
		Canvas orchard1 = BuildingPlans.plan(VillageProject.ORCHARD, VillageStyle.PLAINS, 3, 1);
		Canvas orchard2 = BuildingPlans.plan(VillageProject.ORCHARD, VillageStyle.PLAINS, 3, 2);
		long logs1 = orchard1.cells().values().stream().filter(c -> c.part() == VPart.LOG).count();
		long logs2 = orchard2.cells().values().stream().filter(c -> c.part() == VPart.LOG).count();
		assertTrue(logs2 > logs1, "mais macieiras no II");
		Canvas apiary2 = BuildingPlans.plan(VillageProject.APIARY, VillageStyle.PLAINS, 3, 2);
		assertEquals(FoodRules.hives(2), apiary2.cells().values().stream().filter(c -> c.part() == VPart.BEEHIVE).count());
		Canvas winery = BuildingPlans.plan(VillageProject.WINERY, VillageStyle.PLAINS, 3, 2);
		assertTrue(winery.cells().values().stream().anyMatch(c -> c.part() == VPart.GRAPE_VINE), "videiras");
	}
}
