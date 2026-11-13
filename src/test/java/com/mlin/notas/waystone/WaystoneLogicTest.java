package com.mlin.notas.waystone;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaystoneLogicTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	@Test
	void costIsOneLevelPer500BlocksWithMinimumOne() {
		WaystoneInfo home = new WaystoneInfo(OVERWORLD, 0, 64, 0, "Casa");
		assertEquals(1, WaystoneCost.levels(home, new WaystoneInfo(OVERWORLD, 10, 70, 0, "Perto")));
		assertEquals(1, WaystoneCost.levels(home, new WaystoneInfo(OVERWORLD, 500, 64, 0, "500")));
		assertEquals(2, WaystoneCost.levels(home, new WaystoneInfo(OVERWORLD, 501, 64, 0, "501")));
		assertEquals(3, WaystoneCost.levels(home, new WaystoneInfo(OVERWORLD, 900, 64, 1200, "1500")));
		assertEquals(20, WaystoneCost.levels(home, new WaystoneInfo(OVERWORLD, -10000, 64, 0, "Longe")));
	}

	@Test
	void crossDimensionCostsThreeAndCreativeIsFree() {
		WaystoneInfo home = new WaystoneInfo(OVERWORLD, 0, 64, 0, "Casa");
		WaystoneInfo nether = new WaystoneInfo(NETHER, 0, 64, 0, "Nether");
		assertEquals(3, WaystoneCost.levels(home, nether));
		assertEquals(3, WaystoneCost.levels(home, new WaystoneInfo(NETHER, 99999, 64, 0, "Longe")));
		assertEquals(0, WaystoneCost.charged(home, nether, true));
		assertEquals(3, WaystoneCost.charged(home, nether, false));
		assertTrue(WaystoneCost.canAfford(3, 3));
		assertFalse(WaystoneCost.canAfford(2, 3));
	}

	@Test
	void activationIsSharedAndKeepsTheFirstName() {
		WaystoneRegistry registry = new WaystoneRegistry();
		assertTrue(registry.activate(new WaystoneInfo(OVERWORLD, 1, 2, 3, "Vila")));
		assertFalse(registry.activate(new WaystoneInfo(OVERWORLD, 1, 2, 3, "Outro")), "ja ativada");
		assertEquals("Vila", registry.get(OVERWORLD, 1, 2, 3).orElseThrow().name());
		assertTrue(registry.isActive(OVERWORLD, 1, 2, 3));
		assertFalse(registry.isActive(NETHER, 1, 2, 3), "a dimensao faz parte da chave");
	}

	@Test
	void renameAndRemove() {
		WaystoneRegistry registry = new WaystoneRegistry();
		registry.activate(new WaystoneInfo(OVERWORLD, 0, 0, 0, "A"));
		assertTrue(registry.rename(OVERWORLD, 0, 0, 0, "  Base   principal "));
		assertEquals("Base principal", registry.get(OVERWORLD, 0, 0, 0).orElseThrow().name());
		assertFalse(registry.rename(OVERWORLD, 0, 0, 0, "   "), "nome vazio");
		assertFalse(registry.rename(OVERWORLD, 9, 9, 9, "X"), "pedra nao ativada");
		registry.setLastUsed("p1", registry.get(OVERWORLD, 0, 0, 0).orElseThrow());
		assertTrue(registry.lastUsed("p1").isPresent());
		assertTrue(registry.remove(OVERWORLD, 0, 0, 0));
		assertTrue(registry.lastUsed("p1").isEmpty(), "pedra quebrada nao e mais destino do pergaminho");
		assertFalse(registry.remove(OVERWORLD, 0, 0, 0));
	}

	@Test
	void namesAreCleanedAndLimited() {
		assertThrows(IllegalArgumentException.class, () -> new WaystoneInfo(OVERWORLD, 0, 0, 0, " "));
		assertEquals(WaystoneInfo.MAX_NAME, new WaystoneInfo(OVERWORLD, 0, 0, 0, "x".repeat(80)).name().length());
		String generated = WaystoneNames.generate(WaystoneNames.seed(OVERWORLD, 10, 64, -20));
		assertFalse(generated.isBlank());
		assertEquals(generated, WaystoneNames.generate(WaystoneNames.seed(OVERWORLD, 10, 64, -20)), "mesma posicao, mesmo nome");
	}

	@Test
	void destinationsListSameDimensionByDistanceThenOthers() {
		WaystoneInfo source = new WaystoneInfo(OVERWORLD, 0, 64, 0, "Aqui");
		WaystoneInfo far = new WaystoneInfo(OVERWORLD, 2000, 64, 0, "Longe");
		WaystoneInfo near = new WaystoneInfo(OVERWORLD, 100, 64, 0, "Perto");
		WaystoneInfo nether = new WaystoneInfo(NETHER, 0, 64, 0, "Inferno");
		List<WaystoneInfo> list = WaystoneRegistry.destinations(List.of(far, nether, source, near), source);
		assertEquals(List.of(near, far, nether), list);
	}

	@Test
	void registryRoundTripsThroughItsLists() {
		WaystoneRegistry registry = new WaystoneRegistry();
		WaystoneInfo a = new WaystoneInfo(OVERWORLD, 0, 64, 0, "A");
		registry.activate(a);
		registry.setLastUsed("uuid", a);
		WaystoneRegistry copy = new WaystoneRegistry(registry.all(), registry.lastUsedMap());
		assertEquals(registry.all(), copy.all());
		assertEquals(Map.of("uuid", a.key()), copy.lastUsedMap());
		assertEquals(a, copy.lastUsed("uuid").orElseThrow());
	}
}
