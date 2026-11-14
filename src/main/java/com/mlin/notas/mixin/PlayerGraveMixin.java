package com.mlin.notas.mixin;

import com.mlin.notas.grave.GraveServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lapides: tira o inventario do jogador para uma lapide antes de o vanilla espalhar tudo no chao. A Fabric so tem
 * eventos antes do dano fatal ({@code ALLOW_DEATH}) e depois dos drops ({@code AFTER_DEATH}); o unico ponto entre os
 * dois, com o inventario ainda cheio e so quando os itens vao cair, e {@code Player.dropEquipment}.
 */
@Mixin(Player.class)
public abstract class PlayerGraveMixin {
	@Shadow
	protected abstract void destroyVanishingCursedItems();

	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void mlinnotas$gravestone(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && !level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			// como o vanilla: Maldicao do Desaparecimento some antes (e nao vai para a lapide)
			destroyVanishingCursedItems();
			GraveServer.onDeath(player, level);
		}
	}
}
