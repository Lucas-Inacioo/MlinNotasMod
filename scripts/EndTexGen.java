import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * Texturas do conteudo do End (blocos, itens, criaturas e ovos), recolorindo texturas vanilla do jar do Minecraft:
 * o brilho de cada pixel vira um ponto do degrade escuro -> base -> claro da paleta. Mesmo esquema do TierTexGen.
 *
 * Uso (na raiz): java scripts/EndTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class EndTexGen {
	record Palette(int light, int base, int dark) {
	}

	static final Palette MOSS = new Palette(0xFF7FF0D0, 0xFF2FA58C, 0xFF123F3A);
	static final Palette VOID_GRASS = new Palette(0xFFB8FFF0, 0xFF4FD8C0, 0xFF1C5E58);
	static final Palette ASH = new Palette(0xFF8C8296, 0xFF5A5264, 0xFF2A2430);
	static final Palette CRYSTAL = new Palette(0xFFF2FBFF, 0xFF9EE3FF, 0xFF4C6FC8);
	static final Palette VOID_BRICK = new Palette(0xFF5E4A78, 0xFF33264A, 0xFF140E20);
	static final Palette LAMP = new Palette(0xFFFFF2FF, 0xFFF0A8F0, 0xFF9A4FA0);
	static final Palette PURPUR = new Palette(0xFFE6C2E6, 0xFFA97AA9, 0xFF5E3A63);
	static final Palette CORE = new Palette(0xFFFFD0FF, 0xFFC060D0, 0xFF4A1A5A);
	static final Palette JELLY = new Palette(0xFFC8FFF6, 0xFF6AD0E8, 0xFF2A3E8A);
	static final Palette MITE = new Palette(0xFFF4FCFF, 0xFF8FD8F0, 0xFF3A4E9A);

	static ZipFile jar;

	public static void main(String[] args) throws IOException {
		jar = new ZipFile(args[0]);
		File assets = new File(args[1]);
		File blocks = new File(assets, "textures/block");
		File items = new File(assets, "textures/item");
		File entities = new File(assets, "textures/entity");
		blocks.mkdirs();
		items.mkdirs();
		entities.mkdirs();

		// blocos
		BufferedImage mossTop = recolor(vanilla("block/moss_block"), MOSS);
		write(mossTop, blocks, "end_moss_top");
		write(overlay(vanilla("block/end_stone"), vanilla("block/grass_block_side_overlay"), mossTop), blocks, "end_moss_side");
		write(recolor(vanilla("block/sand"), ASH), blocks, "void_ash");
		write(recolor(vanilla("block/short_grass"), VOID_GRASS), blocks, "void_grass");
		write(recolor(vanilla("block/amethyst_block"), CRYSTAL), blocks, "ether_crystal_block");
		write(recolor(vanilla("block/amethyst_cluster"), CRYSTAL), blocks, "ether_crystal_cluster");
		write(recolor(vanilla("block/end_stone_bricks"), VOID_BRICK), blocks, "void_bricks");
		write(recolor(vanilla("block/redstone_lamp_on"), LAMP), blocks, "purpur_lamp");
		BufferedImage purpur = recolor(vanilla("block/purpur_block"), PURPUR);
		write(elevatorTop(purpur), blocks, "purpur_elevator_top");
		write(elevatorSide(purpur), blocks, "purpur_elevator_side");

		// itens
		write(recolor(vanilla("item/amethyst_shard"), CRYSTAL), items, "ether_crystal_shard");
		write(recolor(vanilla("item/heart_of_the_sea"), CORE), items, "sentinel_core");
		write(recolor(vanilla("item/slime_ball"), JELLY), items, "void_jelly");
		write(recolor(vanilla("item/iron_golem_spawn_egg"), PURPUR), items, "purpur_sentinel_spawn_egg");
		write(recolor(vanilla("item/glow_squid_spawn_egg"), JELLY), items, "void_jelly_spawn_egg");
		write(recolor(vanilla("item/endermite_spawn_egg"), MITE), items, "crystal_mite_spawn_egg");

		write(recolor(vanilla("item/rabbit_spawn_egg"), CHORUS), items, "chorus_hopper_spawn_egg");

		// decoracao dos biomas grandes
		write(recolor(vanilla("block/oak_log"), VOID_WOOD), blocks, "void_log");
		write(recolor(vanilla("block/oak_log_top"), VOID_WOOD), blocks, "void_log_top");
		write(recolor(vanilla("block/oak_planks"), VOID_PLANK), blocks, "void_planks");
		write(recolor(vanilla("block/oak_leaves"), VOID_LEAF), blocks, "void_leaves");
		write(recolor(vanilla("block/lily_of_the_valley"), LILY), blocks, "void_lily");
		write(recolor(vanilla("block/red_mushroom"), MUSHROOM), blocks, "end_mushroom");
		write(recolor(vanilla("block/basalt_side"), BASALT), blocks, "void_basalt");
		write(recolor(vanilla("block/basalt_top"), BASALT), blocks, "void_basalt_top");

		// criaturas: modelos proprios (client/render/EndCreatureModels); cada cubo pintado no seu lugar do layout UV
		write(sentinel(), entities, "purpur_sentinel");
		write(jelly(), entities, "void_jelly");
		write(mite(), entities, "crystal_mite");
		write(hopper(), entities, "chorus_hopper");
	}

	static final Palette VOID_WOOD = new Palette(0xFFB48CC8, 0xFF5E3872, 0xFF22122E);
	static final Palette VOID_PLANK = new Palette(0xFFCFAEDC, 0xFF8B62A0, 0xFF3E2650);
	static final Palette VOID_LEAF = new Palette(0xFFB0FFF2, 0xFF2FC8B4, 0xFF0C4446);
	static final Palette LILY = new Palette(0xFFF4FFFF, 0xFF86ECFF, 0xFF1C5470);
	static final Palette MUSHROOM = new Palette(0xFFFFDCFF, 0xFFD660D8, 0xFF4A1650);
	static final Palette BASALT = new Palette(0xFF746A88, 0xFF3A3448, 0xFF120E18);
	static final Palette CHORUS = new Palette(0xFFEED2F4, 0xFF9A6AA8, 0xFF3C2048);
	static final Palette SHELL = new Palette(0xFF6C82C8, 0xFF2C3878, 0xFF0C1030);

	/**
	 * Pinta um cubo do modelo no layout UV padrao (cubo w x h x d em (u, v)): duas faces w x d em cima, e na fileira
	 * de baixo as quatro laterais (d, w, d, w). Cada face recebe a textura {@code tile} repetida e uma borda mais escura.
	 */
	static void box(BufferedImage img, int u, int v, int w, int h, int d, BufferedImage tile, int alpha) {
		face(img, u + d, v, w, d, tile, alpha);
		face(img, u + d + w, v, w, d, tile, alpha);
		face(img, u, v + d, d, h, tile, alpha);
		face(img, u + d, v + d, w, h, tile, alpha);
		face(img, u + d + w, v + d, d, h, tile, alpha);
		face(img, u + d + w + d, v + d, w, h, tile, alpha);
	}

	static void face(BufferedImage img, int x0, int y0, int w, int h, BufferedImage tile, int alpha) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = tile.getRGB((x0 + x) % tile.getWidth(), (y0 + y) % tile.getHeight());
				boolean edge = w > 2 && h > 2 && (x == 0 || y == 0 || x == w - 1 || y == h - 1);
				if (edge) {
					c = lerp(c, 0xFF000000, 0.25);
				}
				img.setRGB(x0 + x, y0 + y, (alpha << 24) | (c & 0xFFFFFF));
			}
		}
	}

	static void rect(BufferedImage img, int x0, int y0, int w, int h, int argb) {
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				img.setRGB(x, y, argb);
			}
		}
	}

	/** Textura plana com um degrade da paleta de cima (claro) para baixo (escuro) e um pouco de ruido. */
	static BufferedImage gradientTile(Palette p, long seed, double top, double bottom) {
		java.util.Random random = new java.util.Random(seed);
		BufferedImage tile = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double t = top + (bottom - top) * y / 15.0 + (random.nextDouble() - 0.5) * 0.18;
				tile.setRGB(x, y, gradient(p, t));
			}
		}
		return tile;
	}

	/** Sentinela 64x64: cabeca (0,0) 8x8x8, casca (0,43) 9x4x9, tronco (0,16) 10x11x7, propulsor (0,34) 6x4x5 + ponta (22,34) 3x2x3, ombro (36,16) 4x4x4, antebraco (36,24) 3x9x3, punho (48,24) 4x3x4. */
	static BufferedImage sentinel() throws IOException {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		BufferedImage purpur = recolor(vanilla("block/purpur_block"), PURPUR);
		BufferedImage pillar = recolor(vanilla("block/purpur_pillar_side"), PURPUR);
		BufferedImage dark = recolor(vanilla("block/purpur_block"), VOID_BRICK);
		BufferedImage glow = gradientTile(LAMP, 7, 1.0, 0.6);
		box(img, 0, 0, 8, 8, 8, purpur, 255);
		box(img, 0, 43, 9, 4, 9, dark, 255);
		box(img, 0, 16, 10, 11, 7, pillar, 255);
		box(img, 0, 34, 6, 4, 5, dark, 255);
		box(img, 22, 34, 3, 2, 3, glow, 255);
		box(img, 36, 16, 4, 4, 4, dark, 255);
		box(img, 36, 24, 3, 9, 3, pillar, 255);
		box(img, 48, 24, 4, 3, 4, dark, 255);
		// olho de shulker na frente da cabeca (face (8,8) 8x8; a casca cobre as 3 fileiras de cima)
		rect(img, 9, 11, 6, 4, 0xFF1A0A20);
		rect(img, 10, 12, 4, 2, 0xFFF6B8FF);
		rect(img, 11, 12, 2, 2, 0xFF2A0030);
		img.setRGB(10, 12, 0xFFFFFFFF);
		// runa brilhante no peito (face (7,23) 10x11)
		rect(img, 11, 25, 2, 7, 0xFFFFC8FF);
		rect(img, 9, 27, 6, 1, 0xFFFFC8FF);
		rect(img, 11, 28, 2, 1, 0xFFFFFFFF);
		// juntas brilhantes nos punhos (frente do punho em (52,28) 4x3)
		rect(img, 53, 29, 2, 1, 0xFFFFC8FF);
		return img;
	}

	/** Agua-viva 64x64 translucida: topo (0,0) 8x3x8, sino (0,11) 12x5x12, nucleo (32,0) 4x3x4, tentaculo (48,0)/(52,0) 1x6x1, aba (0,28) 4x3x1. */
	static BufferedImage jelly() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		BufferedImage bell = gradientTile(JELLY, 1, 0.85, 0.35);
		java.util.Random random = new java.util.Random(5);
		for (int i = 0; i < 18; i++) {
			bell.setRGB(random.nextInt(16), random.nextInt(16), 0xFFE8FFFF);
		}
		box(img, 0, 0, 8, 3, 8, bell, 200);
		box(img, 0, 11, 12, 5, 12, bell, 150);
		box(img, 32, 0, 4, 3, 4, gradientTile(new Palette(0xFFFFFFFF, 0xFFC8FFF8, 0xFF8AD8FF), 2, 1.0, 0.6), 245);
		box(img, 48, 0, 1, 6, 1, gradientTile(JELLY, 3, 0.9, 0.5), 210);
		box(img, 52, 0, 1, 6, 1, gradientTile(new Palette(0xFFE0C8FF, 0xFF8A6AE8, 0xFF3A2A8A), 4, 0.8, 0.3), 190);
		box(img, 0, 28, 4, 3, 1, gradientTile(new Palette(0xFFD8C8FF, 0xFF7A5AD8, 0xFF2A1E70), 6, 0.9, 0.4), 170);
		return img;
	}

	/** Acaro 64x32: corpo (0,0) 7x3x8, cabeca (0,11) 5x2x3, pinca (16,11) 2x2x3, perna (30,0) 5x1x1, espinho pequeno (42,0) 1x4x1 e grande (46,0) 2x5x2. */
	static BufferedImage mite() throws IOException {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage shell = recolor(vanilla("block/blackstone"), SHELL);
		BufferedImage crystal = recolor(vanilla("block/amethyst_block"), CRYSTAL);
		box(img, 0, 0, 7, 3, 8, shell, 255);
		box(img, 0, 11, 5, 2, 3, shell, 255);
		box(img, 16, 11, 2, 2, 3, recolor(vanilla("block/blackstone"), MITE), 255);
		box(img, 30, 0, 5, 1, 1, gradientTile(SHELL, 9, 0.5, 0.2), 255);
		box(img, 42, 0, 1, 4, 1, crystal, 255);
		box(img, 46, 0, 2, 5, 2, crystal, 255);
		// olhos ciano na frente da cabeca (face (3,14) 5x2)
		img.setRGB(3, 14, 0xFF9EF6FF);
		img.setRGB(7, 14, 0xFF9EF6FF);
		// veios de cristal no casco (topo (8,0) 7x8)
		for (int i = 0; i < 4; i++) {
			img.setRGB(9 + i, 2 + i, 0xFF9EE3FF);
		}
		return img;
	}

	/** Saltador 64x32: corpo (0,0) 7x7x7, petalas (28,0) 4x3x4, botao (44,0) 2x2x2, pe (0,14) 3x2x5, antena (16,14) 1x4x1. */
	static BufferedImage hopper() throws IOException {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage body = vanilla("block/chorus_plant");
		BufferedImage flower = vanilla("block/chorus_flower");
		box(img, 0, 0, 7, 7, 7, body, 255);
		box(img, 28, 0, 4, 3, 4, flower, 255);
		box(img, 44, 0, 2, 2, 2, recolor(vanilla("block/chorus_flower"), LAMP), 255);
		box(img, 0, 14, 3, 2, 5, recolor(vanilla("block/chorus_plant"), CHORUS), 255);
		box(img, 16, 14, 1, 4, 1, flower, 255);
		// olhos e boquinha na frente do corpo (face (7,7) 7x7)
		rect(img, 8, 9, 1, 2, 0xFF140818);
		rect(img, 12, 9, 1, 2, 0xFF140818);
		img.setRGB(8, 9, 0xFFFFFFFF);
		img.setRGB(12, 9, 0xFFFFFFFF);
		rect(img, 10, 12, 1, 1, 0xFF3A1A40);
		return img;
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
		return (0.299 * ((argb >> 16) & 0xFF) + 0.587 * ((argb >> 8) & 0xFF) + 0.114 * (argb & 0xFF)) / 255.0;
	}

	/** Recolore todos os pixels visiveis mantendo o sombreado (brilho normalizado entre o min e o max da imagem). */
	static BufferedImage recolor(BufferedImage src, Palette p) {
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
				out.setRGB(x, y, (c & 0xFF000000) | (gradient(p, t) & 0x00FFFFFF));
			}
		}
		return out;
	}

	/** Lateral com musgo: pedra do End e, onde a mascara da grama vanilla e opaca, o pixel do topo de musgo. */
	static BufferedImage overlay(BufferedImage ground, BufferedImage mask, BufferedImage top) {
		BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				out.setRGB(x, y, (mask.getRGB(x, y) >>> 24) > 0 ? top.getRGB(x, y) : ground.getRGB(x, y));
			}
		}
		return out;
	}

	/** Topo do elevador: purpur com moldura escura e uma seta para cima clara. */
	static BufferedImage elevatorTop(BufferedImage purpur) {
		BufferedImage out = copy(purpur);
		int dark = 0xFF3A1F40;
		int light = 0xFFFFE6FF;
		for (int i = 0; i < 16; i++) {
			out.setRGB(i, 0, dark);
			out.setRGB(i, 15, dark);
			out.setRGB(0, i, dark);
			out.setRGB(15, i, dark);
		}
		// seta: haste 2x6 e ponta triangular
		for (int y = 7; y <= 12; y++) {
			out.setRGB(7, y, light);
			out.setRGB(8, y, light);
		}
		for (int row = 0; row < 4; row++) {
			for (int x = 7 - row; x <= 8 + row; x++) {
				out.setRGB(x, 3 + row, light);
			}
		}
		return out;
	}

	/** Lateral do elevador: purpur com uma faixa luminosa no meio. */
	static BufferedImage elevatorSide(BufferedImage purpur) {
		BufferedImage out = copy(purpur);
		for (int x = 0; x < 16; x++) {
			out.setRGB(x, 7, 0xFFFFC8FF);
			out.setRGB(x, 8, 0xFFE08AE8);
			out.setRGB(x, 0, 0xFF3A1F40);
			out.setRGB(x, 15, 0xFF3A1F40);
		}
		return out;
	}

	static BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		out.getGraphics().drawImage(src, 0, 0, null);
		return out;
	}

	/** Degrade escuro (0) -> base (0.55) -> claro (1). */
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
