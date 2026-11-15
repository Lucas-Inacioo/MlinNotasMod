package com.mlin.notas.village;

/**
 * Preco FIXO da terraplenagem de um anel (logica pura, Vilas 3.0): o campo de alturas ja diz a altura final de cada
 * coluna, entao o preco nao depende mais do terreno de agora. Esmeraldas em {@link MasterPlan#RING_EMERALDS} e uma
 * quantidade fixa de terra/pedregulho por anel ({@link #FILL_ITEMS}), que a pedreira da vila abate
 * ({@link com.mlin.notas.village.CraftsRules#quarryFillCredit}, em blocos de aterro = {@link RingWork#FILL_PER_ITEM} por item).
 *
 * <p>Tabela de terra (itens, aneis 1..7): 16, 32, 48, 64, 96, 128, 96. Escala com a area de cada anel (o 1 e o miolo da
 * praca, o 5 e o 6 sao os maiores) e fica entre meia pilha e duas pilhas, juntavel numa sessao de escavacao; nao e o
 * volume real (o corte do proprio anel ja alimenta o aterro e o mundo novo nasce quase pronto).
 */
public final class RingCost {
	/** Itens de terra/pedregulho de cada anel (1..7). */
	public static final int[] FILL_ITEMS = {16, 32, 48, 64, 96, 128, 96};

	private RingCost() {
	}

	private static int index(int ring) {
		return Math.max(0, Math.min(MasterPlan.RINGS - 1, ring - 1));
	}

	/** Esmeraldas do anel. */
	public static int emeralds(int ring) {
		return MasterPlan.RING_EMERALDS[index(ring)];
	}

	/** Itens de terra do anel depois do credito da pedreira (em blocos de aterro). */
	public static int fillItems(int ring, int quarryCreditBlocks) {
		return Math.max(0, FILL_ITEMS[index(ring)] - quarryCreditBlocks / RingWork.FILL_PER_ITEM);
	}
}
