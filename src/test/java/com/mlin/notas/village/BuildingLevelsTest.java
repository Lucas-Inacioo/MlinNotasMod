package com.mlin.notas.village;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveis das construcoes, Prefeitura, custos, necessidades, trabalhadores, teto de profissao e formato salvo. */
class BuildingLevelsTest {
	private static VillageState village(VillageLevel level) {
		VillageState v = new VillageState("minecraft:overworld", 0, 64, 0, 99L, VillageStyle.PLAINS, "Teste");
		v.setLevel(level);
		return v;
	}

	/** Vila com plano e uma obra pronta de cada tipo dado (lotes reservados e marcados como prontos). */
	private static VillageState planned(VillageLevel level, VillageProject... built) {
		VillageState v = village(level);
		VillagePlannerTest.FakeTerrain terrain = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		v.setLayout(VillageLayout.create(v, terrain));
		for (VillageProject project : built) {
			VillageLayout.Lot lot = v.layout().firstFree(project.id());
			if (lot == null) {
				lot = v.layout().reserve(project.id(), v.style(), v.seed(), 6);
			}
			assertNotNull(lot, "sem lote para " + project);
			lot.setBuilt(true);
			v.addBuilt(project);
		}
		return v;
	}

	private static VillageLayout.Lot lotOf(VillageState v, String kind) {
		return v.layout().lots().stream().filter(l -> l.kind().equals(kind) && l.built()).findFirst().orElseThrow();
	}

	@Test
	void townHallCapsOtherBuildingsAndTheVillageCapsTheTownHall() {
		assertEquals(0, BuildingTypes.townHallCap(VillageLevel.CAMP));
		assertEquals(1, BuildingTypes.townHallCap(VillageLevel.HAMLET));
		assertEquals(2, BuildingTypes.townHallCap(VillageLevel.VILLAGE));
		assertEquals(3, BuildingTypes.townHallCap(VillageLevel.TOWN));
		assertEquals(4, BuildingTypes.townHallCap(VillageLevel.CAPITAL));
		assertEquals(5, BuildingTypes.townHallCap(VillageLevel.METROPOLIS));
		assertEquals(5, BuildingTypes.townHallCap(VillageLevel.CROWN_CITY));
		// sem Prefeitura tudo fica no I; com ela, ate o nivel dela e o maximo do tipo
		assertEquals(1, BuildingTypes.cap("smithy", VillageLevel.CAPITAL, 0));
		assertEquals(3, BuildingTypes.cap("smithy", VillageLevel.CAPITAL, 3));
		assertEquals(3, BuildingTypes.cap("house", VillageLevel.CAPITAL, 5));
		assertEquals(2, BuildingTypes.cap(BuildingTypes.TOWN_HALL, VillageLevel.VILLAGE, 1));
		assertEquals(1, BuildingTypes.cap("plaza", VillageLevel.CAPITAL, 5), "tipo sem nivel fica no I");
	}

	@Test
	void upgradeCostScalesWithTierMaterials() {
		List<VillageCost> two = BuildingTypes.upgradeCost(VillageProject.HOUSE, 2);
		Map<String, Integer> m2 = new HashMap<>();
		two.forEach(c -> m2.put(c.id(), c.count()));
		assertEquals(96, m2.get("#minecraft:planks"), "1,5x as tabuas");
		assertEquals(2, m2.get("#minecraft:beds"), "1,5x arredonda para cima");
		assertEquals(8, m2.get("minecraft:iron_ingot"));
		assertEquals(16, m2.get("minecraft:bricks"));
		Map<String, Integer> m5 = new HashMap<>();
		BuildingTypes.upgradeCost(VillageProject.SMITHY, 5).forEach(c -> m5.put(c.id(), c.count()));
		assertEquals(24 * 6, m5.get("minecraft:iron_ingot"), "6x no V");
		assertEquals(24, m5.get("mlinnotas:cobalt_ingot"));
		assertEquals(6, m5.get("minecraft:diamond"));
		Map<String, Integer> m4 = new HashMap<>();
		BuildingTypes.upgradeCost(VillageProject.SMITHY, 4).forEach(c -> m4.put(c.id(), c.count()));
		assertEquals(24 * 4 + 16, m4.get("minecraft:iron_ingot"), "custo base e material da faixa somam na mesma linha");
	}

