package com.mlin.notas.village;

/** Ponte publica para os testes de outros pacotes montarem um terreno falso com construcoes de jogador e agua. */
public final class VillagePlannerTestAccess {
	private VillagePlannerTestAccess() {
	}

	/** Terreno falso editavel (o {@code FakeTerrain} dos testes do plano). */
	public static final class Fake implements Terrain {
		final VillagePlannerTest.FakeTerrain t;

		Fake(VillagePlannerTest.FakeTerrain t) {
			this.t = t;
		}

		/** Coluna com construcao de jogador. */
		public void build(int x, int z) {
			t.built.add(VillagePlannerTest.FakeTerrain.col(x, z));
		}

		/** Coluna com {@code depth} blocos de agua em cima do fundo. */
		public void water(int x, int z, int depth) {
			t.water.put(VillagePlannerTest.FakeTerrain.col(x, z), depth);
		}

		@Override
		public int ground(int x, int z) {
			return t.ground(x, z);
		}

		@Override
		public boolean free(int x, int z) {
			return t.free(x, z);
		}

		@Override
		public int water(int x, int z) {
			return t.water(x, z);
		}

		@Override
		public boolean playerBuilt(int x, int z) {
			return t.playerBuilt(x, z);
		}
	}

	/** Terreno plano na altura 64. */
	public static Fake flat() {
		return new Fake(new VillagePlannerTest.FakeTerrain((x, z) -> 64));
	}
}
