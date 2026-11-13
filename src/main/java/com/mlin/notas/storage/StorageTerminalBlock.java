package com.mlin.notas.storage;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Terminal de Armazenamento: abre a tela com tudo que a rede guarda (busca, ordem, tirar e guardar). A variante de
 * crafting ({@code crafting = true}) tambem tem uma grade 3x3 que se reabastece da rede.
 */
public class StorageTerminalBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	private final boolean crafting;

	public StorageTerminalBlock(Properties properties, boolean crafting) {
		super(properties);
		this.crafting = crafting;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public boolean crafting() {
		return crafting;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel) {
			open(player, pos, crafting);
		}
		return InteractionResult.SUCCESS;
	}

	public static void open(Player player, BlockPos pos, boolean crafting) {
		TerminalMenu.Data data = new TerminalMenu.Data(pos.immutable(), crafting);
		Component title = Component.translatable(crafting ? "block.mlinnotas.crafting_terminal" : "block.mlinnotas.storage_terminal");
		player.openMenu(new ExtendedMenuProvider<TerminalMenu.Data>() {
			@Override
			public TerminalMenu.Data getScreenOpeningData(ServerPlayer serverPlayer) {
				return data;
			}

			@Override
			public Component getDisplayName() {
				return title;
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
				return new TerminalMenu(containerId, inventory, data);
			}
		});
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
