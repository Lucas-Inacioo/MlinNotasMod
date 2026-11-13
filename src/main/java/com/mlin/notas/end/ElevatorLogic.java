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
