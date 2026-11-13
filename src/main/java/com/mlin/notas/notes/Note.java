package com.mlin.notas.notes;

/**
 * Uma anotacao imutavel. Logica pura, sem dependencia do Minecraft, para ser testavel por JUnit.
 */
public record Note(int id, String text) {
	public static final int MAX_LENGTH = 256;

	public Note {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Texto da nota nao pode ser vazio");
		}
		if (text.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("Texto da nota excede " + MAX_LENGTH + " caracteres");
		}
		text = text.strip();
	}
}
