package com.mlin.notas.mixin;

import com.mlin.notas.gating.ToolLocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trava de ferramentas: arma/ferramenta acima da Mineracao do mundo nao ataca. {@code cannotAttackWithItem} e o ponto
 * unico que o jogo consulta antes do clique de ataque no cliente, da estocada da lanca e do golpe em entidade no
 * servidor; a Fabric nao tem evento para a estocada.
 */
@Mixin(Player.class)
public abstract class PlayerToolGateMixin {
	@Inject(method = "cannotAttackWithItem", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$toolGate(ItemStack itemStack, int tolerance, CallbackInfoReturnable<Boolean> cir) {
		if (ToolLocks.refuse((Player) (Object) this, itemStack)) {
			cir.setReturnValue(true);
		}
	}
}
