package com.mlin.notas.accessories;

import com.mlin.notas.MlinNotas;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu dos acessorios: 6 encaixes (2 aneis e colar a esquerda do boneco; cinto, luvas e amuleto a direita) e o
 * inventario do jogador embaixo, no mesmo lugar da tela de inventario vanilla. Shift+clique equipa no primeiro encaixe
 * livre do tipo certo e desequipa para o inventario. O servidor valida tudo pelos slots.
 */
public class AccessoryMenu extends AbstractContainerMenu {
	public static final int WIDTH = 176;
	public static final int HEIGHT = 166;
	/** Posicao (x, y) de cada encaixe na tela, na ordem de {@link AccessorySlots#LAYOUT}. */
	public static final int[][] SLOT_POS = {{8, 17}, {8, 35}, {8, 53}, {80, 17}, {80, 35}, {80, 53}};
	public static final int INVENTORY_TOP = 84;

	private final Container container;

	/** Lado cliente: os itens chegam pela sincronizacao normal do menu. */
	public AccessoryMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(AccessorySlots.SIZE));
	}

	public AccessoryMenu(int containerId, Inventory inventory, Container container) {
		super(AccessoryRegistry.MENU, containerId);
		this.container = container;
		for (int i = 0; i < AccessorySlots.SIZE; i++) {
			addSlot(new AccessorySlot(this, container, i, SLOT_POS[i][0], SLOT_POS[i][1]));
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_TOP);
	}

	/** Definicoes nos encaixes agora (para a regra de unicos). */
	List<AccessoryDef> equippedDefs() {
		List<AccessoryDef> out = new ArrayList<>(AccessorySlots.SIZE);
		for (int i = 0; i < AccessorySlots.SIZE; i++) {
			out.add(AccessoryData.def(slots.get(i).getItem()));
		}
		return out;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int inventoryStart = AccessorySlots.SIZE;
		int hotbarStart = inventoryStart + 27;
		int end = inventoryStart + 36;
		if (index < AccessorySlots.SIZE) {
			if (!moveItemStackTo(stack, inventoryStart, end, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = false;
			AccessoryDef def = AccessoryData.def(stack);
			if (def != null) {
				int free = AccessorySlots.firstFree(equippedDefs(), def);
				if (free >= 0) {
					moved = moveItemStackTo(stack, free, free + 1, false);
				}
			}
			if (!moved) {
				if (index < hotbarStart) {
					if (!moveItemStackTo(stack, hotbarStart, end, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!moveItemStackTo(stack, inventoryStart, hotbarStart, false)) {
					return ItemStack.EMPTY;
				}
			}
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
		return container.stillValid(player);
	}

	/** Encaixe de acessorio: so o tipo certo, uma unidade, artefatos unicos. */
	public static final class AccessorySlot extends Slot {
		private final AccessoryMenu menu;
		private final int accessoryIndex;

		AccessorySlot(AccessoryMenu menu, Container container, int index, int x, int y) {
			super(container, index, x, y);
			this.menu = menu;
			this.accessoryIndex = index;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return AccessorySlots.canPlace(menu.equippedDefs(), accessoryIndex, AccessoryData.def(stack));
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		public AccessorySlotType type() {
			return AccessorySlots.type(accessoryIndex);
		}

		@Override
		public @Nullable Identifier getNoItemIcon() {
			return MlinNotas.id("container/slot/" + type().id());
		}
	}
}
