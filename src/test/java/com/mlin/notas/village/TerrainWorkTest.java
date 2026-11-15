package com.mlin.notas.village;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Terraplenagem por anel (corte, aterro, arrimo, agua), preco, niveis novos e o formato salvo de hoje. */
class TerrainWorkTest {
	private static VPart at(List<Placement> list, int y) {
		VPart out = null;
		for (Placement p : list) {
			if (p.y() == y) {
				out = p.part();
			}
		}
		return out;
	}

	@Test
	void columnCutsFillsBuildsRetainingWallsAndDrainsShallowWater() {
		// corte: tudo acima do alvo sai, grama no alvo
		List<Placement> cut = TerrainWork.column(0, 0, 70, 0, 65, false, (x, z) -> 65, false);
		for (int y = 66; y <= 71; y++) {
			assertEquals(VPart.AIR, at(cut, y), "corte em y=" + y);
		}
		assertEquals(VPart.TURF, at(cut, 65));
		// aterro baixo: terra em cima, pedra no fundo, sem muro
		List<Placement> fill = TerrainWork.column(0, 0, 58, 0, 65, false, (x, z) -> 65, false);
		assertEquals(VPart.ROCK, at(fill, 59));
		assertEquals(VPart.EARTH, at(fill, 63));
		assertEquals(VPart.TURF, at(fill, 65));
		// vizinho 2+ abaixo: a coluna aterrada vira muro de arrimo
		List<Placement> wall = TerrainWork.column(0, 0, 60, 0, 65, false, (x, z) -> x == 1 ? 62 : 65, false);
		assertEquals(VPart.ROCK, at(wall, 61));
		for (int y = 63; y <= 64; y++) {
			assertEquals(VPart.RETAIN, at(wall, y), "arrimo em y=" + y);
		}
		// corte com vizinho bem abaixo: a face a vista do corte tambem vira arrimo
		List<Placement> cutWall = TerrainWork.column(0, 0, 70, 0, 65, false, (x, z) -> z == 1 ? 61 : 65, false);
		for (int y = 62; y <= 64; y++) {
			assertEquals(VPart.RETAIN, at(cutWall, y), "arrimo no corte em y=" + y);
		}
		assertEquals(VPart.TURF, at(cutWall, 65));
		// agua rasa: drena acima do alvo e aterra o fundo
		List<Placement> water = TerrainWork.column(0, 0, 64, 2, 63, false, (x, z) -> 63, false);
		assertEquals(VPart.DRAIN, at(water, 64));
		assertEquals(VPart.TURF, at(water, 63));
		// rua na altura da agua (o caminho nao substitui agua): a agua sai antes e o caminho fica
		List<Placement> wet = TerrainWork.column(0, 0, 64, 1, 64, true, (x, z) -> 64, false);
		List<VPart> top = wet.stream().filter(p -> p.y() == 64).map(Placement::part).toList();
		assertEquals(List.of(VPart.DRAIN, VPart.PATH), top, "agua tirada antes do caminho");
		// rua: caminho no topo mesmo ja na altura; coluna pronta de quarteirao nao muda nada
		assertEquals(VPart.PATH, at(TerrainWork.column(0, 0, 64, 0, 64, true, (x, z) -> 64, false), 64));
		assertTrue(TerrainWork.column(0, 0, 64, 0, 64, false, (x, z) -> 64, false).stream().allMatch(p -> p.part() == VPart.AIR));
		// muda nos arredores
		assertEquals(VPart.SAPLING, at(TerrainWork.column(0, 0, 64, 0, 64, false, (x, z) -> 64, true), 65));
	}

