package com.mlin.notas.village;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Terrenos do jogador (Vilas 3.0 fase 5): reserva na vila nova (2 lotes L no anel 2-3, planos, com rua e longe um do
 * outro), registro numa vila que ja existe (lotes livres saem, obras e ruas barram), campo pinado com caminho (ate fora
 * do plano) e plano salvo de antes (sem terrenos) que continua carregando.
 */
class PlotPlannerTest {
	private static final long[] SEEDS = {11, 2024};
	private static final String[] TERRAINS = {"flat", "slope", "river", "noisy"};

	private static HeightFieldBuilder.Result build(String terrain, long seed) {
		HeightFieldBuilder.Natural natural = HeightFieldTest.terrains(seed).get(terrain);
		int plaza = HeightFieldBuilder.plazaHeight(natural, HeightFieldTest.MIN_Y);
		return HeightFieldBuilder.forNewVillage(seed, 0, 0, plaza, natural, HeightFieldTest.MIN_Y, List.of(), FieldPins.none(), PlotPlanner.COUNT);
	}

	@Test
	void newVillageReservesTwoFlatPlotsWithStreetAccess() {
		for (long seed : SEEDS) {
			for (String terrain : TERRAINS) {
				HeightFieldBuilder.Result r = build(terrain, seed);
				MasterPlan plan = r.plan();
				HeightField field = r.field();
				String what = terrain + "#" + seed;
				List<MasterPlan.Parcel> plots = plan.plots();
				assertEquals(PlotPlanner.COUNT, plots.size(), what + ": dois terrenos");
				for (MasterPlan.Parcel p : plots) {
					assertTrue(p.player() && p.plot() && !p.retired(), what);
					assertTrue(p.along() >= SizeClass.L.width() && p.deep() >= SizeClass.L.depth(), what + ": tamanho L " + p.along() + "x" + p.deep());
					MasterPlan.Block block = plan.block(p.block());
					assertNotNull(block, what);
					assertTrue(block.ring() == 2 || block.ring() == 3, what + ": anel " + block.ring());
					assertEquals(block.target(), plan.plotFloor(p), what + ": chao do terreno = plato");
					VillageState.Rect rect = p.rect();
					for (int x = rect.x0(); x <= rect.x1(); x++) {
						for (int z = rect.z0(); z <= rect.z1(); z++) {
							assertTrue(plan.code(x, z) >= MasterPlan.BLOCK, what + ": so celulas de quarteirao em " + x + "," + z);
							assertEquals(plan.plotFloor(p), field.heightRel(x, z), what + ": plano em " + x + "," + z);
						}
					}
					List<int[]> path = PlotPlanner.streetPath(plan, p, 8);
					assertFalse(path.isEmpty(), what + ": sem caminho ate a rua");
					int prev = plan.plotFloor(p);
					for (int[] c : path) {
						int h = field.heightRel(c[0], c[1]);
						assertTrue(Math.abs(h - prev) <= 1, what + ": degrau no caminho em " + c[0] + "," + c[1]);
						prev = h;
					}
					assertTrue(HeightFieldTest.walkable(field, rect), what + ": nao alcanca a rua a pe");
				}
				// nao se sobrepoe a nenhum outro lote nem entre si, e ficam em lados diferentes da praca
				MasterPlan.Parcel a = plots.get(0);
				MasterPlan.Parcel b = plots.get(1);
				assertFalse(a.rect().overlaps(b.rect(), 0), what);
				for (MasterPlan.Parcel p : plan.parcels()) {
					if (!p.retired() && !p.plot()) {
						for (MasterPlan.Parcel plot : plots) {
							assertFalse(p.rect().overlaps(plot.rect(), 0), what + ": lote " + p.id() + " sobre o terreno");
						}
					}
				}
				double ax = (a.rect().x0() + a.rect().x1()) / 2.0;
				double az = (a.rect().z0() + a.rect().z1()) / 2.0;
				double bx = (b.rect().x0() + b.rect().x1()) / 2.0;
				double bz = (b.rect().z0() + b.rect().z1()) / 2.0;
				assertTrue(ax * bx + az * bz < 0, what + ": os dois terrenos do mesmo lado da vila");
				assertTrue(Math.hypot(ax, az) < 50 && Math.hypot(bx, bz) < 50, what + ": longe da praca");
			}
		}
	}

