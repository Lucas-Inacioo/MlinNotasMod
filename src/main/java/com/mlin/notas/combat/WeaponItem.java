package com.mlin.notas.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Arma de uma {@link WeaponClass} (montante, adaga, martelo, glaive, rapieira) ou arma assinada de chefe
 * ({@link BossWeapon}). Os numeros ficam nos atributos do item; o comportamento (combo, arco, perfuracao...) fica em
 * {@link CombatServer}.
 */
public class WeaponItem extends Item {
	private final WeaponClass weaponClass;
	private final BossWeapon bossWeapon;

	public WeaponItem(Properties properties, WeaponClass weaponClass, BossWeapon bossWeapon) {
		super(properties);
		this.weaponClass = weaponClass;
		this.bossWeapon = bossWeapon;
	}

	public WeaponClass weaponClass() {
		return weaponClass;
	}

	/** Arma de chefe (ou null). */
	public BossWeapon bossWeapon() {
		return bossWeapon;
	}

	/** Classe da arma na mao (ou null se nao for arma do mod). */
	public static WeaponClass classOf(ItemStack stack) {
		return stack.getItem() instanceof WeaponItem weapon ? weapon.weaponClass : null;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("weapon.mlinnotas." + weaponClass.suffix() + ".desc").withStyle(ChatFormatting.GRAY));
		if (bossWeapon != null) {
			tooltip.accept(Component.translatable("item.mlinnotas." + bossWeapon.id() + ".desc").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		int percent = Math.round((weaponClass.finisherMultiplier() - 1.0F) * 100.0F);
		tooltip.accept(Component.translatable("weapon.mlinnotas.combo", percent).withStyle(ChatFormatting.DARK_GRAY));
	}
}
