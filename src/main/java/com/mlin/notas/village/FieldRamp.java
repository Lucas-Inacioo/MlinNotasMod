package com.mlin.notas.village;

import java.util.function.IntBinaryOperator;

/**
 * Altura de rampa para uma coluna sem altura planejada (faixa da muralha externa ainda sem muralha, beira do plano) de
 * vila antiga refeita pelo {@code /village admin regrade}: media das alturas planejadas em volta, ponderada pelo
 * inverso do quadrado da distancia. Entre dois platos vira uma rampa; junto de um plato fica na altura dele. Logica pura.
 */
public final class FieldRamp {
	/** Raio da vizinhanca lida. */
	public static final int RADIUS = 8;

	private FieldRamp() {
	}

	/** {@code planned.applyAsInt(x, z)} devolve a altura planejada ou {@link Integer#MIN_VALUE}; sem nenhuma em volta: MIN. */
	public static int height(IntBinaryOperator planned, int x, int z) {
		double sum = 0;
		double weight = 0;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int h = planned.applyAsInt(x + dx, z + dz);
				if (h == Integer.MIN_VALUE) {
					continue;
				}
				double w = 1.0 / (dx * dx + dz * dz);
				sum += h * w;
				weight += w;
			}
		}
		return weight == 0 ? Integer.MIN_VALUE : (int) Math.round(sum / weight);
	}
}
