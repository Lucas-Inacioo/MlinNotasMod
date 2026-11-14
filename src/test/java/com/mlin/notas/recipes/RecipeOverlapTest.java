package com.mlin.notas.recipes;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeOverlapTest {
	private static final Set<Integer> E = Set.of();
	private static final Set<Integer> PLANK = Set.of(1, 2); // tag com dois itens
	private static final Set<Integer> OAK = Set.of(1);
	private static final Set<Integer> STICK = Set.of(3);
	private static final Set<Integer> IRON = Set.of(4);

	@SafeVarargs
	private static RecipeOverlap.Grid shaped(int w, int h, Set<Integer>... cells) {
		return RecipeOverlap.Grid.shaped(w, h, Arrays.asList(cells));
	}

	@SafeVarargs
	private static RecipeOverlap.Grid shapeless(Set<Integer>... cells) {
		return RecipeOverlap.Grid.shapeless(List.of(cells));
	}

	@Test
	void sameShapeWithSharedTagItemOverlaps() {
		var a = shaped(1, 2, PLANK, STICK);
		var b = shaped(1, 2, OAK, STICK);
		assertTrue(RecipeOverlap.overlaps(a, b));
	}

	@Test
	void differentDimensionsNeverOverlap() {
		assertFalse(RecipeOverlap.overlaps(shaped(1, 2, OAK, STICK), shaped(2, 1, OAK, STICK)));
	}

	@Test
	void mirroredShapeOverlaps() {
		// diagonal "/" e diagonal "\" sao a mesma receita espelhada
		var slash = shaped(2, 2, E, OAK, STICK, E);
		var backslash = shaped(2, 2, OAK, E, E, STICK);
		assertTrue(RecipeOverlap.overlaps(slash, backslash));
		var different = shaped(2, 2, OAK, E, E, IRON);
		assertFalse(RecipeOverlap.overlaps(slash, different));
	}

	@Test
	void emptyCellMustMatchEmptyCell() {
		assertFalse(RecipeOverlap.overlaps(shaped(2, 1, OAK, E), shaped(2, 1, OAK, STICK)));
	}

	@Test
	void shapelessNeedsPerfectMatching() {
		assertTrue(RecipeOverlap.overlaps(shapeless(OAK, STICK), shapeless(STICK, PLANK)));
		// dois espacos so aceitam carvalho, mas a outra so tem um espaco de tabua
		assertFalse(RecipeOverlap.overlaps(shapeless(OAK, OAK), shapeless(PLANK, STICK)));
		assertFalse(RecipeOverlap.overlaps(shapeless(OAK, STICK), shapeless(OAK, STICK, IRON)));
	}

	@Test
	void shapedAgainstShapelessIgnoresPositions() {
		assertTrue(RecipeOverlap.overlaps(shaped(2, 2, OAK, E, E, STICK), shapeless(STICK, PLANK)));
		assertFalse(RecipeOverlap.overlaps(shaped(2, 2, OAK, E, E, STICK), shapeless(STICK, IRON)));
	}
}
