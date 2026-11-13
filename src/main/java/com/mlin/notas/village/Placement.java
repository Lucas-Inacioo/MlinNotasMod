package com.mlin.notas.village;

/**
 * Um bloco a colocar: x/z relativos ao centro da vila (o quadro), y absoluto. {@code dir} pode ser null (parte sem
 * direcao ou tronco em pe). {@code fill} = encher por baixo ate o terreno (camada do chao).
 */
public record Placement(int x, int y, int z, VPart part, Facing dir, boolean fill) {
	public static long key(int x, int y, int z) {
		return ((long) (x + 2048) << 40) | ((long) (y + 2048) << 20) | (z + 2048);
	}

	public long key() {
		return key(x, y, z);
	}

	/** Ordem de construcao: limpar de cima para baixo, depois solidos de baixo para cima, depois detalhes. */
	public int phase() {
		if (part == VPart.AIR) {
			return 0;
		}
		return part.late() ? 2 : 1;
	}
}
