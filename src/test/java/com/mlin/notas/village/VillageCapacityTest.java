package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capacidade do plano diretor (Vilas 2.0, integracao das fases 5-7): em cada nivel da vila todo tipo com lote ganha os
 * lotes que {@link VillageProject#maxBuilds} promete, em varios terrenos e sementes. O plano tem ~38 lotes S/M/L nos
 * aneis 2, 3 e 5 (os quarteiroes tem ~14 de fundo: uma fileira de lotes so); os lotes ainda livres sao recortados sob
 * demanda ({@link VillageLayout#carveParcel}) e os maiores sao reservados primeiro.
 */
class VillageCapacityTest {
	static final List<IntBinaryOperator> TERRAINS = new ArrayList<>(MasterPlanTest.TERRAINS);
	static final long[] SEEDS = {4321L, 11L, 77L, 2024L, 99_991L};

	static {
		// a encosta do VillageLayoutTest
		TERRAINS.add((x, z) -> 64 + Math.floorDiv(x, 9));
	}

	/** Lotes do plano (vivos) por anel e classe. */
	static Map<Integer, Map<SizeClass, Integer>> parcelsPerRing(MasterPlan plan) {
		Map<Integer, Map<SizeClass, Integer>> out = new TreeMap<>();
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (!p.retired()) {
				out.computeIfAbsent(plan.block(p.block()).ring(), r -> new EnumMap<>(SizeClass.class)).merge(p.size(), 1, Integer::sum);
			}
		}
		return out;
	}

	/** Lotes reservados (pela classe do tipo) por anel. */
	static Map<Integer, Map<SizeClass, Integer>> reservedPerRing(VillageLayout layout) {
		Map<Integer, Map<SizeClass, Integer>> out = new TreeMap<>();
		for (VillageLayout.Lot lot : layout.lots()) {
			MasterPlan.Parcel p = layout.master().parcel(lot.parcel());
			if (p != null) {
				out.computeIfAbsent(layout.master().block(p.block()).ring(), r -> new EnumMap<>(SizeClass.class))
						.merge(LotRules.of(lot.kind()).size(), 1, Integer::sum);
			}
		}
		return out;
	}

	static VillageLayout grow(VillagePlannerTest.FakeTerrain t, long seed, List<String> shortfalls) {
		return grow(t, seed, shortfalls, null);
	}

	/** {@code shortfalls}: essenciais que faltaram (ou qualquer tipo acima do maximo); {@code optional}: oficios sem lugar. */
	static VillageLayout grow(VillagePlannerTest.FakeTerrain t, long seed, List<String> shortfalls, List<String> optional) {
		VillageState v = new VillageState("minecraft:overworld", 0, t.ground(0, 0), 0, seed, VillageStyle.PLAINS, "Teste");
		v.setLevel(VillageLevel.CAMP);
		VillageLayout layout = VillageLayout.create(v, t);
		v.setLayout(layout);
		for (VillageLevel level : VillageLevel.values()) {
			v.setLevel(level);
			layout.extend(v, t, level);
			for (VillageProject project : VillageLayout.LOT_PROJECTS) {
				int got = layout.lotsOf(project.id()).size();
				if (got > project.maxBuilds(level) || got < project.maxBuilds(level) && VillageLayout.essential(project)) {
					shortfalls.add(level + ":" + project.id() + " " + got + "/" + project.maxBuilds(level));
				} else if (got < project.maxBuilds(level) && optional != null) {
					optional.add(level + ":" + project.id());
				}
			}
		}
		return layout;
	}

	@Test
	void everyTypeGetsItsLotsAtEveryLevel() {
		List<String> problems = new ArrayList<>();
		int optionalMissing = 0;
		int runs = 0;
		for (IntBinaryOperator h : TERRAINS) {
			for (long seed : SEEDS) {
				VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(h);
				List<String> shortfalls = new ArrayList<>();
				List<String> optional = new ArrayList<>();
				MasterPlan before = MasterPlanTest.plan(t, seed);
				VillageLayout layout = grow(t, seed, shortfalls, optional);
				long crownMissing = optional.stream().filter(s -> s.startsWith("CROWN_CITY")).count();
				optionalMissing += crownMissing;
				if (crownMissing > 3) {
					problems.add("terreno " + TERRAINS.indexOf(h) + " semente " + seed + ": " + crownMissing + " oficios sem lugar na Coroa");
				}
				runs++;
				System.out.println("terreno " + TERRAINS.indexOf(h) + " semente " + seed + ": lotes do plano " + parcelsPerRing(before)
						+ " -> reservados na Coroa " + reservedPerRing(layout) + (shortfalls.isEmpty() ? "" : " FALTA " + shortfalls) + (optional.isEmpty() ? "" : " sem lugar " + optional));
				shortfalls.forEach(s -> problems.add("terreno " + TERRAINS.indexOf(h) + " semente " + seed + " " + s));
				// recortes nunca sobrepoem nem saem do quarteirao
				MasterPlan plan = layout.master();
				List<MasterPlan.Parcel> live = plan.parcels().stream().filter(p -> !p.retired()).toList();
				for (int i = 0; i < live.size(); i++) {
					VillageState.Rect r = live.get(i).rect();
					for (int x = r.x0(); x <= r.x1(); x++) {
						for (int z = r.z0(); z <= r.z1(); z++) {
							assertEquals(MasterPlan.BLOCK + live.get(i).block(), plan.code(x, z), "lote fora do quarteirao");
						}
					}
					for (int j = i + 1; j < live.size(); j++) {
						assertFalse(r.overlaps(live.get(j).rect(), 0), "lotes do plano sobrepostos");
					}
				}
			}
		}
		assertEquals(List.of(), problems, "lotes que o plano nao conseguiu dar");
		System.out.println("oficios sem lugar na Coroa: " + optionalMissing + " em " + runs + " planos");
		// guarda contra regressao: antes do anel rural (fase 8) ~1 oficio sem lugar a cada 2 planos (12 em 25); com os
		// tipos rurais fora da muralha externa, nenhum (0 em 25)
		assertTrue(optionalMissing <= runs / 10, "oficios demais sem lugar: " + optionalMissing + " em " + runs);
	}

	@Test
	void reservedLotsNeverMoveWhenTheFreeOnesAreRecut() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		VillageState v = new VillageState("minecraft:overworld", 0, 64, 0, 4321L, VillageStyle.PLAINS, "Teste");
		v.setLevel(VillageLevel.CAMP);
		VillageLayout layout = VillageLayout.create(v, t);
		v.setLayout(layout);
		Map<Integer, VillageState.Rect> parcels = new TreeMap<>();
		Map<Integer, String> lots = new TreeMap<>();
		int recuts = 0;
		for (VillageLevel level : VillageLevel.values()) {
			v.setLevel(level);
			int before = layout.master().parcels().size();
			layout.extend(v, t, level);
			recuts += layout.drainRetired().size();
			for (VillageLayout.Lot lot : layout.lots()) {
				MasterPlan.Parcel p = layout.master().parcel(lot.parcel());
				VillageState.Rect old = parcels.putIfAbsent(lot.parcel(), p.rect());
				assertTrue(old == null || old.equals(p.rect()), level + ": lote reservado mudou");
				assertFalse(p.retired(), level + ": lote reservado aposentado");
				String kind = lots.putIfAbsent(lot.id(), lot.kind() + "@" + lot.parcel());
				assertTrue(kind == null || kind.equals(lot.kind() + "@" + lot.parcel()), level + ": obra mudou de lote");
			}
			assertTrue(layout.master().parcels().size() >= before, "ids de lote sao indices: so crescem");
		}
		assertTrue(recuts > 0, "nenhum recorte sob demanda");
	}

	@Test
	void housesAndTheInnHouseEveryWorkerAtTheirTopLevel() {
		for (VillageLevel level : VillageLevel.values()) {
			int workers = 0;
			int residents = 0;
			for (VillageProject p : VillageLayout.LOT_PROJECTS) {
				workers += BuildingTypes.workers(p.id()) * p.maxBuilds(level);
				residents += BuildingTypes.residents(p.id(), BuildingTypes.maxLevel(p.id())) * p.maxBuilds(level);
			}
			System.out.println(level + ": trabalhadores " + workers + ", moradores no nivel maximo " + residents);
			assertTrue(residents >= workers, level + ": " + workers + " trabalhadores para " + residents + " moradores");
		}
	}

	@Test
	void requiredBuildingsCanBeBuiltBeforeTheLevelAsksForThem() {
		for (VillageLevel level : VillageLevel.values()) {
			if (level.ordinal() == 0) {
				continue;
			}
			VillageLevel before = VillageLevel.byOrdinal(level.ordinal() - 1);
			for (VillageProject p : level.required()) {
				assertTrue(p.maxBuilds(before) >= 1, level + " exige " + p.id() + ", que so abre depois");
			}
		}
		// o nivel minimo de cada projeto com lote e o primeiro nivel com obra permitida
		for (VillageProject p : VillageLayout.LOT_PROJECTS) {
			int first = 0;
			while (first < VillageLevel.values().length - 1 && p.maxBuilds(VillageLevel.byOrdinal(first)) == 0) {
				first++;
			}
			assertEquals(VillageLevel.byOrdinal(first), p.minLevel(), "nivel minimo de " + p.id());
		}
	}

	@Test
	void parcelsHoldBiggerClassesByTheirRealSize() {
		MasterPlan.Parcel grown = new MasterPlan.Parcel(0, 0, new VillageState.Rect(0, 0, 14, 12), Facing.SOUTH, SizeClass.S, Zone.DISTRICT, false);
		assertTrue(VillageLayout.holds(grown, SizeClass.M), "lote S crescido para 15x13 comporta M");
		assertFalse(VillageLayout.holds(grown, SizeClass.L));
		MasterPlan.Parcel wall = new MasterPlan.Parcel(1, 0, new VillageState.Rect(0, 0, 14, 12), Facing.SOUTH, SizeClass.WL, Zone.WALL, false);
		assertFalse(VillageLayout.holds(wall, SizeClass.S), "lote de muralha nao vira lote comum");
	}
}