	@Test
	void plotsSurviveSaveAndOldPlansLoad() {
		HeightFieldBuilder.Result r = build("flat", 11);
		MasterPlan plan = r.plan();
		MasterPlan.Parcel first = plan.plots().get(0);
		first.setTerraformPending(true);
		var json = VillageData.MASTER_CODEC.encodeStart(JsonOps.INSTANCE, plan).getOrThrow();
		MasterPlan back = VillageData.MASTER_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(2, back.plots().size());
		MasterPlan.Parcel again = back.parcel(first.id());
		assertTrue(again.plot() && again.terraformPending());
		assertEquals(plan.plotFloor(first), back.plotFloor(again));
		assertEquals(first.rect(), again.rect());
		// chao negativo
		MasterPlan.Parcel p = new MasterPlan.Parcel(0, 0, new VillageState.Rect(0, 0, 1, 1), Facing.SOUTH, SizeClass.S, Zone.CENTER, false);
		p.setPlot(true);
		p.setFloor(-30);
		MasterPlan.Parcel q = new MasterPlan.Parcel(0, 0, new VillageState.Rect(0, 0, 1, 1), Facing.SOUTH, SizeClass.S, Zone.CENTER, false);
		q.restoreFlags(p.flags());
		assertTrue(q.plot());
		assertEquals(-30, q.floor());
		// formato antigo: 0 = lote comum, 1 = lote do jogador (achado por construcao), nunca terreno
		MasterPlan.Parcel old = new MasterPlan.Parcel(0, 0, new VillageState.Rect(0, 0, 1, 1), Facing.SOUTH, SizeClass.S, Zone.CENTER, false);
		old.restoreFlags(1);
		assertTrue(old.player() && !old.plot() && old.builtByPlayer());
		old.restoreFlags(0);
		assertFalse(old.player());
		assertEquals(Integer.MIN_VALUE, old.floor());
		// plano de antes (sem terrenos) segue igual
		MasterPlan plain = RuralRingTest.expanded(new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(2)), 5);
		MasterPlan plainBack = VillageData.MASTER_CODEC.parse(JsonOps.INSTANCE, VillageData.MASTER_CODEC.encodeStart(JsonOps.INSTANCE, plain).getOrThrow())
				.getOrThrow();
		assertTrue(plainBack.plots().isEmpty());
		assertEquals(plain.parcels().size(), plainBack.parcels().size());
	}

	@Test
	void registeringAPlotRetiresFreeLotsAndRefusesBuildingsStreetsAndOtherPlots() {
		MasterPlan plan = RuralRingTest.expanded(new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(2)), 41);
		// retangulo de 5x5 dentro de um quarteirao do anel 3
		MasterPlan.Block block = null;
		for (MasterPlan.Block b : plan.blocks()) {
			if (b.ring() == 3 && b.cells() > 300) {
				block = b;
				break;
			}
		}
		assertNotNull(block);
		VillageState.Rect rect = null;
		search:
		for (int x = block.cx() - 12; x <= block.cx() + 12; x++) {
			for (int z = block.cz() - 12; z <= block.cz() + 12; z++) {
				boolean ok = true;
				for (int dx = 0; dx < 5 && ok; dx++) {
					for (int dz = 0; dz < 5 && ok; dz++) {
						ok = plan.blockAt(x + dx, z + dz) == block;
					}
				}
				if (ok) {
					rect = new VillageState.Rect(x, z, x + 4, z + 4);
					break search;
				}
			}
		}
		assertNotNull(rect, "retangulo livre no quarteirao");
		int before = plan.parcels().size();
		List<Integer> ids = plan.parcels().stream().map(MasterPlan.Parcel::id).toList();
		// obra reservada no lote que o retangulo toca: recusa e o plano nao muda
		MasterPlan.Parcel hit = null;
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (!p.retired() && p.rect().overlaps(rect, 0)) {
				hit = p;
				break;
			}
		}
		if (hit != null) {
			PlotPlanner.Added no = PlotPlanner.add(plan, rect, 70, Set.of(hit.id()), List.of());
			assertEquals(PlotPlanner.Problem.LOT, no.problem());
			assertEquals(before, plan.parcels().size(), "recusado nao muda o plano");
		}
		PlotPlanner.Added ok = PlotPlanner.add(plan, rect, 70, Set.of(), List.of());
		assertNull(ok.problem());
		MasterPlan.Parcel plot = ok.plot();
		assertTrue(plot.plot() && plot.floor() == 70);
		assertEquals(before, plot.id(), "id novo no fim");
		for (int id : ids) {
			assertEquals(id, plan.parcel(id).id());
		}
		for (MasterPlan.Parcel p : ok.retired()) {
			assertTrue(plan.parcel(p.id()).retired());
		}
		for (int x = rect.x0(); x <= rect.x1(); x++) {
			for (int z = rect.z0(); z <= rect.z1(); z++) {
				assertEquals(MasterPlan.CARVED, plan.code(x, z));
				assertTrue(plan.insidePlayerArea(x, z) && plan.insidePlot(x, z));
			}
		}
		assertEquals(plot, plan.parcelAt(rect.x0(), rect.z0()));
		// outro terreno em cima: recusa; pequeno demais: recusa
		assertEquals(PlotPlanner.Problem.PLOT, PlotPlanner.add(plan, rect, 70, Set.of(), List.of()).problem());
		assertEquals(PlotPlanner.Problem.TOO_SMALL, PlotPlanner.add(plan, new VillageState.Rect(0, 0, 2, 2), 70, Set.of(), List.of()).problem());
		// em cima da praca: recusa
		assertEquals(PlotPlanner.Problem.STREET, PlotPlanner.add(plan, new VillageState.Rect(-3, -3, 3, 3), 70, Set.of(), List.of()).problem());
		// obra pronta (retangulo de lote) barra
		VillageState.Rect far = new VillageState.Rect(rect.x1() + 40, rect.z1() + 40, rect.x1() + 50, rect.z1() + 50);
		assertEquals(PlotPlanner.Problem.LOT, PlotPlanner.add(plan, far, 70, Set.of(),
				List.of(new VillageState.Rect(far.x0() + 4, far.z0() + 4, far.x0() + 6, far.z0() + 6))).problem());
		assertTrue(PlotPlanner.remove(plan, plot.id()));
		assertTrue(plan.plots().isEmpty());
	}

	/** Terreno pinado: chao exato, rampa de 1 e caminho andavel ate a rua, dentro do anel rural e fora do plano (faixa de transicao). */
	@Test
	void pinnedPlotGetsLevelSurroundingsAndAPathEvenOutsideThePlan() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(2));
		MasterPlan plan = RuralRingTest.expanded(t, 41);
		HeightFieldBuilder.Natural natural = (x, z) -> MasterPlanTest.TERRAINS.get(2).applyAsInt(x, z);
		for (VillageState.Rect rect : List.of(new VillageState.Rect(60, 20, 75, 31), new VillageState.Rect(124, 10, 135, 18))) {
			int floor = plan.plazaY() + 3;
			FieldPins pins = new FieldPins().plot(rect, floor);
			HeightField f = HeightFieldBuilder.forPlan(plan, 0, 0, natural, HeightFieldTest.MIN_Y, pins);
			for (int x = rect.x0(); x <= rect.x1(); x++) {
				for (int z = rect.z0(); z <= rect.z1(); z++) {
					assertEquals(floor, f.heightRel(x, z), "chao do terreno " + rect);
				}
			}
			assertTrue(HeightFieldTest.walkable(f, rect), "terreno " + rect + " sem caminho andavel ate a rua");
		}
	}
}
