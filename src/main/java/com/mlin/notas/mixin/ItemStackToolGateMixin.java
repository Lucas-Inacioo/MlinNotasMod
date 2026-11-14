package com.mlin.notas.mixin;

import com.mlin.notas.gating.ToolLocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trava de ferramentas: enxada/machado/pa/varinha acima da Mineracao do mundo nao transformam blocos. O
 * {@code UseBlockCallback} da Fabric roda antes do bloco (bau, porta...) e travaria tudo; aqui o bloco ja teve a vez.
 * Devolve PASS (nao FAIL) para a outra mao ainda poder colocar a tocha.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackToolGateMixin {
	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$toolGate(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack self = (ItemStack) (Object) this;
		Player player = context.getPlayer();
		if (player != null && ToolLocks.hasBlockUse(self) && ToolLocks.refuse(player, self)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
