package com.mlin.notas.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Busca e ordenacao da lista do terminal (lado do cliente). Busca por palavras separadas por espaco, todas precisam
 * casar: texto no nome ou no id; {@code @mod} filtra pelo namespace do id (como no visualizador de receitas).
 * Logica pura.
 */
public final class TerminalQuery {
	/** Ordem da lista. Cada modo tem a sua ordem natural; {@code reversed} inverte. */
	public enum SortMode {
		/** A-Z pelo nome. */
		NAME,
		/** Maior quantidade primeiro. */
		COUNT,
		/** Pelo mod (namespace), depois pelo nome. */
		MOD;

		public SortMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/** Uma linha da lista: {@code handle} e o que o chamador usa para achar o item (ex.: o numero da sincronizacao). */
	public record Entry<T>(T handle, String name, String id, long count) {
		public String namespace() {
			int colon = id.indexOf(':');
			return colon < 0 ? "minecraft" : id.substring(0, colon);
		}
	}

	private TerminalQuery() {
	}

	public static <T> List<Entry<T>> apply(Collection<Entry<T>> entries, String search, SortMode mode, boolean reversed) {
		String[] tokens = tokens(search);
		List<Entry<T>> out = new ArrayList<>();
		for (Entry<T> entry : entries) {
			if (entry.count() > 0 && matches(entry, tokens)) {
				out.add(entry);
			}
		}
		Comparator<Entry<T>> byName = Comparator.comparing((Entry<T> e) -> e.name().toLowerCase(Locale.ROOT)).thenComparing(Entry::id);
		Comparator<Entry<T>> order = switch (mode) {
			case NAME -> byName;
			case COUNT -> Comparator.comparingLong((Entry<T> e) -> e.count()).reversed().thenComparing(byName);
			case MOD -> Comparator.comparing((Entry<T> e) -> e.namespace()).thenComparing(byName);
		};
		out.sort(reversed ? order.reversed() : order);
		return out;
	}

	public static boolean matches(Entry<?> entry, String search) {
		return matches(entry, tokens(search));
	}

	private static String[] tokens(String search) {
		String trimmed = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
	}

	private static boolean matches(Entry<?> entry, String[] tokens) {
		String name = entry.name().toLowerCase(Locale.ROOT);
		String id = entry.id().toLowerCase(Locale.ROOT);
		for (String token : tokens) {
			if (token.startsWith("@")) {
				if (!entry.namespace().toLowerCase(Locale.ROOT).startsWith(token.substring(1))) {
					return false;
				}
			} else if (!name.contains(token) && !id.contains(token)) {
				return false;
			}
		}
		return true;
	}
}
