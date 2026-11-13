package com.mlin.notas.waila;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;
import java.util.Optional;

/**
 * Grade de conteudo no tooltip de itens com {@link DataComponents#CONTAINER} (caixa de shulker, mochila).
 * O cliente converte em {@code ContainerTooltipRenderer}.
 */
public record ContainerTooltip(List<ItemGrid.Group<ItemStack>> groups) implements TooltipComponent {
	/** Linhas mostradas no tooltip (a mochila cheia de itens diferentes tem 81). */
	public static final int MAX_ROWS = 9;

	/** Grade do conteudo do item, ou vazio se nao houver nada dentro. */
	public static Optional<TooltipComponent> of(ItemStack stack) {
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) {
			return Optional.empty();
		}
		List<ItemStack> items = contents.nonEmptyItemCopyStream().toList();
		if (items.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new ContainerTooltip(ItemGrid.group(items, ItemStack::isEmpty, ItemStack::isSameItemSameComponents, ItemStack::getCount)));
	}
}
