import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Gera os icones 16x16 das armas do "Combate melhor" (montante, adaga, martelo de guerra, glaive, rapieira) em todos
 * os 12 niveis e as 5 armas de chefe. As formas sao desenhadas por programa numa grade diagonal (cabo embaixo a
 * esquerda, ponta em cima a direita, como as espadas vanilla): {@code a = x - y} corre ao longo da arma e
 * {@code p = x + y - 15} atravessa. Cada parte (lamina, guarda, cabo, pomo, joia) tem contorno escuro e sombreado
 * com luz vindo de cima a esquerda.
 *
 * <p>As cores de cada nivel vanilla saem da lamina da espada vanilla do mesmo material (quantis de brilho); as dos
 * niveis novos sao os degrades do TierTexGen. O cabo de madeira vem da espada de ferro vanilla.</p>
 *
 * Uso (na raiz): java scripts/WeaponTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas [previa.png]
 */
public class WeaponTexGen {
	/** Paleta de 5 tons: contorno, escuro, medio, claro, brilho. */
	record Palette(int outline, int dark, int mid, int light, int highlight) {
		int shade(double t) {
			// t = 0 lado iluminado (cima/esquerda) .. 1 lado da sombra
			if (t < 0.2) {
				return highlight;
			}
			if (t < 0.5) {
				return light;
			}
			if (t < 0.8) {
				return mid;
			}
			return dark;
		}
	}

	static final int BLADE = 1;
	static final int GUARD = 2;
	static final int HANDLE = 3;
	static final int POMMEL = 4;
	static final int ACCENT = 5;

	/** Desenho de uma arma: parte e sombreado (0..1) por pixel. */
	static final class Canvas {
		final int[][] part = new int[16][16];
		final double[][] t = new double[16][16];
		final boolean[][] noOutline = new boolean[16][16];

