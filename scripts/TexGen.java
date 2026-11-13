import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Gera texturas 16x16 procedurais para o mod. Uso: java TexGen.java <pasta-assets-mlinnotas>
 */
public class TexGen {
	static final int T = 0x00000000;

	public static void main(String[] args) throws IOException {
		File assets = new File(args[0]);
		File items = new File(assets, "textures/item");
		File blocks = new File(assets, "textures/block");
		items.mkdirs();
		blocks.mkdirs();

		// Varinhas
		wand(items, "wooden_wand", 0xFFC8A060, 0xFF8B5A2B);
		wand(items, "stone_wand", 0xFFA0A0A0, 0xFF606060);
		wand(items, "iron_wand", 0xFFE8E8E8, 0xFFA0A0A0);
		wand(items, "diamond_wand", 0xFF7FE9E6, 0xFF2BAFA8);

		tablet(items, "crafting_tablet");

		furnaceUpgrade(items, "furnace_upgrade_iron", 0xFFE8E8E8, 0xFFA0A0A0);
		furnaceUpgrade(items, "furnace_upgrade_gold", 0xFFF6D23A, 0xFFB08A10);
		furnaceUpgrade(items, "furnace_upgrade_diamond", 0xFF7FE9E6, 0xFF2BAFA8);

		// Baus
		chest(blocks, "iron_chest", 0xFFD8D8D8, 0xFF9A9A9A, 0xFF6E6E6E);
		chest(blocks, "gold_chest", 0xFFF6D23A, 0xFFC9A520, 0xFF8A6D0E);
		chest(blocks, "diamond_chest", 0xFF8FF3EF, 0xFF4CC9C3, 0xFF2B8A86);
		// Baus animados: textura 64x64 no layout do ChestModel vanilla (entity/chest/<nome>.png)
		File chestEntities = new File(assets, "textures/entity/chest");
		chestEntities.mkdirs();
		chestEntity(chestEntities, "iron", 0xFFC8C8C8, 0xFF6E6E6E, 0xFF3A3A3A);
		chestEntity(chestEntities, "gold", 0xFFF2CC3A, 0xFF9A7A12, 0xFF5A4508);
		chestEntity(chestEntities, "diamond", 0xFF7FE9E6, 0xFF2B8A86, 0xFF14504E);

		upgrade(items, "chest_upgrade_iron", 0xFFD8D8D8, 0xFF8B5A2B);
		upgrade(items, "chest_upgrade_gold", 0xFFF6D23A, 0xFFD8D8D8);
		upgrade(items, "chest_upgrade_diamond", 0xFF8FF3EF, 0xFFF6D23A);

		// Escada de 12 niveis: os niveis que nao existiam antes (iron/gold/diamond acima mantem as cores originais).
		// Cores por material: claro, base, escuro.
		String[] ids = {"wood", "stone", "copper", "iron", "gold", "cobalt", "diamond", "netherite", "infernite", "endite", "astralite", "celestium"};
		int[][] palette = {
				{0xFFC8A060, 0xFF9C7A48, 0xFF8B5A2B}, {0xFFA0A0A0, 0xFF7F7F7F, 0xFF606060}, {0xFFF0A07A, 0xFFD7754A, 0xFF8E4A2C},
				{0xFFE8E8E8, 0xFF9A9A9A, 0xFF6E6E6E}, {0xFFF6D23A, 0xFFC9A520, 0xFF8A6D0E}, {0xFF6E9CFF, 0xFF2F5FD0, 0xFF1A3478},
				{0xFF8FF3EF, 0xFF4CC9C3, 0xFF2B8A86}, {0xFF6E6268, 0xFF4A3F44, 0xFF2A2326}, {0xFFFF8A3C, 0xFFE0401A, 0xFF8A1C08},
				{0xFFD8B8FF, 0xFFA77BE8, 0xFF5E3A9A}, {0xFFFF9CE6, 0xFFE45CC8, 0xFF7A1E6A}, {0xFFFFFFFF, 0xFFFFF0A8, 0xFFC8A84A}};
		java.util.Set<String> original = java.util.Set.of("iron", "gold", "diamond");
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			int light = palette[i][0];
			int base = palette[i][1];
			int dark = palette[i][2];
			if (!id.equals("wood") && !id.equals("stone") && !id.equals("iron") && !id.equals("diamond")) {
				wand(items, id + "_wand", light, dark);
			}
			if (id.equals("wood") || original.contains(id)) {
				continue;
			}
			furnaceUpgrade(items, "furnace_upgrade_" + id, light, dark);
			chest(blocks, id + "_chest", light, base, dark);
			chestEntity(chestEntities, id, base, dark, shade(dark, 0.6));
			upgrade(items, "chest_upgrade_" + id, light, palette[i - 1][1]);
		}

