import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * Texturas do ritmo de progressao, recolorindo texturas vanilla do jar do cliente: os quatro moldes de ferraria
 * (a partir do molde de upgrade de netherite) e a Chave da Forja (a partir da chave de provacao).
 *
 * Uso (na raiz do projeto): java scripts/ProgressionTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class ProgressionTexGen {
	static ZipFile jar;

	/** id do molde, cor escura e clara (RGB) do gradiente. */
	static final Object[][] TEMPLATES = {
			{"infernal", 0x4A0E06, 0xFF8A2A},
			{"end", 0x2C1F3A, 0xE8E2A6},
			{"astral", 0x1A2558, 0x8FE6FF},
			{"celestial", 0x5A4310, 0xFFF4C0},
	};

	public static void main(String[] args) throws IOException {
		jar = new ZipFile(args[0]);
		File items = new File(args[1], "textures/item");
		items.mkdirs();
		BufferedImage template = vanilla("item/netherite_upgrade_smithing_template");
		for (Object[] t : TEMPLATES) {
			write(recolor(template, (int) t[1], (int) t[2]), items, t[0] + "_upgrade_smithing_template");
		}
		// chave da forja: chave de provacao em tons de brasa
		write(recolor(vanilla("item/trial_key"), 0x3A0A04, 0xFFB040), items, "forge_key");
		System.out.println("ok");
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

	static void write(BufferedImage image, File dir, String name) throws IOException {
		ImageIO.write(image, "png", new File(dir, name + ".png"));
	}

	static double luminance(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
	}

	/** Troca cada pixel opaco por um gradiente escuro..claro pela luminancia (mantem o desenho e o alfa). */
	static BufferedImage recolor(BufferedImage src, int dark, int light) {
		double min = 1;
		double max = 0;
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int p = src.getRGB(x, y);
				if ((p >>> 24) != 0) {
					min = Math.min(min, luminance(p));
					max = Math.max(max, luminance(p));
				}
			}
		}
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int p = src.getRGB(x, y);
				if ((p >>> 24) == 0) {
					continue;
				}
				double t = max > min ? (luminance(p) - min) / (max - min) : 0.5;
				out.setRGB(x, y, (p & 0xFF000000) | mix(dark, light, t));
			}
		}
		return out;
	}

	static int mix(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
