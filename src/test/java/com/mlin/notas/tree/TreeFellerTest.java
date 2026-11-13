package com.mlin.notas.tree;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeFellerTest {
	private static Set<Pos> column(int x, int z, int fromY, int toY) {
		Set<Pos> set = new HashSet<>();
		for (int y = fromY; y <= toY; y++) {
			set.add(new Pos(x, y, z));
		}
		return set;
	}

	@Test
	void collectsConnectedLogsBottomUp() {
		Set<Pos> logs = column(0, 0, 0, 5);
		Set<Pos> leaves = Set.of(new Pos(1, 5, 0));
		TreeFeller.Result result = TreeFeller.fell(new Pos(0, 0, 0), logs::contains, leaves::contains, 256);
		assertTrue(result.isTree());
		assertEquals(5, result.logs().size());
		for (int i = 0; i < result.logs().size(); i++) {
			assertEquals(i + 1, result.logs().get(i).y(), "ordem deve ser de baixo para cima");
		}
	}

	@Test
	void followsDiagonalBranches() {
		Set<Pos> logs = new HashSet<>(column(0, 0, 0, 3));
		logs.add(new Pos(1, 4, 1));
		logs.add(new Pos(2, 5, 2));
		TreeFeller.Result result = TreeFeller.fell(new Pos(0, 0, 0), logs::contains, p -> p.equals(new Pos(2, 6, 2)), 256);
		assertEquals(5, result.logs().size());
		assertTrue(result.logs().contains(new Pos(2, 5, 2)));
	}

	@Test
	void withoutLeavesIsNotATree() {
		Set<Pos> logs = column(0, 0, 0, 3);
		TreeFeller.Result result = TreeFeller.fell(new Pos(0, 0, 0), logs::contains, p -> false, 256);
		assertFalse(result.isTree());
		assertEquals(3, result.logs().size());
	}

	@Test
	void respectsMaxLogs() {
		Set<Pos> logs = column(0, 0, 0, 100);
		TreeFeller.Result result = TreeFeller.fell(new Pos(0, 0, 0), logs::contains, p -> true, 10);
		assertEquals(10, result.logs().size());
	}

	@Test
	void ignoresLogsFarHorizontally() {
		Set<Pos> logs = new HashSet<>();
		for (int x = 0; x <= 20; x++) {
			logs.add(new Pos(x, 0, 0));
		}
		TreeFeller.Result result = TreeFeller.fell(new Pos(0, 0, 0), logs::contains, p -> true, 256);
		List<Pos> found = result.logs();
		assertEquals(TreeFeller.MAX_HORIZONTAL_RADIUS, found.size());
	}

	@Test
	void doesNotIncludeStart() {
		Set<Pos> logs = column(0, 0, 0, 1);
		TreeFeller.Result result = TreeFeller.fell(new Pos(0, 0, 0), logs::contains, p -> true, 256);
		assertFalse(result.logs().contains(new Pos(0, 0, 0)));
	}
}
