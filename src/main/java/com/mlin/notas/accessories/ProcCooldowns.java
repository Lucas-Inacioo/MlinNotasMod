package com.mlin.notas.accessories;

import java.util.HashMap;
import java.util.Map;

/**
 * Recargas dos efeitos que disparam em combate, por chave (ex.: {@code "thorns"}). Nenhum dispara mais de uma vez por
 * segundo ({@link #MIN_INTERVAL} ticks), mesmo que alguem peca um intervalo menor. Logica pura (um por jogador).
 */
public final class ProcCooldowns {
	/** 20 ticks = 1 segundo. */
	public static final int MIN_INTERVAL = 20;

	private final Map<String, Long> last = new HashMap<>();

	/** Dispara (e marca) se a recarga de {@code key} passou; {@code interval} abaixo de 20 vira 20. */
	public boolean tryProc(String key, long tick, int interval) {
		int wait = Math.max(MIN_INTERVAL, interval);
		Long previous = last.get(key);
		if (previous != null && tick - previous < wait && tick >= previous) {
			return false;
		}
		last.put(key, tick);
		return true;
	}

	public boolean tryProc(String key, long tick) {
		return tryProc(key, tick, MIN_INTERVAL);
	}

	/** Ticks que faltam para {@code key} poder disparar de novo. */
	public long remaining(String key, long tick, int interval) {
		Long previous = last.get(key);
		if (previous == null || tick < previous) {
			return 0;
		}
		return Math.max(0, Math.max(MIN_INTERVAL, interval) - (tick - previous));
	}

	public void clear() {
		last.clear();
	}
}
