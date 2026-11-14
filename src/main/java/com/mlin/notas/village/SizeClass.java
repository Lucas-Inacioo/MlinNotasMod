package com.mlin.notas.village;

import java.util.Locale;

/**
 * Tamanho de um lote do plano diretor (logica pura): largura ao longo da rua x fundo. Cada tipo de construcao declara
 * a maior classe que vai precisar no nivel maximo da construcao ({@link LotRules}), entao subir o nivel de uma
 * construcao nunca transborda o lote. A planta vai no lote com a porta virada para a rua.
 */
public enum SizeClass {
	/** Pequeno: casas, oficinas pequenas. */
	S(11, 11),
	/** Medio: forja, biblioteca, estalagem, plantacao pequena. */
	M(15, 13),
	/** Grande: mercado, oficinas grandes. */
	L(19, 13),
	/** Muito grande: prefeitura, quartel, museu (lotes de esquina). */
	XL(25, 13),
	/** Encostado na muralha externa: torres e guaritas. */
	W(7, 5),
	/** Minusculo (cantos chanfrados, esquinas largas): jardim, quiosque, lote livre. Acrescentado no fim (ordinal salvo). */
	T(7, 7),
	/** Faixa da muralha onde o fundo deixa: torre maior, casa da guarda. Acrescentado no fim (ordinal salvo). */
	WL(11, 6);

	private final int width;
	private final int depth;

	SizeClass(int width, int depth) {
		this.width = width;
		this.depth = depth;
	}

	/** Largura ao longo da rua. */
	public int width() {
		return width;
	}

	/** Fundo (da rua para dentro). */
	public int depth() {
		return depth;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** A planta {@code cw} x {@code cd} (porta no lado de {@code cw}) cabe neste lote. */
	public boolean fits(int cw, int cd) {
		return cw <= width && cd <= depth;
	}

	/** Este lote serve para uma construcao da classe {@code need} (mesmo tamanho ou maior, mesma familia). */
	public boolean holds(SizeClass need) {
		boolean wall = this == W || this == WL;
		boolean needWall = need == W || need == WL;
		if (wall || needWall) {
			return wall && needWall && width >= need.width && depth >= need.depth;
		}
		return width >= need.width && depth >= need.depth;
	}

	public static SizeClass byOrdinal(int i) {
		return values()[Math.max(0, Math.min(values().length - 1, i))];
	}
}
