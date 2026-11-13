package com.mlin.notas.ambient;

import com.mlin.notas.season.Season;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbientRulesTest {
	private static AmbientRules.Env forest(boolean night, Season season) {
		return new AmbientRules.Env(true, false, false, false, false, false, true, false, true, 70, night, false, season,
				Season.Phase.MID, 0, 0);
	}

	private static AmbientRules.Env swamp(boolean night, Season season) {
		return new AmbientRules.Env(false, true, false, false, false, false, true, false, true, 63, night, false, season,
				Season.Phase.MID, 6, 0);
	}

	private static AmbientRules.Env cave(int y) {
		return new AmbientRules.Env(false, false, false, false, false, false, false, true, false, y, false, false, Season.SUMMER,
				Season.Phase.MID, 0, 0);
	}

	@Test
	void forestHasBirdsByDayAndCricketsOnSummerNights() {
		assertTrue(AmbientRules.oneShotChance(AmbientRules.OneShot.BIRDS, forest(false, Season.SPRING)) > 0);
		assertEquals(0, AmbientRules.oneShotChance(AmbientRules.OneShot.BIRDS, forest(true, Season.SPRING)));
		assertEquals(0, AmbientRules.oneShotChance(AmbientRules.OneShot.BIRDS, forest(false, Season.WINTER)), "passaros somem no inverno");
		assertTrue(AmbientRules.loopVolume(AmbientRules.Loop.CRICKETS, forest(true, Season.SUMMER)) > 0);
		assertEquals(0, AmbientRules.loopVolume(AmbientRules.Loop.CRICKETS, forest(false, Season.SUMMER)), "grilo e de noite");
		assertEquals(0, AmbientRules.loopVolume(AmbientRules.Loop.CRICKETS, forest(true, Season.WINTER)));
	}

	@Test
	void firefliesOnlyOnWarmNightsInSwampsAndForests() {
		assertTrue(AmbientRules.particleRate(AmbientRules.Particle.FIREFLIES, swamp(true, Season.SUMMER))
				> AmbientRules.particleRate(AmbientRules.Particle.FIREFLIES, forest(true, Season.SUMMER)));
		assertTrue(AmbientRules.particleRate(AmbientRules.Particle.FIREFLIES, forest(true, Season.SUMMER)) > 0);
		assertEquals(0, AmbientRules.particleRate(AmbientRules.Particle.FIREFLIES, swamp(false, Season.SUMMER)));
		assertEquals(0, AmbientRules.particleRate(AmbientRules.Particle.FIREFLIES, swamp(true, Season.WINTER)));
	}

	@Test
	void swampFrogsAtNight() {
		assertTrue(AmbientRules.oneShotChance(AmbientRules.OneShot.FROG, swamp(true, Season.SUMMER)) > 0);
		assertEquals(0, AmbientRules.oneShotChance(AmbientRules.OneShot.FROG, forest(true, Season.SUMMER)));
		assertEquals(0, AmbientRules.oneShotChance(AmbientRules.OneShot.FROG, swamp(true, Season.WINTER)));
	}

	@Test
	void cavesHaveDripsRumbleAndDustButNoSurfaceSounds() {
		AmbientRules.Env deep = cave(-30);
		assertTrue(AmbientRules.oneShotChance(AmbientRules.OneShot.CAVE_DRIP, deep) > 0);
		assertTrue(AmbientRules.oneShotChance(AmbientRules.OneShot.CAVE_RUMBLE, deep)
				> AmbientRules.oneShotChance(AmbientRules.OneShot.CAVE_RUMBLE, cave(40)));
		assertTrue(AmbientRules.particleRate(AmbientRules.Particle.DUST, deep) > 0);
		assertEquals(0, AmbientRules.loopVolume(AmbientRules.Loop.WIND, deep));
		assertEquals(0, AmbientRules.particleRate(AmbientRules.Particle.LEAVES, deep));
		assertEquals(0, AmbientRules.oneShotChance(AmbientRules.OneShot.CAVE_DRIP, forest(false, Season.SUMMER)));
	}

	@Test
	void windGrowsWithHeightOnMountains() {
		AmbientRules.Env low = new AmbientRules.Env(false, false, false, true, false, false, false, false, true, 90, false, false,
				Season.SUMMER, Season.Phase.MID, 0, 0);
		AmbientRules.Env peak = new AmbientRules.Env(false, false, false, true, false, false, false, false, true, 190, false, false,
				Season.SUMMER, Season.Phase.MID, 0, 0);
		AmbientRules.Env plainsLow = new AmbientRules.Env(false, false, false, false, false, true, true, false, true, 70, false, false,
				Season.SUMMER, Season.Phase.MID, 0, 0);
		assertTrue(AmbientRules.loopVolume(AmbientRules.Loop.WIND, peak) > AmbientRules.loopVolume(AmbientRules.Loop.WIND, low));
		assertEquals(0, AmbientRules.loopVolume(AmbientRules.Loop.WIND, plainsLow));
		assertTrue(AmbientRules.loopVolume(AmbientRules.Loop.WIND, peak) <= 0.8F);
	}

	@Test
	void desertWindAndDustAndRiverFlow() {
		AmbientRules.Env desert = new AmbientRules.Env(false, false, true, false, false, false, false, false, true, 70, false, false,
				Season.SUMMER, Season.Phase.MID, 0, 0);
		assertTrue(AmbientRules.loopVolume(AmbientRules.Loop.DESERT_WIND, desert) > 0);
		assertTrue(AmbientRules.particleRate(AmbientRules.Particle.DUST, desert) > 0);
		AmbientRules.Env river = new AmbientRules.Env(false, false, false, false, true, false, true, false, true, 63, false, false,
				Season.SUMMER, Season.Phase.MID, 30, 0);
		AmbientRules.Env dryRiver = new AmbientRules.Env(false, false, false, false, true, false, true, false, true, 63, false, false,
				Season.SUMMER, Season.Phase.MID, 1, 0);
		assertTrue(AmbientRules.loopVolume(AmbientRules.Loop.RIVER, river) > 0);
		assertEquals(0, AmbientRules.loopVolume(AmbientRules.Loop.RIVER, dryRiver));
	}

	@Test
	void leavesPeakInAutumnAndSnowflakesOnlyInTemperateWinter() {
		float autumn = AmbientRules.particleRate(AmbientRules.Particle.LEAVES, forest(false, Season.AUTUMN));
		for (Season other : new Season[] {Season.SPRING, Season.SUMMER, Season.WINTER}) {
			assertTrue(autumn > AmbientRules.particleRate(AmbientRules.Particle.LEAVES, forest(false, other)));
		}
		assertTrue(AmbientRules.particleRate(AmbientRules.Particle.SNOWFLAKES, forest(false, Season.WINTER)) > 0);
		assertEquals(0, AmbientRules.particleRate(AmbientRules.Particle.SNOWFLAKES, forest(false, Season.AUTUMN)));
	}

	@Test
	void optionsJsonRoundTripAndClamp() {
		AmbientOptions o = new AmbientOptions();
		o.sounds = false;
		o.particleDensity = 0.5F;
		AmbientOptions back = AmbientOptions.fromJson(o.toJson());
		assertFalse(back.sounds);
		assertTrue(back.particles);
		assertEquals(0.5F, back.particleDensity);
		assertEquals(2.0F, AmbientOptions.fromJson("{\"soundVolume\": 9}").soundVolume);
		assertTrue(AmbientOptions.fromJson("lixo").particles, "JSON invalido volta ao padrao");
	}
}
