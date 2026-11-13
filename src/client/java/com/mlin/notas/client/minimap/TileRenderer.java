package com.mlin.notas.client.minimap;

import com.mlin.notas.minimap.MapColorShading;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Converte um chunk em um tile 16x16 de cores ARGB (indice {@code z * 16 + x}).
 * Chamado so no tick do cliente, nunca por frame.
 */
public final class TileRenderer {
	public static final int SIZE = 16;
	/** Cor de coluna vazia (void) ou sem informacao. */
	public static final int EMPTY = 0xFF000000;
	/** Cor de coluna totalmente solida no modo caverna (jogador dentro de rocha). */
	private static final int SOLID = 0xFF101010;
	private static final int MAX_DESCENT_FOR_COLOR = 12;

	private TileRenderer() {
	}

	public static int[] render(ClientLevel level, LevelChunk chunk, boolean cave, int eyeY) {
		int[] tile = new int[SIZE * SIZE];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				tile[z * SIZE + x] = cave
						? caveColumn(level, chunk, pos, baseX + x, baseZ + z, eyeY)
						: surfaceColumn(level, chunk, pos, baseX + x, baseZ + z);
			}
		}
		return tile;
	}

	private static int surfaceColumn(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int bx, int bz) {
		int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, bz & 15) - 1;
		int minY = level.getMinY();
		if (top < minY) {
			return EMPTY;
		}
		pos.set(bx, top, bz);
		BlockState state = chunk.getBlockState(pos);
		if (state.getFluidState().is(FluidTags.WATER)) {
			int depth = 1;
			int y = top - 1;
			while (y >= minY && depth < 64) {
				pos.set(bx, y, bz);
				if (!chunk.getBlockState(pos).getFluidState().is(FluidTags.WATER)) {
					break;
				}
				depth++;
				y--;
			}
			int water = MapColorShading.opaque(MapColor.WATER.col);
			return MapColorShading.waterByDepth(water, depth);
		}
		int color = colorOf(level, chunk, pos, bx, top, bz, minY);
		int north = northHeight(level, chunk, bx, bz, top);
		return MapColorShading.shadeByHeight(color, top, north);
	}

	/** Desce ate achar um bloco com cor de mapa (plantas, vidro e afins nao tem cor). */
	private static int colorOf(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int bx, int startY, int bz, int minY) {
		for (int y = startY, steps = 0; y >= minY && steps < MAX_DESCENT_FOR_COLOR; y--, steps++) {
			pos.set(bx, y, bz);
			BlockState state = chunk.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			MapColor mapColor = state.getMapColor(level, pos);
			if (mapColor != MapColor.NONE) {
				return MapColorShading.opaque(mapColor.col);
			}
		}
		return EMPTY;
	}

	/** Altura da coluna ao norte (z - 1), buscando no chunk vizinho quando carregado. */
	private static int northHeight(ClientLevel level, LevelChunk chunk, int bx, int bz, int fallback) {
		int nz = bz - 1;
		if ((nz >> 4) == chunk.getPos().z()) {
			return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, nz & 15) - 1;
		}
		if (level.hasChunk(bx >> 4, nz >> 4)) {
			return level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, nz) - 1;
		}
		return fallback;
	}

	private static int caveColumn(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int bx, int bz, int eyeY) {
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		int y = Math.max(minY, Math.min(maxY, eyeY));
		pos.set(bx, y, bz);
		// Dentro de um bloco: procura ar um pouco acima (tetos baixos); se nao houver, coluna solida.
		int lift = 0;
		while (!chunk.getBlockState(pos).isAir() && lift < 3 && y < maxY) {
			y++;
			lift++;
			pos.set(bx, y, bz);
		}
		if (!chunk.getBlockState(pos).isAir()) {
			return SOLID;
		}
		int floor = y - 1;
		while (floor >= minY) {
			pos.set(bx, floor, bz);
			BlockState state = chunk.getBlockState(pos);
			if (!state.isAir()) {
				if (state.getFluidState().is(FluidTags.WATER)) {
					return MapColorShading.caveByDepth(MapColorShading.opaque(MapColor.WATER.col), eyeY - floor);
				}
				int color = colorOf(level, chunk, pos, bx, floor, bz, minY);
				return MapColorShading.caveByDepth(color, eyeY - floor);
			}
			floor--;
		}
		return EMPTY;
	}
}
