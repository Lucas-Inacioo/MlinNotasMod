package com.mlin.notas.recipes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeIndexTest {
	private static final int PLANKS = 1;
	private static final int STICK = 2;
	private static final int CHEST = 3;
	private static final int RAW_IRON = 4;
	private static final int IRON = 5;
	private static final int OAK_LOG = 6;
	private static final int BIRCH_LOG = 7;

	private static final RecipeView STICKS = new RecipeView("minecraft:stick", RecipeKind.SHAPED, 1, 2,
			List.of(List.of(PLANKS), List.of(PLANKS)), STICK, 4, 0, 0);
	private static final RecipeView CHEST_RECIPE = new RecipeView("minecraft:chest", RecipeKind.SHAPED, 3, 3,
			List.of(List.of(PLANKS), List.of(PLANKS), List.of(PLANKS),
					List.of(PLANKS), List.of(), List.of(PLANKS),
					List.of(PLANKS), List.of(PLANKS), List.of(PLANKS)), CHEST, 1, 0, 0);
	private static final RecipeView SMELT = new RecipeView("minecraft:iron_ingot", RecipeKind.SMELTING, 0, 0,
			List.of(List.of(RAW_IRON)), IRON, 1, 200, 0.7F);
	private static final RecipeView PLANKS_RECIPE = new RecipeView("minecraft:oak_planks", RecipeKind.SHAPELESS, 0, 0,
			List.of(List.of(OAK_LOG, BIRCH_LOG)), PLANKS, 4, 0, 0);

	private static final RecipeIndex INDEX = new RecipeIndex(List.of(STICKS, CHEST_RECIPE, SMELT, PLANKS_RECIPE));

	@Test
	void findsRecipesByOutput() {
		assertEquals(List.of(STICKS), INDEX.recipesFor(STICK));
		assertEquals(List.of(SMELT), INDEX.recipesFor(IRON));
		assertTrue(INDEX.recipesFor(RAW_IRON).isEmpty());
	}

	@Test
	void findsUsagesWithoutDuplicates() {
		List<RecipeView> usages = INDEX.usagesOf(PLANKS);
		assertEquals(2, usages.size(), "planks usada em stick e chest, cada uma so uma vez");
		assertTrue(usages.contains(STICKS));
		assertTrue(usages.contains(CHEST_RECIPE));
	}

	@Test
	void tagAlternativesCountAsUsage() {
		assertEquals(List.of(PLANKS_RECIPE), INDEX.usagesOf(BIRCH_LOG));
		assertTrue(PLANKS_RECIPE.usesItem(OAK_LOG));
		assertFalse(PLANKS_RECIPE.usesItem(STICK));
	}

	@Test
	void gridSlotMapsShapedPattern() {
		assertEquals(List.of(PLANKS), STICKS.gridSlot(0, 0));
		assertEquals(List.of(PLANKS), STICKS.gridSlot(1, 0));
		assertTrue(STICKS.gridSlot(0, 1).isEmpty(), "fora da largura da receita");
		assertTrue(STICKS.gridSlot(2, 0).isEmpty(), "fora da altura da receita");
		assertTrue(CHEST_RECIPE.gridSlot(1, 1).isEmpty(), "centro do bau vazio");
	}

	@Test
	void gridSlotForShapelessFillsInOrder() {
		assertEquals(List.of(OAK_LOG, BIRCH_LOG), PLANKS_RECIPE.gridSlot(0, 0));
		assertTrue(PLANKS_RECIPE.gridSlot(0, 1).isEmpty());
	}

	@Test
	void emptyIndexHasNothing() {
		assertEquals(0, RecipeIndex.EMPTY.size());
		assertTrue(RecipeIndex.EMPTY.recipesFor(STICK).isEmpty());
		assertFalse(RecipeIndex.EMPTY.hasRecipes(STICK));
	}

	@Test
	void kindOrdinalRoundTrip() {
		for (RecipeKind kind : RecipeKind.values()) {
			assertEquals(kind, RecipeKind.byOrdinal(kind.ordinal()));
		}
		assertThrows(IllegalArgumentException.class, () -> RecipeKind.byOrdinal(99));
	}
}
