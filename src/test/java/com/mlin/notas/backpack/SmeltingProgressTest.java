package com.mlin.notas.backpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmeltingProgressTest {
	@Test
	void lightsFuelAndFinishesAfterCookTime() {
		SmeltingProgress state = SmeltingProgress.IDLE;
		SmeltingProgress.Step step = state.tick(true, true, 1600, 200);
		assertTrue(step.consumeFuel(), "primeiro tick consome combustivel");
		assertTrue(step.next().isLit());
		assertEquals(1, step.next().cookProgress());
		state = step.next();
		int finishes = 0;
		for (int i = 0; i < 199; i++) {
			step = state.tick(true, true, 1600, 200);
			assertFalse(step.consumeFuel(), "combustivel ja aceso nao consome de novo");
			if (step.finished()) {
				finishes++;
			}
			state = step.next();
		}
		assertEquals(1, finishes, "termina exatamente uma vez em 200 ticks");
		assertEquals(0, state.cookProgress());
		assertEquals(1600 - 199, state.burnRemaining());
	}

	@Test
	void idleWithoutInputDoesNotConsumeFuel() {
		SmeltingProgress.Step step = SmeltingProgress.IDLE.tick(false, true, 1600, 200);
		assertFalse(step.consumeFuel());
		assertFalse(step.next().isLit());
	}

	@Test
	void noFuelMeansNoProgress() {
		SmeltingProgress.Step step = SmeltingProgress.IDLE.tick(true, true, 0, 200);
		assertFalse(step.consumeFuel());
		assertEquals(0, step.next().cookProgress());
	}

	@Test
	void progressDecaysWhenInputRemoved() {
		SmeltingProgress state = new SmeltingProgress(100, 1600, 50);
		SmeltingProgress.Step step = state.tick(false, true, 0, 200);
		assertEquals(48, step.next().cookProgress());
		assertEquals(99, step.next().burnRemaining());
	}

	@Test
	void fullOutputPausesCooking() {
		SmeltingProgress state = new SmeltingProgress(100, 1600, 50);
		SmeltingProgress.Step step = state.tick(true, false, 1600, 200);
		assertFalse(step.finished());
		assertEquals(48, step.next().cookProgress());
	}
}
