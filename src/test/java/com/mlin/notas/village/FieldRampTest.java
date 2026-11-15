package com.mlin.notas.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FieldRampTest {
	/** Plato 108 a oeste (x < 0), faixa sem altura em 0..4, plato 104 a leste (x > 4). */
	private static int plan(int x, int z) {
		return x < 0 ? 108 : x > 4 ? 104 : Integer.MIN_VALUE;
	}

	@Test
	void stripBetweenTwoPlateausBecomesAMonotoneRamp() {
		int prev = 108;
		for (int x = 0; x <= 4; x++) {
			int h = FieldRamp.height(FieldRampTest::plan, x, 0);
			assertTrue(h <= prev && h >= 104, "rampa monotona entre 108 e 104 em x=" + x + ": " + h);
			assertTrue(Math.abs(h - prev) <= 2, "degrau pequeno em x=" + x);
			prev = h;
		}
	}

	@Test
	void nextToOnePlateauKeepsItsHeight() {
		assertEquals(97, FieldRamp.height((x, z) -> x < 0 ? 97 : Integer.MIN_VALUE, 0, 0));
	}

	@Test
	void nothingAroundGivesMin() {
		assertEquals(Integer.MIN_VALUE, FieldRamp.height((x, z) -> Integer.MIN_VALUE, 0, 0));
	}
}
