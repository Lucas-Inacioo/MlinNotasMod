package com.mlin.notas.mixin;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Permite colocar laco em aldeoes (e no mercador ambulante). A vanilla devolve false em AbstractVillager.
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {
	@Inject(method = "canBeLeashed", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$allowLeash(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}
}
