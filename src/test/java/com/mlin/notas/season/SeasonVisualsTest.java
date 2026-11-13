package com.mlin.notas.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonVisualsTest {
	/** Folhagem e grama vanilla das planicies. */
	private static final int PLAINS_FOLIAGE = 0x77AB2F;
	private static final int PLAINS_GRASS = 0x91BD59;
	private static final float PLAINS_TEMPERATURE = 0.8F;
	private static final float SNOW_BELOW = 0.15F;

	private static SeasonDate date(Season season, Season.Phase phase) {
		return new SeasonDate(season, phase, 0, 8);
	}

	private static int r(int c) {
		return (c >> 16) & 0xFF;
	}

	private static int g(int c) {
		return (c >> 8) & 0xFF;
	}

	private static int b(int c) {
		return c & 0xFF;
	}

	@Test
	void summerKeepsVanillaColors() {
		assertEquals(PLAINS_FOLIAGE, SeasonVisuals.foliageColor(PLAINS_FOLIAGE, date(Season.SUMMER, Season.Phase.MID)));
		assertEquals(PLAINS_GRASS, SeasonVisuals.grassColor(PLAINS_GRASS, date(Season.SUMMER, Season.Phase.EARLY)));
	}

	@Test
	void autumnFoliageTurnsOrange() {
		int c = SeasonVisuals.foliageColor(PLAINS_FOLIAGE, date(Season.AUTUMN, Season.Phase.MID));
		assertTrue(r(c) > g(c) && g(c) > b(c), String.format("laranja esperado, veio %06X", c));
	}

	@Test
	void winterIsGreyerThanSummerAndSpringIsGreener() {
		int winter = SeasonVisuals.grassColor(PLAINS_GRASS, date(Season.WINTER, Season.Phase.MID));
		assertTrue(saturation(winter) < saturation(PLAINS_GRASS), "inverno menos saturado");
		int spring = SeasonVisuals.grassColor(PLAINS_GRASS, date(Season.SPRING, Season.Phase.MID));
		assertTrue(g(spring) - r(spring) > g(PLAINS_GRASS) - r(PLAINS_GRASS), "primavera mais verde");
	}

	@Test
	void autumnCanopiesVaryByPlaceOnlyInAutumn() {
		SeasonDate autumn = date(Season.AUTUMN, Season.Phase.MID);
		int base = SeasonVisuals.foliageColor(PLAINS_FOLIAGE, autumn);
		boolean yellower = false;
		boolean redder = false;
		for (int x = 0; x < 400; x += 5) {
			int c = SeasonVisuals.foliageVariation(base, x, x * 0.7, autumn);
			yellower |= g(c) > g(base) + 10;
			redder |= g(c) < g(base) - 10 && r(c) >= r(base) - 10;
		}
		assertTrue(yellower && redder, "deveria haver copas mais amarelas e mais vermelhas");
		assertEquals(base, SeasonVisuals.foliageVariation(base, 17, 3, date(Season.SUMMER, Season.Phase.MID)));
		double n = SeasonVisuals.valueNoise(3.3, -7.9);
		assertTrue(n >= 0 && n <= 1);
		assertEquals(n, SeasonVisuals.valueNoise(3.3, -7.9), "deterministico");
	}

	@Test
	void blendKeepsAlpha() {
		assertEquals(0xFF000000, SeasonVisuals.blend(0xFF102030, 0x405060, 0.5F) & 0xFF000000);
		assertEquals(0xFF102030, SeasonVisuals.blend(0xFF102030, 0x405060, 0));
	}

	@Test
	void plainsSnowOnlyInWinterAndNeverWarmer() {
		for (Season season : Season.values()) {
			for (Season.Phase phase : Season.Phase.values()) {
				float offset = SeasonVisuals.temperatureOffset(date(season, phase));
				assertTrue(offset <= 0, "nunca esquenta (neve natural das montanhas nao derrete)");
				boolean snows = PLAINS_TEMPERATURE + offset < SNOW_BELOW;
				boolean expected = season == Season.WINTER && phase != Season.Phase.EARLY;
				assertEquals(expected, snows, season + " " + phase);
			}
		}
		assertTrue(0.7F + SeasonVisuals.temperatureOffset(date(Season.WINTER, Season.Phase.EARLY)) < SNOW_BELOW,
				"floresta (0,7) ja neva no comeco do inverno");
	}

	private static float saturation(int c) {
		int max = Math.max(r(c), Math.max(g(c), b(c)));
		int min = Math.min(r(c), Math.min(g(c), b(c)));
		return max == 0 ? 0 : (max - min) / (float) max;
	}
}
