package com.mlin.notas.grave;

import com.mlin.notas.grave.GravePlacement.Cell;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GravePlacementTest {
	private static final int MIN_Y = -64;
	private static final int MAX_Y = 319;

	/** Mundo de teste: tudo OPEN acima de groundY, SOLID abaixo, com celulas especiais por cima. */
	static final class Grid implements GravePlacement.World {
		final Map<Long, Cell> cells = new HashMap<>();
		int groundY;
		Cell below = Cell.SOLID;
		Cell above = Cell.OPEN;

		Grid(int groundY) {
			this.groundY = groundY;
		}

		Grid set(int x, int y, int z, Cell cell) {
			cells.put(key(x, y, z), cell);
			return this;
		}

		Grid fill(int x1, int y1, int z1, int x2, int y2, int z2, Cell cell) {
			for (int x = x1; x <= x2; x++) {
				for (int y = y1; y <= y2; y++) {
					for (int z = z1; z <= z2; z++) {
						set(x, y, z, cell);
					}
				}
			}
			return this;
		}

		@Override
		public Cell at(int x, int y, int z) {
			Cell cell = cells.get(key(x, y, z));
			if (cell != null) {
				return cell;
			}
			return y <= groundY ? below : above;
		}

		static long key(int x, int y, int z) {
			return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0xFFFFF) << 22 | (long) z & 0x3FFFFF;
		}
	}

	@Test
	void plainGroundKeepsTheDeathSpot() {
		Grid grid = new Grid(63);
		GravePlacement.Result result = GravePlacement.find(grid, 5, 64, 7, MIN_Y, MAX_Y, null);
		assertTrue(result.safe());
		assertEquals(new GravePlacement.Pos(5, 64, 7), result.pos());
	}

	@Test
	void midAirDropsToTheGroundBelow() {
		Grid grid = new Grid(63);
		GravePlacement.Result result = GravePlacement.find(grid, 0, 70, 0, MIN_Y, MAX_Y, null);
		assertEquals(new GravePlacement.Pos(0, 64, 0), result.pos());
	}

	@Test
	void lavaLakeGoesToTheShore() {
		Grid grid = new Grid(63);
		// lago de lava 9x9 com 3 de fundo; o jogador morre no meio
		grid.fill(-4, 61, -4, 4, 63, 4, Cell.LAVA);
		GravePlacement.Result result = GravePlacement.find(grid, 0, 62, 0, MIN_Y, MAX_Y, null);
		assertTrue(result.safe());
		GravePlacement.Pos pos = result.pos();
		assertEquals(64, pos.y());
		assertTrue(Math.abs(pos.x()) > 4 || Math.abs(pos.z()) > 4, "fora do lago: " + pos);
		assertEquals(Cell.SOLID, grid.at(pos.x(), pos.y() - 1, pos.z()));
	}

	@Test
	void prefersSpotsNotTouchingLava() {
		Grid grid = new Grid(63);
		grid.fill(-4, 61, -4, 4, 63, 4, Cell.LAVA);
		GravePlacement.Pos pos = GravePlacement.find(grid, 0, 62, 0, MIN_Y, MAX_Y, null).pos();
		// a margem encostada no lago (|x| = 5) custa mais que a seguinte
		assertFalse(grid.at(pos.x() + 1, pos.y(), pos.z()) == Cell.LAVA || grid.at(pos.x() - 1, pos.y(), pos.z()) == Cell.LAVA
				|| grid.at(pos.x(), pos.y(), pos.z() + 1) == Cell.LAVA || grid.at(pos.x(), pos.y(), pos.z() - 1) == Cell.LAVA);
	}

	@Test
	void underwaterGoesToTheBankOrSeaFloorAir() {
		Grid grid = new Grid(40);
		// oceano de agua de y 41 a 62 numa area grande; a margem fica longe
		grid.fill(-30, 41, -30, 30, 62, 30, Cell.WATER);
		GravePlacement.Result result = GravePlacement.find(grid, 0, 50, 0, MIN_Y, MAX_Y, null);
		assertNotNull(result);
		// o primeiro lugar seguro na coluna fica acima da agua? Nao ha chao la: cai no ultimo recurso flutuando acima da agua
		assertFalse(result.safe());
		assertEquals(new GravePlacement.Pos(0, 63, 0), result.pos());
	}

	@Test
	void underwaterNearShoreFindsTheShore() {
		Grid grid = new Grid(62);
		grid.fill(-6, 55, -6, 6, 62, 6, Cell.WATER);
		GravePlacement.Result result = GravePlacement.find(grid, 0, 58, 0, MIN_Y, MAX_Y, null);
		assertTrue(result.safe());
		assertEquals(63, result.pos().y());
	}

	@Test
	void insideBlocksFindsTheNearestAir() {
		Grid grid = new Grid(80);
		// jogador sufocado dentro da pedra em y 70; caverna pequena a 3 blocos
		grid.fill(3, 70, 0, 4, 71, 0, Cell.OPEN);
		GravePlacement.Result result = GravePlacement.find(grid, 0, 70, 0, MIN_Y, MAX_Y, null);
		assertEquals(new GravePlacement.Pos(3, 70, 0), result.pos());
	}

	@Test
	void neverUsesBlockedCells() {
		Grid grid = new Grid(63);
		// bau/bloco do jogador no lugar da morte
		grid.set(0, 64, 0, Cell.BLOCKED);
		GravePlacement.Pos pos = GravePlacement.find(grid, 0, 64, 0, MIN_Y, MAX_Y, null).pos();
		assertNotEquals(new GravePlacement.Pos(0, 64, 0), pos);
		assertEquals(Cell.OPEN, grid.at(pos.x(), pos.y(), pos.z()));
	}

	@Test
	void voidDeathUsesLastGroundFirst() {
		Grid grid = new Grid(Integer.MIN_VALUE);
		grid.below = Cell.OPEN;
		// ilha do End: uma plataforma em y 60 perto de (100, 100)
		grid.fill(95, 60, 95, 105, 60, 105, Cell.SOLID);
		GravePlacement.Result result = GravePlacement.find(grid, 120, -80, 120, 0, 255, new GravePlacement.Pos(104, 61, 104));
		assertTrue(result.safe());
		assertEquals(new GravePlacement.Pos(104, 61, 104), result.pos());
	}

	@Test
	void voidDeathWithoutLastGroundTakesTheHighestSpotInTheColumn() {
		Grid grid = new Grid(Integer.MIN_VALUE);
		grid.below = Cell.OPEN;
		grid.set(0, 20, 0, Cell.SOLID);
		grid.set(0, 50, 0, Cell.SOLID);
		GravePlacement.Result result = GravePlacement.find(grid, 0, -100, 0, 0, 255, null);
		assertEquals(new GravePlacement.Pos(0, 51, 0), result.pos());
	}

	@Test
	void respectsTheCeilingHeight() {
		// Nether: mar de lava ate y 31, teto (pedra) de 100 a 127; acima do teto (128+) ha "chao" que nao pode ser usado
		Grid grid = new Grid(31);
		grid.below = Cell.LAVA;
		grid.fill(-40, 100, -40, 40, 127, 40, Cell.SOLID);
		GravePlacement.Result result = GravePlacement.find(grid, 0, 25, 0, 0, 127, null);
		assertNotNull(result);
		assertTrue(result.pos().y() < 100, "nunca acima do teto: " + result.pos());
		// sem chao seguro: flutua no primeiro ar acima da lava
		assertFalse(result.safe());
		assertEquals(32, result.pos().y());
	}

	@Test
	void netherShoreBeatsFloatingOverLava() {
		Grid grid = new Grid(31);
		grid.below = Cell.LAVA;
		grid.fill(10, 0, -5, 20, 35, 5, Cell.SOLID);
		GravePlacement.Result result = GravePlacement.find(grid, 0, 25, 0, 0, 127, null);
		assertTrue(result.safe());
		assertEquals(36, result.pos().y());
		assertTrue(result.pos().x() >= 10);
	}

	@Test
	void staysInsideWorldBounds() {
		Grid grid = new Grid(-65);
		grid.below = Cell.OPEN;
		// mundo todo aberto: nada de chao; morte acima do topo
		GravePlacement.Result result = GravePlacement.find(grid, 0, 400, 0, MIN_Y, MAX_Y, null);
		assertNotNull(result);
		assertTrue(result.pos().y() >= MIN_Y && result.pos().y() <= MAX_Y);
	}

	@Test
	void solidColumnEverywhereGivesUp() {
		Grid grid = new Grid(1000);
		grid.below = Cell.BLOCKED;
		assertNull(GravePlacement.find(grid, 0, 64, 0, MIN_Y, MAX_Y, null));
	}

	@Test
	void doesNotPlaceUnderLava() {
		Grid grid = new Grid(63);
		grid.set(0, 65, 0, Cell.LAVA);
		GravePlacement.Pos pos = GravePlacement.find(grid, 0, 64, 0, MIN_Y, MAX_Y, null).pos();
		assertNotEquals(new GravePlacement.Pos(0, 64, 0), pos);
	}
}
