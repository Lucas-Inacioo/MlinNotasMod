import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Gera texturas 16x16 procedurais para o mod. Uso: java TexGen.java <pasta-assets-mlinnotas>
 */
public class TexGen {
	static final int T = 0x00000000;

	public static void main(String[] args) throws IOException {
		File assets = new File(args[0]);
		File items = new File(assets, "textures/item");
		File blocks = new File(assets, "textures/block");
		items.mkdirs();
		blocks.mkdirs();

		// Varinhas
		wand(items, "wooden_wand", 0xFFC8A060, 0xFF8B5A2B);
		wand(items, "stone_wand", 0xFFA0A0A0, 0xFF606060);
		wand(items, "iron_wand", 0xFFE8E8E8, 0xFFA0A0A0);
		wand(items, "diamond_wand", 0xFF7FE9E6, 0xFF2BAFA8);

		tablet(items, "crafting_tablet");

		furnaceUpgrade(items, "furnace_upgrade_iron", 0xFFE8E8E8, 0xFFA0A0A0);
		furnaceUpgrade(items, "furnace_upgrade_gold", 0xFFF6D23A, 0xFFB08A10);
		furnaceUpgrade(items, "furnace_upgrade_diamond", 0xFF7FE9E6, 0xFF2BAFA8);

		// Baus
		chest(blocks, "iron_chest", 0xFFD8D8D8, 0xFF9A9A9A, 0xFF6E6E6E);
		chest(blocks, "gold_chest", 0xFFF6D23A, 0xFFC9A520, 0xFF8A6D0E);
		chest(blocks, "diamond_chest", 0xFF8FF3EF, 0xFF4CC9C3, 0xFF2B8A86);
		upgrade(items, "chest_upgrade_iron", 0xFFD8D8D8, 0xFF8B5A2B);
		upgrade(items, "chest_upgrade_gold", 0xFFF6D23A, 0xFFD8D8D8);
		upgrade(items, "chest_upgrade_diamond", 0xFF8FF3EF, 0xFFF6D23A);

		// Mochila
		backpack(items, "backpack", 0xFF8B5A2B, 0xFF5C3A18);
		upgrade(items, "backpack_upgrade_size", 0xFF8B5A2B, 0xFFE8E8E8);
		upgrade(items, "backpack_upgrade_crafting", 0xFF8B5A2B, 0xFFC8A060);
		upgrade(items, "backpack_upgrade_furnace", 0xFF8B5A2B, 0xFFFF8C1A);
		upgrade(items, "backpack_upgrade_water", 0xFF8B5A2B, 0xFF3A6FE0);
		upgrade(items, "backpack_upgrade_lava", 0xFF8B5A2B, 0xFFE0521A);

		// Minimapa (icones da HUD)
		arrow(items, "map_arrow", 0xFFFFFFFF, 0xFF202020);
		waypoint(items, "waypoint", 0xFFFFD83A);
	}

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

	static void wand(File dir, String name, int tip, int tipDark) throws IOException {
		BufferedImage im = img();
		int stick = 0xFF8B5A2B;
		int stickDark = 0xFF5C3A18;
		// cabo diagonal de (2,13) a (11,4)
		for (int i = 0; i < 10; i++) {
			set(im, 2 + i, 13 - i, stick);
			set(im, 3 + i, 13 - i, stickDark);
		}
		// ponta
		rect(im, 11, 2, 3, 3, tip);
		set(im, 13, 4, tipDark);
		set(im, 11, 2, 0xFFFFFFFF);
		set(im, 12, 1, tipDark);
		set(im, 14, 3, tipDark);
		set(im, 12, 5, tipDark);
		set(im, 10, 3, tipDark);
		save(im, dir, name);
	}

	static void tablet(File dir, String name) throws IOException {
		BufferedImage im = img();
		rect(im, 2, 1, 12, 14, 0xFF3A3A3A);
		frame(im, 2, 1, 12, 14, 0xFF202020);
		rect(im, 3, 2, 10, 11, 0xFF7FBF7F);
		rect(im, 4, 3, 8, 9, 0xFF5E9E5E);
		for (int gx = 0; gx < 3; gx++) {
			for (int gy = 0; gy < 3; gy++) {
				rect(im, 4 + gx * 3, 3 + gy * 3, 2, 2, 0xFFC8A060);
			}
		}
		rect(im, 7, 13, 2, 1, 0xFF909090);
		save(im, dir, name);
	}

