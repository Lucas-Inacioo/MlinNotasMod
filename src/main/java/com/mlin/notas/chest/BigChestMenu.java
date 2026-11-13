package com.mlin.notas.chest;

import com.mlin.notas.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu generico de grade rows x cols com o inventario do jogador abaixo (centralizado quando a grade e mais larga).
 */
public class BigChestMenu extends AbstractContainerMenu {
	public static final int SLOT_SIZE = 18;
	public static final int GRID_LEFT = 8;
	public static final int GRID_TOP = 18;

	private final ChestTier tier;
	private final Container container;

	/** Construtor usado pelo cliente (container local, sincronizado pelo servidor). */
	public BigChestMenu(ChestTier tier, int containerId, Inventory inventory) {
		this(tier, containerId, inventory, new SimpleContainer(tier.size()));
	}

	public BigChestMenu(ChestTier tier, int containerId, Inventory inventory, Container container) {
		super(ModMenus.forTier(tier), containerId);
		checkContainerSize(container, tier.size());
		this.tier = tier;
		this.container = container;
		container.startOpen(inventory.player);
		for (int row = 0; row < tier.rows(); row++) {
			for (int col = 0; col < tier.cols(); col++) {
				addSlot(new Slot(container, col + row * tier.cols(), GRID_LEFT + col * SLOT_SIZE, GRID_TOP + row * SLOT_SIZE));
			}
		}
		int inventoryTop = GRID_TOP + tier.rows() * SLOT_SIZE + 13;
		addStandardInventorySlots(inventory, playerInventoryLeft(tier), inventoryTop);
	}

	/** Posicao x dos slots do jogador: centralizados na largura da tela. */
	public static int playerInventoryLeft(ChestTier tier) {
		return (tier.screenWidth() - 9 * SLOT_SIZE) / 2 + 1;
	}

	public ChestTier tier() {
		return tier;
	}

	public Container getContainer() {
		return container;
	}

	public int containerSlotCount() {
		return tier.size();
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			int size = tier.size();
			if (slotIndex < size) {
				if (!moveItemStackTo(stack, size, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(stack, 0, size, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return clicked;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}
}
