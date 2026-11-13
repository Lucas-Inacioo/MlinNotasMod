package com.mlin.notas.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XpMathTest {
	@Test
	void matchesVanillaLevelCosts() {
		assertEquals(7, XpMath.pointsToNextLevel(0));
		assertEquals(37, XpMath.pointsToNextLevel(15));
		assertEquals(112, XpMath.pointsToNextLevel(30));
		// valores conhecidos da wiki: 0 -> 15 = 315, 0 -> 30 = 1395, 0 -> 40 = 2920
		assertEquals(315, XpMath.pointsForLevels(15));
		assertEquals(1395, XpMath.pointsForLevels(30));
		assertEquals(2920, XpMath.pointsForLevels(40));
	}

	@Test
	void convertsBackAndForth() {
		for (int level = 0; level <= 60; level++) {
			int points = XpMath.pointsForLevels(level);
			assertEquals(level, XpMath.levelForPoints(points));
			assertEquals(level, XpMath.levelsCovering(points));
			if (level > 0) {
				assertEquals(level - 1, XpMath.levelForPoints(points - 1));
				assertEquals(level, XpMath.levelsCovering(points - 1), "pontos que faltam arredondam para cima");
			}
		}
		assertEquals(0, XpMath.levelForPoints(-5));
	}

	@Test
	void playerPointsIncludeTheBar() {
		assertEquals(315, XpMath.totalPoints(15, 0F));
		assertEquals(315 + 18, XpMath.totalPoints(15, 0.5F), "metade dos 37 da barra");
		// pagar aos poucos custa o mesmo: 10 niveis de 30 -> 20 nao valem o mesmo que 0 -> 10
		assertEquals(true, XpMath.pointsForLevels(30) - XpMath.pointsForLevels(20) > XpMath.pointsForLevels(10));
	}
}
