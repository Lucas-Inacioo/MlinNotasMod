package com.mlin.notas.backpack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.function.Supplier;

/**
 * Container da mochila: carrega o conteudo do ItemStack e escreve de volta a cada mudanca.
 */
public class BackpackContainer extends SimpleContainer {
	private final Supplier<ItemStack> stack;

	public BackpackContainer(Supplier<ItemStack> stack, int size) {
		super(size);
		this.stack = stack;
		ItemStack current = stack.get();
		if (current.getItem() instanceof BackpackItem) {
			current.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
		}
	}

	/** Grava o conteudo no ItemStack (se ele ainda for uma mochila). */
	public void writeBack() {
		ItemStack current = stack.get();
		if (current.getItem() instanceof BackpackItem) {
			current.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();
		writeBack();
	}

	@Override
	public boolean stillValid(Player player) {
		return stack.get().getItem() instanceof BackpackItem;
	}
}
