package com.mlin.notas.minimap;

/**
 * Cabe o texto embaixo do minimapa (coordenadas, bioma, estacao) na largura do mapa (logica pura).
 *
 * <p>Primeiro diminui a escala, so em passos que mantem cada pixel da fonte em pixels inteiros da tela
 * ({@code k / escalaDaGui}) e nunca abaixo de metade nem de 1 pixel de tela por pixel da fonte (senao fica ilegivel);
 * se nem assim couber, o desenho quebra a linha na menor escala. Por fim a linha e empurrada para dentro da tela.</p>
 */
public final class MinimapTextFit {
	/** Menor escala do texto. */
	public static final double MIN_SCALE = 0.5;

	private MinimapTextFit() {
	}

	/** Menor escala permitida nessa escala de GUI (1 pixel de tela por pixel da fonte, no minimo {@link #MIN_SCALE}). */
	public static double minScale(int guiScale) {
		return Math.max(MIN_SCALE, 1.0 / Math.max(1, guiScale));
	}

	/**
	 * Maior escala {@code k / guiScale} (de 1 ate {@link #minScale}) em que {@code textWidth} cabe em {@code maxWidth};
	 * se nenhuma couber, a menor (e o chamador quebra a linha).
	 */
	public static double scale(int textWidth, int maxWidth, int guiScale) {
		int gui = Math.max(1, guiScale);
		double min = minScale(gui);
		for (int k = gui; k >= 1; k--) {
			double s = (double) k / gui;
			if (s + 1e-9 < min) {
				break;
			}
			if (textWidth * s <= maxWidth + 1e-9) {
				return s;
			}
		}
		return min;
	}

	/** Largura (em unidades da fonte, antes da escala) em que a linha deve quebrar nessa escala. */
	public static int wrapWidth(int maxWidth, double scale) {
		return Math.max(1, (int) Math.floor(maxWidth / scale));
	}

	/**
	 * X da esquerda de uma linha de largura {@code width} (ja na escala) centrada em {@code centerX}, empurrada para
	 * ficar entre {@code margin} e {@code screenWidth - margin}.
	 */
	public static double left(double centerX, double width, int screenWidth, int margin) {
		double x = centerX - width / 2.0;
		x = Math.min(x, screenWidth - margin - width);
		x = Math.max(x, margin);
		return x;
	}
}
