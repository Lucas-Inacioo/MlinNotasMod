import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Texturas 16x16 da rede de armazenamento (procedurais, sem arte manual): Unidades de Armazenamento por nivel
 * (frente com 4 gavetas, lados e topo com moldura na cor do metal), Cabo, Terminal e Terminal de Crafting.
 * Uso: java scripts/StorageTexGen.java src/main/resources/assets/mlinnotas
 */
public class StorageTexGen {
	// mesmas cores (claro, base, escuro) da escada no TexGen
	static final String[] IDS = {"copper", "iron", "gold", "cobalt", "diamond", "netherite", "infernite", "endite", "astralite", "celestium"};
	static final int[][] PALETTE = {
			{0xFFF0A07A, 0xFFD7754A, 0xFF8E4A2C}, {0xFFE8E8E8, 0xFF9A9A9A, 0xFF6E6E6E}, {0xFFF6D23A, 0xFFC9A520, 0xFF8A6D0E},
			{0xFF6E9CFF, 0xFF2F5FD0, 0xFF1A3478}, {0xFF8FF3EF, 0xFF4CC9C3, 0xFF2B8A86}, {0xFF6E6268, 0xFF4A3F44, 0xFF2A2326},
			{0xFFFF8A3C, 0xFFE0401A, 0xFF8A1C08}, {0xFFD8B8FF, 0xFFA77BE8, 0xFF5E3A9A}, {0xFFFF9CE6, 0xFFE45CC8, 0xFF7A1E6A},
			{0xFFFFFFFF, 0xFFFFF0A8, 0xFFC8A84A}};

	static final int STEEL_LIGHT = 0xFF6B727B;
	static final int STEEL = 0xFF4C525A;
	static final int STEEL_DARK = 0xFF33383E;
	static final int STEEL_DEEP = 0xFF24282C;
	static final int GLOW = 0xFF4FE3D6;
	static final int GLOW_DIM = 0xFF2A8C86;
	static final int SCREEN = 0xFF0E2226;

	public static void main(String[] args) throws IOException {
		File blocks = new File(args[0], "textures/block");
		blocks.mkdirs();
		for (int i = 0; i < IDS.length; i++) {
			int[] p = PALETTE[i];
			String id = "storage_unit_" + IDS[i];
			save(unitSide(p, i), blocks, id + "_side");
			save(unitTop(p, i), blocks, id + "_top");
			save(unitFront(p, i), blocks, id + "_front");
		}
		save(cable(), blocks, "storage_cable");
		save(terminalSide(), blocks, "storage_terminal_side");
		save(terminalTop(), blocks, "storage_terminal_top");
		save(terminalFront(), blocks, "storage_terminal_front");
		save(craftingFront(), blocks, "crafting_terminal_front");
		save(craftingTop(), blocks, "crafting_terminal_top");
	}

	// ---------------------------------------------------------------- unidades

	/** Moldura de 2 px no metal do nivel (bisel claro em cima/esquerda, escuro embaixo/direita). */
	static void metalFrame(BufferedImage im, int[] p) {
		rect(im, 0, 0, 16, 16, p[1]);
		rect(im, 0, 0, 16, 1, p[0]);
		rect(im, 0, 0, 1, 16, p[0]);
		rect(im, 0, 15, 16, 1, p[2]);
		rect(im, 15, 0, 1, 16, p[2]);
		// cantos com rebite
		for (int[] c : new int[][] {{1, 1}, {14, 1}, {1, 14}, {14, 14}}) {
			set(im, c[0], c[1], p[0]);
		}
	}

	static BufferedImage unitSide(int[] p, int seed) {
		BufferedImage im = img();
		metalFrame(im, p);
		rect(im, 2, 2, 12, 12, STEEL_DARK);
		rect(im, 2, 2, 12, 1, STEEL_DEEP);
		rect(im, 2, 2, 1, 12, STEEL_DEEP);
		// faixa do meio no metal com rebites
		rect(im, 2, 7, 12, 2, p[1]);
		rect(im, 2, 7, 12, 1, p[0]);
		set(im, 4, 8, p[2]);
		set(im, 11, 8, p[2]);
		// frestas de ventilacao
		for (int y : new int[] {4, 11}) {
			rect(im, 4, y, 8, 1, STEEL_DEEP);
			rect(im, 4, y + 1, 8, 1, STEEL);
		}
		noise(im, 100 + seed, 0.10);
		return im;
	}