		/** Pinta a faixa {@code pMin..pMax} (transversal) para cada {@code a} em {@code aFrom..aTo}. */
		void band(int kind, int aFrom, int aTo, java.util.function.IntUnaryOperator pMin, java.util.function.IntUnaryOperator pMax,
				boolean outline) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					int a = x - y;
					int p = x + y - 15;
					if (a < aFrom || a > aTo) {
						continue;
					}
					int lo = pMin.applyAsInt(a);
					int hi = pMax.applyAsInt(a);
					if (p < lo || p > hi) {
						continue;
					}
					part[x][y] = kind;
					t[x][y] = hi == lo ? 0.4 : (p - lo) / (double) (hi - lo);
					noOutline[x][y] = !outline;
				}
			}
		}

		void band(int kind, int aFrom, int aTo, int pMin, int pMax, boolean outline) {
			band(kind, aFrom, aTo, a -> pMin, a -> pMax, outline);
		}

		/** Pinta um pixel pela coordenada diagonal (se cair na grade). */
		void dot(int kind, int a, int p, double shade) {
			if ((a + p + 15) % 2 != 0) {
				p++;
			}
			int x = (a + p + 15) / 2;
			int y = (p - a + 15) / 2;
			if (x >= 0 && x < 16 && y >= 0 && y < 16) {
				part[x][y] = kind;
				t[x][y] = shade;
				noOutline[x][y] = true;
			}
		}

		/** Troca o sombreado dos pixels de {@code kind} numa faixa (sulco, faces escuras). */
		void shadeLine(int kind, int aFrom, int aTo, int pMin, int pMax, double shade) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					int a = x - y;
					int p = x + y - 15;
					if (part[x][y] == kind && a >= aFrom && a <= aTo && p >= pMin && p <= pMax) {
						t[x][y] = shade;
					}
				}
			}
		}

		boolean filled(int x, int y) {
			return x >= 0 && x < 16 && y >= 0 && y < 16 && part[x][y] != 0;
		}

		BufferedImage render(Palette blade, Palette guard, Palette handle, Palette accent) {
			BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					int kind = part[x][y];
					if (kind == 0) {
						continue;
					}
					Palette palette = switch (kind) {
						case BLADE -> blade;
						case GUARD, POMMEL -> guard;
						case HANDLE -> handle;
						default -> accent;
					};
					boolean edge = !filled(x - 1, y) || !filled(x + 1, y) || !filled(x, y - 1) || !filled(x, y + 1);
					int color;
					if (edge && !noOutline[x][y]) {
						color = palette.outline();
					} else {
						color = palette.shade(t[x][y]);
					}
					out.setRGB(x, y, color);
				}
			}
			return out;
		}
	}

	// ------------------------------------------------------------------ formas

	static Canvas greatsword() {
		Canvas c = new Canvas();
		c.band(POMMEL, -15, -14, -1, 1, true);
		c.band(HANDLE, -13, -8, 0, 1, false);
		c.band(GUARD, -7, -6, -6, 6, true);
		// lamina larga com sulco escuro no meio
		c.band(BLADE, -5, 15, a -> a >= 15 ? 0 : a >= 14 ? -1 : a >= 12 ? -2 : -3, a -> a >= 15 ? 0 : a >= 14 ? 1 : a >= 12 ? 2 : 3, true);
		c.shadeLine(BLADE, -4, 10, 0, 1, 0.9);
		return c;
	}

	static Canvas dagger() {
		Canvas c = new Canvas();
		c.band(POMMEL, -11, -10, -1, 1, true);
		c.band(HANDLE, -9, -6, 0, 1, false);
		c.band(GUARD, -5, -4, -3, 3, true);
		c.band(BLADE, -3, 9, a -> a >= 7 ? 0 : -2, a -> a >= 8 ? 0 : a >= 5 ? 1 : 2, true);
		return c;
	}

	static Canvas warhammer() {
		Canvas c = new Canvas();
		c.band(POMMEL, -15, -13, -1, 1, true);
		c.band(HANDLE, -12, 4, 0, 1, false);
		c.band(GUARD, 3, 4, -1, 2, true);
		// cabeca retangular perpendicular ao cabo; as faces de bater mais escuras
		c.band(BLADE, 5, 9, -5, 5, true);
		c.shadeLine(BLADE, 5, 9, -4, -3, 0.1);
		c.shadeLine(BLADE, 5, 9, 3, 4, 0.95);
		c.band(GUARD, 9, 11, -1, 1, true);
		c.dot(ACCENT, 7, 0, 0.3);
		return c;
	}

	static Canvas glaive() {
		Canvas c = new Canvas();
		c.band(POMMEL, -15, -14, -1, 1, true);
		c.band(HANDLE, -13, 2, 0, 1, false);
		c.band(GUARD, 1, 3, -2, 3, true);
		// lamina de um gume, curva para cima/esquerda, com um esporao do outro lado
		c.band(BLADE, 3, 14, a -> a <= 12 ? -(int) Math.round(1.0 + 3.2 * Math.pow((a - 3) / 9.0, 1.3)) : -(int) Math.round(4.0 - 1.4 * (a - 12)), a -> a >= 12 ? 1 - (a - 12) : 1, true);
		c.band(BLADE, 3, 4, 2, 3, true);
		return c;
	}

	static Canvas rapier() {
		Canvas c = new Canvas();
		c.band(POMMEL, -15, -13, -1, 1, true);
		c.band(HANDLE, -12, -9, 0, 1, false);
		// guarda em cruz e arco das juntas ao lado do cabo
		c.band(GUARD, -8, -7, -4, 4, true);
		c.band(GUARD, -13, -8, 3, 4, false);
		c.band(GUARD, -14, -13, 1, 4, false);
		c.band(BLADE, -6, 15, a -> 0, a -> a >= 13 ? 0 : 1, false);
		return c;
	}

	static Canvas shape(String kind) {
		return switch (kind) {
			case "greatsword" -> greatsword();
			case "dagger" -> dagger();
			case "warhammer" -> warhammer();
			case "glaive" -> glaive();
			case "rapier" -> rapier();
			default -> throw new IllegalArgumentException(kind);
		};
	}

	// ------------------------------------------------------------------ cores

	static ZipFile jar;

	static BufferedImage vanilla(String path) throws IOException {
		var entry = jar.getEntry("assets/minecraft/textures/" + path + ".png");
		if (entry == null) {
			throw new IOException("textura vanilla nao encontrada: " + path);
		}
		try (InputStream in = jar.getInputStream(entry)) {
			BufferedImage read = ImageIO.read(in);
			BufferedImage argb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
			argb.getGraphics().drawImage(read, 0, 0, null);
			return argb;
		}
	}

	static double luminance(int argb) {
		return (0.299 * ((argb >> 16) & 0xFF) + 0.587 * ((argb >> 8) & 0xFF) + 0.114 * (argb & 0xFF)) / 255.0;
	}

	/** Paleta das cores distintas da textura escolhidas pelo filtro, ordenadas por brilho (escura..clara). */
	static Palette sample(BufferedImage image, java.util.function.BiPredicate<Integer, Integer> where) {
		List<Integer> pixels = new ArrayList<>();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int p = image.getRGB(x, y);
				if ((p >>> 24) > 0 && where.test(x, y) && !pixels.contains(p | 0xFF000000)) {
					pixels.add(p | 0xFF000000);
				}
			}
		}
		pixels.sort((u, v) -> Double.compare(luminance(u), luminance(v)));
		int n = pixels.size() - 1;
		return new Palette(pixels.get(0), pixels.get(Math.round(n * 0.3F)), pixels.get(Math.round(n * 0.55F)), pixels.get(Math.round(n * 0.8F)),
				pixels.get(n));
	}

	static int lerp(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	/** Paleta a partir do degrade escuro/base/claro (os mesmos do TierTexGen). */
	static Palette gradient(int light, int base, int dark) {
		return new Palette(lerp(dark, 0xFF000000, 0.45), dark, base, lerp(base, light, 0.5), light);
	}

	static Palette darker(Palette p) {
		return new Palette(p.outline(), lerp(p.dark(), 0xFF000000, 0.15), p.dark(), p.mid(), p.light());
	}

	record Material(String prefix, Palette blade) {
	}

	public static void main(String[] args) throws IOException {
		jar = new ZipFile(args[0]);
		File items = new File(args[1], "textures/item");
		items.mkdirs();
		// cabo: parte de madeira da espada de ferro (canto de baixo a esquerda)
		BufferedImage ironSword = vanilla("item/iron_sword");
		Palette wood = sample(ironSword, (x, y) -> x - y <= -9);
		Palette handle = new Palette(wood.outline(), wood.dark(), wood.mid(), wood.light(), wood.light());

		List<Material> materials = new ArrayList<>();
		for (String prefix : List.of("wooden", "stone", "copper", "iron", "golden")) {
			materials.add(new Material(prefix, sample(vanilla("item/" + prefix + "_sword"), (x, y) -> x - y >= 0)));
		}
		materials.add(new Material("cobalt", gradient(0xFF7FA8FF, 0xFF2F5FD0, 0xFF13265C)));
		materials.add(new Material("diamond", sample(vanilla("item/diamond_sword"), (x, y) -> x - y >= 0)));
		materials.add(new Material("netherite", sample(vanilla("item/netherite_sword"), (x, y) -> x - y >= 0)));
		materials.add(new Material("infernite", gradient(0xFFFFB060, 0xFFE0401A, 0xFF6A1406)));
		materials.add(new Material("endite", gradient(0xFFE6D0FF, 0xFFA77BE8, 0xFF452870)));
		materials.add(new Material("astralite", gradient(0xFFFFC0F0, 0xFFE45CC8, 0xFF5E1450)));
		materials.add(new Material("celestium", gradient(0xFFFFFFFF, 0xFFFFE89A, 0xFFA8862E)));

		String[] kinds = {"greatsword", "dagger", "warhammer", "glaive", "rapier"};
		List<BufferedImage> preview = new ArrayList<>();
		for (String kind : kinds) {
			for (Material m : materials) {
				Palette guard = darker(m.blade());
				BufferedImage image = shape(kind).render(m.blade(), guard, handle, m.blade());
				ImageIO.write(image, "png", new File(items, m.prefix() + "_" + kind + ".png"));
				preview.add(image);
			}
		}

		// armas de chefe: mesmas formas, paletas proprias e joias brilhantes
		Palette iron = sample(ironSword, (x, y) -> x - y >= 0);
		Palette cobalt = gradient(0xFF7FA8FF, 0xFF2F5FD0, 0xFF13265C);
		Palette coreGlow = new Palette(0xFF0E2A66, 0xFF2F7CFF, 0xFF5FB8FF, 0xFFA8F0FF, 0xFFFFFFFF);
		Palette black = new Palette(0xFF07080C, 0xFF14161E, 0xFF1F2330, 0xFF2E3446, 0xFF3E4660);
		Palette abyss = new Palette(0xFF03141A, 0xFF0A3440, 0xFF0F5F6A, 0xFF1FB8C4, 0xFF8FFFFF);
		Palette sculk = new Palette(0xFF02303A, 0xFF0B8C9E, 0xFF29DFEB, 0xFF9FFFFF, 0xFFFFFFFF);
		Palette molten = new Palette(0xFF140A08, 0xFF2E201D, 0xFF4A302A, 0xFFFF7A1A, 0xFFFFD36A);
		Palette ember = new Palette(0xFF5A1404, 0xFFE0401A, 0xFFFF8A2A, 0xFFFFD36A, 0xFFFFFFC0);
		Palette basalt = new Palette(0xFF0C0A0A, 0xFF241E1E, 0xFF3A3030, 0xFF524646, 0xFF6A5A5A);
		Palette scale = new Palette(0xFF12051A, 0xFF2E0F3D, 0xFF5A2A78, 0xFFB57EDC, 0xFFF0D8FF);
		Palette voidGem = new Palette(0xFF1A0626, 0xFF8A2BE2, 0xFFCC66FF, 0xFFF0C0FF, 0xFFFFFFFF);
		Palette astral = gradient(0xFFFFFFFF, 0xFFFF9AE8, 0xFF6A1A5E);
		Palette star = new Palette(0xFF6A5A10, 0xFFFFE07A, 0xFFFFF4B0, 0xFFFFFFFF, 0xFFFFFFFF);
		Palette gold = sample(vanilla("item/golden_sword"), (x, y) -> x - y >= 0);

		Canvas hammer = warhammer();
		hammer.dot(ACCENT, 7, -2, 0.1);
		hammer.dot(ACCENT, 7, 2, 0.5);
		write(items, "golem_hammer", hammer.render(cobalt, iron, handle, coreGlow), preview);

		Canvas blade = rapier();
		blade.dot(ACCENT, -7, 0, 0.1);
		write(items, "abyssal_blade", blade.render(abyss, black, black, sculk), preview);

		Canvas great = greatsword();
		great.dot(ACCENT, -6, 0, 0.2);
		write(items, "igneous_greatsword", great.render(molten, basalt, basalt, ember), preview);

		Canvas fangs = dagger();
		fangs.dot(ACCENT, -4, 0, 0.2);
		write(items, "dragon_fangs", fangs.render(scale, darker(scale), black, voidGem), preview);

		Canvas lance = glaive();
		lance.dot(ACCENT, 2, 0, 0.0);
		lance.dot(ACCENT, 9, -1, 0.0);
		write(items, "astral_lance", lance.render(astral, gold, darker(astral), star), preview);

		if (args.length > 2) {
			int columns = materials.size();
			int rows = (preview.size() + columns - 1) / columns;
			int scaleUp = 6;
			BufferedImage sheet = new BufferedImage(columns * 18 * scaleUp, rows * 18 * scaleUp, BufferedImage.TYPE_INT_ARGB);
			for (int i = 0; i < preview.size(); i++) {
				int ox = (i % columns) * 18 * scaleUp;
				int oy = (i / columns) * 18 * scaleUp;
				for (int y = 0; y < 18 * scaleUp; y++) {
					for (int x = 0; x < 18 * scaleUp; x++) {
						sheet.setRGB(ox + x, oy + y, ((x / scaleUp + y / scaleUp) % 2 == 0) ? 0xFF8B8B8B : 0xFF7A7A7A);
					}
				}
				BufferedImage image = preview.get(i);
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						int p = image.getRGB(x, y);
						if ((p >>> 24) == 0) {
							continue;
						}
						for (int dy = 0; dy < scaleUp; dy++) {
							for (int dx = 0; dx < scaleUp; dx++) {
								sheet.setRGB(ox + (x + 1) * scaleUp + dx, oy + (y + 1) * scaleUp + dy, p);
							}
						}
					}
				}
			}
			ImageIO.write(sheet, "png", new File(args[2]));
		}
		System.out.println("ok: " + preview.size() + " icones");
	}

	static void write(File dir, String name, BufferedImage image, List<BufferedImage> preview) throws IOException {
		ImageIO.write(image, "png", new File(dir, name + ".png"));
		preview.add(image);
	}

}
