package com.mlin.notas.sorting;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lado servidor do organizador de inventario: recebe o pedido, converte os slots para {@link SlotItem},
 * aplica {@link InventorySorter} e escreve de volta.
 */
public final class InventorySortServer {
	/** Primeiro slot do inventario principal (depois da hotbar). */
	public static final int MAIN_START = Inventory.SELECTION_SIZE;
	/** Fim exclusivo do inventario principal. */
	public static final int MAIN_END = Inventory.INVENTORY_SIZE;

	private InventorySortServer() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(SortRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (payload.container()) {
				sortOpenContainer(player);
			} else {
				sortPlayerInventory(player);
			}
		});
	}

	/** Ordena o inventario principal (slots 9..35) do jogador. */
	public static void sortPlayerInventory(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = MAIN_START; i < MAIN_END; i++) {
			stacks.add(inventory.getItem(i));
		}
		List<ItemStack> sorted = sortStacks(stacks);
		for (int i = 0; i < sorted.size(); i++) {
			inventory.setItem(MAIN_START + i, sorted.get(i));
		}
		player.containerMenu.broadcastChanges();
	}

	/** Ordena os slots do container aberto (bau, mochila...), ignorando o inventario do jogador. */
	public static void sortOpenContainer(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		Map<Container, List<Slot>> byContainer = new LinkedHashMap<>();
		for (Slot slot : menu.slots) {
			if (isSortable(slot, player)) {
				byContainer.computeIfAbsent(slot.container, c -> new ArrayList<>()).add(slot);
			}
		}
		for (List<Slot> slots : byContainer.values()) {
			if (slots.size() < 2) {
				continue;
			}
			List<ItemStack> stacks = new ArrayList<>();
			for (Slot slot : slots) {
				stacks.add(slot.getItem());
			}
			List<ItemStack> sorted = sortStacks(stacks);
			for (int i = 0; i < slots.size(); i++) {
				slots.get(i).set(sorted.get(i));
			}
		}
		menu.broadcastChanges();
	}

	static boolean isSortable(Slot slot, ServerPlayer player) {
		if (slot.getClass() != Slot.class && !(slot instanceof SortableSlot)) {
			return false; // slots especiais (resultado, combustivel, armadura...)
		}
		Container container = slot.container;
		if (container instanceof Inventory || container instanceof CraftingContainer || container instanceof ResultContainer) {
			return false;
		}
		return slot.mayPickup(player);
	}

	/**
	 * Converte pilhas em {@link SlotItem}, ordena e reconstroi as pilhas. Pilhas iguais
	 * (mesmo item e componentes) recebem a mesma variante, entao a fusao e exata.
	 */
	public static List<ItemStack> sortStacks(List<ItemStack> stacks) {
		// variante -> pilha modelo. Pilha sem componentes extras usa variante "" (ordena primeiro);
		// as demais recebem um indice na ordem em que aparecem.
		Map<String, ItemStack> templates = new LinkedHashMap<>();
		List<SlotItem> items = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				items.add(null);
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			String variant = null;
			if (stack.getComponentsPatch().isEmpty()) {
				variant = "";
			} else {
				for (Map.Entry<String, ItemStack> entry : templates.entrySet()) {
					if (ItemStack.isSameItemSameComponents(entry.getValue(), stack)) {
						variant = entry.getKey().substring(id.length() + 1);
						break;
					}
				}
			}
			if (variant == null) {
				variant = String.format("%05d", templates.size() + 1);
			}
			templates.putIfAbsent(variantKey(id, variant), stack.copyWithCount(1));
			items.add(new SlotItem(id, variant, stack.getCount(), stack.getMaxStackSize()));
		}
		List<SlotItem> sorted = InventorySorter.sort(items);
		List<ItemStack> result = new ArrayList<>(sorted.size());
		for (SlotItem item : sorted) {
			if (item == null) {
				result.add(ItemStack.EMPTY);
			} else {
				ItemStack template = templates.get(variantKey(item.id(), item.variant()));
				result.add(template.copyWithCount(item.count()));
			}
		}
		return result;
	}

	private static String variantKey(String id, String variant) {
		return id + "|" + variant;
	}
}
