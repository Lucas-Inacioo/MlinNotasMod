import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.zip.ZipFile;

/**
 * Texturas dos chefes: modelos proprios (layout UV de client/render/BossModels), altares, itens que eles soltam e ovos.
 * Usa as funcoes do EndTexGen/NetherTexGen (o launcher do Java compila os arquivos da pasta juntos).
 *
 * Uso (na raiz): java scripts/BossTexGen.java ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar src/main/resources/assets/mlinnotas
 */
public class BossTexGen {
	static final EndTexGen.Palette STONE = new EndTexGen.Palette(0xFF9AA4B4, 0xFF59636F, 0xFF1E232B);
	static final EndTexGen.Palette DARK_STONE = new EndTexGen.Palette(0xFF6F7886, 0xFF3C434E, 0xFF14171C);
	static final EndTexGen.Palette COBALT = new EndTexGen.Palette(0xFFC8E4FF, 0xFF3D7CE8, 0xFF0C1E66);
	static final EndTexGen.Palette SCULK = new EndTexGen.Palette(0xFF2F6670, 0xFF102C36, 0xFF04090C);
	static final EndTexGen.Palette BONE = new EndTexGen.Palette(0xFFEDE8D4, 0xFFA9A48C, 0xFF46443A);
	static final EndTexGen.Palette SOUL = new EndTexGen.Palette(0xFFEAFFFF, 0xFF4FE6F2, 0xFF0A5E70);
	static final EndTexGen.Palette BASALT = new EndTexGen.Palette(0xFF706A66, 0xFF38322F, 0xFF120F0E);
	static final EndTexGen.Palette HORN = new EndTexGen.Palette(0xFF8C8478, 0xFF4A443C, 0xFF1A1714);
	static final EndTexGen.Palette ROBE = new EndTexGen.Palette(0xFF7A6EE0, 0xFF2E2472, 0xFF0C0828);
	static final EndTexGen.Palette GOLD = new EndTexGen.Palette(0xFFFFF4B0, 0xFFE2B240, 0xFF6C4A10);
	static final EndTexGen.Palette STAR = new EndTexGen.Palette(0xFFFFFFFF, 0xFFB896FF, 0xFF3A1E8A);
	static final EndTexGen.Palette DRAGON = new EndTexGen.Palette(0xFFC48AE0, 0xFF4A1E5E, 0xFF0E0414);
	static final EndTexGen.Palette PURPUR_GOLD = new EndTexGen.Palette(0xFFFFE6A0, 0xFFA97AA9, 0xFF2E1A36);

	public static void main(String[] args) throws IOException {
		EndTexGen.jar = new ZipFile(args[0]);
		File assets = new File(args[1]);
		File blocks = new File(assets, "textures/block");
		File items = new File(assets, "textures/item");
		File entities = new File(assets, "textures/entity");

		// altares
		EndTexGen.write(EndTexGen.recolor(vanilla("block/lodestone_top"), COBALT), blocks, "cobalt_altar_top");
		EndTexGen.write(EndTexGen.recolor(vanilla("block/lodestone_side"), STONE), blocks, "cobalt_altar_side");
		EndTexGen.write(EndTexGen.recolor(vanilla("block/sculk_catalyst_top"), SOUL), blocks, "abyssal_altar_top");
		EndTexGen.write(EndTexGen.recolor(vanilla("block/sculk_catalyst_side"), SCULK), blocks, "abyssal_altar_side");
		EndTexGen.write(EndTexGen.recolor(vanilla("block/end_portal_frame_top"), PURPUR_GOLD), blocks, "astral_altar_top");
		EndTexGen.write(EndTexGen.recolor(vanilla("block/end_portal_frame_side"), PURPUR_GOLD), blocks, "astral_altar_side");

		// itens
		EndTexGen.write(EndTexGen.recolor(vanilla("item/heart_of_the_sea"), COBALT), items, "cobalt_heart");
		EndTexGen.write(EndTexGen.recolor(vanilla("item/ender_eye"), SOUL), items, "abyssal_core");
		EndTexGen.write(EndTexGen.recolor(vanilla("item/fire_charge"), NetherTexGen.MAGMA), items, "colossus_ember");
		EndTexGen.write(EndTexGen.recolor(vanilla("item/nether_star"), STAR), items, "astral_fragment");
		EndTexGen.write(EndTexGen.recolor(vanilla("item/turtle_scute"), DRAGON), items, "dragon_scale");
		EndTexGen.write(EndTexGen.recolor(vanilla("item/iron_golem_spawn_egg"), COBALT), items, "cobalt_golem_spawn_egg");
		EndTexGen.write(EndTexGen.recolor(vanilla("item/warden_spawn_egg"), SOUL), items, "abyssal_guardian_spawn_egg");
		EndTexGen.write(EndTexGen.recolor(vanilla("item/blaze_spawn_egg"), NetherTexGen.MAGMA), items, "igneous_colossus_spawn_egg");
		EndTexGen.write(EndTexGen.recolor(vanilla("item/evoker_spawn_egg"), ROBE), items, "astral_herald_spawn_egg");

		// chefes
		EndTexGen.write(golem(), entities, "cobalt_golem");
		EndTexGen.write(guardian(), entities, "abyssal_guardian");
		EndTexGen.write(colossus(), entities, "igneous_colossus");
		EndTexGen.write(herald(), entities, "astral_herald");
	}

