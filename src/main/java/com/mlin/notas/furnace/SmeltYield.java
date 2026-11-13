package com.mlin.notas.furnace;

import com.mlin.notas.tier.Tier;

/**
 * Rendimento da fundicao por upgrade (logica pura): fornalhas com upgrade de endita ou melhor dao 2 resultados por
 * minerio bruto ou bloco de minerio; o resto continua 1 para 1.
 */
public final class SmeltYield {
	/** Primeiro upgrade que dobra o minerio. */
	public static final Tier DOUBLE_FROM = Tier.ENDITE;

	private SmeltYield() {
	}

	/** Multiplicador do resultado para a entrada (minerio ou nao) numa fornalha com este upgrade. */
	public static int multiplier(Tier furnaceTier, boolean oreInput) {
		return oreInput && furnaceTier != null && furnaceTier.ordinal() >= DOUBLE_FROM.ordinal() ? 2 : 1;
	}

	/** Quantidade do resultado (limitada ao tamanho maximo da pilha). */
	public static int resultCount(int baseCount, Tier furnaceTier, boolean oreInput, int maxStack) {
		return Math.min(maxStack, baseCount * multiplier(furnaceTier, oreInput));
	}
}
