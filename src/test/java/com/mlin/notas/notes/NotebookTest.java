package com.mlin.notas.notes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotebookTest {
	private Notebook notebook;

	@BeforeEach
	void setUp() {
		notebook = new Notebook();
	}

	@Test
	void addAssignsSequentialIds() {
		Note a = notebook.add("primeira");
		Note b = notebook.add("segunda");
		assertEquals(1, a.id());
		assertEquals(2, b.id());
		assertEquals(2, notebook.size());
	}

	@Test
	void removeDeletesOnlyMatchingNote() {
		notebook.add("a");
		Note b = notebook.add("b");
		assertTrue(notebook.remove(b.id()));
		assertFalse(notebook.remove(b.id()));
		assertEquals(1, notebook.size());
		assertTrue(notebook.find(b.id()).isEmpty());
	}

	@Test
	void idsAreNotReusedAfterRemove() {
		Note a = notebook.add("a");
		notebook.remove(a.id());
		Note b = notebook.add("b");
		assertEquals(2, b.id());
	}

	@Test
	void allIsUnmodifiable() {
		notebook.add("a");
		assertThrows(UnsupportedOperationException.class, () -> notebook.all().clear());
	}
}
