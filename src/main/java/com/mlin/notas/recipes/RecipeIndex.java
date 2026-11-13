package com.mlin.notas.recipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indice de receitas por resultado e por ingrediente. Logica pura, construida a partir de {@link RecipeView}s.
 */
public final class RecipeIndex {
	public static final RecipeIndex EMPTY = new RecipeIndex(List.of());

	private final List<RecipeView> all;
	private final Map<Integer, List<RecipeView>> byOutput = new HashMap<>();
	private final Map<Integer, List<RecipeView>> byInput = new HashMap<>();

	public RecipeIndex(List<RecipeView> recipes) {
		this.all = List.copyOf(recipes);
		for (RecipeView recipe : all) {
			byOutput.computeIfAbsent(recipe.output(), k -> new ArrayList<>()).add(recipe);
			for (List<Integer> slot : recipe.inputs()) {
				for (Integer item : slot) {
					List<RecipeView> list = byInput.computeIfAbsent(item, k -> new ArrayList<>());
					if (list.isEmpty() || list.getLast() != recipe) {
						list.add(recipe);
					}
				}
			}
		}
	}

	public int size() {
		return all.size();
	}

	public List<RecipeView> all() {
		return all;
	}

	/** Receitas que produzem o item. */
	public List<RecipeView> recipesFor(int item) {
		return Collections.unmodifiableList(byOutput.getOrDefault(item, List.of()));
	}

	/** Receitas que usam o item como ingrediente. */
	public List<RecipeView> usagesOf(int item) {
		return Collections.unmodifiableList(byInput.getOrDefault(item, List.of()));
	}

	/** Itens que tem ao menos uma receita (para marcar na lista). */
	public boolean hasRecipes(int item) {
		return byOutput.containsKey(item);
	}
}
