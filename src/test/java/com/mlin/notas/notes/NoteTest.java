package com.mlin.notas.notes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoteTest {
	@Test
	void stripsWhitespace() {
		assertEquals("ola", new Note(1, "  ola  ").text());
	}

	@Test
	void rejectsBlankText() {
		assertThrows(IllegalArgumentException.class, () -> new Note(1, "   "));
		assertThrows(IllegalArgumentException.class, () -> new Note(1, null));
	}

	@Test
	void rejectsTooLongText() {
		String longText = "x".repeat(Note.MAX_LENGTH + 1);
		assertThrows(IllegalArgumentException.class, () -> new Note(1, longText));
		assertDoesNotThrow(() -> new Note(1, "x".repeat(Note.MAX_LENGTH)));
	}
}
