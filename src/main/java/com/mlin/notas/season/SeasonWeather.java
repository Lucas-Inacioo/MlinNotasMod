package com.mlin.notas.season;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Degelo incremental: roda junto do {@code tickPrecipitation} vanilla (a mesma coluna sorteada, ~1/16 dos chunks por
 * tick), entao nao varre nada. Num bioma temperado onde agora choveria (a temperatura ja tem o deslocamento da
 * estacao), tira uma camada de neve do topo ou derrete o gelo da superficie. Neve natural (lugares frios mesmo sem
 * estacao) nunca derrete porque o deslocamento nunca e positivo.
 */
public final class SeasonWeather {
	private SeasonWeather() {
	}

	public static void melt(ServerLevel level, BlockPos column) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		Biome biome = level.getBiome(top).value();
		if (!SeasonBiomes.temperate(biome) || !biome.warmEnoughToRain(top, level.getSeaLevel())) {
			return;
		}
		BlockState state = level.getBlockState(top);
		if (state.is(Blocks.SNOW)) {
			meltSnow(level, top, state);
			return;
		}
		BlockPos below = top.below();
		BlockState belowState = level.getBlockState(below);
		if (belowState.is(Blocks.SNOW)) {
			meltSnow(level, below, belowState);
		} else if (belowState.is(Blocks.ICE)) {
			level.setBlockAndUpdate(below, Blocks.WATER.defaultBlockState());
		}
	}

	private static void meltSnow(ServerLevel level, BlockPos pos, BlockState state) {
		int layers = state.getValue(SnowLayerBlock.LAYERS);
		if (layers > 1) {
			level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
		} else {
			level.removeBlock(pos, false);
		}
	}
}
