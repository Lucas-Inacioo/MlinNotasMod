package com.mlin.notas.boss;

/**
 * Papel de cada bloco de um covil de chefe ({@link LairPlan}). A ponte com o jogo ({@code LairPiece}) troca cada papel
 * por um bloco da paleta do covil. Logica pura.
 */
public enum LairPart {
	/** Espaco limpo explicito (tira terreno, arvores e pedra). */
	AIR(false, false),
	/** Camada de baixo: no mundo desce ate o chao (alicerce). */
	FOUNDATION(true, true),
	FLOOR(true, true),
	FLOOR_ACCENT(true, true),
	FLOOR_CRACKED(true, true),
	PATH(true, true),
	WALL(true, true),
	WALL_ACCENT(true, true),
	WALL_CRACKED(true, true),
	/** Mureta (muro de pedra): nao da para pular por cima. */
	WALL_LOW(false, true),
	CEILING(true, true),
	PILLAR(true, true),
	PILLAR_CAP(true, true),
	/** Plataforma elevada (estrado do altar ou trono). */
	DAIS(true, true),
	/** Meio bloco (degrau). */
	SLAB(true, true),
	/** Bloco decorativo do tema (sculk, magma, musgo): da para pisar. */
	DECOR(true, true),
	/** Lava/agua no nivel do chao: nao da para pisar nem atravessar a pe. */
	LIQUID(false, true),
	/** Moldura de deepslate reforcado (estilo cidade antiga). */
	FRAME(true, true),
	/** Bloco caido de um pilar quebrado. */
	RUBBLE(true, true),
	LIGHT(false, true),
	HANGING_LIGHT(false, true),
	CHAIN(false, true),
	CANDLE(false, true),
	SENSOR(false, true),
	SHRIEKER(false, true),
	CAMPFIRE(false, true),
	SKULL(false, true),
	ALTAR(true, true),
	CHEST(false, true);

	private final boolean standable;
	private final boolean obstacle;

	LairPart(boolean standable, boolean obstacle) {
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
