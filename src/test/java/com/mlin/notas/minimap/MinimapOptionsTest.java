package com.mlin.notas.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapOptionsTest {
	@Test
	void defaultsSurviveRoundTrip() {
		MinimapOptions options = new MinimapOptions();
		MinimapOptions loaded = MinimapOptions.fromJson(options.toJson());
		assertEquals(options.toJson(), loaded.toJson());
		assertTrue(loaded.enabled);
		assertEquals(MinimapOptions.CaveMode.AUTO, loaded.caveMode);
		assertEquals(96, loaded.size);
		assertEquals(1.0F, loaded.zoom);
	}

	@Test
	void smallerAndLargerStopAtTheEnds() {
		MinimapOptions options = new MinimapOptions();
		options.smaller();
		assertEquals(80, options.size);
		for (int i = 0; i < 20; i++) {
			options.smaller();
		}
		assertEquals(MinimapOptions.SIZES[0], options.size);
		for (int i = 0; i < 20; i++) {
			options.larger();
		}
		assertEquals(MinimapOptions.SIZES[MinimapOptions.SIZES.length - 1], options.size);
	}

	@Test
	void changedValuesRoundTrip() {
		MinimapOptions options = new MinimapOptions();
		options.enabled = false;
		options.caveMode = MinimapOptions.CaveMode.ON;
		options.showItems = true;
		options.nextSize();
		options.zoomIn();
		MinimapOptions loaded = MinimapOptions.fromJson(options.toJson());
		assertFalse(loaded.enabled);
		assertEquals(MinimapOptions.CaveMode.ON, loaded.caveMode);
		assertTrue(loaded.showItems);
		assertEquals(112, loaded.size);
		assertEquals(2.0F, loaded.zoom);
	}

	@Test
	void invalidJsonKeepsDefaults() {
		MinimapOptions loaded = MinimapOptions.fromJson("{\"size\": 7, \"zoom\": 3.3, \"caveMode\": \"BANANA\", \"enabled\": false}");
		assertEquals(96, loaded.size);
		assertEquals(1.0F, loaded.zoom);
		assertEquals(MinimapOptions.CaveMode.AUTO, loaded.caveMode);
		assertFalse(loaded.enabled);
		assertEquals(96, MinimapOptions.fromJson("nao e json").size);
	}

	@Test
	void zoomClampsAndVisibleBlocks() {
		MinimapOptions options = new MinimapOptions();
		options.zoomOut();
		options.zoomOut();
		assertEquals(0.5F, options.zoom);
		assertEquals(192, options.visibleBlocks()); // 96px / 0.5
		for (int i = 0; i < 10; i++) {
			options.zoomIn();
		}
		assertEquals(4.0F, options.zoom);
		assertEquals(24, options.visibleBlocks()); // 96px / 4
	}

	@Test
	void caveModeCycles() {
		assertEquals(MinimapOptions.CaveMode.ON, MinimapOptions.CaveMode.AUTO.next());
		assertEquals(MinimapOptions.CaveMode.AUTO, MinimapOptions.CaveMode.OFF.next());
	}
}
