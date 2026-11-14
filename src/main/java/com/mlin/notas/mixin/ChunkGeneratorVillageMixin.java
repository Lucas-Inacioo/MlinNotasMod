package com.mlin.notas.mixin;

import com.mlin.notas.village.StartingVillage;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vila vanilla nao nasce em cima da vila inicial: o inicio de estrutura com a tag {@code #minecraft:village} cujo chunk
 * fica a menos de {@link StartingVillage#SUPPRESS_RADIUS} do centro escolhido e recusado antes de gerar. Mixin porque
 * a Fabric nao tem evento de "inicio de estrutura" (so modificadores de bioma, que mudariam a vila no bioma inteiro) e
 * um structure set proprio nao alcancaria as vilas vanilla de outros mods/datapacks.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorVillageMixin {
	@Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$noVillageAtStarter(StructureSet.StructureSelectionEntry selected, StructureManager structureManager,
			RegistryAccess registryAccess, RandomState randomState, StructureTemplateManager structureTemplateManager, long seed, ChunkAccess centerChunk,
			ChunkPos sourceChunkPos, ResourceKey<Level> level, Climate.Sampler climateSampler, CallbackInfoReturnable<Boolean> cir) {
		if (level == Level.OVERWORLD && StartingVillage.suppresses(sourceChunkPos.x(), sourceChunkPos.z())
				&& selected.structure().is(StructureTags.VILLAGE)) {
			cir.setReturnValue(false);
		}
	}
}
