package com.mlin.notas.food;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Menu da panela: grade 3x2 de ingredientes, espaco do recipiente, saida e inventario do jogador. Dados: progresso,
 * tempo total e se ha calor.
 */
public class CookingPotMenu extends AbstractContainerMenu {
	public static final int GRID_X = 30;
	public static final int GRID_Y = 17;
	public static final int CONTAINER_X = 124;
	public static final int CONTAINER_Y = 55;
	public static final int OUTPUT_X = 124;
	public static final int OUTPUT_Y = 26;
	public static final int INVENTORY_Y = 84;

	private final Container pot;
	private final ContainerData data;

	/** Lado do cliente. */
	public CookingPotMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(CookingPotBlockEntity.SIZE), new SimpleContainerData(3));
	}

	public CookingPotMenu(int containerId, Inventory inventory, Container pot, ContainerData data) {
		super(FoodRegistry.COOKING_POT_MENU, containerId);
		checkContainerSize(pot, CookingPotBlockEntity.SIZE);
		this.pot = pot;
		this.data = data;
		pot.startOpen(inventory.player);
		for (int i = 0; i < CookingPotBlockEntity.INGREDIENTS; i++) {
			addSlot(new Slot(pot, i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18));
		}
		addSlot(new Slot(pot, CookingPotBlockEntity.CONTAINER_SLOT, CONTAINER_X, CONTAINER_Y));
		addSlot(new Slot(pot, CookingPotBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		addDataSlots(data);
	}

	public float progress() {
		int total = data.get(1);
		return total <= 0 ? 0.0F : Math.min(1.0F, data.get(0) / (float) total);
	}

	public boolean heated() {
		return data.get(2) != 0;
	}

	/** Tigelas e garrafas vao para o espaco do recipiente no Shift+clique. */
	static boolean isContainerItem(ItemStack stack) {
		return stack.is(Items.BOWL) || stack.is(Items.GLASS_BOTTLE);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int potEnd = CookingPotBlockEntity.SIZE;
		int inventoryEnd = slots.size();
		if (index < potEnd) {
			if (!moveItemStackTo(stack, potEnd, inventoryEnd, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else if (isContainerItem(stack)) {
			if (!moveItemStackTo(stack, CookingPotBlockEntity.CONTAINER_SLOT, CookingPotBlockEntity.CONTAINER_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 0, CookingPotBlockEntity.INGREDIENTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return pot.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		pot.stopOpen(player);
	}
}