	static BufferedImage unitTop(int[] p, int seed) {
		BufferedImage im = img();
		metalFrame(im, p);
		rect(im, 2, 2, 12, 12, STEEL);
		// 4 tampas de inspecao
		for (int[] c : new int[][] {{3, 3}, {9, 3}, {3, 9}, {9, 9}}) {
			rect(im, c[0], c[1], 4, 4, STEEL_DARK);
			rect(im, c[0], c[1], 4, 1, STEEL_LIGHT);
			rect(im, c[0], c[1], 1, 4, STEEL_LIGHT);
			set(im, c[0] + 2, c[1] + 2, p[1]);
		}
		rect(im, 7, 2, 2, 12, p[2]);
		rect(im, 2, 7, 12, 2, p[2]);
		noise(im, 200 + seed, 0.10);
		return im;
	}

	static BufferedImage unitFront(int[] p, int seed) {
		BufferedImage im = img();
		metalFrame(im, p);
		rect(im, 2, 2, 12, 12, STEEL_DEEP);
		// 4 gavetas (2x2), cada uma com etiqueta e puxador no metal
		for (int[] c : new int[][] {{2, 2}, {8, 2}, {2, 8}, {8, 8}}) {
			int x = c[0];
			int y = c[1];
			rect(im, x, y, 6, 6, STEEL);
			rect(im, x, y, 6, 1, STEEL_LIGHT);
			rect(im, x, y, 1, 6, STEEL_LIGHT);
			rect(im, x + 5, y, 1, 6, STEEL_DARK);
			rect(im, x, y + 5, 6, 1, STEEL_DARK);
			// etiqueta clara
			rect(im, x + 2, y + 1, 2, 1, 0xFFD8D2C0);
			// puxador
			rect(im, x + 1, y + 3, 4, 1, p[0]);
			rect(im, x + 1, y + 4, 4, 1, p[2]);
		}
		// luz de status
		set(im, 13, 13, GLOW);
		noise(im, 300 + seed, 0.06);
		return im;
	}

	// ---------------------------------------------------------------- cabo

	/** O modelo usa a regiao 5..11: casca escura com o fio brilhante no meio e bordas. */
	static BufferedImage cable() {
		BufferedImage im = img();
		rect(im, 0, 0, 16, 16, STEEL);
		rect(im, 0, 0, 16, 16, STEEL);
		for (int i = 0; i < 16; i++) {
			set(im, i, 5, STEEL_DARK);
			set(im, i, 10, STEEL_DARK);
			set(im, 5, i, STEEL_DARK);
			set(im, 10, i, STEEL_DARK);
			set(im, i, 7, GLOW_DIM);
			set(im, i, 8, GLOW);
			set(im, 7, i, GLOW_DIM);
			set(im, 8, i, GLOW);
		}
		rect(im, 7, 7, 2, 2, 0xFFB8FFF8);
		rect(im, 6, 6, 1, 1, STEEL_LIGHT);
		rect(im, 9, 9, 1, 1, STEEL_LIGHT);
		noise(im, 400, 0.08);
		return im;
	}

	// ---------------------------------------------------------------- terminais

	static void casing(BufferedImage im) {
		rect(im, 0, 0, 16, 16, STEEL);
		rect(im, 0, 0, 16, 1, STEEL_LIGHT);
		rect(im, 0, 0, 1, 16, STEEL_LIGHT);
		rect(im, 0, 15, 16, 1, STEEL_DEEP);
		rect(im, 15, 0, 1, 16, STEEL_DEEP);
		for (int[] c : new int[][] {{2, 2}, {13, 2}, {2, 13}, {13, 13}}) {
			set(im, c[0], c[1], STEEL_DEEP);
			set(im, c[0] - 1, c[1] - 1, STEEL_LIGHT);
		}
	}

	static BufferedImage terminalSide() {
		BufferedImage im = img();
		casing(im);
		rect(im, 4, 4, 8, 8, STEEL_DARK);
		rect(im, 4, 4, 8, 1, STEEL_DEEP);
		for (int y = 6; y <= 10; y += 2) {
			rect(im, 5, y, 6, 1, STEEL_DEEP);
		}
		// fio da rede passando
		rect(im, 0, 8, 4, 1, GLOW_DIM);
		rect(im, 12, 8, 4, 1, GLOW_DIM);
		noise(im, 500, 0.08);
		return im;
	}

	static BufferedImage terminalTop() {
		BufferedImage im = img();
		casing(im);
		for (int y = 4; y <= 11; y += 2) {
			rect(im, 4, y, 8, 1, STEEL_DEEP);
		}
		noise(im, 600, 0.08);
		return im;
	}