	@Test
	void upgradeDepositsQueueFinishAndCancelRefundsPaidItems() {
		VillageState v = planned(VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.TOWN_HALL);
		VillageLayout.Lot house = lotOf(v, "house");
		BuildingsState b = v.buildingsState();
		assertEquals(1, b.level(house.id()));
		assertEquals(1, VillageBuildings.cap(v, "house"), "Prefeitura I deixa tudo no I");
		assertEquals(BuildingsState.Outcome.CAPPED, b.deposit(house.id(), VillageProject.HOUSE, 1, VillageBuildings.cap(v, "house"), Map.of()).outcome());
		VillageLayout.Lot hall = lotOf(v, "town_hall");
		b.setLevel(hall.id(), 2);
		assertEquals(2, VillageBuildings.cap(v, "house"));
		Map<String, Integer> all = new HashMap<>();
		BuildingTypes.upgradeCost(VillageProject.HOUSE, 2).forEach(c -> all.put(c.id(), c.count()));
		assertEquals(BuildingsState.Outcome.NOTHING, b.deposit(house.id(), VillageProject.HOUSE, 1, 2, Map.of()).outcome());
		BuildingsState.Deposit part = b.deposit(house.id(), VillageProject.HOUSE, 1, 2, Map.of("#minecraft:planks", 40));
		assertEquals(BuildingsState.Outcome.PARTIAL, part.outcome());
		assertEquals(40, part.taken().get("#minecraft:planks"));
		b.recordPaid(house.id(), Map.of("minecraft:oak_planks", 40));
		BuildingsState.Deposit done = b.deposit(house.id(), VillageProject.HOUSE, 1, 2, all);
		assertEquals(BuildingsState.Outcome.COMPLETE, done.outcome());
		assertEquals(56, done.taken().get("#minecraft:planks"), "so o que faltava");
		assertEquals(house.id(), b.nextQueued());
		assertEquals(BuildingsState.Outcome.INVALID, b.deposit(house.id(), VillageProject.HOUSE, 1, 2, all).outcome(), "ja na fila");
		// cancelar devolve os itens de verdade
		assertEquals(Map.of("minecraft:oak_planks", 40), b.cancel(house.id()));
		assertEquals(-1, b.nextQueued());
		// de novo e termina: sobe de nivel, ganha ponto de obra
		b.deposit(house.id(), VillageProject.HOUSE, 1, 2, all);
		int points = v.buildPoints();
		b.finish(house.id());
		assertEquals(2, b.level(house.id()));
		assertEquals(points + 1, v.buildPoints(), "cada nivel de melhoria vale 1 ponto");
		assertEquals(3, VillageBuildings.residents(v), "casa II abriga 3");
		assertEquals(1, BuildingTypes.newResidents("house", 1, 2));
		// demolida: volta ao I
		b.forget(house.id());
		assertEquals(1, b.level(house.id()));
	}

	@Test
	void levelRequirementSuffixIsChecked() {
		assertEquals(2, VillageLevel.requiredLevel("town_hall:2"));
		assertEquals(1, VillageLevel.requiredLevel("farm"));
		Map<VillageProject, Integer> built = Map.of(VillageProject.INN, 1, VillageProject.LIBRARY, 1, VillageProject.TOWN_HALL, 1,
				VillageProject.WALL, 1, VillageProject.MARKET, 1, VillageProject.WATCHTOWER, 1);
		VillageLevel.Context low = new VillageLevel.Context(5000, built, -1, 100, Map.of("town_hall", 1), -1);
		assertTrue(VillageLevel.CAPITAL.missing(low).contains("level:town_hall:2"), VillageLevel.CAPITAL.missing(low).toString());
		VillageLevel.Context high = new VillageLevel.Context(5000, built, -1, 100, Map.of("town_hall", 2), -1);
		assertFalse(VillageLevel.CAPITAL.missing(high).contains("level:town_hall:2"));
		assertTrue(VillageLevel.CAPITAL.missing(high).isEmpty(), "com a torre de vigia pronta nada falta: " + VillageLevel.CAPITAL.missing(high));
		// sem a obra pronta continua pedindo a obra (nao o nivel)
		VillageLevel.Context none = new VillageLevel.Context(5000, Map.of(), -1, 100, Map.of(), -1);
		assertTrue(VillageLevel.VILLAGE.missing(none).contains("project:town_hall"));
	}