	static void furnaceUpgrade(File dir, String name, int border, int borderDark) throws IOException {
		BufferedImage im = img();
		rect(im, 2, 2, 12, 12, 0xFF8A8A8A);
		noise(im, name.hashCode(), 0.25);
		rect(im, 5, 8, 6, 4, 0xFF303030);
		rect(im, 6, 9, 4, 3, 0xFFFF7A00);
		rect(im, 7, 9, 2, 1, 0xFFFFD040);
		frame(im, 2, 2, 12, 12, border);
		set(im, 13, 13, borderDark);
		set(im, 2, 13, borderDark);
		set(im, 13, 2, borderDark);
		frame(im, 1, 1, 14, 14, borderDark);
		save(im, dir, name);
	}

	static void chest(File dir, String name, int light, int base, int dark) throws IOException {
		// lateral
		BufferedImage side = img();
		rect(side, 0, 0, 16, 16, base);
		noise(side, name.hashCode(), 0.12);
		frame(side, 0, 0, 16, 16, dark);
		rect(side, 1, 1, 14, 1, light);
		rect(side, 1, 1, 1, 14, light);
		rect(side, 0, 7, 16, 1, dark);
		save(side, dir, name + "_side");
		// frente (com fechadura)
		BufferedImage front = img();
		front.setData(side.getData());
		rect(front, 7, 5, 2, 4, 0xFF303030);
		rect(front, 7, 5, 1, 1, 0xFF808080);
		save(front, dir, name + "_front");
		// topo
		BufferedImage top = img();
		rect(top, 0, 0, 16, 16, base);
		noise(top, name.hashCode() + 1, 0.12);
		frame(top, 0, 0, 16, 16, dark);
		frame(top, 1, 1, 14, 14, light);
		save(top, dir, name + "_top");
	}

	static void upgrade(File dir, String name, int from, int to) throws IOException {
		BufferedImage im = img();
		rect(im, 1, 4, 6, 8, from);
		frame(im, 1, 4, 6, 8, shade(from, 0.6));
		rect(im, 9, 4, 6, 8, to);
		frame(im, 9, 4, 6, 8, shade(to, 0.6));
		// seta
		rect(im, 6, 7, 4, 2, 0xFFFFFFFF);
		set(im, 9, 6, 0xFFFFFFFF);
		set(im, 9, 9, 0xFFFFFFFF);
		set(im, 10, 7, 0xFFFFFFFF);
		set(im, 10, 8, 0xFFFFFFFF);
		save(im, dir, name);
	}

	static void backpack(File dir, String name, int base, int dark) throws IOException {
		BufferedImage im = img();
		rect(im, 3, 3, 10, 12, base);
		noise(im, 42, 0.15);
		frame(im, 3, 3, 10, 12, dark);
		rect(im, 4, 2, 8, 2, dark);
		rect(im, 5, 1, 6, 1, dark);
		rect(im, 5, 8, 6, 5, shade(base, 0.85));
		frame(im, 5, 8, 6, 5, dark);
		rect(im, 7, 6, 2, 1, 0xFFE8E8E8);
		save(im, dir, name);
	}

	static void arrow(File dir, String name, int fill, int outline) throws IOException {
		BufferedImage im = img();
		for (int y = 2; y < 14; y++) {
			int half = (y - 2) / 2;
			for (int x = 8 - half; x <= 7 + half; x++) {
				set(im, x, y, fill);
			}
		}
		rect(im, 3, 13, 4, 1, T);
		rect(im, 9, 13, 4, 1, T);
		rect(im, 6, 12, 4, 2, fill);
		for (int y = 1; y < 15; y++) {
			for (int x = 1; x < 15; x++) {
				if (im.getRGB(x, y) == 0) {
					boolean edge = im.getRGB(x + 1, y) == fill || im.getRGB(x - 1, y) == fill || im.getRGB(x, y + 1) == fill || im.getRGB(x, y - 1) == fill;
					if (edge) {
						set(im, x, y, outline);
					}
				}
			}
		}
		save(im, dir, name);
	}

	static void waypoint(File dir, String name, int color) throws IOException {
		BufferedImage im = img();
		for (int y = 2; y <= 9; y++) {
			int half = y <= 5 ? y - 2 : 9 - y + 1;
			rect(im, 8 - half - 1, y, half * 2 + 2, 1, color);
		}
		rect(im, 7, 10, 2, 4, color);
		rect(im, 7, 5, 2, 2, 0xFF202020);
		save(im, dir, name);
	}
}
