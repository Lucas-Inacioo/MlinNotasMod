package com.mlin.notas.notes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda um {@link Notebook} por jogador (UUID). Em memoria por enquanto.
 */
public class NotebookManager {
	private final Map<UUID, Notebook> notebooks = new ConcurrentHashMap<>();

	public Notebook of(UUID playerId) {
		return notebooks.computeIfAbsent(playerId, k -> new Notebook());
	}

	public void reset() {
		notebooks.clear();
	}
}
