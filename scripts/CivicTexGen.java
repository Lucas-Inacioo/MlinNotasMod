import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Texturas das construcoes civicas (Vilas 2.0, fase 7), procedurais sobre texturas do jar vanilla: caixa de correio
 * (tabuas vermelhas com fenda e envelope), pedestal do Museu (quartzo com placa dourada e tampo com cavidade) e quadro de
 * expedicoes da Guilda (tabuas de abeto com pergaminhos presos), mais a pele do guarda da vila (zumbi vanilla recolorido:
 * pele humana e tunica azul).
 * Uso: java scripts/CivicTexGen.java <jar do cliente> src/main/resources/assets/mlinnotas
 */
public class CivicTexGen {
	public static void main(String[] args) throws IOException {
		File out = new File(args[1], "textures");
		File blocks = new File(out, "block");
		File entity = new File(out, "entity");
		blocks.mkdirs();
		entity.mkdirs();
		try (ZipFile jar = new ZipFile(args[0])) {
			BufferedImage red = tint(load(jar, "block/spruce_planks"), 0.95F, 0.38F, 0.32F, 1.25F);
			BufferedImage redDark = tint(load(jar, "block/dark_oak_planks"), 1.35F, 0.45F, 0.40F, 1.3F);
			BufferedImage spruce = load(jar, "block/spruce_planks");
			BufferedImage spruceLog = load(jar, "block/spruce_log");
			BufferedImage quartz = load(jar, "block/quartz_block_side");
			BufferedImage quartzTop = load(jar, "block/quartz_block_top");

			// caixa de correio
			BufferedImage front = copy(red);
			rect(front, 3, 4, 10, 3, 0xFF1D0A0A);
			rect(front, 3, 4, 10, 1, 0xFF3A1414);
			rect(front, 4, 9, 8, 5, 0xFFF1E8D2);
			rect(front, 4, 9, 8, 1, 0xFFD6C9A8);
			for (int i = 0; i < 4; i++) {
				set(front, 4 + i, 9 + i, 0xFFB09E78);
				set(front, 11 - i, 9 + i, 0xFFB09E78);
			}
			set(front, 7, 11, 0xFFB22222);
			set(front, 8, 11, 0xFFB22222);
			rect(front, 0, 0, 16, 1, 0xFF5B1B1B);
			rect(front, 0, 15, 16, 1, 0xFF5B1B1B);
			save(front, blocks, "mailbox_front");
			BufferedImage side = copy(red);
			rect(side, 0, 7, 16, 2, 0xFFC9A227);
			rect(side, 0, 7, 16, 1, 0xFFE8C95A);
			rect(side, 0, 0, 16, 1, 0xFF5B1B1B);
			rect(side, 0, 15, 16, 1, 0xFF5B1B1B);
			save(side, blocks, "mailbox_side");
			BufferedImage top = copy(redDark);
			rect(top, 1, 1, 14, 14, 0xFF4E1A1A);
			rect(top, 2, 2, 12, 12, 0xFF6E2424);
			save(top, blocks, "mailbox_top");

			// pedestal do Museu
			BufferedImage pfront = copy(quartz);
			rect(pfront, 4, 5, 8, 5, 0xFF6B4E10);
			rect(pfront, 5, 6, 6, 3, 0xFFD9B13B);
			rect(pfront, 5, 6, 6, 1, 0xFFF3D77A);
			rect(pfront, 7, 11, 2, 2, 0xFF3FB8AF);
			rect(pfront, 0, 0, 16, 1, 0xFFC9C2B2);
			rect(pfront, 0, 15, 16, 1, 0xFFC9C2B2);
			save(pfront, blocks, "museum_pedestal_front");
			save(quartz, blocks, "museum_pedestal_side");
			BufferedImage ptop = copy(quartzTop);
			rect(ptop, 3, 3, 10, 10, 0xFF8A7B5A);
			rect(ptop, 4, 4, 8, 8, 0xFF2B2530);
			rect(ptop, 5, 5, 6, 6, 0xFF3A3340);
			save(ptop, blocks, "museum_pedestal_top");

			// quadro de expedicoes da Guilda
			BufferedImage gfront = copy(spruce);
			rect(gfront, 0, 0, 16, 1, 0xFF3B2A14);
			rect(gfront, 0, 15, 16, 1, 0xFF3B2A14);
			rect(gfront, 0, 0, 1, 16, 0xFF3B2A14);
			rect(gfront, 15, 0, 1, 16, 0xFF3B2A14);
			int[][] sheets = {{2, 2, 5, 6}, {9, 3, 5, 5}, {3, 9, 4, 5}, {8, 9, 6, 5}};
			int[] paper = {0xFFEBDDB4, 0xFFF3E9C6, 0xFFE1D09A, 0xFFEFE2BC};
			for (int i = 0; i < sheets.length; i++) {
				int[] s = sheets[i];
				rect(gfront, s[0], s[1], s[2], s[3], paper[i]);
				for (int l = 1; l < s[3] - 1; l += 2) {
					rect(gfront, s[0] + 1, s[1] + l, s[2] - 2, 1, 0xFF8A7A52);
				}
				set(gfront, s[0] + s[2] / 2, s[1], 0xFFB22222);
			}
			save(gfront, blocks, "guild_board_front");
			BufferedImage gside = copy(spruce);
			rect(gside, 0, 0, 16, 1, 0xFF3B2A14);
			rect(gside, 0, 15, 16, 1, 0xFF3B2A14);
			save(gside, blocks, "guild_board_side");
			save(spruceLog, blocks, "guild_board_top");

			// guarda da vila: zumbi vanilla com pele humana e tunica azul
			BufferedImage zombie = ImageIO.read(stream(jar, "entity/zombie/zombie"));
			BufferedImage guard = new BufferedImage(zombie.getWidth(), zombie.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < zombie.getHeight(); y++) {
				for (int x = 0; x < zombie.getWidth(); x++) {
					guard.setRGB(x, y, guardPixel(zombie.getRGB(x, y)));
				}
			}
			save(guard, entity, "village_guard");
		}
	}

