package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anel rural fora da muralha externa (Vilas 2.0 fase 8): o plano salvo nunca muda, os campos ganham quarteiroes e lotes
 * rurais com rua andavel ate a praca pelos portoes, e os tipos rurais preferem o anel e nao tem camas.
 */
class RuralRingTest {
	static MasterPlan expanded(VillagePlannerTest.FakeTerrain t, long seed) {
		return RuralRing.expand(MasterPlanTest.plan(t, seed), t);
	}

	@Test
	void theSavedPlanNeverChanges() {
		for (IntBinaryOperator h : MasterPlanTest.TERRAINS) {
			VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(h);
			MasterPlan old = MasterPlanTest.plan(t, 31);
			int e = old.extent();
			int[] codes = new int[(2 * e + 1) * (2 * e + 1)];
			int[] heights = new int[codes.length];
			int[] rings = new int[codes.length];
			for (int x = -e; x <= e; x++) {
				for (int z = -e; z <= e; z++) {
					int i = old.idx(x, z);
					codes[i] = old.code(x, z);
					heights[i] = old.plannedHeight(x, z);
					rings[i] = old.ring(x, z);
				}
			}
			List<Integer> targets = old.blocks().stream().map(MasterPlan.Block::target).toList();
			List<VillageState.Rect> rects = old.parcels().stream().map(MasterPlan.Parcel::rect).toList();
			MasterPlan plan = RuralRing.expand(old, t);
			assertEquals(RuralRing.EXTENT, plan.extent());
			assertTrue(plan.rural());
			assertSame(plan, RuralRing.expand(plan, t), "expandir de novo nao muda nada");
			for (int x = -e; x <= e; x++) {
				for (int z = -e; z <= e; z++) {
					if (RuralRing.outsideOld(x, z)) {
						continue;
					}
					int i = old.idx(x, z);
					assertEquals(codes[i], plan.code(x, z), "celula antiga mudou em " + x + "," + z);
					assertEquals(heights[i], plan.plannedHeight(x, z), "altura antiga mudou em " + x + "," + z);
					assertEquals(rings[i], plan.ring(x, z));
				}
			}
			for (int b = 0; b < targets.size(); b++) {
				assertEquals(targets.get(b), plan.block(b).target(), "plato antigo mudou");
			}
			for (int p = 0; p < rects.size(); p++) {
				assertEquals(rects.get(p), plan.parcel(p).rect(), "lote antigo mudou");
			}
			// mesma semente e terreno: mesmo anel rural
			MasterPlan again = RuralRing.expand(MasterPlanTest.plan(t, 31), t);
			assertEquals(plan.parcels().size(), again.parcels().size());
			for (int x = -RuralRing.EXTENT; x <= RuralRing.EXTENT; x += 3) {
				for (int z = -RuralRing.EXTENT; z <= RuralRing.EXTENT; z += 3) {
					assertEquals(plan.code(x, z), again.code(x, z));
				}
			}
		}
	}

