package com.mlin.notas.accessories;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Container (lado servidor) sobre o attachment de acessorios do jogador; qualquer mudanca reaplica os bonus. */
public class AccessoryContainer implements Container {
	private final Player player;

	public AccessoryContainer(Player player) {
		this.player = player;
	}

	@Override
	public int getContainerSize() {
		return AccessorySlots.SIZE;
	}

	@Override
	public boolean isEmpty() {
		return AccessoryData.get(player).stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return AccessoryData.get(player, slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack current = getItem(slot).copy();
		if (current.isEmpty() || count <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = current.split(count);
		setItem(slot, current);
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack current = getItem(slot);
		setItem(slot, ItemStack.EMPTY);
		return current;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		AccessoryData.set(player, slot, stack);
		setChanged();
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public void setChanged() {
		if (player instanceof ServerPlayer serverPlayer) {
			AccessoryServer.refresh(serverPlayer);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return player == this.player && player.isAlive();
	}

	@Override
	public void clearContent() {
		AccessoryData.clear(player);
		setChanged();
	}
}
