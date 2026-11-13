package com.mlin.notas.food;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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
 * Cura no Barril de Conserva ({@code mlinnotas:fermenting}): a pilha inteira de um espaco vira o resultado (1 para
 * {@code result.count}) depois de {@code days} dias de jogo parada no barril (queijo fresco -> curado, repolho ->
 * chucrute).
 */
public class FermentingRecipe extends SingleItemRecipe {
	public static final MapCodec<FermentingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
			Ingredient.CODEC.fieldOf("ingredient").forGetter(SingleItemRecipe::input),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(FermentingRecipe::output),
			Codec.FLOAT.optionalFieldOf("days", 1.0F).forGetter(FermentingRecipe::days)
	).apply(i, FermentingRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, FermentingRecipe> STREAM_CODEC = StreamCodec.composite(
			Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
			Ingredient.CONTENTS_STREAM_CODEC, SingleItemRecipe::input,
			ItemStackTemplate.STREAM_CODEC, FermentingRecipe::output,
			ByteBufCodecs.FLOAT, FermentingRecipe::days,
			FermentingRecipe::new);

	public static final RecipeSerializer<FermentingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final float days;

	public FermentingRecipe(Recipe.CommonInfo commonInfo, Ingredient ingredient, ItemStackTemplate result, float days) {
		super(commonInfo, ingredient, result);
		this.days = days;
	}

	public ItemStackTemplate output() {
		return result();
	}

	public float days() {
		return days;
	}

	/** Ticks de jogo ate curar. */
	public int ticks() {
		return FoodRules.fermentTicks(days);
	}

	@Override
	public RecipeSerializer<FermentingRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	public RecipeType<FermentingRecipe> getType() {
		return FoodRegistry.FERMENTING_TYPE;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.CRAFTING_MISC;
	}
}
