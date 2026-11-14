package com.mlin.notas.accessories;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessorySlotsTest {
	private static List<AccessoryDef> empty() {
		return new ArrayList<>(Arrays.asList(new AccessoryDef[AccessorySlots.SIZE]));
	}

	@Test
	void layoutHasTwoRingsAndOneOfEachOther() {
		assertEquals(6, AccessorySlots.SIZE);
		assertEquals(2, AccessorySlots.LAYOUT.stream().filter(t -> t == AccessorySlotType.RING).count());
		for (AccessorySlotType type : AccessorySlotType.values()) {
			assertTrue(AccessorySlots.LAYOUT.contains(type), type.id());
		}
	}

	@Test
	void slotsOnlyAcceptTheirType() {
		AccessoryDef ring = AccessoryCatalog.byId("iron_ring");
		AccessoryDef belt = AccessoryCatalog.byId("traveler_belt");
		assertTrue(AccessorySlots.fits(0, ring));
		assertTrue(AccessorySlots.fits(1, ring));
		assertFalse(AccessorySlots.fits(2, ring));
		assertTrue(AccessorySlots.fits(3, belt));
		assertFalse(AccessorySlots.fits(0, belt));
		assertFalse(AccessorySlots.fits(0, null));
		assertFalse(AccessorySlots.fits(9, ring));
	}

	@Test
	void uniqueArtifactsCannotBeDoubled() {
		List<AccessoryDef> equipped = empty();
		AccessoryDef fang = AccessoryCatalog.byId("vampire_fang");
		equipped.set(0, fang);
		assertFalse(AccessorySlots.canPlace(equipped, 1, fang));
		// trocar no proprio encaixe pode
		assertTrue(AccessorySlots.canPlace(equipped, 0, fang));
		// aneis basicos iguais podem
		AccessoryDef copper = AccessoryCatalog.byId("copper_ring");
		equipped.set(0, copper);
		assertTrue(AccessorySlots.canPlace(equipped, 1, copper));
	}

	@Test
	void firstFreeFillsRingsInOrder() {
		List<AccessoryDef> equipped = empty();
		AccessoryDef ring = AccessoryCatalog.byId("gold_ring");
		assertEquals(0, AccessorySlots.firstFree(equipped, ring));
		equipped.set(0, ring);
		assertEquals(1, AccessorySlots.firstFree(equipped, ring));
		equipped.set(1, ring);
		assertEquals(-1, AccessorySlots.firstFree(equipped, ring));
		assertEquals(5, AccessorySlots.firstFree(equipped, AccessoryCatalog.byId("pocket_watch")));
		assertEquals(4, AccessorySlots.firstFree(equipped, AccessoryCatalog.byId("climbing_glove")));
	}
}
