package com.mlin.notas.dragon;

import com.mlin.notas.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feature {@code mlinnotas:dragon_arena}: coloca, chunk por chunk, a parte do {@link ArenaPlan} que cai no chunk sendo
 * decorado. O plano e calculado uma vez por semente com a altura do terreno vinda do proprio gerador (a mesma em todo
 * chunk, entao as pecas que cruzam chunks se encaixam) e os pilares vanilla do mundo. So entra no bioma
 * {@code minecraft:the_end} (ilha central), depois do {@code end_spike}.
 */
public record DragonArenaFeature() implements Feature {
	public static final DragonArenaFeature INSTANCE = new DragonArenaFeature();
	public static final MapCodec<DragonArenaFeature> CODEC = MapCodec.unit(INSTANCE);
	/** Raio (em chunks) em volta de 0,0 onde a arena pode ter blocos. */
	private static final int CHUNK_RADIUS = 5;

	private record Cached(long seed, ArenaPlan plan, Map<Long, List<Long>> byChunk) {
	}

	private static volatile Cached cache;

	@Override
	public MapCodec<DragonArenaFeature> codec() {
		return CODEC;
	}

	/** Plano da arena deste mundo (calculado uma vez por semente). */
	public static ArenaPlan plan(WorldGenLevel level, ChunkGenerator generator) {
		return cached(level, generator).plan();
	}

	private static Cached cached(WorldGenLevel level, ChunkGenerator generator) {
		Cached c = cache;
		long seed = level.getSeed();
		if (c != null && c.seed() == seed) {
			return c;
		}
		synchronized (DragonArenaFeature.class) {
			c = cache;
			if (c != null && c.seed() == seed) {
				return c;
			}
			long start = System.nanoTime();
			RandomState randomState = level.getLevel().getChunkSource().randomState();
			int minY = level.getMinY();
			Map<Long, Integer> heights = new HashMap<>();
			ArenaPlanner.Ground ground = (x, z) -> heights.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> {
				int y = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
				return y <= minY + 1 ? ArenaPlanner.VOID : y;
			});
			List<ArenaPlanner.Spike> spikes = new ArrayList<>();
			for (EndSpikeFeature.EndSpike s : EndSpikeFeature.getSpikesForLevel(level)) {
				spikes.add(new ArenaPlanner.Spike(s.getCenterX(), s.getCenterZ(), s.getRadius(), s.getHeight(), s.isGuarded()));
			}
			ArenaPlan plan = ArenaPlanner.plan(seed, spikes, ground);
			Map<Long, List<Long>> byChunk = new HashMap<>();
			for (long key : plan.blocks().keySet()) {
				long chunk = chunkKey(ArenaPlan.keyX(key) >> 4, ArenaPlan.keyZ(key) >> 4);
				byChunk.computeIfAbsent(chunk, k -> new ArrayList<>()).add(key);
			}
			c = new Cached(seed, plan, byChunk);
			com.mlin.notas.MlinNotas.LOGGER.info("[DragonArena] arena da ilha central planejada: {} blocos em {} ms", plan.blocks().size(),
					(System.nanoTime() - start) / 1_000_000);
			cache = c;
			return c;
		}
	}

	private static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int cx = origin.getX() >> 4;
		int cz = origin.getZ() >> 4;
		if (Math.abs(cx) > CHUNK_RADIUS || Math.abs(cz) > CHUNK_RADIUS) {
			return false;
		}
		Cached c = cached(level, generator);
		List<Long> keys = c.byChunk().get(chunkKey(cx, cz));
		if (keys == null) {
			return false;
		}
		List<BlockPos> walls = new ArrayList<>();
		for (long key : keys) {
			ArenaPart part = c.plan().blocks().get(key);
			BlockPos pos = new BlockPos(ArenaPlan.keyX(key), ArenaPlan.keyY(key), ArenaPlan.keyZ(key));
			BlockState old = level.getBlockState(pos);
			if (part.paving()) {
				if (!old.is(Blocks.END_STONE)) {
					continue;
				}
			} else if (!(old.isAir() || old.canBeReplaced())) {
				continue;
			}
			BlockState state = state(part);
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			if (state.getBlock() instanceof WallBlock) {
				walls.add(pos);
			}
		}
		for (BlockPos pos : walls) {
			BlockState updated = Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos);
			level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
		}
		return true;
	}

	/** Bloco de cada papel do plano. */
	public static BlockState state(ArenaPart part) {
		return switch (part) {
			case PAVE_BRICK, BRICK -> Blocks.END_STONE_BRICKS.defaultBlockState();
			case PAVE_WORN -> Blocks.END_STONE.defaultBlockState();
			case PAVE_MOSS -> ModBlocks.END_MOSS.defaultBlockState();
			case PAVE_VOID, VOID_BRICK -> ModBlocks.VOID_BRICKS.defaultBlockState();
			case PAVE_PURPUR, PURPUR -> Blocks.PURPUR_BLOCK.defaultBlockState();
			case PAVE_OBSIDIAN, OBSIDIAN -> Blocks.OBSIDIAN.defaultBlockState();
			case BRICK_SLAB -> Blocks.END_STONE_BRICK_SLAB.defaultBlockState();
			case BRICK_WALL -> Blocks.END_STONE_BRICK_WALL.defaultBlockState();
			case PURPUR_PILLAR -> Blocks.PURPUR_PILLAR.defaultBlockState();
			case PURPUR_PILLAR_X -> Blocks.PURPUR_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
			case PURPUR_PILLAR_Z -> Blocks.PURPUR_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
			case PURPUR_SLAB -> Blocks.PURPUR_SLAB.defaultBlockState();
			case PURPUR_SLAB_TOP -> Blocks.PURPUR_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			case CRYING_OBSIDIAN -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
			case LAMP -> ModBlocks.PURPUR_LAMP.defaultBlockState();
			case CRYSTAL_BLOCK -> ModBlocks.ETHER_CRYSTAL_BLOCK.defaultBlockState();
			case END_ROD -> Blocks.END_ROD.defaultBlockState();
			case CRYSTAL -> ModBlocks.ETHER_CRYSTAL_CLUSTER.defaultBlockState();
			case SOUL_LANTERN -> Blocks.SOUL_LANTERN.defaultBlockState();
		};
	}
}
