package com.mlin.notas.client.waila;

import com.mlin.notas.waila.ContainerTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/** Grade de conteudo no tooltip da caixa de shulker e da mochila. */
public final class ContainerTooltipRenderer implements ClientTooltipComponent {
	private final ContainerTooltip tooltip;

	public ContainerTooltipRenderer(ContainerTooltip tooltip) {
		this.tooltip = tooltip;
	}

	@Override
	public int getHeight(Font font) {
		return ItemGridDrawer.height(tooltip.groups().size(), ContainerTooltip.MAX_ROWS, 0, font) + 2;
	}

	@Override
	public int getWidth(Font font) {
		return ItemGridDrawer.width(tooltip.groups().size(), ContainerTooltip.MAX_ROWS);
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		ItemGridDrawer.draw(graphics, font, tooltip.groups(), x, y, ContainerTooltip.MAX_ROWS, 0);
	}
}
