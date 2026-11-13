package com.mlin.notas.furnace;

import com.mlin.notas.tier.Tier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Upgrade de fornalha. Use com o botao direito numa fornalha, alto-forno ou defumador.
 * A velocidade vem da escada {@link Tier#furnaceSpeed()} (pedra 1,25x ate celestio 32x).
 */
public class FurnaceUpgradeItem extends Item {
	private final Tier tier;

	public FurnaceUpgradeItem(Properties properties, Tier tier) {
		super(properties);
		this.tier = tier;
	}

	public Tier tier() {
		return tier;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mlinnotas.furnace_upgrade.tooltip", FurnaceUpgrades.formatMultiplier(tier.furnaceSpeed())));
	}
}
