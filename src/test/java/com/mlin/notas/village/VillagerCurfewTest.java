package com.mlin.notas.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VillagerCurfewTest {
	private static final int HALF = 74;
	private static final long DAY = 4000;
	private static final long NIGHT = 14000;

	@Test
	void nightStartsAtDusk() {
		assertFalse(VillagerCurfew.night(10999));
		assertTrue(VillagerCurfew.night(11000));
		assertTrue(VillagerCurfew.night(23999));
		assertFalse(VillagerCurfew.night(24000 + 100));
	}

	@Test
	void dayInsideDoesNothing() {
		assertEquals(VillagerCurfew.Action.NONE, VillagerCurfew.decide(DAY, 30, -20, HALF, null, null).action());
		assertEquals(VillagerCurfew.Action.NONE, VillagerCurfew.decide(DAY, HALF + 10, 0, HALF, null, null).action());
	}

	@Test
	void dayFarOutsideReturnsInsideTheArea() {
		VillagerCurfew.Decision d = VillagerCurfew.decide(DAY, HALF + 60, 10, HALF, null, null);
		assertEquals(VillagerCurfew.Action.RETURN, d.action());
		assertEquals(HALF - VillagerCurfew.INSET, d.tx());
		assertEquals(10, d.tz());
		assertEquals(VillagerCurfew.Action.RETURN, VillagerCurfew.decide(DAY, -(HALF + 17), -(HALF + 30), HALF, null, null).action());
		assertEquals(VillagerCurfew.Action.NONE, VillagerCurfew.decide(DAY, HALF + 16, 0, HALF, null, null).action());
	}

	@Test
	void nightFarFromBedGoesToBed() {
		VillagerCurfew.Decision d = VillagerCurfew.decide(NIGHT, 0, 0, HALF, new int[] {40, 0}, null);
		assertEquals(VillagerCurfew.Action.BED, d.action());
		assertEquals(40, d.tx());
		assertEquals(VillagerCurfew.Action.NONE, VillagerCurfew.decide(NIGHT, 20, 0, HALF, new int[] {40, 0}, null).action());
	}

	@Test
	void bedOutsideTheAreaDoesNotCount() {
		// sem cama valida e fora da area: abrigo; dentro da area: nada
		assertEquals(VillagerCurfew.Action.SHELTER, VillagerCurfew.decide(NIGHT, HALF + 5, 0, HALF, new int[] {HALF + 30, 0}, new int[] {60, 0, 70}).action());
		assertEquals(VillagerCurfew.Action.NONE, VillagerCurfew.decide(NIGHT, 5, 0, HALF, new int[] {HALF + 30, 0}, new int[] {60, 0, 70}).action());
	}

	@Test
	void nightOutsideWithoutShelterReturnsInside() {
		VillagerCurfew.Decision d = VillagerCurfew.decide(NIGHT, 0, HALF + 3, HALF, null, null);
		assertEquals(VillagerCurfew.Action.RETURN, d.action());
		assertEquals(HALF - VillagerCurfew.INSET, d.tz());
	}

	@Test
	void homeValidOnlyInsideTheArea() {
		assertTrue(VillagerCurfew.homeValid(HALF, -HALF, HALF));
		assertFalse(VillagerCurfew.homeValid(HALF + 1, 0, HALF));
	}
}