	@Test
	void fromTownEveryNeedMustPassHalf() {
		Map<VillageProject, Integer> built = Map.of(VillageProject.WALL, 1, VillageProject.MARKET, 1, VillageProject.GRANARY, 1, VillageProject.WATCHTOWER, 1);
		VillageLevel.Context weak = new VillageLevel.Context(5000, built, -1, 100, Map.of(), 50);
		assertTrue(VillageLevel.TOWN.missing(weak).contains("needs"), "50% nao basta");
		assertTrue(VillageLevel.TOWN.missing(new VillageLevel.Context(5000, built, -1, 100, Map.of(), 51)).isEmpty());
		assertFalse(VillageLevel.VILLAGE.missing(new VillageLevel.Context(5000, Map.of(VillageProject.FARM, 1, VillageProject.SMITHY, 1,
				VillageProject.TOWN_HALL, 1), -1, 100, Map.of(), 0)).contains("needs"), "antes da Cidade as necessidades nao travam");
	}

	@Test
	void needsComeFromBuildingsAndDriveDailyProsperity() {
		VillageState v = planned(VillageLevel.TOWN, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.FARM, VillageProject.TOWN_HALL);
		v.buildingsState().setCounts(4, 0);
		Map<VillageNeeds.Need, Integer> values = VillageNeeds.values(v);
		assertEquals(100, values.get(VillageNeeds.Need.HOUSING), "2 casas = 4 camas para 4 moradores");
		assertEquals(100, values.get(VillageNeeds.Need.FOOD), "plantacao I alimenta 5");
		assertEquals(0, values.get(VillageNeeds.Need.COMMERCE));
		int delta = VillageNeeds.dailyProsperity(values);
		assertEquals(2 + 2 - countBad(values), delta);
		// mais gente sem mais casas: moradia cai
		v.buildingsState().setCounts(16, 0);
		assertEquals(25, VillageNeeds.values(v).get(VillageNeeds.Need.HOUSING));
		// fonte nova registrada (celeiro, igreja...) entra na conta
		int before = VillageNeeds.supply(VillageNeeds.Need.COMMERCE, VillageNeeds.input(v));
		VillageNeeds.register(VillageNeeds.Need.COMMERCE, in -> in.population() >= 1000 ? 1 : 0);
		assertEquals(before, VillageNeeds.supply(VillageNeeds.Need.COMMERCE, VillageNeeds.input(v)));
		// nivel de construcao conta: plantacao III alimenta 12
		v.buildingsState().setLevel(lotOf(v, "farm").id(), 3);
		v.buildingsState().setCounts(12, 0);
		assertEquals(100, VillageNeeds.values(v).get(VillageNeeds.Need.FOOD));
	}

	private static int countBad(Map<VillageNeeds.Need, Integer> values) {
		return (int) values.values().stream().filter(x -> x < VillageNeeds.BAD).count();
	}

	@Test
	void craftBuildingsWithoutAFreeResidentStandStill() {
		VillageState v = planned(VillageLevel.TOWN, VillageProject.HOUSE, VillageProject.FARM, VillageProject.SMITHY, VillageProject.MARKET,
				VillageProject.TOWN_HALL);
		// 1 casa = 2 moradores; plantacao, forja e mercado pedem 3
		assertEquals(2, VillageBuildings.residents(v));
		assertEquals(3, VillageBuildings.workersNeeded(v));
		Set<Integer> idle = VillageBuildings.idle(v);
		assertEquals(1, idle.size());
		assertTrue(idle.contains(lotOf(v, "market").id()), "a obra mais nova fica parada");
		assertEquals(0, VillageBuildings.level(v, "market"), "parada nao funciona");
		assertEquals(1, VillageBuildings.bestLevel(v, "market"), "mas conta como pronta");
		// casa II abriga mais um: todas funcionam
		v.buildingsState().setLevel(lotOf(v, "house").id(), 2);
		assertTrue(VillageBuildings.idle(v).isEmpty());
		assertEquals(1, VillageBuildings.level(v, "market"));
	}

