package com.mlin.notas.variants;

import java.util.Optional;
import java.util.Set;

/**
 * Regra pura de sorteio: tipo base + bioma + motivo do spawn -> variante (ou nenhuma).
 */
public final class MobVariants {
	/** Chance de um mob que nasce num bioma da variante virar a variante. */
	public static final double CHANCE = 0.6;

	/** Motivos de spawn que podem gerar variante (nomes de {@code EntitySpawnReason}). */
	public static final Set<String> SPAWN_REASONS = Set.of("NATURAL", "CHUNK_GENERATION", "SPAWNER", "TRIAL_SPAWNER",
			"SPAWN_ITEM_USE", "DISPENSER", "REINFORCEMENT");

	private MobVariants() {
	}

	/** Variante possivel para o tipo no bioma, sem sortear. */
	public static Optional<MobVariant> candidate(String entityType, String biome) {
		for (MobVariant v : MobVariant.values()) {
			if (v.baseType().equals(entityType) && v.biomes().contains(biome)) {
				return Optional.of(v);
			}
		}
		return Optional.empty();
	}

	/**
	 * Sorteio: {@code roll} uniforme em [0, 1). Devolve a variante quando o motivo de spawn conta e
	 * {@code roll < CHANCE}.
	 */
	public static Optional<MobVariant> pick(String entityType, String biome, String spawnReason, double roll) {
		if (spawnReason == null || !SPAWN_REASONS.contains(spawnReason) || roll >= CHANCE) {
			return Optional.empty();
		}
		return candidate(entityType, biome);
	}
}
