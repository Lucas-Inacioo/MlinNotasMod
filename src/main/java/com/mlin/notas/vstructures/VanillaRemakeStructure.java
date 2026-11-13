package com.mlin.notas.vstructures;

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
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Arrays;
import java.util.Optional;

/**
 * Estrutura vanilla refeita (campo {@code kind}: {@link VKind}). Na superficie nasce no meio do chunk, numa altura
 * tirada do terreno sob ela, e desiste em agua ou terreno inclinado demais; no subterraneo escolhe uma altura abaixo
 * do chao. A variante (paleta) vem do bioma ({@link VariantRules}).
 */
public class VanillaRemakeStructure extends Structure {
	public static final MapCodec<VanillaRemakeStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.STRING.xmap(VKind::byName, VKind::serializedName).fieldOf("kind").forGetter(VanillaRemakeStructure::kind)
	).apply(i, VanillaRemakeStructure::new));

	private final VKind kind;

	public VanillaRemakeStructure(Structure.StructureSettings settings, VKind kind) {
		super(settings);
		this.kind = kind;
	}

	public VKind kind() {
		return kind;
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		if (kind.placement() != VKind.Placement.LAND && kind.placement() != VKind.Placement.UNDERGROUND) {
			return SeaPlacement.find(kind, context);
		}
		ChunkPos chunkPos = context.chunkPos();
		int x = chunkPos.getMiddleBlockX();
		int z = chunkPos.getMiddleBlockZ();
		WorldgenRandom random = context.random();
		long seed = random.nextLong();
		int turns = random.nextInt(4);
		boolean mirror = random.nextBoolean();
		ChunkGenerator generator = context.chunkGenerator();
		int surface = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		if (kind.underground()) {
			int y;
			if (kind == VKind.MINESHAFT_MESA) {
				y = Math.clamp(surface - 14L, 48, 100);
			} else {
				int top = Math.min(surface - 24, 36);
				int bottom = context.heightAccessor().getMinY() + 22;
				if (top <= bottom) {
					return Optional.empty();
				}
				y = bottom + random.nextInt(top - bottom + 1);
			}
			BlockPos origin = new BlockPos(x, y, z);
			int variant = VariantRules.variant(kind, biomeAt(context, origin), y);
			return Optional.of(new Structure.GenerationStub(origin, b -> b.addPiece(new VanillaRemakePiece(kind, variant, seed, origin, turns, mirror))));
		}
		int variant = VariantRules.variant(kind, biomeAt(context, new BlockPos(x, surface, z)), surface);
		VPlan plan = VanillaRemakePiece.planFor(kind, seed, variant);
		int[] area = plan.surfaceBounds();
		int midX = (area[0] + area[2]) / 2;
		int midZ = (area[1] + area[3]) / 2;
		int[][] samples = {{area[0], area[1]}, {area[2], area[1]}, {area[0], area[3]}, {area[2], area[3]}, {0, 0}, {midX, midZ},
				{midX, area[1]}, {midX, area[3]}, {area[0], midZ}, {area[2], midZ}};
		int[] heights = new int[samples.length];
		for (int i = 0; i < samples.length; i++) {
			int[] w = turn(samples[i][0], samples[i][1], turns, mirror);
			int sx = x + w[0];
			int sz = z + w[1];
			int top = generator.getFirstOccupiedHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			int floor = generator.getFirstOccupiedHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
			if (floor < top) {
				return Optional.empty(); // agua por cima
			}
			heights[i] = top;
		}
		int[] sorted = heights.clone();
		Arrays.sort(sorted);
		int min = sorted[0];
		int max = sorted[sorted.length - 1];
		// o desnivel conta sem o ponto mais alto (um morrinho isolado nao impede)
		if (sorted[sorted.length - 2] - min > kind.maxSlope() || min <= context.heightAccessor().getMinY() + 24) {
			return Optional.empty();
		}
		int y = switch (kind) {
			case DESERT_PYRAMID -> min + (max - min) / 3;
			default -> sorted[sorted.length / 2];
		};
		BlockPos origin = new BlockPos(x, y + 1, z);
		return Optional.of(new Structure.GenerationStub(origin, b -> b.addPiece(new VanillaRemakePiece(kind, variant, seed, origin, turns, mirror))));
	}

	/** Mesmo giro/espelho da peca. */
	static int[] turn(int x, int z, int turns, boolean mirror) {
		int px = mirror ? -x : x;
		int pz = z;
		for (int i = 0; i < turns; i++) {
			int nx = -pz;
			pz = px;
			px = nx;
		}
		return new int[] {px, pz};
	}

	private static String biomeAt(Structure.GenerationContext context, BlockPos pos) {
		Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
				QuartPos.fromBlock(pos.getZ()));
		return biome.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.VANILLA_REMAKE;
	}
}
