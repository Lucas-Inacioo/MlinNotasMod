package com.mlin.notas.village.works;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Lista de blocos de uma obra publica, sem repeticao de posicao (vale o ultimo) e na ordem de construcao. */
public final class Blueprint {
	private final Map<Long, WBlock> blocks = new LinkedHashMap<>();
	/** Aneis de terraplenagem que a obra toca (so roda com todos prontos). */
	private final TreeSet<Integer> rings = new TreeSet<>();

	public Blueprint add(WBlock block) {
		blocks.remove(block.key());
		blocks.put(block.key(), block);
		return this;
	}

	public Blueprint surface(int x, int y, int z, String state) {
		return add(WBlock.surface(x, y, z, state));
	}

	public Blueprint above(int x, int y, int z, String state) {
		return add(WBlock.above(x, y, z, state));
	}

	public Blueprint needRing(int ring) {
		if (ring > 0) {
			rings.add(ring);
		}
		return this;
	}

	public boolean has(int x, int y, int z) {
		return blocks.containsKey(WBlock.above(x, y, z, "").key());
	}

	public WBlock at(int x, int y, int z) {
		return blocks.get(WBlock.above(x, y, z, "").key());
	}

	public int size() {
		return blocks.size();
	}

	public boolean isEmpty() {
		return blocks.isEmpty();
	}

	public TreeSet<Integer> rings() {
		return rings;
	}

	/** Blocos na ordem de construcao (estavel). */
	public List<WBlock> ordered() {
		List<WBlock> out = new ArrayList<>(blocks.values());
		out.sort(Comparator.comparingInt(WBlock::order));
		return out;
	}
}
