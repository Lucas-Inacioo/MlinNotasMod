package com.mlin.notas.salvage;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Optional;

/**
 * Reciclagem na fornalha ({@code mlinnotas:salvage_smelting}) e no alto-forno ({@code mlinnotas:salvage_blasting}):
 * mesmo JSON de uma receita de fundicao, mas o resultado depende da peca que entra (material e durabilidade, regras em
 * {@link SalvageRules}). O {@code result} do JSON e so o exemplo mostrado no visualizador e no livro de receitas.
 * Os tipos sao os vanilla ({@code smelting}/{@code blasting}), entao a fornalha acha a receita sem mixin; o rendimento
 * do upgrade de fornalha ({@code FurnaceUpgrades.applyYield}) passa por cima sem mudar nada (equipamento nao e minerio).
 */
public class SalvageRecipe extends AbstractCookingRecipe {
	public static final MapCodec<SalvageRecipe> SMELTING_CODEC = cookingMapCodec(SalvageRecipe::smelting);
	public static final MapCodec<SalvageRecipe> BLASTING_CODEC = cookingMapCodec(SalvageRecipe::blasting);
	public static final StreamCodec<RegistryFriendlyByteBuf, SalvageRecipe> SMELTING_STREAM = cookingStreamCodec(SalvageRecipe::smelting);
	public static final StreamCodec<RegistryFriendlyByteBuf, SalvageRecipe> BLASTING_STREAM = cookingStreamCodec(SalvageRecipe::blasting);
	public static final RecipeSerializer<SalvageRecipe> SMELTING_SERIALIZER = new RecipeSerializer<>(SMELTING_CODEC, SMELTING_STREAM);
	public static final RecipeSerializer<SalvageRecipe> BLASTING_SERIALIZER = new RecipeSerializer<>(BLASTING_CODEC, BLASTING_STREAM);

	private final boolean blasting;

	public SalvageRecipe(boolean blasting, Recipe.CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient ingredient,
			ItemStackTemplate result, float experience, int cookingTime) {
		super(commonInfo, bookInfo, ingredient, result, experience, cookingTime);
		this.blasting = blasting;
	}

	private static SalvageRecipe smelting(Recipe.CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient ingredient,
			ItemStackTemplate result, float experience, int cookingTime) {
		return new SalvageRecipe(false, commonInfo, bookInfo, ingredient, result, experience, cookingTime);
	}

	private static SalvageRecipe blasting(Recipe.CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient ingredient,
			ItemStackTemplate result, float experience, int cookingTime) {
		return new SalvageRecipe(true, commonInfo, bookInfo, ingredient, result, experience, cookingTime);
	}

	public static void register() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MlinNotas.id("salvage_smelting"), SMELTING_SERIALIZER);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MlinNotas.id("salvage_blasting"), BLASTING_SERIALIZER);
	}

	public boolean isBlasting() {
		return blasting;
	}

	/** O que a peca devolve; vazio se nao for reciclavel (ou se gasta demais para devolver algo). */
	public static ItemStack salvage(ItemStack stack) {
		Optional<SalvageRules.Piece> piece = SalvageRules.piece(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		if (piece.isEmpty()) {
			return ItemStack.EMPTY;
		}
		SalvageRules.Yield yield = SalvageRules.yield(piece.get(), stack.getDamageValue(), stack.getMaxDamage());
		if (yield.count() <= 0) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(yield.item()));
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, yield.count());
	}

	/** XP extra de uma peca encantada (0 sem encantamentos). */
	public static int enchantXp(ItemStack stack) {
		if (stack.isEmpty() || SalvageRules.piece(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).isEmpty()) {
			return 0;
		}
		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		int levels = 0;
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			levels += entry.getIntValue();
		}
		return SalvageRules.enchantXp(levels);
	}

	/** So casa se a peca devolver alguma coisa (peca de diamante muito gasta nem entra na fila). */
	@Override
	public boolean matches(SingleRecipeInput input, net.minecraft.world.level.Level level) {
		return super.matches(input, level) && !salvage(input.item()).isEmpty();
	}

	@Override
	public ItemStack assemble(SingleRecipeInput input) {
		// sem entrada (visualizador de receitas, livro): o exemplo do JSON
		return input.item().isEmpty() ? super.assemble(input) : salvage(input.item());
	}

	@Override
	protected Item furnaceIcon() {
		return blasting ? Items.BLAST_FURNACE : Items.FURNACE;
	}

	@Override
	public RecipeSerializer<SalvageRecipe> getSerializer() {
		return blasting ? BLASTING_SERIALIZER : SMELTING_SERIALIZER;
	}

	@Override
	public RecipeType<? extends AbstractCookingRecipe> getType() {
		return blasting ? RecipeType.BLASTING : RecipeType.SMELTING;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return blasting ? RecipeBookCategories.BLAST_FURNACE_MISC : RecipeBookCategories.FURNACE_MISC;
	}
}
