package com.mlin.notas.client.food;

import com.mlin.notas.client.screen.GridScreenBackground;
import com.mlin.notas.food.CookingPotMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela da panela: grade 3x2 de ingredientes, chama (acesa com calor embaixo), seta de progresso, saida e recipiente.
 * Fundo desenhado com retangulos (GridScreenBackground) e os sprites da fornalha vanilla.
 */
public class CookingPotScreen extends AbstractContainerScreen<CookingPotMenu> {
	private static final Identifier LIT = Identifier.withDefaultNamespace("container/furnace/lit_progress");
	private static final Identifier BURN = Identifier.withDefaultNamespace("container/furnace/burn_progress");
	private static final int ARROW_X = 89;
	private static final int ARROW_Y = 26;
	private static final int FLAME_X = 49;
	private static final int FLAME_Y = 56;

	public CookingPotScreen(CookingPotMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		GridScreenBackground.panel(graphics, x, y, imageWidth, imageHeight);
		GridScreenBackground.grid(graphics, x + CookingPotMenu.GRID_X, y + CookingPotMenu.GRID_Y, 3, 2);
		GridScreenBackground.slot(graphics, x + CookingPotMenu.CONTAINER_X - 1, y + CookingPotMenu.CONTAINER_Y - 1);
		// saida maior (26x26), como a da fornalha
		graphics.fill(x + CookingPotMenu.OUTPUT_X - 5, y + CookingPotMenu.OUTPUT_Y - 5, x + CookingPotMenu.OUTPUT_X + 21, y + CookingPotMenu.OUTPUT_Y + 21,
				GridScreenBackground.SLOT_DARK);
		graphics.fill(x + CookingPotMenu.OUTPUT_X - 4, y + CookingPotMenu.OUTPUT_Y - 4, x + CookingPotMenu.OUTPUT_X + 21, y + CookingPotMenu.OUTPUT_Y + 21,
				GridScreenBackground.SLOT_LIGHT);
		graphics.fill(x + CookingPotMenu.OUTPUT_X - 4, y + CookingPotMenu.OUTPUT_Y - 4, x + CookingPotMenu.OUTPUT_X + 20, y + CookingPotMenu.OUTPUT_Y + 20,
				GridScreenBackground.SLOT_BG);
		GridScreenBackground.playerInventory(graphics, x + 8, y + CookingPotMenu.INVENTORY_Y);
		// seta (fundo apagado + progresso)
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN, 24, 16, 0, 0, x + ARROW_X, y + ARROW_Y, 24, 16, 0x50FFFFFF);
		int progress = Mth.ceil(menu.progress() * 24.0F);
		if (progress > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN, 24, 16, 0, 0, x + ARROW_X, y + ARROW_Y, progress, 16);
		}
		// chama: acesa com calor, apagada sem
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT, 14, 14, 0, 0, x + FLAME_X, y + FLAME_Y, 14, 14, menu.heated() ? 0xFFFFFFFF : 0x40FFFFFF);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		int fx = leftPos + FLAME_X;
		int fy = topPos + FLAME_Y;
		if (mouseX >= fx && mouseX < fx + 14 && mouseY >= fy && mouseY < fy + 14) {
			graphics.setTooltipForNextFrame(font, Component.translatable(menu.heated() ? "gui.mlinnotas.cooking_pot.heated" : "gui.mlinnotas.cooking_pot.cold"),
					mouseX, mouseY);
		}
	}
}
