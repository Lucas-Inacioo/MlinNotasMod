package com.mlin.notas.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visao unica de tudo que guarda itens numa rede: soma o conteudo e decide onde guardar e de onde tirar.
 *
 * <p>Insercao: primeiro onde o tipo ja esta (como gavetas, sem espalhar), depois em qualquer lugar; em cada passada
 * a ordem e da maior prioridade para a menor (unidades antes de baus). Extracao: so de quem tem o tipo, da menor
 * prioridade para a maior (esvazia os baus antes das unidades). Empate mantem a ordem em que a rede foi achada.
 * Logica pura.</p>
 */
public final class NetworkStorage<K> {
	private final List<StorageTarget<K>> targets;

	public NetworkStorage(List<? extends StorageTarget<K>> targets) {
		List<StorageTarget<K>> sorted = new ArrayList<>(targets);
		sorted.sort(Comparator.comparingInt((StorageTarget<K> t) -> t.priority()).reversed());
		this.targets = List.copyOf(sorted);
	}

	/** Alvos na ordem de insercao (prioridade decrescente). */
	public List<StorageTarget<K>> targets() {
		return targets;
	}

	/** Guarda ate {@code amount}; devolve quanto coube. */
	public long insert(K key, long amount, boolean simulate) {
		if (amount <= 0) {
			return 0;
		}
		long left = amount;
		for (StorageTarget<K> target : targets) {
			if (left <= 0) {
				break;
			}
			if (target.contains(key)) {
				left -= target.insert(key, left, simulate);
			}
		}
		for (StorageTarget<K> target : targets) {
			if (left <= 0) {
				break;
			}
			left -= target.insert(key, left, simulate);
		}
		return amount - left;
	}

	/** Tira ate {@code amount}; devolve quanto saiu. */
	public long extract(K key, long amount, boolean simulate) {
		if (amount <= 0) {
			return 0;
		}
		long left = amount;
		for (int i = targets.size() - 1; i >= 0 && left > 0; i--) {
			StorageTarget<K> target = targets.get(i);
			if (target.contains(key)) {
				left -= target.extract(key, left, simulate);
			}
		}
		return amount - left;
	}

	/** Quanto ha do tipo na rede inteira. */
	public long count(K key) {
		long[] total = {0};
		for (StorageTarget<K> target : targets) {
			if (target.contains(key)) {
				target.forEach((k, c) -> {
					if (k.equals(key)) {
						total[0] += c;
					}
				});
			}
		}
		return total[0];
	}

	/** Soma de tudo por tipo (ordem: primeiro alvo em que o tipo aparece). */
	public Map<K, Long> totals() {
		Map<K, Long> totals = new LinkedHashMap<>();
		for (StorageTarget<K> target : targets) {
			target.forEach((k, c) -> {
				if (c > 0) {
					totals.merge(k, c, Long::sum);
				}
			});
		}
		return totals;
	}
}
