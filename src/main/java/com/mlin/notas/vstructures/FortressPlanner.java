package com.mlin.notas.vstructures;

import java.util.Random;

/**
 * Fortaleza do Nether refeita: em vez da rede vanilla de pontes finas, uma cidadela de tijolos do Nether com
 * <ul>
 *   <li>torre de menagem central 27x27 (base em talude, 4 torreoes de canto, pilastras): sala do trono no terreo
 *       (estrado, trono, corredor de tijolo vermelho, mesa de guerra, colunas), andar do arsenal e terraco com ameias e
 *       a primeira plataforma de blazes;</li>
 *   <li>4 pontes de 7 de largura (sai uma de cada porta) sobre pilares com arcos que descem ate o chao ou o mar de
 *       lava ({@link VPlan#FILL_DEEP}): norte e sul abertas com guarda-corpo de cerca e postes com lanterna de alma;
 *       leste e oeste cobertas (salas de patrulha dos esqueletos wither, com janelas, nichos com baus e lanternas);</li>
 *   <li>ponta norte: torre dos blazes (plataforma no topo com o gerador num estrado e anel de cercas); ponta oeste:
 *       torre do poco de lava (bacia de lava cercada); ponta leste: estufa de fungo do Nether (canteiros de areia das
 *       almas, escadaria central ate um balcao com baus); ponta sul: portao com arco e escadaria ate o chao.</li>
 * </ul>
 * O plano tem o chao em y=0 e as pontes em y=8 ({@link #DECK}); a colocacao ({@link NetherSiting}) poe o y=0 perto da
 * mediana do chao, os pilares descem ate achar rocha e o ar do plano abre tunel onde o morro e mais alto. Entrada pela
 * escadaria sul. Logica pura.
 *
 * <p>Referencias (so forma, camadas e paleta; nada copiado): Refined Fortress (Planet Minecraft: tijolo vermelho e
 * cinzelado como destaque, lava como luz), YUNG's Better Nether Fortresses (rede de pontes + cidadela central + salas
 * de lava, spawns vanilla mantidos), Toni's Better Nether Fortresses (cidadela murada com torre de menagem, sala do
 * trono, pontes em arco, estufa de fungo e alojamentos), Dungeons and Taverns Nether Fortress Overhaul e o
 * "Nether Fortress remodelling" do Sketchfab (pilares grossos com talude e arcos sob as pontes).
 */
public final class FortressPlanner extends PlanBuilder {
	public static final String LOOT = "minecraft:chests/nether_bridge";
	public static final String BLAZE = "minecraft:blaze";
	public static final int DECK = 8;
	static final int KEEP = 13;
	static final int ARM_END = 50;
	public static final String MARKER_THRONE = "throne";
	public static final String MARKER_KEEP_SPAWNER = "keep_spawner";
	public static final String MARKER_TOWER_SPAWNER = "tower_spawner";
	public static final String MARKER_GARDEN = "garden";
	public static final String MARKER_GATE = "gate";

	private static final String[] DIR = {N, E, S, W};
	private static final int[][] FWD = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	private static final String BRICKS = "minecraft:nether_bricks";
	private static final String CRACKED = "minecraft:cracked_nether_bricks";
	private static final String CHISELED = "minecraft:chiseled_nether_bricks";
	private static final String RED = "minecraft:red_nether_bricks";
	private static final String FENCE = "minecraft:nether_brick_fence";
	private static final String WALL = "minecraft:nether_brick_wall";
	private static final String RED_WALL = "minecraft:red_nether_brick_wall";
	private static final String STAIRS = "minecraft:nether_brick_stairs";
	private static final String RED_STAIRS = "minecraft:red_nether_brick_stairs";
	private static final String SLAB = "minecraft:nether_brick_slab";
	private static final String LANTERN = "minecraft:soul_lantern";
	private static final String HANGING = "minecraft:soul_lantern[hanging=true]";

	private FortressPlanner(Random random) {
		super(new VPlan(false), random);
	}

	public static VPlan plan(long seed, int variant) {
		FortressPlanner b = new FortressPlanner(new Random(seed * 7919L + 101));
		b.build();
		return b.p;
	}

