package com.mlin.notas.village.gen;

import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.VillageFieldServer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Tipo de funcao de densidade {@code mlinnotas:village_blend}: embrulha a {@code final_density} do Overworld e, nas
 * colunas do campo de alturas da vila inicial ({@link VillageFieldServer#active()}), troca a densidade pela do campo
 * ({@link VillageGenRules#density}). Fora do campo, sem campo ou com o desvio ligado ({@link VillageGen#bypassed()}) e
 * a funcao de dentro sem mudanca nenhuma (um teste de nulo e de caixa por volume).
 *
 * <p>{@code natural_surface}: superficie preliminar crua do gerador (sem cavernas), usada para saber se um bloco de ar
 * fundo era caverna (fica) ou ceu de um vale aterrado (vira chao).
 */
public record VillageBlendFunction(DensityFunction input, DensityFunction naturalSurface) implements DensityFunction {
	public static final MapCodec<VillageBlendFunction> CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(DensityFunction.CODEC.fieldOf("input").forGetter(VillageBlendFunction::input),
					DensityFunction.CODEC.fieldOf("natural_surface").forGetter(VillageBlendFunction::naturalSurface))
					.apply(i, VillageBlendFunction::new));

	@Override
	public DensitySampler compileSampler(DensityFunction.CompileContext context) {
		return new Sampler(input.compileSampler(context), naturalSurface.compileSampler(context));
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		DensityFunction in = rule.rewrite(input);
		DensityFunction surface = rule.rewrite(naturalSurface);
		return in == input && surface == naturalSurface ? this : new VillageBlendFunction(in, surface);
	}

	@Override
	public Interval range() {
		return Interval.encapsulating(input.range(), Interval.of(-1.0F, 1.0F));
	}

	@Override
	public @DensityFunction.Axes int domainAxes() {
		return DensityFunction.ALL_AXES;
	}

	@Override
	public MapCodec<VillageBlendFunction> codec() {
		return CODEC;
	}

	/** O volume cruza o campo? */
	static boolean overlaps(HeightField field, DensityVolume volume) {
		int x0 = field.centerX() - field.extent();
		int x1 = field.centerX() + field.extent();
		int z0 = field.centerZ() - field.extent();
		int z1 = field.centerZ() + field.extent();
		return volume.maxBlockX() >= x0 && volume.minBlockX() <= x1 && volume.maxBlockZ() >= z0 && volume.minBlockZ() <= z1;
	}

	record Sampler(DensitySampler input, DensitySampler naturalSurface) implements DensitySampler {
		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
			input.sampleVolume(context, out, volume);
			HeightField field = VillageFieldServer.active();
			if (field == null || !overlaps(field, volume) || VillageGen.bypassed()) {
				return;
			}
			DensityVolume flat = new DensityVolume(volume.sizeX(), 1, volume.sizeZ(), volume.minBlockX(), 0, volume.minBlockZ(), volume.stepBlockX(), 1,
					volume.stepBlockZ());
			try (ScopedDensityBuffer surface = context.acquireBuffer(flat)) {
				naturalSurface.sampleVolume(context, surface, flat);
				for (int z = 0; z < volume.sizeZ(); z++) {
					int bz = volume.blockZ(z);
					for (int x = 0; x < volume.sizeX(); x++) {
						int bx = volume.blockX(x);
						if (!field.contains(bx, bz)) {
							continue;
						}
						float alpha = VillageGenRules.alpha(field.weight(bx, bz), field.kind(bx, bz));
						if (alpha <= 0) {
							continue;
						}
						int height = field.height(bx, bz);
						int depth = field.depth(bx, bz);
						int natural = Mth.floor(surface.get(flat.indexUnchecked(x, 0, z)));
						for (int y = 0; y < volume.sizeY(); y++) {
							int index = volume.indexUnchecked(x, y, z);
							out.set(index, VillageGenRules.density(volume.blockY(y), height, depth, natural, out.get(index), alpha));
						}
					}
				}
			}
		}

		@Override
		public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
			float value = input.sampleValue(context, blockX, blockY, blockZ);
			HeightField field = VillageFieldServer.active();
			if (field == null || !field.contains(blockX, blockZ) || VillageGen.bypassed()) {
				return value;
			}
			float alpha = VillageGenRules.alpha(field.weight(blockX, blockZ), field.kind(blockX, blockZ));
			if (alpha <= 0) {
				return value;
			}
			int natural = Mth.floor(naturalSurface.sampleValue(context, blockX, 0, blockZ));
			return VillageGenRules.density(blockY, field.height(blockX, blockZ), field.depth(blockX, blockZ), natural, value, alpha);
		}
	}
}
