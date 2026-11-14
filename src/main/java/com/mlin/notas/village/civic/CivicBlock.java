package com.mlin.notas.village.civic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Bloco de balcao das construcoes civicas (caixa de correio do Correio, pedestal de doacao do Museu e quadro de
 * expedicoes da Guilda): cubo virado para um lado, com a frente decorada. A logica fica em {@link CivicServer}.
 */
public class CivicBlock extends Block {
	public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public enum Kind {
		MAILBOX, PEDESTAL, GUILD_BOARD
	}

	private final Kind kind;

	public CivicBlock(Kind kind, BlockBehaviour.Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
	}

	public Kind kind() {
		return kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			CivicServer.use(kind, serverPlayer, serverLevel, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
