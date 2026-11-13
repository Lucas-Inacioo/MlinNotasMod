package com.mlin.notas.mixin;

import com.mlin.notas.beacon.BeaconFlight;
import com.mlin.notas.beacon.BeaconPowers;
import com.mlin.notas.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sinalizador por nivel de metal: troca a aplicacao dos efeitos pela de {@link BeaconPowers} (alcance e nivel III pelo
 * metal, poderes extras), aceita Garimpo/Voo como secundario e encerra a zona de voo quando o sinalizador sai do mundo.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {
	@Inject(method = "applyEffects", at = @At("HEAD"), cancellable = true)
	private static void mlinnotas$applyByTier(Level level, BlockPos pos, int levels, Holder<MobEffect> primary, Holder<MobEffect> secondary, CallbackInfo ci) {
		BeaconPowers.applyEffects(level, pos, levels, primary, secondary);
		ci.cancel();
	}

	@Inject(method = "validateEffects", at = @At("HEAD"), cancellable = true)
	private static void mlinnotas$acceptExtras(Holder<MobEffect> primary, Holder<MobEffect> secondary, int levels, CallbackInfoReturnable<Boolean> cir) {
		if (ModEffects.extraOf(secondary) != null) {
			cir.setReturnValue(BeaconPowers.validExtraSelection(primary, levels));
		}
	}

	@Inject(method = "setRemoved", at = @At("TAIL"))
	private void mlinnotas$endFlightZone(CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		if (self.getLevel() != null && !self.getLevel().isClientSide()) {
			BeaconFlight.unregister(self.getLevel(), self.getBlockPos());
		}
	}
}
