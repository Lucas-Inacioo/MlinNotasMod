package com.mlin.notas.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Masmorra media (um JSON por tema, campo {@code theme}). O predio da entrada nasce no meio do chunk, na altura do
 * ponto mais alto do terreno sob ele; desiste em agua ou terreno inclinado. Os andares ficam 16+ blocos abaixo; se o
 * terreno em cima de alguma sala do primeiro andar for baixo demais (vale, penhasco) a masmorra ficaria exposta e o
 * lugar e recusado, menos no templo afogado (a carcaca pode aparecer no fundo do mar).
 */
public class DungeonStructure extends Structure {
	public static final MapCodec<DungeonStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.STRING.xmap(DungeonTheme::byId, DungeonTheme::id).fieldOf("theme").forGetter(DungeonStructure::theme)
	).apply(i, DungeonStructure::new));

	private final DungeonTheme theme;

	public DungeonStructure(Structure.StructureSettings settings, DungeonTheme theme) {
		super(settings);
		this.theme = theme;
	}

	public DungeonTheme theme() {
		return theme;
	}

	@Override
	protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int x = chunkPos.getMiddleBlockX();
		int z = chunkPos.getMiddleBlockZ();
		long seed = context.random().nextLong();
		ChunkGenerator generator = context.chunkGenerator();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int[][] samples = {{-6, -8}, {6, -8}, {-6, 9}, {6, 9}, {0, 0}, {0, -8}, {0, 9}, {-6, 0}, {6, 0}};
		for (int[] s : samples) {
			int surface = height(context, x + s[0], z + s[1], Heightmap.Types.WORLD_SURFACE_WG);
			int floor = height(context, x + s[0], z + s[1], Heightmap.Types.OCEAN_FLOOR_WG);
			if (floor < surface) {
				return Optional.empty(); // agua por cima do predio
			}
			min = Math.min(min, surface);
			max = Math.max(max, surface);
		}
		if (max - min > theme.maxSlope()) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(x, max + 1, z);
		DungeonPlan plan = DungeonPlanner.plan(theme, seed);
		int[] b = plan.bounds();
		if (origin.getY() + b[1] <= context.heightAccessor().getMinY() + 4) {
			return Optional.empty();
		}
		if (!theme.allowsExposedShell()) {
			for (DungeonPlan.Room room : plan.rooms()) {
				if (room.floor() != 0) {
					continue;
				}
				int ceiling = origin.getY() + room.fy() + room.h() + 3;
				int[][] points = {{room.x0(), room.z0()}, {room.x1(), room.z1()}, {room.x0(), room.z1()}, {room.x1(), room.z0()},
						{room.cx(), room.cz()}};
				for (int[] p : points) {
					if (height(context, x + p[0], z + p[1], Heightmap.Types.OCEAN_FLOOR_WG) < ceiling) {
						return Optional.empty();
					}
				}
			}
		}
		return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(new DungeonPiece(theme, seed, origin, plan))));
	}

	private static int height(Structure.GenerationContext context, int x, int z, Heightmap.Types type) {
		return context.chunkGenerator().getFirstOccupiedHeight(x, z, type, context.heightAccessor(), context.randomState());
	}

	@Override
	public StructureType<?> type() {
		return DungeonRegistry.DUNGEON;
	}
}
