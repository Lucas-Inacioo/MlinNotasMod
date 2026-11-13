package com.mlin.notas.vein;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinPlannerTest {
	@Test
	void deepslateVariantsShareTheGroup() {
		assertEquals("minecraft:iron_ore", VeinPlanner.group("minecraft:deepslate_iron_ore"));
		assertEquals("minecraft:iron_ore", VeinPlanner.group("minecraft:iron_ore"));
		assertEquals("mlinnotas:cobalt_ore", VeinPlanner.group("mlinnotas:deepslate_cobalt_ore"));
	}

	@Test
	void sizeGrowsWithMiningLevelAndIsCapped() {
		assertEquals(8, VeinPlanner.maxBlocks(1));
		assertEquals(12, VeinPlanner.maxBlocks(2));
		assertEquals(52, VeinPlanner.maxBlocks(12));
		assertTrue(VeinPlanner.maxBlocks(100) <= VeinPlanner.HARD_CAP);
	}

	@Test
	void followsDiagonalsSameGroupOnlyNearestFirst() {
		Map<Pos, String> world = new HashMap<>();
		Pos start = new Pos(0, 0, 0);
		world.put(new Pos(1, 0, 0), "minecraft:iron_ore");
		world.put(new Pos(2, 1, 0), "minecraft:iron_ore"); // diagonal do anterior
		world.put(new Pos(0, 0, 1), "minecraft:coal_ore"); // outro grupo
		world.put(new Pos(0, 0, 5), "minecraft:iron_ore"); // desconectado
		List<Pos> plan = VeinPlanner.plan(start, "minecraft:iron_ore", world::get, 10);
		assertEquals(List.of(new Pos(1, 0, 0), new Pos(2, 1, 0)), plan);
		assertFalse(plan.contains(start));
	}

	@Test
	void respectsMaxAndRadius() {
		Map<Pos, String> world = new HashMap<>();
		for (int x = 1; x <= 20; x++) {
			world.put(new Pos(x, 0, 0), "minecraft:coal_ore");
		}
		assertEquals(5, VeinPlanner.plan(new Pos(0, 0, 0), "minecraft:coal_ore", world::get, 5).size());
		assertEquals(VeinPlanner.MAX_RADIUS, VeinPlanner.plan(new Pos(0, 0, 0), "minecraft:coal_ore", world::get, 64).size());
		assertTrue(VeinPlanner.plan(new Pos(0, 0, 0), "minecraft:coal_ore", world::get, 0).isEmpty());
	}
}
