package com.mlin.notas.storage;

import java.util.function.BiConsumer;

/**
 * Algo da rede de armazenamento que guarda itens: uma Unidade de Armazenamento ({@link StorageCell}) ou um bau
 * ligado (adaptador do lado do jogo). {@code K} identifica o tipo de item (no jogo, item + componentes). Logica pura.
 */
public interface StorageTarget<K> {
	/** Maior recebe primeiro na insercao e e esvaziado por ultimo na extracao. */
	int priority();

	/** Tem pelo menos uma unidade do tipo. */
	boolean contains(K key);

	/** Guarda ate {@code amount}; devolve quanto coube. Com {@code simulate} nao muda nada. */
	long insert(K key, long amount, boolean simulate);

	/** Tira ate {@code amount}; devolve quanto saiu. Com {@code simulate} nao muda nada. */
	long extract(K key, long amount, boolean simulate);

	/** Percorre o conteudo (tipo, quantidade &gt; 0). Pode repetir o tipo (um bau com varias pilhas iguais). */
	void forEach(BiConsumer<K, Long> action);
}
