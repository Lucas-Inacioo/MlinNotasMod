package com.mlin.notas.ruins;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Blocos de cada {@link RuinStyle} (ponte com o jogo da paleta por bioma). */
public record RuinPalette(BlockState foundation, BlockState wall, BlockState mossy, BlockState cracked, BlockState paving, BlockState ground,
		BlockState log, BlockState planks, BlockState slab, BlockState woodSlab, BlockState fence, BlockState tent, BlockState altar) {

	private static RuinPalette of(Block foundation, Block wall, Block mossy, Block cracked, Block paving, Block ground, Block log, Block planks,
			Block slab, Block woodSlab, Block fence, Block tent, Block altar) {
		return new RuinPalette(foundation.defaultBlockState(), wall.defaultBlockState(), mossy.defaultBlockState(), cracked.defaultBlockState(),
				paving.defaultBlockState(), ground.defaultBlockState(), log.defaultBlockState(), planks.defaultBlockState(), slab.defaultBlockState(),
				woodSlab.defaultBlockState(), fence.defaultBlockState(), tent.defaultBlockState(), altar.defaultBlockState());
	}

	public static RuinPalette of(RuinStyle style) {
		return switch (style) {
			case MOSSY -> of(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS,
					Blocks.DIRT_PATH, Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.COBBLESTONE_SLAB, Blocks.OAK_SLAB, Blocks.OAK_FENCE,
					Blocks.WOOL.white(), Blocks.CHISELED_STONE_BRICKS);
			case SPRUCE -> of(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
					Blocks.DIRT_PATH, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE_SLAB, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE,
					Blocks.WOOL.brown(), Blocks.CHISELED_STONE_BRICKS);
			case SANDSTONE -> of(Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE,
					Blocks.COARSE_DIRT, Blocks.STRIPPED_OAK_LOG, Blocks.OAK_PLANKS, Blocks.SANDSTONE_SLAB, Blocks.OAK_SLAB, Blocks.OAK_FENCE,
					Blocks.WOOL.yellow(), Blocks.CHISELED_SANDSTONE);
			case RED_SANDSTONE -> of(Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
					Blocks.SMOOTH_RED_SANDSTONE, Blocks.COARSE_DIRT, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.RED_SANDSTONE_SLAB,
					Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE, Blocks.WOOL.orange(), Blocks.CHISELED_RED_SANDSTONE);
			case ACACIA -> of(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.TERRACOTTA, Blocks.STONE_BRICKS,
					Blocks.DIRT_PATH, Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.COBBLESTONE_SLAB, Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE,
					Blocks.WOOL.orange(), Blocks.CHISELED_STONE_BRICKS);
			case DARK -> of(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
					Blocks.DIRT_PATH, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.DARK_OAK_SLAB,
					Blocks.DARK_OAK_FENCE, Blocks.WOOL.gray(), Blocks.CHISELED_STONE_BRICKS);
			case DEEPSLATE -> of(Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_DEEPSLATE_BRICKS,
					Blocks.DEEPSLATE_BRICKS, Blocks.GRAVEL, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.SPRUCE_SLAB,
					Blocks.SPRUCE_FENCE, Blocks.WOOL.brown(), Blocks.CHISELED_DEEPSLATE);
		};
	}
}
