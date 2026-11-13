package com.mlin.notas.food;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;

/**
 * Receita da Tabua de Corte ({@code mlinnotas:cutting}): um item vira o resultado (ex.: trigo -> farinha, milho ->
 * 2 fubas). JSON igual ao do cortador de pedra: {@code {"type":"mlinnotas:cutting","ingredient":...,"result":{...}}}.
 */
public class CuttingRecipe extends SingleItemRecipe {
	public static final MapCodec<CuttingRecipe> MAP_CODEC = simpleMapCodec(CuttingRecipe::new);
	public static final StreamCodec<RegistryFriendlyByteBuf, CuttingRecipe> STREAM_CODEC = simpleStreamCodec(CuttingRecipe::new);
	public static final RecipeSerializer<CuttingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	public CuttingRecipe(Recipe.CommonInfo commonInfo, Ingredient ingredient, ItemStackTemplate result) {
		super(commonInfo, ingredient, result);
	}

	public ItemStackTemplate output() {
		return result();
	}

	@Override
	public RecipeSerializer<CuttingRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	public RecipeType<CuttingRecipe> getType() {
		return FoodRegistry.CUTTING_TYPE;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.STONECUTTER;
	}
}
