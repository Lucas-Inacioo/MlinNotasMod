package com.mlin.notas.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossRulesTest {
	@Test
	void phaseTwoStartsBelowHalfHealth() {
		assertEquals(1, BossRules.phase(300, 300));
		assertEquals(1, BossRules.phase(150, 300));
		assertEquals(2, BossRules.phase(149.9, 300));
		assertEquals(2, BossRules.phase(1, 300));
	}

	@Test
	void healthIsTunedForTwoPlayersAndGrowsHalfPerExtraPlayer() {
		assertEquals(1.0, BossRules.healthMultiplier(0));
		assertEquals(1.0, BossRules.healthMultiplier(1));
		assertEquals(1.0, BossRules.healthMultiplier(2));
		assertEquals(1.5, BossRules.healthMultiplier(3));
		assertEquals(2.0, BossRules.healthMultiplier(4));
		assertEquals(330.0, BossRules.scaledHealth(220.0, 3));
	}

	@Test
	void sleepingBossWakesInsideItsRadius() {
		assertTrue(BossRules.shouldWake(15.9 * 15.9, 16));
		assertTrue(BossRules.shouldWake(256.0, 16));
		assertFalse(BossRules.shouldWake(16.1 * 16.1, 16));
		assertFalse(BossRules.shouldWake(1.0, 0), "chefe sem raio nao dorme");
	}

	@Test
	void dragonDropsTwoToFourScales() {
		assertEquals(2, BossRules.dragonScales(0));
		assertEquals(4, BossRules.dragonScales(2));
		assertEquals(4, BossRules.dragonScales(9));
		assertEquals(2, BossRules.dragonScales(-1));
	}

	@Test
	void fireWaveExpandsAndHitsOnlyItsRing() {
		assertEquals(0.0, BossRules.waveRadius(0, 20, 10.0));
		assertEquals(5.0, BossRules.waveRadius(10, 20, 10.0));
		assertEquals(10.0, BossRules.waveRadius(40, 20, 10.0));
		assertTrue(BossRules.inRing(5.4, 5.0, 1.5));
		assertFalse(BossRules.inRing(3.0, 5.0, 1.5));
	}

	@Test
	void slamPushesHardestInTheMiddle() {
		assertTrue(BossRules.slamKnockback(1.0, 6.0, 2.0) > BossRules.slamKnockback(5.0, 6.0, 2.0));
		assertEquals(0.0, BossRules.slamKnockback(6.0, 6.0, 2.0));
	}

	@Test
	void everyBossButTheColossusHasAnAltarAndADrop() {
		for (BossKind kind : BossKind.values()) {
			assertNotNull(kind.drop());
			assertTrue(kind.xp() > 0 && kind.baseHealth() > 100);
			if (kind == BossKind.IGNEOUS_COLOSSUS) {
				assertNull(kind.offering());
				assertEquals(16, kind.wakeRadius());
			} else {
				assertTrue(kind.hasAltar());
				assertEquals(4, kind.offeringCount());
				assertEquals(0, kind.wakeRadius());
			}
		}
	}
}
