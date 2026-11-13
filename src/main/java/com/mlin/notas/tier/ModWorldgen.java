package com.mlin.notas.tier;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.end.EndBiomes;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Coloca os minerios novos na geracao de mundo. Os features ficam em
 * {@code data/mlinnotas/worldgen/feature} e {@code worldgen/placed_feature}; aqui so escolhemos os biomas:
 * cobalto no Overworld, infernita so no Nether, endita em todo o End e astralita so nas ilhas externas do End.
 * Usa as tags de bioma por dimensao (e nao "biomas que a dimensao gera"), entao vale para qualquer preset de mundo.
 */
public final class ModWorldgen {
	public static final ResourceKey<PlacedFeature> ORE_COBALT = placed("ore_cobalt");
	public static final ResourceKey<PlacedFeature> ORE_INFERNITE = placed("ore_infernite");
	public static final ResourceKey<PlacedFeature> ORE_ENDITE = placed("ore_endite");
	public static final ResourceKey<PlacedFeature> ORE_ASTRALITE = placed("ore_astralite");
	// veios ricos: blobs grandes (~30-60 blocos) e raros
	public static final ResourceKey<PlacedFeature> ORE_COBALT_RICH = placed("ore_cobalt_rich");
	public static final ResourceKey<PlacedFeature> ORE_INFERNITE_RICH = placed("ore_infernite_rich");
	public static final ResourceKey<PlacedFeature> ORE_INFERNITE_RICH_PEAKS = placed("ore_infernite_rich_peaks");
	public static final ResourceKey<PlacedFeature> ORE_ENDITE_RICH = placed("ore_endite_rich");
	public static final ResourceKey<PlacedFeature> ORE_ASTRALITE_RICH = placed("ore_astralite_rich");

	private ModWorldgen() {
	}

	public static void init() {
		GenerationStep.Decoration step = GenerationStep.Decoration.UNDERGROUND_ORES;
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD), step, ORE_COBALT);
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_NETHER), step, ORE_INFERNITE);
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_END), step, ORE_ENDITE);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS, Biomes.END_BARRENS, EndBiomes.MOSS_FIELDS, EndBiomes.CRYSTAL_GROVE, EndBiomes.VOID_WASTES, EndBiomes.CHORUS_FOREST,
				Biomes.SMALL_END_ISLANDS), step, ORE_ASTRALITE);
		// veios ricos (cobalto fundo no Overworld, infernita no Nether e mais nos Picos de Infernita, endita no End,
		// astralita nas ilhas externas)
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD), step, ORE_COBALT_RICH);
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_NETHER), step, ORE_INFERNITE_RICH);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(com.mlin.notas.nether.NetherRegistry.INFERNITE_PEAKS), step, ORE_INFERNITE_RICH_PEAKS);
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_END), step, ORE_ENDITE_RICH);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS, Biomes.END_BARRENS, EndBiomes.MOSS_FIELDS,
				EndBiomes.CRYSTAL_GROVE, EndBiomes.VOID_WASTES, EndBiomes.CHORUS_FOREST, Biomes.SMALL_END_ISLANDS), step, ORE_ASTRALITE_RICH);
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, MlinNotas.id(name));
	}
}
