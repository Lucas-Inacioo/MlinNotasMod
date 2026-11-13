package com.mlin.notas.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Unidade de Armazenamento: guarda muitos itens de poucos tipos (ver {@link StorageTier}) e faz parte da rede
 * (conduz como um cabo). Botao direito com um item guarda a pilha da mao direto nela; com a mao vazia mostra a
 * ocupacao. Quebrada, leva o conteudo no item (como a caixa de shulker).
 */
public class StorageUnitBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	private final StorageTier tier;

	public StorageUnitBlock(Properties properties, StorageTier tier) {
		super(properties);
		this.tier = tier;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public StorageTier tier() {
		return tier;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof StorageUnitBlockEntity unit)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		// unidades, cabos, terminais e baus na mao: coloca o bloco (montar a rede) em vez de guardar
		if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
				&& StorageRegistry.connectsTo(blockItem.getBlock().defaultBlockState())) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel) {
			ItemKey key = ItemKey.of(stack);
			int moved = (int) unit.cell().insert(key, stack.getCount(), false);
			if (moved > 0) {
				stack.shrink(moved);
				level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F + level.getRandom().nextFloat() * 0.4F);
			}
			player.sendOverlayMessage(moved > 0 ? summary(unit) : Component.translatable("message.mlinnotas.storage.unit_full"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof StorageUnitBlockEntity unit) {
			player.sendOverlayMessage(summary(unit));
		}
		return InteractionResult.SUCCESS;
	}

	/** "3/16 tipos · 1.2k/16k itens". */
	public static Component summary(StorageUnitBlockEntity unit) {
		StorageCell<ItemKey> cell = unit.cell();
		return Component.translatable("message.mlinnotas.storage.unit_summary", cell.typeCount(), cell.maxTypes(),
				CountFormat.compact(cell.totalCount()), CountFormat.compact(unit.tier().capacity()));
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		// no criativo nao ha drop: solta mesmo assim se tiver conteudo, para nao perder (como a caixa de shulker)
		if (!level.isClientSide() && player.preventsBlockDrops() && level.getBlockEntity(pos) instanceof StorageUnitBlockEntity unit
				&& !unit.cell().isEmpty()) {
			ItemStack drop = new ItemStack(this);
			drop.applyComponents(unit.collectComponents());
			ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
			entity.setDefaultPickUpDelay();
			level.addFreshEntity(entity);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		if (level.getBlockEntity(pos) instanceof StorageUnitBlockEntity unit && !unit.cell().isEmpty()) {
			return 1 + (int) Math.floor(unit.cell().fill() * 14.0);
		}
		return 0;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
		return new StorageUnitBlockEntity(worldPosition, blockState);
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
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}
}
