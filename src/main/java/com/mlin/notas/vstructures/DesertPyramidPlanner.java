package com.mlin.notas.vstructures;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Templo do deserto refeito: piramide de degraus de 41x41 (17 camadas) com faixas de arenito liso e desgaste
 * (degraus trocados, areia acumulada), portal de pilone entre as duas torres da frente (as torres vanilla com o
 * glifo de terracota laranja, agora com escada de mao ate o terraco), avenida de obeliscos (alguns caidos) e um
 * santuario no topo. Dentro: o grande salao com pilares e a rosa-dos-ventos de terracota (azul no centro), galerias
 * laterais (deposito com potes, barris e areia suspeita; biblioteca de embalsamar), uma camara superior por escada de
 * mao, a galeria dos ancestrais com a escadaria que desce ao nivel das tumbas (corredor de nichos funerarios, tumba do
 * farao com sarcofago, bau e gerador de husks) e, embaixo da rosa-dos-ventos, a camara do tesouro vanilla: placa de
 * pressao de pedra no centro, 9 TNT embaixo e 4 baus nas paredes. Entrada pela frente (-z). Logica pura.
 *
 * <p>Referencias (so ideia de forma, camadas e paleta; nada copiado): YUNG's Better Desert Temples (camadas,
 * armadilhas e camara final), redesenhos "desert castle" de Superwan e o de escala 3:1 de PixlPlex (PC Gamer,
 * "These Minecraft desert temple redesigns"), mapa "Desert Pyramid Maze" (minecraftmaps.com, varios niveis e
 * escadas) e as piramides com geradores escondidos do Repurposed Structures (wiki do projeto).
 */
public final class DesertPyramidPlanner extends PlanBuilder {
	static final int R = 20;
	static final String TREASURE = "minecraft:chests/desert_pyramid";
	static final String TOMB = "mlinnotas:chests/desert_tomb";
	static final String STORAGE = "mlinnotas:chests/desert_storage";
	static final String DIG = "minecraft:archaeology/desert_pyramid";
	/** Placa da armadilha (centro da camara do tesouro). */
	public static final String MARKER_PLATE = "trap_plate";
	public static final String MARKER_TOMB = "tomb";

	private static final String SANDSTONE = "minecraft:sandstone";
	private static final String CUT = "minecraft:cut_sandstone";
	private static final String SMOOTH = "minecraft:smooth_sandstone";
	private static final String CHISELED = "minecraft:chiseled_sandstone";
	private static final String ORANGE = "minecraft:orange_terracotta";
	private static final String BLUE = "minecraft:blue_terracotta";
	private static final String SAND = "minecraft:sand";

	private final List<int[]> sandPiles = new ArrayList<>();

	private DesertPyramidPlanner(Random random) {
		super(new VPlan(false), random);
	}

	public static VPlan plan(long seed, int variant) {
		DesertPyramidPlanner b = new DesertPyramidPlanner(new Random(seed * 7919L + 11));
		b.build();
		return b.p;
	}

