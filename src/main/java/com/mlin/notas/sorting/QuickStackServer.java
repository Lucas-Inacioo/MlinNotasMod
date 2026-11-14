package com.mlin.notas.sorting;

import com.mlin.notas.backpack.BackpackMenu;
import com.mlin.notas.chest.BigChestMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lado servidor do guardar rapido ({@link QuickStacker}): so em menus de bau (bau/barril/bau do fim, caixa de shulker,
 * baus grandes do mod, mochila) e so com o inventario principal do jogador (slots 9..35; hotbar, armadura e mao
 * secundaria ficam). O pedido e um pacote vazio: o servidor decide tudo pelo menu aberto.
 */
public final class QuickStackServer {
	private QuickStackServer() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(QuickStackPayload.TYPE, (payload, context) -> quickStack(context.player()));
	}

	/** Menus que aceitam o guardar rapido. */
	public static boolean supports(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof BigChestMenu || menu instanceof BackpackMenu;
	}

	/** Guarda e devolve quantos itens foram movidos (0 se o menu nao for de bau). */
	public static int quickStack(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!supports(menu) || !menu.stillValid(player) || player.isSpectator()) {
			return 0;
		}
		// destino: o container inteiro do bau grande (inclusive as linhas fora da janela) ou os slots de conteudo
		List<Target> targets = new ArrayList<>();
		if (menu instanceof BigChestMenu big) {
			Container container = big.getContainer();
			for (int i = 0; i < container.getContainerSize(); i++) {
				targets.add(new Target(container, i, null));
			}
		} else {
			for (Slot slot : menu.slots) {
				if (InventorySortServer.isSortable(slot, player)) {
					targets.add(new Target(slot.container, slot.getContainerSlot(), slot));
				}
			}
		}
		if (targets.isEmpty()) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int locked = menu instanceof BackpackMenu backpack ? backpack.inventorySlot() : -1;
		List<Integer> sources = new ArrayList<>();
		for (int i = InventorySortServer.MAIN_START; i < InventorySortServer.MAIN_END; i++) {
			if (i != locked) {
				sources.add(i);
			}
		}

		List<ItemStack> templates = new ArrayList<>();
		List<SlotItem> box = new ArrayList<>();
		for (Target target : targets) {
			box.add(toItem(target.get(), templates));
		}
		List<SlotItem> inv = new ArrayList<>();
		for (int i : sources) {
			ItemStack stack = inventory.getItem(i);
			// pilha que o slot do container recusa (mochila dentro da mochila) nao entra no plano
			inv.add(acceptsAll(targets, stack) ? toItem(stack, templates) : null);
		}
		QuickStacker.Result result = QuickStacker.plan(box, inv);
		if (result.moved() == 0) {
			return 0;
		}
		for (int i = 0; i < targets.size(); i++) {
			targets.get(i).set(fromItem(result.container().get(i), templates));
		}
		for (int i = 0; i < sources.size(); i++) {
			if (inv.get(i) != null) {
				inventory.setItem(sources.get(i), fromItem(result.player().get(i), templates));
			}
		}
		for (Target target : targets) {
			target.container().setChanged();
		}
		inventory.setChanged();
		menu.broadcastChanges();
		return result.moved();
	}

	private static boolean acceptsAll(List<Target> targets, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		for (Target target : targets) {
			if (target.slot() != null && !target.slot().mayPlace(stack)) {
				return false;
			}
		}
		return true;
	}

	private static SlotItem toItem(ItemStack stack, List<ItemStack> templates) {
		if (stack.isEmpty()) {
			return null;
		}
		int variant = -1;
		for (int i = 0; i < templates.size(); i++) {
			if (ItemStack.isSameItemSameComponents(templates.get(i), stack)) {
				variant = i;
				break;
			}
		}
		if (variant < 0) {
			variant = templates.size();
			templates.add(stack.copyWithCount(1));
		}
		return new SlotItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), Integer.toString(variant), stack.getCount(),
				stack.getMaxStackSize());
	}

	private static ItemStack fromItem(SlotItem item, List<ItemStack> templates) {
		return item == null ? ItemStack.EMPTY : templates.get(Integer.parseInt(item.variant())).copyWithCount(item.count());
	}

	private record Target(Container container, int index, Slot slot) {
		ItemStack get() {
			return container.getItem(index);
		}

		void set(ItemStack stack) {
			container.setItem(index, stack);
		}
	}
}
