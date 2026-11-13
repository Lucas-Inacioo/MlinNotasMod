package com.mlin.notas.variants;

import java.util.Optional;
import java.util.Set;

/**
 * Variantes de bioma dos mobs comuns (logica pura, sem net.minecraft). Cada variante pertence a um tipo base
 * (id do tipo de entidade) e a um conjunto de biomas (ids). O efeito no golpe e o drop extra ficam na ponte
 * {@code MobVariantsServer}; a textura no cliente.
 */
public enum MobVariant {
	MOSSY_ZOMBIE("mossy_zombie", "minecraft:zombie",
			Set.of("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "minecraft:swamp", "minecraft:lush_caves")),
	SNOW_SPIDER("snow_spider", "minecraft:spider",
			Set.of("minecraft:snowy_plains", "minecraft:snowy_beach", "minecraft:snowy_slopes", "minecraft:snowy_taiga", "minecraft:grove",
					"minecraft:ice_spikes", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:frozen_river")),
	BLAZING_SKELETON("blazing_skeleton", "minecraft:skeleton",
			Set.of("minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands")),
	CRYSTAL_CREEPER("crystal_creeper", "minecraft:creeper",
			Set.of("minecraft:dripstone_caves", "minecraft:deep_dark")),
	MANGROVE_DROWNED("mangrove_drowned", "minecraft:drowned",
			Set.of("minecraft:mangrove_swamp")),
	ASHEN_WITHER_SKELETON("ashen_wither_skeleton", "minecraft:wither_skeleton",
			Set.of("mlinnotas:ashen_plains", "minecraft:basalt_deltas"));

	private final String id;
	private final String baseType;
	private final Set<String> biomes;

	MobVariant(String id, String baseType, Set<String> biomes) {
		this.id = id;
		this.baseType = baseType;
		this.biomes = biomes;
	}

	public String id() {
		return id;
	}

	/** Id do tipo de entidade base (ex.: {@code minecraft:zombie}). */
	public String baseType() {
		return baseType;
	}

	public Set<String> biomes() {
		return biomes;
	}

	/** Chave de lang do nome mostrado (nome custom da entidade e HUD). */
	public String translationKey() {
		return "entity.mlinnotas.variant." + id;
	}

	public static Optional<MobVariant> byId(String id) {
		for (MobVariant v : values()) {
			if (v.id.equals(id)) {
				return Optional.of(v);
			}
		}
		return Optional.empty();
	}
}