	private void build() {
		body();
		summit();
		int towerTop = between(17, 19);
		tower(18, towerTop);
		tower(-18, towerTop);
		gate();
		avenue();
		hall();
		westGallery();
		eastGallery();
		upperChamber();
		descent();
		lowerLevel();
		treasureChamber();
		surroundings();
		archaeology();
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, -30);
	}

	// --- piramide ---

	private void body() {
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				p.set(x, -1, z, SANDSTONE, VPlan.FILL_DOWN);
			}
		}
		for (int y = 0; y <= 16; y++) {
			int rad = R - y;
			boolean band = y % 5 == 4;
			for (int x = -rad; x <= rad; x++) {
				for (int z = -rad; z <= rad; z++) {
					if (Math.max(Math.abs(x), Math.abs(z)) < rad) {
						set(x, y, z, SANDSTONE);
						continue;
					}
					String facing = Math.abs(z) >= Math.abs(x) ? (z < 0 ? S : N) : (x < 0 ? E : W);
					int roll = r.nextInt(100);
					String step = band ? "minecraft:smooth_sandstone_stairs" : "minecraft:sandstone_stairs";
					if (roll < 5) {
						set(x, y, z, SANDSTONE);
					} else if (roll < 8) {
						set(x, y, z, stairs("minecraft:smooth_sandstone_stairs", facing));
					} else if (roll < 11 && y > 0) {
						set(x, y, z, SAND);
					} else {
						set(x, y, z, stairs(step, facing));
					}
				}
			}
		}
		// um canto de tras desabado: degraus viram rampa de areia
		if (chance(0.6)) {
			int sx = pick(-1, 1);
			for (int y = 0; y <= 6; y++) {
				int rad = R - y;
				for (int k = 0; k < 7 - y; k++) {
					int x = sx * (rad - k % 3);
					int z = rad - k / 3;
					if (p.get(x, y, z) != null && Math.abs(x) > 12 && z > 12) {
						set(x, y, z, k % 2 == 0 ? SAND : SANDSTONE);
					}
				}
			}
		}
	}

	private void summit() {
		int base = 17;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				set(3 * sx, base, 3 * sz, CHISELED);
				set(3 * sx, base + 1, 3 * sz, CUT);
				set(3 * sx, base + 2, 3 * sz, CHISELED);
			}
		}
		fill(-3, base + 3, -3, 3, base + 3, 3, SMOOTH);
		for (int x = -3; x <= 3; x++) {
			set(x, base + 3, -3, x % 2 == 0 ? ORANGE : CUT);
			set(x, base + 3, 3, x % 2 == 0 ? ORANGE : CUT);
		}
		fill(-2, base + 4, -2, 2, base + 4, 2, CUT);
		fill(-1, base + 5, -1, 1, base + 5, 1, CHISELED);
		set(0, base + 6, 0, "minecraft:gold_block");
		air(-2, base, -2, 2, base + 2, 2);
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				set(x, base - 1, z, (x + z) % 2 == 0 ? ORANGE : SMOOTH);
			}
		}
		set(0, base - 1, 0, BLUE);
		p.suspicious(0, base, 0, "minecraft:suspicious_sand", DIG);
		set(-2, base, -2, "minecraft:decorated_pot");
		set(2, base, 2, "minecraft:decorated_pot");
		set(2, base, -2, s("candle", "candles=3", "lit=false"));
	}

	/** Torre da frente (as duas vanilla): 7x7, glifo de terracota na face da frente, escada de mao ate o terraco. */
	private void tower(int cx, int top) {
		int cz = -18;
		for (int x = cx - 3; x <= cx + 3; x++) {
			for (int z = cz - 3; z <= cz + 3; z++) {
				p.set(x, -1, z, SANDSTONE, VPlan.FILL_DOWN);
				boolean edge = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
				boolean corner = Math.abs(x - cx) == 3 && Math.abs(z - cz) == 3;
				for (int y = 0; y < top; y++) {
					if (corner) {
						set(x, y, z, y % 6 == 0 ? CHISELED : CUT);
					} else if (edge) {
						set(x, y, z, y % 6 == 5 ? CUT : chance(0.06) ? SMOOTH : SANDSTONE);
					} else {
						set(x, y, z, VPlan.AIR);
					}
				}
				set(x, top, z, CUT);
			}
		}
		// cornija para fora e parapeito
		for (int x = cx - 4; x <= cx + 4; x++) {
			for (int z = cz - 4; z <= cz + 4; z++) {
				int dx = x - cx;
				int dz = z - cz;
				if (Math.max(Math.abs(dx), Math.abs(dz)) == 4) {
					String facing = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? S : N) : (dx < 0 ? E : W);
					set(x, top, z, stairsTop("minecraft:smooth_sandstone_stairs", facing));
				}
				if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
					boolean merlon = (Math.abs(dx) == 3 && Math.abs(dz) == 3) || dx == 0 || dz == 0;
					set(x, top + 1, z, merlon ? CUT : s("sandstone_wall"));
				}
			}
		}
		// porta, glifo vanilla (laranja com azul no meio) e seteiras
		int front = cz - 3;
		air(cx, 0, front, cx, 1, front);
		set(cx, 2, front, CHISELED);
		int g = top - 8;
		set(cx, g + 6, front, ORANGE);
		set(cx - 1, g + 5, front, ORANGE);
		set(cx + 1, g + 5, front, ORANGE);
		set(cx - 1, g + 4, front, ORANGE);
		set(cx, g + 4, front, BLUE);
		set(cx + 1, g + 4, front, ORANGE);
		set(cx - 1, g + 3, front, ORANGE);
		set(cx + 1, g + 3, front, ORANGE);
		set(cx, g + 2, front, ORANGE);
		set(cx, g + 1, front, ORANGE);
		set(cx, g, front, ORANGE);
		for (int y : new int[] {5, 11}) {
			set(cx - 3, y, cz, VPlan.AIR);
			set(cx + 3, y, cz, VPlan.AIR);
		}
		// escada de mao encostada na parede de tras e alcapao aberto no teto
		for (int y = 0; y <= top; y++) {
			set(cx, y, cz + 2, ladder(N));
		}
		set(cx, top + 1, cz + 2, VPlan.AIR);
		set(cx - 2, 0, cz - 2, "minecraft:decorated_pot");
		set(cx + 2, 0, cz + 1, "minecraft:decorated_pot");
		if (chance(0.5)) {
			p.loot(cx + 2, 0, cz - 2, s("barrel", "facing=up"), STORAGE);
		}
		sandPiles.add(new int[] {cx - 2, 0, cz + 1});
	}

	/** Pilone de entrada entre as torres, com portico de colunas e friso de terracota. */
	private void gate() {
		fill(-5, 0, -21, 5, 8, -19, CUT);
		for (int x = -5; x <= 5; x++) {
			for (int z = -21; z <= -19; z++) {
				p.set(x, -1, z, SANDSTONE, VPlan.FILL_DOWN);
			}
			set(x, 9, -21, stairs("minecraft:smooth_sandstone_stairs", S));
			set(x, 9, -20, SMOOTH);
			set(x, 9, -19, SMOOTH);
			set(x, 7, -21, x % 2 == 0 ? ORANGE : BLUE);
		}
		set(0, 7, -21, CHISELED);
		for (int y = 0; y <= 6; y++) {
			set(-5, y, -21, y % 3 == 0 ? CHISELED : CUT);
			set(5, y, -21, y % 3 == 0 ? CHISELED : CUT);
		}
		// portico: duas colunas e a cobertura
		for (int x : new int[] {-3, 3}) {
			p.set(x, -1, -24, SANDSTONE, VPlan.FILL_DOWN);
			set(x, 0, -24, CHISELED);
			for (int y = 1; y <= 4; y++) {
				set(x, y, -24, y == 2 ? ORANGE : CUT);
			}
			set(x, 5, -24, CHISELED);
		}
		for (int x = -4; x <= 4; x++) {
			for (int z = -24; z <= -22; z++) {
				set(x, 6, z, CUT);
			}
			set(x, 7, -24, slab("minecraft:smooth_sandstone_slab"));
			set(x, 6, -24, x == 0 ? BLUE : x % 2 == 0 ? ORANGE : CUT);
		}
		for (int x = -4; x <= 4; x++) {
			for (int z = -24; z <= -22; z++) {
				p.set(x, -1, z, SMOOTH, VPlan.FILL_DOWN);
			}
		}
		// passagem
		air(-1, 0, -24, 1, 5, -22);
		air(-1, 0, -21, 1, 3, -8);
		set(-2, 4, -21, CHISELED);
		set(2, 4, -21, CHISELED);
		fill(-1, 4, -21, 1, 4, -21, CHISELED);
		set(0, 5, -21, BLUE);
		for (int z = -20; z <= -9; z++) {
			set(-2, 0, z, CUT);
			set(2, 0, z, CUT);
			if (z % 3 == 0) {
				set(-2, 2, z, CHISELED);
				set(2, 2, z, CHISELED);
			}
			set(0, -1, z, z % 2 == 0 ? SMOOTH : CUT);
		}
		set(-1, 0, -10, SAND);
		sandPiles.add(new int[] {1, 0, -12});
	}

	private void avenue() {
		int length = between(10, 16);
		for (int z = -25; z >= -25 - length; z--) {
			for (int x = -3; x <= 3; x++) {
				String floor = Math.abs(x) == 3 ? CUT : chance(0.15) ? SANDSTONE : SMOOTH;
				p.set(x, -1, z, floor, VPlan.FILL_DOWN);
				for (int y = 0; y <= 6; y++) {
					p.setIfAbsent(x, y, z, VPlan.AIR);
				}
			}
			if (chance(0.25)) {
				set(between(-2, 2), 0, z, SAND);
			}
		}
		for (int z = -27; z >= -25 - length; z -= 4) {
			for (int x : new int[] {-5, 5}) {
				p.set(x, -1, z, SANDSTONE, VPlan.FILL_DOWN);
				set(x, 0, z, CHISELED);
				int h = chance(0.3) ? between(1, 2) : between(4, 5);
				for (int y = 1; y <= h; y++) {
					set(x, y, z, CUT);
				}
				if (h >= 4) {
					set(x, h + 1, z, slab("minecraft:smooth_sandstone_slab"));
					set(x, h - 1, z, ORANGE);
				} else {
					// pedacos caidos ao lado
					int side = x > 0 ? 1 : -1;
					for (int k = 1; k <= 3; k++) {
						p.setIfAbsent(x + side * k, -1, z, SANDSTONE);
						set(x + side * k, 0, z, k == 3 ? slab("minecraft:smooth_sandstone_slab") : CUT);
					}
				}
			}
		}
	}

	// --- interior ---

	private void hall() {
		air(-7, 0, -7, 7, 7, 7);
		for (int i = -7; i <= 7; i++) {
			for (int y = 0; y <= 7; y++) {
				String face = glyph(i, y);
				set(-8, y, i, face);
				set(8, y, i, face);
				set(i, y, -8, face);
				set(i, y, 8, face);
			}
		}
		// rosa-dos-ventos (azul no centro) e piso
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				int ax = Math.abs(x);
				int az = Math.abs(z);
				String floor;
				if (ax <= 3 && az <= 3) {
					floor = (ax == az || x == 0 || z == 0) ? ORANGE : CUT;
				} else if (ax == 4 || az == 4) {
					floor = SMOOTH;
				} else {
					floor = (x + z) % 2 == 0 ? SANDSTONE : SMOOTH;
				}
				set(x, -1, z, floor);
			}
		}
		set(0, -1, 0, BLUE);
		// caminho de escavacao classico: arenito ate o teto da camara do tesouro
		for (int y = -10; y <= -2; y++) {
			set(0, y, 0, SANDSTONE);
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int y = 0; y <= 7; y++) {
					set(4 * sx, y, 4 * sz, y == 0 || y == 7 ? CHISELED : y == 3 ? ORANGE : CUT);
				}
				set(2 * sx, 7, 2 * sz, s("lantern", "hanging=true"));
				set(7 * sx, 7, 7 * sz, "minecraft:cobweb");
				if (chance(0.7)) {
					set(6 * sx, 0, 7 * sz, SAND);
					sandPiles.add(new int[] {7 * sx, 0, 7 * sz});
				}
			}
		}
		set(-7, 0, -6, "minecraft:decorated_pot");
		set(7, 0, 6, "minecraft:decorated_pot");
		// portas laterais e de tras
		air(-9, 0, -1, -8, 2, 1);
		air(8, 0, -1, 9, 2, 1);
		air(-1, 0, 8, 1, 3, 10);
		air(-1, 0, -8, 1, 3, -8);
		set(-8, 3, 0, CHISELED);
		set(8, 3, 0, CHISELED);
	}

	/** Face das paredes do salao: rodape, glifos (laranja/azul/laranja sobre cinzelado) a cada 4 blocos e friso no alto. */
	private static String glyph(int i, int y) {
		if (y == 0 || y == 7) {
			return CUT;
		}
		if (y == 6) {
			return i % 2 == 0 ? ORANGE : CUT;
		}
		if (Math.floorMod(i, 4) == 2) {
			return switch (y) {
				case 1 -> CHISELED;
				case 2, 4 -> ORANGE;
				case 3 -> BLUE;
				default -> CUT;
			};
		}
		return y == 5 ? SMOOTH : SANDSTONE;
	}

	/** Deposito: potes, barris, bau e montes de areia (com areia suspeita). */
	private void westGallery() {
		air(-15, 0, -8, -10, 3, 8);
		for (int z = -8; z <= 8; z++) {
			set(-15, -1, z, z % 2 == 0 ? CUT : SANDSTONE);
			if (z % 2 == 0 && Math.abs(z) != 0) {
				set(-15, 0, z, chance(0.8) ? "minecraft:decorated_pot" : SAND);
			}
			if (z % 4 == 0) {
				set(-16, 2, z, ORANGE);
			}
		}
		p.loot(-15, 0, 0, stairsChest(E), STORAGE);
		p.loot(-10, 0, -8, s("barrel", "facing=up"), STORAGE);
		p.loot(-10, 0, 8, s("barrel", "facing=up"), STORAGE);
		set(-11, 0, -8, s("barrel", "facing=up"));
		set(-10, 1, -8, s("barrel", "facing=up"));
		for (int i = 0; i < 4; i++) {
			sandPiles.add(new int[] {between(-14, -11), 0, between(-6, 6)});
		}
		set(-15, 3, -8, "minecraft:cobweb");
		set(-15, 3, 8, "minecraft:cobweb");
		set(-12, 3, 0, s("lantern", "hanging=true"));
	}

	private static String stairsChest(String facing) {
		return s("chest", "facing=" + facing);
	}

	/** Sala de embalsamar e biblioteca. */
	private void eastGallery() {
		air(10, 0, -8, 15, 3, 8);
		for (int z = -8; z <= 8; z++) {
			set(15, -1, z, CUT);
			for (int y = 0; y <= 2; y++) {
				if (Math.abs(z) % 4 == 2) {
					set(16, y, z, CUT);
				} else if (chance(0.8)) {
					set(16, y, z, chance(0.15) ? s("chiseled_bookshelf", "facing=west") : "minecraft:bookshelf");
				}
			}
		}
		set(13, 0, -6, s("lectern", "facing=west"));
		set(11, 0, 7, "minecraft:cauldron");
		set(12, 0, 7, "minecraft:brewing_stand");
		set(15, 0, -8, CUT);
		set(15, 1, -8, s("candle", "candles=2", "lit=false"));
		// mesa de embalsamar
		fill(12, 0, -1, 13, 0, 1, s("smooth_sandstone_slab", "type=top"));
		set(12, 1, 0, s("white_carpet"));
		set(13, 1, 0, s("white_carpet"));
		set(14, 3, 0, s("lantern", "hanging=true"));
		set(10, 3, 8, "minecraft:cobweb");
		set(15, 3, -7, "minecraft:cobweb");
	}

	/** Camara superior sobre o salao, por escada de mao. */
	private void upperChamber() {
		for (int y = 0; y <= 8; y++) {
			set(5, y, 7, ladder(N));
		}
		air(2, 9, 3, 6, 11, 7);
		for (int x = 2; x <= 6; x++) {
			for (int z = 3; z <= 7; z++) {
				if (!(x == 5 && z == 7)) {
					set(x, 8, z, (x + z) % 2 == 0 ? ORANGE : CUT);
				}
			}
		}
		p.loot(2, 9, 5, stairsChest(E), STORAGE);
		set(2, 9, 3, "minecraft:decorated_pot");
		set(6, 9, 3, "minecraft:decorated_pot");
		set(4, 11, 5, s("lantern", "hanging=true"));
		set(4, 12, 5, CUT);
		set(3, 11, 7, "minecraft:cobweb");
		sandPiles.add(new int[] {3, 9, 3});
	}

	/** Galeria dos ancestrais (estatuas em nichos) e escadaria que desce ao nivel das tumbas. */
	private void descent() {
		air(-12, 0, 11, 2, 3, 13);
		for (int x = -12; x <= 2; x++) {
			set(x, -1, 12, x % 2 == 0 ? ORANGE : CUT);
			if (x % 3 == 0 && x <= -3) {
				// estatua num nicho: corpo de arenito cortado, cabeca cinzelada
				set(x, 0, 14, CUT);
				set(x, 1, 14, CUT);
				set(x, 2, 14, CHISELED);
				set(x, 0, 10, s("candle", "candles=" + between(1, 4), "lit=false"));
				set(x, -1, 10, CUT);
			}
		}
		set(-12, 3, 11, "minecraft:cobweb");
		for (int i = 0; i <= 9; i++) {
			int x = 3 + i;
			room(x, -i, 11, x, Math.min(3, -i + 3), 13, SANDSTONE);
			for (int z = 11; z <= 13; z++) {
				set(x, -1 - i, z, stairs("minecraft:sandstone_stairs", W));
			}
		}
	}

	/** Nivel das tumbas: patamar, corredor de nichos funerarios, tumba do farao e galeria ate a camara do tesouro. */
	private void lowerLevel() {
		roomMixed(13, -9, 9, 17, -6, 15, SANDSTONE, SANDSTONE, CUT);
		// corredor dos nichos
		roomMixed(14, -9, -12, 16, -6, 8, SANDSTONE, SANDSTONE, CUT, SMOOTH);
		for (int z = -11; z <= 7; z += 3) {
			for (int side : new int[] {13, 17}) {
				int back = side == 13 ? 12 : 18;
				p.setIfAbsent(back, -9, z, SANDSTONE);
				p.setIfAbsent(back, -8, z, SANDSTONE);
				p.setIfAbsent(side, -10, z, SANDSTONE);
				if (side == 17 && z >= -5 && z <= 5) {
					continue;
				}
				int roll = r.nextInt(4);
				if (roll == 0) {
					p.suspicious(side, -9, z, "minecraft:suspicious_sand", DIG);
				} else if (roll == 1) {
					set(side, -9, z, s("bone_block", "axis=x"));
				} else {
					set(side, -9, z, s("candle", "candles=" + between(1, 3), "lit=false"));
				}
				set(side, -8, z, chance(0.5) ? "minecraft:cobweb" : VPlan.AIR);
				set(side, -7, z, CHISELED);
			}
		}
		for (int z = -12; z <= 8; z++) {
			set(15, -10, z, z % 2 == 0 ? CUT : SMOOTH);
		}
		set(15, -6, -6, s("soul_lantern", "hanging=true"));
		set(15, -6, 4, s("soul_lantern", "hanging=true"));
		tomb();
		// galeria ate a camara do tesouro
		roomMixed(-4, -9, -12, 13, -6, -10, SANDSTONE, SANDSTONE, CUT);
		for (int x = -4; x <= 13; x++) {
			set(x, -10, -11, x % 3 == 0 ? ORANGE : CUT);
			if (x % 4 == 0) {
				set(x, -7, -13, BLUE);
			}
		}
		for (int i = 1; i <= 5; i++) {
			int z = -10 + i;
			room(-4, -9 - i, z, -3, Math.min(-6, -6 - i + 1), z, SANDSTONE);
			set(-4, -10 - i, z, stairs("minecraft:sandstone_stairs", N));
			set(-3, -10 - i, z, stairs("minecraft:sandstone_stairs", N));
		}
	}

	private void tomb() {
		roomMixed(18, -9, -5, 26, -4, 5, SANDSTONE, CUT);
		air(17, -9, -1, 17, -7, 1);
		set(17, -6, 0, CHISELED);
		for (int x = 18; x <= 26; x++) {
			for (int z = -5; z <= 5; z++) {
				set(x, -10, z, Math.abs(z) <= 1 ? ORANGE : (x + z) % 2 == 0 ? CUT : SMOOTH);
			}
			set(x, -5, -6, x % 2 == 0 ? ORANGE : BLUE);
			set(x, -5, 6, x % 2 == 0 ? ORANGE : BLUE);
		}
		for (int x : new int[] {20, 24}) {
			for (int sz : new int[] {-3, 3}) {
				for (int y = -9; y <= -4; y++) {
					set(x, y, sz, y == -9 || y == -4 ? CHISELED : CUT);
				}
			}
			set(x, -4, 0, s("soul_lantern", "hanging=true"));
		}
		// sarcofago
		fill(21, -9, -1, 23, -9, 1, CUT);
		fill(21, -8, -1, 23, -8, 1, s("smooth_sandstone_slab"));
		fill(21, -8, 0, 23, -8, 0, ORANGE);
		set(23, -8, 0, "minecraft:gold_block");
		set(24, -9, 0, CHISELED);
		p.loot(26, -9, 0, stairsChest(W), TOMB);
		p.mark(MARKER_TOMB, 26, -9, 0);
		set(26, -9, -2, "minecraft:decorated_pot");
		set(26, -9, 2, "minecraft:decorated_pot");
		set(25, -9, -3, s("candle", "candles=3", "lit=false"));
		boolean north = chance(0.5);
		int sz = north ? -4 : 4;
		p.spawner(19, -9, sz, "minecraft:husk");
		set(18, -9, sz, "minecraft:cobweb");
		set(19, -8, sz, "minecraft:cobweb");
		set(26, -4, sz, "minecraft:cobweb");
	}

	/** A camara vanilla: placa de pressao no centro, 9 TNT embaixo e 4 baus nas paredes. */
	private void treasureChamber() {
		room(-4, -14, -4, 4, -11, 4, CUT);
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z));
				set(x, -15, z, ring == 4 ? CUT : ring == 3 ? ORANGE : (x + z) % 2 == 0 ? SANDSTONE : SMOOTH);
				set(x, -17, z, SANDSTONE);
			}
		}
		fill(-5, -16, -5, 5, -16, 5, SANDSTONE);
		fill(-1, -16, -1, 1, -16, 1, "minecraft:tnt");
		set(0, -15, 0, CHISELED);
		set(0, -14, 0, "minecraft:stone_pressure_plate");
		p.mark(MARKER_PLATE, 0, -14, 0);
		p.loot(0, -14, -5, stairsChest(S), TREASURE);
		p.loot(0, -14, 5, stairsChest(N), TREASURE);
		p.loot(-5, -14, 0, stairsChest(E), TREASURE);
		p.loot(5, -14, 0, stairsChest(W), TREASURE);
		for (int i = -4; i <= 4; i++) {
			for (int[] w : new int[][] {{i, -5}, {i, 5}, {-5, i}, {5, i}}) {
				if (p.get(w[0], -12, w[1]) != null && !BlockTraits.isAir(p.get(w[0], -12, w[1]))) {
					set(w[0], -12, w[1], i % 2 == 0 ? ORANGE : CUT);
				}
			}
		}
		set(0, -13, -5, BLUE);
		set(0, -13, 5, BLUE);
		set(-5, -13, 0, BLUE);
		set(5, -13, 0, BLUE);
		set(3, -11, 3, "minecraft:cobweb");
		set(-3, -11, 3, "minecraft:cobweb");
	}

	// --- arredores e areia ---

	private void surroundings() {
		sunDisc();
		for (int sx : new int[] {-9, 9}) {
			sphinx(sx, -31);
		}
		for (int x = -R - 1; x <= R + 1; x++) {
			for (int z = -R - 1; z <= R + 1; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) != R + 1 || p.get(x, 0, z) != null || p.get(x, -1, z) != null) {
					continue;
				}
				if (chance(0.3)) {
					set(x, 0, z, SAND);
					if (chance(0.3)) {
						set(x, 1, z, SAND);
					}
				}
			}
		}
		for (int i = 0; i < 16; i++) {
			int x = between(-32, 32);
			int z = between(-32, 32);
			if (Math.max(Math.abs(x), Math.abs(z)) < 23 || (Math.abs(x) <= 7 && z < -20)) {
				continue;
			}
			if (p.get(x, -1, z) != null || p.get(x, 0, z) != null) {
				continue;
			}
			set(x, -1, z, SAND);
			if (chance(0.3) && p.get(x + 1, 0, z) == null && p.get(x - 1, 0, z) == null && p.get(x, 0, z + 1) == null && p.get(x, 0, z - 1) == null) {
				int h = between(1, 3);
				for (int y = 0; y < h; y++) {
					set(x, y, z, "minecraft:cactus");
				}
			} else {
				set(x, 0, z, "minecraft:dead_bush");
			}
		}
	}

	/** Disco solar de terracota na face da frente, acima do portal. */
	private void sunDisc() {
		for (int y = 10; y <= 14; y++) {
			int z = -(R - y);
			int half = 2 - Math.abs(y - 12);
			for (int x = -half; x <= half; x++) {
				set(x, y, z, (x == 0 && y == 12) ? "minecraft:gold_block" : Math.abs(x) == half ? ORANGE : BLUE);
			}
		}
		for (int x = -4; x <= 4; x++) {
			if (Math.abs(x) >= 3) {
				set(x, 12, -(R - 12), ORANGE);
			}
		}
	}

	/** Esfinge deitada ao lado da avenida, olhando para a avenida (-z). */
	private void sphinx(int cx, int cz) {
		for (int z = cz; z <= cz + 5; z++) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				p.set(x, -1, z, SANDSTONE, VPlan.FILL_DOWN);
				set(x, 0, z, CUT);
				if (z >= cz + 1) {
					set(x, 1, z, z == cz + 5 ? stairs("minecraft:sandstone_stairs", N) : SMOOTH);
				}
			}
		}
		// patas na frente, cabeca e toucado
		set(cx - 1, 1, cz, slab("minecraft:smooth_sandstone_slab"));
		set(cx + 1, 1, cz, slab("minecraft:smooth_sandstone_slab"));
		set(cx, 1, cz, CUT);
		set(cx, 2, cz + 1, CHISELED);
		set(cx, 3, cz + 1, CUT);
		set(cx - 1, 2, cz + 1, stairs("minecraft:sandstone_stairs", E));
		set(cx + 1, 2, cz + 1, stairs("minecraft:sandstone_stairs", W));
		set(cx, 2, cz + 2, ORANGE);
		set(cx, 4, cz + 1, slab("minecraft:smooth_sandstone_slab"));
		set(cx, 2, cz, stairs("minecraft:sandstone_stairs", S));
		if (chance(0.4)) {
			set(cx, 3, cz + 1, VPlan.AIR);
			set(cx, 4, cz + 1, VPlan.AIR);
			p.setIfAbsent(cx + 2, 0, cz + 2, CUT);
		}
		for (int z = cz - 1; z <= cz + 6; z++) {
			for (int x = cx - 2; x <= cx + 2; x++) {
				for (int y = 0; y <= 4; y++) {
					p.setIfAbsent(x, y, z, VPlan.AIR);
				}
			}
		}
	}

	/** Montes de areia soltos; 6 a 10 deles escondem areia suspeita. */
	private void archaeology() {
		int suspicious = between(5, 7);
		java.util.Collections.shuffle(sandPiles, r);
		for (int[] pile : sandPiles) {
			int x = pile[0];
			int y = pile[1];
			int z = pile[2];
			if (!empty(x, y, z)) {
				continue;
			}
			if (suspicious > 0) {
				p.suspicious(x, y, z, "minecraft:suspicious_sand", DIG);
				suspicious--;
			} else {
				set(x, y, z, SAND);
			}
		}
	}
}