	@Test
	void ruralLotsFaceAStreetThatWalksToThePlazaThroughTheGates() {
		long seed = 7;
		for (IntBinaryOperator h : MasterPlanTest.TERRAINS) {
			VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(h);
			long start = System.nanoTime();
			MasterPlan plan = expanded(t, seed);
			long ms = (System.nanoTime() - start) / 1_000_000;
			assertTrue(ms < 15000, "anel rural lento: " + ms + " ms");
			MasterPlanTest.dump(plan, "rural" + MasterPlanTest.TERRAINS.indexOf(h));
			Set<Long> walk = MasterPlanTest.walk(plan);
			int rural = 0;
			int reachable = 0;
			java.util.Map<SizeClass, Integer> classes = new java.util.EnumMap<>(SizeClass.class);
			for (MasterPlan.Parcel p : plan.parcels()) {
				MasterPlan.Block block = plan.block(p.block());
				if (p.retired() || block.ring() != RuralRing.RING) {
					continue;
				}
				rural++;
				classes.merge(p.size(), 1, Integer::sum);
				assertEquals(Zone.RURAL, p.zone());
				VillageState.Rect r = p.rect();
				for (int x = r.x0(); x <= r.x1(); x++) {
					for (int z = r.z0(); z <= r.z1(); z++) {
						assertEquals(MasterPlan.BLOCK + block.id(), plan.code(x, z), "lote fora do quarteirao");
						assertTrue(RuralRing.inFields(x, z), "lote rural fora dos campos em " + x + "," + z);
					}
				}
				for (MasterPlan.Parcel q : plan.parcels()) {
					if (q != p && !q.retired()) {
						assertFalse(r.overlaps(q.rect(), 0), "lotes encostados");
					}
				}
				int[] door = MasterPlanTest.doorCell(p);
				boolean ok = false;
				for (int k = 1; k <= BlockSubdivider.ACCESS_MAX && !ok; k++) {
					int sx = door[0] + p.front().dx * k;
					int sz = door[1] + p.front().dz * k;
					if (plan.ground(sx, sz)) {
						assertTrue(Math.abs(plan.streetHeight(sx, sz) - block.target()) <= 1, "degrau entre o lote e a rua");
						ok = true;
						if (walk.contains(VillageLayout.col(sx, sz))) {
							reachable++;
						}
					}
				}
				assertTrue(ok, "lote rural " + p.id() + " sem rua na frente");
			}
			System.out.println("terreno " + MasterPlanTest.TERRAINS.indexOf(h) + ": " + rural + " lotes rurais " + classes + ", " + reachable
					+ " com rua andavel ate a praca");
			assertTrue(rural >= 40, "poucos lotes no anel rural: " + rural);
			// o terreno em rampa forte (1 a cada 3) pode deixar alguma avenida sem rampa suave na faixa da muralha
			assertTrue(reachable >= rural * 3 / 4, "lotes rurais sem caminho a pe ate a praca: " + (rural - reachable) + " de " + rural);
			if (MasterPlanTest.TERRAINS.indexOf(h) != 1) {
				assertEquals(rural, reachable, "lote rural sem caminho a pe ate a praca");
			}
			seed += 13;
		}
	}

	@Test
	void ruralStreetsRampAtMostOne() {
		for (IntBinaryOperator h : List.of(MasterPlanTest.TERRAINS.get(0), MasterPlanTest.TERRAINS.get(2), MasterPlanTest.TERRAINS.get(3))) {
			VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(h);
			MasterPlan plan = expanded(t, 5);
			int e = plan.extent();
			for (int x = -e; x <= e; x++) {
				for (int z = -e; z <= e; z++) {
					if (!plan.ground(x, z) || !RuralRing.outsideOld(x, z)) {
						continue;
					}
					for (Facing f : Facing.values()) {
						if (plan.ground(x + f.dx, z + f.dz)) {
							assertTrue(Math.abs(plan.streetHeight(x + f.dx, z + f.dz) - plan.streetHeight(x, z)) <= 1, "rampa na rua rural em " + x + "," + z);
						}
					}
				}
			}
		}
	}

