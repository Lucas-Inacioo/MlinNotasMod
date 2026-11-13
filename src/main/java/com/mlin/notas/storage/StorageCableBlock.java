package com.mlin.notas.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Cabo de Armazenamento: liga unidades, terminais e baus numa rede. Encaixa (bracos do modelo) em outros cabos,
 * unidades, terminais e blocos da tag {@code mlinnotas:storage_connectable}.
 */
public class StorageCableBlock extends PipeBlock {
	public StorageCableBlock(Properties properties) {
		super(5.0F, properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
				.setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
	}

	public static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction direction : Direction.values()) {
			state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), StorageRegistry.connectsTo(level.getBlockState(pos.relative(direction))));
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return state.setValue(PROPERTY_BY_DIRECTION.get(directionToNeighbour), StorageRegistry.connectsTo(neighbourState));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}
}
