package com.mlin.notas.food;

import com.mlin.notas.season.CropSeasons;
import com.mlin.notas.season.SeasonClimate;
import com.mlin.notas.season.SeasonDate;
import com.mlin.notas.season.SeasonGrowth;
import com.mlin.notas.season.SeasonState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Ponte entre o ato de comer (mixin {@code FoodPropertiesMixin}, vale para qualquer comida, vanilla ou do mod) e
 * {@link FoodRules}: variedade, frescor e bonus da estacao. A dica do item (cliente) usa {@link #describe} com as
 * mesmas contas.
 */
public final class FoodEating {
	/** Como a proxima mordida desta comida vai render para este jogador. */
	public record Outlook(String itemId, int timesEaten, float variety, FoodRules.Freshness freshness, boolean seasonalBonus) {
		public float multiplier() {
			return variety * (freshness == null ? 1.0F : freshness.multiplier());
		}
	}

	private FoodEating() {
	}

	public static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static List<String> history(Player player) {
		List<String> history = player.getAttached(FoodRegistry.FOOD_HISTORY);
		return history == null ? List.of() : history;
	}

	/** Frescor do item agora; null = nao estraga (ou estacoes desligadas). */
	public static FoodRules.Freshness freshness(ItemStack stack) {
		Long harvested = stack.get(FoodRegistry.HARVEST_SEASON);
		if (harvested == null || SeasonState.current() == null) {
			return null;
		}
		return FoodRules.freshness(harvested, SeasonState.seasonIndex());
	}

	/** Multiplicador de crescimento da cultura que produz o item, onde o jogador esta; -1 = nao sazonal aqui. */
	public static float cropMultiplier(Player player, String itemId) {
		SeasonDate date = SeasonState.current();
		String crop = FoodCatalog.cropBlockOfProduce(itemId);
		if (date == null || crop == null) {
			return -1;
		}
		CropSeasons.Profile profile = CropSeasons.profile(crop);
		SeasonClimate climate = SeasonGrowth.climate(player.level(), player.blockPosition());
		if (profile == null || climate == null) {
			return -1;
		}
		return CropSeasons.seasonal(profile, date, climate);
	}

	public static Outlook describe(Player player, ItemStack stack) {
		String id = itemId(stack);
		int times = FoodRules.timesEaten(history(player), id);
		FoodRules.Freshness freshness = freshness(stack);
		boolean bonus = false;
		float crop = cropMultiplier(player, id);
		if (crop >= 0) {
			Long harvested = stack.get(FoodRegistry.HARVEST_SEASON);
			long now = SeasonState.seasonIndex();
			bonus = FoodRules.seasonalBonus(crop, harvested == null ? now : harvested, now);
		}
		return new Outlook(id, times, FoodRules.varietyMultiplier(times), freshness, bonus);
	}

	/** Chamado ao comer (lado servidor): ajusta a porcao e registra no historico. */
	public static FoodProperties eat(Player player, ItemStack stack, FoodProperties base) {
		if (player.level().isClientSide()) {
			return base;
		}
		Outlook outlook = describe(player, stack);
		player.setAttached(FoodRegistry.FOOD_HISTORY, FoodRules.remember(history(player), outlook.itemId()));
		FoodRules.Portion portion = FoodRules.adjust(base.nutrition(), base.saturation(), outlook.multiplier(), outlook.seasonalBonus());
		return new FoodProperties(portion.nutrition(), portion.saturation(), base.canAlwaysEat());
	}
}
