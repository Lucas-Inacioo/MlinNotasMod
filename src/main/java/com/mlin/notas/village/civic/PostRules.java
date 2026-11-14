package com.mlin.notas.village.civic;

/**
 * Regras do Correio (logica pura): a caixa de saida tem uma fileira de encaixes no nivel I e duas no II, e o que e
 * postado chega no dia seguinte de jogo (nivel I: na virada do proximo dia; nivel II: na proxima meia virada).
 * Destinos: o Correio de outra vila sua ou outro jogador. A entrega vai para a caixa de entrada daquele destino
 * ({@code v:<vila>} ou {@code p:<jogador>}), que qualquer Correio mostra.
 */
public final class PostRules {
	public static final long DAY = 24_000L;
	public static final long HALF_DAY = 12_000L;
	/** Encaixes da caixa de entrada mostrados de uma vez (tres fileiras). */
	public static final int INBOX_SLOTS = 27;

	private PostRules() {
	}

	/** Encaixes de saida do nivel (uma fileira por nivel). */
	public static int outboxSlots(int level) {
		return 9 * Math.max(1, Math.min(2, level));
	}

	/** Linhas do menu: tres de entrada mais as de saida. */
	public static int menuRows(int level) {
		return 3 + Math.max(1, Math.min(2, level));
	}

	/** Tique de jogo em que a carta postada em {@code now} chega. */
	public static long deliveryTick(long now, int level) {
		long unit = level >= 2 ? HALF_DAY : DAY;
		return (Math.floorDiv(now, unit) + 1) * unit;
	}

	/** Chave da caixa de entrada de uma vila. */
	public static String villageInbox(String villageKey) {
		return "v:" + villageKey;
	}

	/** Chave da caixa de entrada de um jogador. */
	public static String playerInbox(String uuid) {
		return "p:" + uuid;
	}

	/** Proximo destino na lista circular (delta = +1 / -1). */
	public static int cycle(int index, int size, int delta) {
		if (size <= 0) {
			return 0;
		}
		return Math.floorMod(index + delta, size);
	}
}
