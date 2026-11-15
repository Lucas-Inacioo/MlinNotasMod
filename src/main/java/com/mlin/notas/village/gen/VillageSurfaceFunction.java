package com.mlin.notas.village.gen;

import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.VillageFieldServer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Tipo {@code mlinnotas:village_surface}: embrulha a superficie preliminar do Overworld (a que o aquifero usa para
 * decidir o nivel da agua e as regras de superficie usam para "acima da superficie" = grama/terra) e, no campo da vila
 * inicial, devolve a altura do campo ({@link VillageGenRules#surface}). Sem isso um morro cortado continuaria "debaixo
 * da terra" para o aquifero (agua parada no ar em cima da vila) e para a superficie (chao de pedra).
 */
public record VillageSurfaceFunction(DensityFunction input) implements DensityFunction {
	public static final MapCodec<VillageSurfaceFunction> CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(DensityFunction.CODEC.fieldOf("input").forGetter(VillageSurfaceFunction::input)).apply(i, VillageSurfaceFunction::new));

	@Override
	public DensitySampler compileSampler(DensityFunction.CompileContext context) {
		return new Sampler(input.compileSampler(context));
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		DensityFunction in = rule.rewrite(input);
		return in == input ? this : new VillageSurfaceFunction(in);
	}

	@Override
	public Interval range() {
		return Interval.encapsulating(input.range(), Interval.of(-2048.0F, 2048.0F));
	}

	@Override
	public @DensityFunction.Axes int domainAxes() {
		return input.domainAxes() | DensityFunction.AXIS_X | DensityFunction.AXIS_Z;
	}

	@Override
	public MapCodec<VillageSurfaceFunction> codec() {
		return CODEC;
	}

	record Sampler(DensitySampler input) implements DensitySampler {
		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
			input.sampleVolume(context, out, volume);
			HeightField field = VillageFieldServer.active();
			if (field == null || !VillageBlendFunction.overlaps(field, volume) || VillageGen.bypassed()) {
				return;
			}
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
					for (int y = 0; y < volume.sizeY(); y++) {
						int index = volume.indexUnchecked(x, y, z);
						out.set(index, VillageGenRules.surface(out.get(index), height, alpha));
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
			return VillageGenRules.surface(value, field.height(blockX, blockZ), VillageGenRules.alpha(field.weight(blockX, blockZ), field.kind(blockX, blockZ)));
		}
	}
}
