package com.mlin.notas.nether;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BurningTreeShapeTest {
	private static long count(List<BurningTreeShape.Cell> cells, BurningTreeShape.Part part) {
		return cells.stream().filter(c -> c.part() == part).count();
	}

	@Test
	void tallTreeHasStemCanopyVinesAndLights() {
		for (long seed = 0; seed < 20; seed++) {
			List<BurningTreeShape.Cell> cells = BurningTreeShape.tree(16, new Random(seed));
			for (int y = 0; y < 16; y++) {
				int level = y;
				assertTrue(cells.stream().anyMatch(c -> c.x() == 0 && c.z() == 0 && c.y() == level && c.part() == BurningTreeShape.Part.STEM),
						"caule continuo em y=" + y);
			}
			assertTrue(count(cells, BurningTreeShape.Part.CAP) > 60, "copa larga");
			int widest = cells.stream().filter(c -> c.part() == BurningTreeShape.Part.CAP).mapToInt(c -> Math.max(Math.abs(c.x()), Math.abs(c.z())))
					.max().orElse(0);
			assertTrue(widest >= 5, "copa com raio >= 5, tem " + widest);
		}
		long vines = 0;
		long lights = 0;
		for (long seed = 0; seed < 20; seed++) {
			List<BurningTreeShape.Cell> cells = BurningTreeShape.tree(16, new Random(seed));
			vines += count(cells, BurningTreeShape.Part.VINE);
			lights += count(cells, BurningTreeShape.Part.LIGHT);
		}
		assertTrue(vines > 0, "cipos pendurados");
		assertTrue(lights > 0, "pontos de luz na copa");
	}

	@Test
	void canopyLeavesWalkingRoomUnderneath() {
		for (long seed = 0; seed < 50; seed++) {
			for (int height = 7; height <= 18; height++) {
				for (BurningTreeShape.Cell cell : BurningTreeShape.tree(height, new Random(seed))) {
					boolean solid = cell.part() == BurningTreeShape.Part.CAP || cell.part() == BurningTreeShape.Part.LIGHT;
					if (solid) {
						assertTrue(cell.y() >= BurningTreeShape.MIN_CANOPY_CLEARANCE, "copa baixa demais em " + cell + " (altura " + height + ")");
					}
					if (cell.part() == BurningTreeShape.Part.STEM && cell.y() == 0) {
						continue;
					}
					if (cell.part() == BurningTreeShape.Part.STEM) {
						assertTrue(cell.x() == 0 && cell.z() == 0, "so as raizes saem do eixo: " + cell);
					}
				}
			}
		}
	}

	@Test
	void vinesHangFromTheCanopy() {
		for (long seed = 0; seed < 30; seed++) {
			List<BurningTreeShape.Cell> cells = BurningTreeShape.tree(14, new Random(seed));
			Set<String> occupied = new HashSet<>();
			for (BurningTreeShape.Cell c : cells) {
				occupied.add(c.x() + "," + c.y() + "," + c.z());
			}
			for (BurningTreeShape.Cell c : cells) {
				if (c.part() == BurningTreeShape.Part.VINE) {
					assertTrue(occupied.contains(c.x() + "," + (c.y() + 1) + "," + c.z()), "cipo solto no ar: " + c);
				}
			}
		}
	}

	@Test
	void bushIsSmallBall() {
		List<BurningTreeShape.Cell> cells = BurningTreeShape.tree(4, new Random(1));
		assertEquals(0, count(cells, BurningTreeShape.Part.VINE));
		assertTrue(cells.stream().allMatch(c -> Math.abs(c.x()) <= 2 && Math.abs(c.z()) <= 2 && c.y() <= 4));
		assertThrows(IllegalArgumentException.class, () -> BurningTreeShape.tree(2, new Random(1)));
	}

	@Test
	void sameSeedSameTree() {
		assertEquals(BurningTreeShape.tree(15, new Random(9)), BurningTreeShape.tree(15, new Random(9)));
	}
}
