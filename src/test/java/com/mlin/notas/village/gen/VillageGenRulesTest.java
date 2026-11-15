package com.mlin.notas.village.gen;

import com.mlin.notas.village.HeightField.Kind;
import com.mlin.notas.village.gen.VillageGenRules.FeatureClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageGenRulesTest {
	private static final int H = 80;
	private static final int D = 8;

	/** Topo do chao que a densidade da numa coluna (o bloco mais alto com densidade > 0). */
	private static int top(float[] natural, int minY, int naturalSurface, float alpha) {
		int top = Integer.MIN_VALUE;
		for (int i = 0; i < natural.length; i++) {
			if (VillageGenRules.density(minY + i, H, D, naturalSurface, natural[i], alpha) > 0) {
				top = minY + i;
			}
		}
		return top;
	}

	/** Coluna natural: chao em {@code n}, densidade crescendo para baixo, com uma caverna em [caveLo, caveHi]. */
	private static float[] column(int minY, int maxY, int n, int caveLo, int caveHi) {
		float[] out = new float[maxY - minY + 1];
		for (int y = minY; y <= maxY; y++) {
			float d = (n + 0.5F - y) * 0.02F;
			if (y >= caveLo && y <= caveHi) {
				d = -0.3F;
			}
			out[y - minY] = d;
		}
		return out;
	}

	@Test
	void insideThePlanTheTopIsExactlyTheField() {
		// vale aterrado (natural 60) e morro cortado (natural 110)
		assertEquals(H, top(column(0, 200, 60, -1, -1), 0, 60, 1));
		assertEquals(H, top(column(0, 200, 110, -1, -1), 0, 110, 1));
		assertEquals(H, top(column(0, 200, H, -1, -1), 0, H, 1));
	}

	@Test
	void targetIsSolidAtTheFieldAndAirJustAbove() {
		assertTrue(VillageGenRules.target(H, H, D, H, -1) > 0);
		assertTrue(VillageGenRules.target(H + 1, H, D, H, 1) < 0);
		assertEquals(-1.0F, VillageGenRules.target(H + 200, H, D, H, 1));
		assertEquals(1.0F, VillageGenRules.target(H - 100, H, D, 0, 1), 1e-6);
	}

	@Test
	void closedLayerHasNoCaveButDeepCavesStay() {
		// caverna natural de 70 a 78 (dentro da camada fechada 73..80) e de 40 a 50 (funda)
		float[] natural = column(0, 200, H, 70, 78);
		for (int y = H - D + 1; y <= H; y++) {
			assertTrue(VillageGenRules.density(y, H, D, H, natural[y], 1) > 0, "camada fechada aberta em " + y);
		}
		float[] deep = column(0, 200, H, 40, 50);
		for (int y = 40; y <= 50; y++) {
			assertTrue(VillageGenRules.density(y, H, D, H, deep[y], 1) < 0, "caverna funda fechada em " + y);
		}
	}

	@Test
	void filledValleyIsNeverHollow() {
		// natural em 50: o ar entre 51 e o fundo da camada fechada vira chao; abaixo de 50 o natural (com caverna) fica
		float[] natural = column(0, 200, 50, 30, 35);
		for (int y = 51; y <= H; y++) {
			assertTrue(VillageGenRules.density(y, H, D, 50, natural[y], 1) > 0, "aterro oco em " + y);
		}
		assertTrue(VillageGenRules.density(32, H, D, 50, natural[32], 1) < 0);
	}

	@Test
	void alphaFollowsWeightAndKeepIsNatural() {
		assertEquals(1.0F, VillageGenRules.alpha(1.0F, Kind.BLOCK));
		assertEquals(1.0F, VillageGenRules.alpha(VillageGenRules.ALPHA_BAND, Kind.BAND), 1e-6);
		assertEquals(0.5F, VillageGenRules.alpha(VillageGenRules.ALPHA_BAND / 2, Kind.BAND), 1e-6);
		assertEquals(0.0F, VillageGenRules.alpha(0, Kind.BAND));
		assertEquals(0.0F, VillageGenRules.alpha(1, Kind.KEEP));
		// sem peso, a densidade e a natural exata
		assertEquals(0.123F, VillageGenRules.density(90, H, D, H, 0.123F, 0));
	}

	@Test
	void weightBlendMovesTheTopMonotonicallyFromNaturalToField() {
		float[] natural = column(0, 200, 110, -1, -1);
		int previous = 110;
		for (int i = 0; i <= 10; i++) {
			int t = top(natural, 0, 110, i / 10.0F);
			assertTrue(t <= previous, "topo subiu com mais peso: " + t + " > " + previous);
			assertTrue(t >= H && t <= 110);
			previous = t;
		}
		assertEquals(H, previous);
		assertEquals(110, top(natural, 0, 110, 0));
	}

	@Test
	void surfaceFollowsTheField() {
		assertEquals(H, VillageGenRules.surface(120.0F, H, 1), 1e-6);
		assertEquals(100.0F, VillageGenRules.surface(120.0F, H, 0.5F), 1e-6);
		assertEquals(120.0F, VillageGenRules.surface(120.0F, H, 0), 1e-6);
	}

	@Test
	void carversStayBelowTheClosedLayer() {
		assertFalse(VillageGenRules.allowsCarve(H, H, D, 1));
		assertFalse(VillageGenRules.allowsCarve(H - D + 1, H, D, 1));
		assertTrue(VillageGenRules.allowsCarve(H - D, H, D, 1));
		assertTrue(VillageGenRules.allowsCarve(H, H, 0, 1));
		assertTrue(VillageGenRules.allowsCarve(H, H, D, 0));
	}

	@Test
	void decorationRules() {
		// rua/praca/portao: nada perto da superficie
		for (Kind k : new Kind[] {Kind.STREET, Kind.PLAZA, Kind.GATE_STONE, Kind.GATE_OUTER, Kind.PATH}) {
			assertFalse(VillageGenRules.allowsFeature(k, FeatureClass.OTHER, H + 1, H, D));
			assertFalse(VillageGenRules.allowsFeature(k, FeatureClass.GROUND_COVER, H + 1, H, D));
			assertTrue(VillageGenRules.allowsFeature(k, FeatureClass.ORE, H - 2, H, D));
			assertTrue(VillageGenRules.allowsFeature(k, FeatureClass.CHUNK_WIDE, H + 1, H, D));
		}
		// quarteirao: grama e flor sim, arvore nao
		assertTrue(VillageGenRules.allowsFeature(Kind.BLOCK, FeatureClass.GROUND_COVER, H + 1, H, D));
		assertFalse(VillageGenRules.allowsFeature(Kind.BLOCK, FeatureClass.OTHER, H + 1, H, D));
		// faixa: arvores sim, lago/fonte nao
		assertTrue(VillageGenRules.allowsFeature(Kind.BAND, FeatureClass.OTHER, H + 1, H, D));
		assertFalse(VillageGenRules.allowsFeature(Kind.BAND, FeatureClass.TERRAIN, H - 3, H, D));
		// bem fundo: tudo
		assertTrue(VillageGenRules.allowsFeature(Kind.STREET, FeatureClass.TERRAIN, H - D - VillageGenRules.FEATURE_MARGIN - 1, H, D));
		// fora: tudo
		assertTrue(VillageGenRules.allowsFeature(Kind.NATURAL, FeatureClass.OTHER, H + 1, H, D));
	}

	@Test
	void bandTreesThinOutTowardsThePlan() {
		int kept = 0;
		int keptOuter = 0;
		for (int i = 0; i < 100; i++) {
			float roll = i / 100.0F;
			if (VillageGenRules.allowsFeature(Kind.BAND, FeatureClass.OTHER, H + 1, H, D, 1.0F, roll)) {
				kept++;
			}
			if (VillageGenRules.allowsFeature(Kind.BAND, FeatureClass.OTHER, H + 1, H, D, 0.1F, roll)) {
				keptOuter++;
			}
			// plantas soltas nao rareiam
			assertTrue(VillageGenRules.allowsFeature(Kind.BAND, FeatureClass.GROUND_COVER, H + 1, H, D, 1.0F, roll));
		}
		assertEquals(15, kept);
		assertTrue(keptOuter > 85);
	}

	@Test
	void structureRules() {
		assertFalse(VillageGenRules.allowsStructure(false, 10, 72));
		assertTrue(VillageGenRules.allowsStructure(true, 40, 72));
		assertFalse(VillageGenRules.allowsStructure(true, 72, 72));
	}
}
