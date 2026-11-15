package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Muralhas lidas do campo de alturas (Vilas 3.0 fase 4), em terrenos sinteticos com encosta, vale e montanha: o portao
 * tem pelo menos 4 de ar livre sobre a rua e nada dentro da passagem, o topo da muralha passa 4 do maior chao dos dois
 * lados em toda a linha, o pe chega ao chao (sem bloco solto no ar) e as torres seguem o mesmo campo.
 */
class WallsFromFieldTest {
	private static final long SEED = 11;
	private static final List<String> TERRAINS = List.of("flat", "slope", "mountain_side", "river", "mountain_inside", "noisy");

	/** Mundo: chao natural do terreno sintetico; rua nao e livre (a muralha nao toca nela, so os portoes). */
	private static Terrain terrain(MasterPlan plan, HeightFieldBuilder.Natural natural) {
		return new Terrain() {
			@Override
			public int ground(int x, int z) {
				return natural.ground(x, z);
			}

			@Override
			public boolean free(int x, int z) {
				return !plan.street(x, z) && !(x == 0 && z == 0);
			}
		};
	}

	private static Map<Long, int[]> columns(List<Placement> placements) {
		// por coluna: {menor y solido, maior y solido}
		Map<Long, int[]> out = new HashMap<>();
		for (Placement p : placements) {
			if (p.part() == VPart.AIR || p.part() == VPart.DRAIN) {
				continue;
			}
			int[] c = out.computeIfAbsent(VillageLayout.col(p.x(), p.z()), k -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
			c[0] = Math.min(c[0], p.y());
			c[1] = Math.max(c[1], p.y());
		}
		return out;
	}

	private static boolean solidAt(List<Placement> placements, int x, int y, int z) {
		for (Placement p : placements) {
			if (p.x() == x && p.y() == y && p.z() == z && p.part() != VPart.AIR && p.part() != VPart.DRAIN) {
				return true;
			}
		}
		return false;
	}

	@Test
	void stoneWallGatesKeepFourBlocksOfHeadroomAndNothingInThePassage() {
		for (String name : TERRAINS) {
			HeightFieldBuilder.Result r = HeightFieldTest.cached(name, SEED);
			HeightField field = r.field();
			Terrain terrain = terrain(r.plan(), HeightFieldTest.terrains(SEED).get(name));
			StoneWallPlanner.Result wall = StoneWallPlanner.plan(field, terrain);
			Map<Long, java.util.Set<Integer>> solids = new HashMap<>();
			for (Placement p : wall.placements()) {
				if (p.part() != VPart.AIR && p.part() != VPart.DRAIN) {
					solids.computeIfAbsent(VillageLayout.col(p.x(), p.z()), k -> new java.util.HashSet<>()).add(p.y());
				}
			}
			int gates = 0;
			for (HeightField.WallPoint p : field.wallPoints(HeightField.Wall.STONE)) {
				if (!p.gate()) {
					continue;
				}
				gates++;
				int level = field.heightRel(p.x(), p.z());
				assertEquals(level, field.gateLevelRel(p.x(), p.z()));
				java.util.Set<Integer> col = solids.getOrDefault(VillageLayout.col(p.x(), p.z()), java.util.Set.of());
				for (int y = level + 1; y <= level + StoneWallPlanner.CLEARANCE; y++) {
					assertFalse(col.contains(y), name + ": bloco na passagem do portao em " + p.x() + "," + y + "," + p.z());
				}
				assertTrue(col.contains(level + StoneWallPlanner.CLEARANCE + 1), name + ": portao sem arco em " + p.x() + "," + p.z());
			}
			assertTrue(gates >= 12, name + ": celulas de portao da muralha de pedra: " + gates);
			// a rua do portao e plana: o campo garante a mesma altura de cada lado
			for (HeightField.Gate g : field.gates()) {
				if (g.wall() == HeightField.Wall.STONE) {
					for (int x = g.x0(); x <= g.x1(); x++) {
						for (int z = g.z0(); z <= g.z1(); z++) {
							assertTrue(!field.kindRel(x, z).gate() || field.heightRel(x, z) == g.level(), name + ": portao nao plano");
						}
					}
				}
			}
			assertFalse(wall.golems().isEmpty(), "golem do portao");
		}
	}

	@Test
	void stoneWallIsNeverBuriedAndTouchesTheGround() {
		for (String name : TERRAINS) {
			HeightFieldBuilder.Result r = HeightFieldTest.cached(name, SEED);
			HeightField field = r.field();
			Terrain terrain = terrain(r.plan(), HeightFieldTest.terrains(SEED).get(name));
			StoneWallPlanner.Result wall = StoneWallPlanner.plan(field, terrain);
			Map<Long, int[]> cols = columns(wall.placements());
			int checked = 0;
			for (HeightField.WallPoint p : field.wallPoints(HeightField.Wall.STONE)) {
				int[] c = cols.get(VillageLayout.col(p.x(), p.z()));
				if (c == null) {
					assertTrue(p.gate() || !terrain.free(p.x(), p.z()), name + ": buraco na muralha em " + p.x() + "," + p.z());
					continue;
				}
				if (p.gate()) {
					continue;
				}
				int low = Math.min(p.ground(), Math.min(p.inner(), p.outer()));
				int high = Math.max(p.ground(), Math.max(p.inner(), p.outer()));
				assertTrue(c[1] >= high + StoneWallPlanner.HEIGHT, name + ": muralha enterrada em " + p.x() + "," + p.z() + ": topo " + c[1] + " chao " + high);
				assertTrue(c[0] <= low, name + ": pe acima do chao em " + p.x() + "," + p.z() + ": pe " + c[0] + " chao " + low);
				// coluna inteira, sem buraco no meio
				for (int y = c[0]; y <= c[1]; y++) {
					assertTrue(solidAt(wall.placements(), p.x(), y, p.z()), name + ": bloco solto/buraco em " + p.x() + "," + y + "," + p.z());
				}
				checked++;
			}
			assertTrue(checked > 150, name + ": colunas de muralha " + checked);
			// torres: topo acima do maior chao da area
			for (int[] t : List.of(new int[] {49, 33}, new int[] {-49, -33})) {
				int[] c = cols.get(VillageLayout.col(t[0], t[1]));
				if (c != null) {
					assertTrue(c[1] >= field.heightRel(t[0], t[1]) + StoneWallPlanner.TOWER, name + ": torre baixa");
				}
			}
		}
	}

	@Test
	void outerWallFollowsTheFieldAndLeavesItsGatesOpen() {
		for (String name : TERRAINS) {
			HeightFieldBuilder.Result r = HeightFieldTest.cached(name, SEED);
			MasterPlan plan = r.plan();
			HeightField field = r.field();
			assertNotNull(plan.field());
			Terrain terrain = terrain(plan, HeightFieldTest.terrains(SEED).get(name));
			OuterWallPlanner.Result wall = OuterWallPlanner.plan(plan, terrain);
			assertTrue(wall.gates().size() >= 4, name + ": portoes " + wall.gates().size());
			Map<Long, int[]> cols = columns(wall.placements());
			for (OuterWallPlanner.Gate g : wall.gates()) {
				int level = field.heightRel(g.cells().get(0)[0], g.cells().get(0)[1]);
				assertEquals(level, g.base(), name + ": base do portao = nivel da rua");
				for (int[] c : g.cells()) {
					assertEquals(level, field.heightRel(c[0], c[1]), name + ": rua do portao plana");
					for (int y = level + 1; y <= level + 4; y++) {
						assertFalse(solidAt(wall.placements(), c[0], y, c[1]), name + ": bloco na passagem do portao externo em " + c[0] + "," + y + "," + c[1]);
					}
					assertTrue(solidAt(wall.placements(), c[0], level + 5, c[1]), name + ": arco do portao externo");
				}
			}
			// muralha nunca abaixo do chao do campo + 4 nos dois lados e com o pe no menor chao
			int checked = 0;
			for (HeightField.WallPoint p : field.wallPoints(HeightField.Wall.OUTER)) {
				int[] c = cols.get(VillageLayout.col(p.x(), p.z()));
				if (c == null || p.gate() || plan.street(p.x(), p.z())) {
					continue;
				}
				int high = Math.max(p.ground(), Math.max(p.inner(), p.outer()));
				int low = Math.min(p.ground(), Math.min(p.inner(), p.outer()));
				assertTrue(c[1] >= high + 4, name + ": muralha externa enterrada em " + p.x() + "," + p.z() + ": topo " + c[1] + " chao " + high);
				assertTrue(c[0] <= low + 1, name + ": pe da muralha externa no ar em " + p.x() + "," + p.z() + ": pe " + c[0] + " chao " + low);
				checked++;
			}
			assertTrue(checked > 150, name + ": colunas da muralha externa " + checked);
		}
	}
}
