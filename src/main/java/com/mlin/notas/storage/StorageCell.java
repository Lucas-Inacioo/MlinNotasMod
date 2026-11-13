package com.mlin.notas.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Conteudo de uma Unidade de Armazenamento: ate {@code maxTypes} tipos, cada um com ate {@code perType} unidades,
 * sem limite de pilha. Mantem a ordem de chegada dos tipos. Logica pura.
 */
public final class StorageCell<K> implements StorageTarget<K> {
	private final int maxTypes;
	private final long perType;
	private final int priority;
	private final Predicate<K> accepts;
	private final Map<K, Long> counts = new LinkedHashMap<>();
	private Runnable onChange = () -> {
	};

	public StorageCell(int maxTypes, long perType, int priority, Predicate<K> accepts) {
		if (maxTypes <= 0 || perType <= 0) {
			throw new IllegalArgumentException("capacidade invalida");
		}
		this.maxTypes = maxTypes;
		this.perType = perType;
		this.priority = priority;
		this.accepts = accepts;
	}

	public StorageCell(StorageTier tier, Predicate<K> accepts) {
		this(tier.types(), tier.perType(), tier.priority(), accepts);
	}

	/** Chamado depois de toda mudanca real (para marcar o block entity como sujo). */
	public void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	public int maxTypes() {
		return maxTypes;
	}

	public long perType() {
		return perType;
	}

	@Override
	public int priority() {
		return priority;
	}

	public boolean accepts(K key) {
		return accepts.test(key);
	}

	@Override
	public boolean contains(K key) {
		return counts.containsKey(key);
	}

	public long count(K key) {
		return counts.getOrDefault(key, 0L);
	}

	public int typeCount() {
		return counts.size();
	}

	public long totalCount() {
		long total = 0;
		for (long c : counts.values()) {
			total += c;
		}
		return total;
	}

	public boolean isEmpty() {
		return counts.isEmpty();
	}

	/** Fracao ocupada (0..1) da capacidade total. */
	public double fill() {
		return (double) totalCount() / ((double) maxTypes * perType);
	}

	/** Conteudo somente leitura, na ordem de chegada. */
	public Map<K, Long> contents() {
		return Collections.unmodifiableMap(counts);
	}

	@Override
	public long insert(K key, long amount, boolean simulate) {
		if (amount <= 0 || !accepts.test(key)) {
			return 0;
		}
		long current = counts.getOrDefault(key, 0L);
		if (current == 0 && counts.size() >= maxTypes) {
			return 0;
		}
		long moved = Math.min(amount, perType - current);
		if (moved <= 0) {
			return 0;
		}
		if (!simulate) {
			counts.put(key, current + moved);
			onChange.run();
		}
		return moved;
	}

	@Override
	public long extract(K key, long amount, boolean simulate) {
		long current = counts.getOrDefault(key, 0L);
		long moved = Math.min(Math.max(amount, 0), current);
		if (moved <= 0) {
			return 0;
		}
		if (!simulate) {
			if (moved == current) {
				counts.remove(key);
			} else {
				counts.put(key, current - moved);
			}
			onChange.run();
		}
		return moved;
	}

	@Override
	public void forEach(BiConsumer<K, Long> action) {
		counts.forEach(action);
	}

	/** Troca todo o conteudo (carregar do disco ou do item). Tipos acima do limite e excessos sao cortados. */
	public void load(Map<K, Long> contents) {
		counts.clear();
		for (Map.Entry<K, Long> entry : contents.entrySet()) {
			if (counts.size() >= maxTypes) {
				break;
			}
			long count = Math.min(entry.getValue(), perType);
			if (count > 0) {
				counts.merge(entry.getKey(), count, (a, b) -> Math.min(perType, a + b));
			}
		}
	}

	public void clear() {
		if (!counts.isEmpty()) {
			counts.clear();
			onChange.run();
		}
	}
}
