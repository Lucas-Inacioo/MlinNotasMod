package com.mlin.notas.client.screen;

import com.mlin.notas.chest.BigChestMenu;
import com.mlin.notas.chest.ChestTier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela dos baus grandes: janela de ate 6 linhas x 9 colunas e inventario do jogador abaixo.
 * Baus maiores mostram uma barra de rolagem a direita (roda do mouse, clicar ou arrastar na barra).
 */
public class BigChestScreen extends AbstractContainerScreen<BigChestMenu> {
	private static final int BAR_WIDTH = 12;
	private static final int THUMB_HEIGHT = 15;

	private final ChestTier tier;
	private boolean draggingBar;

	public BigChestScreen(BigChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, menu.tier().screenWidth(), 114 + menu.tier().visibleRows() * BigChestMenu.SLOT_SIZE);
		this.tier = menu.tier();
		this.inventoryLabelY = this.imageHeight - 94;
		this.inventoryLabelX = BigChestMenu.playerInventoryLeft(tier);
	}

	private boolean scrollable() {
		return tier.maxScroll() > 0;
	}

	private int barLeft() {
		return leftPos + BigChestMenu.GRID_LEFT + tier.cols() * BigChestMenu.SLOT_SIZE + 4;
	}

	private int barTop() {
		return topPos + BigChestMenu.GRID_TOP - 1;
	}

	private int barHeight() {
		return tier.visibleRows() * BigChestMenu.SLOT_SIZE;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		GridScreenBackground.panel(graphics, x, y, imageWidth, imageHeight);
		GridScreenBackground.grid(graphics, x + BigChestMenu.GRID_LEFT, y + BigChestMenu.GRID_TOP, tier.cols(), tier.visibleRows());
		GridScreenBackground.playerInventory(graphics, x + BigChestMenu.playerInventoryLeft(tier), y + BigChestMenu.inventoryTop(tier));
		if (scrollable()) {
			int left = barLeft();
			int top = barTop();
			int height = barHeight();
			// trilho afundado
			graphics.fill(left, top, left + BAR_WIDTH, top + height, GridScreenBackground.SLOT_DARK);
			graphics.fill(left + 1, top + 1, left + BAR_WIDTH, top + height, GridScreenBackground.SLOT_LIGHT);
			graphics.fill(left + 1, top + 1, left + BAR_WIDTH - 1, top + height - 1, GridScreenBackground.SLOT_BG);
			// alca saltada
			int thumbY = top + 1 + (height - 2 - THUMB_HEIGHT) * menu.scrollRow() / tier.maxScroll();
			graphics.fill(left + 1, thumbY, left + BAR_WIDTH - 1, thumbY + THUMB_HEIGHT, GridScreenBackground.DARK);
			graphics.fill(left + 1, thumbY, left + BAR_WIDTH - 2, thumbY + THUMB_HEIGHT - 1, GridScreenBackground.LIGHT);
			graphics.fill(left + 2, thumbY + 1, left + BAR_WIDTH - 2, thumbY + THUMB_HEIGHT - 1, GridScreenBackground.PANEL);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractLabels(graphics, mouseX, mouseY);
		if (scrollable()) {
			// "linhas 1-6 de 12" a direita do titulo
			int first = menu.scrollRow() + 1;
			int last = menu.scrollRow() + tier.visibleRows();
			Component rows = Component.translatable("gui.mlinnotas.chest.rows", first, last, tier.rows());
			graphics.text(font, rows, imageWidth - 8 - font.width(rows), titleLabelY, 0xFF404040, false);
		}
	}

	/** Pede a linha {@code row} (muda aqui e avisa o servidor, que reenvia a janela). */
	private void scrollTo(int row) {
		int clamped = tier.clampScroll(row, 0);
		if (clamped == menu.scrollRow()) {
			return;
		}
		menu.setScrollRow(clamped);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, clamped);
		}
	}

	private void scrollToMouse(double mouseY) {
		double fraction = (mouseY - barTop() - THUMB_HEIGHT / 2.0) / (barHeight() - THUMB_HEIGHT);
		scrollTo((int) Math.round(Math.clamp(fraction, 0.0, 1.0) * tier.maxScroll()));
	}

	private boolean overBar(double mouseX, double mouseY) {
		return scrollable() && mouseX >= barLeft() && mouseX < barLeft() + BAR_WIDTH && mouseY >= barTop() && mouseY < barTop() + barHeight();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// primeiro a vanilla (ex.: roda sobre uma bundle), depois a rolagem do bau
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		if (scrollable() && scrollY != 0) {
			scrollTo(menu.scrollRow() - (int) Math.signum(scrollY));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overBar(event.x(), event.y())) {
			draggingBar = true;
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingBar) {
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingBar = false;
		return super.mouseReleased(event);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		return super.hasClickedOutside(mouseX, mouseY, left, top) && !overBar(mouseX, mouseY);
	}
}
