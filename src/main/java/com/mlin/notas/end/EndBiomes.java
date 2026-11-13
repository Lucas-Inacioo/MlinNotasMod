package com.mlin.notas.end;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.biome.v1.TheEndBiomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Biomas novos do End (JSON em data/mlinnotas/worldgen/biome; superficie em data/minecraft/worldgen/material_rule/end.json).
 * Ficam nas ilhas externas, misturados aos vanilla:
 * <ul>
 *   <li>Campos de Musgo do End (terras altas): musgo, grama do vazio e aguas-vivas.</li>
 *   <li>Bosque Cristalino (terras altas): espinhos e aglomerados de cristal etereo, acaros cristalinos.</li>
 *   <li>Ermos do Vazio (beiradas das ilhas): cinza do vazio, cinzas no ar, acaros.</li>
 * </ul>
 */
public final class EndBiomes {
	public static final ResourceKey<Biome> MOSS_FIELDS = key("end_moss_fields");
	public static final ResourceKey<Biome> CRYSTAL_GROVE = key("ether_crystal_grove");
	public static final ResourceKey<Biome> VOID_WASTES = key("void_wastes");
	public static final ResourceKey<Biome> CHORUS_FOREST = key("chorus_forest");

	private EndBiomes() {
	}

	private static ResourceKey<Biome> key(String name) {
		return ResourceKey.create(Registries.BIOME, MlinNotas.id(name));
	}

	public static void init() {
		TheEndBiomes.addHighlandsBiome(MOSS_FIELDS, 1.0);
		TheEndBiomes.addHighlandsBiome(CRYSTAL_GROVE, 0.7);
		TheEndBiomes.addHighlandsBiome(CHORUS_FOREST, 0.7);
		TheEndBiomes.addBarrensBiome(Biomes.END_HIGHLANDS, VOID_WASTES, 0.5);
		TheEndBiomes.addBarrensBiome(MOSS_FIELDS, VOID_WASTES, 1.0);
		TheEndBiomes.addBarrensBiome(CRYSTAL_GROVE, VOID_WASTES, 1.0);
		TheEndBiomes.addBarrensBiome(CHORUS_FOREST, VOID_WASTES, 1.0);
		EndFeatures.init();
	}
}
