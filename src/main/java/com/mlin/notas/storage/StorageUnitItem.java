package com.mlin.notas.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** Item da Unidade de Armazenamento: tooltip com a capacidade do nivel e, se tiver conteudo, um resumo dele. */
public class StorageUnitItem extends BlockItem {
	private static final int LINES = 5;

	private final StorageTier tier;

	public StorageUnitItem(Block block, StorageTier tier, Properties properties) {
		super(block, properties);
		this.tier = tier;
	}

	public StorageTier tier() {
		return tier;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mlinnotas.storage_unit.capacity", tier.types(), CountFormat.compact(tier.perType()))
				.withStyle(ChatFormatting.GRAY));
		StoredItems contents = stack.getOrDefault(StorageRegistry.CONTENTS, StoredItems.EMPTY);
		if (contents.isEmpty()) {
			return;
		}
		tooltip.accept(Component.translatable("item.mlinnotas.storage_unit.contents", contents.entries().size(), tier.types(),
				CountFormat.compact(contents.totalCount())).withStyle(ChatFormatting.GOLD));
		int shown = 0;
		for (StoredItems.Entry entry : contents.entries()) {
			if (shown++ >= LINES) {
				tooltip.accept(Component.translatable("item.mlinnotas.storage_unit.more", contents.entries().size() - LINES)
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				break;
			}
			tooltip.accept(Component.translatable("item.mlinnotas.storage_unit.line", CountFormat.compact(entry.count()), entry.item().getHoverName())
					.withStyle(ChatFormatting.GRAY));
		}
	}
}
