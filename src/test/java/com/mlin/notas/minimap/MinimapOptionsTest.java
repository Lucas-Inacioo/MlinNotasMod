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
		assertEquals(128, loaded.size);
		assertEquals(1.0F, loaded.zoom);
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
		assertEquals(160, loaded.size);
		assertEquals(2.0F, loaded.zoom);
	}

	@Test
	void invalidJsonKeepsDefaults() {
		MinimapOptions loaded = MinimapOptions.fromJson("{\"size\": 7, \"zoom\": 3.3, \"caveMode\": \"BANANA\", \"enabled\": false}");
		assertEquals(128, loaded.size);
		assertEquals(1.0F, loaded.zoom);
		assertEquals(MinimapOptions.CaveMode.AUTO, loaded.caveMode);
		assertFalse(loaded.enabled);
		assertEquals(128, MinimapOptions.fromJson("nao e json").size);
	}

	@Test
	void zoomClampsAndVisibleBlocks() {
		MinimapOptions options = new MinimapOptions();
		options.zoomOut();
		options.zoomOut();
		assertEquals(0.5F, options.zoom);
		assertEquals(256, options.visibleBlocks());
		for (int i = 0; i < 10; i++) {
			options.zoomIn();
		}
		assertEquals(4.0F, options.zoom);
		assertEquals(32, options.visibleBlocks());
	}

	@Test
	void caveModeCycles() {
		assertEquals(MinimapOptions.CaveMode.ON, MinimapOptions.CaveMode.AUTO.next());
		assertEquals(MinimapOptions.CaveMode.AUTO, MinimapOptions.CaveMode.OFF.next());
	}
}
