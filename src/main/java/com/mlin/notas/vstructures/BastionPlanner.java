package com.mlin.notas.vstructures;

import java.util.Random;

/**
 * Bastiao em ruinas refeito, com os 4 tipos vanilla como variantes ({@link #TYPES}; sorteado na colocacao como o
 * vanilla sorteia o {@code bastion/starts}):
 * <ol start="0">
 *   <li>{@link #HOUSING} alojamentos: muralha quadrada de 49x49 com adarve e 4 baluartes de canto, 4 casas de dois
 *       andares (sala, dormitorio, terraco ligado ao adarve) em volta de um patio em cruz com fonte de lava (coluna de
 *       basalto com quedas de lava numa bacia);</li>
 *   <li>{@link #STABLES} estabulos de hoglins: patio murado com corredor central e 10 baias com hoglins, galeria em U
 *       sobre pilares de basalto, rampa larga ate ela, quedas de lava na parede do fundo e baus {@code hoglin_stable};</li>
 *   <li>{@link #TREASURE} sala do tesouro: cubo de 41x41 com o lago de lava no fundo, o nucleo de magma (coluna de
 *       basalto polido e magma com quedas de lava) segurando o estrado de blackstone dourado com blocos de ouro e o bau
 *       {@code bastion_treasure}, 4 pontes ate a galeria alta, escada no canto e 2 geradores de cubo de magma;</li>
 *   <li>{@link #BRIDGE} ponte: torre de entrada com escada, ponte em arcos sobre um fosso de lava (quedas de lava dos
 *       lados), bau {@code bastion_bridge} e o bloco de ouro escondido no portao, ate a fortaleza de dois andares com
 *       adarve.</li>
 * </ol>
 * Todos com blackstone polido/rachado, blackstone dourado, basalto, correntes, piglins, piglins brutos (e hoglins nos
 * estabulos) nascendo com a estrutura. Chao em y=0 sobre um patio de blackstone que desce ate o terreno; a colocacao
 * ({@link NetherSiting}) poe o y=0 na mediana do chao. Logica pura.
 *
 * <p>Referencias (so forma, camadas e paleta; nada copiado): os 4 tipos de bastiao vanilla (Minecraft Wiki), Better
 * Bastions (layouts procedurais com muralhas, baluartes de canto, portao, quedas de lava e guarnicao de piglins),
 * builds de "piglin castle" e "bastion remnant" da comunidade (Planet Minecraft/galerias: torres em talude de
 * blackstone com faixas de blackstone dourado, nucleo de magma iluminando o tesouro).
 */
public final class BastionPlanner extends PlanBuilder {
	public static final int TYPES = 4;
	public static final int HOUSING = 0;
	public static final int STABLES = 1;
	public static final int TREASURE = 2;
	public static final int BRIDGE = 3;

	public static final String LOOT_TREASURE = "minecraft:chests/bastion_treasure";
	public static final String LOOT_OTHER = "minecraft:chests/bastion_other";
	public static final String LOOT_BRIDGE = "minecraft:chests/bastion_bridge";
	public static final String LOOT_STABLE = "minecraft:chests/bastion_hoglin_stable";
	public static final String MARKER_TREASURE = "treasure";
	public static final String MARKER_CORE = "core";
	public static final String MARKER_GOLD = "hidden_gold";
	public static final String MARKER_VIEW = "view";

	private static final String BRICKS = "minecraft:polished_blackstone_bricks";
	private static final String CRACKED = "minecraft:cracked_polished_blackstone_bricks";
	private static final String STONE = "minecraft:blackstone";
	private static final String POLISHED = "minecraft:polished_blackstone";
	private static final String CHISELED = "minecraft:chiseled_polished_blackstone";
	private static final String GILDED = "minecraft:gilded_blackstone";
	private static final String GOLD = "minecraft:gold_block";
	private static final String BASALT = "minecraft:polished_basalt";
	private static final String MAGMA = "minecraft:magma_block";
	private static final String BRICK_STAIRS = "minecraft:polished_blackstone_brick_stairs";
	private static final String STAIRS = "minecraft:blackstone_stairs";
	private static final String SLAB = "minecraft:polished_blackstone_brick_slab";
	private static final String BRICK_WALL = "minecraft:polished_blackstone_brick_wall";
	private static final String WALL = "minecraft:blackstone_wall";
	private static final String CHAIN = "minecraft:iron_chain";
	private static final String LAVA = "minecraft:lava";
	private static final String FALL = "minecraft:lava[level=8]";
	private static final String SOUL_SOIL = "minecraft:soul_soil";