	private void build() {
		keep();
		for (int arm = 0; arm < 4; arm++) {
			bridge(arm, arm == 1 || arm == 3);
		}
		blazeTower(0);
		wartHall(1);
		gate(2);
		lavaTower(3);
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, 64);
	}

	// ------------------------------------------------------------------ ajudantes

	private String brick() {
		double d = r.nextDouble();
		return d < 0.1 ? CRACKED : BRICKS;
	}

	/** Coordenadas no referencial de um braco: {@code s} = distancia do centro, {@code perp} = lado (positivo a direita). */
	private static int ax(int arm, int s, int perp) {
		int[] f = FWD[arm];
		int[] r = FWD[(arm + 1) % 4];
		return f[0] * s + r[0] * perp;
	}

	private static int az(int arm, int s, int perp) {
		int[] f = FWD[arm];
		int[] r = FWD[(arm + 1) % 4];
		return f[1] * s + r[1] * perp;
	}

	private void at(int arm, int s, int y, int perp, String state) {
		p.set(ax(arm, s, perp), y, az(arm, s, perp), state);
	}

	private void at(int arm, int s, int y, int perp, String state, int flag) {
		p.set(ax(arm, s, perp), y, az(arm, s, perp), state, flag);
	}

	private String getA(int arm, int s, int y, int perp) {
		return p.get(ax(arm, s, perp), y, az(arm, s, perp));
	}

	private void fillA(int arm, int s1, int y1, int p1, int s2, int y2, int p2, String state) {
		for (int s = Math.min(s1, s2); s <= Math.max(s1, s2); s++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int q = Math.min(p1, p2); q <= Math.max(p1, p2); q++) {
					at(arm, s, y, q, state);
				}
			}
		}
	}

	private void airA(int arm, int s1, int y1, int p1, int s2, int y2, int p2) {
		fillA(arm, s1, y1, p1, s2, y2, p2, VPlan.AIR);
	}

	private void bricksA(int arm, int s1, int y1, int p1, int s2, int y2, int p2) {
		for (int s = Math.min(s1, s2); s <= Math.max(s1, s2); s++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int q = Math.min(p1, p2); q <= Math.max(p1, p2); q++) {
					at(arm, s, y, q, brick());
				}
			}
		}
	}

	private static String out(int arm) {
		return DIR[arm];
	}

	private static String in(int arm) {
		return opposite(DIR[arm]);
	}

	private static String right(int arm) {
		return cw(DIR[arm]);
	}

	/** Lado (para fora do corredor) de quem esta em {@code perp}. */
	private static String side(int arm, int perp) {
		return perp > 0 ? right(arm) : opposite(right(arm));
	}

	/**
	 * Base macica de {@code (s1..s2) x (p1..p2)} de y=0 ate {@code top}: borda e uma grade interna descem ate o chao
	 * ({@link VPlan#FILL_DEEP}), com faixa de tijolo vermelho no topo.
	 */
	private void plinth(int arm, int s1, int s2, int p1, int p2, int top) {
		for (int s = s1; s <= s2; s++) {
			for (int q = p1; q <= p2; q++) {
				boolean edge = s == s1 || s == s2 || q == p1 || q == p2;
				for (int y = 0; y <= top; y++) {
					at(arm, s, y, q, edge && y == top - 1 ? RED : brick());
				}
				if (edge || (s - s1) % 4 == 0 && (q - p1) % 4 == 0) {
					at(arm, s, 0, q, brick(), VPlan.FILL_DEEP);
				}
			}
		}
	}

	/** Ameias: parapeito em {@code y} e merlons alternados em {@code y + 1} no contorno do retangulo. */
	private void parapet(int arm, int s1, int s2, int p1, int p2, int y) {
		for (int s = s1; s <= s2; s++) {
			for (int q = p1; q <= p2; q++) {
				if (s != s1 && s != s2 && q != p1 && q != p2) {
					continue;
				}
				at(arm, s, y, q, brick());
				if ((s + q) % 2 == 0) {
					at(arm, s, y + 1, q, BRICKS);
				} else if (chance(0.5)) {
					at(arm, s, y + 1, q, FENCE);
				}
			}
		}
	}

	// ------------------------------------------------------------------ torre de menagem

	private void keep() {
		int k = KEEP;
		// base em talude: anel de 3 de altura com degraus subindo para a parede
		for (int arm = 0; arm < 4; arm++) {
			for (int q = -k - 1; q <= k + 1; q++) {
				for (int y = 0; y <= 2; y++) {
					at(arm, k + 1, y, q, brick(), y == 0 ? VPlan.FILL_DEEP : 0);
				}
				at(arm, k + 1, 3, q, stairs(STAIRS, in(arm)));
			}
		}
		for (int x = -k; x <= k; x++) {
			for (int z = -k; z <= k; z++) {
				boolean edge = Math.abs(x) == k || Math.abs(z) == k;
				for (int y = 0; y <= 6; y++) {
					set(x, y, z, edge && y == 6 ? RED : brick());
				}
				if (edge || (x % 4 == 0 && z % 4 == 0)) {
					p.set(x, 0, z, brick(), VPlan.FILL_DEEP);
				}
				set(x, 7, z, Math.abs(x) <= 1 && !edge ? RED : brick());
				if (edge) {
					for (int y = 8; y <= 21; y++) {
						set(x, y, z, y == 14 || y == 21 ? RED : brick());
					}
				} else {
					air(x, 8, z, x, 13, z);
					set(x, 14, z, brick());
					air(x, 15, z, x, 20, z);
					set(x, 21, z, brick());
				}
			}
		}
		parapet(0, -k, k, -k, k, 22);
		for (int arm = 0; arm < 4; arm++) {
			// porta com arco (3 de largura, 4 de altura)
			airA(arm, k, 8, -1, k, 11, 1);
			at(arm, k, 11, -1, stairsTop(STAIRS, side(arm, 1)));
			at(arm, k, 11, 1, stairsTop(STAIRS, side(arm, -1)));
			at(arm, k, 12, 0, CHISELED);
			// pilastras e janelas
			for (int q : new int[] {-7, 7}) {
				for (int y = 4; y <= 20; y++) {
					at(arm, k + 1, y, q, y % 7 == 0 ? CHISELED : brick());
				}
				at(arm, k + 1, 21, q, stairs(STAIRS, in(arm)));
			}
			for (int q = -10; q <= 10; q += 4) {
				if (Math.abs(q) > 2) {
					at(arm, k, 9, q, FENCE);
					at(arm, k, 10, q, FENCE);
				}
				at(arm, k, 16, q, FENCE);
				at(arm, k, 17, q, FENCE);
			}
		}
		// torreoes de canto (5x5, ate y=25, ameias)
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int x = sx * k + dx;
						int z = sz * k + dz;
						for (int y = 0; y <= 25; y++) {
							set(x, y, z, y == 7 || y == 14 || y == 21 ? RED : brick());
						}
						if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
							p.set(x, 0, z, brick(), VPlan.FILL_DEEP);
							set(x, 26, z, (dx + dz) % 2 == 0 ? BRICKS : FENCE);
						}
					}
				}
				set(sx * k, 26, sz * k, LANTERN);
			}
		}
		warHall();
		throneRoom();
		roof();
	}

	private void warHall() {
		// salao de guerra no terreo (o trono fica no andar de cima: as 4 portas ocupam as paredes daqui)
		for (int z = -10; z <= 10; z += 4) {
			if (Math.abs(z) > 2) {
				set(-9, 8, z, FENCE);
				set(-9, 9, z, FENCE);
			}
		}
		// colunas
		for (int x : new int[] {-6, 6}) {
			for (int z : new int[] {-4, 4}) {
				for (int y = 8; y <= 13; y++) {
					set(x, y, z, y == 8 || y == 13 ? CHISELED : RED);
				}
			}
		}
		// mesa de guerra
		fill(-2, 8, 4, 2, 8, 6, slabTop(SLAB));
		set(0, 9, 5, s("lectern", "facing=south"));
		for (int x = -3; x <= 3; x += 6) {
			for (int z = 3; z <= 7; z += 4) {
				set(x, 8, z, stairs(STAIRS, x < 0 ? E : W));
			}
		}
		for (int z = -6; z <= 10; z += 8) {
			set(0, 13, z, HANGING);
		}
		p.loot(-12, 8, 10, s("chest", "facing=east"), LOOT);
		p.loot(12, 8, -4, s("chest", "facing=west"), LOOT);
		p.entity("minecraft:wither_skeleton", -3.5, 8, 0.5, "");
		p.entity("minecraft:wither_skeleton", 3.5, 8, 9.5, "");
		// escada para o andar de cima (leste, subindo para o norte)
		for (int i = 0; i <= 5; i++) {
			for (int x = 11; x <= 12; x++) {
				set(x, 8 + i, 7 - i, stairs(STAIRS, N));
				for (int y = 8; y < 8 + i; y++) {
					set(x, y, 7 - i, BRICKS);
				}
			}
		}
		air(11, 14, 2, 12, 14, 5);
		for (int z = 2; z <= 5; z++) {
			set(10, 15, z, FENCE);
		}
		set(11, 15, 6, FENCE);
		set(12, 15, 6, FENCE);
	}

	private void throneRoom() {
		// sala do trono: estrado ao norte com degrau, trono, encosto e colunas cinzeladas; tapete vermelho e braseiro
		fill(-5, 15, -12, 5, 15, -9, RED);
		for (int x = -5; x <= 5; x++) {
			set(x, 15, -8, stairs(RED_STAIRS, N));
		}
		set(0, 16, -11, stairs(RED_STAIRS, N));
		set(-1, 16, -11, RED_WALL);
		set(1, 16, -11, RED_WALL);
		fill(-1, 16, -12, 1, 19, -12, RED);
		set(0, 17, -12, CHISELED);
		set(0, 19, -12, CHISELED);
		set(0, 20, -12, stairsTop(RED_STAIRS, S));
		p.mark(MARKER_THRONE, 0, 16, -11);
		for (int x : new int[] {-4, 4}) {
			fill(x, 16, -12, x, 20, -12, CHISELED);
			set(x, 20, -10, HANGING);
		}
		fill(-1, 14, -7, 1, 14, 9, RED);
		set(0, 14, 4, s("soul_sand"));
		set(0, 15, 4, s("soul_campfire", "lit=true", "facing=north"));
		p.loot(-12, 15, 10, s("chest", "facing=east"), LOOT);
		p.loot(12, 15, -10, s("chest", "facing=west"), LOOT);
		for (int x = -6; x <= 6; x += 6) {
			set(x, 20, -6, HANGING);
			set(x, 20, 6, HANGING);
		}
		// escada para o terraco (oeste, subindo para o sul)
		for (int i = 0; i <= 5; i++) {
			for (int x = -12; x <= -11; x++) {
				set(x, 15 + i, -7 + i, stairs(STAIRS, S));
				for (int y = 15; y < 15 + i; y++) {
					set(x, y, -7 + i, BRICKS);
				}
			}
		}
		air(-12, 21, -5, -11, 21, -2);
		for (int z = -5; z <= -2; z++) {
			set(-10, 22, z, FENCE);
		}
		set(-12, 22, -6, FENCE);
		set(-11, 22, -6, FENCE);
		p.entity("minecraft:wither_skeleton", 4.5, 15, -3.5, "");
	}

	private void roof() {
		// plataforma de blazes no terraco
		fill(-1, 22, -1, 1, 22, 1, RED);
		p.spawner(0, 23, 0, BLAZE);
		p.mark(MARKER_KEEP_SPAWNER, 0, 23, 0);
		blazeRing(0, 0, 22, 3);
		for (int x = -8; x <= 8; x += 16) {
			for (int z = -8; z <= 8; z += 16) {
				set(x, 22, z, BRICKS);
				set(x, 23, z, LANTERN);
			}
		}
	}

	/** Anel de cercas (raio {@code rad}) em volta do gerador, com vaos nos 4 lados. */
	private void blazeRing(int cx, int cz, int y, int rad) {
		for (int dx = -rad; dx <= rad; dx++) {
			for (int dz = -rad; dz <= rad; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != rad || dx == 0 || dz == 0) {
					continue;
				}
				if (empty(cx + dx, y, cz + dz)) {
					set(cx + dx, y, cz + dz, FENCE);
				}
			}
		}
	}

	// ------------------------------------------------------------------ pontes

	private void bridge(int arm, boolean covered) {
		int[] piers = {22, 32, 42};
		for (int s = KEEP + 1; s <= ARM_END; s++) {
			// tunel (onde o morro passa da ponte) e tabuleiro
			airA(arm, s, DECK, -3, s, DECK + (covered ? 5 : 3), 3);
			for (int q = -3; q <= 3; q++) {
				at(arm, s, DECK - 1, q, Math.abs(q) == 3 ? RED : brick());
			}
			at(arm, s, DECK - 2, -3, brick());
			at(arm, s, DECK - 2, 3, brick());
			// arcos entre os pilares (a menagem e a ponta tambem seguram)
			int gap = Integer.MAX_VALUE;
			for (int pier : piers) {
				gap = Math.min(gap, Math.abs(s - pier) - 1);
			}
			gap = Math.max(0, Math.min(gap, Math.min(s - KEEP - 2, ARM_END - s)));
			for (int q = -3; q <= 3; q++) {
				if (gap == 0) {
					at(arm, s, DECK - 2, q, brick());
					at(arm, s, DECK - 3, q, brick());
					at(arm, s, DECK - 4, q, stairsTop(STAIRS, nearestPier(arm, s, piers)));
				} else if (gap == 1) {
					at(arm, s, DECK - 2, q, brick());
					at(arm, s, DECK - 3, q, stairsTop(STAIRS, nearestPier(arm, s, piers)));
				} else if (gap == 2) {
					at(arm, s, DECK - 2, q, stairsTop(STAIRS, nearestPier(arm, s, piers)));
				}
			}
			if (covered) {
				coveredSlice(arm, s);
			} else {
				openSlice(arm, s);
			}
		}
		for (int pier : piers) {
			pier(arm, pier);
		}
		if (covered) {
			for (int s : new int[] {24, 40}) {
				alcove(arm, s, s == 24 ? 1 : -1);
			}
			for (int s = 18; s <= ARM_END - 2; s += 8) {
				at(arm, s, DECK + 4, 0, HANGING);
			}
			for (int s : new int[] {20, 30, 44}) {
				p.entity("minecraft:wither_skeleton", ax(arm, s, 0) + 0.5, DECK, az(arm, s, 0) + 0.5, "");
			}
		}
	}

	/** Direcao (no mundo) do pilar ou apoio mais proximo de {@code s}: o degrau de arco encosta nele. */
	private String nearestPier(int arm, int s, int[] piers) {
		int best = KEEP;
		for (int pier : piers) {
			if (Math.abs(s - pier) < Math.abs(s - best)) {
				best = pier;
			}
		}
		if (Math.abs(s - (ARM_END + 1)) < Math.abs(s - best)) {
			best = ARM_END + 1;
		}
		return best < s ? in(arm) : out(arm);
	}

	private void pier(int arm, int s) {
		for (int ds = -1; ds <= 1; ds++) {
			for (int q = -3; q <= 3; q++) {
				for (int y = 0; y <= DECK - 2; y++) {
					at(arm, s + ds, y, q, y % 5 == 4 && (Math.abs(q) == 3 || ds != 0) ? CHISELED : brick());
				}
				at(arm, s + ds, 0, q, brick(), VPlan.FILL_DEEP);
			}
		}
		// contrafortes em talude nos dois lados
		for (int q : new int[] {-4, 4}) {
			for (int ds = -1; ds <= 1; ds++) {
				at(arm, s + ds, 0, q, brick(), VPlan.FILL_DEEP);
				at(arm, s + ds, 1, q, brick());
				at(arm, s + ds, 2, q, stairs(STAIRS, q > 0 ? opposite(right(arm)) : right(arm)));
			}
		}
	}

	private void openSlice(int arm, int s) {
		for (int q : new int[] {-3, 3}) {
			if (s % 6 == 0) {
				at(arm, s, DECK, q, BRICKS);
				at(arm, s, DECK + 1, q, LANTERN);
			} else if (!chance(0.07)) {
				at(arm, s, DECK, q, FENCE);
			}
		}
	}

	private void coveredSlice(int arm, int s) {
		for (int q : new int[] {-3, 3}) {
			for (int y = DECK; y <= DECK + 4; y++) {
				boolean window = s % 4 == 0 && (y == DECK + 1 || y == DECK + 2);
				at(arm, s, y, q, window ? FENCE : s % 4 == 2 && y == DECK ? CHISELED : brick());
			}
			at(arm, s, DECK + 6, q, s % 2 == 0 ? BRICKS : slab(SLAB));
		}
		for (int q = -3; q <= 3; q++) {
			at(arm, s, DECK + 5, q, Math.abs(q) == 3 ? RED : brick());
		}
	}

	/** Nicho com bau ao lado do corredor coberto ({@code sign} = lado). */
	private void alcove(int arm, int s, int sign) {
		for (int ds = -2; ds <= 2; ds++) {
			for (int k = 3; k <= 6; k++) {
				int q = sign * k;
				for (int y = DECK - 1; y <= DECK + 3; y++) {
					boolean inside = Math.abs(ds) <= 1 && k <= 5 && y >= DECK && y <= DECK + 2;
					at(arm, s + ds, y, q, inside ? VPlan.AIR : brick());
				}
				at(arm, s + ds, DECK - 2, q, brick());
			}
		}
		p.loot(ax(arm, s, sign * 5), DECK, az(arm, s, sign * 5), s("chest", "facing=" + side(arm, -sign)), LOOT);
		at(arm, s, DECK + 2, sign * 5, HANGING);
	}

	// ------------------------------------------------------------------ pontas

	/** Torre de 13x13 no fim do braco: base, sala com porta para a ponte, escada interna e plataforma em cima. */
	private void tower(int arm, int c) {
		int s1 = c - 6;
		int s2 = c + 6;
		plinth(arm, s1, s2, -6, 6, DECK - 1);
		for (int s = s1; s <= s2; s++) {
			for (int q = -6; q <= 6; q++) {
				boolean edge = s == s1 || s == s2 || Math.abs(q) == 6;
				if (edge) {
					for (int y = DECK; y <= DECK + 6; y++) {
						at(arm, s, y, q, y == DECK + 6 ? RED : (Math.abs(q) == 6 && (s == s1 || s == s2)) ? CHISELED : brick());
					}
				} else {
					airA(arm, s, DECK, q, s, DECK + 5, q);
					at(arm, s, DECK + 6, q, brick());
				}
			}
		}
		parapet(arm, s1, s2, -6, 6, DECK + 7);
		// porta e janelas
		airA(arm, s1, DECK, -1, s1, DECK + 3, 1);
		at(arm, s1, DECK + 3, -1, stairsTop(STAIRS, side(arm, 1)));
		at(arm, s1, DECK + 3, 1, stairsTop(STAIRS, side(arm, -1)));
		for (int s = s1 + 3; s <= s2 - 3; s += 3) {
			at(arm, s, DECK + 2, -6, FENCE);
			at(arm, s, DECK + 3, -6, FENCE);
			at(arm, s, DECK + 2, 6, FENCE);
			at(arm, s, DECK + 3, 6, FENCE);
		}
		// escada ao longo da parede direita, subindo para fora
		for (int i = 0; i <= 5; i++) {
			for (int q = 4; q <= 5; q++) {
				at(arm, c - 4 + i, DECK + i, q, stairs(STAIRS, out(arm)));
				for (int y = DECK; y < DECK + i; y++) {
					at(arm, c - 4 + i, y, q, BRICKS);
				}
			}
		}
		airA(arm, c - 2, DECK + 6, 4, c + 1, DECK + 6, 5);
		for (int s = c - 2; s <= c + 1; s++) {
			at(arm, s, DECK + 7, 3, FENCE);
		}
		at(arm, c - 3, DECK + 7, 4, FENCE);
		at(arm, c - 3, DECK + 7, 5, FENCE);
		at(arm, c, DECK + 5, -3, HANGING);
	}

	private void blazeTower(int arm) {
		int c = 57;
		tower(arm, c);
		// estrado e gerador no topo
		int sq = -2;
		for (int ds = -1; ds <= 1; ds++) {
			for (int q = sq - 1; q <= sq + 1; q++) {
				at(arm, c + ds, DECK + 7, q, ds == 0 && q == sq ? CHISELED : RED);
			}
		}
		p.spawner(ax(arm, c, sq), DECK + 8, az(arm, c, sq), BLAZE);
		p.mark(MARKER_TOWER_SPAWNER, ax(arm, c, sq), DECK + 8, az(arm, c, sq));
		blazeRing(ax(arm, c, sq), az(arm, c, sq), DECK + 7, 3);
		// segundo gerador na sala de baixo, como o vanilla (pequena plataforma)
		at(arm, c + 2, DECK, -2, RED);
		p.spawner(ax(arm, c + 2, -2), DECK + 1, az(arm, c + 2, -2), BLAZE);
		p.loot(ax(arm, c + 5, 0), DECK, az(arm, c + 5, 0), s("chest", "facing=" + in(arm)), LOOT);
	}

	private void lavaTower(int arm) {
		int c = 57;
		tower(arm, c);
		// bacia de lava cercada no meio da sala
		for (int ds = -2; ds <= 2; ds++) {
			for (int q = -3; q <= 1; q++) {
				boolean rim = Math.abs(ds) == 2 || q == -3 || q == 1;
				if (rim) {
					at(arm, c + ds, DECK, q, FENCE);
				} else {
					at(arm, c + ds, DECK - 1, q, s("lava"));
					at(arm, c + ds, DECK - 2, q, BRICKS);
				}
			}
		}
		at(arm, c, DECK + 5, -1, HANGING);
		p.loot(ax(arm, c + 5, -4), DECK, az(arm, c + 5, -4), s("chest", "facing=" + in(arm)), LOOT);
		p.loot(ax(arm, c + 5, 2), DECK, az(arm, c + 5, 2), s("chest", "facing=" + in(arm)), LOOT);
		// mirante com lanternas
		for (int q : new int[] {-5, 5}) {
			at(arm, c + 5, DECK + 7, q, BRICKS);
			at(arm, c + 5, DECK + 8, q, LANTERN);
		}
	}

	/** Estufa de fungo do Nether: canteiros de areia das almas, escadaria central e balcao com baus. */
	private void wartHall(int arm) {
		int s1 = ARM_END;
		int s2 = 66;
		int top = DECK + 10;
		plinth(arm, s1, s2, -7, 7, DECK - 1);
		for (int s = s1; s <= s2; s++) {
			for (int q = -7; q <= 7; q++) {
				boolean edge = s == s1 || s == s2 || Math.abs(q) == 7;
				if (edge) {
					for (int y = DECK; y <= top; y++) {
						boolean window = Math.abs(q) == 7 && (s - s1) % 4 == 2 && y >= DECK + 2 && y <= DECK + 4;
						at(arm, s, y, q, window ? FENCE : y == top ? RED : brick());
					}
				} else {
					airA(arm, s, DECK, q, s, top - 1, q);
					at(arm, s, top, q, (s - s1) % 4 == 2 && Math.abs(q) % 4 == 2 ? s("glowstone") : brick());
				}
			}
		}
		parapet(arm, s1, s2, -7, 7, top + 1);
		airA(arm, s1, DECK, -1, s1, DECK + 3, 1);
		at(arm, s1, DECK + 3, -1, stairsTop(STAIRS, side(arm, 1)));
		at(arm, s1, DECK + 3, 1, stairsTop(STAIRS, side(arm, -1)));
		// canteiros
		for (int s = s1 + 2; s <= s2 - 2; s++) {
			for (int q : new int[] {-6, -5, -4, 4, 5, 6}) {
				at(arm, s, DECK - 1, q, s("soul_sand"));
				at(arm, s, DECK, q, s("nether_wart", "age=" + r.nextInt(4)));
			}
			for (int q : new int[] {-3, 3}) {
				at(arm, s, DECK, q, slab(SLAB));
			}
		}
		p.mark(MARKER_GARDEN, ax(arm, s1 + 4, 5), DECK, az(arm, s1 + 4, 5));
		// escadaria central ate o balcao do fundo
		int b1 = s1 + 10;
		for (int i = 0; i <= 4; i++) {
			for (int q = -2; q <= 2; q++) {
				at(arm, s1 + 5 + i, DECK + i, q, stairs(STAIRS, out(arm)));
				for (int y = DECK; y < DECK + i; y++) {
					at(arm, s1 + 5 + i, y, q, BRICKS);
				}
			}
		}
		for (int s = b1; s <= s2 - 1; s++) {
			for (int q = -6; q <= 6; q++) {
				at(arm, s, DECK + 4, q, s == b1 ? RED : brick());
			}
		}
		for (int q = -6; q <= 6; q++) {
			if (Math.abs(q) > 2) {
				at(arm, b1, DECK + 5, q, FENCE);
			}
		}
		// colunas sob o balcao
		for (int q : new int[] {-3, 3}) {
			for (int y = DECK; y <= DECK + 3; y++) {
				at(arm, b1, y, q, CHISELED);
			}
		}
		p.loot(ax(arm, s2 - 1, -4), DECK + 5, az(arm, s2 - 1, -4), s("chest", "facing=" + in(arm)), LOOT);
		p.loot(ax(arm, s2 - 1, 4), DECK + 5, az(arm, s2 - 1, 4), s("chest", "facing=" + in(arm)), LOOT);
		at(arm, s1 + 4, top - 1, 0, HANGING);
	}

	/** Portao sul: patamar, arco alto e escadaria larga ate o chao. */
	private void gate(int arm) {
		plinth(arm, ARM_END + 1, ARM_END + 4, -5, 5, DECK - 1);
		airA(arm, ARM_END + 1, DECK, -5, ARM_END + 4, DECK + 4, 5);
		for (int q : new int[] {-5, -4, 4, 5}) {
			for (int y = DECK; y <= DECK + 7; y++) {
				at(arm, ARM_END + 2, y, q, y == DECK + 7 ? CHISELED : RED);
			}
		}
		for (int q = -5; q <= 5; q++) {
			at(arm, ARM_END + 2, DECK + 7, q, Math.abs(q) == 0 ? CHISELED : RED);
			at(arm, ARM_END + 2, DECK + 8, q, q % 2 == 0 ? RED : RED_WALL);
		}
		at(arm, ARM_END + 2, DECK + 6, -3, stairsTop(RED_STAIRS, side(arm, 1)));
		at(arm, ARM_END + 2, DECK + 6, 3, stairsTop(RED_STAIRS, side(arm, -1)));
		at(arm, ARM_END + 2, DECK + 6, 0, HANGING);
		for (int q : new int[] {-5, 5}) {
			for (int s = ARM_END + 1; s <= ARM_END + 4; s += 3) {
				at(arm, s, DECK, q, BRICKS);
				at(arm, s, DECK + 1, q, LANTERN);
			}
		}
		p.mark(MARKER_GATE, ax(arm, ARM_END + 2, 0), DECK, az(arm, ARM_END + 2, 0));
		// escadaria
		int first = ARM_END + 5;
		for (int i = 0; i < DECK - 1; i++) {
			int s = first + i;
			int y = DECK - 2 - i;
			for (int q = -4; q <= 4; q++) {
				if (Math.abs(q) == 4) {
					for (int yy = 0; yy <= y + 1; yy++) {
						at(arm, s, yy, q, brick());
					}
					at(arm, s, y + 2, q, i % 2 == 0 ? WALL : FENCE);
				} else {
					at(arm, s, y, q, stairs(STAIRS, in(arm)));
					for (int yy = 0; yy < y; yy++) {
						at(arm, s, yy, q, brick());
					}
				}
				if (y > 0 || Math.abs(q) == 4) {
					at(arm, s, 0, q, getA(arm, s, 0, q), VPlan.FILL_DEEP);
				}
				for (int yy = y + 1 + (Math.abs(q) == 4 ? 2 : 0); yy <= y + 4; yy++) {
					at(arm, s, yy, q, VPlan.AIR);
				}
			}
		}
	}
}
