import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * Texturas dos acessorios (aneis, colares, artefatos, reliquias) e os icones de encaixe vazio da tela de acessorios.
 * Tudo procedural; alguns artefatos partem de texturas vanilla (pata de coelho, pena, relogio, lanterna) lidas do jar
 * do cliente. Uso: java scripts/AccessoryTexGen.java <minecraft-client.jar> src/main/resources/assets/mlinnotas
 *
 * <p>Cada icone e montado numa grade 16x16 e recebe um contorno escuro automatico (cor do vizinho escurecida), como os
 * itens vanilla.</p>
 */
public class AccessoryTexGen {
	static final int T = 0;

	// metais: claro, base, escuro
	static final int[] COPPER = {0xFFF6B48E, 0xFFD7754A, 0xFF8E4A2C};
	static final int[] IRON = {0xFFFFFFFF, 0xFFC8C8C8, 0xFF7A7A7A};
	static final int[] GOLD = {0xFFFFF4A0, 0xFFF2C23A, 0xFFA07010};
	static final int[] COBALT = {0xFF9CC0FF, 0xFF3F6FE0, 0xFF1A3478};
	static final int[] DIAMOND = {0xFFD8FFFC, 0xFF6FE6E0, 0xFF1F8A86};
	static final int[] NETHERITE = {0xFF8A7E84, 0xFF4E4348, 0xFF2A2326};
	static final int[] LEATHER = {0xFFB07A48, 0xFF8B5A2B, 0xFF5C3A18};
	static final int[] RUBY = {0xFFFF9A9A, 0xFFE02A3A, 0xFF8A1020};

	static ZipFile jar;

	public static void main(String[] args) throws IOException {
		jar = new ZipFile(args[0]);
		File assets = new File(args[1]);
		File items = new File(assets, "textures/item");
		File slots = new File(assets, "textures/gui/sprites/container/slot");
		items.mkdirs();
		slots.mkdirs();

		// --- basicos ---
		String[] metals = {"copper", "iron", "gold", "cobalt", "diamond", "netherite"};
		int[][] band = {COPPER, IRON, GOLD, COBALT, IRON, NETHERITE};
		int[][] gem = {null, null, RUBY, COBALT, DIAMOND, GOLD};
		for (int i = 0; i < metals.length; i++) {
			save(outline(ring(band[i], gem[i])), items, metals[i] + "_ring");
			int[] pendant = gem[i] != null ? gem[i] : band[i];
			save(outline(necklace(band[i], pendant, i >= 4)), items, metals[i] + "_necklace");
		}

		// --- artefatos ---
		save(outline(rabbitFoot()), items, "rabbit_foot_charm");
		save(outline(glove(new int[] {0xFFC89A6A, 0xFF9C6A3A, 0xFF5E3A1A}, LEATHER, IRON, true)), items, "climbing_glove");
		save(outline(minerLantern()), items, "miner_lantern");
		save(outline(heartAmulet()), items, "life_amulet");
		save(outline(featherCharm()), items, "feather_charm");
		save(outline(thornNecklace()), items, "thorn_necklace");
		save(outline(magnetRing()), items, "magnet_ring");
		save(outline(pocketWatch()), items, "pocket_watch");
		save(outline(anchorBelt()), items, "anchor_belt");
		save(outline(fang()), items, "vampire_fang");
		save(outline(ring(GOLD, new int[] {0xFFB8FFF0, 0xFF5AD8C0, 0xFF1E7A70})), items, "diver_ring");
		save(outline(glove(LEATHER, new int[] {0xFF6E5030, 0xFF4E3820, 0xFF2E2010}, GOLD, false)), items, "prospector_glove");
		save(outline(horseshoe()), items, "lucky_horseshoe");
		save(outline(belt(LEATHER, GOLD, new int[] {0xFF4E8A3A, 0xFF2E6A22, 0xFF1A4012})), items, "traveler_belt");
		save(outline(glove(new int[] {0xFFFF7A6A, 0xFFC8302A, 0xFF7A1410}, new int[] {0xFFF0F0F0, 0xFFD0D0D0, 0xFF909090}, GOLD, false)),
				items, "duelist_glove");
		save(outline(frostRing()), items, "frost_ring");
		save(outline(sunPendant()), items, "sun_pendant");

		// --- reliquias ---
		save(outline(resonantPendant()), items, "resonant_pendant");
		save(outline(shell()), items, "abyssal_shell");
		save(outline(belt(new int[] {0xFF4A4448, 0xFF2E2A2C, 0xFF161414}, new int[] {0xFFFFC870, 0xFFFF7A1A, 0xFF9A2A08},
				new int[] {0xFFFF9A3A, 0xFFE0401A, 0xFF8A1C08})), items, "colossus_girdle");
		save(outline(dragonScale()), items, "dragonscale_charm");
		save(outline(star()), items, "herald_star");
		save(outline(ring(new int[] {0xFF5A5A5A, 0xFF2E2E2E, 0xFF141414}, new int[] {0xFFE8E8E8, 0xFF9A9A9A, 0xFF3A3A3A})), items, "wither_ring");
		save(outline(signet()), items, "champion_seal");

		// --- encaixes vazios (contorno cinza como os vanilla) ---
		save(slotRing(), slots, "ring");
		save(slotNecklace(), slots, "necklace");
		save(slotBelt(), slots, "belt");
		save(slotGloves(), slots, "gloves");
		save(slotCharm(), slots, "charm");
		System.out.println("ok");
	}

