package com.mlin.notas.ruins;

import com.mlin.notas.registry.ModStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Ruina de superficie (um tipo por JSON, campo {@code kind}). Nasce no meio do chunk, na altura do ponto mais alto
 * do terreno sob ela; desiste em agua ou se o terreno for inclinado demais. A paleta vem do bioma.
 */
public class RuinStructure extends Structure {
	public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.STRING.xmap(RuinKind::byName, RuinKind::serializedName).fieldOf("kind").forGetter(RuinStructure::kind)
	).apply(i, RuinStructure::new));

	private final RuinKind kind;

	public RuinStructure(Structure.StructureSettings settings, RuinKind kind) {
		super(settings);
		this.kind = kind;
	}

	public RuinKind kind() {
		return kind;
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int x = chunkPos.getMiddleBlockX();
		int z = chunkPos.getMiddleBlockZ();
		long seed = context.random().nextLong();
		RuinPlan plan = RuinPlanner.plan(kind, seed);
		int[] area = plan.surfaceBounds();
		ChunkGenerator generator = context.chunkGenerator();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int[][] samples = {{area[0], area[1]}, {area[2], area[1]}, {area[0], area[3]}, {area[2], area[3]}, {0, 0},
				{(area[0] + area[2]) / 2, (area[1] + area[3]) / 2}};
		for (int[] s : samples) {
			int surface = generator.getFirstOccupiedHeight(x + s[0], z + s[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			int floor = generator.getFirstOccupiedHeight(x + s[0], z + s[1], Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
			if (floor < surface) {
				return Optional.empty(); // agua por cima
			}
			min = Math.min(min, surface);
			max = Math.max(max, surface);
		}
		if (max - min > kind.maxSlope() || min <= context.heightAccessor().getMinY() + 16) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(x, max + 1, z);
		Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(origin.getY()), QuartPos.fromBlock(z));
		RuinStyle style = RuinStyle.forBiome(biome.unwrapKey().map(k -> k.identifier().toString()).orElse(null));
		return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(new RuinPiece(kind, style, seed, origin))));
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.RUIN;
	}
}
