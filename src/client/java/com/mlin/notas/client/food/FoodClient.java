package com.mlin.notas.client.food;

import com.mlin.notas.food.FoodCatalog;
import com.mlin.notas.food.FoodEating;
import com.mlin.notas.food.FoodItem;
import com.mlin.notas.food.FoodRegistry;
import com.mlin.notas.food.FoodRules;
import com.mlin.notas.season.CropSeasons;
import com.mlin.notas.season.SeasonState;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Cliente da cozinha: tela da panela e as linhas extras da dica de qualquer comida/semente (melhor estacao, frescor,
 * bonus da estacao, variedade, Bem Alimentado, conserva).
 */
public final class FoodClient {
	private FoodClient() {
	}

	public static void init() {
		MenuScreens.register(FoodRegistry.COOKING_POT_MENU, CookingPotScreen::new);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> appendTooltip(stack, lines));
	}

	static void appendTooltip(ItemStack stack, List<Component> lines) {
		String id = FoodEating.itemId(stack);
		seasonLine(id, lines);
		FoodCatalog.Food food = stack.getItem() instanceof FoodItem item ? item.food()
				: id.startsWith("mlinnotas:") ? FoodCatalog.food(id.substring("mlinnotas:".length())) : null;
		if (food != null) {
			kindLine(food, lines);
		}
		Player player = Minecraft.getInstance().player;
		if (player == null || !stack.has(DataComponents.FOOD)) {
			return;
		}
		FoodEating.Outlook outlook = FoodEating.describe(player, stack);
		if (outlook.freshness() != null) {
			ChatFormatting color = switch (outlook.freshness()) {
				case FRESH -> ChatFormatting.GREEN;
				case AGING -> ChatFormatting.GOLD;
				case STALE -> ChatFormatting.RED;
			};
			lines.add(Component.translatable("tooltip.mlinnotas.food.freshness." + outlook.freshness().id()).withStyle(color));
		} else if (food != null && food.perishable() && SeasonState.current() != null) {
			lines.add(Component.translatable("tooltip.mlinnotas.food.perishable").withStyle(ChatFormatting.GRAY));
		}
		if (outlook.seasonalBonus()) {
			lines.add(Component.translatable("tooltip.mlinnotas.food.in_season", FoodRules.IN_SEASON_NUTRITION).withStyle(ChatFormatting.GREEN));
		}
		if (outlook.variety() < 1.0F) {
			lines.add(Component.translatable("tooltip.mlinnotas.food.repetitive", outlook.timesEaten(), FoodRules.VARIETY_WINDOW,
					Math.round((1.0F - outlook.variety()) * 100)).withStyle(outlook.variety() <= FoodRules.VARIETY_HARSH ? ChatFormatting.RED : ChatFormatting.GOLD));
		}
	}

	/** "Melhor estacao: verao" (e "aguenta o inverno") para sementes e produtos de culturas sazonais. */
	private static void seasonLine(String id, List<Component> lines) {
		String crop = FoodCatalog.cropBlockOfItem(id);
		CropSeasons.Profile profile = crop == null ? null : CropSeasons.profile(crop);
		if (profile == null) {
			return;
		}
		Component season = Component.translatable("season.mlinnotas." + FoodCatalog.bestSeason(profile).id());
		lines.add(Component.translatable("tooltip.mlinnotas.food.best_season", season).withStyle(ChatFormatting.DARK_GREEN));
		if (FoodCatalog.winterHardy(profile)) {
			lines.add(Component.translatable("tooltip.mlinnotas.food.winter_hardy").withStyle(ChatFormatting.AQUA));
		}
	}

	private static void kindLine(FoodCatalog.Food food, List<Component> lines) {
		int ticks = FoodRules.wellFedTicks(food.kind());
		if (ticks > 0) {
			String level = FoodRules.wellFedAmplifier(food.kind()) >= 1 ? "II" : "I";
			String time = String.format(Locale.ROOT, "%d:%02d", ticks / 1200, ticks / 20 % 60);
			lines.add(Component.translatable("tooltip.mlinnotas.food.well_fed", level, time).withStyle(ChatFormatting.GOLD));
		} else if (food.kind() == FoodCatalog.Kind.PRESERVE) {
			lines.add(Component.translatable("tooltip.mlinnotas.food.preserve").withStyle(ChatFormatting.BLUE));
		}
	}
}
