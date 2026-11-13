package com.mlin.notas.vstructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Arrays;
import java.util.Optional;

/**
 * Onde nascem as estruturas do mar e as pequenas ({@link VKind.Placement} diferente de LAND/UNDERGROUND). Consome o
 * aleatorio na mesma ordem da {@link VanillaRemakeStructure} (semente, giro, espelho).
 */
final class SeaPlacement {
	private SeaPlacement() {
	}

	static Optional<Structure.GenerationStub> find(VKind kind, Structure.GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		WorldgenRandom random = context.random();
		long seed = random.nextLong();
		int turns = random.nextInt(4);
		boolean mirror = random.nextBoolean();
		ChunkGenerator generator = context.chunkGenerator();
		int seaLevel = generator.getSeaLevel();
		VKind.Placement placement = kind.placement();
		if (placement == VKind.Placement.BURIED) {
			// o mapa do tesouro aponta para o bloco (9, 9) do chunk (locate_offset do structure set): o bau fica ali
			int x = chunkPos.getBlockX(9);
			int z = chunkPos.getBlockZ(9);
			int top = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
			BlockPos origin = new BlockPos(x, top + 1, z);
			int variant = VariantRules.variant(kind, biomeAt(context, origin), top);
			return stub(kind, variant, seed, origin, 0, false);
		}
		int x = chunkPos.getMiddleBlockX();
		int z = chunkPos.getMiddleBlockZ();
		if (placement == VKind.Placement.MONUMENT) {
			for (Holder<Biome> biome : context.biomeResolver().getBiomesWithin(x, seaLevel, z, 29)) {
				if (!biome.is(BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING)) {
					return Optional.empty();
				}
			}
			int variant = VariantRules.variant(kind, biomeAt(context, new BlockPos(x, seaLevel, z)), seaLevel);
			VPlan plan = VanillaRemakePiece.planFor(kind, seed, variant);
			// topo 2 blocos abaixo da agua (como o vanilla, y 39..61 com o mar em 63)
			BlockPos origin = new BlockPos(x, seaLevel - 2 - plan.bounds()[4], z);
			return stub(kind, variant, seed, origin, turns, mirror);
		}
		if (placement == VKind.Placement.NETHER) {
			return nether(kind, context, seed, turns, mirror);
		}
		int surface = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
		int variant = VariantRules.variant(kind, biomeAt(context, new BlockPos(x, surface, z)), surface);
		VPlan plan = VanillaRemakePiece.planFor(kind, seed, variant);
		int[] area = plan.surfaceBounds();
		int midX = (area[0] + area[2]) / 2;
		int midZ = (area[1] + area[3]) / 2;
		int[][] samples = {{area[0], area[1]}, {area[2], area[1]}, {area[0], area[3]}, {area[2], area[3]}, {0, 0}, {midX, midZ},
				{midX, area[1]}, {midX, area[3]}, {area[0], midZ}, {area[2], midZ}};
		int[] heights = new int[samples.length];
		int deepest = 0;
		for (int i = 0; i < samples.length; i++) {
			int[] w = VanillaRemakeStructure.turn(samples[i][0], samples[i][1], turns, mirror);
			int sx = x + w[0];
			int sz = z + w[1];
			int top = generator.getFirstOccupiedHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			int floor = generator.getFirstOccupiedHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
			heights[i] = floor;
			deepest = Math.max(deepest, top - floor);
			if (i == 4 && placement == VKind.Placement.SEAFLOOR && top - floor < 4) {
				return Optional.empty(); // precisa de mar por cima
			}
		}
		if (placement == VKind.Placement.SHORE && deepest > 7) {
			return Optional.empty(); // fundo demais para a beira
		}
		int[] sorted = heights.clone();
		Arrays.sort(sorted);
		int min = sorted[0];
		if (sorted[sorted.length - 2] - min > kind.maxSlope() || min <= context.heightAccessor().getMinY() + 12) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(x, sorted[sorted.length / 2 - 1] + 1, z);
		return stub(kind, variant, seed, origin, turns, mirror);
	}

	/** No Nether: o primeiro chao de verdade abaixo de y 100 com 3 de ar em cima, sem lava perto. */
	private static Optional<Structure.GenerationStub> nether(VKind kind, Structure.GenerationContext context, long seed, int turns, boolean mirror) {
		ChunkPos chunkPos = context.chunkPos();
		int x = chunkPos.getMiddleBlockX();
		int z = chunkPos.getMiddleBlockZ();
		ChunkGenerator generator = context.chunkGenerator();
		int floor = netherFloor(generator.getBaseColumn(x, z, context.heightAccessor(), context.randomState()), generator.getSeaLevel());
		if (floor == Integer.MIN_VALUE) {
			return Optional.empty();
		}
		int variant = VariantRules.variant(kind, biomeAt(context, new BlockPos(x, floor, z)), floor);
		VPlan plan = VanillaRemakePiece.planFor(kind, seed, variant);
		int[] area = plan.surfaceBounds();
		for (int[] c : new int[][] {{area[0], area[1]}, {area[2], area[1]}, {area[0], area[3]}, {area[2], area[3]}}) {
			int[] w = VanillaRemakeStructure.turn(c[0], c[1], turns, mirror);
			int other = netherFloor(generator.getBaseColumn(x + w[0], z + w[1], context.heightAccessor(), context.randomState()), generator.getSeaLevel());
			if (other == Integer.MIN_VALUE || Math.abs(other - floor) > kind.maxSlope()) {
				return Optional.empty();
			}
		}
		return stub(kind, variant, seed, new BlockPos(x, floor + 1, z), turns, mirror);
	}

	private static int netherFloor(NoiseColumn column, int lavaLevel) {
		for (int y = 100; y > lavaLevel + 2; y--) {
			BlockState here = column.getBlock(y);
			if (!here.isAir() && here.getFluidState().isEmpty() && column.getBlock(y + 1).isAir() && column.getBlock(y + 2).isAir()
					&& column.getBlock(y + 3).isAir()) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static Optional<Structure.GenerationStub> stub(VKind kind, int variant, long seed, BlockPos origin, int turns, boolean mirror) {
		return Optional.of(new Structure.GenerationStub(origin, b -> b.addPiece(new VanillaRemakePiece(kind, variant, seed, origin, turns, mirror))));
	}

	private static String biomeAt(Structure.GenerationContext context, BlockPos pos) {
		Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
				QuartPos.fromBlock(pos.getZ()));
		return biome.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
	}
}
