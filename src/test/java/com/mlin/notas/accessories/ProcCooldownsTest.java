package com.mlin.notas.accessories;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcCooldownsTest {
	@Test
	void atMostOncePerSecond() {
		ProcCooldowns procs = new ProcCooldowns();
		assertTrue(procs.tryProc("thorns", 100));
		assertFalse(procs.tryProc("thorns", 100));
		assertFalse(procs.tryProc("thorns", 119));
		assertTrue(procs.tryProc("thorns", 120));
	}

	@Test
	void shorterIntervalsAreRaisedToTheMinimum() {
		ProcCooldowns procs = new ProcCooldowns();
		assertTrue(procs.tryProc("x", 0, 1));
		assertFalse(procs.tryProc("x", 10, 1));
		assertEquals(10, procs.remaining("x", 10, 1));
		assertTrue(procs.tryProc("x", 20, 1));
	}

	@Test
	void keysAreIndependentAndLongIntervalsWork() {
		ProcCooldowns procs = new ProcCooldowns();
		assertTrue(procs.tryProc("lantern", 0, 1200));
		assertTrue(procs.tryProc("frost", 5));
		assertFalse(procs.tryProc("lantern", 600, 1200));
		assertTrue(procs.tryProc("lantern", 1200, 1200));
	}

	@Test
	void clockGoingBackwardsDoesNotLockForever() {
		ProcCooldowns procs = new ProcCooldowns();
		assertTrue(procs.tryProc("x", 5000));
		assertTrue(procs.tryProc("x", 10));
	}

	@Test
	void pocketWatchReadsTheClock() {
		assertEquals("06:00", PocketWatch.time(0));
		assertEquals("12:00", PocketWatch.time(6000));
		assertEquals("00:00", PocketWatch.time(18000));
		assertEquals("18:30", PocketWatch.time(12500));
		assertEquals(1, PocketWatch.day(0));
		assertEquals(3, PocketWatch.day(48000));
	}
}
