package com.mlin.notas.storage;

import net.minecraft.world.item.ItemStack;

/**
 * Tipo de item da rede: item + componentes (dois itens iguais com encantamentos diferentes sao tipos diferentes).
 * Guarda uma copia com quantidade 1 que nunca e alterada.
 */
public final class ItemKey {
	private final ItemStack template;
	private final int hash;

	private ItemKey(ItemStack template) {
		this.template = template;
		this.hash = ItemStack.hashItemAndComponents(template);
	}

	public static ItemKey of(ItemStack stack) {
		if (stack.isEmpty()) {
			throw new IllegalArgumentException("pilha vazia");
		}
		return new ItemKey(stack.copyWithCount(1));
	}

	/** Modelo com quantidade 1 (nao altere). */
	public ItemStack template() {
		return template;
	}

	/** Pilha nova desse tipo com a quantidade pedida. */
	public ItemStack toStack(int count) {
		return template.copyWithCount(count);
	}

	public int maxStackSize() {
		return template.getMaxStackSize();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ItemKey key && hash == key.hash && ItemStack.isSameItemSameComponents(template, key.template);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return template.toString();
	}
}
