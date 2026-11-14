package com.mlin.notas.doors;

/**
 * Portas duplas (logica pura): duas portas formam um par quando ficam lado a lado, viradas para o mesmo lado, com as
 * dobradicas em lados opostos e no mesmo estado (as duas abertas ou as duas fechadas). Abrir uma abre a outra.
 *
 * <p>Lado do par: a porta com dobradica a esquerda tem a parceira a direita de quem olha para a frente da porta (sentido
 * horario a partir de {@code facing}); a de dobradica a direita, no sentido anti-horario. E o mesmo criterio que o
 * vanilla usa para escolher a dobradica ao colocar a segunda porta.</p>
 */
public final class DoubleDoorRules {
	private DoubleDoorRules() {
	}

	/** Verdadeiro se a parceira fica no sentido horario a partir da frente da porta clicada. */
	public static boolean partnerClockwise(boolean hingeLeft) {
		return hingeLeft;
	}

	/** As duas portas formam um par (estado de antes do clique). */
	public static boolean pairs(String facingA, boolean hingeLeftA, boolean openA, String facingB, boolean hingeLeftB, boolean openB) {
		return facingA.equals(facingB) && hingeLeftA != hingeLeftB && openA == openB;
	}

	/** Quem pode acionar o par: jogador (nao espectador) que nao esta agachado. */
	public static boolean triggers(boolean isPlayer, boolean sneaking, boolean spectator) {
		return isPlayer && !sneaking && !spectator;
	}
}
