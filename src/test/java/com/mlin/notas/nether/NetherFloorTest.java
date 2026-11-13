package com.mlin.notas.nether;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetherFloorTest {
	private static boolean[] range(int size, int from, int to) {
		boolean[] out = new boolean[size];
		for (int i = from; i < to; i++) {
			out[i] = true;
		}
		return out;
	}

	@Test
	void findsDryGroundAboveTheLavaSea() {
		// rocha de 0 a 59, ar de 60 a 109, teto de 110 a 127
		boolean[] solid = range(128, 0, 60);
		System.arraycopy(range(128, 110, 128), 110, solid, 110, 18);
		assertEquals(60, NetherFloor.find(solid, new boolean[128], 0, 32));
	}

	@Test
	void lavaLakeFallsBackToSeaLevel() {
		boolean[] solid = range(128, 0, 20);
		System.arraycopy(range(128, 110, 128), 110, solid, 110, 18);
		boolean[] lava = range(128, 20, 32);
		assertEquals(32, NetherFloor.find(solid, lava, 0, 32));
	}

	@Test
	void ignoresFloorsBelowTheSea() {
		// caverna embaixo do mar (y 10) e chao de verdade em 45
		boolean[] solid = range(128, 0, 45);
		solid[10] = false;
		solid[11] = false;
		assertEquals(45, NetherFloor.find(solid, new boolean[128], 0, 32));
	}

	@Test
	void averageRounds() {
		assertEquals(51, NetherFloor.average(50, 51, 52, 51, 51));
		assertEquals(61, NetherFloor.average(60, 61));
		assertThrows(IllegalArgumentException.class, NetherFloor::average);
	}

	@Test
	void shiftPutsTheBottomOnTheFloorUnlessItHitsTheRoof() {
		// caixa 33..70 (37 de altura), chao em 60, fundo 2 abaixo: desce para 58
		assertEquals(25, NetherFloor.shift(33, 70, 60, -2, Integer.MAX_VALUE));
		assertEquals(25, NetherFloor.shift(33, 70, 60, -2, 124));
		// caixa alta (33..112): o topo limitaria em 124, entao sobe so 12 e o pe fica enterrado
		assertEquals(12, NetherFloor.shift(33, 112, 60, -2, 124));
		assertEquals(-10, NetherFloor.shift(60, 80, 54, -4, Integer.MAX_VALUE));
	}
}