	/** Tela com a lista de itens: barra de busca e quadradinhos coloridos em grade. */
	static BufferedImage terminalFront() {
		BufferedImage im = img();
		casing(im);
		rect(im, 2, 2, 12, 11, STEEL_DEEP);
		rect(im, 3, 3, 10, 9, SCREEN);
		// barra de busca
		rect(im, 8, 3, 5, 1, GLOW_DIM);
		// grade de itens 4x3
		int[] colors = {0xFFB0B0B0, 0xFFE8C040, 0xFF60C060, 0xFFD06040, 0xFF6090E0, 0xFF9A9A9A, 0xFFE07AD0, 0xFF8B5A2B, 0xFF4FE3D6, 0xFFF0A07A,
				0xFFFFFFFF, 0xFF6E6268};
		Random rnd = new Random(7);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 4; col++) {
				int c = colors[(row * 4 + col) % colors.length];
				int x = 4 + col * 2 + (col >= 2 ? 1 : 0);
				int y = 5 + row * 2;
				if (rnd.nextInt(6) != 0) {
					set(im, x, y, c);
				}
			}
		}
		// barra de rolagem
		rect(im, 12, 5, 1, 6, 0xFF1C3A3E);
		set(im, 12, 5, GLOW_DIM);
		set(im, 12, 6, GLOW_DIM);
		// teclado / base
		rect(im, 4, 13, 8, 1, STEEL_DARK);
		set(im, 12, 13, GLOW);
		return im;
	}

	/** Tela com a grade 3x3, seta e resultado. */
	static BufferedImage craftingFront() {
		BufferedImage im = img();
		casing(im);
		rect(im, 2, 2, 12, 11, STEEL_DEEP);
		rect(im, 3, 3, 10, 9, SCREEN);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				set(im, 4 + col * 2, 5 + row * 2, (row + col) % 2 == 0 ? GLOW : GLOW_DIM);
			}
		}
		// seta
		rect(im, 9, 7, 2, 1, GLOW_DIM);
		set(im, 10, 6, GLOW_DIM);
		set(im, 10, 8, GLOW_DIM);
		// resultado
		rect(im, 11, 6, 1, 3, 0xFFE8C040);
		rect(im, 4, 13, 8, 1, STEEL_DARK);
		set(im, 12, 13, 0xFFE8C040);
		return im;
	}

	/** Topo de bancada: madeira com a grade do crafting. */
	static BufferedImage craftingTop() {
		BufferedImage im = img();
		casing(im);
		int wood = 0xFFA0723E;
		int woodDark = 0xFF6E4A22;
		rect(im, 2, 2, 12, 12, wood);
		frame(im, 2, 2, 12, 12, woodDark);
		rect(im, 6, 3, 1, 10, woodDark);
		rect(im, 9, 3, 1, 10, woodDark);
		rect(im, 3, 6, 10, 1, woodDark);
		rect(im, 3, 9, 10, 1, woodDark);
		noise(im, 700, 0.12);
		return im;
	}

	// ---------------------------------------------------------------- utilitarios

	static BufferedImage img() {
		return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
	}

	static void save(BufferedImage im, File dir, String name) throws IOException {
		ImageIO.write(im, "png", new File(dir, name + ".png"));
	}

	static void set(BufferedImage im, int x, int y, int c) {
		if (x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight()) {
			im.setRGB(x, y, c);
		}
	}

	static void rect(BufferedImage im, int x0, int y0, int w, int h, int c) {
		for (int x = x0; x < x0 + w; x++) {
			for (int y = y0; y < y0 + h; y++) {
				set(im, x, y, c);
			}
		}
	}

	static void frame(BufferedImage im, int x0, int y0, int w, int h, int c) {
		rect(im, x0, y0, w, 1, c);
		rect(im, x0, y0 + h - 1, w, 1, c);
		rect(im, x0, y0, 1, h, c);
		rect(im, x0 + w - 1, y0, 1, h, c);
	}

	static int shade(int c, double f) {
		int a = (c >>> 24) & 0xFF;
		int r = clamp((int) (((c >> 16) & 0xFF) * f));
		int g = clamp((int) (((c >> 8) & 0xFF) * f));
		int b = clamp((int) ((c & 0xFF) * f));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	static void noise(BufferedImage im, long seed, double amount) {
		Random rnd = new Random(seed);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				int c = im.getRGB(x, y);
				if ((c >>> 24) != 0) {
					im.setRGB(x, y, shade(c, 1.0 + (rnd.nextDouble() - 0.5) * amount));
				}
			}
		}
	}
}
