package com.mlin.notas.end;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.List;
import java.util.Random;

/**
 * Features dos biomas grandes do End (tipos {@code mlinnotas:*} usados nos JSON de {@code worldgen/feature}). As formas
 * vem de {@link EndShapes} (logica pura); aqui so viram blocos. Todas partem do chao (origem = primeiro bloco de ar
 * acima da superficie, como o placement {@code heightmap} entrega) e desistem se nao houver chao.
 */
public final class EndFeatures {
	private EndFeatures() {
	}

	public static void init() {
		register("void_tree", VoidTree.CODEC);
		register("giant_crystal_spike", CrystalSpike.CODEC);
		register("crystal_geode", CrystalGeode.CODEC);
		register("void_crater", Crater.CODEC);
		register("giant_fossil", Fossil.CODEC);
		register("void_basalt_pillars", BasaltPillars.CODEC);
		register("void_pond", Pond.CODEC);
		register("giant_chorus", GiantChorus.CODEC);
		register("surface_patch", SurfacePatch.CODEC);
	}

	private static void register(String name, MapCodec<? extends Feature> codec) {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, MlinNotas.id(name), codec);
	}

	private static Random random(RandomSource random) {
		return new Random(random.nextLong());
	}

	/** Chao firme logo abaixo da origem (pedra do End, musgo, cinza ou basalto). */
	static boolean onGround(WorldGenLevel level, BlockPos origin) {
		BlockState below = level.getBlockState(origin.below());
		return below.is(Blocks.END_STONE) || below.is(ModBlocks.END_MOSS) || below.is(ModBlocks.VOID_ASH) || below.is(ModBlocks.VOID_BASALT)
				|| below.is(Blocks.CALCITE);
	}

	/** Coloca so onde nao ha bloco solido (nao corta outras estruturas). */
	static void placeIfFree(WorldGenLevel level, BlockPos pos, BlockState state) {
		BlockState old = level.getBlockState(pos);
		if (old.isAir() || old.canBeReplaced()) {
			level.setBlock(pos, state, 2);
		}
	}

	/** Arvore do vazio ou arbusto ({@code bush}). */
	public record VoidTree(boolean bush) implements Feature {
		public static final MapCodec<VoidTree> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.BOOL.optionalFieldOf("bush", false).forGetter(VoidTree::bush)).apply(i, VoidTree::new));

		@Override
		public MapCodec<VoidTree> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!onGround(level, origin) || !level.isEmptyBlock(origin)) {
				return false;
			}
			List<EndShapes.Cell> cells = bush ? EndShapes.bush(random(random)) : EndShapes.voidTree(random(random));
			BlockState log = ModBlocks.VOID_LOG.defaultBlockState();
			BlockState leaves = ModBlocks.VOID_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			for (EndShapes.Cell cell : cells) {
				BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
				if (cell.part() == EndShapes.Part.SOLID) {
					level.setBlock(pos, log, 2);
				} else {
					placeIfFree(level, pos, leaves);
				}
			}
			return true;
		}
	}

	/** Espinho gigante de cristal etereo ({@code min_height} a {@code max_height} blocos). */
	public record CrystalSpike(int minHeight, int maxHeight) implements Feature {
		public static final MapCodec<CrystalSpike> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.intRange(2, 32).fieldOf("min_height").forGetter(CrystalSpike::minHeight),
				Codec.intRange(2, 32).fieldOf("max_height").forGetter(CrystalSpike::maxHeight)).apply(i, CrystalSpike::new));

		@Override
		public MapCodec<CrystalSpike> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!onGround(level, origin)) {
				return false;
			}
			int height = minHeight + random.nextInt(Math.max(1, maxHeight - minHeight + 1));
			BlockState crystal = ModBlocks.ETHER_CRYSTAL_BLOCK.defaultBlockState();
			for (EndShapes.Cell cell : EndShapes.crystalSpike(random(random), height)) {
				BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
				if (cell.part() == EndShapes.Part.TIP) {
					placeIfFree(level, pos, ModBlocks.ETHER_CRYSTAL_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
				} else if (cell.y() < 0) {
					level.setBlock(pos, crystal, 2);
				} else {
					placeIfFree(level, pos, crystal);
				}
			}
			return true;
		}
	}

	/** Geodo de cristal semi-enterrado e aberto em cima: calcita, cristal etereo e aglomerados no miolo. */
	public record CrystalGeode() implements Feature {
		public static final MapCodec<CrystalGeode> CODEC = MapCodec.unit(CrystalGeode::new);

		@Override
		public MapCodec<CrystalGeode> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!onGround(level, origin)) {
				return false;
			}
			int radius = 4 + random.nextInt(3);
			BlockPos center = origin.below(radius - 2);
			List<EndShapes.Cell> cells = EndShapes.geode(radius);
			for (EndShapes.Cell cell : cells) {
				BlockPos pos = center.offset(cell.x(), cell.y(), cell.z());
				switch (cell.part()) {
					case SHELL -> {
						if (!level.isEmptyBlock(pos)) {
							level.setBlock(pos, Blocks.CALCITE.defaultBlockState(), 2);
						}
					}
					case SOLID -> level.setBlock(pos, ModBlocks.ETHER_CRYSTAL_BLOCK.defaultBlockState(), 2);
					default -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			// aglomerados crescendo do fundo e das paredes
			for (EndShapes.Cell cell : cells) {
				if (cell.part() == EndShapes.Part.AIR && random.nextInt(4) == 0) {
					BlockPos pos = center.offset(cell.x(), cell.y(), cell.z());
					for (Direction dir : Direction.values()) {
						if (level.getBlockState(pos.relative(dir.getOpposite())).is(ModBlocks.ETHER_CRYSTAL_BLOCK)) {
							level.setBlock(pos, ModBlocks.ETHER_CRYSTAL_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, dir), 2);
							break;
						}
					}
				}
			}
			return true;
		}
	}

	/** Cratera de impacto nos Ermos do Vazio: tigela forrada de cinza e basalto, borda levantada. */
	public record Crater() implements Feature {
		public static final MapCodec<Crater> CODEC = MapCodec.unit(Crater::new);

		@Override
		public MapCodec<Crater> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!onGround(level, origin)) {
				return false;
			}
			int radius = 4 + random.nextInt(4);
			int depth = 2 + random.nextInt(3);
			for (EndShapes.CraterColumn column : EndShapes.crater(random(random), radius, depth)) {
				int x = origin.getX() + column.x();
				int z = origin.getZ() + column.z();
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
				if (Math.abs(top - (origin.getY() - 1)) > 3) {
					continue;
				}
				if (column.rim()) {
					level.setBlock(new BlockPos(x, top + 1, z), (random.nextBoolean() ? ModBlocks.VOID_BASALT : ModBlocks.VOID_ASH).defaultBlockState(), 2);
					continue;
				}
				for (int d = 0; d < column.dig(); d++) {
					level.setBlock(new BlockPos(x, top - d, z), Blocks.AIR.defaultBlockState(), 2);
				}
				BlockPos floor = new BlockPos(x, top - column.dig(), z);
				if (!level.isEmptyBlock(floor)) {
					level.setBlock(floor, (random.nextInt(4) == 0 ? ModBlocks.VOID_BASALT : ModBlocks.VOID_ASH).defaultBlockState(), 2);
				}
			}
			return true;
		}
	}

	/** Esqueleto de criatura gigante (blocos de osso) meio enterrado. */
	public record Fossil() implements Feature {
		public static final MapCodec<Fossil> CODEC = MapCodec.unit(Fossil::new);

		@Override
		public MapCodec<Fossil> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!onGround(level, origin)) {
				return false;
			}
			boolean alongX = random.nextBoolean();
			int length = 9 + random.nextInt(8);
			BlockPos base = origin.below(1 + random.nextInt(3));
			BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
			for (EndShapes.Cell cell : EndShapes.fossil(random(random), length)) {
				BlockPos pos = alongX ? base.offset(cell.x() - length / 2, cell.y(), cell.z()) : base.offset(cell.z(), cell.y(), cell.x() - length / 2);
				if (cell.part() == EndShapes.Part.AIR) {
					if (level.getBlockState(pos).is(Blocks.BONE_BLOCK)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				} else if (random.nextInt(10) != 0) {
					level.setBlock(pos, bone, 2);
				}
			}
			return true;
		}
	}

	/** Pilares de basalto do vazio. */
	public record BasaltPillars() implements Feature {
		public static final MapCodec<BasaltPillars> CODEC = MapCodec.unit(BasaltPillars::new);

		@Override
		public MapCodec<BasaltPillars> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!onGround(level, origin)) {
				return false;
			}
			for (EndShapes.Cell cell : EndShapes.basaltPillars(random(random))) {
				BlockPos pos = origin.offset(cell.x(), cell.y(), cell.z());
				if (cell.y() < 0) {
					level.setBlock(pos, ModBlocks.VOID_BASALT.defaultBlockState(), 2);
				} else {
					placeIfFree(level, pos, ModBlocks.VOID_BASALT.defaultBlockState());
				}
			}
			return true;
		}
	}

	/** Lago raso dos Campos de Musgo: so poe agua onde ela fica cercada (nao vaza pela beira da ilha). */
	public record Pond() implements Feature {
		public static final MapCodec<Pond> CODEC = MapCodec.unit(Pond::new);

		@Override
		public MapCodec<Pond> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!onGround(level, origin)) {
				return false;
			}
			List<EndShapes.Cell> cells = EndShapes.pond(random(random));
			java.util.Set<BlockPos> water = new java.util.HashSet<>();
			for (EndShapes.Cell cell : cells) {
				water.add(origin.offset(cell.x(), cell.y(), cell.z()));
			}
			for (BlockPos pos : water) {
				if (level.isEmptyBlock(pos) || level.isEmptyBlock(pos.below())) {
					return false;
				}
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					BlockPos side = pos.relative(dir);
					if (!water.contains(side) && level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
						return false;
					}
				}
			}
			for (BlockPos pos : water) {
				level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
				// nada flutuando em cima da agua
				if (!level.getBlockState(pos.above()).isAir() && !water.contains(pos.above())) {
					level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
				}
			}
			return true;
		}
	}

	/** Coro gigante: tronco de 3 a 7 blocos e a copa de coro vanilla em cima, espalhando mais. */
	public record GiantChorus() implements Feature {
		public static final MapCodec<GiantChorus> CODEC = MapCodec.unit(GiantChorus::new);

		@Override
		public MapCodec<GiantChorus> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.isEmptyBlock(origin) || !level.getBlockState(origin.below()).is(BlockTags.SUPPORTS_CHORUS_PLANT)) {
				return false;
			}
			int trunk = 3 + random.nextInt(5);
			for (int y = 0; y < trunk; y++) {
				if (!level.isEmptyBlock(origin.above(y))) {
					return false;
				}
			}
			BlockState plant = Blocks.CHORUS_PLANT.defaultBlockState();
			for (int y = 0; y < trunk; y++) {
				level.setBlock(origin.above(y), plant, 2);
			}
			ChorusFlowerBlock.generatePlant(level, origin.above(trunk), random, 12);
			for (int y = 0; y <= trunk; y++) {
				BlockPos pos = origin.above(y);
				if (level.getBlockState(pos).is(Blocks.CHORUS_PLANT)) {
					level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, plant), 2);
				}
			}
			return true;
		}
	}

	/** Mancha irregular na superficie (calcita no Bosque Cristalino). */
	public record SurfacePatch(BlockState state, int radius) implements Feature {
		public static final MapCodec<SurfacePatch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				BlockState.CODEC.fieldOf("state").forGetter(SurfacePatch::state),
				Codec.intRange(1, 8).fieldOf("radius").forGetter(SurfacePatch::radius)).apply(i, SurfacePatch::new));

		@Override
		public MapCodec<SurfacePatch> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!onGround(level, origin)) {
				return false;
			}
			for (EndShapes.Cell cell : EndShapes.patch(random(random), radius)) {
				int x = origin.getX() + cell.x();
				int z = origin.getZ() + cell.z();
				BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
				if (Math.abs(top.getY() - (origin.getY() - 1)) <= 2 && (level.getBlockState(top).is(Blocks.END_STONE)
						|| level.getBlockState(top).is(ModBlocks.END_MOSS))) {
					level.setBlock(top, state, 2);
				}
			}
			return true;
		}
	}
}
