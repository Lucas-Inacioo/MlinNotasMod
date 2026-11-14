import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * Texturas das construcoes de oficios (Vilas 2.0, fase 6): a tina de curtir recolore o barril vanilla em carvalho escuro
 * e pinta o banho de tanino (marrom turvo) na boca. Uso: java scripts/CraftsTexGen.java &lt;jar do cliente&gt; &lt;pasta assets/mlinnotas&gt;
 */
public class CraftsTexGen {
	public static void main(String[] args) throws Exception {
		File out = new File(args[1], "textures/block");
		out.mkdirs();
		try (ZipFile jar = new ZipFile(args[0])) {
			BufferedImage side = vanilla(jar, "barrel_side");
			tone(side, 0xFF2B1D10, 0xFF553920, 0xFF7A5230);
			ImageIO.write(side, "png", new File(out, "tanning_vat_side.png"));
			BufferedImage bottom = vanilla(jar, "barrel_bottom");
			tone(bottom, 0xFF2B1D10, 0xFF553920, 0xFF7A5230);
			ImageIO.write(bottom, "png", new File(out, "tanning_vat_bottom.png"));
			BufferedImage top = vanilla(jar, "barrel_top");
			tone(top, 0xFF2B1D10, 0xFF553920, 0xFF7A5230);
			// banho de tanino dentro do aro (2..13)
			for (int y = 2; y <= 13; y++) {
				for (int x = 2; x <= 13; x++) {
					int n = Math.floorMod(x * 31 + y * 17 + (x * y) % 5, 7);
					int base = n < 2 ? 0xFF5E4519 : n < 5 ? 0xFF6F5320 : 0xFF86652A;
					top.setRGB(x, y, base);
				}
			}
			ImageIO.write(top, "png", new File(out, "tanning_vat_top.png"));
		}
	}

	private static BufferedImage vanilla(ZipFile jar, String name) throws Exception {
		try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/block/" + name + ".png"))) {
			BufferedImage src = ImageIO.read(in);
			BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < src.getHeight(); y++) {
				for (int x = 0; x < src.getWidth(); x++) {
					copy.setRGB(x, y, src.getRGB(x, y));
				}
			}
			return copy;
		}
	}

	/** Troca cada pixel por um de tres tons conforme a luminosidade (mantem o desenho das tabuas e das cintas). */
	private static void tone(BufferedImage im, int dark, int mid, int light) {
		for (int y = 0; y < im.getHeight(); y++) {
			for (int x = 0; x < im.getWidth(); x++) {
				int argb = im.getRGB(x, y);
				int a = argb >>> 24;
				double lum = (0.299 * ((argb >> 16) & 255) + 0.587 * ((argb >> 8) & 255) + 0.114 * (argb & 255)) / 255.0;
				int c = lum < 0.38 ? dark : lum < 0.62 ? mid : light;
				im.setRGB(x, y, (a << 24) | (c & 0xFFFFFF));
			}
		}
	}
}
