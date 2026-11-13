package com.mlin.notas.client.screen;

import com.mlin.notas.backpack.BackpackMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Tela da mochila: conteudo 9 x rows, grade de crafting opcional a direita, inventario abaixo. */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
	public BackpackScreen(BackpackMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, menu.imageWidth(), 114 + menu.upgrades().rows() * BackpackMenu.SLOT_SIZE);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		int rows = menu.upgrades().rows();
		GridScreenBackground.panel(graphics, x, y, imageWidth, imageHeight);
		GridScreenBackground.grid(graphics, x + BackpackMenu.GRID_LEFT, y + BackpackMenu.GRID_TOP, 9, rows);
		int inventoryTop = BackpackMenu.GRID_TOP + rows * BackpackMenu.SLOT_SIZE + 13;
		GridScreenBackground.playerInventory(graphics, x + BackpackMenu.GRID_LEFT, y + inventoryTop);
		if (menu.hasCrafting()) {
			int top = BackpackMenu.GRID_TOP + Math.max(0, (rows * BackpackMenu.SLOT_SIZE - 3 * BackpackMenu.SLOT_SIZE) / 2);
			GridScreenBackground.grid(graphics, x + BackpackMenu.CRAFT_GRID_LEFT, y + top, 3, 3);
			GridScreenBackground.arrow(graphics, x + BackpackMenu.CRAFT_GRID_LEFT + 3 * BackpackMenu.SLOT_SIZE + 3, y + top + BackpackMenu.SLOT_SIZE + 4);
			GridScreenBackground.slot(graphics, x + BackpackMenu.RESULT_LEFT - 1, y + top + BackpackMenu.SLOT_SIZE - 1);
		}
	}
}
