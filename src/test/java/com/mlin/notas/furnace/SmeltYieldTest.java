package com.mlin.notas.furnace;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmeltYieldTest {
	@Test
	void enditeAndAboveDoubleOres() {
		for (Tier tier : Tier.values()) {
			int expected = tier.ordinal() >= Tier.ENDITE.ordinal() ? 2 : 1;
			assertEquals(expected, SmeltYield.multiplier(tier, true), tier.id());
		}
	}

	@Test
	void nonOreInputIsNeverDoubled() {
		assertEquals(1, SmeltYield.multiplier(Tier.CELESTIUM, false));
		assertEquals(1, SmeltYield.multiplier(null, true));
	}

	@Test
	void resultCountRespectsStackLimit() {
		assertEquals(2, SmeltYield.resultCount(1, Tier.ENDITE, true, 64));
		assertEquals(1, SmeltYield.resultCount(1, Tier.ASTRALITE, true, 1));
		assertEquals(1, SmeltYield.resultCount(1, Tier.DIAMOND, true, 64));
	}
}
