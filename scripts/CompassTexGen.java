import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Texturas das bussolas de bioma e de estrutura: recolore os 32 quadros da bussola vanilla do jar do cliente (a caixa
 * cinza vira verde-musgo ou azul-cobalto; a agulha vermelha/branca fica como esta) e pinta um pequeno emblema na
 * moldura de baixo (folha / tijolo).
 *
 * <p>Uso (na raiz): java scripts/CompassTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class CompassTexGen {
	public static void main(String[] args) throws IOException {
		try (ZipFile jar = new ZipFile(args[0])) {
			File out = new File(args[1], "textures/item");
			out.mkdirs();
			for (int frame = 0; frame < 32; frame++) {
				String name = String.format("compass_%02d.png", frame);
				ZipEntry entry = jar.getEntry("assets/minecraft/textures/item/" + name);
				BufferedImage base;
				try (InputStream in = jar.getInputStream(entry)) {
					base = ImageIO.read(in);
				}
				ImageIO.write(recolor(base, new double[] {0.62, 1.0, 0.48}, new double[] {0.30, 0.42, 0.20}, 0xE6D84A),
						"png", new File(out, String.format("biome_compass_%02d.png", frame)));
				ImageIO.write(recolor(base, new double[] {0.55, 0.72, 1.0}, new double[] {0.22, 0.26, 0.48}, 0xC0583E),
						"png", new File(out, String.format("structure_compass_%02d.png", frame)));
			}
		}
	}

	/** Caixa multiplicada por {@code tint}; o mostrador escuro (2f2f2f) puxado para {@code face}; emblema na base. */
	static BufferedImage recolor(BufferedImage base, double[] tint, double[] face, int emblem) {
		BufferedImage img = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < base.getHeight(); y++) {
			for (int x = 0; x < base.getWidth(); x++) {
				int argb = base.getRGB(x, y);
				int a = argb >>> 24;
				int r = argb >> 16 & 0xFF;
				int g = argb >> 8 & 0xFF;
				int b = argb & 0xFF;
				if (a == 0) {
					img.setRGB(x, y, 0);
					continue;
				}
				boolean grey = Math.abs(r - g) < 12 && Math.abs(g - b) < 12;
				if (!grey) { // agulha
					img.setRGB(x, y, argb);
					continue;
				}
				double lum = (r + g + b) / 3.0;
				double[] t = lum >= 0x2c && lum <= 0x32 ? face : tint;
				double boost = t == face ? 3.2 : 1.0;
				int nr = clamp(lum * t[0] * boost);
				int ng = clamp(lum * t[1] * boost);
				int nb = clamp(lum * t[2] * boost);
				img.setRGB(x, y, a << 24 | nr << 16 | ng << 8 | nb);
			}
		}
		// emblema: 2 pixels na moldura de baixo (y = 12), no meio
		img.setRGB(7, 12, 0xFF000000 | emblem);
		img.setRGB(8, 12, 0xFF000000 | darker(emblem));
		return img;
	}

	static int darker(int rgb) {
		return ((rgb >> 16 & 0xFF) * 3 / 4) << 16 | ((rgb >> 8 & 0xFF) * 3 / 4) << 8 | (rgb & 0xFF) * 3 / 4;
	}

	static int clamp(double v) {
		return (int) Math.max(0, Math.min(255, Math.round(v)));
	}
}
