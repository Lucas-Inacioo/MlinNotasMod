package com.mlin.notas.chest;

import com.mlin.notas.registry.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu do bau grande: uma janela de ate {@link ChestTier#MAX_VISIBLE_ROWS} linhas x 9 colunas sobre o conteudo
 * inteiro, com o inventario do jogador abaixo. Rolar muda qual linha do bau aparece no topo da janela
 * ({@link #scrollRow()}); os slots da janela apontam para o indice deslocado do container.
 *
 * <p>Rolagem: a tela muda a linha localmente e manda o numero da linha como clique de botao
 * ({@link #clickMenuButton}); o servidor muda a mesma linha e reenvia o conteudo da janela.</p>
 */
public class BigChestMenu extends AbstractContainerMenu {
	public static final int SLOT_SIZE = 18;
	public static final int GRID_LEFT = 8;
	public static final int GRID_TOP = 18;

	private final ChestTier tier;
	private final Container container;
	private final int windowSlots;
	private int scrollRow;

	/** Construtor usado pelo cliente (container local, sincronizado pelo servidor). */
	public BigChestMenu(ChestTier tier, int containerId, Inventory inventory) {
		this(tier, containerId, inventory, new SimpleContainer(tier.size()));
	}

	public BigChestMenu(ChestTier tier, int containerId, Inventory inventory, Container container) {
		super(ModMenus.forTier(tier), containerId);
		checkContainerSize(container, tier.size());
		this.tier = tier;
		this.container = container;
		this.windowSlots = tier.visibleRows() * tier.cols();
		container.startOpen(inventory.player);
		for (int row = 0; row < tier.visibleRows(); row++) {
			for (int col = 0; col < tier.cols(); col++) {
				addSlot(new WindowSlot(container, col + row * tier.cols(), GRID_LEFT + col * SLOT_SIZE, GRID_TOP + row * SLOT_SIZE));
			}
		}
		addStandardInventorySlots(inventory, playerInventoryLeft(tier), inventoryTop(tier));
	}

	/** Posicao x dos slots do jogador (alinhados com a grade). */
	public static int playerInventoryLeft(ChestTier tier) {
		return GRID_LEFT;
	}

	/** Posicao y do primeiro slot do inventario do jogador. */
	public static int inventoryTop(ChestTier tier) {
		return GRID_TOP + tier.visibleRows() * SLOT_SIZE + 13;
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

	/** Slots da janela visivel (os primeiros do menu; depois vem o inventario do jogador). */
	public int windowSlotCount() {
		return windowSlots;
	}

	/** Linha do bau mostrada no topo da janela. */
	public int scrollRow() {
		return scrollRow;
	}

	/** Muda a linha do topo (limitada ao intervalo valido). Devolve true se mudou. */
	public boolean setScrollRow(int row) {
		int clamped = tier.clampScroll(row, 0);
		if (clamped == scrollRow) {
			return false;
		}
		scrollRow = clamped;
		return true;
	}

	/** O botao e a linha desejada. No servidor reenvia a janela inteira (os slots agora mostram outros itens). */
	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId < 0 || buttonId > tier.maxScroll()) {
			return false;
		}
		setScrollRow(buttonId);
		if (player instanceof ServerPlayer) {
			sendAllDataToRemote();
		}
		return true;
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
			if (slotIndex < windowSlots) {
				if (!moveItemStackTo(stack, windowSlots, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveIntoContainer(stack)) {
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

	/**
	 * Shift+clique do inventario: guarda no bau inteiro (nao so na parte visivel), primeiro completando
	 * pilhas iguais e depois nos espacos vazios. Devolve true se moveu alguma coisa.
	 */
	private boolean moveIntoContainer(ItemStack stack) {
		int before = stack.getCount();
		for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack existing = container.getItem(i);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				int room = Math.min(container.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount();
				if (room > 0) {
					int moved = Math.min(room, stack.getCount());
					existing.grow(moved);
					stack.shrink(moved);
					container.setChanged();
				}
			}
		}
		for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
			if (container.getItem(i).isEmpty() && container.canPlaceItem(i, stack)) {
				container.setItem(i, stack.split(Math.min(stack.getCount(), container.getMaxStackSize(stack))));
				container.setChanged();
			}
		}
		return stack.getCount() != before;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}

	/** Slot da janela: o indice no container e o da posicao na janela mais a rolagem. */
	private class WindowSlot extends Slot {
		WindowSlot(Container container, int windowIndex, int x, int y) {
			super(container, windowIndex, x, y);
		}

		private int index() {
			return super.getContainerSlot() + scrollRow * tier.cols();
		}

		@Override
		public int getContainerSlot() {
			return index();
		}

		@Override
		public ItemStack getItem() {
			return container.getItem(index());
		}

		@Override
		public void set(ItemStack itemStack) {
			container.setItem(index(), itemStack);
			setChanged();
		}

		@Override
		public ItemStack remove(int amount) {
			return container.removeItem(index(), amount);
		}
	}
}
