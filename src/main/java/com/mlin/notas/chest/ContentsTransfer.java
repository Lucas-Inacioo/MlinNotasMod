package com.mlin.notas.chest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Copia o conteudo de um container para outro de tamanho diferente sem perder nada.
 * Logica pura: trabalha com listas genericas; {@code isEmpty} diz quais entradas sao "vazias" e {@code empty} e o preenchimento.
 */
public final class ContentsTransfer {
	private ContentsTransfer() {
	}

	/**
	 * @param source   conteudo de origem (pode ter entradas vazias)
	 * @param newSize  tamanho do destino
	 * @param isEmpty  identifica entradas vazias
	 * @param empty    valor usado para preencher slots sobrando
	 * @return lista com exatamente {@code newSize} entradas; primeiro as mesmas posicoes que cabem,
	 *         depois as que nao cabem sao realocadas em slots vazios
	 * @throws IllegalStateException se os itens nao vazios nao couberem no destino
	 */
	public static <T> List<T> resize(List<T> source, int newSize, Predicate<T> isEmpty, T empty) {
		List<T> result = new ArrayList<>(newSize);
		for (int i = 0; i < newSize; i++) {
			result.add(i < source.size() ? source.get(i) : empty);
		}
		List<T> overflow = new ArrayList<>();
		for (int i = newSize; i < source.size(); i++) {
			if (!isEmpty.test(source.get(i))) {
				overflow.add(source.get(i));
			}
		}
		int cursor = 0;
		for (T item : overflow) {
			while (cursor < newSize && !isEmpty.test(result.get(cursor))) {
				cursor++;
			}
			if (cursor >= newSize) {
				throw new IllegalStateException("conteudo nao cabe em " + newSize + " slots");
			}
			result.set(cursor, item);
		}
		return result;
	}

	/** Quantas entradas nao vazias existem. */
	public static <T> int countNonEmpty(List<T> list, Predicate<T> isEmpty) {
		int n = 0;
		for (T t : list) {
			if (!isEmpty.test(t)) {
				n++;
			}
		}
		return n;
	}
}
