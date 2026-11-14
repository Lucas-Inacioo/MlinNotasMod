package com.mlin.notas.grave;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * API para outras partes do mod redirecionarem os itens de uma morte antes da lapide.
 *
 * <p>Exemplo (futuro): morreu perto de uma vila com Estalagem II -> os itens vao para o bau de achados e perdidos da
 * estalagem e o jogador renasce la. O interceptador tira o que quiser de {@link GraveContents} (inclusive o XP, com
 * {@code setXp(0)}) e devolve {@code true} para encerrar a cadeia. O que sobrar nos conteudos ainda vira lapide:
 * nada se perde. Roda no servidor, durante a morte, antes do inventario ser esvaziado pelo vanilla (o inventario e
 * os acessorios ja foram copiados para os conteudos).</p>
 */
public final class GraveHooks {
	private GraveHooks() {
	}

	@FunctionalInterface
	public interface DeathItemsInterceptor {
		/**
		 * @param deathPos onde o jogador morreu (bloco dos pes)
		 * @param contents itens e XP da morte; tire o que for levar para outro lugar
		 * @return true para parar a cadeia (os outros interceptadores nao rodam)
		 */
		boolean claim(ServerPlayer player, ServerLevel level, BlockPos deathPos, GraveContents contents);
	}

	@FunctionalInterface
	public interface GravePlaced {
		void placed(ServerPlayer player, ServerLevel level, BlockPos gravePos, GraveInfo info);
	}

	/** Antes de a lapide ser colocada. */
	public static final Event<DeathItemsInterceptor> CLAIM_DEATH_ITEMS = EventFactory.createArrayBacked(DeathItemsInterceptor.class,
			listeners -> (player, level, deathPos, contents) -> {
				for (DeathItemsInterceptor listener : listeners) {
					if (listener.claim(player, level, deathPos, contents)) {
						return true;
					}
				}
				return false;
			});

	/** Depois de a lapide ser colocada (mensagens, quests, etc.). */
	public static final Event<GravePlaced> GRAVE_PLACED = EventFactory.createArrayBacked(GravePlaced.class,
			listeners -> (player, level, pos, info) -> {
				for (GravePlaced listener : listeners) {
					listener.placed(player, level, pos, info);
				}
			});
}
