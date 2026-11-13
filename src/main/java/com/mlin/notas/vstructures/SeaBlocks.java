package com.mlin.notas.vstructures;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Estruturas no mar ({@link VKind.Placement#aquatic()}): o plano usa ar no vazio e a peca decide pela altura real. Abaixo
 * do nivel do mar o ar vira agua e blocos que aceitam ficam alagados; acima dele, plantas que so vivem na agua (alga,
 * capim marinho, coral) nao sao colocadas.
 */
final class SeaBlocks {
	private SeaBlocks() {
	}

	/** Estado a colocar, ou {@code null} para nao colocar nada nessa posicao. */
	static @Nullable BlockState forDepth(BlockState state, boolean underwater) {
		if (underwater) {
			if (state.isAir()) {
				return Blocks.WATER.defaultBlockState();
			}
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				return state.setValue(BlockStateProperties.WATERLOGGED, true);
			}
			return state;
		}
		if (needsWater(state)) {
			return null;
		}
		return state;
	}

	static boolean needsWater(BlockState state) {
		return state.is(Blocks.WATER) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS)
				|| state.is(Blocks.TALL_SEAGRASS) || state.is(BlockTags.CORALS) || state.is(BlockTags.WALL_CORALS);
	}
}
