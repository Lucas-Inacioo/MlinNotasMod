package com.mlin.notas.storage;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.util.function.BiConsumer;

/**
 * Bau (ou barril, caixa de shulker, bau grande do mod) ligado a rede: adapta os slots de um {@link Container} vanilla
 * para {@link StorageTarget}. Respeita o limite de pilha do item e do container e o {@code canPlaceItem} de cada slot.
 */
public final class ContainerTarget implements StorageTarget<ItemKey> {
	private final Container container;

	public ContainerTarget(Container container) {
		this.container = container;
	}

	public Container container() {
		return container;
	}

	@Override
	public int priority() {
		return 0;
	}

	private boolean accepts(ItemKey key) {
		// caixa de shulker nao aceita outra caixa de shulker (nem itens que nao cabem em itens-container)
		return !(container instanceof ShulkerBoxBlockEntity) || key.template().getItem().canFitInsideContainerItems();
	}

	@Override
	public boolean contains(ItemKey key) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, key.template())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public long insert(ItemKey key, long amount, boolean simulate) {
		if (amount <= 0 || !accepts(key)) {
			return 0;
		}
		int limit = Math.min(key.maxStackSize(), container.getMaxStackSize(key.template()));
		long left = amount;
		boolean changed = false;
		// primeiro completa pilhas iguais, depois ocupa slots vazios
		for (int pass = 0; pass < 2 && left > 0; pass++) {
			for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
				ItemStack stack = container.getItem(i);
				if (pass == 0) {
					if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, key.template())) {
						continue;
					}
					int room = Math.min(limit, stack.getMaxStackSize()) - stack.getCount();
					if (room <= 0) {
						continue;
					}
					int moved = (int) Math.min(room, left);
					if (!simulate) {
						stack.grow(moved);
						changed = true;
					}
					left -= moved;
				} else {
					if (!stack.isEmpty() || !container.canPlaceItem(i, key.template())) {
						continue;
					}
					int moved = (int) Math.min(limit, left);
					if (!simulate) {
						container.setItem(i, key.toStack(moved));
						changed = true;
					}
					left -= moved;
				}
			}
		}
		if (changed) {
			container.setChanged();
		}
		return amount - left;
	}

	@Override
	public long extract(ItemKey key, long amount, boolean simulate) {
		if (amount <= 0) {
			return 0;
		}
		long left = amount;
		boolean changed = false;
		// tira das ultimas pilhas primeiro (as primeiras ficam arrumadas)
		for (int i = container.getContainerSize() - 1; i >= 0 && left > 0; i--) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, key.template())) {
				continue;
			}
			int moved = (int) Math.min(stack.getCount(), left);
			if (!simulate) {
				stack.shrink(moved);
				if (stack.isEmpty()) {
					container.setItem(i, ItemStack.EMPTY);
				}
				changed = true;
			}
			left -= moved;
		}
		if (changed) {
			container.setChanged();
		}
		return amount - left;
	}

	@Override
	public void forEach(BiConsumer<ItemKey, Long> action) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				action.accept(ItemKey.of(stack), (long) stack.getCount());
			}
		}
	}
}
