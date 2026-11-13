package com.mlin.notas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mlin.notas.furnace.FurnaceUpgrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplica a velocidade de cozimento pelo nivel de upgrade da fornalha.
 * A vanilla ja tem um multiplicador por combustivel (speedMultiplier); so escalamos o resultado.
 * Upgrades de endita para cima tambem dobram o resultado de minerio bruto/bloco de minerio ({@code furnace/SmeltYield}).
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
	@Inject(method = "getSpeedMultiplier", at = @At("RETURN"), cancellable = true)
	private void mlinnotas$applyUpgrade(ServerLevel level, ItemStack fuelItem, CallbackInfoReturnable<Float> cir) {
		float multiplier = FurnaceUpgrades.tierOf((BlockEntity) (Object) this).furnaceSpeed();
		if (multiplier != 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * multiplier);
		}
	}

	@WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack mlinnotas$doubleOre(AbstractCookingRecipe recipe, SingleRecipeInput input, Operation<ItemStack> original,
			@Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
		return FurnaceUpgrades.applyYield(furnace, input.item(), original.call(recipe, input));
	}
}
