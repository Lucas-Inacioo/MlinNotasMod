package com.mlin.notas.grave;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lapides existentes no mundo, por posicao (logica pura; persistida por {@code GraveData}). */
public final class GraveIndex {
	private final Map<String, GraveInfo> graves = new LinkedHashMap<>();

	public GraveIndex() {
	}

	public GraveIndex(Collection<GraveInfo> initial) {
		for (GraveInfo info : initial) {
			graves.put(info.key(), info);
		}
	}

	/** Todas, na ordem em que foram criadas. */
	public List<GraveInfo> all() {
		return List.copyOf(graves.values());
	}

	public Optional<GraveInfo> get(String dimension, int x, int y, int z) {
		return Optional.ofNullable(graves.get(GraveInfo.key(dimension, x, y, z)));
	}

	/** Adiciona ou substitui a lapide da mesma posicao. */
	public void put(GraveInfo info) {
		graves.put(info.key(), info);
	}

	/** Devolve true se havia lapide nessa posicao. */
	public boolean remove(String dimension, int x, int y, int z) {
		return graves.remove(GraveInfo.key(dimension, x, y, z)) != null;
	}

	/** Lapides de um dono, da mais nova para a mais velha. */
	public List<GraveInfo> ofOwner(String owner) {
		List<GraveInfo> out = new ArrayList<>();
		for (GraveInfo info : graves.values()) {
			if (info.owner().equals(owner)) {
				out.add(0, info);
			}
		}
		return out;
	}

	public int size() {
		return graves.size();
	}
}
