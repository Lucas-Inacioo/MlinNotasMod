import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.zip.ZipFile;

/**
 * Texturas da lapide: pedra da lapide (andesito polido do jar vanilla, um pouco mais fria e escura) com musgo no alto
 * e nos cantos, frente com cruz e moldura entalhadas (o nome e o dia sao escritos por cima pelo renderer) e o icone
 * do minimapa/mapa grande. A base do modelo usa as texturas vanilla de tijolo musgoso direto.
 *
 * <p>Uso (na raiz): java scripts/GraveTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class GraveTexGen {
	public static void main(String[] args) throws IOException {
		BufferedImage andesite;
		BufferedImage moss;
		try (ZipFile jar = new ZipFile(args[0])) {
			andesite = read(jar, "polished_andesite");
			moss = read(jar, "moss_block");
		}
		File blocks = new File(args[1], "textures/block");
		File gui = new File(args[1], "textures/gui");
		blocks.mkdirs();
		gui.mkdirs();
		ImageIO.write(side(andesite, moss), "png", new File(blocks, "gravestone_side.png"));
		ImageIO.write(front(andesite, moss), "png", new File(blocks, "gravestone_front.png"));
		ImageIO.write(marker(), "png", new File(gui, "grave_marker.png"));
	}

	static BufferedImage read(ZipFile jar, String name) throws IOException {
		try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/block/" + name + ".png"))) {
			return ImageIO.read(in);
		}
	}

	/** Pedra base: andesito polido esfriado (granito velho). */
	static BufferedImage stone(BufferedImage andesite) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = andesite.getRGB(x, y);
				img.setRGB(x, y, rgb(r(c) * 0.86, g(c) * 0.88, b(c) * 0.94));
			}
		}
		return img;
	}

	static BufferedImage side(BufferedImage andesite, BufferedImage moss) {
		BufferedImage img = stone(andesite);
		Random random = new Random(41);
		// musgo escorrendo do alto, mais denso em cima
		for (int y = 0; y < 6; y++) {
			for (int x = 0; x < 16; x++) {
				double chance = 0.85 - y * 0.17;
				if (random.nextDouble() < chance) {
					img.setRGB(x, y, moss.getRGB(x, y));
				}
			}
		}
		// manchas de liquen
		for (int i = 0; i < 6; i++) {
			int x = random.nextInt(16);
			int y = 6 + random.nextInt(10);
			img.setRGB(x, y, blend(img.getRGB(x, y), 0x8C9A6A, 0.55));
		}
		return img;
	}

	/**
	 * Frente: a pedra ocupa x 3..12; corpo em y 3..12 e o arco em y 0..2 (como no modelo, v = 16 - altura). Cruz
	 * entalhada no alto, moldura fina e musgo nas bordas do arco e nos cantos de baixo. O meio (y 5..11) fica liso para
	 * o nome e o dia.
	 */
	static BufferedImage front(BufferedImage andesite, BufferedImage moss) {
		BufferedImage img = stone(andesite);
		// cruz
		for (int y = 0; y <= 5; y++) {
			engrave(img, 7, y);
			engrave(img, 8, y);
		}
		for (int x = 5; x <= 10; x++) {
			engrave(img, x, 2);
		}
		// moldura: laterais e base do campo do texto
		for (int y = 6; y <= 11; y++) {
			engrave(img, 4, y);
			engrave(img, 11, y);
		}
		for (int x = 4; x <= 11; x++) {
			engrave(img, x, 11);
		}
		// musgo: bordas do arco e cantos de baixo
		int[][] spots = {{6, 0}, {9, 0}, {4, 1}, {5, 1}, {11, 1}, {3, 3}, {3, 4}, {12, 3}, {3, 2}, {12, 2},
				{3, 12}, {4, 12}, {3, 11}, {12, 12}, {11, 12}, {12, 10}, {6, 12}, {9, 12}};
		for (int[] s : spots) {
			img.setRGB(s[0], s[1], moss.getRGB(s[0], s[1]));
		}
		// sombra na base (umidade)
		for (int x = 3; x <= 12; x++) {
			img.setRGB(x, 12, blend(img.getRGB(x, 12), 0x2E3A26, 0.25));
		}
		return img;
	}

	/** Entalhe: pixel escuro e luz no pixel de baixo/direita (profundidade). */
	static void engrave(BufferedImage img, int x, int y) {
		int c = img.getRGB(x, y);
		img.setRGB(x, y, rgb(r(c) * 0.52, g(c) * 0.52, b(c) * 0.56));
		if (y + 1 < 16) {
			int below = img.getRGB(x, y + 1);
			if (r(below) > r(c) * 0.7) {
				img.setRGB(x, y + 1, rgb(r(below) * 1.08, g(below) * 1.08, b(below) * 1.08));
			}
		}
	}

	/** Icone do mapa: lapide arredondada com cruz, base de terra e um tufo de musgo; contorno escuro. */
	static BufferedImage marker() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		boolean[][] mask = new boolean[16][16];
		for (int y = 2; y <= 12; y++) {
			int from = y == 2 ? 6 : y == 3 ? 5 : 4;
			int to = y == 2 ? 9 : y == 3 ? 10 : 11;
			for (int x = from; x <= to; x++) {
				mask[y][x] = true;
			}
		}
		for (int y = 12; y <= 13; y++) {
			for (int x = 2; x <= 13; x++) {
				mask[y][x] = true;
			}
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (mask[y][x]) {
					double shade = 1.0 - (x - 4) * 0.035 - (y - 2) * 0.01;
					int base = y >= 12 ? 0x7C6A55 : 0xC9CBD2;
					img.setRGB(x, y, rgb(((base >> 16) & 0xFF) * shade, ((base >> 8) & 0xFF) * shade, (base & 0xFF) * shade));
				}
			}
		}
		// contorno
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (mask[y][x]) {
					continue;
				}
				boolean edge = false;
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = x + d[0];
					int ny = y + d[1];
					if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && mask[ny][nx]) {
						edge = true;
					}
				}
				if (edge) {
					img.setRGB(x, y, 0xFF1C1C22);
				}
			}
		}
		// cruz
		for (int y = 4; y <= 9; y++) {
			img.setRGB(7, y, 0xFF3A3A44);
			img.setRGB(8, y, 0xFF3A3A44);
		}
		for (int x = 5; x <= 10; x++) {
			img.setRGB(x, 5, 0xFF3A3A44);
		}
		// musgo
		for (int[] s : new int[][] {{4, 11}, {5, 11}, {11, 10}, {11, 11}, {10, 11}, {6, 2}, {5, 3}}) {
			img.setRGB(s[0], s[1], 0xFF5E7A2E);
		}
		return img;
	}

	static int blend(int argb, int rgb, double t) {
		return rgb(r(argb) * (1 - t) + ((rgb >> 16) & 0xFF) * t, g(argb) * (1 - t) + ((rgb >> 8) & 0xFF) * t, b(argb) * (1 - t) + (rgb & 0xFF) * t);
	}

	static int r(int c) {
		return c >> 16 & 0xFF;
	}

	static int g(int c) {
		return c >> 8 & 0xFF;
	}

	static int b(int c) {
		return c & 0xFF;
	}

	static int rgb(double r, double g, double b) {
		return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
	}

	static int clamp(double v) {
		return (int) Math.max(0, Math.min(255, Math.round(v)));
	}
}
