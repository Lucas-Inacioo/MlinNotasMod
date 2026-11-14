package com.mlin.notas.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;

/**
 * Tina de curtir: 8 espacos usaveis (o nono da tela de 9 fica fechado). Carne podre no espaco cura por
 * {@link CraftsRules#TANNING_TICKS} ticks e vira couro ({@link CraftsRules#tanned}: 1 couro a cada 2 carnes no curtume
 * nivel I, 1 a 1 no II). Fora de um curtume funcionando a tina so guarda. O tempo de cada espaco recomeca quando o item
 * do espaco muda.
 */
public class TanningVatBlockEntity extends BaseContainerBlockEntity {
	public static final int SIZE = 9;
	public static final int CHECK_TICKS = 20;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int[] progress = new int[SIZE];
	private int clock;

	public TanningVatBlockEntity(BlockPos pos, BlockState state) {
		super(CraftsRegistry.TANNING_VAT_ENTITY, pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.mlinnotas.tanning_vat");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot < CraftsRules.VAT_SLOTS;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (!ItemStack.isSameItem(items.get(slot), stack)) {
			progress[slot] = 0;
		}
		super.setItem(slot, stack);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		progress = Arrays.copyOf(input.getIntArray("progress").orElse(new int[SIZE]), SIZE);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		output.putIntArray("progress", progress);
	}

	public int progress(int slot) {
		return progress[slot];
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, TanningVatBlockEntity vat) {
		if (level instanceof ServerLevel serverLevel && ++vat.clock >= CHECK_TICKS) {
			vat.clock = 0;
			int tannery = CraftsServer.levelAt(serverLevel, pos, CraftsBuildings.TANNERY);
			if (tannery >= 1) {
				vat.advance(CHECK_TICKS, tannery);
			}
		}
	}

	/** Avanca a cura de todos os espacos em {@code ticks} com o curtume no nivel {@code level}. */
	public void advance(int ticks, int level) {
		boolean changed = false;
		for (int slot = 0; slot < CraftsRules.VAT_SLOTS; slot++) {
			ItemStack stack = items.get(slot);
			if (!stack.is(Items.ROTTEN_FLESH)) {
				progress[slot] = 0;
				continue;
			}
			progress[slot] += ticks;
			if (progress[slot] >= CraftsRules.TANNING_TICKS) {
				items.set(slot, new ItemStack(Items.LEATHER, CraftsRules.tanned(stack.getCount(), level)));
				progress[slot] = 0;
				changed = true;
			}
		}
		if (changed) {
			setChanged();
		}
	}
}
