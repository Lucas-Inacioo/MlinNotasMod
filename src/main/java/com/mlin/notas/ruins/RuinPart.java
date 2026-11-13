package com.mlin.notas.ruins;

/**
 * Papel de cada bloco de uma ruina no plano ({@link RuinPlan}). A peca ({@code RuinPiece}) troca cada papel pelo
 * bloco da paleta do bioma ({@link RuinStyle}). Logica pura.
 */
public enum RuinPart {
	/** Espaco livre explicito (limpa o terreno natural). */
	AIR(false, false),
	/** Alicerce: na camada y=-1 a peca preenche para baixo ate o chao. */
	FOUNDATION(true, true),
	/** Chao de pedra (capela, cofre da mina). */
	PAVING(true, true),
	/** Terra batida do acampamento. */
	GROUND(true, true),
	/** Assoalho de tabuas (andares da torre). */
	FLOOR(true, true),
	WALL(true, true),
	WALL_MOSSY(true, true),
	WALL_CRACKED(true, true),
	/** Tronco (pilar, viga, banco). */
	BEAM(true, true),
	PLANKS(true, true),
	/** Laje de pedra (entulho, telhado). */
	SLAB(true, true),
	/** Laje de madeira (bancos da capela, telhado do poco). */
	WOOD_SLAB(true, true),
	/** Cerca (escoras da mina, colunas do poco). */
	FENCE(false, true),
	/** Escada de mao: subir/descer. Direcao em {@link RuinPlan#facing}. */
	LADDER(false, false),
	CHEST(false, true),
	BARREL(true, true),
	/** Fogueira apagada do acampamento. */
	CAMPFIRE(false, true),
	/** La da barraca. */
	TENT(true, true),
	/** Bloco do altar da capela. */
	ALTAR(true, true),
	/** Atril em cima do altar: lugar reservado ao item que vai invocar um chefe (marcador {@code boss_summon}). */
	LECTERN(false, true),
	/** Bloco provisorio da sala do chefe da mina (Golem de Cobalto; marcador {@code boss_altar}). */
	BOSS_ALTAR(true, true),
	/** Veio de minerio (cobre ou ferro, conforme {@link RuinPlan#ironVein()}). */
	ORE(true, true),
	TORCH(false, false),
	COBWEB(false, false),
	/** Metade de baixo da pedra de teleporte (a peca coloca as duas metades). */
	WAYSTONE(false, true),
	/** Metade de cima da pedra de teleporte (so ocupa o espaco no plano). */
	WAYSTONE_TOP(false, true);

	private final boolean standable;
	private final boolean obstacle;

	RuinPart(boolean standable, boolean obstacle) {
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
