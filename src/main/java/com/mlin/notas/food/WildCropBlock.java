package com.mlin.notas.food;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Forma selvagem de uma cultura, nascida no mundo (grama/terra). Nao cresce; quebrar da sementes ou o proprio
 * produto (loot table), que e como o jogador encontra cada cultura.
 */
public class WildCropBlock extends VegetationBlock {
	private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

	public WildCropBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
