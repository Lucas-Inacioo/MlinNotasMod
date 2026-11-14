package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.MasterPlanTestAccess;
import com.mlin.notas.village.StreetPlanner;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillageStyle;
import com.mlin.notas.village.Zone;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Planejadores das obras publicas: nada solto, nada enterrado, tudo na altura do plano diretor, nada em lote reservado. */
class WorksPlannersTest {
	@Test
	void streetStagesAreGroundedAndGetRicherEachLevel() {
		for (int t = 0; t < WorksTestSupport.TERRAINS.size(); t++) {
			MasterPlan plan = WorksTestSupport.plan(t, 11 + t * 7);
			WorkContext ctx = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 5);
			int last = 0;
			for (int stage = 1; stage <= 5; stage++) {
				Blueprint bp = StreetWorks.plan(ctx, stage);
				WorksTestSupport.assertGrounded(plan, bp, "ruas " + stage + " terreno " + t);
				assertFalse(bp.isEmpty());
				if (stage >= 4) {
					assertTrue(bp.ordered().stream().anyMatch(b -> b.state().contains("_log")), "canteiro com arvores no estagio " + stage);
				}
				if (stage >= 5) {
					assertTrue(bp.ordered().stream().anyMatch(b -> b.state().contains("lantern[hanging=true]")), "poste duplo no estagio 5");
				}
				assertTrue(bp.size() >= last * 0.6, "cada estagio tem obra");
				last = bp.size();
			}
		}
	}

	@Test
	void plazaStagesFitInsideThePlaza() {
		for (int t = 0; t < WorksTestSupport.TERRAINS.size(); t++) {
			MasterPlan plan = WorksTestSupport.plan(t, 11 + t * 7);
			for (VillageStyle style : VillageStyle.values()) {
				WorkContext ctx = WorksTestSupport.ctx(plan, style, 9);
				for (int stage = 1; stage <= 5; stage++) {
					Blueprint bp = PlazaWorks.plan(ctx, stage);
					WorksTestSupport.assertGrounded(plan, bp, "praca " + stage + " " + style + " terreno " + t);
					if (stage == 5 && t == 0) {
						assertTrue(bp.ordered().stream().anyMatch(b -> b.state().equals("minecraft:lightning_rod")), "monumento no estagio 5");
						assertTrue(bp.ordered().stream().anyMatch(b -> b.state().equals("minecraft:water")), "chafariz");
					}
				}
			}
		}
	}

	@Test
	void plazaFeaturesGiveWayToWhatIsThere() {
		MasterPlan plan = WorksTestSupport.plan(0, 11);
		WorkContext free = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 9);
		WorkContext taken = new WorkContext(plan, VillageStyle.PLAINS, 9, MasterPlan.RINGS, (x, z) -> x >= 2 && z <= -2, Set.of(), List.of(), Set.of(), List.of(),
				"cobalt_golem", null, (x, z) -> Integer.MIN_VALUE);
		assertTrue(PlazaWorks.plan(free, 2).ordered().stream().anyMatch(b -> b.state().equals("minecraft:water")));
		assertFalse(PlazaWorks.plan(taken, 2).ordered().stream().anyMatch(b -> b.state().equals("minecraft:water")), "chafariz nao entra onde ja tem coisa");
		assertTrue(PlazaWorks.plan(taken, 4).ordered().stream().anyMatch(b -> b.state().equals("mlinnotas:cobalt_block")), "estatua do golem");
		assertEquals(StreetWorks.stageOf(StreetPlanner.RING, 3), 2);
	}

	@Test
	void parcelWorksStayInVacantLotsAndYardsAndAreGrounded() {
		int stakes = 0;
		for (int t = 0; t < WorksTestSupport.TERRAINS.size(); t++) {
			MasterPlan plan = WorksTestSupport.plan(t, 11 + t * 7);
			// os 3 primeiros lotes de cada anel estao reservados: nada de obra publica neles
			Set<Integer> reserved = new HashSet<>();
			int[] perRing = new int[7];
			for (MasterPlan.Parcel p : plan.parcels()) {
				int ring = plan.block(p.block()).ring();
				if (!p.retired() && perRing[ring]++ < 3) {
					reserved.add(p.id());
				}
			}
			WorkContext ctx = new WorkContext(plan, VillageStyle.PLAINS, 4, MasterPlan.RINGS, (x, z) -> false, reserved, List.of(), Set.of(), List.of(), "", null,
					(x, z) -> Integer.MIN_VALUE);
			for (int ring = 1; ring <= 6; ring++) {
				Blueprint bp = ParcelWorks.stakes(ctx, ring);
				WorksTestSupport.assertGrounded(plan, bp, "estacas anel " + ring + " terreno " + t);
				stakes += bp.size();
				for (WBlock b : bp.ordered()) {
					MasterPlan.Parcel p = plan.parcelAt(b.x(), b.z());
					assertNotNull(p, "estaca fora de lote");
					assertFalse(reserved.contains(p.id()), "estaca em lote reservado");
				}
			}
			for (Blueprint bp : List.of(ParcelWorks.gardens(ctx), ParcelWorks.rural(ctx))) {
				WorksTestSupport.assertGrounded(plan, bp, "jardins/zona rural terreno " + t);
				for (WBlock b : bp.ordered()) {
					MasterPlan.Parcel p = plan.parcelAt(b.x(), b.z());
					assertTrue(p == null || !reserved.contains(p.id()), "obra em lote reservado");
				}
			}
			assertTrue(ParcelWorks.gardens(ctx).size() > 0, "jardim nos lotes minusculos");
			assertTrue(ParcelWorks.rural(ctx).size() > 0, "horta/pomar/pasto nos arredores");
			for (Blueprint bp : List.of(ParcelWorks.trees(ctx), ParcelWorks.decor(ctx))) {
				WorksTestSupport.assertGrounded(plan, bp, "ruas verdes/decoracao terreno " + t);
				assertTrue(bp.size() > 0);
				for (WBlock b : bp.ordered()) {
					assertNull(plan.parcelAt(b.x(), b.z()), "arvore/decoracao dentro de lote em " + b.x() + "," + b.z());
				}
			}
		}
		assertTrue(stakes > 200, "estacas e placas: " + stakes);
	}

	@Test
	void stakeSignsFaceTheStreetAndHaveText() {
		MasterPlan plan = WorksTestSupport.plan(0, 11);
		WorkContext ctx = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 4);
		boolean any = false;
		for (WBlock b : ParcelWorks.stakes(ctx, 3).ordered()) {
			if (b.text() != null) {
				any = true;
				assertEquals("tr:sign.mlinnotas.free_lot", b.text()[0]);
				assertTrue(b.state().contains("_sign[rotation="));
			}
		}
		assertTrue(any);
	}

	@Test
	void bridgesHaveASmoothDeckBetweenTheTwoBanks() {
		MasterPlan plan = MasterPlanTestAccess.planWithRiver(3);
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			plan.setRingPrepared(ring);
		}
		List<BridgeWorks.Span> spans = BridgeWorks.spans(plan);
		assertFalse(spans.isEmpty(), "o rio tem que virar ponte onde a avenida passa");
		for (BridgeWorks.Span span : spans) {
			boolean touchesGround = false;
			for (int i = 0; i < span.cells().size(); i++) {
				int[] c = span.cells().get(i);
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = c[0] + d[0];
					int nz = c[1] + d[1];
					if (plan.ground(nx, nz)) {
						touchesGround = true;
						assertTrue(Math.abs(plan.streetHeight(nx, nz) - span.heights()[i]) <= 1, "degrau entre a ponta e o tabuleiro");
					}
					for (int j = 0; j < span.cells().size(); j++) {
						if (span.cells().get(j)[0] == nx && span.cells().get(j)[1] == nz) {
							assertTrue(Math.abs(span.heights()[j] - span.heights()[i]) <= 1, "degrau no tabuleiro");
						}
					}
				}
			}
			assertTrue(touchesGround, "ponte sem margem");
		}
		WorkContext ctx = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 3);
		int total = 0;
		for (int ring = 1; ring <= 6; ring++) {
			Blueprint bp = BridgeWorks.plan(ctx, ring);
			total += bp.size();
			for (WBlock b : bp.ordered()) {
				assertTrue(plan.street(b.x(), b.z()), "ponte fora da rua");
			}
		}
		assertTrue(total > 50, "tabuleiro, guarda-corpo e arco: " + total);
	}

	@Test
	void bridgeDeckRisesToTheWaterLevelWhenTheBanksAreLower() {
		MasterPlan plan = MasterPlanTestAccess.planWithRiver(3);
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			plan.setRingPrepared(ring);
		}
		com.mlin.notas.village.Terrain river = MasterPlanTestAccess.planRiverTerrain();
		List<BridgeWorks.Span> flat = BridgeWorks.spans(plan);
		List<BridgeWorks.Span> lifted = BridgeWorks.spans(plan, river);
		assertEquals(flat.size(), lifted.size());
		boolean higher = false;
		for (int s = 0; s < flat.size(); s++) {
			for (int i = 0; i < flat.get(s).cells().size(); i++) {
				assertTrue(lifted.get(s).heights()[i] >= flat.get(s).heights()[i], "o tabuleiro nunca desce");
				higher |= lifted.get(s).heights()[i] > flat.get(s).heights()[i];
			}
			for (int i = 0; i < lifted.get(s).cells().size(); i++) {
				for (int j = 0; j < lifted.get(s).cells().size(); j++) {
					int[] a = lifted.get(s).cells().get(i);
					int[] b = lifted.get(s).cells().get(j);
					if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) == 1) {
						assertTrue(Math.abs(lifted.get(s).heights()[i] - lifted.get(s).heights()[j]) <= 1, "degrau no tabuleiro levantado");
					}
				}
			}
		}
		assertTrue(higher, "agua de 6 sobre as margens: o tabuleiro sobe");
	}

	@Test
	void palisadeAndGatesFollowTheWallLineAndOpenWhereAvenuesCross() {
		for (int t = 0; t < WorksTestSupport.TERRAINS.size(); t++) {
			MasterPlan plan = WorksTestSupport.plan(t, 11 + t * 7);
			WorkContext ctx = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 5);
			List<WallWorks.Gate> gates = WallWorks.gates(ctx);
			assertTrue(gates.size() >= 4, "um portao em cada avenida que cruza a muralha: " + gates.size());
			Blueprint palisade = WallWorks.palisade(ctx);
			WorksTestSupport.assertGrounded(plan, palisade, "palicada terreno " + t);
			for (WBlock b : palisade.ordered()) {
				assertFalse(WallWorks.isGateCell(gates, b.x(), b.z()), "palicada fechando a passagem");
			}
			assertTrue(palisade.size() > 500, "palicada inteira: " + palisade.size());
			Blueprint wooden = WallWorks.woodenGates(ctx);
			WorksTestSupport.assertGrounded(plan, wooden, "portoes terreno " + t);
			assertTrue(wooden.ordered().stream().anyMatch(b -> b.state().contains("fence_gate")), "folhas de portao");
			// a passagem fica livre: nada em y+2..y+4 sobre as celulas
			for (WallWorks.Gate g : gates) {
				if (!g.axis()) {
					continue;
				}
				for (int[] c : g.cells()) {
					int y = plan.streetHeight(c[0], c[1]);
					for (int dy = 2; dy <= 3; dy++) {
						WBlock b = wooden.at(c[0], y + dy, c[1]);
						assertTrue(b == null, "passagem entupida em " + c[0] + "," + (y + dy) + "," + c[1]);
					}
				}
			}
		}
	}

	@Test
	void capitalWallWorksNeedTheStoneWallAndPortcullisOnTheAxisGates() {
		MasterPlan plan = WorksTestSupport.plan(0, 11);
		WorkContext none = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 5);
		assertTrue(WallWorks.battlements(none).isEmpty(), "sem muralha de pedra nao ha ameia");
		WorkContext wall = new WorkContext(plan, VillageStyle.PLAINS, 5, MasterPlan.RINGS, (x, z) -> false, Set.of(), List.of(), Set.of(), List.of(), "", null,
				(x, z) -> plan.plannedHeight(x, z) == Integer.MIN_VALUE ? Integer.MIN_VALUE : plan.plannedHeight(x, z) + 5);
		Blueprint bp = WallWorks.battlements(wall);
		assertTrue(bp.size() > 100, "ameias nas paredes: " + bp.size());
		List<WallWorks.Portcullis> ports = WallWorks.portcullises(wall);
		assertTrue(ports.size() >= 2, "grades de ferro nos portoes dos eixos: " + ports.size());
		for (WallWorks.Portcullis p : ports) {
			assertEquals(9, p.bars().size(), "3x3 de barras");
			assertTrue(p.leverState().contains("lever"));
		}
	}

	@Test
	void roadsStartAtAvenueEndsAndFollowTheGroundWithStepsOfOne() {
		com.mlin.notas.village.Terrain terrain = MasterPlanTestAccess.terrain(2);
		MasterPlan plan = MasterPlanTestAccess.plan(2, 11);
		for (int ring = 1; ring <= 6; ring++) {
			plan.setRingPrepared(ring);
		}
		List<RoadWorks.Exit> exits = RoadWorks.exits(plan);
		assertTrue(exits.size() >= 4, "pontas de avenida: " + exits.size());
		WorkContext ctx = new WorkContext(plan, VillageStyle.PLAINS, 5, MasterPlan.RINGS, (x, z) -> false, Set.of(), List.of(), Set.of(),
				List.of(new WorkContext.Destination(180, 10, "waystone", "Pedra Leste"), new WorkContext.Destination(-5, -400, "village", "Longe Demais")), "",
				terrain, (x, z) -> Integer.MIN_VALUE);
		List<RoadWorks.Road> roads = RoadWorks.roads(ctx);
		assertEquals(1, roads.size(), "so o destino a menos de 300 blocos e na direcao da avenida");
		RoadWorks.Road road = roads.get(0);
		assertEquals("Pedra Leste", road.destination().name());
		for (int i = 1; i < road.path().size(); i++) {
			int[] a = road.path().get(i - 1);
			int[] b = road.path().get(i);
			assertTrue(Math.abs(terrain.ground(a[0], a[1]) - terrain.ground(b[0], b[1])) <= 1, "degrau maior que 1 na estrada");
			assertTrue(Math.abs(a[0] - b[0]) <= 1 && Math.abs(a[1] - b[1]) <= 1);
		}
		Blueprint bp = RoadWorks.plan(ctx);
		assertTrue(bp.size() > road.path().size() * 2, "3 de largura: " + bp.size());
		assertTrue(bp.ordered().stream().anyMatch(b -> b.text() != null && b.text()[0].equals("Pedra Leste")), "placa de direcao");
		assertTrue(bp.ordered().stream().anyMatch(b -> b.state().contains("lantern")), "poste de lanterna");
		for (WBlock b : bp.ordered()) {
			if (b.kind() == WBlock.Kind.SURFACE) {
				assertEquals(terrain.ground(b.x(), b.z()), b.y(), "estrada na altura do chao");
				assertEquals(MasterPlan.OUT, plan.code(b.x(), b.z()), "estrada dentro do plano");
			}
		}
	}

	@Test
	void roadsGiveUpOnWater() {
		assertNull(RoadWorks.route(MasterPlanTestAccess.riverTerrain(100, 110), 80, 0, 180, 0, 4), "rio largo sem ponte: sem estrada");
	}

	@Test
	void everyLevelBringsItsWorksAndEveryRingBridgesAndStakes() {
		Set<String> all = new HashSet<>();
		for (VillageLevel level : VillageLevel.values()) {
			for (WorkSpec spec : WorkSpec.introducedAt(level)) {
				assertTrue(all.add(spec.id()), "id repetido " + spec.id());
				assertEquals(spec, WorkSpec.byId(spec.id()));
				assertEquals(level, spec.level());
			}
		}
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			assertTrue(all.contains("stakes:" + ring) && all.contains("bridges:" + ring), "anel " + ring);
		}
		for (int s = 1; s <= 5; s++) {
			assertTrue(all.contains("streets:" + s) && all.contains("plaza:" + s));
		}
		assertTrue(all.containsAll(Set.of("wall:palisade", "wall:gates", "wall:battlements", "roads:1", "rural:1", "trees:1", "gardens:1", "decor:1")));
		assertEquals(WorkSpec.introducedAt(VillageLevel.CAMP).size() + WorkSpec.introducedAt(VillageLevel.HAMLET).size(), WorkSpec.upTo(VillageLevel.HAMLET).size());
	}

	@Test
	void ownershipOnlyOwnsWhatWasRecordedAndStillTheSameBlock() {
		Ownership o = new Ownership();
		WBlock b = WBlock.surface(3, 64, -2, "minecraft:gravel");
		assertFalse(o.owns(b.key(), "minecraft:gravel"));
		o.record(b, "minecraft:gravel", "streets:1");
		assertTrue(o.owns(b.key(), "minecraft:gravel"));
		assertFalse(o.owns(b.key(), "minecraft:stone"), "o jogador trocou o bloco: nao e mais da vila");
		assertTrue(o.columns().contains(VillageLayout.col(3, -2)));
		Ownership back = Ownership.restore(o.keys(), o.blockIndexes(o.keys()), o.workIndexes(o.keys()), o.palette(), o.workIds());
		assertTrue(back.owns(b.key(), "minecraft:gravel"));
		assertEquals("streets:1", back.owner(b.key()));
		assertEquals(1, back.inRect(0, -5, 5, 0).size());
		back.forgetAt(b.key());
		assertFalse(back.recorded(b.key()));
		assertTrue(back.columns().isEmpty());
	}

	@Test
	void blueprintOrdersGroundBeforeThingsAndLaterBlocksWin() {
		Blueprint bp = new Blueprint();
		bp.above(0, 66, 0, "minecraft:oak_log").surface(0, 64, 0, "minecraft:gravel").above(0, 65, 0, "minecraft:oak_fence").above(0, 66, 0, "minecraft:lantern");
		List<WBlock> ordered = bp.ordered();
		assertEquals(3, ordered.size());
		assertEquals(WBlock.Kind.SURFACE, ordered.get(0).kind());
		assertEquals("minecraft:oak_fence", ordered.get(1).state());
		assertEquals("minecraft:lantern", ordered.get(2).state());
	}

	@Test
	void ruralWorksOnlyInTheOutskirts() {
		MasterPlan plan = WorksTestSupport.plan(0, 11);
		WorkContext ctx = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 4);
		for (WBlock b : ParcelWorks.rural(ctx).ordered()) {
			MasterPlan.Parcel p = plan.parcelAt(b.x(), b.z());
			assertTrue(p != null && p.zone() == Zone.OUTSKIRTS, "rural so nos arredores");
		}
	}
}