		// Mochila
		backpack(items, "backpack", 0xFF8B5A2B, 0xFF5C3A18);
		upgrade(items, "backpack_upgrade_size", 0xFF8B5A2B, 0xFFE8E8E8);
		upgrade(items, "backpack_upgrade_crafting", 0xFF8B5A2B, 0xFFC8A060);
		upgrade(items, "backpack_upgrade_furnace", 0xFF8B5A2B, 0xFFFF8C1A);
		upgrade(items, "backpack_upgrade_water", 0xFF8B5A2B, 0xFF3A6FE0);
		upgrade(items, "backpack_upgrade_lava", 0xFF8B5A2B, 0xFFE0521A);

		// Minimapa (icones da HUD)
		arrow(items, "map_arrow", 0xFFFFFFFF, 0xFF202020);
		waypoint(items, "waypoint", 0xFFFFD83A);

		// Pedra de teleporte (bloco de 2 de altura, item e icone do minimapa) e Pergaminho de Retorno
		waystoneSide(blocks, "waystone_side", true);
		waystoneSide(blocks, "waystone_side_upper", false);
		waystoneTop(blocks, "waystone_top");
		waystoneItem(items, "waystone");
		returnScroll(items, "return_scroll");

		// Livro de quests
		questBook(items, "quest_book");
		// Masmorras: Chave da Masmorra e Portao Selado
		dungeonKey(items, "dungeon_key");
		dungeonGate(blocks, "dungeon_gate");

