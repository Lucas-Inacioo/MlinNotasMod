package com.mlin.notas.chest;

import com.mlin.notas.registry.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte um bau (vanilla ou grande) no proximo nivel mantendo conteudo, nome e orientacao.
 */
public final class ChestUpgrades {
	private ChestUpgrades() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register(ChestUpgrades::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof ChestUpgradeItem)) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		if (!(state.getBlock() instanceof ChestBlock) && !(state.getBlock() instanceof BigChestBlock)) {
			return InteractionResult.PASS;
		}
		return apply(level, hit.getBlockPos(), player, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/**
	 * Aplica o upgrade da mao no bau em {@code pos}. Devolve true se converteu (e consumiu o item).
	 * No cliente so valida; a conversao acontece no servidor.
	 */
	public static boolean apply(Level level, BlockPos pos, Player player, ItemStack stack) {
		if (!(stack.getItem() instanceof ChestUpgradeItem upgrade)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		ChestTier current = state.getBlock() instanceof BigChestBlock big ? big.tier() : null;
		boolean vanilla = state.getBlock() instanceof ChestBlock && !(state.getBlock() instanceof BigChestBlock);
		if (!vanilla && current == null) {
			return false;
		}
		ChestTier expected = upgrade.target().previous();
		boolean matches = expected == null ? vanilla : expected == current;
		if (!matches) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.chest_upgrade.wrong_chest"));
			return false;
		}
		if (level.isClientSide()) {
			return true;
		}
		if (!convert(level, pos, state, upgrade.target())) {
			return false;
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		player.sendOverlayMessage(Component.translatable("message.mlinnotas.chest_upgrade.applied",
				Component.translatable("block.mlinnotas." + upgrade.target().blockName())));
		return true;
	}

	/** Troca o bloco pelo bau do nivel {@code target}, movendo o conteudo. Servidor apenas. */
	public static boolean convert(Level level, BlockPos pos, BlockState oldState, ChestTier target) {
		List<ItemStack> items = new ArrayList<>();
		Component name = null;
		Direction facing = oldState.hasProperty(BigChestBlock.FACING) ? oldState.getValue(BigChestBlock.FACING) : Direction.NORTH;
		if (level.getBlockEntity(pos) instanceof BigChestBlockEntity big) {
			items.addAll(big.copyItems());
			name = big.getCustomName();
			big.clearContent();
		} else if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			// So a metade clicada de um bau duplo e convertida; a outra vira um bau simples.
			for (int i = 0; i < chest.getContainerSize(); i++) {
				items.add(chest.getItem(i).copy());
			}
			name = chest.getCustomName();
			chest.clearContent();
			if (oldState.hasProperty(ChestBlock.TYPE) && oldState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				Direction connected = ChestBlock.getConnectedDirection(oldState);
				BlockPos otherPos = pos.relative(connected);
				BlockState other = level.getBlockState(otherPos);
				if (other.getBlock() == oldState.getBlock()) {
					level.setBlock(otherPos, other.setValue(ChestBlock.TYPE, ChestType.SINGLE), Block.UPDATE_ALL);
				}
			}
		} else {
			return false;
		}
		List<ItemStack> resized = ContentsTransfer.resize(items, target.size(), ItemStack::isEmpty, ItemStack.EMPTY);
		BlockState newState = ModBlocks.chest(target).defaultBlockState().setValue(BigChestBlock.FACING, facing);
		level.setBlock(pos, newState, Block.UPDATE_ALL);
		if (!(level.getBlockEntity(pos) instanceof BigChestBlockEntity created)) {
			return false;
		}
		NonNullList<ItemStack> list = NonNullList.withSize(target.size(), ItemStack.EMPTY);
		for (int i = 0; i < resized.size(); i++) {
			list.set(i, resized.get(i));
		}
		for (int i = 0; i < list.size(); i++) {
			created.setItem(i, list.get(i));
		}
		created.setCustomName(name);
		created.setChanged();
		return true;
	}
}
