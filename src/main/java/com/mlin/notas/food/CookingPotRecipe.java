package com.mlin.notas.food;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Receita da Panela ({@code mlinnotas:cooking}): 1 a 6 ingredientes sem forma, recipiente opcional (tigela, garrafa)
 * consumido uma vez por preparo, resultado, tempo e experiencia. JSON:
 * {@code {"type":"mlinnotas:cooking","ingredients":[...],"container":"minecraft:bowl","result":{...},"cookingtime":200}}.
 */
public class CookingPotRecipe implements Recipe<CookingPotRecipe.Input> {
	public static final int MAX_INGREDIENTS = 6;

	/** Conteudo da panela: os 6 espacos de ingrediente e o recipiente. */
	public record Input(List<ItemStack> ingredients, ItemStack container) implements RecipeInput {
		@Override
		public ItemStack getItem(int index) {
			return index < ingredients.size() ? ingredients.get(index) : container;
		}

		@Override
		public int size() {
			return ingredients.size() + 1;
		}
	}

	public static final MapCodec<CookingPotRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Ingredient.CODEC.listOf(1, MAX_INGREDIENTS).fieldOf("ingredients").forGetter(CookingPotRecipe::ingredients),
			Ingredient.CODEC.optionalFieldOf("container").forGetter(CookingPotRecipe::container),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(CookingPotRecipe::result),
			Codec.INT.optionalFieldOf("cookingtime", FoodRules.POT_COOK_TICKS).forGetter(CookingPotRecipe::cookingTime),
			Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(CookingPotRecipe::experience)
	).apply(i, CookingPotRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, CookingPotRecipe> STREAM_CODEC = StreamCodec.composite(
			Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), CookingPotRecipe::ingredients,
			Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, CookingPotRecipe::container,
			ItemStackTemplate.STREAM_CODEC, CookingPotRecipe::result,
			ByteBufCodecs.VAR_INT, CookingPotRecipe::cookingTime,
			ByteBufCodecs.FLOAT, CookingPotRecipe::experience,
			CookingPotRecipe::new);

	public static final RecipeSerializer<CookingPotRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final List<Ingredient> ingredients;
	private final Optional<Ingredient> container;
	private final ItemStackTemplate result;
	private final int cookingTime;
	private final float experience;

	public CookingPotRecipe(List<Ingredient> ingredients, Optional<Ingredient> container, ItemStackTemplate result, int cookingTime, float experience) {
		this.ingredients = List.copyOf(ingredients);
		this.container = container;
		this.result = result;
		this.cookingTime = cookingTime;
		this.experience = experience;
	}

	public List<Ingredient> ingredients() {
		return ingredients;
	}

	public Optional<Ingredient> container() {
		return container;
	}

	public ItemStackTemplate result() {
		return result;
	}

	public int cookingTime() {
		return cookingTime;
	}

	public float experience() {
		return experience;
	}

	/** Os ingredientes casam (sem forma, todos os itens presentes usados), sem olhar o recipiente. */
	public boolean matchesIngredients(List<ItemStack> stacks) {
		List<ItemStack> present = new ArrayList<>();
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				present.add(stack);
			}
		}
		return IngredientMatcher.match(ingredients.size(), present.size(), (i, j) -> ingredients.get(i).test(present.get(j))) != null;
	}

	/** O recipiente serve (ou a receita nao pede recipiente). */
	public boolean acceptsContainer(ItemStack stack) {
		return container.isEmpty() || container.get().test(stack);
	}

	@Override
	public boolean matches(Input input, Level level) {
		return matchesIngredients(input.ingredients()) && acceptsContainer(input.container());
	}

	@Override
	public ItemStack assemble(Input input) {
		return result.create();
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public RecipeSerializer<CookingPotRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	public RecipeType<CookingPotRecipe> getType() {
		return FoodRegistry.COOKING_TYPE;
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.CRAFTING_MISC;
	}
}
