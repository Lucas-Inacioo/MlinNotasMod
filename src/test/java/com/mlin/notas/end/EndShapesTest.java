package com.mlin.notas.end;

import com.mlin.notas.end.EndShapes.Cell;
import com.mlin.notas.end.EndShapes.Part;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndShapesTest {
	@Test
	void voidTreeHasTrunkAndWideCanopy() {
		for (int seed = 0; seed < 20; seed++) {
			List<Cell> tree = EndShapes.voidTree(new Random(seed));
			int top = tree.stream().filter(c -> c.part() == Part.SOLID).mapToInt(Cell::y).max().orElseThrow();
			assertTrue(top >= 3 && top <= 7, "tronco de 4 a 7, topo em " + top);
			assertTrue(tree.stream().anyMatch(c -> c.x() == 0 && c.y() == 0 && c.z() == 0 && c.part() == Part.SOLID), "tronco na origem");
			long leaves = tree.stream().filter(c -> c.part() == Part.LEAVES).count();
			assertTrue(leaves >= 20, "copa cheia: " + leaves);
			int width = tree.stream().filter(c -> c.part() == Part.LEAVES).mapToInt(Cell::x).max().orElseThrow()
					- tree.stream().filter(c -> c.part() == Part.LEAVES).mapToInt(Cell::x).min().orElseThrow();
			assertTrue(width >= 4, "copa larga: " + width);
		}
	}

	@Test
	void crystalSpikeTapersToATip() {
		for (int h = 6; h <= 14; h++) {
			final int height = h;
			List<Cell> spike = EndShapes.crystalSpike(new Random(height), height);
			assertEquals(1, spike.stream().filter(c -> c.part() == Part.TIP).count());
			assertEquals(height, spike.stream().filter(c -> c.part() == Part.TIP).findFirst().orElseThrow().y());
			long base = spike.stream().filter(c -> c.y() == 0).count();
			long near = spike.stream().filter(c -> c.y() == height - 1).count();
			assertTrue(base > near, "mais largo na base (" + base + ") que no topo (" + near + ")");
			assertTrue(spike.stream().anyMatch(c -> c.y() < 0), "enterrado no chao");
		}
	}

	@Test
	void geodeHasShellCrystalAndHollowCore() {
		List<Cell> geode = EndShapes.geode(5);
		assertTrue(geode.stream().anyMatch(c -> c.part() == Part.SHELL));
		assertTrue(geode.stream().anyMatch(c -> c.part() == Part.SOLID));
		assertTrue(geode.stream().anyMatch(c -> c.x() == 0 && c.y() == 0 && c.z() == 0 && c.part() == Part.AIR), "miolo vazio");
		assertTrue(geode.stream().allMatch(c -> c.y() <= 3), "aberto em cima");
	}

	@Test
	void craterIsDeepestInTheMiddleWithARim() {
		List<EndShapes.CraterColumn> crater = EndShapes.crater(new Random(3), 6, 4);
		int center = crater.stream().filter(c -> c.x() == 0 && c.z() == 0).findFirst().orElseThrow().dig();
		assertEquals(4, center);
		assertTrue(crater.stream().filter(c -> !c.rim()).allMatch(c -> c.dig() <= center && c.dig() >= 1));
		assertTrue(crater.stream().anyMatch(EndShapes.CraterColumn::rim), "borda levantada");
	}

	@Test
	void fossilHasSpineRibsAndSkull() {
		List<Cell> fossil = EndShapes.fossil(new Random(1), 12);
		for (int x = 0; x < 12; x++) {
			final int fx = x;
			assertTrue(fossil.stream().anyMatch(c -> c.x() == fx && c.y() == 4 && c.z() == 0), "vertebra em x=" + x);
		}
		assertTrue(fossil.stream().anyMatch(c -> c.z() >= 3), "costelas para os lados");
		assertTrue(fossil.stream().anyMatch(c -> c.part() == Part.AIR), "orbitas do cranio");
		assertTrue(fossil.stream().allMatch(c -> c.y() >= 0), "nada abaixo da base");
	}

	@Test
	void basaltPillarsAndPond() {
		for (int seed = 0; seed < 10; seed++) {
			List<Cell> pillars = EndShapes.basaltPillars(new Random(seed));
			int top = pillars.stream().mapToInt(Cell::y).max().orElseThrow();
			assertTrue(top >= 5 && top <= 9, "pilar principal de 6 a 10, topo " + top);
			List<Cell> pond = EndShapes.pond(new Random(seed));
			assertTrue(pond.stream().allMatch(c -> c.part() == Part.WATER && c.y() < 0 && c.y() >= -2), "lago raso");
			assertTrue(pond.size() >= 12, "lago com pelo menos 12 blocos de agua: " + pond.size());
		}
	}
}
