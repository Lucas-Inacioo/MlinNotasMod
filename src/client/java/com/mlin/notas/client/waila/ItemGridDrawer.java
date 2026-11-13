package com.mlin.notas.client.waila;

import com.mlin.notas.waila.ItemGrid;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Desenha uma grade de itens (linhas de 9, celulas de 18 px) com a quantidade total no canto. */
final class ItemGridDrawer {
	private static final int SLOT_BORDER = 0xFF373737;
	private static final int SLOT_FILL = 0xFF8B8B8B;

	private ItemGridDrawer() {
	}

	/** Altura usada pela grade, incluindo a linha "+N" quando ha excedente. */
	static int height(int entries, int maxRows, int extra, Font font) {
		int cells = ItemGrid.visible(entries, maxRows);
		return ItemGrid.height(cells) + (ItemGrid.overflow(entries, maxRows) + extra > 0 ? font.lineHeight + 1 : 0);
	}

	static int width(int entries, int maxRows) {
		return ItemGrid.width(ItemGrid.visible(entries, maxRows));
	}

	static void draw(GuiGraphicsExtractor graphics, Font font, List<ItemGrid.Group<ItemStack>> groups, int x, int y, int maxRows, int extra) {
		int cells = ItemGrid.visible(groups.size(), maxRows);
		for (int i = 0; i < cells; i++) {
			int cx = x + ItemGrid.cellX(i);
			int cy = y + ItemGrid.cellY(i);
			graphics.fill(cx, cy, cx + ItemGrid.CELL, cy + ItemGrid.CELL, SLOT_BORDER);
			graphics.fill(cx + 1, cy + 1, cx + ItemGrid.CELL - 1, cy + ItemGrid.CELL - 1, SLOT_FILL);
			ItemGrid.Group<ItemStack> group = groups.get(i);
			graphics.item(group.sample(), cx + 1, cy + 1);
			String label = ItemGrid.countLabel(group.count());
			if (label.length() < 3) {
				graphics.itemDecorations(font, group.sample(), cx + 1, cy + 1, label);
			} else {
				// 999, 1.2k, 34M: meia escala para nao invadir a celula vizinha
				graphics.itemDecorations(font, group.sample(), cx + 1, cy + 1, "");
				graphics.pose().pushMatrix();
				graphics.pose().translate(cx + 17.5F, cy + 13.5F);
				graphics.pose().scale(0.5F, 0.5F);
				graphics.text(font, label, -font.width(label), 0, 0xFFFFFFFF, true);
				graphics.pose().popMatrix();
			}
		}
		int overflow = ItemGrid.overflow(groups.size(), maxRows) + extra;
		if (overflow > 0) {
			graphics.text(font, "+" + overflow, x, y + ItemGrid.height(cells) + 1, 0xFFAAAAAA);
		}
	}
}
