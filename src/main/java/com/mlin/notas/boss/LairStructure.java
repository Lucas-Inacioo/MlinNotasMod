package com.mlin.notas.boss;

import com.mlin.notas.nether.NetherFloor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Covil de chefe (um JSON por tipo, campo {@code kind}). Cada tipo acha o lugar de um jeito:
 * <ul>
 *   <li>Santuario de Cobalto: na superficie, terreno com no maximo 8 blocos de desnivel e sem agua por cima;</li>
 *   <li>Santuario Abissal: cavado abaixo de y=0, na primeira altura (de -40 a -16) em que o bioma e o Deep Dark;</li>
 *   <li>Forja do Colosso: no chao do Nether novo (primeiro ar sobre rocha acima do mar de lava, ver {@link NetherFloor}).</li>
 * </ul>
 */
public class LairStructure extends Structure {
	public static final MapCodec<LairStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.STRING.xmap(LairKind::byName, LairKind::id).fieldOf("kind").forGetter(LairStructure::kind)
	).apply(i, LairStructure::new));

	private static final int[] SANCTUM_HEIGHTS = {-40, -32, -24, -16};

	private final LairKind kind;

	public LairStructure(Structure.StructureSettings settings, LairKind kind) {
		super(settings);
		this.kind = kind;
	}

	public LairKind kind() {
		return kind;
	}

	@Override
	protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int x = chunkPos.getMiddleBlockX();
		int z = chunkPos.getMiddleBlockZ();
		long seed = context.random().nextLong();
		Optional<Integer> y = switch (kind) {
			case COBALT_SHRINE -> surfaceY(context, x, z);
			case ABYSSAL_SANCTUM -> deepDarkY(context, x, z);
			case COLOSSUS_FORGE -> netherY(context, x, z);
		};
		if (y.isEmpty()) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(x, y.get(), z);
		return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(new LairPiece(kind, seed, origin))));
	}

	private static Optional<Integer> surfaceY(Structure.GenerationContext context, int x, int z) {
		ChunkGenerator generator = context.chunkGenerator();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		long sum = 0;
		int[][] samples = {{0, 0}, {11, 0}, {-11, 0}, {0, 11}, {0, -11}, {8, 8}, {-8, -8}};
		for (int[] s : samples) {
			int surface = generator.getFirstOccupiedHeight(x + s[0], z + s[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			int floor = generator.getFirstOccupiedHeight(x + s[0], z + s[1], Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
			if (floor < surface) {
				return Optional.empty();
			}
			min = Math.min(min, surface);
			max = Math.max(max, surface);
			sum += surface;
		}
		if (max - min > 8 || min <= context.heightAccessor().getMinY() + 16) {
			return Optional.empty();
		}
		return Optional.of((int) Math.round((double) sum / samples.length));
	}

	private static Optional<Integer> deepDarkY(Structure.GenerationContext context, int x, int z) {
		for (int y : SANCTUM_HEIGHTS) {
			Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
			if (biome.is(Biomes.DEEP_DARK)) {
				return Optional.of(y);
			}
		}
		return Optional.empty();
	}

	private static Optional<Integer> netherY(Structure.GenerationContext context, int x, int z) {
		int sea = context.chunkGenerator().getSeaLevel();
		int[] floors = {floorAt(context, x, z), floorAt(context, x + 10, z), floorAt(context, x - 10, z), floorAt(context, x, z + 10),
				floorAt(context, x, z - 10)};
		int lakes = 0;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int f : floors) {
			if (f <= sea) {
				lakes++;
			}
			min = Math.min(min, f);
			max = Math.max(max, f);
		}
		if (lakes > 1 || max - min > 12) {
			return Optional.empty();
		}
		// o chao do covil (y=0) fica no topo da rocha
		return Optional.of(NetherFloor.average(floors) - 1);
	}

	private static int floorAt(Structure.GenerationContext context, int x, int z) {
		LevelHeightAccessor height = context.heightAccessor();
		NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, height, context.randomState());
		int minY = height.getMinY();
		int size = height.getHeight();
		boolean[] solid = new boolean[size];
		boolean[] fluid = new boolean[size];
		for (int i = 0; i < size; i++) {
			BlockState state = column.getBlock(minY + i);
			fluid[i] = !state.getFluidState().isEmpty();
			solid[i] = !state.isAir() && !fluid[i];
		}
		return NetherFloor.find(solid, fluid, minY, context.chunkGenerator().getSeaLevel());
	}

	@Override
	public StructureType<?> type() {
		return BossRegistry.LAIR;
	}
}
