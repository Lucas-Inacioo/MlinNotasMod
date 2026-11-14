package com.mlin.notas.mixin;

import com.mlin.notas.accessories.AccessoryServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reducao de dano dos acessorios (geral + fogo/sonico/magico/explosao), aplicada depois de armadura, Resistencia e
 * Protecao. A Fabric nao tem evento que altere o valor do dano, por isso o mixin.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAccessoryMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void mlinnotas$accessoryReduction(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ServerPlayer player && cir.getReturnValueF() > 0.0F) {
			cir.setReturnValue((float) (cir.getReturnValueF() * AccessoryServer.incomingFactor(player, source)));
		}
	}
}
