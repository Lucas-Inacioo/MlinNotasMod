package com.mlin.notas.end;

import com.mlin.notas.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Planta pequena do End (lirio do vazio, cogumelo do End): cresce sobre musgo do End, pedra do End, cinza do vazio
 * e basalto do vazio.
 */
public class EndPlantBlock extends VegetationBlock {
	private final VoxelShape shape;

	public EndPlantBlock(VoxelShape shape, Properties properties) {
		super(properties);
		this.shape = shape;
	}

	public static EndPlantBlock lily(Properties properties) {
		return new EndPlantBlock(Block.column(6.0, 0.0, 12.0), properties);
	}

	public static EndPlantBlock mushroom(Properties properties) {
		return new EndPlantBlock(Block.column(8.0, 0.0, 9.0), properties);
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(ModBlocks.END_MOSS) || state.is(Blocks.END_STONE) || state.is(ModBlocks.VOID_ASH) || state.is(ModBlocks.VOID_BASALT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}
}
