package com.mlin.notas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mlin.notas.village.gen.VillageGen;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vilas 3.0 fase 2: cavernas e canions dos carvers nao abrem a camada fechada embaixo da vila inicial. O gerador ja
 * passa um filtro do carving mask (o do blender de chunks antigos); aqui ele e composto com o da vila
 * ({@link VillageGen#carvingFilter}). Mixin porque nao ha evento de carver na Fabric.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorVillageMixin {
	@WrapOperation(method = "generateCarvers", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/blending/Blender;getCarvingFilter()Lnet/minecraft/world/level/chunk/CarvingMask$Filter;"))
	private CarvingMask.Filter mlinnotas$villageCarvingFilter(Blender blender, Operation<CarvingMask.Filter> original,
			@Local(argsOnly = true) ChunkAccess chunk) {
		return VillageGen.carvingFilter(original.call(blender), chunk.getPos().x(), chunk.getPos().z());
	}
}
