package com.mlin.notas.grave;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tela da lapide: usa a janela vanilla de bau grande ({@link MenuType#GENERIC_9x6}, sem codigo de cliente). Cada
 * slot mostra a posicao {@link GraveRules#graveIndexForMenuSlot} (equipamento e acessorios em cima, inventario no
 * meio, barra embaixo). So tira: nada entra na lapide. Ao fechar, lapide vazia some ({@link GraveServer#onMenuClosed}).
 */
public class GraveMenu extends AbstractContainerMenu {
	private final GraveBlockEntity grave;

	public GraveMenu(int containerId, Inventory inventory, GraveBlockEntity grave) {
		super(MenuType.GENERIC_9x6, containerId);
		this.grave = grave;
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 9; col++) {
				int menuSlot = col + row * 9;
				addSlot(new TakeOnlySlot(grave, GraveRules.graveIndexForMenuSlot(menuSlot), 8 + col * 18, 18 + row * 18));
			}
		}
		addStandardInventorySlots(inventory, 8, 18 + 6 * 18 + 13);
	}

	public GraveBlockEntity grave() {
		return grave;
	}

	@Override
	public boolean stillValid(Player player) {
		return grave.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (slotIndex >= GraveRules.SIZE || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack clicked = stack.copy();
		if (!moveItemStackTo(stack, GraveRules.SIZE, slots.size(), true)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return clicked;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		GraveServer.onMenuClosed(player, grave);
	}

	/** Slot que so deixa tirar. */
	static final class TakeOnlySlot extends Slot {
		TakeOnlySlot(GraveBlockEntity grave, int index, int x, int y) {
			super(grave, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
