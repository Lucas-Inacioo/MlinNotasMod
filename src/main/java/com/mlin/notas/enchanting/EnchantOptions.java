package com.mlin.notas.enchanting;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Lista os encantamentos que podem ser escolhidos para um item. Usada pela tela (cliente, para mostrar)
 * e pelo menu (servidor, para validar), entao os dois lados sempre concordam.
 *
 * So entram encantamentos da tag vanilla {@code in_enchanting_table} (os tesouros, como Remendo,
 * continuam vindo de baus e aldeoes).
 */
public final class EnchantOptions {
	public record Option(Holder<Enchantment> enchantment, int currentLevel, int maxLevel, int anvilCost, boolean compatible) {
		public String name() {
			return enchantment.value().description().getString();
		}
	}

	private EnchantOptions() {
	}

	/** Item aceita encantamento escolhido: livros ou itens encantaveis (mesmo que ja tenham encantamentos). */
	public static boolean canHold(ItemStack stack) {
		return !stack.isEmpty() && (isBook(stack) || stack.has(DataComponents.ENCHANTABLE));
	}

	public static boolean isBook(ItemStack stack) {
		return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
	}

	public static List<Option> forItem(RegistryAccess access, ItemStack stack) {
		List<Option> options = new ArrayList<>();
		if (!canHold(stack)) {
			return options;
		}
		Optional<HolderSet.Named<Enchantment>> table = access.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
		if (table.isEmpty()) {
			return options;
		}
		ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		boolean book = isBook(stack);
		for (Holder<Enchantment> holder : table.get()) {
			Enchantment enchantment = holder.value();
			if (!book && !enchantment.canEnchant(stack)) {
				continue;
			}
			options.add(new Option(holder, existing.getLevel(holder), enchantment.getMaxLevel(), enchantment.getAnvilCost(),
					compatibleWithExisting(existing, holder)));
		}
		options.sort(Comparator.comparing(Option::compatible).reversed().thenComparing(Option::name));
		return options;
	}

	public static Optional<Option> find(RegistryAccess access, ItemStack stack, Holder<Enchantment> enchantment) {
		return forItem(access, stack).stream().filter(o -> o.enchantment().equals(enchantment)).findFirst();
	}

	static boolean compatibleWithExisting(ItemEnchantments existing, Holder<Enchantment> candidate) {
		for (Holder<Enchantment> other : existing.keySet()) {
			if (!other.equals(candidate) && !Enchantment.areCompatible(other, candidate)) {
				return false;
			}
		}
		return true;
	}
}
