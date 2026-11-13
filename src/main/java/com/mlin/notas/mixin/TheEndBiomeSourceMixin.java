package com.mlin.notas.mixin;

import com.mlin.notas.end.LargeEndBiomes;
import com.mlin.notas.end.LargeEndBiomesHolder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Biomas grandes nas ilhas externas do End ({@link LargeEndBiomes}). Nao ha evento da Fabric para trocar o
 * resolvedor do End: a Fabric Biome API so sorteia substitutos com ruido de escala pequena (manchas de 1-2 chunks).
 * O embrulho e aplicado no retorno de {@code createResolver}, entao roda depois do sorteio da Fabric (que continua
 * colocando os biomas do mod em {@code possibleBiomes}). A fonte e criada por {@code create(HolderGetter)}
 * (o codec da Fabric tambem passa por ele); os holders sao resolvidos so no primeiro uso, como a Fabric faz.
 */
@Mixin(TheEndBiomeSource.class)
public abstract class TheEndBiomeSourceMixin implements LargeEndBiomesHolder {
	@Unique
	private HolderGetter<Biome> mlinnotas$biomeLookup;
	@Unique
	private LargeEndBiomes mlinnotas$largeBiomes;
	@Unique
	private boolean mlinnotas$resolved;

	@Override
	public void mlinnotas$setBiomeLookup(HolderGetter<Biome> biomes) {
		mlinnotas$biomeLookup = biomes;
	}

	@Inject(method = "create", at = @At("RETURN"))
	private static void mlinnotas$captureBiomes(HolderGetter<Biome> biomes, CallbackInfoReturnable<TheEndBiomeSource> cir) {
		((LargeEndBiomesHolder) cir.getReturnValue()).mlinnotas$setBiomeLookup(biomes);
	}

	@Inject(method = "createResolver", at = @At("RETURN"), cancellable = true)
	private void mlinnotas$largeBiomes(Climate.Sampler sampler, CallbackInfoReturnable<BiomeResolver> cir) {
		LargeEndBiomes large;
		synchronized (this) {
			if (!mlinnotas$resolved) {
				mlinnotas$resolved = true;
				mlinnotas$largeBiomes = mlinnotas$biomeLookup == null ? null : LargeEndBiomes.tryCreate(mlinnotas$biomeLookup);
			}
			large = mlinnotas$largeBiomes;
		}
		if (large != null) {
			cir.setReturnValue(large.wrap(cir.getReturnValue(), sampler));
		}
	}
}
