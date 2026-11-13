package com.mlin.notas.waila;

import com.mlin.notas.backpack.BackpackItem;
import com.mlin.notas.furnace.FurnaceUpgrades;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lado servidor do HUD WAILA: responde o conteudo de um container que o jogador esta mirando, so se ele estiver
 * a ate {@link WailaRules#MAX_DISTANCE} blocos e puder abrir o container (nao espectador, sem tranca, sem loot
 * ainda nao gerado).
 */
public final class WailaServer {
	private WailaServer() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ContainerPeekRequestPayload.TYPE, ContainerPeekRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ContainerPeekPayload.TYPE, ContainerPeekPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ContainerPeekRequestPayload.TYPE, (payload, context) ->
				peek(context.player(), payload.pos()).ifPresent(answer -> ServerPlayNetworking.send(context.player(), answer)));
		// Caixa de shulker e mochila mostram a grade no tooltip (ContainerTooltip): esconde a lista de texto vanilla repetida.
		DefaultItemComponentEvents.MODIFY.register(context -> {
			for (Item item : BuiltInRegistries.ITEM) {
				if (item instanceof BackpackItem || item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
					context.modify(item, builder -> {
						TooltipDisplay display = builder.get(DataComponents.TOOLTIP_DISPLAY);
						builder.set(DataComponents.TOOLTIP_DISPLAY,
								(display == null ? TooltipDisplay.DEFAULT : display).withHidden(DataComponents.CONTAINER, true));
					});
				}
			}
		});
	}

	/** Resposta para o pedido, ou vazio se recusado (longe, espectador, trancado, nao e container, loot pendente). */
	public static Optional<ContainerPeekPayload> peek(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		if (player.isSpectator() || !level.isLoaded(pos)
				|| !WailaRules.withinReach(player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)))) {
			return Optional.empty();
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof com.mlin.notas.storage.StorageUnitBlockEntity unit) {
			return Optional.of(com.mlin.notas.storage.StorageWaila.peek(pos, unit));
		}
		if (!(blockEntity instanceof BaseContainerBlockEntity containerEntity) || !containerEntity.canOpen(player)) {
			return Optional.empty();
		}
		if (hasPendingLoot(blockEntity)) {
			return Optional.empty();
		}
		Container container = containerEntity;
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock chestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			if (hasPendingLoot(level.getBlockEntity(other))) {
				return Optional.empty();
			}
			Container combined = ChestBlock.getContainer(chestBlock, state, level, pos, true);
			if (combined != null) {
				container = combined;
			}
		}
		List<ItemStack> stacks = new ArrayList<>(container.getContainerSize());
		for (int i = 0; i < container.getContainerSize(); i++) {
			stacks.add(container.getItem(i));
		}
		List<ItemGrid.Group<ItemStack>> groups = ItemGrid.group(stacks, ItemStack::isEmpty, ItemStack::isSameItemSameComponents, ItemStack::getCount);
		int sent = Math.min(groups.size(), ContainerPeekPayload.MAX_GROUPS);
		List<ItemStack> items = new ArrayList<>(sent);
		List<Integer> counts = new ArrayList<>(sent);
		for (int i = 0; i < sent; i++) {
			items.add(groups.get(i).sample().copyWithCount(1));
			counts.add(groups.get(i).count());
		}
		int furnaceTier = blockEntity instanceof AbstractFurnaceBlockEntity ? FurnaceUpgrades.tierOf(blockEntity).ordinal() : -1;
		return Optional.of(new ContainerPeekPayload(pos.immutable(), items, counts, groups.size() - sent, furnaceTier));
	}

	/** Container com tabela de loot ainda nao aberta: ler o conteudo geraria o loot, entao nao revela. */
	private static boolean hasPendingLoot(BlockEntity blockEntity) {
		return blockEntity instanceof RandomizableContainer randomizable && randomizable.getLootTable() != null;
	}
}
