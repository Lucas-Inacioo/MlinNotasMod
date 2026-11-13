package com.mlin.notas.mixin;

import com.mlin.notas.village.VillageServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aldeoes que moram numa vila do mod: o nivel da vila limita ate onde a profissao sobe (Acampamento = aprendiz ...
 * Cidade = mestre) e da desconto em esmeraldas; fora de vila fundada o teto e jornaleiro
 * ({@code VillageLevel.WILD_TRADE_CAP}). Nao ha evento da Fabric para o nivel nem para o preco da troca.
 */
@Mixin(Villager.class)
public abstract class VillagerVillageMixin {
	@Inject(method = "shouldIncreaseLevel", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$capLevel(CallbackInfoReturnable<Boolean> cir) {
		Villager self = (Villager) (Object) this;
		if (self.level() instanceof ServerLevel level) {
			int cap = VillageServer.tradeCap(level, self.blockPosition());
			if (cap > 0 && self.getVillagerData().level() >= cap) {
				cir.setReturnValue(false);
			}
		}
	}

	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void mlinnotas$discount(Player player, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.level() instanceof ServerLevel level) {
			int discount = VillageServer.discount(level, self.blockPosition());
			if (discount > 0) {
				for (MerchantOffer offer : self.getOffers()) {
					offer.addToSpecialPriceDiff(-discount);
				}
			}
		}
	}
}
