package com.mlin.notas.season;

/**
 * Como as estacoes afetam um bioma (tags {@code mlinnotas:seasonal/temperate} e {@code mlinnotas:seasonal/hot}).
 * Logica pura.
 */
public enum SeasonClimate {
	/** Planicies, florestas, taigas, pantano, rios, praias: cores mudam, neva no inverno, colheita sazonal. */
	TEMPERATE,
	/** Selva, savana, deserto, badlands, mangue: sem neve nem cor nova; o "inverno" e so uma estacao seca branda. */
	HOT,
	/** O resto do Overworld (biomas ja nevados, oceanos, cavernas): sem neve nem cor nova, colheita sazonal. */
	OTHER
}
