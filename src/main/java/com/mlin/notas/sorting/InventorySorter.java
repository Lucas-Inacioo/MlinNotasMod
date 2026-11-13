package com.mlin.notas.sorting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordena e funde pilhas de uma faixa de slots. Logica pura: recebe a lista de slots (null = vazio)
 * e devolve a nova lista do mesmo tamanho.
 *
 * Ordem: por id (namespace:path), depois variante, pilhas cheias primeiro.
 */
public final class InventorySorter {
	private InventorySorter() {
	}

	public static List<SlotItem> sort(List<SlotItem> slots) {
		Map<String, Merged> merged = new LinkedHashMap<>();
		for (SlotItem item : slots) {
			if (item == null) {
				continue;
			}
			String key = item.id() + "|" + item.variant();
			merged.computeIfAbsent(key, k -> new Merged(item)).total += item.count();
		}

		List<SlotItem> stacks = new ArrayList<>();
		for (Merged m : merged.values()) {
			int remaining = m.total;
			while (remaining > 0) {
				int take = Math.min(remaining, m.template.max());
				stacks.add(m.template.withCount(take));
				remaining -= take;
			}
		}
		stacks.sort(Comparator.comparing(SlotItem::id)
				.thenComparing(SlotItem::variant)
				.thenComparing(Comparator.comparingInt(SlotItem::count).reversed()));

		if (stacks.size() > slots.size()) {
			// Fundir nunca aumenta o numero de pilhas; so acontece se max for inconsistente entre slots.
			throw new IllegalStateException("ordenacao gerou mais pilhas do que slots");
		}
		List<SlotItem> result = new ArrayList<>(slots.size());
		result.addAll(stacks);
		while (result.size() < slots.size()) {
			result.add(null);
		}
		return result;
	}

	/** True se a ordenacao nao alteraria nada (evita reenviar o inventario a toa). */
	public static boolean isSorted(List<SlotItem> slots) {
		return slots.equals(sort(slots));
	}

	private static final class Merged {
		final SlotItem template;
		int total;

		Merged(SlotItem template) {
			this.template = template;
		}
	}
}
