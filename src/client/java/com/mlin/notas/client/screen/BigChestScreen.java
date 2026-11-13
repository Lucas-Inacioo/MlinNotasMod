package com.mlin.notas.client.screen;

import com.mlin.notas.chest.BigChestMenu;
import com.mlin.notas.chest.ChestTier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Tela dos baus grandes: grade rows x cols e inventario do jogador abaixo. */
public class BigChestScreen extends AbstractContainerScreen<BigChestMenu> {
	private final ChestTier tier;

	public BigChestScreen(BigChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, menu.tier().screenWidth(), 114 + menu.tier().rows() * BigChestMenu.SLOT_SIZE);
		this.tier = menu.tier();
		this.inventoryLabelY = this.imageHeight - 94;
		this.inventoryLabelX = BigChestMenu.playerInventoryLeft(tier);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		GridScreenBackground.panel(graphics, x, y, imageWidth, imageHeight);
		GridScreenBackground.grid(graphics, x + BigChestMenu.GRID_LEFT, y + BigChestMenu.GRID_TOP, tier.cols(), tier.rows());
		int inventoryTop = BigChestMenu.GRID_TOP + tier.rows() * BigChestMenu.SLOT_SIZE + 13;
		GridScreenBackground.playerInventory(graphics, x + BigChestMenu.playerInventoryLeft(tier), y + inventoryTop);
	}
}
