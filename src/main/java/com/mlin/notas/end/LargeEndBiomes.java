package com.mlin.notas.end;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ponte entre o {@link EndBiomeLayout} (logica pura) e o {@code TheEndBiomeSource} vanilla (mixin
 * {@code TheEndBiomeSourceMixin}): embrulha o resolvedor de biomas do End e, nas ilhas externas, troca o bioma
 * pelo da regiao grande. A ilha principal e as ilhotas pequenas (vazio) continuam vanilla. A classificacao de
 * altura e a mesma do vanilla (ruido de ilhas do End no centro do chunk).
 */
public final class LargeEndBiomes {
	private final Map<EndBiomeLayout.Biome, Holder<Biome>> holders = new EnumMap<>(EndBiomeLayout.Biome.class);
	private volatile EndBiomeLayout layout;

	public LargeEndBiomes(HolderGetter<Biome> biomes) {
		holders.put(EndBiomeLayout.Biome.MOSS_FIELDS, biomes.getOrThrow(EndBiomes.MOSS_FIELDS));
		holders.put(EndBiomeLayout.Biome.CRYSTAL_GROVE, biomes.getOrThrow(EndBiomes.CRYSTAL_GROVE));
		holders.put(EndBiomeLayout.Biome.CHORUS_FOREST, biomes.getOrThrow(EndBiomes.CHORUS_FOREST));
		holders.put(EndBiomeLayout.Biome.VOID_WASTES, biomes.getOrThrow(EndBiomes.VOID_WASTES));
		holders.put(EndBiomeLayout.Biome.END_HIGHLANDS, biomes.getOrThrow(Biomes.END_HIGHLANDS));
		holders.put(EndBiomeLayout.Biome.END_MIDLANDS, biomes.getOrThrow(Biomes.END_MIDLANDS));
		holders.put(EndBiomeLayout.Biome.END_BARRENS, biomes.getOrThrow(Biomes.END_BARRENS));
	}

	/** Cria o embrulho, ou null se algum bioma nao existir (datapack sem os biomas do mod). */
	public static LargeEndBiomes tryCreate(HolderGetter<Biome> biomes) {
		try {
			return new LargeEndBiomes(biomes);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public BiomeResolver wrap(BiomeResolver original, Climate.Sampler sampler) {
		EndBiomeLayout layout = layoutFor(sampler);
		return (quartX, quartY, quartZ) -> pick(layout, original.getNoiseBiome(quartX, quartY, quartZ), sampler, quartX, quartY, quartZ);
	}

	private Holder<Biome> pick(EndBiomeLayout layout, Holder<Biome> base, Climate.Sampler sampler, int quartX, int quartY, int quartZ) {
		int blockX = QuartPos.toBlock(quartX);
		int blockZ = QuartPos.toBlock(quartZ);
		if (!EndBiomeLayout.applies(blockX, blockZ) || base.is(Biomes.THE_END) || base.is(Biomes.SMALL_END_ISLANDS)) {
			return base;
		}
		return holders.get(layout.biomeAt(blockX, blockZ, height(sampler, blockX, QuartPos.toBlock(quartY), blockZ)));
	}

	/** Mesma conta do TheEndBiomeSource vanilla (ruido de ilhas no centro do chunk). */
	static EndBiomeLayout.Height height(Climate.Sampler sampler, int blockX, int blockY, int blockZ) {
		int centerX = (SectionPos.blockToSectionCoord(blockX) * 2 + 1) * 8;
		int centerZ = (SectionPos.blockToSectionCoord(blockZ) * 2 + 1) * 8;
		double value = sampler.erosion().sampleValue(centerX, blockY, centerZ);
		if (value > 0.25) {
			return EndBiomeLayout.Height.HIGHLANDS;
		}
		return value >= -0.0625 ? EndBiomeLayout.Height.MIDLANDS : EndBiomeLayout.Height.BARRENS;
	}

	/**
	 * O sampler nao expoe a seed do mundo; o ruido de ilhas do End e semeado por ela, entao um hash de algumas
	 * amostras espalhadas serve de seed do layout (mesma seed de mundo = mesmo layout).
	 */
	private EndBiomeLayout layoutFor(Climate.Sampler sampler) {
		EndBiomeLayout cached = layout;
		if (cached == null) {
			long hash = 0x6A09E667F3BCC909L;
			for (int i = 0; i < 16; i++) {
				int x = 2048 + (i % 4) * 1536;
				int z = -3072 + (i / 4) * 2048;
				hash = EndBiomeLayout.mix(hash ^ Float.floatToIntBits(sampler.erosion().sampleValue(x, 64, z)));
			}
			cached = new EndBiomeLayout(hash);
			layout = cached;
		}
		return cached;
	}
}
