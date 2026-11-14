package com.mlin.notas.doors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubleDoorRulesTest {
	@Test
	void pairNeedsSameFacingOppositeHingeSameState() {
		assertTrue(DoubleDoorRules.pairs("north", true, false, "north", false, false));
		assertFalse(DoubleDoorRules.pairs("north", true, false, "south", false, false));
		assertFalse(DoubleDoorRules.pairs("north", true, false, "north", true, false));
		assertFalse(DoubleDoorRules.pairs("north", true, true, "north", false, false));
	}

	@Test
	void partnerSideFollowsHinge() {
		assertTrue(DoubleDoorRules.partnerClockwise(true));
		assertFalse(DoubleDoorRules.partnerClockwise(false));
	}

	@Test
	void onlyStandingPlayersTrigger() {
		assertTrue(DoubleDoorRules.triggers(true, false, false));
		assertFalse(DoubleDoorRules.triggers(true, true, false));
		assertFalse(DoubleDoorRules.triggers(true, false, true));
		assertFalse(DoubleDoorRules.triggers(false, false, false));
	}
}
