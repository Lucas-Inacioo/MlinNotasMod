import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Icone 16x16 do Tablet de Rede (procedural, sem arte manual): placa de aco escuro no mesmo tom do Terminal, tela
 * verde-agua com a grade de itens do terminal, botao e uma perola do End (a antena) no topo, cantos de cobalto.
 * Uso: java scripts/TabletTexGen.java src/main/resources/assets/mlinnotas
 */
public class TabletTexGen {
	static final int CLEAR = 0x00000000;
	static final int OUTLINE = 0xFF1A1D21;
	static final int STEEL_LIGHT = 0xFF6B727B;
	static final int STEEL = 0xFF4C525A;
	static final int STEEL_DARK = 0xFF33383E;
	static final int SCREEN = 0xFF0E2226;
	static final int GLOW = 0xFF4FE3D6;
	static final int GLOW_DIM = 0xFF2A8C86;
	static final int COBALT = 0xFF2F5FD0;
	static final int COBALT_LIGHT = 0xFF6E9CFF;
	static final int PEARL = 0xFF258474;
	static final int PEARL_LIGHT = 0xFF8BE3C9;
	static final int PEARL_DARK = 0xFF0C3B33;

	public static void main(String[] args) throws IOException {
		File items = new File(args[0], "textures/item");
		items.mkdirs();
		ImageIO.write(tablet(), "png", new File(items, "network_tablet.png"));
	}

	static BufferedImage tablet() {
		BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		// corpo: x 2..13, y 3..15 (contorno escuro, bisel claro em cima/esquerda)
		rect(im, 2, 3, 12, 13, OUTLINE);
		rect(im, 3, 4, 10, 11, STEEL);
		rect(im, 3, 4, 10, 1, STEEL_LIGHT);
		rect(im, 3, 4, 1, 11, STEEL_LIGHT);
		rect(im, 12, 5, 1, 10, STEEL_DARK);
		rect(im, 4, 14, 9, 1, STEEL_DARK);
		// cantos de cobalto
		set(im, 3, 4, COBALT_LIGHT);
		set(im, 12, 4, COBALT);
		set(im, 3, 14, COBALT);
		set(im, 12, 14, COBALT);
		// tela: x 4..11, y 5..11, com a grade 3x2 de "itens" do terminal
		rect(im, 4, 5, 8, 7, SCREEN);
		int[][] cells = {{5, 6, 0xFFD7754A}, {7, 6, 0xFF9A9A9A}, {9, 6, 0xFF4CC9C3}, {5, 9, 0xFFC9A520}, {7, 9, 0xFF2F5FD0}, {9, 9, GLOW}};
		for (int[] c : cells) {
			rect(im, c[0], c[1], 2, 2, GLOW_DIM);
			set(im, c[0], c[1], c[2]);
		}
		rect(im, 4, 11, 8, 1, GLOW_DIM);
		set(im, 11, 5, GLOW);
		// botao embaixo da tela
		rect(im, 7, 12, 2, 2, STEEL_DARK);
		set(im, 7, 12, GLOW);
		// antena: perola do End presa no topo
		set(im, 7, 2, OUTLINE);
		set(im, 8, 2, OUTLINE);
		rect(im, 6, 0, 4, 3, PEARL_DARK);
		rect(im, 7, 0, 2, 2, PEARL);
		set(im, 7, 0, PEARL_LIGHT);
		set(im, 6, 0, CLEAR);
		set(im, 9, 0, CLEAR);
		return im;
	}

	static void rect(BufferedImage im, int x, int y, int w, int h, int color) {
		for (int i = x; i < x + w; i++) {
			for (int j = y; j < y + h; j++) {
				set(im, i, j, color);
			}
		}
	}

	static void set(BufferedImage im, int x, int y, int color) {
		if (x >= 0 && y >= 0 && x < 16 && y < 16) {
			im.setRGB(x, y, color);
		}
	}
}
