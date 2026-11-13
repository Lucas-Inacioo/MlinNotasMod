package com.mlin.notas.nether;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.List;
import java.util.Random;

/**
 * Features dos biomas novos do Nether (tipos {@code mlinnotas:*} dos JSON em {@code worldgen/feature}). A origem vem do
 * placement {@code count_on_every_layer}: primeiro bloco de ar acima de um chao.
 */
public final class NetherFeatures {
	private NetherFeatures() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, MlinNotas.id("burning_tree"), BurningTree.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, MlinNotas.id("ash_dune"), AshDune.CODEC);
	}

	private static boolean replaceable(BlockState state) {
		return state.isAir() || state.canBeReplaced();
	}

	/** Fungo gigante do Bosque Ardente (ou arbusto, se a altura for baixa). So nasce em nilio de brasas. */
	public record BurningTree(int minHeight, int maxHeight) implements Feature {
		public static final MapCodec<BurningTree> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.intRange(3, 40).fieldOf("min_height").forGetter(BurningTree::minHeight),
				Codec.intRange(3, 40).fieldOf("max_height").forGetter(BurningTree::maxHeight)).apply(i, BurningTree::new));

		@Override
		public MapCodec<BurningTree> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin.below()).is(NetherRegistry.EMBER_NYLIUM) || !level.isEmptyBlock(origin)) {
				return false;
			}
			int height = minHeight + random.nextInt(Math.max(1, maxHeight - minHeight + 1));
			// precisa de espaco ate o teto
			for (int y = 1; y <= height + 2; y++) {
				if (!replaceable(level.getBlockState(origin.above(y)))) {
					return false;
				}
			}
			List<BurningTreeShape.Cell> cells = BurningTreeShape.tree(height, new Random(random.nextLong()));
			BlockState stem = NetherRegistry.BURNING_STEM.defaultBlockState();
			BlockState cap = NetherRegistry.BURNING_CAP.defaultBlockState();
			BlockState light = Blocks.SHROOMLIGHT.defaultBlockState();
			for (BurningTreeShape.Cell cell : cells) {
				BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
				BlockState old = level.getBlockState(pos);
				switch (cell.part()) {
					case STEM -> {
						if (replaceable(old) || cell.x() == 0 && cell.z() == 0) {
							level.setBlock(pos, stem, 2);
						}
					}
					case CAP -> {
						if (replaceable(old)) {
							level.setBlock(pos, cap, 2);
						}
					}
					case LIGHT -> {
						if (replaceable(old)) {
							level.setBlock(pos, light, 2);
						}
					}
					case VINE -> {
					}
				}
			}
			// cipos depois da copa: so penduram de bloco solido ou de outro cipo
			for (BurningTreeShape.Cell cell : cells) {
				if (cell.part() != BurningTreeShape.Part.VINE) {
					continue;
				}
				BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
				BlockState above = level.getBlockState(pos.above());
				boolean hangs = above.is(Blocks.WEEPING_VINES_PLANT) || above.is(Blocks.WEEPING_VINES) || above.is(NetherRegistry.BURNING_CAP)
						|| above.is(Blocks.SHROOMLIGHT);
				if (!hangs || !level.isEmptyBlock(pos)) {
					continue;
				}
				if (above.is(Blocks.WEEPING_VINES)) {
					level.setBlock(pos.above(), Blocks.WEEPING_VINES_PLANT.defaultBlockState(), 2);
				}
				level.setBlock(pos, Blocks.WEEPING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 25), 2);
			}
			return true;
		}
	}

	/** Duna baixa de cinza nas Planicies de Cinza: 1-2 blocos de altura, borda em degraus de 1 (da para subir andando). */
	public record AshDune() implements Feature {
		public static final MapCodec<AshDune> CODEC = MapCodec.unit(AshDune::new);

		@Override
		public MapCodec<AshDune> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin.below()).is(NetherRegistry.NETHER_ASH)) {
				return false;
			}
			int radius = 3 + random.nextInt(4);
			BlockState ash = NetherRegistry.NETHER_ASH.defaultBlockState();
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz) / radius;
					int height = d < 0.45 ? 2 : d < 1.0 ? 1 : 0;
					BlockPos column = origin.offset(dx, 0, dz);
					// so sobre chao de cinza ou solo de almas na mesma altura
					BlockState ground = level.getBlockState(column.below());
					if (!ground.is(NetherRegistry.NETHER_ASH) && !ground.is(Blocks.SOUL_SOIL)) {
						continue;
					}
					for (int y = 0; y < height; y++) {
						if (level.isEmptyBlock(column.above(y))) {
							level.setBlock(column.above(y), ash, 2);
						}
					}
				}
			}
			return true;
		}
	}
}