	// ======================== base ========================

	static BufferedImage img() {
		return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
	}

	static void save(BufferedImage im, File dir, String name) throws IOException {
		ImageIO.write(im, "png", new File(dir, name + ".png"));
	}

	static BufferedImage vanilla(String path) throws IOException {
		try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/" + path + ".png"))) {
			BufferedImage src = ImageIO.read(in);
			BufferedImage out = img();
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					out.setRGB(x, y, src.getRGB(x, y));
				}
			}
			return out;
		}
	}

	static void set(BufferedImage im, int x, int y, int c) {
		if (x >= 0 && y >= 0 && x < 16 && y < 16) {
			im.setRGB(x, y, c);
		}
	}

	static int get(BufferedImage im, int x, int y) {
		return x >= 0 && y >= 0 && x < 16 && y < 16 ? im.getRGB(x, y) : 0;
	}

	static boolean solid(BufferedImage im, int x, int y) {
		return (get(im, x, y) >>> 24) > 0;
	}

	static void rect(BufferedImage im, int x0, int y0, int w, int h, int c) {
		for (int x = x0; x < x0 + w; x++) {
			for (int y = y0; y < y0 + h; y++) {
				set(im, x, y, c);
			}
		}
	}

	static int shade(int c, double f) {
		int a = (c >>> 24) & 0xFF;
		int r = Math.min(255, Math.max(0, (int) (((c >> 16) & 0xFF) * f)));
		int g = Math.min(255, Math.max(0, (int) (((c >> 8) & 0xFF) * f)));
		int b = Math.min(255, Math.max(0, (int) ((c & 0xFF) * f)));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Contorno de 1 px em volta das partes opacas, com a cor do vizinho bem escurecida. */
	static BufferedImage outline(BufferedImage im) {
		BufferedImage out = img();
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				out.setRGB(x, y, im.getRGB(x, y));
			}
		}
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (solid(im, x, y)) {
					continue;
				}
				for (int[] d : dirs) {
					if (solid(im, x + d[0], y + d[1])) {
						out.setRGB(x, y, shade(get(im, x + d[0], y + d[1]), 0.35) | 0xFF000000);
						break;
					}
				}
			}
		}
		return out;
	}

	/** Cor do metal pela posicao: claro no alto/esquerda, escuro embaixo/direita. */
	static int lit(int[] pal, double x, double y, double cx, double cy) {
		double t = (x - cx) * 0.6 + (y - cy);
		return t < -1.5 ? pal[0] : t > 1.5 ? pal[2] : pal[1];
	}

	// ======================== formas ========================

	/** Anel em perspectiva (elipse) com pedra opcional no alto. */
	static BufferedImage ring(int[] band, int[] gem) {
		BufferedImage im = img();
		double cx = 7.5;
		double cy = 9.5;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = (x - cx) / 5.6;
				double dy = (y - cy) / 4.6;
				double outer = dx * dx + dy * dy;
				double ix = (x - cx) / 4.0;
				double iy = (y - cy + 0.4) / 2.9;
				double inner = ix * ix + iy * iy;
				if (outer <= 1.0 && inner > 1.0) {
					set(im, x, y, lit(band, x, y, cx, cy - 1));
				}
			}
		}
		// brilho
		set(im, 4, 6, band[0]);
		set(im, 5, 5, band[0]);
		if (gem != null) {
			rect(im, 6, 2, 4, 4, gem[1]);
			set(im, 6, 2, T);
			set(im, 9, 2, T);
			set(im, 6, 5, T);
			set(im, 9, 5, T);
			rect(im, 7, 1, 2, 1, gem[1]);
			set(im, 7, 3, gem[0]);
			set(im, 7, 2, gem[0]);
			set(im, 8, 4, gem[2]);
			set(im, 8, 5, gem[2]);
			// garras
			set(im, 5, 4, band[2]);
			set(im, 10, 4, band[2]);
		}
		return im;
	}

	/** Colar: corrente em V com pingente embaixo; {@code faceted} = pedra lapidada em vez de disco. */
	static BufferedImage necklace(int[] chain, int[] pendant, boolean faceted) {
		BufferedImage im = img();
		chainV(im, chain, 9);
		pendant(im, pendant, 8, 12, faceted);
		return im;
	}

	static void chainV(BufferedImage im, int[] chain, int bottom) {
		for (int i = 0; i <= 6; i++) {
			int y = 1 + i * (bottom - 1) / 6;
			int left = 1 + i;
			int right = 14 - i;
			set(im, left, y, (i % 2 == 0) ? chain[0] : chain[1]);
			set(im, right, y, (i % 2 == 0) ? chain[1] : chain[2]);
			if (i < 6) {
				set(im, left, y + 1, chain[1]);
				set(im, right, y + 1, chain[2]);
			}
		}
	}

	static void pendant(BufferedImage im, int[] pal, int cx, int cy, boolean faceted) {
		if (faceted) {
			rect(im, cx - 2, cy - 1, 4, 3, pal[1]);
			rect(im, cx - 1, cy - 2, 2, 1, pal[0]);
			rect(im, cx - 1, cy + 2, 2, 1, pal[2]);
			set(im, cx - 2, cy - 1, pal[0]);
			set(im, cx - 1, cy - 1, pal[0]);
			set(im, cx + 1, cy + 1, pal[2]);
		} else {
			rect(im, cx - 2, cy - 2, 4, 4, pal[1]);
			set(im, cx - 2, cy - 2, T);
			set(im, cx + 1, cy - 2, T);
			set(im, cx - 2, cy + 1, T);
			set(im, cx + 1, cy + 1, T);
			set(im, cx - 1, cy - 1, pal[0]);
			set(im, cx, cy, pal[2]);
			set(im, cx - 1, cy + 1, pal[2]);
		}
		set(im, cx - 1, cy - 3, pal[2]);
	}

	/** Luva: palma, 4 dedos, polegar e punho; {@code claws} = garras de metal nas pontas. */
	static BufferedImage glove(int[] main, int[] cuff, int[] accent, boolean claws) {
		BufferedImage im = img();
		// palma
		for (int y = 6; y <= 12; y++) {
			for (int x = 4; x <= 10; x++) {
				set(im, x, y, lit(main, x, y, 7, 9));
			}
		}
		// dedos
		int[] fx = {4, 6, 8, 10};
		int[] top = {3, 2, 2, 3};
		for (int i = 0; i < 4; i++) {
			for (int y = top[i]; y < 6; y++) {
				set(im, fx[i], y, y == top[i] ? main[0] : main[1]);
			}
			if (claws) {
				set(im, fx[i], top[i] - 1, accent[0]);
			}
		}
		// polegar
		rect(im, 11, 7, 2, 2, main[1]);
		set(im, 12, 6, main[1]);
		set(im, 13, 6, claws ? accent[0] : main[0]);
		// punho
		for (int x = 3; x <= 11; x++) {
			set(im, x, 13, cuff[0]);
			set(im, x, 14, cuff[1]);
		}
		set(im, 7, 13, accent[1]);
		set(im, 7, 14, accent[2]);
		// costura
		set(im, 6, 9, main[2]);
		set(im, 8, 9, main[2]);
		return im;
	}

	/** Cinto em diagonal com fivela. */
	static BufferedImage belt(int[] strap, int[] buckle, int[] trim) {
		BufferedImage im = img();
		for (int x = 0; x < 16; x++) {
			int y0 = 4 + x / 3;
			set(im, x, y0, strap[0]);
			set(im, x, y0 + 1, strap[1]);
			set(im, x, y0 + 2, strap[1]);
			set(im, x, y0 + 3, strap[2]);
			if (x % 3 == 1) {
				set(im, x, y0 + 1, trim[1]);
			}
		}
		// fivela
		rect(im, 5, 4, 6, 7, buckle[1]);
		rect(im, 6, 5, 4, 5, T);
		set(im, 5, 4, buckle[0]);
		set(im, 6, 4, buckle[0]);
		set(im, 5, 5, buckle[0]);
		set(im, 10, 10, buckle[2]);
		set(im, 9, 10, buckle[2]);
		set(im, 10, 9, buckle[2]);
		// pino e tira passando por dentro
		for (int y = 5; y < 10; y++) {
			set(im, 8, y, y == 7 ? buckle[0] : T);
		}
		rect(im, 6, 6, 2, 3, strap[1]);
		rect(im, 9, 7, 1, 3, strap[2]);
		return im;
	}

	// ======================== artefatos ========================

	static BufferedImage rabbitFoot() throws IOException {
		BufferedImage im = vanilla("item/rabbit_foot");
		// argola dourada no topo
		set(im, 11, 1, GOLD[1]);
		set(im, 12, 1, GOLD[0]);
		set(im, 13, 2, GOLD[1]);
		set(im, 10, 2, GOLD[2]);
		set(im, 11, 3, GOLD[2]);
		return im;
	}

	static BufferedImage minerLantern() throws IOException {
		BufferedImage lantern = vanilla("item/lantern");
		BufferedImage im = img();
		// lanterna um pouco menor, pendurada numa tira de couro
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (solid(lantern, x, y)) {
					set(im, x, y, lantern.getRGB(x, y));
				}
			}
		}
		for (int x = 0; x < 16; x++) {
			int y = 2 - (x > 7 ? 0 : 0);
			if (!solid(im, x, y)) {
				set(im, x, y, x % 3 == 0 ? LEATHER[2] : LEATHER[1]);
			}
			if (!solid(im, x, y + 1)) {
				set(im, x, y + 1, LEATHER[2]);
			}
		}
		return im;
	}

	static BufferedImage heartAmulet() {
		BufferedImage im = img();
		chainV(im, GOLD, 8);
		// coracao
		int[][] heart = {
				{0, 1, 1, 0, 1, 1, 0},
				{1, 2, 1, 1, 1, 1, 1},
				{1, 1, 1, 1, 1, 1, 1},
				{0, 1, 1, 1, 1, 3, 0},
				{0, 0, 1, 1, 3, 0, 0},
				{0, 0, 0, 3, 0, 0, 0}};
		for (int y = 0; y < heart.length; y++) {
			for (int x = 0; x < heart[y].length; x++) {
				int v = heart[y][x];
				if (v > 0) {
					set(im, 5 + x, 9 + y, v == 2 ? 0xFFFFC0C0 : v == 3 ? RUBY[2] : RUBY[1]);
				}
			}
		}
		set(im, 8, 8, GOLD[2]);
		return im;
	}

	static BufferedImage featherCharm() throws IOException {
		BufferedImage im = vanilla("item/feather");
		// fio com conta azul
		set(im, 1, 14, 0xFFE8E0D0);
		set(im, 2, 13, 0xFFE8E0D0);
		set(im, 0, 15, 0xFF5AA8E8);
		set(im, 1, 15, 0xFF3A78C8);
		return im;
	}

	static BufferedImage thornNecklace() {
		BufferedImage im = img();
		int[] vine = {0xFF8AD06A, 0xFF4E9A3A, 0xFF2A5A1E};
		chainV(im, vine, 9);
		// espinhos
		int[][] thorns = {{2, 2}, {4, 5}, {13, 2}, {11, 5}, {6, 8}, {9, 8}};
		for (int[] t : thorns) {
			set(im, t[0] + (t[0] < 8 ? -1 : 1), t[1], 0xFFE8E0C0);
		}
		// rosa
		rect(im, 6, 10, 4, 4, RUBY[1]);
		set(im, 6, 10, T);
		set(im, 9, 13, T);
		set(im, 7, 11, RUBY[0]);
		set(im, 8, 12, RUBY[2]);
		set(im, 7, 14, vine[1]);
		set(im, 8, 14, vine[2]);
		return im;
	}

	static BufferedImage magnetRing() {
		BufferedImage im = img();
		int[] red = {0xFFFF8A80, 0xFFE0302A, 0xFF8A1410};
		// ima em U (vermelho) com pontas de metal
		for (int y = 3; y <= 12; y++) {
			for (int x = 3; x <= 12; x++) {
				double dx = x - 7.5;
				double dy = y - 7.5;
				double d = Math.sqrt(dx * dx + dy * dy);
				if (y >= 7.5 && d <= 5.2 && d >= 2.4) {
					set(im, x, y, lit(red, x, y, 7.5, 9));
				}
			}
		}
		rect(im, 3, 3, 3, 5, red[1]);
		rect(im, 10, 3, 3, 5, red[2]);
		set(im, 3, 4, red[0]);
		rect(im, 3, 1, 3, 2, IRON[1]);
		rect(im, 10, 1, 3, 2, IRON[2]);
		set(im, 3, 1, IRON[0]);
		// faiscas
		set(im, 7, 1, 0xFFFFF4A0);
		set(im, 8, 3, 0xFFFFF4A0);
		return im;
	}

	static BufferedImage pocketWatch() throws IOException {
		BufferedImage clock = vanilla("item/clock_00");
		BufferedImage im = img();
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (solid(clock, x, y) && y >= 2) {
					set(im, x, y, clock.getRGB(x, y));
				}
			}
		}
		// corrente
		set(im, 7, 1, GOLD[0]);
		set(im, 8, 1, GOLD[1]);
		set(im, 9, 0, GOLD[1]);
		set(im, 11, 0, GOLD[2]);
		set(im, 13, 1, GOLD[1]);
		set(im, 14, 3, GOLD[2]);
		return im;
	}

	static BufferedImage anchorBelt() {
		// a = claro, b = base, c = escuro, l/m = corda
		return map(new String[] {
				"......bbb.......",
				".....b...c......",
				"......bbc.......",
				"...abbbbbbbc....",
				".......bc..l....",
				".......bc...m...",
				".......bc...l...",
				".......bc..m....",
				".......bc.......",
				".a.....bc.....c.",
				"aab....bc....cbc",
				".ab....bc....bc.",
				"..ab...bc...bc..",
				"...abbbbbbbbc...",
				".....abbbbc.....",
				"................"},
				'a', 0xFFC8D0D8, 'b', 0xFF7A828A, 'c', 0xFF3A4048, 'l', LEATHER[0], 'm', LEATHER[1]);
	}

	static BufferedImage fang() {
		// presa curva de osso com ponta vermelha, presa num aro de ouro
		return map(new String[] {
				"................",
				"....g......g....",
				".....g....g.....",
				"......gGGg......",
				".....awwwws.....",
				".....awwwws.....",
				".....awwwws.....",
				"......awwws.....",
				"......awwws.....",
				".......awws.....",
				".......awwr.....",
				"........awr.....",
				"........arR.....",
				".........R......",
				"................",
				"................"},
				'g', LEATHER[1], 'G', GOLD[1], 'a', 0xFFFFFFFF, 'w', 0xFFE8E0D0, 's', 0xFFA89C88, 'r', RUBY[1], 'R', RUBY[2]);
	}

	/** Icone a partir de um desenho em texto (ponto = transparente) e pares caractere/cor. */
	static BufferedImage map(String[] rows, Object... palette) {
		java.util.Map<Character, Integer> colors = new java.util.HashMap<>();
		for (int i = 0; i < palette.length; i += 2) {
			colors.put((Character) palette[i], (Integer) palette[i + 1]);
		}
		BufferedImage im = img();
		for (int y = 0; y < rows.length; y++) {
			for (int x = 0; x < rows[y].length(); x++) {
				Integer c = colors.get(rows[y].charAt(x));
				if (c != null) {
					set(im, x, y, c);
				}
			}
		}
		return im;
	}

	static BufferedImage horseshoe() {
		BufferedImage im = img();
		int[] metal = {0xFFE0E0E8, 0xFF9A9AA4, 0xFF5A5A64};
		for (int y = 1; y < 15; y++) {
			for (int x = 1; x < 15; x++) {
				double dx = x - 7.5;
				double dy = y - 6.5;
				double d = Math.sqrt(dx * dx + dy * dy * 0.8);
				if (d <= 6.4 && d >= 3.6 && !(y < 6 && Math.abs(dx) < 3.5)) {
					set(im, x, y, lit(metal, x, y, 7.5, 7));
				}
			}
		}
		// pregos
		set(im, 3, 7, metal[2]);
		set(im, 12, 7, metal[2]);
		set(im, 5, 11, metal[2]);
		set(im, 10, 11, metal[2]);
		// fita verde da sorte
		set(im, 7, 13, 0xFF5AC85A);
		set(im, 8, 13, 0xFF3A9A3A);
		set(im, 7, 14, 0xFF3A9A3A);
		set(im, 9, 14, 0xFF2A7A2A);
		return im;
	}

	static BufferedImage frostRing() {
		BufferedImage im = ring(new int[] {0xFFFFFFFF, 0xFFC8E8F8, 0xFF7AA8C8}, new int[] {0xFFF0FFFF, 0xFF8AD8FF, 0xFF3A88C8});
		// cristais de gelo
		set(im, 2, 3, 0xFFE8FFFF);
		set(im, 13, 2, 0xFFE8FFFF);
		set(im, 14, 6, 0xFFB8E8FF);
		return im;
	}

	static BufferedImage sunPendant() {
		BufferedImage im = img();
		chainV(im, GOLD, 7);
		int cx = 8;
		int cy = 11;
		int[] sun = {0xFFFFF8C0, 0xFFFFD040, 0xFFE08A10};
		for (int y = cy - 4; y <= cy + 4; y++) {
			for (int x = cx - 4; x <= cx + 4; x++) {
				double d = Math.hypot(x - cx + 0.5, y - cy + 0.5);
				if (d <= 2.3) {
					set(im, x, y, lit(sun, x, y, cx, cy));
				}
			}
		}
		// raios
		int[][] rays = {{0, -4}, {0, 3}, {-4, 0}, {3, 0}, {-3, -3}, {2, -3}, {-3, 2}, {2, 2}};
		for (int[] r : rays) {
			set(im, cx + r[0], cy + r[1], sun[2]);
		}
		set(im, cx - 1, cy - 1, 0xFFFFFFFF);
		return im;
	}

	// ======================== reliquias ========================

	static BufferedImage resonantPendant() {
		BufferedImage im = img();
		chainV(im, IRON, 7);
		int cx = 8;
		int cy = 11;
		// diapasao de cobalto com ondas
		rect(im, cx - 2, cy - 2, 1, 4, COBALT[0]);
		rect(im, cx + 1, cy - 2, 1, 4, COBALT[2]);
		rect(im, cx - 2, cy + 2, 4, 1, COBALT[1]);
		rect(im, cx - 1, cy + 3, 2, 2, COBALT[1]);
		set(im, cx - 1, cy + 3, COBALT[0]);
		int wave = 0xFF8ACFFF;
		set(im, cx - 4, cy - 1, wave);
		set(im, cx - 4, cy, wave);
		set(im, cx - 5, cy - 2, 0xFF5A9AE8);
		set(im, cx + 3, cy - 1, wave);
		set(im, cx + 3, cy, wave);
		set(im, cx + 4, cy - 2, 0xFF5A9AE8);
		return im;
	}

	static BufferedImage shell() {
		BufferedImage im = img();
		int[] pal = {0xFF8AF0E0, 0xFF2A9A8C, 0xFF0E4A48};
		// leque de concha
		for (int y = 2; y <= 13; y++) {
			for (int x = 1; x <= 14; x++) {
				double dx = x - 7.5;
				double dy = 13 - y;
				double d = Math.sqrt(dx * dx + dy * dy);
				if (d <= 10.5 && dy >= Math.abs(dx) * 0.35 - 0.5) {
					double ang = Math.atan2(dx, dy);
					boolean rib = ((int) Math.floor((ang + 1.6) * 3.2)) % 2 == 0;
					set(im, x, y, rib ? pal[1] : (d < 5 ? pal[2] : pal[0]));
				}
			}
		}
		rect(im, 6, 13, 4, 2, pal[2]);
		// perola
		set(im, 7, 9, 0xFFFFFFFF);
		set(im, 8, 9, 0xFFE0E8F0);
		set(im, 7, 10, 0xFFC8D0E0);
		set(im, 8, 10, 0xFFA8B0C8);
		return im;
	}

	static BufferedImage dragonScale() {
		BufferedImage im = img();
		int[] pal = {0xFFC88AF0, 0xFF6A2A9A, 0xFF2A0E44};
		// escama em gota
		for (int y = 2; y <= 14; y++) {
			for (int x = 2; x <= 13; x++) {
				double dx = (x - 7.5) / 5.5;
				double dy = (y - 9.0) / 5.5;
				boolean body = dx * dx + dy * dy <= 1.0 && y >= 4 || Math.abs(x - 7.5) <= (y - 1.5) * 0.9 && y < 6;
				if (body) {
					set(im, x, y, lit(pal, x, y, 7, 9));
				}
			}
		}
		// nervura central e brilho
		for (int y = 4; y <= 13; y++) {
			set(im, 7, y, pal[2]);
		}
		set(im, 5, 7, 0xFFF0D0FF);
		set(im, 4, 8, pal[0]);
		set(im, 7, 1, GOLD[1]);
		set(im, 8, 1, GOLD[2]);
		return im;
	}

	static BufferedImage star() {
		BufferedImage im = img();
		int[] pal = {0xFFFFFFFF, 0xFFFF9CE6, 0xFFA03A9A};
		double cx = 7.5;
		double cy = 8.0;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = x - cx;
				double dy = y - cy;
				double ang = Math.atan2(dy, dx) + Math.PI / 2;
				double r = Math.hypot(dx, dy);
				double k = Math.abs(Math.cos(ang * 2.5));
				double limit = 2.6 + 4.6 * Math.pow(k, 3);
				if (r <= limit) {
					set(im, x, y, r < 2.0 ? pal[0] : lit(pal, x, y, cx, cy));
				}
			}
		}
		set(im, 2, 2, 0xFFFFF0FF);
		set(im, 13, 13, 0xFFFFF0FF);
		return im;
	}

	static BufferedImage signet() {
		BufferedImage im = ring(GOLD, null);
		// sinete vermelho com marca
		rect(im, 5, 2, 6, 5, RUBY[1]);
		set(im, 5, 2, T);
		set(im, 10, 2, T);
		set(im, 5, 6, T);
		set(im, 10, 6, T);
		rect(im, 6, 3, 2, 1, RUBY[0]);
		set(im, 7, 4, GOLD[0]);
		set(im, 8, 4, GOLD[0]);
		set(im, 8, 5, GOLD[1]);
		set(im, 7, 3, GOLD[0]);
		return im;
	}

	// ======================== encaixes vazios ========================

	static final int S = 0xFF9C9C9C;

	static BufferedImage slotRing() {
		BufferedImage im = img();
		double cx = 7.5;
		double cy = 9.0;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = (x - cx) / 5.0;
				double dy = (y - cy) / 4.0;
				double d = Math.sqrt(dx * dx + dy * dy);
				if (Math.abs(d - 1.0) < 0.12) {
					set(im, x, y, S);
				}
			}
		}
		rect(im, 7, 3, 2, 2, S);
		return im;
	}

	static BufferedImage slotNecklace() {
		BufferedImage im = img();
		for (int i = 0; i <= 6; i++) {
			int y = 2 + i * 7 / 6;
			set(im, 2 + i, y, S);
			set(im, 13 - i, y, S);
		}
		rect(im, 7, 10, 2, 1, S);
		set(im, 6, 11, S);
		set(im, 9, 11, S);
		set(im, 6, 12, S);
		set(im, 9, 12, S);
		rect(im, 7, 13, 2, 1, S);
		return im;
	}

	static BufferedImage slotBelt() {
		BufferedImage im = img();
		rect(im, 1, 6, 14, 1, S);
		rect(im, 1, 10, 14, 1, S);
		// fivela
		rect(im, 6, 5, 4, 1, S);
		rect(im, 6, 11, 4, 1, S);
		rect(im, 6, 5, 1, 7, S);
		rect(im, 9, 5, 1, 7, S);
		for (int x = 7; x < 9; x++) {
			set(im, x, 6, T);
			set(im, x, 10, T);
		}
		return im;
	}

	static BufferedImage slotGloves() {
		BufferedImage im = img();
		// contorno de mao
		int[] fx = {4, 6, 8, 10};
		for (int i = 0; i < 4; i++) {
			rect(im, fx[i], 3 + (i == 0 || i == 3 ? 1 : 0), 1, 4, S);
		}
		rect(im, 4, 7, 1, 6, S);
		rect(im, 10, 7, 1, 2, S);
		set(im, 11, 9, S);
		set(im, 12, 8, S);
		set(im, 12, 9, T);
		rect(im, 10, 10, 1, 3, S);
		rect(im, 4, 13, 7, 1, S);
		return im;
	}

	static BufferedImage slotCharm() {
		BufferedImage im = img();
		// losango (talisma) com argola
		for (int i = 0; i <= 4; i++) {
			set(im, 7 - i, 5 + i, S);
			set(im, 8 + i, 5 + i, S);
			set(im, 7 - i, 13 - i, S);
			set(im, 8 + i, 13 - i, S);
		}
		set(im, 7, 3, S);
		set(im, 8, 3, S);
		set(im, 6, 2, S);
		set(im, 9, 2, S);
		set(im, 7, 1, S);
		set(im, 8, 1, S);
		return im;
	}
}
