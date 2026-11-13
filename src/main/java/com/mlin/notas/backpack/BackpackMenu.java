package com.mlin.notas.backpack;

import com.mlin.notas.registry.ModMenus;
import com.mlin.notas.sorting.SortableSlot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Menu da mochila: grade do conteudo (9 x rows), opcionalmente uma grade de crafting 3x3 a direita,
 * e o inventario do jogador abaixo. O slot do inventario que contem a mochila aberta fica travado.
 */
public class BackpackMenu extends AbstractContainerMenu {
	public static final int SLOT_SIZE = 18;
	public static final int GRID_LEFT = 8;
	public static final int GRID_TOP = 18;
	public static final int BASE_WIDTH = 176;
	/** Largura extra quando ha grade de crafting. */
	public static final int CRAFTING_EXTRA_WIDTH = 100;
	public static final int CRAFT_GRID_LEFT = BASE_WIDTH + 4;
	public static final int RESULT_LEFT = CRAFT_GRID_LEFT + 3 * SLOT_SIZE + 22;

	/**
	 * Dados sincronizados ao abrir.
	 *
	 * @param inventorySlot indice no inventario do jogador (0..35 ou 40 = mao secundaria)
	 * @param upgrades      upgrades da mochila no momento da abertura
	 */
	public record Data(int inventorySlot, BackpackUpgrades upgrades) {
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Data::inventorySlot,
				BackpackCodecs.UPGRADES_STREAM_CODEC, Data::upgrades,
				Data::new);
	}

	private final Player player;
	private final int inventorySlot;
	private final BackpackUpgrades upgrades;
	private final BackpackContainer container;
	private final @Nullable CraftingContainer craftSlots;
	private final @Nullable ResultContainer resultSlots;
	private final int resultSlotIndex;

	public BackpackMenu(int containerId, Inventory inventory, Data data) {
		super(ModMenus.BACKPACK, containerId);
		this.player = inventory.player;
		this.inventorySlot = data.inventorySlot();
		this.upgrades = data.upgrades();
		this.container = new BackpackContainer(() -> inventory.getItem(inventorySlot), upgrades.slotCount());

		int rows = upgrades.rows();
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new ContentSlot(container, col + row * 9, GRID_LEFT + col * SLOT_SIZE, GRID_TOP + row * SLOT_SIZE));
			}
		}

		int inventoryTop = GRID_TOP + rows * SLOT_SIZE + 13;
		addStandardInventorySlots(inventory, GRID_LEFT, inventoryTop);
		int lockedIndex = -1;
		for (Slot slot : slots) {
			if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
				lockedIndex = slot.index;
			}
		}
		if (lockedIndex >= 0) {
			Slot original = slots.get(lockedIndex);
			slots.set(lockedIndex, new LockedSlot(inventory, inventorySlot, original.x, original.y, lockedIndex));
		}

		if (upgrades.crafting()) {
			craftSlots = new TransientCraftingContainer(this, 3, 3);
			resultSlots = new ResultContainer();
			int top = GRID_TOP + Math.max(0, (rows * SLOT_SIZE - 3 * SLOT_SIZE) / 2);
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					addSlot(new Slot(craftSlots, col + row * 3, CRAFT_GRID_LEFT + col * SLOT_SIZE, top + row * SLOT_SIZE));
				}
			}
			Slot result = addSlot(new ResultSlot(player, craftSlots, resultSlots, 0, RESULT_LEFT, top + SLOT_SIZE));
			resultSlotIndex = result.index;
		} else {
			craftSlots = null;
			resultSlots = null;
			resultSlotIndex = -1;
		}
	}

	public BackpackUpgrades upgrades() {
		return upgrades;
	}

	public int inventorySlot() {
		return inventorySlot;
	}

	public BackpackContainer getContainer() {
		return container;
	}

	public int contentSlotCount() {
		return upgrades.slotCount();
	}

	public boolean hasCrafting() {
		return craftSlots != null;
	}

	public int imageWidth() {
		return BASE_WIDTH + (hasCrafting() ? CRAFTING_EXTRA_WIDTH : 0);
	}

	/** Slot do conteudo: nao aceita outra mochila. */
	private static final class ContentSlot extends Slot implements SortableSlot {
		ContentSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !(stack.getItem() instanceof BackpackItem);
		}
	}

	/** Slot do inventario onde esta a mochila aberta: nao pode ser mexido. */
	private static final class LockedSlot extends Slot {
		LockedSlot(Container container, int slot, int x, int y, int index) {
			super(container, slot, x, y);
			this.index = index;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public void slotsChanged(Container changed) {
		super.slotsChanged(changed);
		if (craftSlots != null && changed == craftSlots) {
			updateCraftingResult();
		}
	}

	private void updateCraftingResult() {
		if (craftSlots == null || resultSlots == null || !(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		CraftingInput input = craftSlots.asCraftInput();
		ItemStack result = ItemStack.EMPTY;
		Optional<RecipeHolder<CraftingRecipe>> maybe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		if (maybe.isPresent()) {
			RecipeHolder<CraftingRecipe> holder = maybe.get();
			if (resultSlots.setRecipeUsed(serverPlayer, holder)) {
				ItemStack assembled = holder.value().assemble(input);
				if (assembled.isItemEnabled(level.enabledFeatures())) {
					result = assembled;
				}
			}
		}
		resultSlots.setItem(0, result);
		setRemoteSlot(resultSlotIndex, result);
		serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), resultSlotIndex, result));
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return (resultSlots == null || target.container != resultSlots) && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);
		if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		clicked = stack.copy();
		int content = contentSlotCount();
		int playerStart = content;
		int playerEnd = content + 36;
		if (slotIndex == resultSlotIndex) {
			stack.getItem().onCraftedBy(stack, player);
			if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, clicked);
		} else if (slotIndex < content) {
			if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex < playerEnd) {
			if (stack.getItem() instanceof BackpackItem) {
				return ItemStack.EMPTY;
			}
			if (!moveItemStackTo(stack, 0, content, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, playerStart, playerEnd, false)) {
			// grade de crafting -> inventario do jogador
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == clicked.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return clicked;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.writeBack();
		if (craftSlots != null) {
			clearContainer(player, craftSlots);
		}
	}
}
