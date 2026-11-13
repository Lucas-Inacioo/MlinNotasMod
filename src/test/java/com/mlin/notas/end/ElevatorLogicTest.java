package com.mlin.notas.end;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ElevatorLogicTest {
	@Test
	void findsTheElevatorRightUnderTheFeet() {
		assertEquals(10, ElevatorLogic.elevatorBelow(10, y -> y == 10, y -> y <= 10).getAsInt());
	}

	@Test
	void stillFindsItMidJump() {
		// pulo: pes 1 ou 2 blocos acima do elevador, so ar no meio
		assertEquals(10, ElevatorLogic.elevatorBelow(11, y -> y == 10, y -> y <= 10).getAsInt());
		assertEquals(10, ElevatorLogic.elevatorBelow(12, y -> y == 10, y -> y <= 10).getAsInt());
		assertFalse(ElevatorLogic.elevatorBelow(13, y -> y == 10, y -> y <= 10).isPresent(), "alto demais");
	}

	@Test
	void solidFloorHidesAnElevatorBelowIt() {
		assertFalse(ElevatorLogic.elevatorBelow(11, y -> y == 10, y -> y <= 11).isPresent());
	}
}
