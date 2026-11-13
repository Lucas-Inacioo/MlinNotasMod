package com.mlin.notas.end;

/**
 * Papel de cada bloco da cidadela no plano ({@link CitadelPlan}). A ponte com o jogo ({@code CitadelPiece})
 * troca cada papel por um bloco de verdade. Logica pura.
 */
public enum CitadelPart {
	/** Espaco livre explicito (interior das torres). */
	AIR(false, false),
	ISLAND(true, true),
	/** Topo da ilha (musgo do End). */
	ISLAND_TOP(true, true),
	/** Caminho do patio. */
	PATH(true, true),
	FLOOR(true, true),
	WALL(true, true),
	PILLAR(true, true),
	/** Faixa decorativa nas linhas de andar. */
	TRIM(true, true),
	WINDOW(true, true),
	LAMP(true, true),
	ELEVATOR(true, true),
	/** Guarda-corpo (meia altura: da para ver por cima, nao da para atravessar). */
	RAILING(false, true),
	BOOKSHELF(true, true),
	BREWING_STAND(false, true),
	DRAGON_HEAD(false, true),
	END_ROD(false, true),
	/** Planta do patio (atravessavel). */
	PLANT(false, false),
	CHEST(false, true);

	private final boolean standable;
	private final boolean obstacle;

	CitadelPart(boolean standable, boolean obstacle) {
		this.standable = standable;
		this.obstacle = obstacle;
	}

	/** Da para ficar em pe em cima. */
	public boolean standable() {
		return standable;
	}

	/** Ocupa o espaco (o jogador nao atravessa). */
	public boolean obstacle() {
		return obstacle;
	}
}
