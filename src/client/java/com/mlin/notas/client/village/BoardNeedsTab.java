package com.mlin.notas.client.village;

import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillageNeeds;
import com.mlin.notas.village.VillageState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aba Necessidades do quadro (a partir da Prefeitura I): moradores e trabalhadores, e uma barra de 0 a 100% para cada
 * necessidade (verde acima de 75% rende prosperidade, vermelha abaixo de 25% tira), com oferta x demanda na dica.
 */
final class BoardNeedsTab {
	private static final ItemStack[] ICONS = {new ItemStack(Items.BED.red()), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_SWORD),
			new ItemStack(Items.LANTERN), new ItemStack(Items.BELL), new ItemStack(Items.EMERALD)};

	private BoardNeedsTab() {
	}

	static void draw(VillageBoardScreen screen, GuiGraphicsExtractor graphics, Font font, int x, int top, int right, int bottom, int mouseX,
			int mouseY) {
		VillageState s = screen.state();
		VillageNeeds.Input in = VillageNeeds.input(s);
		Map<VillageNeeds.Need, Integer> values = VillageNeeds.values(in);
		int residents = VillageBuildings.residents(s);
		int workers = VillageBuildings.workersNeeded(s);
		int idle = VillageBuildings.idle(s).size();
		Component head = Component.translatable("gui.mlinnotas.village.needs.head", in.population(), residents, Math.min(residents, workers), workers)
				.withStyle(ChatFormatting.GRAY);
		graphics.text(font, head, x, top, 0xFFFFFFFF);
		if (idle > 0) {
			Component warn = Component.translatable("gui.mlinnotas.village.needs.idle", idle).withStyle(ChatFormatting.RED);
			graphics.text(font, warn, right - font.width(warn), top, 0xFFFFFFFF);
		}
		int delta = VillageNeeds.dailyProsperity(values);
		int rowH = Math.max(18, (bottom - top - 24) / VillageNeeds.Need.values().length);
		int y = top + 12;
		int barX = x + 112;
		int barR = right - 62;
		for (VillageNeeds.Need need : VillageNeeds.Need.values()) {
			int v = values.get(need);
			boolean hover = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + rowH - 2;
			graphics.fill(x, y, right, y + rowH - 2, hover ? 0x60FFFFFF : 0x40000000);
			graphics.item(ICONS[need.ordinal()], x + 2, y + (rowH - 18) / 2);
			graphics.text(font, Component.translatable("gui.mlinnotas.village.needs." + need.id()), x + 22, y + (rowH - 10) / 2, 0xFFFFFFFF);
			int by = y + (rowH - 10) / 2;
			graphics.fill(barX, by, barR, by + 9, 0xFF101010);
			int color = v > VillageNeeds.GOOD ? 0xFF3FAF3F : v < VillageNeeds.BAD ? 0xFFC03030 : v <= VillageNeeds.LEVEL_UP ? 0xFFC08A20 : 0xFF9FB040;
			graphics.fill(barX + 1, by + 1, barX + 1 + (barR - barX - 2) * v / 100, by + 8, color);
			// marca dos 50% (exigido para subir da Cidade em diante)
			int mark = barX + 1 + (barR - barX - 2) * VillageNeeds.LEVEL_UP / 100;
			graphics.fill(mark, by - 1, mark + 1, by + 10, 0xFFFFFFFF);
			String pct = v + "%";
			graphics.text(font, pct, right - 4 - font.width(pct), by + 1, 0xFFFFFFFF);
			Component effect = v > VillageNeeds.GOOD ? Component.literal("+" + VillageNeeds.GOOD_BONUS).withStyle(ChatFormatting.GREEN)
					: v < VillageNeeds.BAD ? Component.literal("-" + VillageNeeds.BAD_PENALTY).withStyle(ChatFormatting.RED) : null;
			if (effect != null) {
				graphics.text(font, effect, barR + 4, by + 1, 0xFFFFFFFF);
			}
			if (hover) {
				List<FormattedCharSequence> tip = new ArrayList<>();
				tip.add(Component.translatable("gui.mlinnotas.village.needs." + need.id()).withStyle(ChatFormatting.GOLD).getVisualOrderText());
				tip.add(Component.translatable("gui.mlinnotas.village.needs.supply", VillageNeeds.supply(need, in), VillageNeeds.demand(need,
						in.population())).withStyle(ChatFormatting.AQUA).getVisualOrderText());
				tip.addAll(font.split(Component.translatable("gui.mlinnotas.village.needs." + need.id() + ".desc").withStyle(ChatFormatting.GRAY), 240));
				graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
			}
			y += rowH;
		}
		Component foot = Component.translatable("gui.mlinnotas.village.needs.daily", (delta >= 0 ? "+" : "") + delta,
				s.level().ordinal() + 1 >= VillageLevel.TOWN.ordinal() ? Component.translatable("gui.mlinnotas.village.needs.town_rule",
						VillageNeeds.LEVEL_UP) : Component.empty()).withStyle(delta >= 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.RED);
		List<FormattedCharSequence> lines = font.split(foot, right - x);
		graphics.text(font, lines.getFirst(), x, y + 1, 0xFFFFFFFF);
	}
}
