import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.function.IntPredicate;
import java.util.zip.ZipFile;

/**
 * Texturas da cozinha: culturas (4 estagios + forma selvagem), sementes, comidas, Panela, Tabua de Corte, Barril de
 * Conserva e o icone do Bem Alimentado. Recolore texturas vanilla do jar do cliente (formas de maca, cenoura, tigelas,
 * garrafa de mel, estagios de cenoura/batata/beterraba/trigo) e desenha por cima o que nao existe (alface, morango,
 * queijo, espigas de milho...).
 *
 * Uso: java scripts/FoodTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class FoodTexGen {
	static ZipFile jar;
	static File items;
	static File blocks;

	public static void main(String[] args) throws IOException {
		jar = new ZipFile(args[0]);
		File assets = new File(args[1]);
		items = new File(assets, "textures/item");
		blocks = new File(assets, "textures/block");
		File effects = new File(assets, "textures/mob_effect");
		items.mkdirs();
		blocks.mkdirs();
		effects.mkdirs();

		produce();
		seeds();
		dishes();
		crops();
		stations();
		wellFedIcon(effects);
		System.out.println("ok");
	}

	// ------------------------------------------------------------------ utilitarios

	static BufferedImage vanilla(String path) throws IOException {
		try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/" + path + ".png"))) {
			BufferedImage src = ImageIO.read(in);
			BufferedImage out = new BufferedImage(src.getWidth(), Math.min(src.getHeight(), src.getWidth()), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < out.getHeight(); y++) {
				for (int x = 0; x < out.getWidth(); x++) {
					out.setRGB(x, y, src.getRGB(x, y));
				}
			}
			return out;
		}
	}

	static BufferedImage copy(BufferedImage im) {
		BufferedImage out = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < im.getHeight(); y++) {
			for (int x = 0; x < im.getWidth(); x++) {
				out.setRGB(x, y, im.getRGB(x, y));
			}
		}
		return out;
	}

	static BufferedImage blank() {
		return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
	}

	static void save(BufferedImage im, File dir, String name) throws IOException {
		ImageIO.write(im, "png", new File(dir, name + ".png"));
	}

	static int a(int c) {
		return c >>> 24;
	}

	static int r(int c) {
		return (c >> 16) & 0xFF;
	}

	static int g(int c) {
		return (c >> 8) & 0xFF;
	}

	static int b(int c) {
		return c & 0xFF;
	}

	static int rgb(int r, int g, int b) {
		return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	static double lum(int c) {
		return (0.299 * r(c) + 0.587 * g(c) + 0.114 * b(c)) / 255.0;
	}

	static float[] hsb(int c) {
		return java.awt.Color.RGBtoHSB(r(c), g(c), b(c), null);
	}

	static double hue(int c) {
		return hsb(c)[0] * 360.0;
	}

	static double sat(int c) {
		return hsb(c)[1];
	}

	static boolean isRed(int c) {
		double h = hue(c);
		return a(c) > 0 && sat(c) > 0.35 && (h < 20 || h > 330);
	}

	static boolean isOrange(int c) {
		double h = hue(c);
		return a(c) > 0 && sat(c) > 0.35 && h >= 12 && h < 50;
	}

	static boolean isGreen(int c) {
		double h = hue(c);
		return a(c) > 0 && sat(c) > 0.2 && h >= 60 && h < 170;
	}

	static boolean isBrown(int c) {
		double h = hue(c);
		return a(c) > 0 && h >= 15 && h < 55 && lum(c) < 0.45;
	}

	/** Gradiente por varios pontos (t de 0 a 1). */
	static int ramp(double t, int... stops) {
		t = Math.max(0, Math.min(1, t));
		double pos = t * (stops.length - 1);
		int i = Math.min(stops.length - 2, (int) pos);
		double f = pos - i;
		int c0 = stops[i];
		int c1 = stops[i + 1];
		return rgb((int) Math.round(r(c0) + (r(c1) - r(c0)) * f), (int) Math.round(g(c0) + (g(c1) - g(c0)) * f),
				(int) Math.round(b(c0) + (b(c1) - b(c0)) * f));
	}

	/** Recolore os pixels escolhidos mapeando a luminancia (normalizada entre eles) no gradiente escuro->claro. */
	static void recolor(BufferedImage im, IntPredicate select, int... stops) {
		double min = 1;
		double max = 0;
		for (int y = 0; y < im.getHeight(); y++) {
			for (int x = 0; x < im.getWidth(); x++) {
				int c = im.getRGB(x, y);
				if (a(c) > 0 && select.test(c)) {
					min = Math.min(min, lum(c));
					max = Math.max(max, lum(c));
				}
			}
		}
		double span = Math.max(0.05, max - min);
		for (int y = 0; y < im.getHeight(); y++) {
			for (int x = 0; x < im.getWidth(); x++) {
				int c = im.getRGB(x, y);
				if (a(c) > 0 && select.test(c)) {
					int out = ramp((lum(c) - min) / span, stops);
					im.setRGB(x, y, (c & 0xFF000000) | (out & 0xFFFFFF));
				}
			}
		}
	}

	static void recolorAll(BufferedImage im, int... stops) {
		recolor(im, c -> true, stops);
	}

	static void set(BufferedImage im, int x, int y, int c) {
		if (x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight()) {
			im.setRGB(x, y, c);
		}
	}

	static boolean opaque(BufferedImage im, int x, int y) {
		return x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight() && a(im.getRGB(x, y)) > 0;
	}

	/** Contorno escuro de 1 px em volta dos pixels opacos (estilo dos itens vanilla). */
	static void outline(BufferedImage im, int color) {
		BufferedImage src = copy(im);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (!opaque(src, x, y) && (opaque(src, x - 1, y) || opaque(src, x + 1, y) || opaque(src, x, y - 1) || opaque(src, x, y + 1))) {
					im.setRGB(x, y, color);
				}
			}
		}
	}

	/** Disco preenchido com gradiente radial (claro em cima-esquerda). */
	static void blob(BufferedImage im, double cx, double cy, double rx, double ry, int... stops) {
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = (x + 0.5 - cx) / rx;
				double dy = (y + 0.5 - cy) / ry;
				double d = dx * dx + dy * dy;
				if (d <= 1.0) {
					double light = 1.0 - Math.min(1, Math.sqrt((dx + 0.45) * (dx + 0.45) + (dy + 0.45) * (dy + 0.45)) / 1.6);
					im.setRGB(x, y, ramp(light, stops));
				}
			}
		}
	}

	/** Pontinhos de cor sobre os pixels escolhidos (deterministico). */
	static void specks(BufferedImage im, IntPredicate where, long seed, double chance, int... colors) {
		Random random = new Random(seed);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = im.getRGB(x, y);
				double roll = random.nextDouble();
				int pick = random.nextInt(colors.length);
				if (a(c) > 0 && where.test(c) && roll < chance) {
					im.setRGB(x, y, colors[pick]);
				}
			}
		}
	}

	// ------------------------------------------------------------------ produtos

	static void produce() throws IOException {
		// tomate: maca vermelho-alaranjada com o cabinho/folha em estrela verde
		BufferedImage tomato = vanilla("item/apple");
		recolor(tomato, FoodTexGen::isRed, 0xFF6A0F08, 0xFFC4220F, 0xFFF0452A, 0xFFFF9A7A);
		recolor(tomato, c -> !isRed(c) && a(c) > 0 && lum(c) > 0.12 && hue(c) > 40 && hue(c) < 180 || isBrown(c), 0xFF1F4A12, 0xFF3D8A22, 0xFF6CC23A);
		save(tomato, items, "tomato");

		// pimentao: maca verde brilhante com cabo escuro
		BufferedImage pepper = vanilla("item/apple");
		recolor(pepper, FoodTexGen::isRed, 0xFF0F3A0C, 0xFF237A16, 0xFF3FB02A, 0xFFB6F08A);
		recolor(pepper, c -> isBrown(c) || isGreen(c) && lum(c) < 0.5, 0xFF1A2A0A, 0xFF3A5A12);
		save(pepper, items, "bell_pepper");

		// milho: cenoura amarela com graos (xadrez) e palha verde-clara
		BufferedImage corn = vanilla("item/carrot");
		recolor(corn, FoodTexGen::isOrange, 0xFF8A6008, 0xFFE0B020, 0xFFFFE070);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = corn.getRGB(x, y);
				if (a(c) > 0 && hue(c) > 30 && hue(c) < 60 && sat(c) > 0.4 && (x + y) % 2 == 0) {
					corn.setRGB(x, y, rgb((int) (r(c) * 0.85), (int) (g(c) * 0.8), (int) (b(c) * 0.6)));
				}
			}
		}
		recolor(corn, FoodTexGen::isGreen, 0xFF4A6A1A, 0xFF8AB040, 0xFFD0E890);
		save(corn, items, "corn");

		// milho assado: o milho tostado
		BufferedImage roasted = copy(corn);
		recolor(roasted, c -> a(c) > 0 && hue(c) > 25 && hue(c) < 65 && sat(c) > 0.3, 0xFF5A3008, 0xFFB07818, 0xFFE8B040);
		specks(roasted, c -> hue(c) > 20 && hue(c) < 60 && sat(c) > 0.3, 31, 0.25, 0xFF4A2A08, 0xFF6A3A10);
		recolor(roasted, FoodTexGen::isGreen, 0xFF5A4A1A, 0xFF8A7A3A, 0xFFB8A868);
		save(roasted, items, "roasted_corn");

		// cebola: bulbo dourado redondo com ponta e brotinho verde
		BufferedImage onion = blank();
		blob(onion, 8, 10, 5.5, 5, 0xFF7A4A18, 0xFFC8903A, 0xFFF0CC80, 0xFFFFF0C8);
		for (int y = 3; y < 6; y++) {
			set(onion, 8, y, 0xFFC8903A);
			set(onion, 7, y + 1, 0xFFB07830);
		}
		set(onion, 8, 1, 0xFF5CAA2A);
		set(onion, 9, 2, 0xFF4E9A22);
		set(onion, 8, 2, 0xFF4E9A22);
		for (int y = 7; y < 15; y++) {
			if (opaque(onion, 6, y)) {
				set(onion, 6, y, 0xFFB07830);
			}
			if (opaque(onion, 10, y)) {
				set(onion, 10, y, 0xFFB07830);
			}
		}
		outline(onion, 0xFF4A2A08);
		save(onion, items, "onion");

		// alho: batata branco-creme com gomos
		BufferedImage garlic = vanilla("item/potato");
		recolorAll(garlic, 0xFF6A6050, 0xFFC8BCA0, 0xFFF0EADA, 0xFFFFFFFF);
		for (int y = 4; y < 14; y++) {
			if (opaque(garlic, 6, y)) {
				set(garlic, 6, y, 0xFFB0A488);
			}
			if (opaque(garlic, 10, y)) {
				set(garlic, 10, y, 0xFFB0A488);
			}
		}
		save(garlic, items, "garlic");

		// alface: cabeca de folhas crespas verde-clara
		BufferedImage lettuce = blank();
		blob(lettuce, 8, 8.5, 6.5, 6, 0xFF2E6A14, 0xFF4E9A22, 0xFF7CCB3A, 0xFFB8F078);
		Random random = new Random(5);
		for (int i = 0; i < 18; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int x = (int) Math.round(8 + Math.cos(angle) * 6.6);
			int y = (int) Math.round(8.5 + Math.sin(angle) * 6.1);
			set(lettuce, x, y, 0xFF5CAA2A);
		}
		for (int i = 0; i < 5; i++) {
			set(lettuce, 8, 9 + i, 0xFFD8F8B0);
			set(lettuce, 6 - i / 2, 10 + i / 2 + 1, 0xFFC8EC98);
			set(lettuce, 10 + i / 2, 10 + i / 2 + 1, 0xFFC8EC98);
		}
		outline(lettuce, 0xFF1A3A0A);
		save(lettuce, items, "lettuce");

		// repolho: bola verde-azulada com nervuras claras
		BufferedImage cabbage = blank();
		blob(cabbage, 8, 8.5, 6.5, 6.2, 0xFF2A5A3A, 0xFF4A8A5A, 0xFF8CC498, 0xFFD0F0D0);
		for (int y = 4; y < 14; y++) {
			set(cabbage, 8 + (y - 9) / 4, y, 0xFFE0F8E0);
		}
		for (int x = 3; x < 13; x++) {
			int y = 6 + Math.abs(x - 8) / 2;
			if (opaque(cabbage, x, y)) {
				set(cabbage, x, y, 0xFFC0E8C8);
			}
		}
		outline(cabbage, 0xFF173A22);
		save(cabbage, items, "cabbage");

		// morango: coracao vermelho com sementinhas e coroa verde
		BufferedImage strawberry = blank();
		for (int y = 4; y < 15; y++) {
			double half = y < 7 ? 5.5 : 5.5 - (y - 7) * 0.62;
			for (int x = 0; x < 16; x++) {
				if (Math.abs(x + 0.5 - 8) <= half) {
					double t = 1.0 - ((x - 4) * 0.06 + (y - 4) * 0.07);
					strawberry.setRGB(x, y, ramp(t, 0xFF6A0A10, 0xFFC0162A, 0xFFF04050, 0xFFFF9AA0));
				}
			}
		}
		for (int[] p : new int[][] {{5, 6}, {9, 6}, {11, 8}, {7, 8}, {4, 9}, {9, 10}, {6, 11}, {10, 12}, {8, 13}, {12, 6}}) {
			set(strawberry, p[0], p[1], 0xFFFFE070);
		}
		for (int[] p : new int[][] {{6, 3}, {7, 3}, {8, 3}, {9, 3}, {10, 3}, {5, 4}, {7, 4}, {9, 4}, {11, 4}, {8, 2}, {8, 1}}) {
			set(strawberry, p[0], p[1], p[1] <= 2 ? 0xFF3A6A18 : 0xFF4E9A22);
		}
		outline(strawberry, 0xFF3A0608);
		save(strawberry, items, "strawberry");

		// arroz: graos brancos
		BufferedImage rice = vanilla("item/wheat_seeds");
		recolorAll(rice, 0xFFB8B0A0, 0xFFF0ECE0, 0xFFFFFFFF);
		save(rice, items, "rice");

		// feijao: preto com brilho
		BufferedImage beans = vanilla("item/pumpkin_seeds");
		recolorAll(beans, 0xFF140C0A, 0xFF2E201C, 0xFF5A4A44);
		save(beans, items, "beans");

		// farinha e fuba: montinhos (acucar recolorido)
		BufferedImage flour = vanilla("item/sugar");
		recolorAll(flour, 0xFFB8A888, 0xFFE8DCC0, 0xFFFFF8EA);
		save(flour, items, "flour");
		BufferedImage cornmeal = vanilla("item/sugar");
		recolorAll(cornmeal, 0xFFB07A10, 0xFFF0C030, 0xFFFFE890);
		save(cornmeal, items, "cornmeal");
	}

	static void seeds() throws IOException {
		BufferedImage lettuce = vanilla("item/wheat_seeds");
		recolorAll(lettuce, 0xFF5A5A48, 0xFFA8A890, 0xFFE0E0C8);
		save(lettuce, items, "lettuce_seeds");
		BufferedImage tomato = vanilla("item/pumpkin_seeds");
		recolorAll(tomato, 0xFF8A7A30, 0xFFD8C870, 0xFFFFF4B0);
		save(tomato, items, "tomato_seeds");
		BufferedImage corn = vanilla("item/melon_seeds");
		recolorAll(corn, 0xFF8A5A08, 0xFFE8A820, 0xFFFFE070);
		save(corn, items, "corn_seeds");
		BufferedImage pepper = vanilla("item/pumpkin_seeds");
		recolorAll(pepper, 0xFF9A8A60, 0xFFEAE0B8, 0xFFFFFFF0);
		save(pepper, items, "bell_pepper_seeds");
		BufferedImage cabbage = vanilla("item/melon_seeds");
		recolorAll(cabbage, 0xFF2A140C, 0xFF5A2A1A, 0xFF8A4A30);
		save(cabbage, items, "cabbage_seeds");
		BufferedImage strawberry = vanilla("item/wheat_seeds");
		recolorAll(strawberry, 0xFF8A2010, 0xFFE05030, 0xFFFFC060);
		save(strawberry, items, "strawberry_seeds");
	}

	// ------------------------------------------------------------------ pratos

	/** Tigela vanilla com o conteudo recolorido (pixels que diferem da tigela vazia). */
	static BufferedImage bowl(String base, int... stops) throws IOException {
		BufferedImage stew = vanilla("item/" + base);
		BufferedImage empty = vanilla("item/bowl");
		BufferedImage out = copy(stew);
		double min = 1;
		double max = 0;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (a(stew.getRGB(x, y)) > 0 && stew.getRGB(x, y) != empty.getRGB(x, y)) {
					min = Math.min(min, lum(stew.getRGB(x, y)));
					max = Math.max(max, lum(stew.getRGB(x, y)));
				}
			}
		}
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = stew.getRGB(x, y);
				if (a(c) > 0 && c != empty.getRGB(x, y)) {
					out.setRGB(x, y, ramp((lum(c) - min) / Math.max(0.05, max - min), stops));
				}
			}
		}
		return out;
	}

	/** Pinta pontinhos so no conteudo da tigela. */
	static void bowlSpecks(BufferedImage dish, String base, long seed, double chance, int... colors) throws IOException {
		BufferedImage empty = vanilla("item/bowl");
		BufferedImage stew = vanilla("item/" + base);
		Random random = new Random(seed);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double roll = random.nextDouble();
				int pick = random.nextInt(colors.length);
				if (a(stew.getRGB(x, y)) > 0 && stew.getRGB(x, y) != empty.getRGB(x, y) && roll < chance) {
					dish.setRGB(x, y, colors[pick]);
				}
			}
		}
	}

	/** Garrafa de mel com o liquido recolorido. */
	static BufferedImage bottle(int... stops) throws IOException {
		BufferedImage im = vanilla("item/honey_bottle");
		recolor(im, c -> sat(c) > 0.45 && hue(c) > 15 && hue(c) < 60, stops);
		return im;
	}

	static void dishes() throws IOException {
		BufferedImage riceBeans = bowl("mushroom_stew", 0xFFB8B0A0, 0xFFF0ECE0, 0xFFFFFFFF);
		bowlSpecks(riceBeans, "mushroom_stew", 11, 0.22, 0xFF4A2E22, 0xFF6A4A38);
		save(riceBeans, items, "rice_and_beans");

		BufferedImage feijoada = bowl("rabbit_stew", 0xFF140A08, 0xFF30201A, 0xFF5A3E30);
		bowlSpecks(feijoada, "rabbit_stew", 12, 0.22, 0xFFC05030, 0xFFE09060, 0xFF8A3A28);
		save(feijoada, items, "feijoada");

		BufferedImage veg = bowl("mushroom_stew", 0xFF8A5010, 0xFFD89030, 0xFFF0C060);
		bowlSpecks(veg, "mushroom_stew", 13, 0.3, 0xFF4E9A22, 0xFFE0601A, 0xFF8CC498);
		save(veg, items, "vegetable_soup");

		BufferedImage pumpkin = bowl("beetroot_soup", 0xFFA04A08, 0xFFE07A18, 0xFFFFB050);
		bowlSpecks(pumpkin, "beetroot_soup", 14, 0.08, 0xFFFFE0A0);
		save(pumpkin, items, "pumpkin_soup");

		BufferedImage beef = bowl("rabbit_stew", 0xFF3A1A0A, 0xFF7A3A18, 0xFFB86A30);
		bowlSpecks(beef, "rabbit_stew", 15, 0.18, 0xFFE07A20, 0xFFD8C060);
		save(beef, items, "beef_stew");

		BufferedImage galinhada = bowl("mushroom_stew", 0xFFA07010, 0xFFE8C040, 0xFFFFF0A0);
		bowlSpecks(galinhada, "mushroom_stew", 16, 0.3, 0xFFC08040, 0xFF3FB02A, 0xFFE04030);
		save(galinhada, items, "galinhada");

		BufferedImage pasta = bowl("rabbit_stew", 0xFFB08020, 0xFFF0D060, 0xFFFFF0B0);
		bowlSpecks(pasta, "rabbit_stew", 17, 0.35, 0xFFC4220F, 0xFFE04028);
		save(pasta, items, "pasta");

		BufferedImage polenta = bowl("beetroot_soup", 0xFFC08A10, 0xFFF0C838, 0xFFFFF0A0);
		save(polenta, items, "polenta");

		BufferedImage garden = bowl("rabbit_stew", 0xFF2E6A14, 0xFF5CAA2A, 0xFFB8F078);
		bowlSpecks(garden, "rabbit_stew", 18, 0.25, 0xFFE0301A, 0xFFF0E0B0, 0xFF8A3A9A);
		save(garden, items, "garden_salad");

		BufferedImage fruit = bowl("rabbit_stew", 0xFFA01A20, 0xFFE04050, 0xFFFF9AA0);
		bowlSpecks(fruit, "rabbit_stew", 19, 0.45, 0xFFF0A020, 0xFF7CCB3A, 0xFFFFE070, 0xFF6A1030);
		save(fruit, items, "fruit_salad");

		BufferedImage popcorn = bowl("rabbit_stew", 0xFFD8C090, 0xFFFFF4D8, 0xFFFFFFFF);
		bowlSpecks(popcorn, "rabbit_stew", 20, 0.2, 0xFFFFE070, 0xFFE8C040);
		// a pipoca nao tem tigela: apaga a tigela e deixa so o monte
		BufferedImage empty = vanilla("item/bowl");
		BufferedImage stew = vanilla("item/rabbit_stew");
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (stew.getRGB(x, y) == empty.getRGB(x, y)) {
					popcorn.setRGB(x, y, 0);
				}
			}
		}
		BufferedImage popcornShifted = blank();
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (y + 3 < 16) {
					popcornShifted.setRGB(x, y + 3, popcorn.getRGB(x, y));
				}
			}
		}
		outline(popcornShifted, 0xFF8A7040);
		save(popcornShifted, items, "popcorn");

		save(bottle(0xFF4A0410, 0xFF9A1024, 0xFFE03048, 0xFFFF90A0), items, "strawberry_jam");
		save(bottle(0xFF5A1004, 0xFFB02810, 0xFFE85A30, 0xFFFFA070), items, "tomato_sauce");
		BufferedImage pickles = bottle(0xFF5A6A10, 0xFFA8B830, 0xFFE0E880);
		specks(pickles, c -> hue(c) > 50 && hue(c) < 80 && sat(c) > 0.3, 21, 0.3, 0xFF2E6A14, 0xFFC04020);
		save(pickles, items, "pickles");

		BufferedImage bread = vanilla("item/bread");
		recolorAll(bread, 0xFF6A4008, 0xFFC08A20, 0xFFF0C850, 0xFFFFF0A0);
		save(bread, items, "corn_bread");

		// pao de queijo: tres bolinhas douradas
		BufferedImage cheeseBread = blank();
		blob(cheeseBread, 5.5, 10, 3.6, 3.3, 0xFFA06A10, 0xFFE0A830, 0xFFFFE090);
		blob(cheeseBread, 11, 10.5, 3.6, 3.3, 0xFFA06A10, 0xFFE0A830, 0xFFFFE090);
		blob(cheeseBread, 8.2, 5.8, 3.6, 3.3, 0xFFA06A10, 0xFFE8B038, 0xFFFFE8A0);
		specks(cheeseBread, c -> true, 22, 0.08, 0xFFFFF4D0);
		outline(cheeseBread, 0xFF5A3808);
		save(cheeseBread, items, "cheese_bread");

		BufferedImage omelette = vanilla("item/pumpkin_pie");
		recolor(omelette, c -> isOrange(c) || hue(c) >= 30 && hue(c) < 60 && sat(c) > 0.3, 0xFF9A6A08, 0xFFE8C030, 0xFFFFF0A0);
		recolor(omelette, c -> a(c) > 0 && sat(c) <= 0.3, 0xFFC09040, 0xFFF8E090);
		specks(omelette, c -> hue(c) > 40 && hue(c) < 60, 23, 0.12, 0xFF3FB02A, 0xFFE04030);
		save(omelette, items, "omelette");

		BufferedImage pie = vanilla("item/pumpkin_pie");
		recolor(pie, FoodTexGen::isOrange, 0xFF7A0A18, 0xFFC0162A, 0xFFF04050);
		specks(pie, c -> isRed(c), 24, 0.15, 0xFFFF9AA0);
		save(pie, items, "strawberry_pie");

		BufferedImage cake = vanilla("item/cake");
		recolor(cake, c -> a(c) > 0 && lum(c) > 0.7, 0xFFE0A830, 0xFFFFE070);
		recolor(cake, FoodTexGen::isRed, 0xFFFFF0B0, 0xFFFFFFE0);
		recolor(cake, c -> a(c) > 0 && hue(c) > 15 && hue(c) < 50 && lum(c) <= 0.7 && sat(c) > 0.2, 0xFF7A5010, 0xFFC08A20, 0xFFF0C040);
		save(cake, items, "corn_cake");

		BufferedImage jerky = vanilla("item/cooked_beef");
		recolorAll(jerky, 0xFF2A0A06, 0xFF5A1A10, 0xFF8A3A20, 0xFFC07050);
		save(jerky, items, "charque");

		BufferedImage fish = vanilla("item/cooked_cod");
		recolor(fish, c -> a(c) > 0 && sat(c) > 0.1 || lum(c) > 0.3, 0xFF8A7A58, 0xFFD8CCA8, 0xFFFFF8E8);
		save(fish, items, "dried_fish");

		save(freshCheese(), items, "fresh_cheese");
		save(cheeseWheel(true), items, "aged_cheese");

		// chucrute: pote de barro com repolho fermentado
		BufferedImage crock = blank();
		for (int y = 5; y < 15; y++) {
			double half = y < 7 ? 4.5 : y > 12 ? 4.5 : 5.5;
			for (int x = 0; x < 16; x++) {
				if (Math.abs(x + 0.5 - 8) <= half) {
					crock.setRGB(x, y, ramp(1.0 - (x - 2) / 13.0, 0xFF5A2E14, 0xFF9A5A2E, 0xFFC88A50));
				}
			}
		}
		for (int x = 3; x < 13; x++) {
			set(crock, x, 4, 0xFFE8E0A0);
			set(crock, x, 5, x % 3 == 0 ? 0xFFB8C060 : 0xFFF0E8B0);
		}
		for (int x = 3; x < 13; x++) {
			set(crock, x, 8, 0xFF7A4020);
		}
		outline(crock, 0xFF2E160A);
		save(crock, items, "sauerkraut");
	}

	/** Queijo fresco (tipo minas): roda branca baixa vista de lado-cima. */
	static BufferedImage freshCheese() {
		BufferedImage im = blank();
		for (int y = 7; y < 13; y++) {
			for (int x = 2; x < 14; x++) {
				im.setRGB(x, y, ramp((x - 2) / 12.0, 0xFFF4F0E4, 0xFFE0DACA, 0xFFC8C0AC));
			}
		}
		for (int y = 3; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = (x + 0.5 - 8) / 6.0;
				double top = (y + 0.5 - 7) / 3.2;
				double bottom = (y + 0.5 - 12.5) / 2.2;
				if (dx * dx + top * top <= 1.0) {
					im.setRGB(x, y, ramp(1.0 - (dx + top + 1) / 3.0, 0xFFE8E4D8, 0xFFFFFFFA));
				} else if (dx * dx + bottom * bottom <= 1.0 && y >= 12) {
					im.setRGB(x, y, 0xFFD0C8B4);
				}
			}
		}
		set(im, 6, 6, 0xFFF0EADA);
		set(im, 10, 7, 0xFFF0EADA);
		outline(im, 0xFF6A665A);
		return im;
	}

	/** Queijo em fatia (cunha): fresco branco, curado amarelo com buracos. */
	static BufferedImage cheeseWheel(boolean aged) {
		BufferedImage im = blank();
		int top = aged ? 0xFFFFE070 : 0xFFFFFFF4;
		int side = aged ? 0xFFE8B020 : 0xFFE8E4D8;
		int dark = aged ? 0xFFB07810 : 0xFFC8C0B0;
		int rind = aged ? 0xFFC88A10 : 0xFFD8D2C4;
		// face de cima (triangulo) e lateral
		for (int y = 3; y < 9; y++) {
			for (int x = 2 + (8 - y) * 0; x < 14; x++) {
				if (x - 2 >= (y - 3) * 2 - 0 && x <= 13 - (8 - y) / 3) {
					im.setRGB(x, y, top);
				}
			}
		}
		for (int y = 9; y < 14; y++) {
			for (int x = 2; x < 14; x++) {
				im.setRGB(x, y, x < 12 ? side : rind);
			}
		}
		for (int y = 3; y < 9; y++) {
			int x0 = 2 + (y - 3) * 2;
			for (int x = x0; x < Math.min(14, x0 + 2); x++) {
				im.setRGB(x, y, top);
			}
		}
		// preenche o triangulo de cima direito
		for (int y = 3; y < 9; y++) {
			for (int x = 2 + (y - 3) * 2; x < 14; x++) {
				im.setRGB(x, y, x >= 12 ? rind : top);
			}
		}
		for (int x = 2; x < 14; x++) {
			im.setRGB(x, 9, dark);
		}
		if (aged) {
			for (int[] p : new int[][] {{5, 11}, {9, 12}, {7, 10}, {10, 6}, {12, 4}, {3, 12}}) {
				set(im, p[0], p[1], 0xFFB88A10);
			}
		}
		outline(im, aged ? 0xFF6A4A08 : 0xFF6A665A);
		return im;
	}

	// ------------------------------------------------------------------ culturas

	interface Stage {
		BufferedImage make(int stage) throws IOException;
	}

	static void crops() throws IOException {
		// alface: folhas claras; no fim, cabecas redondas
		cropSet("lettuce", s -> {
			BufferedImage im = vanilla("block/carrots_stage" + s);
			recolor(im, FoodTexGen::isGreen, 0xFF2E6A14, 0xFF5CAA2A, 0xFFA8E070);
			recolor(im, FoodTexGen::isOrange, 0xFF5CAA2A, 0xFF8CD848);
			if (s == 3) {
				heads(im, 0xFF3E8A1A, 0xFF7CCB3A, 0xFFC8F890);
			}
			return im;
		});
		// repolho: folhas verde-azuladas e cabecas grandes
		cropSet("cabbage", s -> {
			BufferedImage im = vanilla("block/potatoes_stage" + s);
			recolor(im, FoodTexGen::isGreen, 0xFF1F4A2A, 0xFF4A8A5A, 0xFF8CC498);
			if (s >= 2) {
				heads(im, 0xFF3A7A4A, 0xFF8CC498, 0xFFD8F4D8);
			}
			return im;
		});
		// cebola: bulbos dourados
		cropSet("onion", s -> {
			BufferedImage im = vanilla("block/carrots_stage" + s);
			recolor(im, FoodTexGen::isOrange, 0xFF7A4A18, 0xFFC89040, 0xFFF0D090);
			return im;
		});
		// alho: folhas verde-azuladas, bulbos brancos
		cropSet("garlic", s -> {
			BufferedImage im = vanilla("block/carrots_stage" + s);
			recolor(im, FoodTexGen::isGreen, 0xFF2A5A4A, 0xFF5A9A82, 0xFFA8D8C0);
			recolor(im, FoodTexGen::isOrange, 0xFFA89A88, 0xFFE8E0D0, 0xFFFFFFFF);
			return im;
		});
		// tomate: pe com frutos vermelhos no fim
		cropSet("tomato", s -> {
			BufferedImage im = vanilla("block/beetroots_stage" + s);
			recolor(im, c -> isRed(c) || hue(c) > 280 && a(c) > 0 && sat(c) > 0.2, 0xFF6A0F08, 0xFFD8301A, 0xFFFF7A5A);
			if (s == 3) {
				fruits(im, 0xFF8A1008, 0xFFE8301A, 0xFFFF8A6A, 33);
			}
			return im;
		});
		// pimentao: frutos amarelos
		cropSet("bell_pepper", s -> {
			BufferedImage im = vanilla("block/beetroots_stage" + s);
			recolor(im, c -> isRed(c) || hue(c) > 280 && a(c) > 0 && sat(c) > 0.2, 0xFF8A6A08, 0xFFF0C020, 0xFFFFF080);
			if (s == 3) {
				fruits(im, 0xFF9A7A08, 0xFFF0C820, 0xFFFFF4A0, 34);
			}
			return im;
		});
		// feijao: ramas com vagens
		cropSet("beans", s -> {
			BufferedImage im = vanilla("block/potatoes_stage" + s);
			recolor(im, FoodTexGen::isGreen, 0xFF1F4A12, 0xFF3D8A22, 0xFF7CC04A);
			if (s >= 2) {
				pods(im, s == 3 ? 0xFFC8B060 : 0xFF6CB040, 35);
			}
			return im;
		});
		// morango: rasteiro, flores brancas e frutinhas vermelhas
		cropSet("strawberry", s -> {
			BufferedImage im = vanilla("block/potatoes_stage" + s);
			recolor(im, FoodTexGen::isGreen, 0xFF1F4A12, 0xFF3D8A22, 0xFF6CC23A);
			if (s == 2) {
				dots(im, 0xFFFFFFFF, 0xFFFFE070, 36);
			}
			if (s == 3) {
				fruits(im, 0xFF7A0A10, 0xFFE02838, 0xFFFF9AA0, 37);
			}
			return im;
		});
		// milho: pe alto verde, espigas amarelas no fim
		cropSet("corn", s -> {
			BufferedImage im = vanilla("block/wheat_stage" + (s * 2 + 1));
			recolorAll(im, 0xFF1F4A12, 0xFF3D8A22, 0xFF7CC04A);
			if (s == 3) {
				for (int x : new int[] {3, 8, 12}) {
					for (int y = 6; y < 11; y++) {
						set(im, x, y, y % 2 == 0 ? 0xFFF0C020 : 0xFFFFE070);
						set(im, x + 1, y, 0xFFC09018);
					}
					set(im, x, 5, 0xFF9AC060);
					set(im, x + 1, 11, 0xFF6A9A3A);
				}
				for (int x = 2; x < 14; x += 2) {
					set(im, x, 0, 0xFFE8D8A0);
					set(im, x + 1, 1, 0xFFC8B880);
				}
			}
			return im;
		});
		// arroz: como trigo, mas palha clara
		cropSet("rice", s -> {
			BufferedImage im = vanilla("block/wheat_stage" + (s * 2 + 1));
			if (s < 3) {
				recolorAll(im, 0xFF1F5A2A, 0xFF4AA04A, 0xFF8CD87A);
			} else {
				recolorAll(im, 0xFF8A7A40, 0xFFD8C890, 0xFFFFF4D0);
			}
			return im;
		});
	}

	static void cropSet(String name, Stage stage) throws IOException {
		for (int s = 0; s < 4; s++) {
			save(stage.make(s), blocks, name + "_crop_stage" + s);
		}
		// forma selvagem: o estagio maduro
		save(stage.make(3), blocks, "wild_" + name);
	}

	/** Cabecas redondas (alface/repolho) na base. */
	static void heads(BufferedImage im, int dark, int mid, int light) {
		for (int[] h : new int[][] {{3, 13}, {8, 12}, {13, 13}}) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dx = -2; dx <= 2; dx++) {
					if (dx * dx + dy * dy <= 5) {
						int c = dy < 0 && dx < 1 ? light : dx + dy > 1 ? dark : mid;
						set(im, h[0] + dx, h[1] + dy, c);
					}
				}
			}
		}
	}

	/** Frutos de 2x2 espalhados sobre a folhagem. */
	static void fruits(BufferedImage im, int dark, int mid, int light, long seed) {
		Random random = new Random(seed);
		for (int i = 0; i < 6; i++) {
			int x = 1 + random.nextInt(13);
			int y = 4 + random.nextInt(9);
			set(im, x, y, light);
			set(im, x + 1, y, mid);
			set(im, x, y + 1, mid);
			set(im, x + 1, y + 1, dark);
		}
	}

	/** Vagens (traco vertical de 3 px). */
	static void pods(BufferedImage im, int color, long seed) {
		Random random = new Random(seed);
		for (int i = 0; i < 6; i++) {
			int x = 1 + random.nextInt(14);
			int y = 5 + random.nextInt(8);
			for (int k = 0; k < 3; k++) {
				set(im, x, y + k, k == 0 ? brighten(color) : color);
			}
		}
	}

	/** Florzinhas (centro amarelo). */
	static void dots(BufferedImage im, int petal, int center, long seed) {
		Random random = new Random(seed);
		for (int i = 0; i < 5; i++) {
			int x = 2 + random.nextInt(12);
			int y = 6 + random.nextInt(7);
			set(im, x, y, center);
			set(im, x - 1, y, petal);
			set(im, x + 1, y, petal);
			set(im, x, y - 1, petal);
		}
	}

	static int brighten(int c) {
		return rgb(r(c) + 40, g(c) + 40, b(c) + 30);
	}

	// ------------------------------------------------------------------ estacoes

	static void stations() throws IOException {
		// panela de ferro: textura usada nas linhas 8..15 (lado) e no quadrado 3..12 (topo)
		BufferedImage side = blank();
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int base = y <= 9 ? (y == 8 ? 0xFF8A8A92 : 0xFF6A6A72) : ramp((x - 3) / 10.0, 0xFF2A2A30, 0xFF4A4A52, 0xFF3A3A42);
				side.setRGB(x, y, base);
			}
		}
		for (int x = 4; x < 13; x += 4) {
			side.setRGB(x, 11, 0xFF7A7A82);
		}
		for (int x = 0; x < 16; x++) {
			side.setRGB(x, 15, 0xFF1E1E22);
		}
		save(side, blocks, "cooking_pot_side");

		BufferedImage top = blank();
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				boolean rim = x == 3 || x == 12 || y == 3 || y == 12;
				boolean inner = x == 4 || x == 11 || y == 4 || y == 11;
				int c;
				if (rim) {
					c = 0xFF8A8A92;
				} else if (inner) {
					c = 0xFF2A2A30;
				} else {
					double d = Math.hypot(x - 7.5, y - 7.5);
					c = ramp(1.0 - d / 4.5, 0xFF7A4A1A, 0xFFB07028, 0xFFD89A48);
				}
				top.setRGB(x, y, c);
			}
		}
		top.setRGB(6, 6, 0xFFF0C878);
		top.setRGB(9, 8, 0xFFE0B060);
		top.setRGB(7, 9, 0xFF5A8A2A);
		save(top, blocks, "cooking_pot_top");

		BufferedImage bottom = blank();
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				bottom.setRGB(x, y, (x + y) % 5 == 0 ? 0xFF26262A : 0xFF1E1E22);
			}
		}
		save(bottom, blocks, "cooking_pot_bottom");
		BufferedImage handle = blank();
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				handle.setRGB(x, y, y % 2 == 0 ? 0xFF5A5A62 : 0xFF3A3A42);
			}
		}
		save(handle, blocks, "cooking_pot_handle");

		// tabua: madeira clara com veios e borda escura
		BufferedImage planks = vanilla("block/birch_planks");
		BufferedImage board = copy(planks);
		recolorAll(board, 0xFF9A7448, 0xFFD0A870, 0xFFF0D0A0);
		for (int i = 0; i < 16; i++) {
			board.setRGB(i, 1, 0xFF8A6438);
			board.setRGB(i, 14, 0xFF8A6438);
			board.setRGB(2, i, 0xFF8A6438);
			board.setRGB(13, i, 0xFF8A6438);
		}
		board.setRGB(7, 13, 0xFF5A3E20);
		board.setRGB(8, 13, 0xFF5A3E20);
		save(board, blocks, "cutting_board_top");
		BufferedImage boardSide = copy(planks);
		recolorAll(boardSide, 0xFF7A5430, 0xFFB08850, 0xFFD0A870);
		save(boardSide, blocks, "cutting_board_side");

		// barril de conserva: barril vanilla mais escuro com aros de cobre
		BufferedImage barrelSide = vanilla("block/barrel_side");
		recolor(barrelSide, c -> sat(c) < 0.25 && lum(c) < 0.5, 0xFF6A3A1A, 0xFFB86A3A, 0xFFE89A5A);
		recolor(barrelSide, c -> sat(c) >= 0.25, 0xFF3A2414, 0xFF6A4428, 0xFF8A5A34);
		save(barrelSide, blocks, "preserving_barrel_side");
		BufferedImage barrelTop = vanilla("block/barrel_top");
		recolor(barrelTop, c -> sat(c) < 0.25 && lum(c) < 0.5, 0xFF6A3A1A, 0xFFB86A3A, 0xFFE89A5A);
		recolor(barrelTop, c -> sat(c) >= 0.25, 0xFF3A2414, 0xFF6A4428, 0xFF8A5A34);
		save(barrelTop, blocks, "preserving_barrel_top");
		BufferedImage barrelBottom = vanilla("block/barrel_bottom");
		recolor(barrelBottom, c -> sat(c) < 0.25 && lum(c) < 0.5, 0xFF6A3A1A, 0xFFB86A3A, 0xFFE89A5A);
		recolor(barrelBottom, c -> sat(c) >= 0.25, 0xFF3A2414, 0xFF6A4428, 0xFF8A5A34);
		save(barrelBottom, blocks, "preserving_barrel_bottom");
	}

	static void wellFedIcon(File dir) throws IOException {
		BufferedImage stew = bowl("rabbit_stew", 0xFF8A4010, 0xFFE07A20, 0xFFFFC060);
		BufferedImage im = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				im.setRGB(x + 1, y + 2, stew.getRGB(x, y));
			}
		}
		// vapor
		for (int[] p : new int[][] {{6, 1}, {7, 2}, {6, 3}, {10, 0}, {11, 1}, {10, 2}, {11, 3}}) {
			im.setRGB(p[0], p[1], 0xC0FFFFFF);
		}
		save(im, dir, "well_fed");
	}
}
