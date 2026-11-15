package com.mlin.notas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mlin.notas.village.gen.VillageGen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vilas 3.0 fase 2: decoracao do mundo (arvores, pedregulhos, aboboras, lagos, fontes...) nao nasce na rua, na praca,
 * nos portoes nem nos quarteiroes da vila inicial ({@link VillageGen#allowsFeature}). Mixin porque a posicao final de
 * uma feature so existe aqui (depois dos modificadores de posicao) e a Fabric so modifica biomas inteiros; vale so na
 * geracao do Overworld (WorldGenRegion), nunca no osso de farinha.
 */
@Mixin(FeaturePlacer.class)
public abstract class FeaturePlacerVillageMixin {
	@WrapOperation(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/levelgen/feature/Feature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean mlinnotas$villageDecoration(Feature feature, WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
			Operation<Boolean> original) {
		if (!VillageGen.allowsFeature(level, feature, origin)) {
			return false;
		}
		boolean placed = original.call(feature, level, generator, random, origin);
		if (placed) {
			VillageGen.afterGroundCover(level, feature, origin);
		}
		return placed;
	}
}
