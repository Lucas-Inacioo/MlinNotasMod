package com.mlin.notas.accessories;

/** Origem do acessorio: basico (receita), artefato (exploracao) ou reliquia (chefe). Logica pura. */
public enum AccessoryKind {
	/** Anel/colar de metal feito na bancada; varios iguais podem ser usados juntos. */
	BASIC,
	/** Achado so em baus de estruturas; unico (dois iguais nao contam). */
	ARTIFACT,
	/** Solto por um chefe; unico. Ajuda contra o proximo chefe sem anular a mecanica dele. */
	RELIC;

	public boolean unique() {
		return this != BASIC;
	}
}