	@Test
	void professionCapNeedsTheBuildingLevelAboveJourneyman() {
		assertEquals(3, VillageBuildings.professionCap(5, 0), "fora de obra: jornaleiro");
		assertEquals(3, VillageBuildings.professionCap(5, 2));
		assertEquals(4, VillageBuildings.professionCap(5, 4));
		assertEquals(5, VillageBuildings.professionCap(5, 5), "mestre so com a obra no V");
		assertEquals(4, VillageBuildings.professionCap(4, 5), "teto da vila ainda vale");
		assertEquals(2, VillageBuildings.professionCap(2, 5));
		VillageState v = planned(VillageLevel.CAPITAL, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.SMITHY, VillageProject.TOWN_HALL);
		VillageLayout.Lot smithy = lotOf(v, "smithy");
		v.buildingsState().setLevel(smithy.id(), 4);
		assertEquals(4, VillageBuildings.professionCap(v, smithy.id()));
		assertEquals(3, VillageBuildings.professionCap(v, -1));
	}

	@Test
	void forgeRepairIsCheaperAtHigherSmithyLevels() {
		assertEquals(0, VillageShop.forgeRepair(0, 250, 1)[0]);
		int[] one = VillageShop.forgeRepair(250, 250, 1);
		int[] five = VillageShop.forgeRepair(250, 250, 5);
		assertEquals(5, one[0]);
		assertEquals(4, one[1]);
		assertEquals(1, five[0]);
		assertEquals(3, five[1]);
		assertEquals(1, VillageShop.forgeRepair(10, 250, 1)[0], "minimo 1");
		assertEquals("smithy:2", VillageShop.requirement(VillageShop.byId("cobalt_pickaxe")));
		VillageState v = planned(VillageLevel.CAPITAL, VillageProject.HOUSE, VillageProject.SMITHY, VillageProject.TOWN_HALL);
		assertTrue(VillageShop.available(VillageShop.byId("repair"), v));
		assertFalse(VillageShop.available(VillageShop.byId("cobalt_pickaxe"), v));
		v.buildingsState().setLevel(lotOf(v, "smithy").id(), 2);
		assertTrue(VillageShop.available(VillageShop.byId("cobalt_pickaxe"), v));
	}

	@Test
	void projectsNeededCountsUpgradePoints() {
		// da Capital em diante a tabela pesa mais que so as obras: as melhorias completam
		assertEquals(VillageLevel.CAPITAL.projects(), VillageLevel.CAPITAL.projectsNeeded());
		assertEquals(VillageLevel.TOWN.projects(), VillageLevel.TOWN.projectsNeeded());
	}

	@Test
	void savedFormatKeepsLevelsAndOldFilesLoadAtLevelOne() {
		VillageState v = planned(VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.TOWN_HALL);
		VillageLayout.Lot house = lotOf(v, "house");
		v.buildingsState().setLevel(lotOf(v, "town_hall").id(), 2);
		Map<String, Integer> all = new HashMap<>();
		BuildingTypes.upgradeCost(VillageProject.HOUSE, 2).forEach(c -> all.put(c.id(), c.count() / 2));
		v.buildingsState().deposit(house.id(), VillageProject.HOUSE, 1, 2, all);
		v.buildingsState().recordPaid(house.id(), Map.of("minecraft:oak_planks", 48));
		v.buildingsState().setCounts(7, 2);
		v.buildingsState().setNeedsDay(12);
		var json = VillageData.VILLAGE_CODEC.encodeStart(JsonOps.INSTANCE, v).getOrThrow();
		VillageState again = VillageData.VILLAGE_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(2, again.buildingsState().level(lotOf(again, "town_hall").id()));
		assertEquals(1, again.buildingsState().level(house.id()));
		BuildingsState.Upgrade up = again.buildingsState().upgrade(house.id());
		assertNotNull(up);
		assertEquals(2, up.toLevel());
		assertEquals(Map.of("minecraft:oak_planks", 48), up.paid());
		assertEquals(7, again.buildingsState().population());
		assertEquals(12, again.buildingsState().needsDay());
		// arquivo de antes (sem o campo): tudo no nivel I, sem melhoria
		var obj = json.getAsJsonObject();
		obj.remove("buildings");
		VillageState old = VillageData.VILLAGE_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(obj.toString())).getOrThrow();
		assertEquals(1, old.buildingsState().level(lotOf(old, "town_hall").id()));
		assertNull(old.buildingsState().upgrade(house.id()));
		assertTrue(old.buildingsState().isEmpty());
	}
}
