import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * Gera as texturas dos materiais novos (cobalto, infernita, endita, astralita, celestio) recolorindo as texturas
 * de ferro do jar do Minecraft: o brilho de cada pixel cinza vira um ponto no degrade escuro -> base -> claro do
 * material, entao o sombreado vanilla e mantido. Minerios usam a mascara "minerio - pedra" do minerio vanilla
 * pintada sobre a pedra do lugar (pedra, ardosia, netherrack, pedra do End).
 *
 * Uso (na raiz): java scripts/TierTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class TierTexGen {
	record Material(String id, int light, int base, int dark, boolean ore) {
	}

	static final Material[] MATERIALS = {
			new Material("cobalt", 0xFF7FA8FF, 0xFF2F5FD0, 0xFF13265C, true),
			new Material("infernite", 0xFFFFB060, 0xFFE0401A, 0xFF6A1406, true),
			new Material("endite", 0xFFE6D0FF, 0xFFA77BE8, 0xFF452870, true),
			new Material("astralite", 0xFFFFC0F0, 0xFFE45CC8, 0xFF5E1450, true),
			new Material("celestium", 0xFFFFFFFF, 0xFFFFE89A, 0xFFA8862E, false),
	};

	static ZipFile jar;

	public static void main(String[] args) throws IOException {
		jar = new ZipFile(args[0]);
		File assets = new File(args[1]);
		File items = new File(assets, "textures/item");
		File blocks = new File(assets, "textures/block");
		File humanoid = new File(assets, "textures/entity/equipment/humanoid");
		File leggings = new File(assets, "textures/entity/equipment/humanoid_leggings");
		for (File dir : new File[] {items, blocks, humanoid, leggings}) {
			dir.mkdirs();
		}
		String[] gear = {"sword", "shovel", "pickaxe", "axe", "hoe", "spear", "spear_in_hand", "helmet", "chestplate", "leggings", "boots"};
		for (Material m : MATERIALS) {
			for (String g : gear) {
				write(recolor(vanilla("item/iron_" + g), m, true), items, m.id + "_" + g);
			}
			write(recolor(vanilla("item/iron_ingot"), m, false), items, m.id + "_ingot");
			// blocos de armazenamento (base do sinalizador) e de minerio bruto
			write(recolor(vanilla("block/iron_block"), m, false), blocks, m.id + "_block");
			if (m.ore) {
				write(recolor(vanilla("item/raw_iron"), m, false), items, "raw_" + m.id);
				write(recolor(vanilla("block/raw_iron_block"), m, false), blocks, "raw_" + m.id + "_block");
			}
			write(recolor(vanilla("entity/equipment/humanoid/iron"), m, false), humanoid, m.id);
			write(recolor(vanilla("entity/equipment/humanoid_leggings/iron"), m, false), leggings, m.id);
		}
		Material cobalt = MATERIALS[0];
		write(ore(vanilla("block/iron_ore"), vanilla("block/stone"), vanilla("block/stone"), cobalt), blocks, "cobalt_ore");
		write(ore(vanilla("block/deepslate_iron_ore"), vanilla("block/deepslate"), vanilla("block/deepslate"), cobalt), blocks, "deepslate_cobalt_ore");
		write(ore(vanilla("block/nether_gold_ore"), vanilla("block/netherrack"), vanilla("block/netherrack"), MATERIALS[1]), blocks, "infernite_ore");
		write(ore(vanilla("block/iron_ore"), vanilla("block/stone"), vanilla("block/end_stone"), MATERIALS[2]), blocks, "endite_ore");
		write(ore(vanilla("block/diamond_ore"), vanilla("block/stone"), vanilla("block/end_stone"), MATERIALS[3]), blocks, "astralite_ore");
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

	static double saturation(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		return max == 0 ? 0 : (max - min) / (double) max;
	}

	/** Pixels de metal: no modo "so cinza" ignora o cabo de madeira (marrom, saturado). */
	static boolean isMetal(int argb, boolean grayOnly) {
		return (argb >>> 24) != 0 && (!grayOnly || saturation(argb) < 0.2);
	}

	static BufferedImage recolor(BufferedImage src, Material m, boolean grayOnly) {
		double min = 1;
		double max = 0;
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int p = src.getRGB(x, y);
				if (isMetal(p, grayOnly)) {
					min = Math.min(min, luminance(p));
					max = Math.max(max, luminance(p));
				}
			}
		}
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int p = src.getRGB(x, y);
				if (isMetal(p, grayOnly)) {
					double t = max > min ? (luminance(p) - min) / (max - min) : 0.5;
					out.setRGB(x, y, (p & 0xFF000000) | (gradient(m, t) & 0x00FFFFFF));
				} else {
					out.setRGB(x, y, p);
				}
			}
		}
		return out;
	}

	/** Pinta sobre {@code ground} os pixels em que {@code ore} difere de {@code oreGround}. */
	static BufferedImage ore(BufferedImage ore, BufferedImage oreGround, BufferedImage ground, Material m) {
		int w = ore.getWidth();
		int h = ore.getHeight();
		boolean[][] mask = new boolean[w][h];
		double min = 1;
		double max = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int a = ore.getRGB(x, y);
				int b = oreGround.getRGB(x, y);
				int diff = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
						+ Math.abs((a & 0xFF) - (b & 0xFF));
				if (diff > 24) {
					mask[x][y] = true;
					min = Math.min(min, luminance(a));
					max = Math.max(max, luminance(a));
				}
			}
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (mask[x][y]) {
					double t = max > min ? (luminance(ore.getRGB(x, y)) - min) / (max - min) : 0.5;
					out.setRGB(x, y, gradient(m, t));
				} else {
					out.setRGB(x, y, ground.getRGB(x, y));
				}
			}
		}
		return out;
	}

	/** Degrade escuro (0) -> base (0.55) -> claro (1). */
	static int gradient(Material m, double t) {
		t = Math.clamp(t, 0.0, 1.0);
		return t < 0.55 ? lerp(m.dark, m.base, t / 0.55) : lerp(m.base, m.light, (t - 0.55) / 0.45);
	}

	static int lerp(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
