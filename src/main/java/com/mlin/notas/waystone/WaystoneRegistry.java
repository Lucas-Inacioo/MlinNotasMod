package com.mlin.notas.waystone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pedras de teleporte ativadas no mundo (compartilhadas por todos os jogadores) e a ultima pedra usada por cada
 * jogador (destino do Pergaminho de Retorno). Logica pura; {@code WaystoneData} guarda isto no mundo.
 */
public final class WaystoneRegistry {
	private final Map<String, WaystoneInfo> waystones = new LinkedHashMap<>();
	private final Map<String, String> lastUsed = new LinkedHashMap<>();

	public WaystoneRegistry() {
	}

	public WaystoneRegistry(Collection<WaystoneInfo> waystones, Map<String, String> lastUsed) {
		for (WaystoneInfo info : waystones) {
			this.waystones.put(info.key(), info);
		}
		this.lastUsed.putAll(lastUsed);
	}

	/** Ativa a pedra; devolve false se ela ja estava ativada (o nome antigo fica). */
	public boolean activate(WaystoneInfo info) {
		return waystones.putIfAbsent(info.key(), info) == null;
	}

	public boolean isActive(String dimension, int x, int y, int z) {
		return waystones.containsKey(WaystoneInfo.key(dimension, x, y, z));
	}

	public Optional<WaystoneInfo> get(String dimension, int x, int y, int z) {
		return Optional.ofNullable(waystones.get(WaystoneInfo.key(dimension, x, y, z)));
	}

	public Optional<WaystoneInfo> byKey(String key) {
		return Optional.ofNullable(waystones.get(key));
	}

	/** Renomeia; false se a pedra nao esta ativada ou o nome e vazio. */
	public boolean rename(String dimension, int x, int y, int z, String name) {
		String key = WaystoneInfo.key(dimension, x, y, z);
		WaystoneInfo info = waystones.get(key);
		String clean = WaystoneInfo.cleanName(name);
		if (info == null || clean == null) {
			return false;
		}
		waystones.put(key, info.withName(clean));
		return true;
	}

	/** Remove a pedra (quebrada); quem a tinha como ultima usada perde o retorno. */
	public boolean remove(String dimension, int x, int y, int z) {
		String key = WaystoneInfo.key(dimension, x, y, z);
		if (waystones.remove(key) == null) {
			return false;
		}
		lastUsed.values().removeIf(key::equals);
		return true;
	}

	public List<WaystoneInfo> all() {
		return List.copyOf(waystones.values());
	}

	public int size() {
		return waystones.size();
	}

	/**
	 * Destinos a partir de {@code source}: as outras pedras, primeiro as da mesma dimensao (mais perto primeiro), depois
	 * as de outras dimensoes por nome.
	 */
	public static List<WaystoneInfo> destinations(Collection<WaystoneInfo> all, WaystoneInfo source) {
		List<WaystoneInfo> out = new ArrayList<>();
		for (WaystoneInfo info : all) {
			if (!info.key().equals(source.key())) {
				out.add(info);
			}
		}
		out.sort(Comparator.<WaystoneInfo>comparingInt(w -> w.dimension().equals(source.dimension()) ? 0 : 1)
				.thenComparingDouble(w -> w.dimension().equals(source.dimension()) ? w.horizontalDistance(source) : 0.0)
				.thenComparing(WaystoneInfo::dimension)
				.thenComparing(w -> w.name().toLowerCase(java.util.Locale.ROOT)));
		return out;
	}

	public void setLastUsed(String player, WaystoneInfo info) {
		lastUsed.put(player, info.key());
	}

	public Optional<WaystoneInfo> lastUsed(String player) {
		String key = lastUsed.get(player);
		return key == null ? Optional.empty() : byKey(key);
	}

	public Map<String, String> lastUsedMap() {
		return Map.copyOf(lastUsed);
	}
}
