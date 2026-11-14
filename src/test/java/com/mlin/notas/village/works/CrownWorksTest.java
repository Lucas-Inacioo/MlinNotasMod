package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.MasterPlanTestAccess;
import com.mlin.notas.village.OuterWallPlanner;
import com.mlin.notas.village.Placement;
import com.mlin.notas.village.RuralRing;
import com.mlin.notas.village.RuralRules;
import com.mlin.notas.village.StreetPlanner;
import com.mlin.notas.village.Terrain;
import com.mlin.notas.village.VPart;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanner;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase 8 das Vilas 2.0: muralha externa (portoes nas avenidas, torres, vaos onde ha construcao de jogador), barbacas,
 * cerca viva e postes do anel rural, passeio da muralha de pedra e as regras de protecao dos campos.
 */
class CrownWorksTest {
	static MasterPlan plan(int terrain, long seed) {
		MasterPlan plan = MasterPlanTestAccess.ruralPlan(terrain, seed);
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			plan.setRingPrepared(ring);
		}
		return plan;
	}

	static WorkContext ctx(MasterPlan plan, Terrain terrain, java.util.function.BiFunction<Integer, Integer, Integer> top) {
		return new WorkContext(plan, VillageStyle.PLAINS, 99, 6, (x, z) -> false, Set.of(), List.of(), Set.of(), List.of(), "", terrain, top);
	}

	/** Blocos ligados ao chao: cada um em cima do chao da coluna ou encostado (6 lados) num bloco ligado. */
	static void assertConnected(Map<Long, int[]> solids, Set<Long> seeds, String what) {
		Set<Long> grounded = new HashSet<>(seeds);
		ArrayDeque<Long> queue = new ArrayDeque<>(seeds);
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			int[] b = solids.get(queue.poll());
			for (int[] d : dirs) {
				long k = Placement.key(b[0] + d[0], b[1] + d[1], b[2] + d[2]);
				if (solids.containsKey(k) && grounded.add(k)) {
					queue.add(k);
				}
			}
		}
		for (Map.Entry<Long, int[]> e : solids.entrySet()) {
			int[] b = e.getValue();
			if (!grounded.contains(e.getKey())) {
				StringBuilder sb = new StringBuilder();
				for (int[] o : solids.values()) {
					if (Math.abs(o[0] - b[0]) <= 1 && Math.abs(o[2] - b[2]) <= 1) {
						sb.append(" ").append(o[0]).append(",").append(o[1]).append(",").append(o[2]).append(grounded.contains(Placement.key(o[0], o[1], o[2])) ? "g" : "");
					}
				}
				System.out.println("SOLTO " + sb);
			}
			assertTrue(grounded.contains(e.getKey()), what + ": bloco solto em " + b[0] + "," + b[1] + "," + b[2]);
		}
	}

	@Test
	void outerWallHasGatesOnEveryAvenueTowersAndNothingFloats() {
		for (int t = 0; t < MasterPlanTestAccess.terrains().size(); t++) {
			Terrain terrain = MasterPlanTestAccess.terrain(t);
			MasterPlan plan = plan(t, 11 + t);
			OuterWallPlanner.Result r = OuterWallPlanner.plan(plan, terrain);
			assertTrue(r.gates().size() >= 4, "portoes: " + r.gates().size());
			assertTrue(r.gates().stream().filter(OuterWallPlanner.Gate::axis).count() >= 4, "portoes dos eixos");
			assertTrue(r.breaches().isEmpty(), "vao sem motivo: " + r.breaches().size());
			assertEquals(2, Math.min(2, r.golems().size()));
			Map<Long, int[]> solids = new HashMap<>();
			Set<Long> seeds = new HashSet<>();
			int towerTops = 0;
			for (Placement p : r.placements()) {
				assertTrue(RuralRing.inBand(p.x(), p.z()), "muralha fora da faixa em " + p.x() + "," + p.z());
				if (p.part() == VPart.AIR) {
					continue;
				}
				// passagem dos portoes: nada solido abaixo do arco
				if (plan.street(p.x(), p.z()) && p.part() != VPart.LANTERN_HANGING) {
					OuterWallPlanner.Gate g = r.gates().stream().filter(x -> x.cells().stream().anyMatch(c -> c[0] == p.x() && c[1] == p.z())).findFirst().orElseThrow();
					assertTrue(p.y() > g.base() + 4, "bloco na passagem do portao em " + p.x() + "," + p.y() + "," + p.z());
				}
				solids.put(p.key(), new int[] {p.x(), p.y(), p.z()});
				if (p.part() == VPart.FOUNDATION || p.y() == terrain.ground(p.x(), p.z()) + 1) {
					seeds.add(p.key());
				}
				if (p.part() == VPart.LANTERN && p.y() > terrain.ground(p.x(), p.z()) + OuterWallPlanner.TOWER) {
					towerTops++;
				}
			}
			assertConnected(solids, seeds, "muralha externa");
			assertTrue(towerTops >= 12, "torres com lanterna no topo: " + towerTops);
			// a muralha fecha a linha toda fora dos portoes
			int e = RuralRing.WALL + 1;
			for (int x = -e; x <= e; x++) {
				for (int z = -e; z <= e; z++) {
					if (OuterWallPlanner.layer(x, z) == 1 && !plan.street(x, z)) {
						int fx = x;
						int fz = z;
						assertTrue(r.placements().stream().anyMatch(p -> p.x() == fx && p.z() == fz && p.part() != VPart.AIR), "buraco na muralha em " + x + "," + z);
					}
				}
			}
		}
	}

	@Test
	void outerWallLeavesAWarnedGapAtPlayerBuildingsAndDeepWater() {
		com.mlin.notas.village.VillagePlannerTestAccess.Fake t = com.mlin.notas.village.VillagePlannerTestAccess.flat();
		for (int z = 20; z <= 26; z++) {
			for (int x = 73; x <= 79; x++) {
				t.build(x, z);
			}
		}
		for (int x = -10; x <= 10; x++) {
			for (int z = -80; z <= -70; z++) {
				if (Math.abs(x) > 3) {
					t.water(x, z, 9);
				}
			}
		}
		MasterPlan plan = RuralRing.expand(MasterPlanTestAccess.planOn(t, 11), t);
		OuterWallPlanner.Result r = OuterWallPlanner.plan(plan, t);
		assertFalse(r.breaches().isEmpty());
		assertTrue(r.breaches().stream().anyMatch(b -> b[0] == 75 && b[1] == 23 && b[2] == 0), "vao na casa do jogador");
		assertTrue(r.breaches().stream().anyMatch(b -> b[2] == 1), "vao na agua funda");
		for (Placement p : r.placements()) {
			assertFalse(p.x() >= 73 && p.x() <= 79 && p.z() >= 20 && p.z() <= 26, "muralha em cima da casa do jogador");
		}
		assertEquals(r.breaches().size(), OuterWallPlanner.breaches(plan, t).size());
	}

	@Test
	void hedgeAndLampsOfTheFields() {
		for (int t = 0; t < MasterPlanTestAccess.terrains().size(); t++) {
			Terrain terrain = MasterPlanTestAccess.terrain(t);
			MasterPlan plan = plan(t, 5 + t);
			WorkContext ctx = ctx(plan, terrain, (x, z) -> Integer.MIN_VALUE);
			Blueprint hedge = CrownWorks.hedge(ctx);
			assertTrue(hedge.size() > 400, "cerca viva curta: " + hedge.size());
			int posts = 0;
			for (WBlock b : hedge.ordered()) {
				assertTrue(RuralRing.hedgeLine(b.x(), b.z()), "cerca viva fora da borda");
				assertFalse(plan.street(b.x(), b.z()), "cerca viva fecha a avenida");
				int g = terrain.ground(b.x(), b.z());
				assertTrue(b.y() > g && b.y() <= g + 4, "cerca viva solta");
				for (int y = g + 1; y < b.y(); y++) {
					assertTrue(hedge.has(b.x(), y, b.z()), "cerca viva solta em " + b.x() + "," + b.y() + "," + b.z());
				}
				if (b.state().contains("lantern")) {
					posts++;
				}
			}
			assertTrue(posts >= 8, "porteiras: " + posts);
			Blueprint lamps = CrownWorks.lamps(ctx);
			WorksTestSupport.assertGrounded(plan, lamps, "postes do anel rural");
			List<int[]> spots = CrownWorks.lampSpots(plan);
			assertTrue(spots.size() >= 40, "postes nos campos: " + spots.size());
			for (int i = 0; i < spots.size(); i++) {
				assertTrue(RuralRing.inFields(spots.get(i)[0], spots.get(i)[1]));
				assertTrue(plan.parcelAt(spots.get(i)[0], spots.get(i)[1]) == null, "poste em lote");
				for (int j = i + 1; j < spots.size(); j++) {
					int dx = spots.get(i)[0] - spots.get(j)[0];
					int dz = spots.get(i)[1] - spots.get(j)[1];
					assertTrue(dx * dx + dz * dz >= CrownWorks.LAMP_SPACING * CrownWorks.LAMP_SPACING);
				}
			}
		}
	}

	@Test
	void barbicansStandOutsideTheAxisGatesAndPromenadeSitsOnTheWall() {
		Terrain terrain = MasterPlanTestAccess.terrain(0);
		MasterPlan plan = plan(0, 4321);
		WorkContext ctx = ctx(plan, terrain, (x, z) -> VillagePlanner.wallCell(x, z) ? 70 : Integer.MIN_VALUE);
		Blueprint barbican = CrownWorks.barbican(ctx);
		assertTrue(barbican.size() > 200, "barbacas: " + barbican.size());
		Map<Long, int[]> solids = new HashMap<>();
		Set<Long> seeds = new HashSet<>();
		for (WBlock b : barbican.ordered()) {
			assertTrue(RuralRing.inBand(b.x(), b.z()), "barbaca fora da faixa da muralha");
			assertFalse(plan.street(b.x(), b.z()) && b.y() <= terrain.ground(b.x(), b.z()) + 4 && !b.state().contains("lantern"), "barbaca fecha a avenida");
			solids.put(Placement.key(b.x(), b.y(), b.z()), new int[] {b.x(), b.y(), b.z()});
			if (b.y() == terrain.ground(b.x(), b.z()) + 1) {
				seeds.add(Placement.key(b.x(), b.y(), b.z()));
			}
		}
		assertConnected(solids, seeds, "barbaca");
		Blueprint promenade = CrownWorks.promenade(ctx);
		assertTrue(promenade.size() > 40);
		for (WBlock b : promenade.ordered()) {
			assertTrue(VillagePlanner.wallCell(b.x(), b.z()));
			assertTrue(b.y() == 71 || b.y() == 72 && b.state().contains("lantern"), "passeio fora do topo da muralha");
		}
	}

	@Test
	void fieldsAreProtectedOnceTheRingIsPrepared() {
		// monstro natural: bloqueado so no anel rural aplainado; dentro da muralha vale a regra antiga
		assertTrue(RuralRules.blocksSpawn(true, true, 95, 0));
		assertTrue(RuralRules.blocksSpawn(true, true, 0, -108));
		assertTrue(RuralRules.blocksSpawn(true, true, 77, 3), "faixa da muralha");
		assertFalse(RuralRules.blocksSpawn(false, true, 95, 0), "anel rural ainda sem terraplenagem");
		assertFalse(RuralRules.blocksSpawn(true, false, 95, 0), "gerador, ovo, comando");
		assertFalse(RuralRules.blocksSpawn(true, true, 40, 0), "dentro da muralha");
		assertFalse(RuralRules.blocksSpawn(true, true, 120, 0), "fora da cerca viva");
		// patrulha ate a cerca viva, em passos curtos
		assertEquals(VillageLevel.CROWN_CITY.radius(), RuralRules.patrolRadius(VillageLevel.CROWN_CITY.radius(), false));
		int r = RuralRules.patrolRadius(VillageLevel.CROWN_CITY.radius(), true);
		assertTrue(r >= RuralRing.HALF - 6 && r <= RuralRing.HALF, "raio da patrulha " + r);
		int[] step = RuralRules.patrolStep(0, 0, 100, 0);
		assertEquals(RuralRules.PATROL_STEP, step[0]);
		assertEquals(0, step[1]);
		int[] near = RuralRules.patrolStep(0, 0, 10, 5);
		assertEquals(10, near[0]);
		// torre de vigia mostra os campos
		assertTrue(RuralRules.watched(100, 0, 76, true));
		assertFalse(RuralRules.watched(100, 0, 76, false));
		assertTrue(RuralRules.evacuate(true, false, 95, 0));
		assertTrue(RuralRules.evacuate(false, true, 95, 0));
		assertFalse(RuralRules.evacuate(false, false, 95, 0));
		assertFalse(RuralRules.evacuate(true, true, 30, 0), "quem esta dentro fica");
		// abrigos: um por portao, por dentro da muralha externa, na avenida
		MasterPlan plan = plan(0, 77);
		List<int[]> shelters = RuralRules.shelters(plan);
		assertTrue(shelters.size() >= 4, "abrigos: " + shelters.size());
		for (int[] s : shelters) {
			assertFalse(RuralRing.outsideOld(s[0], s[1]));
			assertEquals(StreetPlanner.AVENUE, plan.streetKind(s[0], s[1]));
		}
		int[] best = RuralRules.nearestShelter(shelters, 0, -100);
		assertTrue(best[1] < -60 && Math.abs(best[0]) < 10, "abrigo do norte");
	}

	@Test
	void levelsMetropolisAndCrownAreReachable() {
		for (VillageLevel level : List.of(VillageLevel.METROPOLIS, VillageLevel.CROWN_CITY)) {
			assertEquals(level.projects(), level.projectsNeeded(), level + ": pontos de obra da tabela");
			for (String id : level.requiredIds()) {
				String kind = id.contains(":") ? id.substring(0, id.indexOf(':')) : id;
				assertTrue(VillageProject.byId(kind) != null, level + " exige " + id + ", que nao existe");
			}
		}
		assertTrue(VillageLevel.CROWN_CITY.required().contains(VillageProject.OUTER_WALL));
		assertEquals(VillageLevel.METROPOLIS, VillageProject.OUTER_WALL.minLevel());
		// tabela do plano
		assertEquals(1800, VillageLevel.METROPOLIS.prosperity());
		assertEquals(38, VillageLevel.METROPOLIS.projects());
		assertEquals(64, VillageLevel.METROPOLIS.radius());
		assertEquals(2, VillageLevel.METROPOLIS.golems());
		assertEquals(2800, VillageLevel.CROWN_CITY.prosperity());
		assertEquals(52, VillageLevel.CROWN_CITY.projects());
		assertEquals(76, VillageLevel.CROWN_CITY.radius());
		assertEquals(4, VillageLevel.CROWN_CITY.discount());
		assertEquals(3, VillageLevel.CROWN_CITY.golems());
	}
}
