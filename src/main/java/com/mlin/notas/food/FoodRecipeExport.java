package com.mlin.notas.food;

import com.mlin.notas.recipes.RecipeExporter;
import com.mlin.notas.recipes.RecipeKind;
import com.mlin.notas.recipes.RecipeView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

/** Achata as receitas da cozinha (panela, tabua, barril) para o visualizador de receitas. */
public final class FoodRecipeExport {
	private FoodRecipeExport() {
	}

	/** View da receita, ou null se nao for da cozinha. */
	public static RecipeView export(String id, Recipe<?> recipe) {
		if (recipe instanceof CookingPotRecipe pot) {
			ItemStack result = pot.result().create();
			List<List<Integer>> inputs = new ArrayList<>();
			for (Ingredient ingredient : pot.ingredients()) {
				inputs.add(alternatives(ingredient));
			}
			pot.container().ifPresent(container -> inputs.add(alternatives(container)));
			return new RecipeView(id, RecipeKind.COOKING_POT, pot.container().isPresent() ? 1 : 0, 0, inputs,
					RecipeExporter.itemId(result), result.getCount(), pot.cookingTime(), pot.experience());
		}
		if (recipe instanceof CuttingRecipe cutting) {
			ItemStack result = cutting.output().create();
			return new RecipeView(id, RecipeKind.CUTTING, 0, 0, List.of(alternatives(cutting.input())), RecipeExporter.itemId(result),
					result.getCount(), 0, 0);
		}
		if (recipe instanceof FermentingRecipe fermenting) {
			ItemStack result = fermenting.output().create();
			return new RecipeView(id, RecipeKind.FERMENTING, 0, 0, List.of(alternatives(fermenting.input())), RecipeExporter.itemId(result),
					result.getCount(), fermenting.ticks(), 0);
		}
		return null;
	}

	private static List<Integer> alternatives(Ingredient ingredient) {
		List<Integer> ids = new ArrayList<>();
		ingredient.items().forEach(holder -> ids.add(BuiltInRegistries.ITEM.getId(holder.value())));
		return ids;
	}
}
