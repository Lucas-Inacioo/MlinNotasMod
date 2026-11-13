package com.mlin.notas.sleep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepRulesTest {
	@Test
	void halfOfThePlayersRoundedUp() {
		assertEquals(1, SleepRules.sleepersNeeded(1, 50));
		assertEquals(1, SleepRules.sleepersNeeded(2, 50));
		assertEquals(2, SleepRules.sleepersNeeded(3, 50));
		assertEquals(5, SleepRules.sleepersNeeded(10, 50));
		assertEquals(10, SleepRules.sleepersNeeded(10, 100));
		assertEquals(1, SleepRules.sleepersNeeded(10, 0));
	}

	@Test
	void statusCountsMissingSleepers() {
		SleepRules.Status status = SleepRules.status(5, 1, 50);
		assertEquals(3, status.needed());
		assertEquals(2, status.missing());
		assertFalse(status.enough());

		SleepRules.Status done = SleepRules.status(4, 2, 50);
		assertTrue(done.enough());
		assertEquals(0, done.missing());
	}

	@Test
	void modDefaultOnlyReplacesVanillaDefaultOnce() {
		assertTrue(SleepRules.shouldApplyModDefault(100, false));
		assertFalse(SleepRules.shouldApplyModDefault(100, true));
		assertFalse(SleepRules.shouldApplyModDefault(30, false));
	}
}