	static BufferedImage vanilla(String path) throws IOException {
		return EndTexGen.vanilla(path);
	}

	/** Frente de um cubo w x h x d em (u, v): (u + d, v + d). */
	static int fx(int u, int d) {
		return u + d;
	}

	/** Veios finos (caminhadas aleatorias) dentro de um retangulo. */
	static void veins(BufferedImage img, int x0, int y0, int w, int h, int count, long seed, int color, int glow) {
		Random random = new Random(seed);
		for (int i = 0; i < count; i++) {
			int x = x0 + random.nextInt(w);
			int y = y0 + random.nextInt(h);
			int len = 4 + random.nextInt(8);
			for (int s = 0; s < len; s++) {
				if (x >= x0 && x < x0 + w && y >= y0 && y < y0 + h) {
					img.setRGB(x, y, s % 3 == 1 ? glow : color);
				}
				if (random.nextBoolean()) {
					x += random.nextBoolean() ? 1 : -1;
				} else {
					y += random.nextBoolean() ? 1 : -1;
				}
			}
		}
	}

	/** Golem de Cobalto 128x128 (layout em BossModels.GolemModel). */
	static BufferedImage golem() throws IOException {
		BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		BufferedImage stone = EndTexGen.recolor(vanilla("block/cobbled_deepslate"), STONE);
		BufferedImage dark = EndTexGen.recolor(vanilla("block/deepslate_tiles"), DARK_STONE);
		BufferedImage crystal = EndTexGen.recolor(vanilla("block/amethyst_block"), COBALT);
		BufferedImage moss = EndTexGen.recolor(vanilla("block/moss_block"), new EndTexGen.Palette(0xFF8FB070, 0xFF4E6E38, 0xFF1C2A12));
		int vein = 0xFF2F6CE0;
		int veinGlow = 0xFF9CD0FF;
		EndTexGen.box(img, 0, 0, 26, 18, 16, stone, 255);
		veins(img, 0, 16, 84, 18, 26, 1L, vein, veinGlow);
		// musgo no alto das costas e dos ombros (face de cima do tronco em (16,0) 26x16)
		for (int y = 0; y < 16; y++) {
			for (int x = 16; x < 42; x++) {
				if (((x * 7 + y * 13) % 11) < 4) {
					img.setRGB(x, y, moss.getRGB(x % 16, y % 16));
				}
			}
		}
		EndTexGen.box(img, 84, 0, 10, 9, 10, dark, 255);
		// fenda do olho (frente da cabeca em (94,10) 10x9)
		EndTexGen.rect(img, 95, 13, 8, 3, 0xFF0A0E16);
		EndTexGen.rect(img, 96, 14, 6, 1, 0xFF6FC4FF);
		EndTexGen.rect(img, 98, 14, 2, 1, 0xFFE8F6FF);
		EndTexGen.box(img, 0, 61, 12, 5, 12, dark, 255);
		veins(img, 0, 61, 48, 17, 8, 2L, vein, veinGlow);
		EndTexGen.box(img, 84, 19, 3, 8, 3, crystal, 255);
		EndTexGen.box(img, 96, 19, 4, 11, 4, crystal, 255);
		EndTexGen.box(img, 0, 34, 9, 18, 9, stone, 255);
		veins(img, 0, 43, 36, 18, 8, 3L, vein, veinGlow);
		EndTexGen.box(img, 36, 34, 11, 10, 11, dark, 255);
		// juntas de cobalto na frente do punho (47,45) 11x10
		for (int k = 0; k < 3; k++) {
			EndTexGen.rect(img, 48 + k * 3, 46, 2, 2, veinGlow);
		}
		EndTexGen.box(img, 80, 34, 8, 12, 8, stone, 255);
		veins(img, 80, 42, 32, 12, 5, 4L, vein, veinGlow);
		return img;
	}

