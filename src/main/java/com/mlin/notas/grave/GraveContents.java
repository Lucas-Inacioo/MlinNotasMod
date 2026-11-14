package com.mlin.notas.grave;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * O que o jogador levava ao morrer, na disposicao da lapide ({@link GraveRules}: inventario, equipamento, acessorios,
 * sobras) + os pontos de XP guardados. Mutavel: os interceptadores do {@link GraveHooks} podem tirar itens.
 */
public final class GraveContents {
	private final NonNullList<ItemStack> items = NonNullList.withSize(GraveRules.SIZE, ItemStack.EMPTY);
	private int xp;

	public ItemStack get(int index) {
		return items.get(index);
	}

	public void set(int index, ItemStack stack) {
		items.set(index, stack);
	}

	/** Tira a pilha da posicao (fica vazia) e devolve. */
	public ItemStack take(int index) {
		ItemStack stack = items.get(index);
		items.set(index, ItemStack.EMPTY);
		return stack;
	}

	/** Poe numa posicao de sobra livre; devolve false se nao coube. */
	public boolean addExtra(ItemStack stack) {
		for (int i = GraveRules.EXTRA_START; i < GraveRules.SIZE; i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack);
				return true;
			}
		}
		return false;
	}

	/** Tira e devolve tudo que casa com o filtro (ex.: interceptador que leva so parte dos itens). */
	public NonNullList<ItemStack> takeMatching(Predicate<ItemStack> filter) {
		NonNullList<ItemStack> out = NonNullList.create();
		for (int i = 0; i < items.size(); i++) {
			if (!items.get(i).isEmpty() && filter.test(items.get(i))) {
				out.add(take(i));
			}
		}
		return out;
	}

	public int xp() {
		return xp;
	}

	public void setXp(int xp) {
		this.xp = Math.max(0, xp);
	}

	public int stackCount() {
		int count = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	public boolean isEmpty() {
		return stackCount() == 0 && xp == 0;
	}

	/** Lista interna (tamanho {@link GraveRules#SIZE}). */
	public NonNullList<ItemStack> items() {
		return items;
	}
}
