package com.mlin.notas.end;

import com.mlin.notas.registry.ModStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Cidadela do End: substitui a End City vanilla (o structure set {@code minecraft:end_cities} aponta para ela).
 * Como traz a propria ilha, nao precisa de terreno: nasce na altura do chao se houver ilha no centro do chunk,
 * ou em y=64 sobre o vazio.
 */
public class CitadelStructure extends Structure {
	public static final MapCodec<CitadelStructure> CODEC = simpleCodec(CitadelStructure::new);

	public CitadelStructure(Structure.StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int x = chunkPos.getBlockX(8);
		int z = chunkPos.getBlockZ(8);
		if (!context.couldValidBiomeExistInTerrainColumn(x, z)) {
			return Optional.empty();
		}
		int ground = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		int y = ground <= context.heightAccessor().getMinY() + 1 ? 64 : Math.clamp(ground, 56, 80);
		BlockPos origin = new BlockPos(x, y, z);
		long seed = context.random().nextLong();
		return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(new CitadelPiece(seed, origin))));
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.CITADEL;
	}
}