	/** Guardiao das Profundezas 128x128 (layout em BossModels.GuardianModel). */
	static BufferedImage guardian() throws IOException {
		BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		BufferedImage sculk = EndTexGen.recolor(NetherTexGen.frame(vanilla("block/sculk")), SCULK);
		BufferedImage bone = EndTexGen.recolor(vanilla("block/bone_block_side"), BONE);
		BufferedImage glow = EndTexGen.gradientTile(SOUL, 3L, 1.0, 0.45);
		EndTexGen.box(img, 0, 0, 16, 12, 10, sculk, 255);
		// costelas de osso na frente (10,10) 16x12 e nas costas (36,10)
		for (int side : new int[] {10, 36}) {
			for (int row = 1; row < 12; row += 3) {
				for (int x = 0; x < 16; x++) {
					if (Math.abs(x - 7.5) > 1.2) {
						img.setRGB(side + x, 10 + row, bone.getRGB(x, row));
					}
				}
			}
			EndTexGen.rect(img, side + 7, 10, 2, 12, BONE.base());
		}
		EndTexGen.box(img, 52, 0, 6, 6, 2, glow, 255);
		EndTexGen.box(img, 72, 0, 6, 4, 6, bone, 255);
		EndTexGen.box(img, 0, 22, 8, 10, 8, sculk, 255);
		// mascara de osso sem rosto com duas orbitas vazias (frente da cabeca (8,30) 8x10)
		EndTexGen.face(img, 8, 30, 8, 7, bone, 255);
		EndTexGen.rect(img, 9, 33, 2, 2, 0xFF02181C);
		EndTexGen.rect(img, 13, 33, 2, 2, 0xFF02181C);
		img.setRGB(9, 34, 0xFF4FE6F2);
		img.setRGB(14, 34, 0xFF4FE6F2);
		EndTexGen.box(img, 32, 22, 2, 10, 2, bone, 255);
		EndTexGen.box(img, 40, 22, 2, 5, 2, bone, 255);
		EndTexGen.box(img, 48, 22, 2, 14, 2, EndTexGen.gradientTile(SOUL, 5L, 0.7, 0.2), 255);
		EndTexGen.box(img, 0, 64, 5, 20, 5, sculk, 255);
		EndTexGen.rect(img, 6, 75, 3, 12, BONE.base()); // canela de osso
		EndTexGen.box(img, 20, 64, 12, 4, 7, bone, 255);
		EndTexGen.box(img, 58, 64, 6, 8, 5, bone, 255);
		EndTexGen.box(img, 0, 90, 4, 24, 4, sculk, 255);
		veins(img, 0, 94, 16, 24, 6, 6L, 0xFF1E8C9A, 0xFF7FF4FF);
		EndTexGen.box(img, 16, 90, 5, 6, 5, bone, 255);
		veins(img, 0, 10, 52, 12, 10, 7L, 0xFF1E8C9A, 0xFF7FF4FF);
		return img;
	}

