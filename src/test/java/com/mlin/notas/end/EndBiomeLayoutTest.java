package com.mlin.notas.end;

import com.mlin.notas.end.EndBiomeLayout.Biome;
import com.mlin.notas.end.EndBiomeLayout.Family;
import com.mlin.notas.end.EndBiomeLayout.Height;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndBiomeLayoutTest {
	private static final int SIZE = 20000;
	private static final int STEP = 25;

	@Test
	void regionsAreLarge() {
		for (long seed : new long[] {0L, 42L, -99L}) {
			EndBiomeLayout layout = new EndBiomeLayout(seed);
			Map<Long, Integer> area = new HashMap<>();
			// so regioes longe da borda da amostra (area completa)
			for (int x = 0; x < SIZE; x += STEP) {
				for (int z = 0; z < SIZE; z += STEP) {
					area.merge(layout.regionAt(x, z), 1, Integer::sum);
				}
			}
			double mean = area.values().stream().mapToInt(Integer::intValue).average().orElseThrow() * STEP * STEP;
			double size = Math.sqrt(mean);
			assertTrue(size >= 250 && size <= 600, "tamanho medio da regiao " + size + " (seed " + seed + ")");
		}
	}

	@Test
	void familyRunsAlongALineAreLong() {
		EndBiomeLayout layout = new EndBiomeLayout(7L);
		int runs = 1;
		Family last = layout.familyAt(0, 1234);
		for (int x = 0; x < SIZE; x += 4) {
			Family f = layout.familyAt(x, 1234);
			if (f != last) {
				runs++;
				last = f;
			}
		}
		double meanRun = (double) SIZE / runs;
		assertTrue(meanRun >= 200, "trechos de mesma familia deveriam ter centenas de blocos, media " + meanRun);
	}

	@Test
	void everyFamilyAppears() {
		for (long seed : new long[] {1L, 2L, 3L, 123456789L}) {
			EndBiomeLayout layout = new EndBiomeLayout(seed);
			Set<Family> seen = EnumSet.noneOf(Family.class);
			for (int x = -SIZE / 2; x < SIZE / 2; x += 200) {
				for (int z = -SIZE / 2; z < SIZE / 2; z += 200) {
					seen.add(layout.familyAt(x, z));
				}
			}
			assertEquals(EnumSet.allOf(Family.class), seen, "seed " + seed);
		}
	}

	@Test
	void deterministicPerSeed() {
		EndBiomeLayout a = new EndBiomeLayout(555L);
		EndBiomeLayout b = new EndBiomeLayout(555L);
		EndBiomeLayout c = new EndBiomeLayout(556L);
		int different = 0;
		for (int x = -5000; x < 5000; x += 97) {
			for (int z = -5000; z < 5000; z += 89) {
				assertEquals(a.regionAt(x, z), b.regionAt(x, z));
				assertEquals(a.familyAt(x, z), b.familyAt(x, z));
				if (a.familyAt(x, z) != c.familyAt(x, z)) {
					different++;
				}
			}
		}
		assertNotEquals(0, different, "seeds diferentes deveriam dar layouts diferentes");
	}

	@Test
	void bordersAreWarped() {
		// com o ruido, a fronteira entre duas regioes nao e uma reta: ao longo de varias linhas paralelas
		// o ponto de troca de regiao muda de lugar de forma irregular
		EndBiomeLayout layout = new EndBiomeLayout(9L);
		Set<Integer> offsets = new java.util.HashSet<>();
		for (int z = 0; z < 400; z += 20) {
			long first = layout.regionAt(0, z);
			int x = 0;
			while (layout.regionAt(x, z) == first && x < 2000) {
				x++;
			}
			offsets.add(x);
		}
		assertTrue(offsets.size() > 5, "fronteira deveria ondular, trocas em " + offsets);
	}

	@Test
	void heightPicksCenterOrEdgeVariant() {
		assertEquals(Biome.MOSS_FIELDS, EndBiomeLayout.biome(Family.MOSS_FIELDS, Height.HIGHLANDS));
		assertEquals(Biome.MOSS_FIELDS, EndBiomeLayout.biome(Family.MOSS_FIELDS, Height.MIDLANDS));
		assertEquals(Biome.VOID_WASTES, EndBiomeLayout.biome(Family.CRYSTAL_GROVE, Height.BARRENS));
		assertEquals(Biome.VOID_WASTES, EndBiomeLayout.biome(Family.CHORUS_FOREST, Height.BARRENS));
		assertEquals(Biome.CHORUS_FOREST, EndBiomeLayout.biome(Family.CHORUS_FOREST, Height.HIGHLANDS));
		assertEquals(Biome.END_HIGHLANDS, EndBiomeLayout.biome(Family.HIGHLANDS, Height.HIGHLANDS));
		assertEquals(Biome.END_MIDLANDS, EndBiomeLayout.biome(Family.HIGHLANDS, Height.MIDLANDS));
		assertEquals(Biome.END_BARRENS, EndBiomeLayout.biome(Family.HIGHLANDS, Height.BARRENS));
	}

	@Test
	void onlyOuterIslands() {
		assertFalse(EndBiomeLayout.applies(0, 0));
		assertFalse(EndBiomeLayout.applies(700, 700));
		assertTrue(EndBiomeLayout.applies(1500, 0));
		assertTrue(EndBiomeLayout.applies(-800, -800));
	}
}
