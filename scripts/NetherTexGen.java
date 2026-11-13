import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * Texturas do conteudo do Nether (blocos dos biomas novos, Lesma de Magma e o ovo), recolorindo texturas vanilla com
 * as funcoes do EndTexGen (o launcher do Java compila os dois juntos).
 *
 * Uso (na raiz): java scripts/NetherTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class NetherTexGen {
	static final EndTexGen.Palette ASH = new EndTexGen.Palette(0xFFC4BCB4, 0xFF7E766F, 0xFF34302D);
	static final EndTexGen.Palette BURN_WOOD = new EndTexGen.Palette(0xFFFFB45A, 0xFFB4501E, 0xFF3E1608);
	static final EndTexGen.Palette BURN_PLANK = new EndTexGen.Palette(0xFFFFC27A, 0xFFC4682E, 0xFF5A2410);
	static final EndTexGen.Palette BURN_CAP = new EndTexGen.Palette(0xFFF0A040, 0xFFB8461A, 0xFF4A1206);
	static final EndTexGen.Palette EMBER = new EndTexGen.Palette(0xFFF2A450, 0xFFB24A16, 0xFF461006);
	static final EndTexGen.Palette CRUST = new EndTexGen.Palette(0xFF6A5650, 0xFF33261F, 0xFF120B08);
	static final EndTexGen.Palette MAGMA = new EndTexGen.Palette(0xFFFFF0A0, 0xFFFF8C1A, 0xFF901C06);

	public static void main(String[] args) throws IOException {
		EndTexGen.jar = new ZipFile(args[0]);
		File assets = new File(args[1]);
		File blocks = new File(assets, "textures/block");
		File items = new File(assets, "textures/item");
		File entities = new File(assets, "textures/entity");

		EndTexGen.write(frame(EndTexGen.recolor(vanilla("block/soul_soil"), ASH)), blocks, "nether_ash");
		EndTexGen.write(frame(EndTexGen.recolor(frame(vanilla("block/crimson_stem")), BURN_WOOD)), blocks, "burning_stem");
		EndTexGen.write(frame(EndTexGen.recolor(frame(vanilla("block/crimson_stem_top")), BURN_WOOD)), blocks, "burning_stem_top");
		EndTexGen.write(EndTexGen.recolor(vanilla("block/crimson_planks"), BURN_PLANK), blocks, "burning_planks");
		EndTexGen.write(EndTexGen.recolor(vanilla("block/nether_wart_block"), BURN_CAP), blocks, "burning_cap");
		BufferedImage nylium = EndTexGen.recolor(frame(vanilla("block/crimson_nylium")), EMBER);
		EndTexGen.write(nylium, blocks, "ember_nylium");
		EndTexGen.write(EndTexGen.overlay(vanilla("block/netherrack"), vanilla("block/grass_block_side_overlay"), nylium), blocks, "ember_nylium_side");
		EndTexGen.write(EndTexGen.recolor(vanilla("block/nether_sprouts"), EMBER), blocks, "ember_sprouts");

		EndTexGen.write(EndTexGen.recolor(vanilla("item/magma_cube_spawn_egg"), MAGMA), items, "magma_slug_spawn_egg");
		EndTexGen.write(slug(), entities, "magma_slug");
	}

	static BufferedImage vanilla(String path) throws IOException {
		return EndTexGen.vanilla(path);
	}

	/** Primeiro quadro 16x16 de texturas animadas (tiras verticais). */
	static BufferedImage frame(BufferedImage src) {
		if (src.getHeight() <= src.getWidth()) {
			return src;
		}
		return EndTexGen.copy(src.getSubimage(0, 0, src.getWidth(), src.getWidth()));
	}

	/**
	 * Lesma 64x32 (layout de NetherRenderers.MagmaSlugModel): cabeca (0,0) 6x5x5, meio (0,10) 6x4x5, cauda (0,19) 4x3x5,
	 * pedunculo (24,0) 1x4x1, olho (30,0) 2x2x2. Crosta escura com rachaduras de magma; barriga e olhos acesos.
	 */
	static BufferedImage slug() throws IOException {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage crust = cracked(frame(vanilla("block/magma")));
		EndTexGen.box(img, 0, 0, 6, 5, 5, crust, 255);
		EndTexGen.box(img, 0, 10, 6, 4, 5, crust, 255);
		EndTexGen.box(img, 0, 19, 4, 3, 5, crust, 255);
		EndTexGen.box(img, 24, 0, 1, 4, 1, EndTexGen.recolor(frame(vanilla("block/magma")), CRUST), 255);
		BufferedImage glow = EndTexGen.gradientTile(MAGMA, 5L, 1.0, 0.7);
		EndTexGen.box(img, 30, 0, 2, 2, 2, glow, 255);
		// barriga (face de baixo de cada gomo) acesa
		EndTexGen.face(img, 5 + 6, 0, 6, 5, EndTexGen.gradientTile(MAGMA, 7L, 0.8, 0.5), 255);
		EndTexGen.face(img, 5 + 6, 10, 6, 5, EndTexGen.gradientTile(MAGMA, 8L, 0.8, 0.5), 255);
		EndTexGen.face(img, 5 + 4, 19, 4, 5, EndTexGen.gradientTile(MAGMA, 9L, 0.8, 0.5), 255);
		// pupilas pretas na frente do olho (face frontal em (32,2) 2x2)
		img.setRGB(32, 2, 0xFF1A0A04);
		img.setRGB(33, 3, 0xFF1A0A04);
		// boca: risco escuro na frente da cabeca (face frontal em (5,5) 6x5)
		for (int x = 7; x <= 9; x++) {
			img.setRGB(x, 8, 0xFF2A0C04);
		}
		return img;
	}

	/** Crosta: pixels escuros do bloco de magma viram crosta de basalto, os claros viram magma brilhante. */
	static BufferedImage cracked(BufferedImage magma) {
		BufferedImage out = new BufferedImage(magma.getWidth(), magma.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < magma.getHeight(); y++) {
			for (int x = 0; x < magma.getWidth(); x++) {
				double l = EndTexGen.luminance(magma.getRGB(x, y));
				int c = l > 0.4 ? EndTexGen.gradient(MAGMA, 0.35 + (l - 0.4) * 1.2) : EndTexGen.gradient(CRUST, l * 1.8);
				out.setRGB(x, y, c);
			}
		}
		return out;
	}
}
