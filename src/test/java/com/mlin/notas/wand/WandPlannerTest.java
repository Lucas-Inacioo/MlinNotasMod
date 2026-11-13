package com.mlin.notas.wand;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandPlannerTest {
	private static final Pos UP = new Pos(0, 1, 0);
	private static final Pos NORTH = new Pos(0, 0, -1);

	@Test
	void extendsFloorUpwardsOneLayer() {
		Set<Pos> floor = new HashSet<>();
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				floor.add(new Pos(x, 0, z));
			}
		}
		List<Pos> targets = WandPlanner.plan(new Pos(1, 0, 1), UP, floor::contains, p -> !floor.contains(p), 64);
		assertEquals(9, targets.size());
		assertTrue(targets.stream().allMatch(p -> p.y() == 1));
		assertEquals(new Pos(1, 1, 1), targets.get(0), "primeiro alvo e o bloco em frente ao clicado");
	}

	@Test
	void extendsWallSideways() {
		Set<Pos> wall = new HashSet<>();
		for (int x = 0; x < 4; x++) {
			for (int y = 0; y < 2; y++) {
				wall.add(new Pos(x, y, 5));
			}
		}
		List<Pos> targets = WandPlanner.plan(new Pos(0, 0, 5), NORTH, wall::contains, p -> !wall.contains(p), 64);
		assertEquals(8, targets.size());
		assertTrue(targets.stream().allMatch(p -> p.z() == 4));
	}

	@Test
	void respectsLimit() {
		Set<Pos> floor = new HashSet<>();
		for (int x = 0; x < 10; x++) {
			floor.add(new Pos(x, 0, 0));
		}
		List<Pos> targets = WandPlanner.plan(new Pos(0, 0, 0), UP, floor::contains, p -> true, 3);
		assertEquals(3, targets.size());
	}

	@Test
	void skipsBlockedTargets() {
		Set<Pos> floor = Set.of(new Pos(0, 0, 0), new Pos(1, 0, 0), new Pos(2, 0, 0));
		Set<Pos> blocked = Set.of(new Pos(1, 1, 0));
		List<Pos> targets = WandPlanner.plan(new Pos(0, 0, 0), UP, floor::contains, p -> !blocked.contains(p), 64);
		assertEquals(List.of(new Pos(0, 1, 0)), targets, "bloqueio interrompe a continuidade do plano");
	}

	@Test
	void nothingWhenClickedFaceIsBlocked() {
		List<Pos> targets = WandPlanner.plan(new Pos(0, 0, 0), UP, p -> true, p -> false, 64);
		assertTrue(targets.isEmpty());
	}
}
