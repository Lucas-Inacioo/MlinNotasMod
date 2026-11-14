package com.mlin.notas.client.village;

import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Linha de obra pronta na aba Obras do quadro: nome com o nivel (Casa II), situacao (parada sem trabalhador, melhoria
 * entregue/na fila/construindo), barra da melhoria e o botao "Melhorar para III" com o custo na dica.
 */
final class BoardBuildingsRow {
	private BoardBuildingsRow() {
	}

	static Component numeral(int level) {
		return Component.translatable("village.mlinnotas.building.level." + Math.max(1, Math.min(5, level)));
	}

	static Component title(VillageState s, VillageLayout.Lot lot) {
		Component name = VillageOverlay.kindName(lot.kind());
		if (BuildingTypes.of(lot.kind()) == null) {
			return Component.translatable("gui.mlinnotas.village.row.built", name);
		}
		return Component.translatable("gui.mlinnotas.village.row.built_level", name, numeral(s.buildingsState().level(lot.id())));
	}

	static Component status(VillageBoardScreen screen, VillageState s, VillageLayout.Lot lot, Component where) {
		MutableComponent out = where.copy().withStyle(ChatFormatting.GRAY);
		BuildingsState.Upgrade up = s.buildingsState().upgrade(lot.id());
		if (screen.upgradeRunning(lot.id())) {
			int pct = screen.view().total() == 0 ? 0 : screen.view().done() * 100 / screen.view().total();
			out.append(Component.literal(" · ")).append(Component.translatable("gui.mlinnotas.village.upgrade_building", pct)
					.withStyle(ChatFormatting.YELLOW));
		} else if (up != null && up.queued()) {
			out.append(Component.literal(" · ")).append(Component.translatable("gui.mlinnotas.village.upgrade_queued", numeral(up.toLevel()))
					.withStyle(ChatFormatting.YELLOW));
		} else if (VillageBuildings.idle(s).contains(lot.id())) {
			out.append(Component.literal(" · ")).append(Component.translatable("gui.mlinnotas.village.idle").withStyle(ChatFormatting.RED));
		}
		return out;
	}

	/** Barra fina do quanto da melhoria aberta ja foi entregue. */
	static void progress(GuiGraphicsExtractor graphics, VillageState s, VillageLayout.Lot lot, int x, int y, int width) {
		BuildingsState.Upgrade up = s.buildingsState().upgrade(lot.id());
		VillageProject project = lot.project();
		if (up == null || up.queued() || project == null) {
			return;
		}
		double f = BuildingsState.fraction(up, project);
		graphics.fill(x, y, x + width, y + 1, 0xFF303030);
		graphics.fill(x, y, x + (int) (width * f), y + 1, 0xFF60C0FF);
	}

	/** Botao "Melhorar para N" (null = tipo sem nivel); desligado no teto, com o motivo e o custo na dica. */
	static Button upgradeButton(VillageBoardScreen screen, VillageLayout.Lot lot, int x, int y) {
		VillageState s = screen.state();
		VillageProject project = lot.project();
		if (project == null || BuildingTypes.of(project.id()) == null || lot.legacy()) {
			return null;
		}
		int level = s.buildingsState().level(lot.id());
		int max = BuildingTypes.maxLevel(project.id());
		BuildingsState.Upgrade up = s.buildingsState().upgrade(lot.id());
		if (level >= max) {
			Button b = Button.builder(Component.translatable("gui.mlinnotas.village.upgrade_max"), x2 -> {
			}).bounds(x, y, 84, 18).build();
			b.active = false;
			return b;
		}
		int to = level + 1;
		int cap = VillageBuildings.cap(s, project.id());
		Button b = Button.builder(Component.translatable("gui.mlinnotas.village.upgrade", numeral(to)), x2 -> VillageClient.send(screen.board(),
				"upgrade", String.valueOf(lot.id()))).bounds(x, y, 84, 18).build();
		MutableComponent tip = Component.translatable("gui.mlinnotas.village.upgrade_tooltip", VillageOverlay.kindName(lot.kind()), numeral(to))
				.withStyle(ChatFormatting.GOLD);
		tip.append("\n").append(Component.translatable("village.mlinnotas.building." + project.id() + ".level." + to).withStyle(ChatFormatting.GRAY));
		for (VillageCost cost : BuildingTypes.upgradeCost(project, to)) {
			int have = up == null ? 0 : up.deposited(cost.id());
			tip.append("\n").append(Component.literal(" - ").append(costName(cost.id())).append(": " + have + "/" + cost.count())
					.withStyle(have >= cost.count() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
		}
		if (to > cap) {
			b.active = false;
			tip.append("\n").append(Component.translatable(project == VillageProject.TOWN_HALL ? "gui.mlinnotas.village.upgrade_capped_hall"
					: "gui.mlinnotas.village.upgrade_capped", numeral(to)).withStyle(ChatFormatting.RED));
		} else if (up != null && up.queued()) {
			b.active = false;
		}
		b.setTooltip(Tooltip.create(tip));
		return b;
	}

	static Component costName(String id) {
		if (id.startsWith("#")) {
			return Component.translatable("village.mlinnotas.cost." + id.substring(1).replace(':', '.').replace('/', '.'));
		}
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
		return new net.minecraft.world.item.ItemStack(item).getHoverName();
	}
}
