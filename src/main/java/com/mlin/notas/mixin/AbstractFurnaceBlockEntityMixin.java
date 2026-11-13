package com.mlin.notas.mixin;

import com.mlin.notas.furnace.FurnaceUpgrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplica a velocidade de cozimento pelo nivel de upgrade da fornalha.
 * A vanilla ja tem um multiplicador por combustivel (speedMultiplier); so escalamos o resultado.
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
}