		// Icones dos efeitos do sinalizador (18x18, atlas mob_effect)
		File effects = new File(assets, "textures/mob_effect");
		effects.mkdirs();
		prospectingIcon(effects, "prospecting");
		flightIcon(effects, "beacon_flight");
	}

	static BufferedImage img() {
		return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
	}

	static void save(BufferedImage im, File dir, String name) throws IOException {
		ImageIO.write(im, "png", new File(dir, name + ".png"));
	}

	static void set(BufferedImage im, int x, int y, int c) {
		if (x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight()) {
			im.setRGB(x, y, c);
		}
	}

	static void rect(BufferedImage im, int x0, int y0, int w, int h, int c) {
		for (int x = x0; x < x0 + w; x++) {
			for (int y = y0; y < y0 + h; y++) {
				set(im, x, y, c);
			}
		}
	}

	static void frame(BufferedImage im, int x0, int y0, int w, int h, int c) {
		rect(im, x0, y0, w, 1, c);
		rect(im, x0, y0 + h - 1, w, 1, c);
		rect(im, x0, y0, 1, h, c);
		rect(im, x0 + w - 1, y0, 1, h, c);
	}

	static int shade(int c, double f) {
		int a = (c >>> 24) & 0xFF;
		int r = clamp((int) (((c >> 16) & 0xFF) * f));
		int g = clamp((int) (((c >> 8) & 0xFF) * f));
		int b = clamp((int) ((c & 0xFF) * f));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	static void noise(BufferedImage im, long seed, double amount) {
		Random rnd = new Random(seed);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				int c = im.getRGB(x, y);
				if ((c >>> 24) != 0) {
					im.setRGB(x, y, shade(c, 1.0 + (rnd.nextDouble() - 0.5) * amount));
				}
			}
		}
	}

	static void wand(File dir, String name, int tip, int tipDark) throws IOException {
		BufferedImage im = img();
		int stick = 0xFF8B5A2B;
		int stickDark = 0xFF5C3A18;
		// cabo diagonal de (2,13) a (11,4)
		for (int i = 0; i < 10; i++) {
			set(im, 2 + i, 13 - i, stick);
			set(im, 3 + i, 13 - i, stickDark);
		}
		// ponta
		rect(im, 11, 2, 3, 3, tip);
		set(im, 13, 4, tipDark);
		set(im, 11, 2, 0xFFFFFFFF);
		set(im, 12, 1, tipDark);
		set(im, 14, 3, tipDark);
		set(im, 12, 5, tipDark);
		set(im, 10, 3, tipDark);
		save(im, dir, name);
	}

	static void tablet(File dir, String name) throws IOException {
		BufferedImage im = img();
		rect(im, 2, 1, 12, 14, 0xFF3A3A3A);
		frame(im, 2, 1, 12, 14, 0xFF202020);
		rect(im, 3, 2, 10, 11, 0xFF7FBF7F);
		rect(im, 4, 3, 8, 9, 0xFF5E9E5E);
		for (int gx = 0; gx < 3; gx++) {
			for (int gy = 0; gy < 3; gy++) {
				rect(im, 4 + gx * 3, 3 + gy * 3, 2, 2, 0xFFC8A060);
			}
		}
		rect(im, 7, 13, 2, 1, 0xFF909090);
		save(im, dir, name);
	}

	static void furnaceUpgrade(File dir, String name, int border, int borderDark) throws IOException {
		BufferedImage im = img();
		rect(im, 2, 2, 12, 12, 0xFF8A8A8A);
		noise(im, name.hashCode(), 0.25);
		rect(im, 5, 8, 6, 4, 0xFF303030);
		rect(im, 6, 9, 4, 3, 0xFFFF7A00);
		rect(im, 7, 9, 2, 1, 0xFFFFD040);
		frame(im, 2, 2, 12, 12, border);
		set(im, 13, 13, borderDark);
		set(im, 2, 13, borderDark);
		set(im, 13, 2, borderDark);
		frame(im, 1, 1, 14, 14, borderDark);
		save(im, dir, name);
	}

	static void chest(File dir, String name, int light, int base, int dark) throws IOException {
		// lateral
		BufferedImage side = img();
		rect(side, 0, 0, 16, 16, base);
		noise(side, name.hashCode(), 0.12);
		frame(side, 0, 0, 16, 16, dark);
		rect(side, 1, 1, 14, 1, light);
		rect(side, 1, 1, 1, 14, light);
		rect(side, 0, 7, 16, 1, dark);
		save(side, dir, name + "_side");
		// frente (com fechadura)
		BufferedImage front = img();
		front.setData(side.getData());
		rect(front, 7, 5, 2, 4, 0xFF303030);
		rect(front, 7, 5, 1, 1, 0xFF808080);
		save(front, dir, name + "_front");
		// topo
		BufferedImage top = img();
		rect(top, 0, 0, 16, 16, base);
		noise(top, name.hashCode() + 1, 0.12);
		frame(top, 0, 0, 16, 16, dark);
		frame(top, 1, 1, 14, 14, light);
		save(top, dir, name + "_top");
	}

	/**
	 * Textura de bau no layout UV do ChestModel (64x64): tampa em texOffs(0,0) 14x5x14, base em (0,19) 14x10x14,
	 * trava em (0,0) 2x4x1. Cada face e pintada com o material, ruido e borda escura; a trava fica escura.
	 */
	static void chestEntity(File dir, String name, int base, int trim, int lock) throws IOException {
		BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		boxFaces(im, 0, 0, 14, 5, 14, base, trim);
		boxFaces(im, 0, 19, 14, 10, 14, base, trim);
		noise(im, name.hashCode(), 0.14);
		// faixa de reforco no meio das laterais da base
		for (int[] face : sideFaces(0, 19, 14, 10, 14)) {
			rect(im, face[0], face[1] + face[3] / 2, face[2], 1, trim);
		}
		boxFaces(im, 0, 0, 2, 4, 1, lock, shade(lock, 0.6));
		ImageIO.write(im, "png", new File(dir, name + ".png"));
	}

	/** Faces laterais (4) de uma caixa no layout de box UV: {x, y, largura, altura}. */
	static int[][] sideFaces(int u, int v, int w, int h, int d) {
		return new int[][] {
				{u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h}, {u + 2 * d + w, v + d, w, h}
		};
	}

	static void boxFaces(BufferedImage im, int u, int v, int w, int h, int d, int color, int border) {
		int[][] faces = {
				{u + d, v, w, d}, {u + d + w, v, w, d},
				{u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h}, {u + 2 * d + w, v + d, w, h}
		};
		for (int[] f : faces) {
			rect(im, f[0], f[1], f[2], f[3], color);
			frame(im, f[0], f[1], f[2], f[3], border);
		}
	}

	static void upgrade(File dir, String name, int from, int to) throws IOException {
		BufferedImage im = img();
		rect(im, 1, 4, 6, 8, from);
		frame(im, 1, 4, 6, 8, shade(from, 0.6));
		rect(im, 9, 4, 6, 8, to);
		frame(im, 9, 4, 6, 8, shade(to, 0.6));
		// seta
		rect(im, 6, 7, 4, 2, 0xFFFFFFFF);
		set(im, 9, 6, 0xFFFFFFFF);
		set(im, 9, 9, 0xFFFFFFFF);
		set(im, 10, 7, 0xFFFFFFFF);
		set(im, 10, 8, 0xFFFFFFFF);
		save(im, dir, name);
	}

	static void backpack(File dir, String name, int base, int dark) throws IOException {
		BufferedImage im = img();
		rect(im, 3, 3, 10, 12, base);
		noise(im, 42, 0.15);
		frame(im, 3, 3, 10, 12, dark);
		rect(im, 4, 2, 8, 2, dark);
		rect(im, 5, 1, 6, 1, dark);
		rect(im, 5, 8, 6, 5, shade(base, 0.85));
		frame(im, 5, 8, 6, 5, dark);
		rect(im, 7, 6, 2, 1, 0xFFE8E8E8);
		save(im, dir, name);
	}

	static void arrow(File dir, String name, int fill, int outline) throws IOException {
		BufferedImage im = img();
		for (int y = 2; y < 14; y++) {
			int half = (y - 2) / 2;
			for (int x = 8 - half; x <= 7 + half; x++) {
				set(im, x, y, fill);
			}
		}
		rect(im, 3, 13, 4, 1, T);
		rect(im, 9, 13, 4, 1, T);
		rect(im, 6, 12, 4, 2, fill);
		for (int y = 1; y < 15; y++) {
			for (int x = 1; x < 15; x++) {
				if (im.getRGB(x, y) == 0) {
					boolean edge = im.getRGB(x + 1, y) == fill || im.getRGB(x - 1, y) == fill || im.getRGB(x, y + 1) == fill || im.getRGB(x, y - 1) == fill;
					if (edge) {
						set(im, x, y, outline);
					}
				}
			}
		}
		save(im, dir, name);
	}

	static void waypoint(File dir, String name, int color) throws IOException {
		BufferedImage im = img();
		for (int y = 2; y <= 9; y++) {
			int half = y <= 5 ? y - 2 : 9 - y + 1;
			rect(im, 8 - half - 1, y, half * 2 + 2, 1, color);
		}
		rect(im, 7, 10, 2, 4, color);
		rect(im, 7, 5, 2, 2, 0xFF202020);
		save(im, dir, name);
	}

	/** Garimpo: picareta sobre uma gema. */
	static void prospectingIcon(File dir, String name) throws IOException {
		BufferedImage im = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
		// gema (losango) embaixo a direita
		int[] gem = {0xFFB8FFF6, 0xFF4CE0D2, 0xFF1E8C84};
		for (int y = 0; y < 7; y++) {
			int half = y < 3 ? y + 1 : 6 - y + 1;
			for (int x = -half; x < half; x++) {
				int c = y < 2 ? gem[0] : (x < 0 ? gem[1] : gem[2]);
				set(im, 12 + x, 10 + y, c);
			}
		}
		// cabo de madeira na diagonal
		for (int i = 0; i < 9; i++) {
			set(im, 3 + i, 14 - i, 0xFF8B5A2B);
			set(im, 4 + i, 14 - i, 0xFF5C3A18);
		}
		// cabeca da picareta (arco dourado)
		int[][] head = {{6, 2}, {7, 2}, {8, 2}, {9, 3}, {10, 3}, {11, 4}, {12, 5}, {13, 6}, {13, 7}, {14, 8}, {5, 2}, {4, 3}, {3, 4}};
		for (int[] p : head) {
			set(im, p[0], p[1], 0xFFF6D23A);
			set(im, p[0], p[1] + 1, 0xFF8A6D0E);
		}
		save(im, dir, name);
	}

	/** Voo: asa branca com penas em degrade. */
	static void flightIcon(File dir, String name) throws IOException {
		BufferedImage im = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
		int[] feather = {0xFFFFFFFF, 0xFFE6F0FF, 0xFFC8DAF5, 0xFFA8BFE6, 0xFF8AA4D6};
		for (int f = 0; f < 5; f++) {
			int length = 13 - f * 2;
			int y = 4 + f * 2;
			for (int x = 0; x < length; x++) {
				set(im, 3 + x, y - x / 4, feather[f]);
				set(im, 3 + x, y + 1 - x / 4, shade(feather[f], 0.85));
			}
		}
		// osso da asa
		for (int y = 3; y < 15; y++) {
			set(im, 2, y, 0xFF5E6E8E);
		}
		save(im, dir, name);
	}

	static final int RUNE = 0xFFB070FF;
	static final int RUNE_GLOW = 0xFFE6CCFF;

	/** Lateral da pedra de teleporte: pedra polida com uma runa roxa brilhante (embaixo) ou o topo da runa (em cima). */
	static void waystoneSide(File dir, String name, boolean lower) throws IOException {
		BufferedImage im = img();
		rect(im, 0, 0, 16, 16, 0xFF8E8E8E);
		frame(im, 0, 0, 16, 16, 0xFF6A6A6A);
		noise(im, name.hashCode(), 0.18);
		if (lower) {
			// runa: losango com traco vertical
			for (int y = 3; y <= 12; y++) {
				set(im, 7, y, RUNE);
				set(im, 8, y, RUNE);
			}
			for (int d = 0; d <= 3; d++) {
				set(im, 4 + d, 7 - d, RUNE);
				set(im, 11 - d, 7 - d, RUNE);
				set(im, 4 + d, 8 + d, RUNE);
				set(im, 11 - d, 8 + d, RUNE);
			}
			set(im, 7, 7, RUNE_GLOW);
			set(im, 8, 8, RUNE_GLOW);
		} else {
			for (int y = 0; y <= 6; y++) {
				set(im, 7, y, RUNE);
				set(im, 8, y, RUNE);
			}
			rect(im, 6, 2, 4, 2, RUNE_GLOW);
		}
		save(im, dir, name);
	}

	/** Topo: cristal roxo. */
	static void waystoneTop(File dir, String name) throws IOException {
		BufferedImage im = img();
		rect(im, 0, 0, 16, 16, 0xFF7A3FBF);
		frame(im, 0, 0, 16, 16, 0xFF4A2080);
		rect(im, 4, 4, 8, 8, 0xFFB070FF);
		rect(im, 6, 6, 4, 4, RUNE_GLOW);
		noise(im, name.hashCode(), 0.12);
		save(im, dir, name);
	}

	/** Icone do item e do minimapa: obelisco cinza com cristal roxo. */
	static void waystoneItem(File dir, String name) throws IOException {
		BufferedImage im = img();
		int outline = 0xFF2A2A2A;
		rect(im, 3, 13, 10, 3, 0xFF8E8E8E);
		frame(im, 3, 13, 10, 3, outline);
		rect(im, 5, 4, 6, 10, 0xFFA0A0A0);
		frame(im, 5, 4, 6, 10, outline);
		rect(im, 7, 6, 2, 6, RUNE);
		set(im, 7, 8, RUNE_GLOW);
		rect(im, 6, 1, 4, 3, 0xFFB070FF);
		frame(im, 6, 1, 4, 3, 0xFF4A2080);
		set(im, 7, 2, RUNE_GLOW);
		save(im, dir, name);
	}

	/** Chave antiga de latao com argola e uma gema azul (cobalto) no cabo. */
	static void dungeonKey(File dir, String name) throws IOException {
		BufferedImage im = img();
		int outline = 0xFF1E1A16;
		int metal = 0xFFB8A27A;
		int light = 0xFFE8D7A8;
		int dark = 0xFF7A6340;
		// argola (canto de cima a esquerda)
		rect(im, 1, 1, 6, 6, outline);
		rect(im, 2, 2, 4, 4, metal);
		set(im, 2, 2, light);
		set(im, 5, 5, dark);
		set(im, 3, 3, 0xFF3A7BFF);
		set(im, 4, 4, 0xFF9CC4FF);
		set(im, 3, 4, 0xFF2050C0);
		set(im, 4, 3, 0xFF2050C0);
		// haste diagonal
		for (int i = 0; i < 8; i++) {
			set(im, 6 + i, 6 + i, metal);
			set(im, 7 + i, 6 + i, outline);
			set(im, 6 + i, 7 + i, dark);
		}
		set(im, 7, 7, light);
		set(im, 9, 9, light);
		// dentes
		rect(im, 9, 12, 3, 3, outline);
		rect(im, 10, 12, 1, 2, metal);
		rect(im, 12, 9, 3, 3, outline);
		rect(im, 12, 10, 2, 1, metal);
		save(im, dir, name);
	}

	/** Portao selado: grade de ferro escuro sobre pedra, com fechadura dourada e runas azuis. */
	static void dungeonGate(File dir, String name) throws IOException {
		BufferedImage im = img();
		rect(im, 0, 0, 16, 16, 0xFF2B2B30);
		noise(im, name.hashCode(), 0.25);
		int bar = 0xFF55555E;
		int barLight = 0xFF8A8A95;
		for (int x = 1; x < 16; x += 4) {
			rect(im, x, 0, 2, 16, bar);
			rect(im, x, 0, 1, 16, barLight);
		}
		for (int y : new int[] {1, 13}) {
			rect(im, 0, y, 16, 2, bar);
			rect(im, 0, y, 16, 1, barLight);
		}
		frame(im, 0, 0, 16, 16, 0xFF18181C);
		// fechadura
		rect(im, 6, 6, 4, 5, 0xFFD8B040);
		frame(im, 6, 6, 4, 5, 0xFF6E5414);
		set(im, 7, 7, 0xFFFFE89A);
		rect(im, 7, 8, 2, 1, 0xFF1A1206);
		set(im, 7, 9, 0xFF1A1206);
		// runas
		for (int[] r : new int[][] {{3, 5}, {12, 10}, {12, 5}, {3, 10}}) {
			set(im, r[0], r[1], 0xFF5AA0FF);
		}
		save(im, dir, name);
	}

	/** Pergaminho enrolado com selo roxo. */
	static void returnScroll(File dir, String name) throws IOException {
		BufferedImage im = img();
		int paper = 0xFFEADBB0;
		int edge = 0xFF9C7A48;
		for (int i = 0; i < 10; i++) {
			// faixa diagonal (o papel aberto)
			rect(im, 3 + i, 11 - i, 3, 3, paper);
		}
		rect(im, 1, 11, 4, 4, 0xFFD8C090);
		frame(im, 1, 11, 4, 4, edge);
		rect(im, 11, 1, 4, 4, 0xFFD8C090);
		frame(im, 11, 1, 4, 4, edge);
		for (int i = 2; i < 9; i += 2) {
			set(im, 4 + i, 10 - i, edge);
		}
		rect(im, 7, 6, 3, 3, RUNE);
		set(im, 8, 7, RUNE_GLOW);
		save(im, dir, name);
	}

	/** Livro de capa verde com fecho dourado e um ponto de exclamacao (quest). */
	static void questBook(File dir, String name) throws IOException {
		BufferedImage im = img();
		int outline = 0xFF1E2A14;
		int cover = 0xFF3E7A2E;
		int coverDark = 0xFF2A5420;
		int pages = 0xFFF2E8C8;
		int gold = 0xFFF2C230;
		// paginas (lado direito e base)
		rect(im, 4, 13, 10, 2, pages);
		rect(im, 13, 3, 2, 11, pages);
		for (int y = 4; y < 14; y += 2) {
			set(im, 14, y, 0xFFC8BC98);
		}
		// capa
		rect(im, 2, 1, 11, 13, cover);
		rect(im, 2, 1, 2, 13, coverDark);
		frame(im, 1, 0, 13, 15, outline);
		rect(im, 3, 15, 12, 1, outline);
		rect(im, 15, 2, 1, 13, outline);
		// moldura dourada e exclamacao
		frame(im, 5, 2, 7, 11, gold);
		rect(im, 8, 4, 1, 5, gold);
		set(im, 8, 10, gold);
		set(im, 7, 4, 0xFFFFE680);
		noise(im, 77, 0.12);
		save(im, dir, name);
	}
}
