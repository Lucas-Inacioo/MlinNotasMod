package com.mlin.notas.chest;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Upgrade de bau: use com o botao direito no bau do nivel anterior para converte-lo em {@link #target()}.
 */
public class ChestUpgradeItem extends Item {
	private final ChestTier target;

	public ChestUpgradeItem(Properties properties, ChestTier target) {
		super(properties);
		this.target = target;
	}

	public ChestTier target() {
		return target;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		ChestTier previous = target.previous();
		Component from = previous == null
				? Component.translatable("block.minecraft.chest")
				: Component.translatable("block.mlinnotas." + previous.blockName());
		Component to = Component.translatable("block.mlinnotas." + target.blockName());
		tooltip.accept(Component.translatable("item.mlinnotas.chest_upgrade.tooltip", from, to, target.size()));
	}
}