	static int guardPixel(int argb) {
		int a = argb >>> 24;
		if (a == 0) {
			return argb;
		}
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		float l = Math.min(1.0F, (r * 0.3F + g * 0.59F + b * 0.11F) / 150.0F);
		if (g > r + 8 && g > b + 8) {
			// pele verde -> pele humana
			return pack(a, 215 * l + 20, 165 * l + 14, 125 * l + 10);
		}
		if (b > r + 25) {
			// tunica ciano/azul -> azul real da guarda
			return pack(a, r * 0.45F + 10, g * 0.55F + 12, Math.min(255, b * 1.05F + 30));
		}
		return argb;
	}

	static int pack(int a, float r, float g, float b) {
		return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}

	static BufferedImage tint(BufferedImage src, float r, float g, float b, float gain) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int p = src.getRGB(x, y);
				float l = ((((p >> 16) & 0xFF) * 0.3F + ((p >> 8) & 0xFF) * 0.59F + (p & 0xFF) * 0.11F) / 255.0F) * gain;
				out.setRGB(x, y, pack(p >>> 24, 255 * l * r, 255 * l * g, 255 * l * b));
			}
		}
		return out;
	}

	static InputStream stream(ZipFile jar, String name) throws IOException {
		ZipEntry entry = jar.getEntry("assets/minecraft/textures/" + name + ".png");
		if (entry == null) {
			throw new IOException("textura do jar nao achada: " + name);
		}
		return jar.getInputStream(entry);
	}

	static BufferedImage load(ZipFile jar, String name) throws IOException {
		return ImageIO.read(stream(jar, name));
	}

	static BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		out.getGraphics().drawImage(src, 0, 0, null);
		return out;
	}

	static void rect(BufferedImage im, int x, int y, int w, int h, int argb) {
		for (int i = x; i < x + w; i++) {
			for (int j = y; j < y + h; j++) {
				set(im, i, j, argb);
			}
		}
	}

	static void set(BufferedImage im, int x, int y, int argb) {
		if (x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight()) {
			im.setRGB(x, y, argb);
		}
	}

	static void save(BufferedImage im, File dir, String name) throws IOException {
		ImageIO.write(im, "png", new File(dir, name + ".png"));
	}
}
