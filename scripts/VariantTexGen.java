import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.zip.ZipFile;

/**
 * Texturas das variantes de bioma dos mobs (variants/ + client/variants/MobVariantTextures): recolore a textura
 * vanilla do mob base misturando o pixel original com o degrade da paleta (mantem olhos, costelas e sombreado) e,
 * em algumas, salpica manchas (musgo, neve, cristal). Saida: textures/entity/variant/<variante>/<arquivo vanilla>.png.
 *
 * Uso (na raiz): java scripts/VariantTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class VariantTexGen {
	record Palette(int light, int base, int dark) {
	}

	static final Palette MOSS = new Palette(0xFF9BCF55, 0xFF4E8A2A, 0xFF1F3A12);
	static final Palette SNOW = new Palette(0xFFFFFFFF, 0xFFF0F6FA, 0xFFC4D6E6);
	static final Palette BLAZE = new Palette(0xFFFFE27A, 0xFFF08A1E, 0xFF7A2A08);
	static final Palette AMETHYST = new Palette(0xFFF2C8FF, 0xFFA45ADC, 0xFF3E1C6E);
	static final Palette MANGROVE = new Palette(0xFFB48A5A, 0xFF6B4A2A, 0xFF2A1E10);
	static final Palette ASH = new Palette(0xFFE8E2DC, 0xFFB8B0A8, 0xFF6A625A);
	static final Palette MANGROVE_LEAF = new Palette(0xFFC88A60, 0xFF8A4430, 0xFF3A1810);

	static ZipFile jar;

	public static void main(String[] args) throws IOException {
		jar = new ZipFile(args[0]);
		File root = new File(args[1], "textures/entity/variant");

		gen(root, "mossy_zombie", "zombie/zombie", MOSS, 0.75, 0.12, 1);
		gen(root, "mossy_zombie", "zombie/zombie_baby", MOSS, 0.75, 0.12, 2);
		gen(root, "snow_spider", "spider/spider", SNOW, 0.9, 0.10, 3);
		gen(root, "blazing_skeleton", "skeleton/skeleton", BLAZE, 0.7, 0.0, 4);
		gen(root, "crystal_creeper", "creeper/creeper", AMETHYST, 0.85, 0.08, 5);
		gen(root, "mangrove_drowned", "zombie/drowned", MANGROVE, 0.7, 0.06, 6);
		gen(root, "mangrove_drowned", "zombie/drowned_baby", MANGROVE, 0.7, 0.06, 7);
		gen(root, "mangrove_drowned", "zombie/drowned_outer_layer", MANGROVE_LEAF, 0.85, 0.08, 9);
		gen(root, "mangrove_drowned", "zombie/drowned_outer_layer_baby", MANGROVE_LEAF, 0.85, 0.08, 10);
		gen(root, "ashen_wither_skeleton", "skeleton/wither_skeleton", ASH, 0.9, 0.05, 8);
	}

	/** Mistura {@code mix} da paleta sobre o original e salpica {@code speckle} dos pixels com tons claro/escuro. */
	static void gen(File root, String variant, String vanillaPath, Palette p, double mix, double speckle, long seed) throws IOException {
		BufferedImage src = vanilla("entity/" + vanillaPath);
		BufferedImage out = recolor(src, p, mix);
		Random random = new Random(seed);
		for (int y = 0; y < out.getHeight(); y++) {
			for (int x = 0; x < out.getWidth(); x++) {
				int c = out.getRGB(x, y);
				if ((c >>> 24) != 0 && random.nextDouble() < speckle) {
					int tone = random.nextBoolean() ? p.light() : p.base();
					out.setRGB(x, y, (c & 0xFF000000) | (tone & 0x00FFFFFF));
				}
			}
		}
		File dir = new File(root, variant);
		dir.mkdirs();
		String file = vanillaPath.substring(vanillaPath.lastIndexOf('/') + 1);
		ImageIO.write(out, "png", new File(dir, file + ".png"));
	}

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

	static BufferedImage recolor(BufferedImage src, Palette p, double mix) {
		double min = 1;
		double max = 0;
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int c = src.getRGB(x, y);
				if ((c >>> 24) != 0) {
					min = Math.min(min, luminance(c));
					max = Math.max(max, luminance(c));
				}
			}
		}
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int c = src.getRGB(x, y);
				if ((c >>> 24) == 0) {
					continue;
				}
				double t = max > min ? (luminance(c) - min) / (max - min) : 0.5;
				int mixed = lerp(c, gradient(p, t), mix);
				out.setRGB(x, y, (c & 0xFF000000) | (mixed & 0x00FFFFFF));
			}
		}
		return out;
	}

	static int gradient(Palette p, double t) {
		t = Math.clamp(t, 0.0, 1.0);
		return t < 0.55 ? lerp(p.dark(), p.base(), t / 0.55) : lerp(p.base(), p.light(), (t - 0.55) / 0.45);
	}

	static int lerp(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
