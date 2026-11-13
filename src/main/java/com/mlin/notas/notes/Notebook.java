package com.mlin.notas.notes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Caderno de anotacoes de um jogador. Logica pura (sem Minecraft).
 */
public class Notebook {
	private final List<Note> notes = new ArrayList<>();
	private int nextId = 1;

	public Note add(String text) {
		Note note = new Note(nextId++, text);
		notes.add(note);
		return note;
	}

	public boolean remove(int id) {
		return notes.removeIf(n -> n.id() == id);
	}

	public Optional<Note> find(int id) {
		return notes.stream().filter(n -> n.id() == id).findFirst();
	}

	public List<Note> all() {
		return Collections.unmodifiableList(notes);
	}

	public int size() {
		return notes.size();
	}

	public void clear() {
		notes.clear();
	}
}
