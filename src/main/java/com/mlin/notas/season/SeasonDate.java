package com.mlin.notas.season;

/**
 * Data no calendario das estacoes: estacao, sub-fase, dia dentro da estacao (0 = primeiro) e duracao da estacao em
 * dias. Logica pura.
 */
public record SeasonDate(Season season, Season.Phase phase, int dayOfSeason, int lengthDays) {
	/** Chave visual (estacao + fase): muda no maximo 12 vezes por ano, e o que decide cores e temperatura. */
	public int visualKey() {
		return season.ordinal() * 3 + phase.ordinal();
	}

	/** Dia dentro da estacao contando de 1, para mostrar ao jogador. */
	public int displayDay() {
		return dayOfSeason + 1;
	}
}
