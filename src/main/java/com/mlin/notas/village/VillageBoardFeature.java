package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Coloca o quadro de missoes nas vilas vanilla geradas: no chunk que tem o sino da praca (o ponto de encontro do
 * centro da vila), o quadro vai em chao livre a 2-4 blocos do sino, virado para ele. So olha chunks que fazem parte
 * de uma estrutura com a tag {@code #minecraft:village}.
 */
public record VillageBoardFeature() implements Feature {
	public static final MapCodec<VillageBoardFeature> CODEC = MapCodec.unit(VillageBoardFeature::new);
	public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, MlinNotas.id("village_board"));

	static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, MlinNotas.id("village_board"), CODEC);
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD), GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PLACED);
	}

	@Override
	public MapCodec<VillageBoardFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		ChunkAccess chunk = level.getChunk(origin);
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		boolean village = false;
		for (Structure structure : chunk.getAllReferences().keySet()) {
			if (structures.wrapAsHolder(structure).is(StructureTags.VILLAGE)) {
				village = true;
				break;
			}
		}
		if (!village) {
			return false;
		}
		ChunkPos chunkPos = chunk.getPos();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
			for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				for (int y = top; y >= top - 12; y--) {
					pos.set(x, y, z);
					if (level.getBlockState(pos).is(Blocks.BELL)) {
						return placeNear(level, chunkPos, pos.immutable());
					}
				}
			}
		}
		return false;
	}

	private static boolean placeNear(WorldGenLevel level, ChunkPos chunk, BlockPos bell) {
		for (int r = 2; r <= 5; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = bell.getX() + dx;
					int z = bell.getZ() + dz;
					if (x < chunk.getMinBlockX() || x > chunk.getMaxBlockX() || z < chunk.getMinBlockZ() || z > chunk.getMaxBlockZ()) {
						continue;
					}
					BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
					BlockState ground = level.getBlockState(feet.below());
					if (Math.abs(feet.getY() - bell.getY()) > 3 || !ground.isFaceSturdy(level, feet.below(), Direction.UP)
							|| ground.is(Blocks.DIRT_PATH) || !ground.getFluidState().isEmpty()) {
						continue;
					}
					if (!level.getBlockState(feet).canBeReplaced() || !level.getBlockState(feet.above()).canBeReplaced()
							|| !level.getBlockState(feet).getFluidState().isEmpty()) {
						continue;
					}
					Direction facing = Facing.toward(-dx, -dz) == Facing.NORTH ? Direction.NORTH : Facing.toward(-dx, -dz) == Facing.SOUTH
							? Direction.SOUTH : Facing.toward(-dx, -dz) == Facing.EAST ? Direction.EAST : Direction.WEST;
					VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState().setValue(VillageBoardBlock.FACING, facing), feet,
							Block.UPDATE_CLIENTS);
					return true;
				}
			}
		}
		return false;
	}
}
