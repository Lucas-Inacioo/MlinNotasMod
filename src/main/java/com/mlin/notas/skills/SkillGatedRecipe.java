package com.mlin.notas.skills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Receita sem forma que so casa se o mundo tiver o nivel {@code level} no ramo {@code skill} (ex.: a liga de celestio
 * exige Mineracao: Celestio). Vale para qualquer grade (mesa, inventario, tablet, mochila, crafter), porque o
 * progresso e do mundo e nao do jogador. Tipo JSON {@code mlinnotas:skill_gated_shapeless}.
 */
public class SkillGatedRecipe extends ShapelessRecipe {
	public static final MapCodec<SkillGatedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
			CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
			ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result),
			Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.ingredients),
			Codec.STRING.fieldOf("skill").forGetter(o -> o.skill),
			Codec.INT.fieldOf("level").forGetter(o -> o.level)
	).apply(i, SkillGatedRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SkillGatedRecipe> STREAM_CODEC = StreamCodec.composite(
			Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
			CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
			ItemStackTemplate.STREAM_CODEC, o -> o.result,
			Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.ingredients,
			ByteBufCodecs.STRING_UTF8, o -> o.skill,
			ByteBufCodecs.VAR_INT, o -> o.level,
			SkillGatedRecipe::new);

	public static final RecipeSerializer<SkillGatedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final ItemStackTemplate result;
	private final List<Ingredient> ingredients;
	private final String skill;
	private final int level;

	public SkillGatedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result,
			List<Ingredient> ingredients, String skill, int level) {
		super(commonInfo, bookInfo, result, ingredients);
		this.result = result;
		this.ingredients = ingredients;
		this.skill = skill;
		this.level = level;
	}

	public SkillBranch branch() {
		return SkillBranch.byId(skill);
	}

	public int requiredLevel() {
		return level;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (!super.matches(input, level)) {
			return false;
		}
		// o cliente nao calcula resultados de crafting; no servidor vale o progresso do mundo
		if (level instanceof ServerLevel serverLevel) {
			SkillBranch branch = branch();
			return branch != null && SkillData.get(serverLevel.getServer()).progress().has(branch, this.level);
		}
		return true;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public RecipeSerializer<ShapelessRecipe> getSerializer() {
		return (RecipeSerializer) SERIALIZER;
	}
}
