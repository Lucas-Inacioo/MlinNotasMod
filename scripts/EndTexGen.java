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

		// criaturas (mesmo layout UV dos modelos vanilla reaproveitados)
		write(recolor(vanilla("entity/iron_golem/iron_golem"), PURPUR), entities, "purpur_sentinel");
		write(recolor(vanilla("entity/squid/glow_squid"), JELLY), entities, "void_jelly");
		write(recolor(vanilla("entity/endermite/endermite"), MITE), entities, "crystal_mite");
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
