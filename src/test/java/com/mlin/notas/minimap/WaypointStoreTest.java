package com.mlin.notas.minimap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointStoreTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	@Test
	void addRemoveAndReplace() {
		WaypointStore store = new WaypointStore();
		Waypoint base = new Waypoint("Base", 10, 64, -20, Waypoint.PALETTE[0], OVERWORLD);
		store.add(base);
		assertEquals(1, store.size());

		Waypoint renamed = base.withName("Casa");
		assertTrue(store.replace(base, renamed));
		assertEquals("Casa", store.get(0).name());
		assertTrue(store.findByName("casa").isPresent());

		assertTrue(store.remove(renamed));
		assertEquals(0, store.size());
		assertFalse(store.remove(renamed));
	}

	@Test
	void filtersByDimension() {
		WaypointStore store = new WaypointStore();
		store.add(new Waypoint("A", 0, 0, 0, 0xFFFFFFFF, OVERWORLD));
		store.add(new Waypoint("B", 0, 0, 0, 0xFFFFFFFF, NETHER));
		store.add(new Waypoint("C", 0, 0, 0, 0xFFFFFFFF, OVERWORLD));
		assertEquals(2, store.inDimension(OVERWORLD).size());
		assertEquals("B", store.inDimension(NETHER).get(0).name());
	}

	@Test
	void jsonRoundTrip() {
		WaypointStore store = new WaypointStore();
		store.add(new Waypoint("Mina", 100, 12, -300, 0xFF55FF55, OVERWORLD));
		store.add(new Waypoint("Fortaleza", -5, 70, 8, 0xFFFF5555, NETHER));
		String json = store.toJson();
		WaypointStore loaded = WaypointStore.fromJson(json);
		assertEquals(store.all(), loaded.all());
	}

	@Test
	void jsonSkipsCorruptEntriesAndEmptyInput() {
		String json = "{\"version\":1,\"waypoints\":[{\"name\":\"Ok\",\"x\":1,\"y\":2,\"z\":3,\"color\":\"#FF0000\",\"dimension\":\"minecraft:overworld\"},{\"name\":\"semCoord\"},5]}";
		WaypointStore loaded = WaypointStore.fromJson(json);
		assertEquals(1, loaded.size());
		assertEquals(0xFFFF0000, loaded.get(0).color());
		assertEquals(0, WaypointStore.fromJson("").size());
		assertEquals(0, WaypointStore.fromJson("[]").size());
	}

	@Test
	void colorParsingFallsBackToPalette() {
		assertEquals(Waypoint.PALETTE[0], WaypointStore.parseColor("xyz"));
		assertEquals(0xFF123456, WaypointStore.parseColor("123456"));
	}

	@Test
	void waypointValidation() {
		assertThrows(IllegalArgumentException.class, () -> new Waypoint("  ", 0, 0, 0, 0, OVERWORLD));
		assertEquals("Casa", new Waypoint("  Casa ", 0, 0, 0, 0, OVERWORLD).name());
		assertEquals(Waypoint.PALETTE[1], Waypoint.nextColor(Waypoint.PALETTE[0]));
		assertEquals(Waypoint.PALETTE[0], Waypoint.nextColor(Waypoint.PALETTE[Waypoint.PALETTE.length - 1]));
		assertEquals(5.0, new Waypoint("P", 3, 0, 4, 0, OVERWORLD).horizontalDistance(0.5, 0.5), 1e-9);
	}
}
