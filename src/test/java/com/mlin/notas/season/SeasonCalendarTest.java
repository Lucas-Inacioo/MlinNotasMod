package com.mlin.notas.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonCalendarTest {
	@Test
	void worldStartsInEarlySpring() {
		SeasonDate d = SeasonCalendar.date(0, 8);
		assertEquals(Season.SPRING, d.season());
		assertEquals(Season.Phase.EARLY, d.phase());
		assertEquals(1, d.displayDay());
	}

	@Test
	void eightDaySeasonsSplitInThirds() {
		Season.Phase[] expected = {Season.Phase.EARLY, Season.Phase.EARLY, Season.Phase.EARLY, Season.Phase.MID, Season.Phase.MID,
				Season.Phase.MID, Season.Phase.LATE, Season.Phase.LATE};
		for (int day = 0; day < 8; day++) {
			assertEquals(expected[day], SeasonCalendar.date(day, 8).phase(), "dia " + day);
		}
	}

	@Test
	void seasonsAdvanceAndYearWraps() {
		assertEquals(Season.SUMMER, SeasonCalendar.date(8, 8).season());
		assertEquals(Season.AUTUMN, SeasonCalendar.date(16, 8).season());
		assertEquals(Season.WINTER, SeasonCalendar.date(31, 8).season());
		assertEquals(Season.SPRING, SeasonCalendar.date(32, 8).season());
		assertEquals(Season.WINTER, SeasonCalendar.date(-1, 8).season(), "dia negativo volta para o fim do ano");
	}

	@Test
	void clockDayUsesFullDays() {
		assertEquals(0, SeasonCalendar.clockDay(23999));
		assertEquals(1, SeasonCalendar.clockDay(24000));
		assertEquals(-1, SeasonCalendar.clockDay(-1));
	}

	@Test
	void offsetLandsOnRequestedSeasonAndPhase() {
		for (int length : new int[] {1, 2, 3, 8, 30}) {
			for (Season season : Season.values()) {
				for (Season.Phase phase : Season.Phase.values()) {
					long clockDay = 1234;
					long offset = SeasonCalendar.offsetFor(clockDay, season, phase, length);
					SeasonDate d = SeasonCalendar.date(clockDay + offset, length);
					assertEquals(season, d.season(), "duracao " + length);
					if (length >= 3) {
						assertEquals(phase, d.phase(), "duracao " + length + " " + season);
					}
				}
			}
		}
	}

	@Test
	void changingLengthKeepsSeasonAndFraction() {
		long clockDay = 77;
		long offset = SeasonCalendar.offsetFor(clockDay, Season.AUTUMN, Season.Phase.LATE, 8);
		long newOffset = SeasonCalendar.offsetKeepingDate(clockDay, offset, 8, 16);
		SeasonDate d = SeasonCalendar.date(clockDay + newOffset, 16);
		assertEquals(Season.AUTUMN, d.season());
		assertEquals(Season.Phase.LATE, d.phase());
	}
}
