package com.mlin.notas.village.works;

/**
 * Um bloco de obra publica (logica pura): x/z relativos ao quadro, y absoluto, estado em texto
 * ({@code minecraft:oak_stairs[facing=north]}, convertido na ponte com o jogo). {@code kind} diz que regra de posse vale
 * no mundo: {@link Kind#SURFACE} troca o bloco do chao (so terra/grama/caminho natural ou bloco que esta obra mesma
 * colocou antes); {@link Kind#ABOVE} so entra onde ha ar ou planta (ou bloco que a obra colocou). {@code text} so vale
 * para placas (ate 4 linhas, chaves de traducao ou texto).
 */
public record WBlock(int x, int y, int z, String state, Kind kind, String[] text) {
	public enum Kind {
		SURFACE, ABOVE
	}

	public static WBlock surface(int x, int y, int z, String state) {
		return new WBlock(x, y, z, state, Kind.SURFACE, null);
	}

	public static WBlock above(int x, int y, int z, String state) {
		return new WBlock(x, y, z, state, Kind.ABOVE, null);
	}

	public static WBlock sign(int x, int y, int z, String state, String... lines) {
		return new WBlock(x, y, z, state, Kind.ABOVE, lines);
	}

	public long col() {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	public long key() {
		return ((long) (x + 2048) << 40) | ((long) (y + 2048) << 20) | (z + 2048);
	}

	/** Ordem de construcao: chao primeiro, depois de baixo para cima. */
	public int order() {
		return (kind == Kind.SURFACE ? 0 : 1000) + y;
	}
}