	/** Colosso Igneo 128x128 (layout em BossModels.ColossusModel). */
	static BufferedImage colossus() throws IOException {
		BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		BufferedImage basalt = EndTexGen.recolor(vanilla("block/basalt_side"), BASALT);
		BufferedImage basaltTop = EndTexGen.recolor(vanilla("block/basalt_top"), BASALT);
		BufferedImage crust = NetherTexGen.cracked(NetherTexGen.frame(vanilla("block/magma")));
		BufferedImage blackstone = EndTexGen.recolor(vanilla("block/blackstone"), BASALT);
		BufferedImage horn = EndTexGen.recolor(vanilla("block/bone_block_side"), HORN);
		EndTexGen.box(img, 0, 0, 32, 20, 18, blackstone, 255);
		veins(img, 0, 18, 100, 20, 40, 11L, 0xFFE0520E, 0xFFFFD060);
		// nucleo de magma no peito (frente (18,18) 32x20)
		for (int y = 0; y < 10; y++) {
			for (int x = 0; x < 12; x++) {
				double d = Math.hypot(x - 5.5, y - 4.5);
				if (d < 5.2) {
					img.setRGB(28 + x, 22 + y, crust.getRGB(x, y));
				}
			}
		}
		EndTexGen.box(img, 0, 38, 12, 12, 12, blackstone, 255);
		// olhos e boca de brasa (frente da cabeca (12,50) 12x12)
		EndTexGen.rect(img, 14, 54, 3, 2, 0xFFFFC040);
		EndTexGen.rect(img, 19, 54, 3, 2, 0xFFFFC040);
		img.setRGB(15, 54, 0xFFFFF4C0);
		img.setRGB(20, 54, 0xFFFFF4C0);
		EndTexGen.rect(img, 15, 58, 6, 1, 0xFFE0520E);
		EndTexGen.box(img, 100, 0, 3, 8, 3, horn, 255);
		EndTexGen.box(img, 112, 0, 4, 14, 4, basalt, 255);
		EndTexGen.face(img, 116, 0, 4, 4, basaltTop, 255);
		EndTexGen.box(img, 0, 64, 11, 22, 11, basalt, 255);
		veins(img, 0, 75, 44, 22, 8, 12L, 0xFFE0520E, 0xFFFFD060);
		EndTexGen.box(img, 44, 64, 22, 6, 14, crust, 255);
		EndTexGen.box(img, 48, 38, 10, 16, 10, basalt, 255);
		EndTexGen.box(img, 0, 98, 12, 16, 12, crust, 255);
		return img;
	}

	/** Arauto Astral 128x64 (layout em BossModels.HeraldModel). */
	static BufferedImage herald() throws IOException {
		BufferedImage img = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
		BufferedImage robe = EndTexGen.recolor(vanilla("block/white_wool"), ROBE);
		BufferedImage gold = EndTexGen.gradientTile(GOLD, 9L, 1.0, 0.4);
		BufferedImage star = EndTexGen.gradientTile(STAR, 10L, 1.0, 0.6);
		EndTexGen.box(img, 0, 0, 9, 9, 9, robe, 255);
		// rosto: vazio escuro com uma estrela no meio e pontinhos (frente do capuz (9,9) 9x9)
		EndTexGen.rect(img, 10, 10, 7, 8, 0xFF04020C);
		int[][] dots = {{11, 11}, {15, 12}, {12, 16}, {16, 16}};
		for (int[] d : dots) {
			img.setRGB(d[0], d[1], 0xFF9A8CFF);
		}
		EndTexGen.rect(img, 13, 12, 1, 5, 0xFFFFFFFF);
		EndTexGen.rect(img, 11, 14, 5, 1, 0xFFFFFFFF);
		img.setRGB(13, 14, 0xFFFFF4B0);
		// borda dourada do capuz na frente
		for (int i = 9; i < 18; i++) {
			img.setRGB(i, 9, GOLD.base());
			img.setRGB(9, i, GOLD.base());
			img.setRGB(17, i, GOLD.base());
		}
		EndTexGen.box(img, 0, 18, 10, 10, 6, robe, 255);
		EndTexGen.rect(img, 10, 24, 1, 10, GOLD.light()); // faixa no peito
		EndTexGen.rect(img, 0, 33, 32, 1, GOLD.base()); // cinto
		EndTexGen.box(img, 0, 34, 12, 8, 8, robe, 255);
		EndTexGen.box(img, 40, 34, 14, 6, 10, robe, 255);
		EndTexGen.rect(img, 40, 48, 48, 2, GOLD.base()); // barra dourada
		// runas douradas na saia (frente (8,42) 12x8)
		for (int x = 9; x < 19; x += 3) {
			EndTexGen.rect(img, x, 44, 1, 3, GOLD.light());
		}
		EndTexGen.box(img, 36, 0, 4, 10, 4, robe, 255);
		EndTexGen.rect(img, 36, 12, 16, 2, GOLD.base()); // punho da manga
		EndTexGen.box(img, 52, 0, 3, 3, 3, star, 255);
		EndTexGen.box(img, 64, 0, 16, 1, 1, gold, 255);
		EndTexGen.box(img, 64, 4, 1, 14, 1, gold, 255);
		EndTexGen.box(img, 72, 4, 3, 3, 3, star, 255);
		return img;
	}
}