	@Test
	void avenuesCrossTheWallBandAndTheBandHasNoLots() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		MasterPlan plan = expanded(t, 4321);
		int e = plan.extent();
		int gates = 0;
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (!RuralRing.inBand(x, z)) {
					continue;
				}
				assertTrue(plan.code(x, z) == MasterPlan.WALL_BAND || plan.streetKind(x, z) == StreetPlanner.AVENUE, "faixa da muralha com " + plan.code(x, z));
				assertTrue(plan.parcelAt(x, z) == null);
				if (RuralRing.wallLine(x, z) && plan.street(x, z)) {
					gates++;
				}
			}
		}
		assertTrue(gates >= 4 * 5, "avenidas nao passam pela muralha: " + gates);
		// avenidas dos 4 eixos saem pela borda
		assertTrue(plan.street(RuralRing.EDGE, 0) || plan.street(RuralRing.EDGE, 1) || plan.street(RuralRing.EDGE, -1));
		assertTrue(plan.street(0, -RuralRing.EDGE) || plan.street(1, -RuralRing.EDGE) || plan.street(-1, -RuralRing.EDGE));
	}

	@Test
	void playerBuildingsInTheFieldsAreCutOut() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		// casa de jogador no meio dos campos ao norte, em cima da rua do anel
		for (int x = 10; x <= 17; x++) {
			for (int z = -100; z <= -92; z++) {
				t.built.add(VillagePlannerTest.FakeTerrain.col(x, z));
			}
		}
		MasterPlan plan = expanded(t, 11);
		for (int x = 10; x <= 17; x++) {
			for (int z = -100; z <= -92; z++) {
				assertEquals(MasterPlan.CARVED, plan.code(x, z), "casa de jogador dentro do plano rural em " + x + "," + z);
				assertTrue(plan.parcelAt(x, z) == null);
			}
		}
		assertTrue(plan.insidePlayerArea(12, -95));
		// a rua do anel contorna a casa: continua andavel dos dois lados
		Set<Long> walk = MasterPlanTest.walk(plan);
		assertTrue(walk.contains(VillageLayout.col(0, -95)) && walk.contains(VillageLayout.col(30, -95)), "rua do anel cortada pela casa");
	}

	@Test
	void ruralTypesPreferTheRuralRingAndHaveNoBeds() {
		for (String kind : RuralRing.RURAL_KINDS) {
			LotRules.Rule rule = LotRules.of(kind);
			assertEquals(Zone.RURAL, rule.zones().get(0), kind + " deveria preferir o anel rural");
			assertTrue(rule.rural());
			int max = Math.max(1, BuildingTypes.maxLevel(kind));
			for (int level = 1; level <= max; level++) {
				for (long seed = 1; seed <= 4; seed++) {
					Canvas c = BuildingPlans.plan(kind, VillageStyle.PLAINS, seed, level);
					assertNotNull(c, kind);
					for (Canvas.Cell cell : c.cells().values()) {
						assertTrue(cell.part() != VPart.BED && cell.part() != VPart.BED_HEAD, kind + " " + level + " tem cama: quem trabalha nos campos dorme dentro da muralha");
					}
				}
			}
		}
		// casas nunca vao para os campos
		assertFalse(LotRules.of("house").allows(Zone.RURAL));
		assertFalse(LotRules.accepts(LotRules.of("house"), Zone.RURAL, true, true));
	}

	@Test
	void ringOpensWithTheOutskirts() {
		assertFalse(MasterPlan.isOpen(RuralRing.RING, VillageLevel.VILLAGE.rings()));
		assertTrue(MasterPlan.isOpen(RuralRing.RING, VillageLevel.TOWN.rings()));
		assertTrue(MasterPlan.isOpen(3, VillageLevel.TOWN.rings()));
		assertFalse(MasterPlan.isOpen(6, VillageLevel.TOWN.rings()));
		assertEquals(Zone.RURAL, Zone.ofRing(RuralRing.RING));
	}

	@Test
	void growingAVillageMovesRuralTypesOutAndKeepsTheirOldLots() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		VillageState v = new VillageState("minecraft:overworld", 0, 64, 0, 4321L, VillageStyle.PLAINS, "Teste");
		v.setLevel(VillageLevel.CAMP);
		VillageLayout layout = VillageLayout.create(v, t);
		v.setLayout(layout);
		List<String> before = new ArrayList<>();
		for (VillageLevel level : VillageLevel.values()) {
			v.setLevel(level);
			layout.extend(v, t, level);
			if (level == VillageLevel.VILLAGE) {
				for (VillageLayout.Lot lot : layout.lots()) {
					before.add(lot.id() + ":" + lot.kind() + "@" + lot.parcel());
				}
				assertFalse(layout.master().rural(), "o anel rural so abre na Cidade");
			}
		}
		assertTrue(layout.master().rural());
		List<String> after = new ArrayList<>();
		for (VillageLayout.Lot lot : layout.lots()) {
			after.add(lot.id() + ":" + lot.kind() + "@" + lot.parcel());
		}
		assertTrue(after.containsAll(before), "lote reservado antes do anel rural mudou");
		int farmsOutside = 0;
		for (VillageLayout.Lot lot : layout.lots()) {
			MasterPlan.Parcel p = layout.master().parcel(lot.parcel());
			if (RuralRing.RURAL_KINDS.contains(lot.kind()) && p.zone() == Zone.RURAL) {
				farmsOutside++;
			}
			if (p.zone() == Zone.RURAL) {
				assertTrue(RuralRing.RURAL_KINDS.contains(lot.kind()), lot.kind() + " no anel rural");
			}
		}
		// as plantacoes de antes da Cidade ficam onde estavam; o resto dos rurais vai para fora
		assertTrue(farmsOutside >= 9, "tipos rurais no anel rural: " + farmsOutside);
	}
}
