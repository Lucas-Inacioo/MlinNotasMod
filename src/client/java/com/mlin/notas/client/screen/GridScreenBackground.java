package com.mlin.notas.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Desenha fundos de tela de container no estilo vanilla usando apenas retangulos (serve para qualquer tamanho).
 */
public final class GridScreenBackground {
	public static final int PANEL = 0xFFC6C6C6;
	public static final int LIGHT = 0xFFFFFFFF;
	public static final int DARK = 0xFF555555;
	public static final int OUTLINE = 0xFF000000;
	public static final int SLOT_BG = 0xFF8B8B8B;
	public static final int SLOT_LIGHT = 0xFFFFFFFF;
	public static final int SLOT_DARK = 0xFF373737;
	public static final int SLOT = 18;

	private GridScreenBackground() {
	}

	/** Moldura completa (borda preta arredondada, bisel claro/escuro, painel cinza). */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		// contorno preto com cantos cortados
		g.fill(x + 1, y, x + w - 1, y + 1, OUTLINE);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, OUTLINE);
		g.fill(x, y + 1, x + 1, y + h - 1, OUTLINE);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, OUTLINE);
		// painel
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
		// bisel
		g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
		g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
		g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, DARK);
		g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, DARK);
	}

	/** Um slot de 18x18 na posicao (x, y) do canto superior esquerdo do quadrado (o item e desenhado em x+1, y+1). */
	public static void slot(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + SLOT, y + SLOT, SLOT_DARK);
		g.fill(x + 1, y + 1, x + SLOT, y + SLOT, SLOT_LIGHT);
		g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, SLOT_BG);
	}

	/** Grade de slots cujo primeiro item fica em (left, top) (coordenadas do slot, como em Slot.x/Slot.y). */
	public static void grid(GuiGraphicsExtractor g, int left, int top, int cols, int rows) {
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				slot(g, left - 1 + c * SLOT, top - 1 + r * SLOT);
			}
		}
	}

	/** Inventario do jogador: 3 linhas + hotbar, com o primeiro slot em (left, top). */
	public static void playerInventory(GuiGraphicsExtractor g, int left, int top) {
		grid(g, left, top, 9, 3);
		grid(g, left, top + 58, 9, 1);
	}

	/** Seta "->" simples entre a grade de crafting e o resultado. */
	public static void arrow(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y + 3, x + 10, y + 7, DARK);
		g.fill(x + 10, y + 1, x + 12, y + 9, DARK);
		g.fill(x + 12, y + 3, x + 14, y + 7, DARK);
		g.fill(x + 14, y + 4, x + 16, y + 6, DARK);
	}
}
