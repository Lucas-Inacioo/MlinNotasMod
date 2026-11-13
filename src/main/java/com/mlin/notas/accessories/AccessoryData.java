package com.mlin.notas.accessories;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Acessorios equipados de um jogador: attachment persistente {@code mlinnotas:accessories} (lista de
 * {@link AccessorySlots#SIZE} pilhas), sincronizado so com o proprio jogador (a tela, o pulo duplo e o teto no tooltip
 * leem do cliente). Copiado ao renascer: sem keepInventory o servidor ja dropou e esvaziou tudo na morte.
 */
public final class AccessoryData {
	public static final AttachmentType<List<ItemStack>> EQUIPPED = AttachmentRegistry.create(MlinNotas.id("accessories"),
			builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath()
					.syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private AccessoryData() {
	}

	/** Copia da lista (sempre com {@link AccessorySlots#SIZE} posicoes; vazio = {@link ItemStack#EMPTY}). */
	public static List<ItemStack> get(Player player) {
		List<ItemStack> stored = player.getAttached(EQUIPPED);
		List<ItemStack> out = new ArrayList<>(AccessorySlots.SIZE);
		for (int i = 0; i < AccessorySlots.SIZE; i++) {
			out.add(stored != null && i < stored.size() && stored.get(i) != null ? stored.get(i) : ItemStack.EMPTY);
		}
		return out;
	}

	public static ItemStack get(Player player, int index) {
		return get(player).get(index);
	}

	public static void set(Player player, int index, ItemStack stack) {
		List<ItemStack> list = get(player);
		list.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		player.setAttached(EQUIPPED, List.copyOf(list));
	}

	public static void clear(Player player) {
		player.removeAttached(EQUIPPED);
	}

	/** Definicoes equipadas na ordem dos encaixes (null = vazio ou item que nao e acessorio). */
	public static List<AccessoryDef> defs(Player player) {
		List<AccessoryDef> out = new ArrayList<>(AccessorySlots.SIZE);
		for (ItemStack stack : get(player)) {
			out.add(def(stack));
		}
		return out;
	}

	/** Definicao do acessorio da pilha, ou null. */
	public static AccessoryDef def(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof AccessoryItem)) {
			return null;
		}
		return AccessoryCatalog.byId(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
	}
}
