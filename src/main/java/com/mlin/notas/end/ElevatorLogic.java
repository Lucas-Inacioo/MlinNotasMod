package com.mlin.notas.end;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Regra do Elevador de Purpur: em cima de um elevador, pular leva ao proximo elevador acima na mesma coluna e
 * agachar leva ao proximo abaixo, desde que haja 2 blocos livres em cima do destino. Logica pura.
 */
public final class ElevatorLogic {
	private ElevatorLogic() {
	}

	/** Quantos blocos abaixo dos pes ainda contam como "em cima do elevador" (o pulo ja tirou o jogador do chao). */
	public static final int MAX_DEPTH = 2;

	/**
	 * Elevador sob o jogador: procura de cima para baixo a partir do bloco dos pes ({@code feetY}, ja com a folga de
	 * -0.2) ate {@link #MAX_DEPTH} blocos abaixo, parando no primeiro bloco solido que nao seja elevador. Assim o
	 * pulo (jogador ate ~1,25 bloco acima) ainda acha o elevador, mas um elevador escondido sob outro piso nao conta.
	 *
	 * @param isElevator se ha elevador no y dado (mesma coluna)
	 * @param isSolid    se o y dado tem colisao
	 */
	public static OptionalInt elevatorBelow(int feetY, IntPredicate isElevator, IntPredicate isSolid) {
		for (int y = feetY; y >= feetY - MAX_DEPTH; y--) {
			if (isElevator.test(y)) {
				return OptionalInt.of(y);
			}
			if (isSolid.test(y)) {
				return OptionalInt.empty();
			}
		}
		return OptionalInt.empty();
	}

	/**
	 * Y do elevador de destino.
	 *
	 * @param fromY      y do elevador onde o jogador esta
	 * @param up         true para subir, false para descer
	 * @param isElevator se ha elevador no y dado (mesma coluna)
	 * @param isFree     se o y dado deixa o jogador passar (ar, plantas...)
	 * @param range      distancia maxima em blocos
	 */
	public static OptionalInt target(int fromY, boolean up, IntPredicate isElevator, IntPredicate isFree, int range) {
		int step = up ? 1 : -1;
		for (int y = fromY + step; Math.abs(y - fromY) <= range; y += step) {
			if (isElevator.test(y) && isFree.test(y + 1) && isFree.test(y + 2)) {
				return OptionalInt.of(y);
			}
		}
		return OptionalInt.empty();
	}
}
