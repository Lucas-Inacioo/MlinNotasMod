package com.mlin.notas.village;

import java.util.List;
import java.util.function.IntBinaryOperator;

/** Ponte publica para os testes de outros pacotes (obras publicas) usarem os terrenos e o plano dos testes do plano diretor. */
public final class MasterPlanTestAccess {
	private MasterPlanTestAccess() {
	}

	public static List<IntBinaryOperator> terrains() {
		return MasterPlanTest.TERRAINS;
	}

	public static MasterPlan plan(int terrain, long seed) {
		return MasterPlanTest.plan(new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(terrain)), seed);
	}

	/** Terreno plano com uma faixa de agua funda atravessando o plano em z = -40..-34 (as avenidas viram pontes). */
	public static MasterPlan planWithRiver(long seed) {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		for (int x = -75; x <= 75; x++) {
			for (int z = -40; z <= -34; z++) {
				t.water.put(VillagePlannerTest.FakeTerrain.col(x, z), 6);
			}
		}
		return MasterPlanTest.plan(t, seed);
	}

	/** Terreno plano com um rio de agua funda em x = x0..x1 (todo z). */
	/** O terreno do rio de {@link #planWithRiver}: agua de 6 em z = -40..-34 sobre fundo 64. */
	public static Terrain planRiverTerrain() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		for (int x = -75; x <= 75; x++) {
			for (int z = -40; z <= -34; z++) {
				t.water.put(VillagePlannerTest.FakeTerrain.col(x, z), 6);
			}
		}
		return t;
	}

	public static Terrain riverTerrain(int x0, int x1) {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain((x, z) -> 64);
		for (int x = x0; x <= x1; x++) {
			for (int z = -400; z <= 400; z++) {
				t.water.put(VillagePlannerTest.FakeTerrain.col(x, z), 3);
			}
		}
		return t;
	}

	/** Plano diretor (sem anel rural) num terreno qualquer. */
	public static MasterPlan planOn(Terrain t, long seed) {
		return MasterPlan.create(seed, t, t.ground(0, 0), java.util.List.of(), java.util.List.of(), java.util.Set.of(), 0);
	}

	/** Plano do terreno {@code terrain} ja com o anel rural (fase 8). */
	public static MasterPlan ruralPlan(int terrain, long seed) {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(terrain));
		return RuralRing.expand(MasterPlanTest.plan(t, seed), t);
	}

	public static Terrain terrain(int terrain) {
		return new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(terrain));
	}
}
