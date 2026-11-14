package com.mlin.notas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mlin.notas.food.FoodEating;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cozinha: a porcao de qualquer comida (vanilla ou do mod) passa por {@link FoodEating#eat} (variedade, frescor,
 * bonus da estacao). A Fabric nao tem evento de "comeu", e aqui e o unico ponto em que a comida vira fome.
 */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
	@WrapOperation(method = "onConsume", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void mlinnotas$adjustPortion(FoodData data, FoodProperties properties, Operation<Void> original,
			@Local(argsOnly = true) LivingEntity user, @Local(argsOnly = true) ItemStack stack) {
		FoodProperties portion = user instanceof Player player ? FoodEating.eat(player, stack, properties) : properties;
		original.call(data, portion);
	}
}