	@Test
	void ringPriceIsAFixedTable() {
		int[] emeralds = {5, 10, 15, 20, 30, 40, 30};
		int[] fill = {16, 32, 48, 64, 96, 128, 96};
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			assertEquals(emeralds[ring - 1], RingCost.emeralds(ring), "esmeraldas do anel " + ring);
			assertEquals(fill[ring - 1], RingCost.fillItems(ring, 0), "terra do anel " + ring);
			RingWork work = new RingWork(ring);
			assertEquals(emeralds[ring - 1], work.emeralds());
			assertEquals(fill[ring - 1], work.fill());
		}
		// a pedreira abate a terra (credito em blocos de aterro, 4 por item), nunca abaixo de zero
		assertEquals(96 - 320 / RingWork.FILL_PER_ITEM, RingCost.fillItems(5, 320));
		assertEquals(0, RingCost.fillItems(1, 320));
		assertEquals(16 - 60 / RingWork.FILL_PER_ITEM, RingCost.fillItems(1, 60));
	}

	@Test
	void ringDepositsArePartialAndClose() {
		RingWork work = new RingWork(2);
		assertEquals(10, work.emeralds());
		Map<String, Integer> first = work.deposit(Map.of(RingWork.EMERALD, 3));
		assertEquals(Map.of(RingWork.EMERALD, 3), first);
		assertFalse(work.paidOff());
		work.deposit(Map.of(RingWork.EMERALD, 64, RingWork.FILL, 64));
		assertTrue(work.paidOff());
		assertEquals(10, work.depositedEmeralds());
		assertEquals(32, work.depositedFill());
		// pago: o preco nao muda mais, nem com pedreira nova
		work.setState(RingWork.State.QUEUED);
		work.reprice(10_000);
		assertEquals(10, work.emeralds());
		assertEquals(32, work.fill());
		// aberto: a pedreira baixa o que falta
		RingWork open = new RingWork(3);
		open.reprice(160);
		assertEquals(48 - 40, open.fill());
	}

	@Test
	void ringColumnsAreBroughtToTheFieldHeight() {
		for (String name : List.of("slope", "mountain_side", "noisy")) {
			HeightFieldBuilder.Result result = HeightFieldTest.cached(name, 11);
			MasterPlan plan = result.plan();
			HeightField field = result.field();
			assertSame(field, plan.field(), "o plano esta ligado ao campo");
			HeightFieldBuilder.Natural natural = HeightFieldTest.terrains(11).get(name);
			int checked = 0;
			for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
				for (long col : plan.columns(ring)) {
					int x = VillageLayout.colX(col);
					int z = VillageLayout.colZ(col);
					int target = plan.plannedHeight(x, z);
					assertEquals(field.heightRel(x, z), target, name + ": plano segue o campo em " + x + "," + z);
					int ground = natural.ground(x, z);
					List<Placement> placements = TerrainWork.column(x, z, ground, 0, target, plan.ground(x, z), (nx, nz) -> {
						int h = plan.plannedHeight(nx, nz);
						return h != Integer.MIN_VALUE ? h : field.heightRel(nx, nz);
					}, false);
					// o que passa do alvo sai e o alvo vira grama/caminho (ou ja era o chao): a coluna fica no alvo
					for (int y = target + 1; y <= ground + 1; y++) {
						int yy = y;
						assertTrue(placements.stream().noneMatch(p -> p.y() == yy && p.part() != VPart.AIR && p.part() != VPart.DRAIN),
								name + ": bloco acima do alvo em " + x + "," + z);
					}
					assertTrue(ground == target && !plan.ground(x, z) || placements.stream().anyMatch(p -> p.y() == target
							&& (p.part() == VPart.TURF || p.part() == VPart.PATH)), name + ": sem topo no alvo em " + x + "," + z);
					checked++;
				}
			}
			assertTrue(checked > 3000, name + ": colunas dos aneis " + checked);
		}
	}


	@Test
	void newLevelsAreAppendedAndNeedTheirRingPrepared() {
		assertEquals(5, VillageLevel.METROPOLIS.ordinal());
		assertEquals(6, VillageLevel.CROWN_CITY.ordinal());
		assertEquals("crown_city", VillageLevel.CROWN_CITY.id());
		assertEquals(76, VillageLevel.CROWN_CITY.radius());
		assertNull(VillageLevel.CROWN_CITY.next());
		// ids que ainda nao existem (prefeitura, celeiro...) ficam de fora ate a fase deles
		assertEquals(List.of(VillageProject.FARM, VillageProject.SMITHY, VillageProject.TOWN_HALL), VillageLevel.VILLAGE.required());
		assertTrue(VillageLevel.VILLAGE.requiredIds().contains("town_hall:1"));
		for (VillageLevel level : VillageLevel.values()) {
			if (level.next() != null) {
				assertTrue(level.next().rings() >= level.rings());
				assertTrue(level.next().projectsNeeded() >= level.projectsNeeded());
			}
		}
		VillageState v = new VillageState("minecraft:overworld", 0, 64, 0, 9L, VillageStyle.PLAINS, "Aneis");
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		v.setLayout(VillageLayout.create(v, terrain));
		v.addBuilt(VillageProject.HOUSE);
		v.addBuilt(VillageProject.HOUSE);
		v.addBuilt(VillageProject.FARM);
		v.addBuilt(VillageProject.PLAZA);
		assertNull(v.addProsperity(100), "sem a terraplenagem dos aneis 1-3 nao vira Aldeia");
		assertTrue(VillageLevel.HAMLET.missing(v.levelContext()).contains("ring:3"));
		v.ring(1).setState(RingWork.State.DONE);
		v.ring(2).setState(RingWork.State.DONE);
		assertNull(v.recompute());
		v.ring(3).setState(RingWork.State.DONE);
		assertEquals(VillageLevel.HAMLET, v.recompute());
		// casa de jogador (cama + porta) conta pontos e prosperidade uma vez
		int before = v.prosperity();
		v.setPlayerHouses(1);
		assertEquals(before + VillageProject.HOUSE.prosperity(), v.prosperity());
		v.setPlayerHouses(1);
		assertEquals(before + VillageProject.HOUSE.prosperity(), v.prosperity());
		assertEquals(v.totalBuilt() + 1, v.buildPoints());
	}

	/** villages.dat de hoje: vila com plano antigo (lotes nos eixos, uma casa pronta, um lote reservado vazio). */
	private static final String TODAY = """
			{"villages":[{"core":{"dimension":"minecraft:overworld","x":100,"y":70,"z":-40,"seed":77,"style":"plains","name":"Vila de Hoje",
			"starter":true},"progress":{"level":1,"prosperity":150,"built":{"plaza":1,"house":1,"farm":1},"queue":[],"lots":[9,9,17,19]},
			"layout":{"radius":30,"next_id":3,"lots":[
			{"id":0,"kind":"house","x":9,"z":9,"rot":0,"cw":9,"cd":11,"door":4,"seed":5,"built":true,"access":[13,20,13,21]},
			{"id":1,"kind":"house","x":-20,"z":9,"rot":1,"cw":9,"cd":9,"door":4,"seed":6,"built":false,"preferred":true},
			{"id":2,"kind":"farm","x":12,"z":-22,"rot":2,"cw":11,"cd":9,"door":5,"seed":7,"built":true}],
			"roads":[],"lamps":[20,1,1,-20,1,0]}}],
			"records":[{"village":"minecraft:overworld@100,70,-40","lot":0,"ground":70,"positions":[1],"blocks":[0],"palette":["minecraft:oak_door"]}],
			"starter_checked":true}
			""";

	@Test
	void todaysVillagesFileLoadsAndKeepsBuiltLotsFixedInTheMasterPlan() {
		VillageData data = VillageData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(TODAY)).getOrThrow();
		VillageState v = data.villages().iterator().next();
		assertNotNull(v.layout());
		assertNull(v.layout().master(), "arquivo de hoje nao tem plano diretor");
		assertEquals(3, v.layout().lots().size());
		assertTrue(v.ringWorks().isEmpty());
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 69);
		// as obras prontas sao construcao no mundo
		for (VillageLayout.Lot lot : v.layout().lots()) {
			if (lot.built()) {
				VillageState.Rect r = lot.rect();
				for (int x = r.x0(); x <= r.x1(); x++) {
					for (int z = r.z0(); z <= r.z1(); z++) {
						terrain.blocked.add(VillagePlannerTest.FakeTerrain.col(x, z));
					}
				}
			}
		}
		VillageLayout layout = VillagePlanner.layout(v, terrain);
		MasterPlan master = layout.master();
		assertNotNull(master, "o plano antigo passa para o plano diretor");
		// obras prontas ficam onde estao, viram recorte fixo; o lote vazio antigo e refeito num lote do plano
		VillageLayout.Lot house = layout.lot(0);
		VillageLayout.Lot farm = layout.lot(2);
		assertEquals(9, house.x());
		assertEquals(9, house.z());
		assertEquals(12, farm.x());
		assertNull(layout.lot(1), "lote vazio de antes nao fica");
		for (VillageLayout.Lot old : List.of(house, farm)) {
			VillageState.Rect r = old.rect();
			for (int x = r.x0(); x <= r.x1(); x++) {
				for (int z = r.z0(); z <= r.z1(); z++) {
					assertEquals(MasterPlan.CARVED, master.code(x, z), "obra antiga dentro do plano em " + x + "," + z);
				}
			}
			for (MasterPlan.Parcel p : master.parcels()) {
				assertFalse(p.rect().overlaps(r, 0), "lote do plano em cima de obra antiga");
			}
			assertTrue(master.playerAreas().stream().noneMatch(a -> a.overlaps(r, 0)), "obra da vila virou area de jogador");
		}
		for (VillageLayout.Lot lot : layout.lots()) {
			if (!lot.built()) {
				assertTrue(lot.parcel() >= 0, "lote novo fora do plano diretor");
			}
		}
		// e o formato novo volta igual, com a terraplenagem
		v.ring(1).setState(RingWork.State.DONE);
		var json = VillageData.VILLAGE_CODEC.encodeStart(JsonOps.INSTANCE, v).getOrThrow();
		VillageState again = VillageData.VILLAGE_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		MasterPlan back = again.layout().master();
		assertNotNull(back);
		assertEquals(master.parcels().size(), back.parcels().size());
		assertEquals(java.util.Arrays.toString(master.gridRle()), java.util.Arrays.toString(back.gridRle()));
		assertEquals(java.util.Arrays.toString(master.streetHeights()), java.util.Arrays.toString(back.streetHeights()));
		assertTrue(again.ring(1).done());
		for (VillageLayout.Lot lot : again.layout().lots()) {
			assertEquals(layout.lot(lot.id()).parcel(), lot.parcel());
		}
	}
}
