package com.mlin.notas.recipes;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converte as receitas do servidor em {@link RecipeView}s (sem classes do Minecraft) para enviar ao cliente.
 * Receitas especiais (tingir armadura, fogos, mapas...) nao tem forma fixa e ficam de fora.
 */
public final class RecipeExporter {
	private RecipeExporter() {
	}

	public static List<RecipeView> export(RecipeManager manager) {
		List<RecipeView> views = new ArrayList<>();
		for (RecipeHolder<?> holder : manager.getRecipes()) {
			RecipeView view = export(holder);
			if (view != null) {
				views.add(view);
			}
		}
		views.sort((a, b) -> a.id().compareTo(b.id()));
		return views;
	}

	public static RecipeView export(RecipeHolder<?> holder) {
		Recipe<?> recipe = holder.value();
		if (recipe.isSpecial()) {
			return null;
		}
		String id = holder.id().identifier().toString();
		if (recipe instanceof ShapedRecipe shaped) {
			ItemStack result = shaped.assemble(CraftingInput.EMPTY);
			if (result.isEmpty()) {
				return null;
			}
			List<List<Integer>> inputs = new ArrayList<>();
			for (Optional<Ingredient> ingredient : shaped.getIngredients()) {
				inputs.add(ingredient.map(RecipeExporter::alternatives).orElse(List.of()));
			}
			return new RecipeView(id, RecipeKind.SHAPED, shaped.getWidth(), shaped.getHeight(), inputs,
					itemId(result), result.getCount(), 0, 0);
		}
		if (recipe instanceof ShapelessRecipe shapeless) {
			ItemStack result = shapeless.assemble(CraftingInput.EMPTY);
			if (result.isEmpty()) {
				return null;
			}
			List<List<Integer>> inputs = new ArrayList<>();
			for (Ingredient ingredient : shapeless.placementInfo().ingredients()) {
				inputs.add(alternatives(ingredient));
			}
			return new RecipeView(id, RecipeKind.SHAPELESS, 0, 0, inputs, itemId(result), result.getCount(), 0, 0);
		}
		if (recipe instanceof net.minecraft.world.item.crafting.TransmuteRecipe transmute) {
			// item + material -> outro item com os mesmos componentes (caixa de shulker tingida, melhoria da unidade de armazenamento)
			List<Ingredient> ingredients = transmute.placementInfo().ingredients();
			List<ItemStack> sample = new ArrayList<>();
			for (Ingredient ingredient : ingredients) {
				sample.add(ingredient.items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY));
			}
			ItemStack result = sample.isEmpty() ? ItemStack.EMPTY : transmute.assemble(CraftingInput.of(sample.size(), 1, sample));
			if (result.isEmpty()) {
				return null;
			}
			List<List<Integer>> inputs = new ArrayList<>();
			for (Ingredient ingredient : ingredients) {
				inputs.add(alternatives(ingredient));
			}
			return new RecipeView(id, RecipeKind.SHAPELESS, 0, 0, inputs, itemId(result), result.getCount(), 0, 0);
		}
		if (recipe instanceof AbstractCookingRecipe cooking) {
			RecipeKind kind;
			if (cooking instanceof com.mlin.notas.salvage.SalvageRecipe salvage) {
				// reciclagem: o resultado depende da peca; o visualizador mostra o exemplo do JSON
				kind = salvage.isBlasting() ? RecipeKind.BLASTING : RecipeKind.SMELTING;
			} else if (cooking instanceof SmeltingRecipe) {
				kind = RecipeKind.SMELTING;
			} else if (cooking instanceof BlastingRecipe) {
				kind = RecipeKind.BLASTING;
			} else if (cooking instanceof SmokingRecipe) {
				kind = RecipeKind.SMOKING;
			} else if (cooking instanceof CampfireCookingRecipe) {
				kind = RecipeKind.CAMPFIRE;
			} else {
				return null;
			}
			ItemStack result = cooking.assemble(new SingleRecipeInput(ItemStack.EMPTY));
			if (result.isEmpty()) {
				return null;
			}
			return new RecipeView(id, kind, 0, 0, List.of(alternatives(cooking.input())),
					itemId(result), result.getCount(), cooking.cookingTime(), cooking.experience());
		}
		if (recipe instanceof StonecutterRecipe stonecutter) {
			ItemStack result = stonecutter.assemble(new SingleRecipeInput(ItemStack.EMPTY));
			if (result.isEmpty()) {
				return null;
			}
			return new RecipeView(id, RecipeKind.STONECUTTING, 0, 0, List.of(alternatives(stonecutter.input())),
					itemId(result), result.getCount(), 0, 0);
		}
		if (recipe instanceof SmithingTransformRecipe smithing) {
			ItemStack result = smithing.assemble(new SmithingRecipeInput(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
			if (result.isEmpty()) {
				return null;
			}
			List<List<Integer>> inputs = new ArrayList<>();
			inputs.add(smithing.templateIngredient().map(RecipeExporter::alternatives).orElse(List.of()));
			inputs.add(alternatives(smithing.baseIngredient()));
			inputs.add(smithing.additionIngredient().map(RecipeExporter::alternatives).orElse(List.of()));
			return new RecipeView(id, RecipeKind.SMITHING, 0, 0, inputs, itemId(result), result.getCount(), 0, 0);
		}
		return com.mlin.notas.food.FoodRecipeExport.export(id, recipe);
	}

	private static List<Integer> alternatives(Ingredient ingredient) {
		List<Integer> ids = new ArrayList<>();
		ingredient.items().forEach(holder -> ids.add(itemId(holder)));
		return ids;
	}

	private static int itemId(Holder<net.minecraft.world.item.Item> holder) {
		return BuiltInRegistries.ITEM.getId(holder.value());
	}

	public static int itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getId(stack.getItem());
	}
}