	private final int type;

	private BastionPlanner(Random random, int type) {
		super(new VPlan(false), random);
		this.type = type;
	}

	public static VPlan plan(long seed, int variant) {
		int type = Math.floorMod(variant, TYPES);
		BastionPlanner b = new BastionPlanner(new Random(seed * 4099L + 31 + type), type);
		b.build();
		return b.p;
	}

	private void build() {
		switch (type) {
			case STABLES -> stables();
			case TREASURE -> treasure();
			case BRIDGE -> bridge();
			default -> housing();
		}
		p.removeFloating();
	}

	// ------------------------------------------------------------------ ajudantes

	private String mix() {
		double d = r.nextDouble();
		if (d < 0.58) {
			return BRICKS;
		}
		if (d < 0.76) {
			return CRACKED;
		}
		if (d < 0.95) {
			return STONE;
		}
		return GILDED;
	}

	private String ground() {
		double d = r.nextDouble();
		return d < 0.55 ? STONE : d < 0.8 ? POLISHED : d < 0.93 ? BASALT : GILDED;
	}

	private void mixBox(int x1, int y1, int z1, int x2, int y2, int z2) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, mix());
				}
			}
		}
	}

	/** Patio: chao em y=-1 descendo ate o terreno e ar ate {@code h} (corta morro). */
	private void site(int x1, int z1, int x2, int z2, int h) {
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				p.set(x, -1, z, ground(), VPlan.FILL_DOWN);
				air(x, 0, z, x, h, z);
			}
		}
	}

	/** Muro macico com faixa de blackstone dourado e base descendo ate o chao. */
	private void wall(int x1, int z1, int x2, int z2, int y1, int y2) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
				p.set(x, y1 - 1, z, STONE, VPlan.FILL_DOWN);
				for (int y = y1; y <= y2; y++) {
					set(x, y, z, y == y1 + 4 && r.nextInt(3) == 0 ? GILDED : y == y1 ? POLISHED : mix());
				}
			}
		}
	}

	/** Merlons alternados em {@code y} no contorno. */
	private void merlons(int x1, int z1, int x2, int z2, int y) {
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				if (x != x1 && x != x2 && z != z1 && z != z2) {
					continue;
				}
				if ((x + z) % 2 == 0) {
					set(x, y, z, mix());
				} else if (chance(0.3)) {
					set(x, y, z, BRICK_WALL);
				}
			}
		}
	}

	/** Queda de lava: fonte presa num nicho em {@code top} e lava caindo ate {@code bottom} (que deve ter lava ou chao embaixo). */
	private void lavaFall(int x, int z, int bottom, int top) {
		set(x, top, z, LAVA);
		for (int y = bottom; y < top; y++) {
			set(x, y, z, FALL);
		}
	}

	private void piglin(double x, double y, double z) {
		p.entity("minecraft:piglin", x + 0.5, y, z + 0.5, "");
	}

	private void brute(double x, double y, double z) {
		p.entity("minecraft:piglin_brute", x + 0.5, y, z + 0.5, "");
	}

	private void hoglin(double x, double y, double z) {
		p.entity("minecraft:hoglin", x + 0.5, y, z + 0.5, "");
	}

	private void chest(int x, int y, int z, String facing, String table) {
		p.loot(x, y, z, s("chest", "facing=" + facing), table);
	}

	/** Escada reta de {@code n} degraus a partir de {@code (x, y, z)} subindo na direcao {@code dir}, largura em {@code wx/wz}. */
	private void flight(int x, int y, int z, String dir, int n, int width) {
		int dx = dx(dir);
		int dz = dz(dir);
		for (int i = 0; i < n; i++) {
			for (int w = 0; w < width; w++) {
				int px = x + dx * i + (dx == 0 ? w : 0);
				int pz = z + dz * i + (dz == 0 ? w : 0);
				set(px, y + i, pz, stairs(BRICK_STAIRS, dir));
				for (int yy = -1; yy < y + i; yy++) {
					set(px, yy, pz, mix());
				}
				air(px, y + i + 1, pz, px, y + i + 4, pz);
			}
		}
	}

	/** Torre de canto (baluarte) macica com topo de ameias. */
	private void tower(int cx, int cz, int half, int top) {
		for (int x = cx - half; x <= cx + half; x++) {
			for (int z = cz - half; z <= cz + half; z++) {
				boolean corner = Math.abs(x - cx) == half && Math.abs(z - cz) == half;
				if (corner) {
					continue;
				}
				p.set(x, -1, z, STONE, VPlan.FILL_DOWN);
				for (int y = 0; y <= top; y++) {
					set(x, y, z, y % 6 == 5 ? (r.nextInt(3) == 0 ? GILDED : POLISHED) : mix());
				}
			}
		}
		for (int x = cx - half; x <= cx + half; x++) {
			for (int z = cz - half; z <= cz + half; z++) {
				int ax = Math.abs(x - cx);
				int az = Math.abs(z - cz);
				if ((ax == half && az < half) || (az == half && ax < half) || (ax == half - 1 && az == half - 1)) {
					if ((x + z) % 2 == 0) {
						set(x, top + 1, z, mix());
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ 0: alojamentos

	private void housing() {
		int h = 24;
		site(-h, -h, h, h, 16);
		// muralha (3 de espessura, adarve em y=10)
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				if (edge >= h - 2) {
					wall(x, z, x, z, 0, 9);
					if (edge == h) {
						set(x, 10, z, mix());
						if ((x + z) % 2 == 0) {
							set(x, 11, z, mix());
						}
					} else {
						set(x, 9, z, edge == h - 2 ? BRICKS : POLISHED);
					}
				}
			}
		}
		// portao sul com o bloco de ouro escondido em cima
		air(-2, 0, h - 2, 2, 5, h);
		for (int z = h - 2; z <= h; z++) {
			set(-2, 5, z, stairsTop(BRICK_STAIRS, E));
			set(2, 5, z, stairsTop(BRICK_STAIRS, W));
		}
		set(0, 7, h - 1, GOLD);
		p.mark(MARKER_GOLD, 0, 7, h - 1);
		for (int x = -3; x <= 3; x += 6) {
			set(x, 6, h + 1, CHAIN);
			set(x, 7, h + 1, CHAIN);
			set(x, 8, h + 1, CHAIN);
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				tower(sx * (h - 1), sz * (h - 1), 4, 15);
			}
		}
		// escadas para o adarve (dentro, junto do muro leste e oeste)
		flight(h - 4, 0, 6, N, 9, 2);
		flight(-h + 3, 0, -6, S, 9, 2);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				house(sx, sz);
			}
		}
		fountain();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, h + 3);
		p.mark(MARKER_VIEW, 0, 0, 0);
		brute(0, 0, 8);
		brute(-8, 0, 0);
		for (int i = 0; i < 4; i++) {
			piglin(i < 2 ? 5 : -5, 0, i % 2 == 0 ? 5 : -5);
		}
		piglin(h - 1, 10, 0);
		piglin(-h + 1, 10, 4);
	}

	/** Casa de 2 andares (13x13) num quadrante, porta virada para o patio. */
	private void house(int sx, int sz) {
		int x1 = sx > 0 ? 6 : -18;
		int x2 = x1 + 12;
		int z1 = sz > 0 ? 6 : -18;
		int z2 = z1 + 12;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
				p.set(x, -1, z, POLISHED, VPlan.FILL_DOWN);
				for (int y = 0; y <= 11; y++) {
					if (edge) {
						set(x, y, z, y == 5 || y == 11 ? POLISHED : mix());
					} else if (y == 5 || y == 11) {
						set(x, y, z, y == 11 ? mix() : POLISHED);
					} else {
						set(x, y, z, VPlan.AIR);
					}
				}
			}
		}
		merlons(x1, z1, x2, z2, 12);
		// porta para o patio (lado de x perto do centro) e janelas
		int doorX = sx > 0 ? x1 : x2;
		int midZ = (z1 + z2) / 2;
		air(doorX, 0, midZ - 1, doorX, 3, midZ + 1);
		set(doorX, 3, midZ - 1, stairsTop(BRICK_STAIRS, S));
		set(doorX, 3, midZ + 1, stairsTop(BRICK_STAIRS, N));
		for (int z = z1 + 2; z <= z2 - 2; z += 4) {
			int outX = sx > 0 ? x2 : x1;
			set(outX, 7, z, BRICK_WALL);
			set(outX, 8, z, BRICK_WALL);
		}
		// escada interna ate o andar de cima e ate o terraco
		int stairX = sx > 0 ? x2 - 2 : x1 + 1;
		flightInside(stairX, 0, z2 - 1, N, 5);
		air(stairX, 5, z2 - 5, stairX + 1, 5, z2 - 3);
		int upperX = sx > 0 ? x1 + 1 : x2 - 2;
		flightInside(upperX, 6, z1 + 5, S, 5);
		air(upperX, 11, z1 + 7, upperX + 1, 11, z1 + 9);
		// moveis: baus, ouro, correntes com lanternas de alma
		int otherX = sx > 0 ? x1 + 2 : x2 - 2;
		chest(otherX, 0, z1 + 1, S, LOOT_OTHER);
		chest(otherX, 6, z2 - 1, N, LOOT_OTHER);
		set(otherX + (sx > 0 ? 1 : -1), 0, z1 + 1, GOLD);
		set((x1 + x2) / 2, 4, midZ, CHAIN);
		set((x1 + x2) / 2, 3, midZ, s("soul_lantern", "hanging=true"));
		set((x1 + x2) / 2, 10, midZ, s("soul_lantern", "hanging=true"));
		piglin((x1 + x2) / 2.0, 0, midZ - 3);
		piglin((x1 + x2) / 2.0 - 1, 6, midZ);
		// passarela do terraco ate o adarve
		int wz1 = sz > 0 ? z2 : z1;
		int toWall = sz > 0 ? 21 : -21;
		for (int z = Math.min(wz1, toWall); z <= Math.max(wz1, toWall); z++) {
			for (int x = (x1 + x2) / 2 - 1; x <= (x1 + x2) / 2 + 1; x++) {
				if (empty(x, 11, z)) {
					set(x, 11, z, POLISHED);
				}
			}
		}
		air((x1 + x2) / 2 - 1, 12, wz1, (x1 + x2) / 2 + 1, 12, wz1);
	}

	/** Escada de 5 degraus, 2 de largura, dentro de uma casa. */
	private void flightInside(int x, int y, int z, String dir, int n) {
		for (int i = 0; i < n; i++) {
			for (int w = 0; w <= 1; w++) {
				int zz = z + dz(dir) * i;
				set(x + w, y + i, zz, stairs(BRICK_STAIRS, dir));
				for (int yy = y; yy < y + i; yy++) {
					set(x + w, yy, zz, mix());
				}
			}
		}
	}

	/** Fonte de lava do patio: coluna de basalto com quedas de lava numa bacia cercada. */
	private void fountain() {
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				if (edge == 3) {
					set(x, 0, z, slab(SLAB));
				} else {
					set(x, -1, z, LAVA);
					p.set(x, -2, z, STONE, VPlan.FILL_DOWN);
				}
			}
		}
		for (int y = -1; y <= 8; y++) {
			set(0, y, 0, y == 8 ? CHISELED : BASALT);
		}
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			set(d[0], 8, d[1], stairsTop(BRICK_STAIRS, d[0] > 0 ? W : d[0] < 0 ? E : d[1] > 0 ? N : S));
			set(d[0], 9, d[1], mix());
			set(d[0] * 2, 9, d[1] * 2, mix());
			set(d[0] * 2 + d[1], 9, d[1] * 2 + d[0], mix());
			set(d[0] * 2 - d[1], 9, d[1] * 2 - d[0], mix());
			lavaFall(d[0] * 2, d[1] * 2, 0, 8);
			set(d[0] * 2, 9, d[1] * 2, mix());
		}
		set(0, 9, 0, GILDED);
		set(0, 10, 0, GOLD);
	}

	// ------------------------------------------------------------------ 1: estabulos

	private void stables() {
		int hx = 18;
		int hz = 23;
		site(-hx, -hz, hx, hz, 16);
		for (int x = -hx; x <= hx; x++) {
			for (int z = -hz; z <= hz; z++) {
				if (Math.abs(x) >= hx - 1 || Math.abs(z) >= hz - 1) {
					wall(x, z, x, z, 0, 13);
				}
			}
		}
		merlons(-hx, -hz, hx, hz, 14);
		tower(-hx, -hz, 3, 17);
		tower(hx, -hz, 3, 17);
		tower(-hx, hz, 3, 17);
		tower(hx, hz, 3, 17);
		// portao sul
		air(-2, 0, hz - 1, 2, 5, hz);
		set(-2, 5, hz, stairsTop(BRICK_STAIRS, E));
		set(2, 5, hz, stairsTop(BRICK_STAIRS, W));
		set(0, 8, hz, GOLD);
		p.mark(MARKER_GOLD, 0, 8, hz);
		// corredor central e baias
		for (int z = -hz + 2; z <= hz - 2; z++) {
			for (int x = -3; x <= 3; x++) {
				set(x, -1, z, x == 0 ? GILDED : POLISHED);
			}
		}
		int[] starts = {-20, -12, -4, 4, 12};
		int pen = 0;
		for (int z0 : starts) {
			for (int side = -1; side <= 1; side += 2) {
				int xa = side * 4;
				int xb = side * 11;
				for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++) {
					for (int z = z0; z <= z0 + 6; z++) {
						boolean border = x == xa || x == xb || z == z0 || z == z0 + 6;
						set(x, -1, z, border ? POLISHED : (r.nextInt(3) == 0 ? SOUL_SOIL : STONE));
						if (border && Math.abs(x) <= 11) {
							set(x, 0, z, BRICK_WALL);
						}
					}
				}
				if (pen % 3 != 2) {
					hoglin((xa + xb) / 2.0, 0, z0 + 2);
					hoglin((xa + xb) / 2.0 + side, 0, z0 + 4);
				}
				// fungo carmesim no canto (comida dos hoglins)
				set(xb - side, 0, z0 + 1, s("crimson_fungus"));
				set(xb - side, -1, z0 + 1, s("crimson_nylium"));
				pen++;
			}
		}
		// galeria em U a y=8 (pes em 9) sobre pilares
		for (int x = -hx + 2; x <= hx - 2; x++) {
			for (int z = -hz + 2; z <= hz - 2; z++) {
				boolean east = x >= 13;
				boolean west = x <= -13;
				boolean north = z <= -18;
				if (!(east || west || north)) {
					continue;
				}
				if (east && z >= 12) {
					continue; // rampa
				}
				set(x, 8, z, (x + z) % 5 == 0 ? GILDED : POLISHED);
				if (Math.abs(x) == 13 && !north || z == -18 && Math.abs(x) < 13) {
					set(x, 9, z, BRICK_WALL);
				}
			}
		}
		for (int z = -15; z <= 10; z += 5) {
			for (int x : new int[] {-13, 13}) {
				for (int y = -1; y <= 7; y++) {
					set(x, y, z, BASALT);
				}
				set(x, 9, z, BRICKS);
				set(x, 10, z, s("soul_lantern"));
			}
		}
		for (int x = -10; x <= 10; x += 5) {
			for (int y = -1; y <= 7; y++) {
				set(x, y, -18, BASALT);
			}
		}
		// rampa larga no lado leste (sobe para o norte)
		for (int i = 0; i < 8; i++) {
			for (int x = 13; x <= 16; x++) {
				set(x, i, 19 - i, stairs(BRICK_STAIRS, N));
				for (int y = -1; y < i; y++) {
					set(x, y, 19 - i, mix());
				}
			}
		}
		set(12, 8, 11, BRICK_WALL);
		for (int z = 12; z <= 19; z++) {
			set(12, Math.max(0, 19 - z + 1), z, BRICK_WALL);
		}
		// quedas de lava na parede do fundo, numa vala cercada
		for (int x = -9; x <= 9; x += 6) {
			set(x, 7, -hz + 2, mix());
			set(x, 8, -hz + 2, LAVA);
			lavaFall(x, -hz + 2, 9, 13);
		}
		chest(15, 9, -20, W, LOOT_STABLE);
		chest(-15, 9, 0, E, LOOT_STABLE);
		chest(-2, 0, -21, S, LOOT_OTHER);
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, hz + 3);
		p.mark(MARKER_VIEW, 0, 0, 0);
		brute(0, 0, -15);
		piglin(15, 9, -5);
		piglin(-15, 9, -10);
		piglin(0, 9, -20);
		piglin(1, 0, 10);
		piglin(-1, 0, -2);
	}

	// ------------------------------------------------------------------ 2: sala do tesouro

	private void treasure() {
		int h = 20;
		int top = 27;
		site(-h - 1, -h - 1, h + 1, h + 1, top + 2);
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				if (edge >= h - 2) {
					wall(x, z, x, z, 0, top);
				}
			}
		}
		merlons(-h, -h, h, h, top + 1);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				tower(sx * h, sz * h, 3, top + 3);
			}
		}
		// faixas de blackstone dourado e janelas de muro na fachada
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) == h && Math.min(Math.abs(x), Math.abs(z)) < h - 3) {
					set(x, 12, z, (x + z) % 3 == 0 ? GILDED : POLISHED);
					set(x, 20, z, (x + z) % 4 == 0 ? GILDED : POLISHED);
				}
			}
		}
		// anel de baixo (chao em y=-1) e lago de lava no meio
		for (int x = -17; x <= 17; x++) {
			for (int z = -17; z <= 17; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				if (edge >= 15) {
					set(x, -1, z, edge == 15 ? BRICKS : POLISHED);
				} else {
					set(x, -1, z, LAVA);
					p.set(x, -2, z, STONE, VPlan.FILL_DOWN);
				}
				if (edge == 15) {
					set(x, 0, z, (x + z) % 2 == 0 ? BRICK_WALL : SLAB);
				}
			}
		}
		// portao sul
		air(-2, 0, h - 2, 2, 4, h);
		air(-1, 0, 15, 1, 0, 15);
		set(-2, 4, h - 2, stairsTop(BRICK_STAIRS, E));
		set(2, 4, h - 2, stairsTop(BRICK_STAIRS, W));
		set(0, 6, h, GOLD);
		p.mark(MARKER_GOLD, 0, 6, h);
		// nucleo de magma
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
				boolean face = Math.abs(x) == 3 || Math.abs(z) == 3;
				p.set(x, -2, z, BASALT, VPlan.FILL_DOWN);
				for (int y = -1; y <= 11; y++) {
					set(x, y, z, corner ? BASALT : face ? (y % 4 == 3 ? GILDED : MAGMA) : STONE);
				}
			}
		}
		p.mark(MARKER_CORE, 0, 5, 3);
		for (int[] d : new int[][] {{0, 4}, {0, -4}, {4, 0}, {-4, 0}}) {
			lavaFall(d[0], d[1], 0, 11);
		}
		// estrado do tesouro em y=12 (pes em 13)
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				set(x, 12, z, edge == 5 ? POLISHED : (x + z) % 2 == 0 ? GILDED : STONE);
				if (edge == 5 && Math.abs(x) > 1 && Math.abs(z) > 1) {
					set(x, 13, z, BRICK_WALL);
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				set(sx * 4, 13, sz * 4, GOLD);
				set(sx * 4, 14, sz * 4, GILDED);
			}
		}
		set(0, 13, 0, GILDED);
		chest(0, 14, 0, S, LOOT_TREASURE);
		p.mark(MARKER_TREASURE, 0, 14, 0);
		set(0, 13, -1, stairs(BRICK_STAIRS, S));
		// correntes do teto ate o estrado
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int y = 15; y <= top - 1; y++) {
					set(sx * 3, y, sz * 3, CHAIN);
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				set(sx * 3, top, sz * 3, mix());
				for (int k = 4; k <= h - 3; k++) {
					set(sx * 3, top, sz * k, mix());
				}
			}
		}
		// galeria alta (y=12) junto do muro, com vao em cima da escada
		for (int x = -17; x <= 17; x++) {
			for (int z = -17; z <= 17; z++) {
				int edge = Math.max(Math.abs(x), Math.abs(z));
				if (edge < 15 || (x >= 15 && z >= 3 && z <= 14)) {
					continue;
				}
				set(x, 12, z, edge == 15 ? BRICKS : POLISHED);
				if (edge == 15 && !(x >= 14 && z >= 2 && z <= 15)) {
					set(x, 13, z, BRICK_WALL);
				}
			}
		}
		// pontes do estrado ate a galeria, com pilar no meio descendo ao lago
		for (int[] d : new int[][] {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
			for (int k = 6; k <= 15; k++) {
				for (int w = -2; w <= 2; w++) {
					int x = d[0] * k + d[1] * w;
					int z = d[1] * k + d[0] * w;
					set(x, 12, z, Math.abs(w) == 2 ? POLISHED : BRICKS);
					if (Math.abs(w) == 2) {
						set(x, 13, z, k % 3 == 0 ? BRICK_WALL : WALL);
					}
					if (Math.abs(w) <= 1) {
						air(x, 13, z, x, 15, z);
					}
				}
			}
			for (int w = -1; w <= 1; w++) {
				int x = d[0] * 10 + d[1] * w;
				int z = d[1] * 10 + d[0] * w;
				for (int y = -1; y <= 11; y++) {
					set(x, y, z, y == 11 ? stairsTop(BRICK_STAIRS, d[0] > 0 ? W : d[0] < 0 ? E : d[1] > 0 ? N : S) : BASALT);
				}
				p.set(x, -2, z, BASALT, VPlan.FILL_DOWN);
			}
		}
		// abre a passagem da galeria para cada ponte
		for (int[] d : new int[][] {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
			for (int w = -1; w <= 1; w++) {
				int x = d[0] * 15 + d[1] * w;
				int z = d[1] * 15 + d[0] * w;
				air(x, 13, z, x, 13, z);
			}
		}
		// escada do anel de baixo ate a galeria (leste, subindo para o norte)
		for (int i = 0; i < 12; i++) {
			for (int x = 15; x <= 17; x++) {
				set(x, i, 14 - i, stairs(BRICK_STAIRS, N));
				for (int y = -1; y < i; y++) {
					set(x, y, 14 - i, mix());
				}
				air(x, i + 1, 14 - i, x, i + 4, 14 - i);
			}
		}
		// geradores de cubo de magma nos cantos de baixo
		p.spawner(-16, 0, -16, "minecraft:magma_cube");
		p.spawner(16, 0, -16, "minecraft:magma_cube");
		chest(-16, 0, 16, E, LOOT_OTHER);
		chest(-16, 13, -16, S, LOOT_OTHER);
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, h + 3);
		p.mark(MARKER_VIEW, 0, 13, 15);
		brute(2, 13, 2);
		brute(-2, 13, -2);
		piglin(-16, 13, 0);
		piglin(0, 13, -16);
		piglin(-16, 0, -5);
		piglin(16, 13, -8);
	}

	// ------------------------------------------------------------------ 3: ponte

	private void bridge() {
		int deck = 8;
		site(-14, -32, 14, 32, 22);
		// fosso de lava sob a ponte
		for (int x = -9; x <= 9; x++) {
			for (int z = -4; z <= 16; z++) {
				boolean rim = Math.abs(x) == 9 || z == -4 || z == 16;
				if (rim) {
					set(x, 0, z, (x + z) % 2 == 0 ? BRICK_WALL : POLISHED);
				} else {
					set(x, -1, z, LAVA);
					p.set(x, -2, z, STONE, VPlan.FILL_DOWN);
				}
			}
		}
		// torre de entrada (11x11, z 18..28), escada interna ate o tabuleiro
		int g1 = 18;
		int g2 = 28;
		for (int x = -5; x <= 5; x++) {
			for (int z = g1; z <= g2; z++) {
				boolean edge = Math.abs(x) == 5 || z == g1 || z == g2;
				p.set(x, -1, z, POLISHED, VPlan.FILL_DOWN);
				for (int y = 0; y <= 16; y++) {
					if (edge) {
						set(x, y, z, y == 8 || y == 16 ? POLISHED : mix());
					} else if (y == 7 || y == 16) {
						set(x, y, z, mix());
					} else {
						set(x, y, z, VPlan.AIR);
					}
				}
			}
		}
		merlons(-5, g1, 5, g2, 17);
		air(-1, 0, g2, 1, 3, g2);
		set(0, 5, g2, GOLD);
		p.mark(MARKER_GOLD, 0, 5, g2);
		for (int i = 0; i < 7; i++) {
			for (int x = 2; x <= 4; x++) {
				set(x, i, g2 - 1 - i, stairs(BRICK_STAIRS, N));
				for (int y = 0; y < i; y++) {
					set(x, y, g2 - 1 - i, mix());
				}
			}
		}
		air(2, 7, g1 + 3, 4, 7, g2 - 4);
		for (int z = g1 + 3; z <= g2 - 4; z++) {
			set(1, 8, z, BRICK_WALL);
		}
		chest(-4, 8, g1 + 1, E, LOOT_BRIDGE);
		chest(-4, 0, g2 - 1, E, LOOT_OTHER);
		air(-1, deck, g1, 1, deck + 3, g1);
		// tabuleiro com arcos e quedas de lava dos lados
		for (int z = -6; z < g1; z++) {
			for (int x = -3; x <= 3; x++) {
				set(x, deck - 1, z, Math.abs(x) == 3 ? POLISHED : (z % 4 == 0 && x == 0 ? GILDED : BRICKS));
				if (Math.abs(x) <= 2) {
					air(x, deck, z, x, deck + 3, z);
				}
			}
			set(-3, deck, z, z % 3 == 0 ? BRICK_WALL : WALL);
			set(3, deck, z, z % 3 == 0 ? BRICK_WALL : WALL);
			set(-3, deck - 2, z, mix());
			set(3, deck - 2, z, mix());
		}
		for (int zp : new int[] {-2, 6, 14}) {
			for (int z = zp - 1; z <= zp + 1; z++) {
				for (int x = -3; x <= 3; x++) {
					p.set(x, -2, z, BASALT, VPlan.FILL_DOWN);
					for (int y = -1; y <= deck - 2; y++) {
						set(x, y, z, Math.abs(x) == 3 && Math.abs(z - zp) == 1 ? BASALT : mix());
					}
				}
			}
			for (int x = -3; x <= 3; x++) {
				set(x, deck - 3, zp + 2, stairsTop(BRICK_STAIRS, N));
				set(x, deck - 3, zp - 2, stairsTop(BRICK_STAIRS, S));
			}
		}
		for (int z : new int[] {2, 10}) {
			for (int x : new int[] {-4, 4}) {
				set(x, deck - 1, z, mix());
				set(x, deck, z, mix());
				lavaFall(x, z, 0, deck - 1);
			}
		}
		// sacada com bau no meio da ponte
		for (int x = 4; x <= 6; x++) {
			for (int z = 5; z <= 7; z++) {
				set(x, deck - 1, z, POLISHED);
				if (x == 6 || z != 6) {
					set(x, deck, z, BRICK_WALL);
				}
			}
		}
		set(4, deck, 6, VPlan.AIR);
		set(3, deck, 6, VPlan.AIR);
		chest(5, deck, 6, W, LOOT_BRIDGE);
		// fortaleza do outro lado (25x25, z -31..-7): terreo, salao em y=8 e adarve em y=16
		int k1 = -31;
		int k2 = -7;
		for (int x = -12; x <= 12; x++) {
			for (int z = k1; z <= k2; z++) {
				boolean edge = Math.abs(x) == 12 || z == k1 || z == k2;
				p.set(x, -1, z, POLISHED, VPlan.FILL_DOWN);
				for (int y = 0; y <= 16; y++) {
					if (edge) {
						set(x, y, z, y == 8 || y == 16 ? POLISHED : (y == 12 && (x + z) % 3 == 0) ? GILDED : mix());
					} else if (y == 7 || y == 16) {
						set(x, y, z, y == 16 ? POLISHED : mix());
					} else {
						set(x, y, z, VPlan.AIR);
					}
				}
			}
		}
		merlons(-12, k1, 12, k2, 17);
		for (int sx = -1; sx <= 1; sx += 2) {
			tower(sx * 12, k1, 3, 21);
			tower(sx * 12, k2, 3, 21);
		}
		air(-1, deck, k2, 1, deck + 3, k2);
		air(-1, 0, k1, 1, 3, k1);
		// escada do salao ao terreo e ao adarve
		for (int i = 0; i < 7; i++) {
			for (int x = -11; x <= -10; x++) {
				set(x, i, k1 + 2 + i, stairs(BRICK_STAIRS, S));
				for (int y = 0; y < i; y++) {
					set(x, y, k1 + 2 + i, mix());
				}
			}
		}
		air(-11, 7, k1 + 4, -10, 7, k1 + 8);
		for (int i = 0; i < 8; i++) {
			for (int x = 10; x <= 11; x++) {
				set(x, 8 + i, k1 + 2 + i, stairs(BRICK_STAIRS, S));
				for (int y = 8; y < 8 + i; y++) {
					set(x, y, k1 + 2 + i, mix());
				}
			}
		}
		air(10, 16, k1 + 7, 11, 16, k1 + 9);
		for (int z = k1 + 4; z <= k1 + 8; z++) {
			set(-9, 8, z, BRICK_WALL);
		}
		set(-11, 8, k1 + 3, BRICK_WALL);
		set(-10, 8, k1 + 3, BRICK_WALL);
		for (int z = k1 + 7; z <= k1 + 9; z++) {
			set(9, 17, z, BRICK_WALL);
		}
		set(10, 17, k1 + 6, BRICK_WALL);
		set(11, 17, k1 + 6, BRICK_WALL);
		// salao: trono de ouro, colunas, baus
		for (int x : new int[] {-6, 6}) {
			for (int z : new int[] {-24, -14}) {
				for (int y = 8; y <= 15; y++) {
					set(x, y, z, y == 8 || y == 15 ? GILDED : BASALT);
				}
			}
		}
		set(0, 8, k1 + 1, GOLD);
		set(0, 9, k1 + 2, stairs(BRICK_STAIRS, N));
		set(0, 8, k1 + 2, GILDED);
		set(0, 9, k1 + 1, GILDED);
		chest(-8, 8, k1 + 1, S, LOOT_OTHER);
		chest(6, 0, k1 + 1, S, LOOT_OTHER);
		for (int z = k1 + 4; z <= k2 - 4; z += 6) {
			set(0, 15, z, CHAIN);
			set(0, 14, z, s("soul_lantern", "hanging=true"));
		}
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, g2 + 3);
		p.mark(MARKER_VIEW, 0, deck, 0);
		brute(0, 8, -20);
		piglin(-2, deck, 4);
		piglin(1, deck, 12);
		piglin(4, 8, -25);
		piglin(-3, 0, -20);
		piglin(0, 8, g1 + 4);
		piglin(-8, 17, -20);
	}
}
