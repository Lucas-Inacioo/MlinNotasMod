package com.mlin.notas.nether;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkPathTest {
	/** Grade plana em y=50 (pes em 51) de w x d. */
	private static WalkGrid flat(int w, int d, int feet) {
		WalkGrid grid = new WalkGrid(w, d);
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				grid.setLevels(x, z, feet);
			}
		}
		return grid;
	}

	@Test
	void flatGroundConnectsOppositeCorners() {
		WalkPath path = new WalkPath(flat(16, 16, 51));
		assertTrue(path.mutual(path.floor(0, 0), path.floor(15, 15)));
		assertEquals(1.0, path.connectedPairFraction(20, 1L));
	}

	@Test
	void stepOfOneBlockIsWalkableBothWays() {
		WalkGrid grid = flat(4, 1, 51);
		grid.setLevels(2, 0, 52);
		grid.setLevels(3, 0, 53);
		WalkPath path = new WalkPath(grid);
		assertTrue(path.mutual(path.floor(0, 0), path.floor(3, 0)));
	}

	@Test
	void wallOfTwoBlocksBlocks() {
		WalkGrid grid = flat(3, 1, 51);
		grid.setLevels(1, 0, 53);
		WalkPath path = new WalkPath(grid);
		boolean[] reach = path.reachableFrom(path.floor(0, 0));
		assertFalse(path.reaches(reach, path.floor(1, 0)), "nao sobe 2 blocos");
		assertFalse(path.reaches(reach, path.floor(2, 0)));
	}

	@Test
	void dropOfThreeIsOneWay() {
		WalkGrid grid = new WalkGrid(2, 1);
		grid.setLevels(0, 0, 54);
		grid.setLevels(1, 0, 51);
		WalkPath path = new WalkPath(grid);
		assertTrue(path.reaches(path.reachableFrom(path.floor(0, 0)), path.floor(1, 0)), "desce 3");
		assertFalse(path.reaches(path.reachableFrom(path.floor(1, 0)), path.floor(0, 0)), "nao sobe 3");
		assertFalse(path.mutual(path.floor(0, 0), path.floor(1, 0)));
	}

	@Test
	void dropOfFourIsTooHigh() {
		WalkGrid grid = new WalkGrid(2, 1);
		grid.setLevels(0, 0, 55);
		grid.setLevels(1, 0, 51);
		WalkPath path = new WalkPath(grid);
		assertFalse(path.reaches(path.reachableFrom(path.floor(0, 0)), path.floor(1, 0)));
	}

	@Test
	void lavaRiverSplitsTheMap() {
		WalkGrid grid = flat(9, 9, 51);
		boolean[] lavaSolid = new boolean[8];
		boolean[] lavaHazard = new boolean[8];
		lavaSolid[0] = true;
		lavaHazard[1] = true; // lava em cima do chao
		for (int z = 0; z < 9; z++) {
			grid.setColumn(4, z, 49, lavaSolid, lavaHazard);
		}
		WalkPath path = new WalkPath(grid);
		assertTrue(grid.levels(4, 0).length == 0, "lava nao e chao");
		assertFalse(path.mutual(path.floor(0, 0), path.floor(8, 8)));
		double fraction = path.connectedPairFraction(60, 7L);
		assertTrue(fraction < 0.75, "dois lados separados: " + fraction);
	}

	@Test
	void riverWithAFordIsCrossable() {
		WalkGrid grid = flat(9, 9, 51);
		for (int z = 0; z < 9; z++) {
			if (z != 6) {
				grid.setLevels(4, z);
			}
		}
		WalkPath path = new WalkPath(grid);
		assertTrue(path.mutual(path.floor(0, 0), path.floor(8, 0)));
	}

	@Test
	void stepUpNeedsHeadroomToJump() {
		WalkGrid grid = new WalkGrid(2, 1);
		boolean[] solid = new boolean[6];
		boolean[] hazard = new boolean[6];
		// chao em 50, pes 51-52 livres, teto em 53: so 2 de espaco, nao da para pular
		solid[0] = true;
		solid[3] = true;
		grid.setColumn(0, 0, 50, solid, hazard);
		grid.setLevels(1, 0, 52);
		WalkPath path = new WalkPath(grid);
		assertEquals(2, grid.clearance(0, 0, 0));
		assertFalse(path.reaches(path.reachableFrom(path.floor(0, 0)), path.floor(1, 0)));
	}

	@Test
	void setColumnFindsEveryStandingSpot() {
		WalkGrid grid = new WalkGrid(1, 1);
		// 0 chao, 1-2 ar, 3 bloco, 4-9 ar
		boolean[] solid = {true, false, false, true, false, false, false, false, false, false};
		grid.setColumn(0, 0, 40, solid, new boolean[10]);
		int[] levels = grid.levels(0, 0);
		assertEquals(2, levels.length);
		assertEquals(41, levels[0]);
		assertEquals(44, levels[1]);
		assertEquals(41, grid.floor(0, 0));
		assertEquals(WalkGrid.MAX_CLEARANCE, grid.clearance(0, 0, 1));
	}

	@Test
	void nearestFloorSkipsHoles() {
		WalkGrid grid = new WalkGrid(5, 5);
		grid.setLevels(3, 3, 60);
		WalkPath path = new WalkPath(grid);
		assertNull(path.floor(0, 0));
		WalkPath.Node node = path.nearestFloor(0, 0, 4);
		assertNotNull(node);
		assertEquals(3, node.x());
		assertEquals(60, grid.levels(node.x(), node.z())[node.level()]);
	}

	@Test
	void closestToPicksTheLevelNearTheGround() {
		WalkGrid grid = new WalkGrid(1, 1);
		grid.setLevels(0, 0, 40, 61, 75);
		WalkPath path = new WalkPath(grid);
		assertEquals(1, path.closestTo(0, 0, 60, 4).level());
		assertNull(path.closestTo(0, 0, 50, 4), "nada a 4 blocos de 50");
		assertEquals(1.0, path.connectedPairFraction(java.util.List.of(path.floor(0, 0))));
	}
}
