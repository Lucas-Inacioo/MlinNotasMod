package com.mlin.notas.backpack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Upgrade de mochila: arraste sobre a mochila no inventario e clique com o botao direito. */
public class BackpackUpgradeItem extends Item {
	private final BackpackUpgradeKind kind;

	public BackpackUpgradeItem(Properties properties, BackpackUpgradeKind kind) {
		super(properties);
		this.kind = kind;
	}

	public BackpackUpgradeKind kind() {
		return kind;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mlinnotas." + kind.itemName() + ".tooltip"));
		tooltip.accept(Component.translatable("item.mlinnotas.backpack_upgrade.howto"));
	}
}
