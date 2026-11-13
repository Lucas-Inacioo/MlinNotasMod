package com.mlin.notas.village;

import java.util.Locale;

/**
 * Missao do dia (logica pura). {@code target} depende do tipo: id de item (entregar), id de entidade (cacar) ou
 * chave de exploracao ({@link BountyBoard#EXPLORE_KEYS}). A recompensa vai para quem receber: esmeraldas, um item
 * util e prosperidade para a vila.
 */
public record Bounty(Kind kind, String target, int count, int emeralds, String rewardItem, int rewardCount, int prosperity) {
	public enum Kind {
		/** Entregar itens no quadro (sao consumidos). */
		DELIVER,
		/** Qualquer jogador cacar N criaturas (conta a partir do sorteio). */
		HUNT,
		/** Qualquer jogador visitar um lugar (bioma, estrutura, dimensao). */
		EXPLORE;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return DELIVER;
		}
	}
}
