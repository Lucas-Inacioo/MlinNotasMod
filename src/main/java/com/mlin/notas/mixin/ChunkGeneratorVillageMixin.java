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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mlin.notas.village.gen.VillageGen;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import java.util.function.Predicate;
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

	/**
	 * Vilas 3.0 fase 2: nenhuma estrutura de superficie cuja caixa cruze o campo de alturas da vila inicial (plano + faixa
	 * de transicao), e as subterraneas so se ficam abaixo da camada fechada ({@link VillageGen#filterStart}). Pela caixa
	 * pronta do inicio (pecas inclusas), nao so pelo chunk de origem.
	 */
	@WrapOperation(method = "tryGenerateStructure", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/structure/Structure;generate(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/Climate$Sampler;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
	private StructureStart mlinnotas$noStructureInVillageField(Structure structure, Holder<Structure> selected, ResourceKey<Level> dimension,
			RegistryAccess registryAccess, ChunkGenerator chunkGenerator, BiomeSource biomeSource, Climate.Sampler climateSampler, RandomState randomState,
			StructureTemplateManager templates, long seed, ChunkPos sourceChunkPos, int references, LevelHeightAccessor heightAccessor,
			Predicate<Holder<Biome>> validBiome, Operation<StructureStart> original) {
		StructureStart start = original.call(structure, selected, dimension, registryAccess, chunkGenerator, biomeSource, climateSampler, randomState, templates,
				seed, sourceChunkPos, references, heightAccessor, validBiome);
		return dimension == Level.OVERWORLD ? VillageGen.filterStart(structure, start) : start;
	}
}
