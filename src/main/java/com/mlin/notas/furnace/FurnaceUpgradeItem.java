package com.mlin.notas.furnace;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Upgrade de fornalha. Use com o botao direito numa fornalha, alto-forno ou defumador.
 * Cada nivel dobra a velocidade (ferro 2x, ouro 4x, diamante 8x).
 */
public class FurnaceUpgradeItem extends Item {
	private final int tier;

	public FurnaceUpgradeItem(Properties properties, int tier) {
		super(properties);
		this.tier = tier;
	}

	public int tier() {
		return tier;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mlinnotas.furnace_upgrade.tooltip", FurnaceUpgrades.speedMultiplier(tier)));
	}
}
