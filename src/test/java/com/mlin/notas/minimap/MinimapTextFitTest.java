package com.mlin.notas.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapTextFitTest {
	@Test
	void fullScaleWhenItFits() {
		assertEquals(1.0, MinimapTextFit.scale(80, 100, 2));
	}

	@Test
	void shrinksInWholePixelSteps() {
		// escala 4: passos de 0,25; 130 * 0,75 = 97,5 cabe em 100
		assertEquals(0.75, MinimapTextFit.scale(130, 100, 4));
		// escala 3: 2/3
		assertEquals(2.0 / 3, MinimapTextFit.scale(140, 100, 3), 1e-9);
		// escala 2: so 1 ou 0,5
		assertEquals(0.5, MinimapTextFit.scale(150, 100, 2));
	}

	@Test
	void neverBelowOneScreenPixelOrHalf() {
		// escala 1: nao diminui (o desenho quebra a linha)
		assertEquals(1.0, MinimapTextFit.scale(300, 52, 1));
		assertEquals(0.5, MinimapTextFit.scale(300, 52, 4));
		assertEquals(0.5, MinimapTextFit.minScale(6));
		assertEquals(1.0, MinimapTextFit.minScale(1));
	}

	@Test
	void wrapWidthUndoesScale() {
		assertEquals(104, MinimapTextFit.wrapWidth(52, 0.5));
		assertEquals(52, MinimapTextFit.wrapWidth(52, 1.0));
	}

	@Test
	void lineIsPushedInsideScreen() {
		// minimapa no canto direito: linha mais larga que o mapa nao passa da borda
		double left = MinimapTextFit.left(400, 60, 420, 2);
		assertTrue(left + 60 <= 418);
		assertEquals(370, MinimapTextFit.left(400, 60, 1000, 2));
		assertEquals(2, MinimapTextFit.left(10, 60, 1000, 2));
	}
}
