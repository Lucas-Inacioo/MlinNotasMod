package com.mlin.notas.food;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IngredientMatcherTest {
	private static int[] match(List<Predicate<String>> ingredients, List<String> items) {
		return IngredientMatcher.match(ingredients.size(), items.size(), (i, j) -> ingredients.get(i).test(items.get(j)));
	}

	@Test
	void matchesInAnyOrder() {
		int[] a = match(List.of("rice"::equals, "beans"::equals, "onion"::equals), List.of("onion", "rice", "beans"));
		assertArrayEquals(new int[] {1, 2, 0}, a);
	}

	@Test
	void everyItemMustBeUsedAndEachOnlyOnce() {
		assertNull(match(List.of("rice"::equals, "beans"::equals), List.of("rice", "beans", "onion")), "item sobrando");
		assertNull(match(List.of("beans"::equals, "beans"::equals), List.of("beans", "rice")), "falta um feijao");
		assertNotNull(match(List.of("beans"::equals, "beans"::equals), List.of("beans", "beans")));
	}

	@Test
	void backtracksWhenTagsOverlap() {
		// "qualquer queijo" pega o curado primeiro, mas o curado precisa ir para o ingrediente especifico
		Predicate<String> anyCheese = s -> s.endsWith("cheese");
		Predicate<String> aged = "aged_cheese"::equals;
		assertNotNull(match(List.of(anyCheese, aged), List.of("aged_cheese", "fresh_cheese")));
	}
}
