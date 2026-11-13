package com.mlin.notas.waila;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Resumo de conteudo em grade (logica pura, generica no tipo do item): junta pilhas iguais somando a quantidade
 * e distribui em linhas de 9 celulas de 18 px.
 */
public final class ItemGrid {
	public static final int COLUMNS = 9;
	public static final int CELL = 18;

	private ItemGrid() {
	}

	/** Item representante + quantidade total. */
	public record Group<T>(T sample, int count) {
	}

	/** Agrupa itens iguais (na ordem em que aparecem pela primeira vez), ignorando vazios. */
	public static <T> List<Group<T>> group(List<T> items, Predicate<T> isEmpty, BiPredicate<T, T> same, ToIntFunction<T> count) {
		List<T> samples = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		for (T item : items) {
			if (item == null || isEmpty.test(item)) {
				continue;
			}
			int index = -1;
			for (int i = 0; i < samples.size(); i++) {
				if (same.test(samples.get(i), item)) {
					index = i;
					break;
				}
			}
			if (index < 0) {
				samples.add(item);
				totals.add(count.applyAsInt(item));
			} else {
				totals.set(index, totals.get(index) + count.applyAsInt(item));
			}
		}
		List<Group<T>> groups = new ArrayList<>(samples.size());
		for (int i = 0; i < samples.size(); i++) {
			groups.add(new Group<>(samples.get(i), totals.get(i)));
		}
		return groups;
	}

	/** Celulas mostradas com no maximo {@code maxRows} linhas. */
	public static int visible(int entries, int maxRows) {
		return Math.max(0, Math.min(entries, maxRows * COLUMNS));
	}

	/** Entradas que nao couberam. */
	public static int overflow(int entries, int maxRows) {
		return entries - visible(entries, maxRows);
	}

	public static int rows(int cells) {
		return (cells + COLUMNS - 1) / COLUMNS;
	}

	public static int columns(int cells) {
		return Math.min(cells, COLUMNS);
	}

	public static int width(int cells) {
		return columns(cells) * CELL;
	}

	public static int height(int cells) {
		return rows(cells) * CELL;
	}

	public static int cellX(int index) {
		return (index % COLUMNS) * CELL;
	}

	public static int cellY(int index) {
		return (index / COLUMNS) * CELL;
	}

	/** Texto curto da quantidade: 1 -> "", 64, 999, 1200 -> "1.2k", 15000 -> "15k". */
	public static String countLabel(int count) {
		if (count <= 1) {
			return "";
		}
		if (count < 1000) {
			return Integer.toString(count);
		}
		if (count < 10000) {
			int tenths = count / 100;
			return tenths % 10 == 0 ? (tenths / 10) + "k" : (tenths / 10) + "." + (tenths % 10) + "k";
		}
		return count < 1_000_000 ? (count / 1000) + "k" : com.mlin.notas.storage.CountFormat.compact(count);
	}
}
