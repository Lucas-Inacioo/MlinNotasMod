package com.mlin.notas.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapColorShadingTest {
	private static final int GRAY = 0xFF808080;

	private static int red(int argb) {
		return (argb >> 16) & 0xFF;
	}

	@Test
	void higherIsLighterLowerIsDarker() {
		int higher = MapColorShading.shadeByHeight(GRAY, 70, 64);
		int flat = MapColorShading.shadeByHeight(GRAY, 64, 64);
		int lower = MapColorShading.shadeByHeight(GRAY, 60, 64);
		assertTrue(red(higher) > red(flat), "mais alto deve ser mais claro");
		assertTrue(red(lower) < red(flat), "mais baixo deve ser mais escuro");
		assertEquals(GRAY, flat);
	}

	@Test
	void scaleSaturatesAndKeepsAlpha() {
		int bright = MapColorShading.scale(0x80F0F0F0, 2.0F);
		assertEquals(0x80FFFFFF, bright);
		assertEquals(0xFF000000, MapColorShading.scale(0xFF101010, 0.0F));
	}

	@Test
	void deeperWaterIsDarker() {
		int shallow = MapColorShading.waterByDepth(0xFF4040FF, 1);
		int deep = MapColorShading.waterByDepth(0xFF4040FF, 20);
		int abyss = MapColorShading.waterByDepth(0xFF4040FF, 200);
		assertTrue((shallow & 0xFF) > (deep & 0xFF));
		assertTrue((abyss & 0xFF) >= 0xFF * 0.35F - 1, "nunca escurece alem de 35%");
	}

	@Test
	void caveDepthDarkensWithFloor() {
		assertEquals(GRAY, MapColorShading.caveByDepth(GRAY, 0));
		assertTrue(red(MapColorShading.caveByDepth(GRAY, 10)) < red(GRAY));
		assertEquals(red(MapColorShading.caveByDepth(GRAY, 100)), red(MapColorShading.caveByDepth(GRAY, 500)), "satura no minimo");
	}

	@Test
	void colorConversions() {
		assertEquals(0xFF123456, MapColorShading.opaque(0x123456));
		assertEquals(0xFF563412, MapColorShading.argbToAbgr(0xFF123456));
	}
}
