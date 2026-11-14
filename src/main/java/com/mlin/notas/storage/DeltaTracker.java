package com.mlin.notas.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sincronizacao incremental do terminal (servidor -> um jogador): cada tipo ganha um numero curto na primeira vez
 * que aparece; o item completo so viaja nessa primeira vez, depois so o numero e a quantidade. Cada atualizacao
 * manda apenas o que mudou desde a anterior (quantidade 0 = sumiu). Logica pura.
 */
public final class DeltaTracker<K> {
	/** Uma mudanca: {@code key} so vem preenchida quando o cliente ainda nao conhece o numero. */
	public record Change<K>(int id, K key, long count) {
	}

	private final Map<K, Integer> ids = new HashMap<>();
	private final Map<Integer, K> byId = new HashMap<>();
	private final Set<Integer> known = new HashSet<>();
	private Map<K, Long> sent = new HashMap<>();
	private int nextId = 1;

	/** Diferencas entre o ultimo envio e {@code current}; depois disso {@code current} passa a ser o enviado. */
	public List<Change<K>> update(Map<K, Long> current) {
		List<Change<K>> changes = new ArrayList<>();
		for (Map.Entry<K, Long> entry : current.entrySet()) {
			long count = entry.getValue();
			if (count <= 0) {
				continue;
			}
			Long before = sent.get(entry.getKey());
			if (before != null && before == count) {
				continue;
			}
			int id = idOf(entry.getKey());
			boolean first = known.add(id);
			changes.add(new Change<>(id, first ? entry.getKey() : null, count));
		}
		for (K key : sent.keySet()) {
			Long now = current.get(key);
			if (now == null || now <= 0) {
				changes.add(new Change<>(ids.get(key), null, 0));
			}
		}
		Map<K, Long> next = new HashMap<>();
		current.forEach((k, c) -> {
			if (c > 0) {
				next.put(k, c);
			}
		});
		sent = next;
		return changes;
	}

	/** Esquece o que foi enviado (o proximo update manda tudo de novo, com os itens completos). */
	public void reset() {
		sent = new HashMap<>();
		known.clear();
	}

	/** Tipo de um numero que o cliente mandou de volta, ou null se nao existe. */
	public K keyOf(int id) {
		return byId.get(id);
	}

	/** Numero ja dado ao tipo, ou -1. */
	public int find(K key) {
		Integer id = ids.get(key);
		return id == null ? -1 : id;
	}

	private int idOf(K key) {
		Integer id = ids.get(key);
		if (id == null) {
			id = nextId++;
			ids.put(key, id);
			byId.put(id, key);
		}
		return id;
	}
}
