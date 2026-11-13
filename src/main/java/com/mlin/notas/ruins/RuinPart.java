package com.mlin.notas.ruins;

/**
 * Papel de cada bloco de uma ruina no plano ({@link RuinPlan}). A peca ({@code RuinPiece}) troca cada papel pelo
 * bloco da paleta do bioma ({@link RuinStyle}). Partes orientadas usam a direcao em {@link RuinPlan#facing}. Logica pura.
 */
public enum RuinPart {
	/** Espaco livre explicito (limpa o terreno natural). */
	AIR(false, false, false),
	/** Alicerce: na camada y=-1 a peca preenche para baixo ate o chao. */
	FOUNDATION(true, true, false),
	/** Chao de pedra (capela, cofre da mina, praca do poco). */
	PAVING(true, true, false),
	/** Terra batida (caminhos, acampamento). */
	GROUND(true, true, false),
	/** Terra com grama (areia no deserto): onde nascem plantas. */
	SOIL(true, true, false),
	GRAVEL(true, true, false),
	/** Assoalho de tabuas. */
	FLOOR(true, true, false),
	/** Pedra principal da parede. As quatro variantes abaixo sao misturadas por peso (textura da parede). */
	WALL(true, true, false),
	/** Pedra secundaria (pedregulho, andesito...). */
	WALL_ALT(true, true, false),
	WALL_MOSSY(true, true, false),
	WALL_CRACKED(true, true, false),
	/** Pedra de destaque (cantoneiras, arcos, pilares). */
	ACCENT(true, true, false),
	/** Tronco: em pe sem direcao; deitado ao longo do eixo da direcao. */
	BEAM(true, true, false),
	PLANKS(true, true, false),
	/** Laje de pedra (metade de baixo). */
	SLAB(true, true, false),
	/** Laje de pedra na metade de cima (arcos, prateleiras). */
	SLAB_TOP(true, true, false),
	/** Degrau de pedra: a parte alta fica do lado da direcao. */
	STAIRS(true, true, false),
	/** Degrau de pedra de ponta-cabeca (misulas, arcos, beirais). */
	STAIRS_TOP(true, true, false),
	/** Muro de pedra (lapides, parapeitos baixos). */
	STONE_WALL(false, true, false),
	WOOD_SLAB(true, true, false),
	WOOD_SLAB_TOP(true, true, false),
	/** Degrau de madeira (escada da torre, bancos, telhados). */
	WOOD_STAIRS(true, true, false),
	WOOD_STAIRS_TOP(true, true, false),
	FENCE(false, true, false),
	/** Alcapao aberto (roda de carroca, veneziana): encostado no lado oposto ao da direcao. */
	TRAPDOOR(false, true, false),
	/** Alcapao fechado na metade de cima (tampo de mesa, prateleira). */
	TRAPDOOR_TOP(true, true, false),
	/** Escada de mao: subir/descer. Direcao = para onde ela "olha" (a parede fica atras). */
	LADDER(false, false, false),
	CHEST(false, true, false),
	/** Barril com loot. */
	BARREL(true, true, false),
	/** Barril vazio (caixote de decoracao). */
	CRATE(true, true, false),
	/** Fogueira apagada. */
	CAMPFIRE(false, true, true),
	/** La da barraca e a faixa de outra cor. */
	TENT(true, true, false),
	TENT_ALT(true, true, false),
	/** Tapete da cor da barraca (saco de dormir, lona caida). */
	CARPET(false, false, true),
	/** Tapete vermelho do corredor da capela. */
	RUNNER(false, false, true),
	/** Bloco do altar da capela. */
	ALTAR(true, true, false),
	/** Atril em cima do altar: lugar reservado ao item que vai invocar um chefe (marcador {@code boss_summon}). */
	LECTERN(false, true, true),
	/** Bloco provisorio da sala do chefe da mina (Golem de Cobalto; marcador {@code boss_altar}). */
	BOSS_ALTAR(true, true, false),
	/** Veio de minerio (cobre ou ferro, conforme {@link RuinPlan#ironVein()}). */
	ORE(true, true, false),
	TORCH(false, false, true),
	/** Lanterna apoiada no chao (ou em cerca, caixote...). */
	LANTERN(false, true, true),
	/** Lanterna pendurada (corrente ou viga em cima). */
	LANTERN_HANGING(false, true, false),
	/** Corrente: em pe sem direcao; deitada ao longo do eixo da direcao. */
	CHAIN(false, true, false),
	COBWEB(false, false, false),
	/** Trepadeira: a direcao aponta para a parede onde ela se apoia. */
	VINE(false, false, false),
	LEAVES(true, true, false),
	MOSS_CARPET(false, false, true),
	/** Grama/samambaia/arbusto seco, conforme o bioma. */
	PLANT(false, false, true),
	FLOWER(false, false, true),
	FLOWER_POT(false, true, true),
	CANDLE(false, false, true),
	/** Botao de pedra no chao (pedrinhas soltas). */
	BUTTON(false, false, true),
	HAY(true, true, false),
	CAULDRON(true, true, false),
	CRAFTING_TABLE(true, true, false),
	ANVIL(true, true, false),
	DECORATED_POT(false, true, true),
	/** Vidro colorido (vitral quebrado): a direcao da o eixo da parede. */
	GLASS_PANE(false, true, false),
	IRON_BARS(false, true, false),
	/** Sino caido no chao. */
	BELL(false, true, true),
	WATER(false, false, false),
	/** Trilho reto ao longo do eixo da direcao. */
	RAIL(false, false, true),
	/** Trilho em rampa: sobe para o lado da direcao. */
	RAIL_UP(false, false, true),
	/** Trilho reto com um carrinho de mina (entidade) em cima. */
	MINECART(false, false, true),
	/** Metade de baixo da pedra de teleporte (a peca coloca as duas metades). */
	WAYSTONE(false, true, false),
	/** Metade de cima da pedra de teleporte (so ocupa o espaco no plano). */
	WAYSTONE_TOP(false, true, false);

	private final boolean standable;
	private final boolean obstacle;
	private final boolean needsFloor;

	RuinPart(boolean standable, boolean obstacle, boolean needsFloor) {
		this.standable = standable;
		this.obstacle = obstacle;
		this.needsFloor = needsFloor;
	}

	/** Da para ficar em pe em cima. */
	public boolean standable() {
		return standable;
	}

	/** Ocupa o espaco (o jogador nao atravessa). */
	public boolean obstacle() {
		return obstacle;
	}

	/** Precisa de um bloco embaixo (planta, tapete, vela, trilho...). */
	public boolean needsFloor() {
		return needsFloor;
	}
}
