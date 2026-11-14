package com.mlin.notas.village.works;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registro dos blocos que as obras publicas colocaram (logica pura; posicoes relativas ao quadro, chave de
 * {@link WBlock#key()}): bloco colocado, obra que colocou. Uma obra so troca ou tira o que esta registrado e ainda e o
 * mesmo bloco, ou o chao natural; nunca bloco de jogador. Guarda tambem as colunas ocupadas, que o plano diretor usa
 * para nao confundir a decoracao da vila com construcao de jogador.
 */
public final class Ownership {
	private final Map<Long, int[]> blocks = new HashMap<>();
	private final List<String> palette = new ArrayList<>();
	private final List<String> works = new ArrayList<>();
	private final Map<Long, Integer> columnCount = new HashMap<>();
	private final Set<Long> columns = new HashSet<>();

	public int size() {
		return blocks.size();
	}

	/** Colunas (chave {@code VillageLayout.col}) com bloco da vila; o conjunto e vivo (reflete as mudancas). */
	public Set<Long> columns() {
		return java.util.Collections.unmodifiableSet(columns);
	}

	private int index(List<String> list, String value) {
		int i = list.indexOf(value);
		if (i < 0) {
			list.add(value);
			i = list.size() - 1;
		}
		return i;
	}

	/** Anota que a obra {@code work} colocou o bloco {@code blockId} (so o id, sem propriedades) na posicao. */
	public void record(WBlock b, String blockId, String work) {
		long key = b.key();
		boolean fresh = !blocks.containsKey(key);
		blocks.put(key, new int[] {index(palette, blockId), index(works, work)});
		if (fresh) {
			columnCount.merge(b.col(), 1, Integer::sum);
			columns.add(b.col());
		}
	}

	/** O bloco na posicao e da vila: anotado e o id atual ainda e o anotado. */
	public boolean owns(long key, String currentBlockId) {
		int[] v = blocks.get(key);
		return v != null && palette.get(v[0]).equals(currentBlockId);
	}

	public boolean recorded(long key) {
		return blocks.containsKey(key);
	}

	public String owner(long key) {
		int[] v = blocks.get(key);
		return v == null ? null : works.get(v[1]);
	}

	public void forget(WBlock b) {
		forgetKey(b.key(), b.col());
	}

	private void forgetKey(long key, long col) {
		if (blocks.remove(key) != null && columnCount.merge(col, -1, Integer::sum) <= 0) {
			columnCount.remove(col);
			columns.remove(col);
		}
	}

	/** Posicoes anotadas (x, y, z) dentro do retangulo (relativo), com o bloco anotado. */
	public List<long[]> inRect(int x0, int z0, int x1, int z1) {
		List<long[]> out = new ArrayList<>();
		for (Map.Entry<Long, int[]> e : blocks.entrySet()) {
			long k = e.getKey();
			int x = (int) (k >> 40) - 2048;
			int z = (int) (k & 0xFFFFF) - 2048;
			if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
				out.add(new long[] {k, e.getValue()[0]});
			}
		}
		return out;
	}

	/** Posicoes anotadas por obras cujo id comeca com {@code prefix}. */
	public List<long[]> ofWork(String prefix) {
		List<long[]> out = new ArrayList<>();
		for (Map.Entry<Long, int[]> e : blocks.entrySet()) {
			if (works.get(e.getValue()[1]).startsWith(prefix)) {
				out.add(new long[] {e.getKey(), e.getValue()[0]});
			}
		}
		return out;
	}

	public void forgetAt(long key) {
		int x = (int) (key >> 40) - 2048;
		int z = (int) (key & 0xFFFFF) - 2048;
		forgetKey(key, ((long) x << 32) ^ (z & 0xFFFFFFFFL));
	}

	public String blockId(int paletteIndex) {
		return palette.get(paletteIndex);
	}

	// ---------------------------------------------------------------- formato salvo

	public long[] keys() {
		return blocks.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
	}

	public int[] blockIndexes(long[] keys) {
		int[] out = new int[keys.length];
		for (int i = 0; i < keys.length; i++) {
			out[i] = blocks.get(keys[i])[0];
		}
		return out;
	}

	public int[] workIndexes(long[] keys) {
		int[] out = new int[keys.length];
		for (int i = 0; i < keys.length; i++) {
			out[i] = blocks.get(keys[i])[1];
		}
		return out;
	}

	public List<String> palette() {
		return palette;
	}

	public List<String> workIds() {
		return works;
	}

	public static Ownership restore(long[] keys, int[] blockIdx, int[] workIdx, List<String> palette, List<String> works) {
		Ownership o = new Ownership();
		o.palette.addAll(palette);
		o.works.addAll(works);
		for (int i = 0; i < keys.length && i < blockIdx.length && i < workIdx.length; i++) {
			long k = keys[i];
			o.blocks.put(k, new int[] {blockIdx[i], workIdx[i]});
			int x = (int) (k >> 40) - 2048;
			int z = (int) (k & 0xFFFFF) - 2048;
			long col = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
			o.columnCount.merge(col, 1, Integer::sum);
			o.columns.add(col);
		}
		return o;
	}
}
