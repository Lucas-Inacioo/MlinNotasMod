package com.mlin.notas.food;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bem Alimentado: vida maxima extra (modificador de atributo, ver {@link FoodRegistry}) e cura lenta de 1 ponto a cada
 * {@link FoodRules#wellFedRegenInterval} ticks. Nivel I nas refeicoes fartas, II nos banquetes.
 */
public class WellFedEffect extends MobEffect {
	public WellFedEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xE8A33A);
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
		if (mob.getHealth() < mob.getMaxHealth()) {
			mob.heal(1.0F);
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		return tickCount % FoodRules.wellFedRegenInterval(amplification) == 0;
	}
}
