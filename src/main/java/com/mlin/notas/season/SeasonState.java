package com.mlin.notas.season;

/**
 * Data atual das estacoes, lida nos caminhos quentes (temperatura do bioma, cores, random tick de culturas) sem tocar
 * em registro nenhum. O servidor atualiza a cada segundo e quando a data muda por comando/gamerule; o cliente atualiza
 * ao receber o pacote de sincronizacao. Num jogo local os dois escrevem a mesma data. {@code null} = estacoes
 * desligadas (temperaturas e cores vanilla, colheita normal). Logica pura.
 */
public final class SeasonState {
	private static volatile SeasonDate current;
	private static volatile float temperatureOffset;

	private SeasonState() {
	}

	public static SeasonDate current() {
		return current;
	}

	public static void set(SeasonDate date) {
		current = date;
		temperatureOffset = date == null ? 0.0F : SeasonVisuals.temperatureOffset(date);
	}

	public static void clear() {
		set(null);
	}

	/** Deslocamento de temperatura dos biomas temperados agora. */
	public static float temperatureOffset() {
		return temperatureOffset;
	}
}
