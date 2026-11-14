package com.mlin.notas.end;

import com.mlin.notas.vstructures.VPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Planeja a Cidadela do End, a cidade que substitui a End City vanilla. Logica pura (sem Minecraft): o resultado e um
 * {@link CitadelPlan} com o estado de cada bloco em texto.
 *
 * <h2>Desenho</h2>
 * <ul>
 *   <li><b>Ilha</b> propria (raio ~{@link #RADIUS}): patio alto (raio {@link #PLATEAU}) cercado por uma muralha
 *       ameiada com portoes nas diagonais e trechos desabados, muro de arrimo de tijolos e um terraco 3 blocos abaixo
 *       com jardins de coro, lirios do vazio e arvores do vazio. Embaixo, o fundo de pedra do End em cone com
 *       aglomerados de cristal etereo pendurados.</li>
 *   <li><b>Fortaleza</b> central de {@link #KEEP_FLOORS} niveis que afina com a altura (15 -&gt; 13 -&gt; 11 -&gt; cupula),
 *       com sacadas em balanco nas trocas de largura, torreoes de canto, arcobotantes, portico nos 4 portoes e
 *       cupula de observatorio com agulha. Andares tematicos: salao (sentinelas), ninho de shulkers (pontes),
 *       biblioteca, salao de banquete (ponte alta), alquimia, cofre (cabeca de dragao, baus, altar do Arauto Astral)
 *       e observatorio.</li>
 *   <li>Tres <b>torres-satelite</b> de 3 a 5 andares (alturas diferentes) com o ultimo andar em balanco e telhado em
 *       agulha de camadas de purpura/tijolo do End ou coroa ameiada; salas de guarda, ninho, arsenal, alojamento,
 *       estufa e deposito. Pontes no 2o andar e uma ponte alta da fortaleza ate a torre mais alta.</li>
 *   <li><b>Cais</b> com mastro de elevadores e a <b>Nave do End</b> atracada (casco curvo de purpura, velas magenta,
 *       castelo de popa com a cabine do tesouro: o elitro, um por cidadela, e dois baus; porao com bau e shulkers;
 *       cabeca de dragao na proa).</li>
 *   <li><b>Ilhotas</b> de destrocos em volta, ligadas ao terraco por pontes (santuario de cristal, jardim de coro,
 *       gazebo em ruinas com bau), e rochas soltas que flutuam de proposito ({@link CitadelPlan#floatingBodies()}).</li>
 * </ul>
 *
 * <h2>Referencias da comunidade</h2>
 * Desenho escrito do zero, inspirado em: o guia "Build an End City tower" do minecraft.how (paredes recuando a cada
 * poucos andares, faixas de laje para sombra, pilares de purpura em nervura, varas do End e lanternas em correntes,
 * mirante no topo); as pecas vanilla da End City (fat tower, tower top, a nave com cabeca de dragao na proa e a sala do
 * elitro na popa; schematics em minecraftmaps.com); o pacote Tonis Better End Cities (agulha de purpura em quatro
 * camadas sobre uma grande praca, pontes no ceu, nave de verdade com elitro); o End Castle do Dungeons and Taverns (a
 * nave grande atracada no castelo guardando o elitro); a Mega Citadel do Clear End City (alas, torres, pontes e
 * parapeitos sobre o vazio, cofre do tesouro; campos de destrocos e jardins de coro); e o "Build it: purpur" do
 * minecraft.net (purpura com um bloco claro de contraste para um ar nobre).
 */
public final class CitadelPlanner {
	public static final int FLOOR_HEIGHT = 6;
	/** Raio aproximado da ilha principal (o terraco vai ate aqui). */
	public static final int RADIUS = 33;
	/** Raio do patio alto (y=0); dali para fora o terraco fica em y=-3. */
	public static final double PLATEAU = 27.5;
	public static final int TERRACE_Y = -3;
	public static final double WALL_R = 26.0;
	public static final int KEEP_HALF = 7;
	/** Niveis da fortaleza com elevador: 0 salao ... 5 cofre, 6 observatorio. */
	public static final int KEEP_FLOORS = 7;
	public static final int VAULT_FLOOR = 5;
	public static final int VAULT_Y = VAULT_FLOOR * FLOOR_HEIGHT + 1;
	public static final int TOWER_HALF = 4;
	/** Andar (0 = terreo) em que as pontes chegam. */
	public static final int BRIDGE_FLOOR = 1;
	/** Andar da ponte alta (fortaleza ate a torre mais alta). */
	public static final int SKY_FLOOR = 3;
	/** Alcance maximo do elevador (blocos). */
	public static final int ELEVATOR_RANGE = 32;
	public static final int SATELLITE_DIST = 18;
	public static final int DOCK_DIST = 20;
	public static final int DECK_Y = 24;
	public static final int SHIP_DIST = 39;

	static final String N = "north";
	static final String S = "south";
	static final String E = "east";
	static final String W = "west";

	// paleta
	static final String PURPUR = "minecraft:purpur_block";
	static final String PILLAR = "minecraft:purpur_pillar";
	static final String PURPUR_STAIRS = "minecraft:purpur_stairs";
	static final String PURPUR_SLAB = "minecraft:purpur_slab";
	static final String BRICKS = "minecraft:end_stone_bricks";
	static final String BRICK_STAIRS = "minecraft:end_stone_brick_stairs";
	static final String BRICK_SLAB = "minecraft:end_stone_brick_slab";
	static final String BRICK_WALL = "minecraft:end_stone_brick_wall";
	static final String END_STONE = "minecraft:end_stone";
	static final String END_ROD = "minecraft:end_rod";
	static final String VOID_BRICKS = "mlinnotas:void_bricks";
	static final String LAMP = "mlinnotas:purpur_lamp";
	static final String ELEVATOR = "mlinnotas:purpur_elevator";
	static final String MOSS = "mlinnotas:end_moss";
	static final String VOID_PLANKS = "mlinnotas:void_planks";
	static final String VOID_LOG = "mlinnotas:void_log";
	static final String VOID_LEAVES = "mlinnotas:void_leaves[persistent=true]";
	static final String CRYSTAL = "mlinnotas:ether_crystal_block";
	static final String CLUSTER = "mlinnotas:ether_crystal_cluster";
	static final String PANE = "minecraft:magenta_stained_glass_pane";
	static final String GLASS = "minecraft:magenta_stained_glass";
	public static final String ALTAR = "mlinnotas:astral_altar";

	/** Norte, leste, sul, oeste (x, z). */
	private static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	private enum Theme {
		ARMORY,
		QUARTERS,
		CONSERVATORY,
		STOREROOM,
		STAR_CHAMBER
	}

	private final CitadelPlan p;
	private final Random r;
	private final long seed;

	private CitadelPlanner(long seed) {
		this.seed = seed;
		this.r = new Random(seed);
		this.p = new CitadelPlan(RADIUS);
	}

	public static CitadelPlan plan(long seed) {
		CitadelPlanner planner = new CitadelPlanner(seed);
		planner.build();
		return planner.p;
	}

	// ------------------------------------------------------------------ utilidades

	static String s(String id, String... props) {
		return VPlan.s(id, props);
	}

	static String dir(int dx, int dz) {
		if (dx > 0) {
			return E;
		}
		if (dx < 0) {
			return W;
		}
		return dz > 0 ? S : N;
	}

	static String stairs(String id, String facing) {
		return s(id, "facing=" + facing);
	}

	static String stairsTop(String id, String facing) {
		return s(id, "facing=" + facing, "half=top");
	}

	static String rod(String facing) {
		return s(END_ROD, "facing=" + facing);
	}

	static String banner(String color, String facing) {
		return s("minecraft:" + color + "_wall_banner", "facing=" + facing);
	}

	private void set(int x, int y, int z, String state) {
		p.set(x, y, z, state);
	}

	private boolean empty(int x, int y, int z) {
		return p.get(x, y, z) == null;
	}

	private void setIfEmpty(int x, int y, int z, String state) {
		if (empty(x, y, z)) {
			p.set(x, y, z, state);
		}
	}

	private long hash(int x, int z, int salt) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return h;
	}

	private double noise01(int x, int z, int salt) {
		return (hash(x, z, salt) >>> 11) / (double) (1L << 53);
	}

	private boolean chance(double c) {
		return r.nextDouble() < c;
	}

	/** Raio da beira da ilha no angulo dado (ondulado). */
	private double edgeRadius(double angle) {
		double phase = (seed & 0xFFFF) / 1000.0;
		return RADIUS + 1.2 * Math.sin(3 * angle + phase) + 0.8 * Math.sin(7 * angle + phase * 2);
	}

	/** Celula em coordenadas da fachada: centro (cx, cz), lado (dx, dz) e posicao {@code a} ao longo da parede. */
	private static int fx(int cx, int dx, int dz, int dist, int a) {
		return cx + dx * dist + Math.abs(dz) * a;
	}

	private static int fz(int cz, int dx, int dz, int dist, int a) {
		return cz + dz * dist + Math.abs(dx) * a;
	}

	/** Direcao (texto) do eixo ao longo da parede, no sentido de {@code a} positivo. */
	private static String alongDir(int dx, int dz, int sign) {
		return dir(Math.abs(dz) * sign, Math.abs(dx) * sign);
	}

	// ------------------------------------------------------------------ plano geral

	private record Sat(int dx, int dz, int dist, int floors, boolean spire) {
		int x() {
			return dx * dist;
		}

		int z() {
			return dz * dist;
		}

		/** Meia largura da parede no andar dado (o ultimo andar fica em balanco). */
		int half(int floor) {
			return floor == floors - 1 ? TOWER_HALF + 1 : TOWER_HALF;
		}
	}

	private void build() {
		List<int[]> dirs = new ArrayList<>(List.of(DIRECTIONS));
		Collections.shuffle(dirs, r);
		int[] dock = dirs.get(3);
		List<Integer> heights = new ArrayList<>(List.of(3, 4, 5));
		Collections.shuffle(heights, r);
		List<Sat> sats = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			int[] d = dirs.get(i);
			int floors = heights.get(i);
			sats.add(new Sat(d[0], d[1], SATELLITE_DIST, floors, floors == 5 || chance(0.6)));
		}
		Sat tallest = sats.stream().max((a, b) -> Integer.compare(a.floors(), b.floors())).orElseThrow();

		island();
		plaza(sats, dock);
		outerWall();

		keep(sats, dock, tallest);
		for (Sat sat : sats) {
			satellite(sat, sat == tallest);
			bridge(sat.dx(), sat.dz(), BRIDGE_FLOOR * FLOOR_HEIGHT, KEEP_HALF + 1, sat.dist() - sat.half(BRIDGE_FLOOR) - 1, true);
		}
		bridge(tallest.dx(), tallest.dz(), SKY_FLOOR * FLOOR_HEIGHT, KEEP_HALF, tallest.dist() - tallest.half(SKY_FLOOR) - 1, false);

		dockAndShip(dock[0], dock[1]);
		islets(dock[0], dock[1]);
		gardens();
		debris();
		p.mark("entrance", -21, TERRACE_Y + 1, 21);
	}

	// ------------------------------------------------------------------ ilha

	private void island() {
		for (int x = -RADIUS - 3; x <= RADIUS + 3; x++) {
			for (int z = -RADIUS - 3; z <= RADIUS + 3; z++) {
				double dist = Math.sqrt(x * x + z * z);
				double edge = edgeRadius(Math.atan2(z, x));
				if (dist > edge) {
					continue;
				}
				int top = dist <= PLATEAU ? 0 : TERRACE_Y;
				double t = dist / (edge + 1);
				int depth = 3 + (int) Math.round(Math.sqrt(Math.max(0.0, 1.0 - t * t)) * 22) + (int) (hash(x, z, 1) & 1);
				if (noise01(x, z, 2) < 0.03) {
					depth += 2 + (int) (hash(x, z, 3) & 3); // estalactites
				}
				boolean rim = dist <= PLATEAU && dist > PLATEAU - 1.2;
				for (int y = top; y >= -depth; y--) {
					String block;
					if (y == top) {
						block = rim ? VOID_BRICKS : MOSS;
					} else if (rim && y > TERRACE_Y) {
						block = BRICKS;
					} else {
						block = END_STONE;
					}
					set(x, y, z, block);
				}
				if (noise01(x, z, 4) < 0.08) {
					set(x, -depth - 1, z, s(CLUSTER, "facing=down"));
				}
			}
		}
	}

	/** Praca da fortaleza, caminhos ate as torres, o cais e os portoes. */
	private void plaza(List<Sat> sats, int[] dock) {
		int ph = 13;
		for (int x = -ph; x <= ph; x++) {
			for (int z = -ph; z <= ph; z++) {
				int m = Math.max(Math.abs(x), Math.abs(z));
				String block = m == ph ? PURPUR : Math.abs(x) == Math.abs(z) ? VOID_BRICKS : (x == 0 || z == 0) ? PURPUR : BRICKS;
				set(x, 0, z, block);
			}
		}
		List<int[]> radial = new ArrayList<>();
		for (Sat sat : sats) {
			radial.add(new int[] {sat.dx(), sat.dz()});
		}
		radial.add(dock);
		for (int[] d : radial) {
			for (int k = ph + 1; k <= DOCK_DIST; k++) {
				for (int a = -2; a <= 2; a++) {
					set(fx(0, d[0], d[1], k, a), 0, fz(0, d[0], d[1], k, a), Math.abs(a) == 2 ? PURPUR : BRICKS);
				}
			}
		}
		// caminhos diagonais ate os portoes, e rampas ate o terraco
		for (int[] g : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
			for (int x = -RADIUS; x <= RADIUS; x++) {
				for (int z = -RADIUS; z <= RADIUS; z++) {
					double along = (x * g[0] + z * g[1]) / Math.sqrt(2);
					double perp = Math.abs(x * g[1] - z * g[0]) / Math.sqrt(2);
					if (perp > 1.8 || along < ph * Math.sqrt(2) - 1 || along > 31.5) {
						continue;
					}
					double dist = Math.sqrt(x * x + z * z);
					if (dist <= PLATEAU) {
						set(x, 0, z, perp > 1.2 ? PURPUR : BRICKS);
					} else {
						int top = Math.max(TERRACE_Y, -(int) Math.ceil(dist - PLATEAU));
						set(x, top, z, perp > 1.2 ? PURPUR : BRICKS);
						for (int y = top + 1; y <= 0; y++) {
							p.remove(x, y, z);
						}
						for (int y = top - 1; y >= TERRACE_Y - 1; y--) {
							set(x, y, z, BRICKS);
						}
					}
				}
			}
		}
		// postes de luz nos cantos da praca
		for (int cx : new int[] {-ph, ph}) {
			for (int cz : new int[] {-ph, ph}) {
				lampPost(cx + Integer.signum(cx), cz - 3 * Integer.signum(cz), 3);
				lampPost(cx - 3 * Integer.signum(cx), cz + Integer.signum(cz), 3);
			}
		}
	}

	private void lampPost(int x, int z, int height) {
		for (int y = 1; y <= height; y++) {
			set(x, y, z, y == 1 ? VOID_BRICKS : PILLAR);
		}
		set(x, height + 1, z, LAMP);
		set(x, height + 2, z, rod("up"));
	}

	/** Muralha ameiada em volta do patio, com portoes nas diagonais, postes e trechos desabados. */
	private void outerWall() {
		double[] gates = {Math.PI / 4, 3 * Math.PI / 4, -Math.PI / 4, -3 * Math.PI / 4};
		double[] breaches = new double[3];
		for (int i = 0; i < breaches.length; i++) {
			breaches[i] = (i * 2 * Math.PI / 3) + Math.PI / 8 + r.nextDouble() * 0.5;
		}
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				double dist = Math.sqrt(x * x + z * z);
				if (dist < WALL_R - 0.7 || dist >= WALL_R + 0.7) {
					continue;
				}
				double angle = Math.atan2(z, x);
				double gateArc = Double.MAX_VALUE;
				for (double g : gates) {
					gateArc = Math.min(gateArc, Math.abs(angleDiff(angle, g)) * WALL_R);
				}
				double breachArc = Double.MAX_VALUE;
				for (double b : breaches) {
					breachArc = Math.min(breachArc, Math.abs(angleDiff(angle, b)) * WALL_R);
				}
				int height = 4;
				if (breachArc < 3.5) {
					height = (int) (noise01(x, z, 7) * 2.6 * (breachArc / 3.5));
				}
				boolean gate = gateArc <= 1.9;
				boolean post = gateArc > 1.9 && gateArc <= 3.3;
				for (int y = 1; y <= height; y++) {
					if (gate) {
						continue;
					}
					set(x, y, z, y == 1 ? VOID_BRICKS : BRICKS);
				}
				if (gate) {
					set(x, 5, z, VOID_BRICKS);
					if ((x + z & 1) == 0) {
						set(x, 6, z, BRICKS);
					}
				} else if (post) {
					for (int y = height + 1; y <= 7; y++) {
						set(x, y, z, y == 7 ? PURPUR : PILLAR);
					}
					set(x, 8, z, (x + z & 1) == 0 ? rod("up") : LAMP);
				} else if (height == 4) {
					set(x, 5, z, (x + z & 1) == 0 ? BRICKS : BRICK_WALL);
				}
				if (breachArc < 3.5 && height < 4) {
					// entulho caido dos dois lados
					for (int k = 0; k < 2; k++) {
						double out = (k == 0 ? 1.0 : -1.0) * (1.5 + noise01(x, z, 8 + k) * 2.5);
						int rx = (int) Math.round(x + Math.cos(angle) * out);
						int rz = (int) Math.round(z + Math.sin(angle) * out);
						double rd = Math.sqrt(rx * rx + rz * rz);
						int ground = rd <= PLATEAU ? 0 : TERRACE_Y;
						if (noise01(x, z, 10 + k) < 0.55 && empty(rx, ground + 1, rz) && p.get(rx, ground, rz) != null) {
							set(rx, ground + 1, rz, noise01(rx, rz, 12) < 0.5 ? BRICKS : BRICK_SLAB);
						}
					}
				}
			}
		}
	}

	private static double angleDiff(double a, double b) {
		double d = a - b;
		while (d > Math.PI) {
			d -= 2 * Math.PI;
		}
		while (d < -Math.PI) {
			d += 2 * Math.PI;
		}
		return d;
	}

	// ------------------------------------------------------------------ paredes de torre

	/** Tipo da celula da fachada: P pilar, W janela, S parede lisa. */
	private static char facadeCell(int h, int a) {
		int aa = Math.abs(a);
		if (aa == h || (h >= 5 && aa == h - 3)) {
			return 'P';
		}
		return aa % 2 == h % 2 ? 'S' : 'W';
	}

	/**
	 * Um andar: laje do piso em {@code floorY} (|d| &lt;= h) e paredes de {@code floorY+1} ate {@code floorY+FH-1}, com
	 * pilares, janelas altas de vidro magenta com peitoril e verga, faixa de tijolos do vazio na linha do piso e
	 * arandelas de vara do End.
	 */
	private void storey(int cx, int cz, int h, int floorY, boolean ground) {
		int top = floorY + FLOOR_HEIGHT - 1;
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				int x = cx + dx;
				int z = cz + dz;
				if (m < h) {
					String floor;
					if (m == h - 1) {
						floor = VOID_BRICKS;
					} else if (h >= 4 && Math.abs(dx) == h - 2 && Math.abs(dz) == h - 2 && !ground) {
						floor = LAMP;
					} else if (dx == 0 || dz == 0) {
						floor = BRICKS;
					} else {
						floor = PURPUR;
					}
					set(x, floorY, z, floor);
					for (int y = floorY + 1; y <= top; y++) {
						set(x, y, z, VPlan.AIR);
					}
				}
			}
		}
		for (int[] d : DIRECTIONS) {
			String out = dir(d[0], d[1]);
			String in = dir(-d[0], -d[1]);
			for (int a = -h; a <= h; a++) {
				int x = fx(cx, d[0], d[1], h, a);
				int z = fz(cz, d[0], d[1], h, a);
				int ox = x + d[0];
				int oz = z + d[1];
				char cell = facadeCell(h, a);
				set(x, floorY, z, cell == 'P' ? PILLAR : ground ? BRICKS : VOID_BRICKS);
				for (int level = 1; level < FLOOR_HEIGHT; level++) {
					int y = floorY + level;
					String block;
					if (cell == 'P') {
						block = PILLAR;
					} else if (cell == 'W' && level >= 2 && level <= 4) {
						block = PANE;
					} else {
						block = ground && level <= 2 ? BRICKS : PURPUR;
					}
					set(x, y, z, block);
				}
				if (cell == 'W') {
					setIfEmpty(ox, floorY + 1, oz, s(PURPUR_SLAB, "type=top"));
					setIfEmpty(ox, floorY + 5, oz, stairsTop(PURPUR_STAIRS, in));
				} else if (cell == 'P' && Math.abs(a) != h) {
					setIfEmpty(ox, floorY + 3, oz, rod(out));
				}
			}
		}
	}

	/** Sacada em balanco: laje de {@code hIn} ate {@code hOut} em {@code y}, mureta na beira e misulas embaixo. */
	private void ledge(int cx, int cz, int hIn, int hOut, int y) {
		for (int dx = -hOut; dx <= hOut; dx++) {
			for (int dz = -hOut; dz <= hOut; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				if (m <= hIn) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				set(x, y, z, m == hOut ? VOID_BRICKS : PURPUR);
				if (m == hOut) {
					set(x, y + 1, z, BRICK_WALL);
					int ox = Math.abs(dx) == m ? Integer.signum(dx) : 0;
					int oz = ox == 0 ? Integer.signum(dz) : 0;
					set(x, y - 1, z, stairsTop(PURPUR_STAIRS, dir(-ox, -oz)));
				}
			}
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				int x = cx + sx * hOut;
				int z = cz + sz * hOut;
				set(x, y + 1, z, PILLAR);
				set(x, y + 2, z, rod("up"));
			}
		}
	}

	/**
	 * Porta na parede do lado (dx, dz) a {@code dist} do centro: vao de 3 de largura e {@code height} de altura, umbrais
	 * de pilar e verga em arco. Limpa peitoris e arandelas da frente do vao.
	 */
	private void door(int cx, int cz, int dx, int dz, int dist, int floorY, int height) {
		String out = dir(dx, dz);
		for (int a = -2; a <= 2; a++) {
			int x = fx(cx, dx, dz, dist, a);
			int z = fz(cz, dx, dz, dist, a);
			for (int level = 1; level <= height + 1; level++) {
				int y = floorY + level;
				if (Math.abs(a) == 2) {
					set(x, y, z, PILLAR);
				} else if (level <= height) {
					set(x, y, z, VPlan.AIR);
					String outside = p.get(x + dx, y, z + dz);
					if (outside != null && !VPlan.id(outside).equals("air") && !VPlan.id(outside).endsWith("_wall") && !VPlan.id(outside).equals("purpur_pillar")) {
						p.remove(x + dx, y, z + dz);
					}
				} else {
					set(x, y, z, a == 0 ? PURPUR : stairsTop(PURPUR_STAIRS, alongDir(dx, dz, a)));
				}
			}
			if (Math.abs(a) < 2) {
				p.remove(x - dx, floorY + 1, z - dz); // por dentro, nada na frente da porta
			}
		}
		for (int a : new int[] {-2, 2}) {
			int bx = fx(cx, dx, dz, dist, a) + dx;
			int bz = fz(cz, dx, dz, dist, a) + dz;
			String there = p.get(bx, floorY + height, bz);
			if (there == null || VPlan.id(there).endsWith("_stairs") || VPlan.id(there).endsWith("_slab") || VPlan.id(there).equals("end_rod")) {
				set(bx, floorY + height, bz, banner("magenta", out));
			}
		}
	}

	private void elevator(int x, int y, int z) {
		set(x, y, z, ELEVATOR);
		set(x, y + 1, z, VPlan.AIR);
		set(x, y + 2, z, VPlan.AIR);
	}

	// ------------------------------------------------------------------ fortaleza

	private static int keepHalf(int floor) {
		return floor <= 2 ? KEEP_HALF : floor <= 4 ? KEEP_HALF - 1 : KEEP_HALF - 2;
	}

	private void keep(List<Sat> sats, int[] dock, Sat tallest) {
		p.tower(new CitadelPlan.Tower("keep", 0, 0, KEEP_HALF, KEEP_FLOORS));
		for (int f = 0; f < VAULT_FLOOR + 1; f++) {
			storey(0, 0, keepHalf(f), f * FLOOR_HEIGHT, f == 0);
		}
		ledge(0, 0, keepHalf(3), KEEP_HALF + 1, 3 * FLOOR_HEIGHT);
		ledge(0, 0, keepHalf(5), keepHalf(3) + 1, 5 * FLOOR_HEIGHT);
		// torreoes de canto da base
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				turret(sx * (KEEP_HALF + 1), sz * (KEEP_HALF + 1), 22);
			}
		}
		// arcobotantes nos 4 lados
		for (int[] d : DIRECTIONS) {
			for (int a : new int[] {-4, 4}) {
				flyingButtress(d[0], d[1], a);
			}
		}
		// portoes do terreo com portico
		for (int[] d : DIRECTIONS) {
			door(0, 0, d[0], d[1], KEEP_HALF, 0, 4);
			portico(d[0], d[1]);
		}
		for (Sat sat : sats) {
			door(0, 0, sat.dx(), sat.dz(), KEEP_HALF, BRIDGE_FLOOR * FLOOR_HEIGHT, 3);
		}
		door(0, 0, tallest.dx(), tallest.dz(), keepHalf(SKY_FLOOR), SKY_FLOOR * FLOOR_HEIGHT, 3);
		// saidas para as sacadas
		for (int[] d : new int[][] {dock, {-dock[0], -dock[1]}}) {
			door(0, 0, d[0], d[1], keepHalf(3), 3 * FLOOR_HEIGHT, 3);
		}
		roofAndObservatory();
		for (int f = 0; f < KEEP_FLOORS; f++) {
			elevator(0, f * FLOOR_HEIGHT, 0);
		}
		hall(0);
		nest(FLOOR_HEIGHT);
		library(2 * FLOOR_HEIGHT);
		dining(3 * FLOOR_HEIGHT, tallest);
		alchemy(4 * FLOOR_HEIGHT);
		vault(5 * FLOOR_HEIGHT);
		observatory(6 * FLOOR_HEIGHT);
		// shulkers de vigia nas sacadas
		spawn(CitadelPlan.Kind.SHULKER, KEEP_HALF, 3 * FLOOR_HEIGHT + 1, -3);
		spawn(CitadelPlan.Kind.SHULKER, -3, 5 * FLOOR_HEIGHT + 1, keepHalf(3));
	}

	/** Torreao 3x3 macico no canto, com faixas, lampadas e coruchéu. */
	private void turret(int cx, int cz, int height) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y <= height; y++) {
					String block;
					if (y % FLOOR_HEIGHT == 0) {
						block = VOID_BRICKS;
					} else if (y <= 2) {
						block = BRICKS;
					} else if (dx != 0 && dz != 0) {
						block = PILLAR;
					} else if ((dx == 0) != (dz == 0) && y % FLOOR_HEIGHT == 3 && Math.abs(cx + dx) + Math.abs(cz + dz) > Math.abs(cx) + Math.abs(cz)) {
						block = LAMP;
					} else {
						block = PURPUR;
					}
					set(cx + dx, y, cz + dz, block);
				}
				if (dx != 0 || dz != 0) {
					String facing = dir(-dx, -dz);
					if (dx != 0 && dz != 0) {
						facing = dir(-dx, 0);
					}
					set(cx + dx, height + 1, cz + dz, stairs(BRICK_STAIRS, facing));
				}
			}
		}
		set(cx, height + 1, cz, PURPUR);
		set(cx, height + 2, cz, PILLAR);
		set(cx, height + 3, cz, PILLAR);
		set(cx, height + 4, cz, rod("up"));
	}

	/** Arcobotante: pilar no chao a 4 blocos da parede e um arco de degraus subindo ate a fortaleza. */
	private void flyingButtress(int dx, int dz, int a) {
		String toKeep = dir(-dx, -dz);
		int[][] arch = {{8, 13}, {9, 12}, {10, 11}, {11, 10}};
		for (int[] c : arch) {
			int x = fx(0, dx, dz, c[0], a);
			int z = fz(0, dx, dz, c[0], a);
			set(x, c[1], z, BRICKS);
			set(x, c[1] - 1, z, c[0] == 11 ? BRICKS : stairsTop(BRICK_STAIRS, dir(dx, dz)));
			set(x, c[1] + 1, z, stairs(PURPUR_STAIRS, toKeep));
		}
		int x = fx(0, dx, dz, 11, a);
		int z = fz(0, dx, dz, 11, a);
		for (int y = 1; y <= 9; y++) {
			set(x, y, z, y <= 2 ? VOID_BRICKS : y == 9 ? PURPUR : PILLAR);
		}
		int px = fx(0, dx, dz, 12, a);
		int pz = fz(0, dx, dz, 12, a);
		for (int y = 1; y <= 7; y++) {
			set(px, y, pz, y <= 2 ? VOID_BRICKS : BRICKS);
		}
		set(px, 8, pz, stairs(BRICK_STAIRS, toKeep));
		set(px, 9, pz, rod("up"));
	}

	private void portico(int dx, int dz) {
		String in = dir(-dx, -dz);
		for (int a = -2; a <= 2; a++) {
			for (int k = KEEP_HALF + 1; k <= KEEP_HALF + 2; k++) {
				int x = fx(0, dx, dz, k, a);
				int z = fz(0, dx, dz, k, a);
				if (Math.abs(a) == 2 && k == KEEP_HALF + 2) {
					for (int y = 1; y <= 4; y++) {
						set(x, y, z, y == 1 ? VOID_BRICKS : PILLAR);
					}
					set(x, 5, z, PURPUR);
					set(x, 6, z, rod("up"));
				} else {
					set(x, 5, z, Math.abs(a) == 2 || k == KEEP_HALF + 2 ? stairsTop(PURPUR_STAIRS, Math.abs(a) == 2 ? alongDir(dx, dz, -Integer.signum(a)) : in)
							: s(PURPUR_SLAB, "type=top"));
				}
			}
		}
		// lanterna do portico
		set(fx(0, dx, dz, KEEP_HALF + 1, 0), 4, fz(0, dx, dz, KEEP_HALF + 1, 0), s("minecraft:soul_lantern", "hanging=true"));
	}

	/** Teto do cofre com terraco ameiado e a cupula do observatorio com agulha. */
	private void roofAndObservatory() {
		int y = KEEP_FLOORS - 1;
		int roofY = y * FLOOR_HEIGHT;
		int h = keepHalf(VAULT_FLOOR);
		for (int dx = -h - 1; dx <= h + 1; dx++) {
			for (int dz = -h - 1; dz <= h + 1; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				set(dx, roofY, dz, m == h + 1 ? VOID_BRICKS : (dx == 0 || dz == 0) ? BRICKS : PURPUR);
				if (m == h + 1) {
					set(dx, roofY + 1, dz, (dx + dz & 1) == 0 ? BRICKS : BRICK_WALL);
					int ox = Math.abs(dx) == m ? Integer.signum(dx) : 0;
					int oz = ox == 0 ? Integer.signum(dz) : 0;
					set(dx, roofY - 1, dz, stairsTop(PURPUR_STAIRS, dir(-ox, -oz)));
				}
			}
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				int x = sx * (h + 1);
				int z = sz * (h + 1);
				for (int k = 1; k <= 4; k++) {
					set(x, roofY + k, z, PILLAR);
				}
				set(x, roofY + 5, z, stairs(PURPUR_STAIRS, dir(-sx, 0)));
				set(x, roofY + 6, z, rod("up"));
			}
		}
		// cupula (paredes em |d| = 3)
		int d = 3;
		for (int dx = -d; dx <= d; dx++) {
			for (int dz = -d; dz <= d; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				if (m < d) {
					for (int k = 1; k <= 4; k++) {
						set(dx, roofY + k, dz, VPlan.AIR);
					}
					continue;
				}
				boolean rib = (Math.abs(dx) == d && Math.abs(dz) == d) || dx == 0 || dz == 0;
				for (int k = 1; k <= 4; k++) {
					set(dx, roofY + k, dz, rib ? PILLAR : GLASS);
				}
			}
		}
		// portas para o terraco (norte e sul)
		for (int sz : new int[] {-1, 1}) {
			set(0, roofY + 1, sz * d, VPlan.AIR);
			set(0, roofY + 2, sz * d, VPlan.AIR);
		}
		int capY = roofY + 5;
		for (int dx = -d; dx <= d; dx++) {
			for (int dz = -d; dz <= d; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				if (m == d) {
					int ox = Math.abs(dx) == m ? Integer.signum(dx) : 0;
					int oz = ox == 0 ? Integer.signum(dz) : 0;
					set(dx, capY, dz, stairs(PURPUR_STAIRS, dir(-ox, -oz)));
				} else if (m == 2) {
					set(dx, capY, dz, PURPUR);
					int ox = Math.abs(dx) == m ? Integer.signum(dx) : 0;
					int oz = ox == 0 ? Integer.signum(dz) : 0;
					set(dx, capY + 1, dz, stairs(PURPUR_STAIRS, dir(-ox, -oz)));
				} else {
					set(dx, capY, dz, GLASS);
					if (m == 1) {
						int ox = Math.abs(dx) == m ? Integer.signum(dx) : 0;
						int oz = ox == 0 ? Integer.signum(dz) : 0;
						set(dx, capY + 1, dz, PURPUR);
						set(dx, capY + 2, dz, stairs(BRICK_STAIRS, dir(-ox, -oz)));
					} else {
						set(dx, capY + 1, dz, PURPUR);
						set(dx, capY + 2, dz, PURPUR);
					}
				}
			}
		}
		for (int k = 3; k <= 6; k++) {
			set(0, capY + k, 0, PILLAR);
		}
		set(0, capY + 7, 0, LAMP);
		set(0, capY + 8, 0, rod("up"));
	}

	// ------------------------------------------------------------------ salas da fortaleza

	private void spawn(CitadelPlan.Kind kind, int x, int y, int z) {
		p.spawn(new CitadelPlan.Spawn(kind, x, y, z, 0, 0));
	}

	private void chest(int x, int y, int z, String facing) {
		p.chest(x, y, z, s("minecraft:chest", "facing=" + facing));
	}

	/** Salao do terreo: colunas com capitel, tapete em cruz, lustres, estandartes e duas sentinelas. */
	private void hall(int floorY) {
		int y = floorY + 1;
		int in = KEEP_HALF - 1;
		for (int a = -in; a <= in; a++) {
			for (int w = -1; w <= 1; w++) {
				if (a != 0 || w != 0) {
					set(a, y, w, w == 0 ? "minecraft:purple_carpet" : "minecraft:magenta_carpet");
					set(w, y, a, w == 0 ? "minecraft:purple_carpet" : "minecraft:magenta_carpet");
				}
			}
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				int x = sx * 3;
				int z = sz * 3;
				for (int k = 0; k < 4; k++) {
					set(x, y + k, z, PILLAR);
				}
				set(x + sx, y + 4, z, stairsTop(PURPUR_STAIRS, dir(-sx, 0)));
				set(x, y + 4, z + sz, stairsTop(PURPUR_STAIRS, dir(0, -sz)));
				set(x, y + 4, z, PURPUR);
				// bancos nos cantos
				set(sx * 5, y, sz * 5, stairs(PURPUR_STAIRS, dir(sx, 0)));
				set(sx * 5, y, sz * 4, stairs(PURPUR_STAIRS, dir(sx, 0)));
				// lustre
				set(sx * 4, y + 4, 0, "minecraft:iron_chain");
				set(sx * 4, y + 3, 0, s("minecraft:soul_lantern", "hanging=true"));
				set(0, y + 4, sz * 4, "minecraft:iron_chain");
				set(0, y + 3, sz * 4, s("minecraft:soul_lantern", "hanging=true"));
			}
		}
		for (int[] d : DIRECTIONS) {
			for (int a : new int[] {-4, 4}) {
				set(fx(0, d[0], d[1], in, a), y + 3, fz(0, d[0], d[1], in, a), banner("purple", dir(-d[0], -d[1])));
			}
		}
		spawn(CitadelPlan.Kind.SENTINEL, -4, y, 2);
		spawn(CitadelPlan.Kind.SENTINEL, 4, y, -2);
	}

	/** Ninho de shulkers (andar das pontes): musgo e cristais nos cantos, tres shulkers e um bau. */
	private void nest(int floorY) {
		int y = floorY + 1;
		int in = KEEP_HALF - 1;
		int[][] corners = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
		for (int i = 0; i < corners.length; i++) {
			int sx = corners[i][0];
			int sz = corners[i][1];
			for (int a = 3; a <= in; a++) {
				for (int b = 3; b <= in; b++) {
					if (a + b <= 9) {
						set(sx * a, floorY, sz * b, MOSS);
					}
				}
			}
			set(sx * in, y, sz * (in - 2), s(CLUSTER, "facing=up"));
			set(sx * (in - 2), y, sz * in, s(CLUSTER, "facing=up"));
			set(sx * in, y + 4, sz * in, s(CLUSTER, "facing=down"));
			if (i == 3) {
				chest(sx * in, y, sz * in, dir(-sx, 0));
			} else {
				spawn(CitadelPlan.Kind.SHULKER, sx * in, y, sz * in);
			}
		}
		spawn(CitadelPlan.Kind.SHULKER, 3, y, 0);
	}

	/** Biblioteca: estantes nas paredes, mesas de leitura com velas, atris e um bau. */
	private void library(int floorY) {
		int y = floorY + 1;
		int in = KEEP_HALF - 1;
		for (int[] d : DIRECTIONS) {
			for (int a = -in; a <= in; a++) {
				if (Math.abs(a) <= 1) {
					continue;
				}
				int x = fx(0, d[0], d[1], in, a);
				int z = fz(0, d[0], d[1], in, a);
				for (int k = 0; k < 3; k++) {
					set(x, y + k, z, "minecraft:bookshelf");
				}
				set(x, y + 3, z, s(PURPUR_SLAB, "type=top"));
			}
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				// mesa 2x1 com velas e cadeiras
				set(sx * 3, y, sz * 3, VOID_PLANKS);
				set(sx * 3, y, sz * 2, VOID_PLANKS);
				set(sx * 3, y + 1, sz * 3, s("minecraft:purple_candle", "candles=3", "lit=true"));
				set(sx * 2, y, sz * 3, stairs(PURPUR_STAIRS, dir(-sx, 0)));
				set(0, y + 4, sz * 3, "minecraft:iron_chain");
				set(0, y + 3, sz * 3, s("minecraft:soul_lantern", "hanging=true"));
			}
		}
		set(4, y, -1, s("minecraft:lectern", "facing=west"));
		set(-4, y, 1, s("minecraft:lectern", "facing=east"));
		chest(4, y, 1, W);
		set(-4, y, -1, "minecraft:enchanting_table");
	}

	/** Salao de banquete (ponte alta): duas mesas compridas com velas e cadeiras, estandartes. */
	private void dining(int floorY, Sat tallest) {
		int y = floorY + 1;
		int in = keepHalf(3) - 1;
		boolean alongX = tallest.dx() == 0;
		for (int t = -3; t <= 3; t++) {
			for (int side : new int[] {-2, 2}) {
				int x = alongX ? t : side;
				int z = alongX ? side : t;
				set(x, y, z, VOID_PLANKS);
				if ((t & 1) == 0) {
					set(x, y + 1, z, s("minecraft:magenta_candle", "candles=" + (2 + Math.abs(t) % 2), "lit=true"));
				}
				int cx = alongX ? t : side + Integer.signum(side);
				int cz = alongX ? side + Integer.signum(side) : t;
				if (Math.abs(t) <= 3 && t != 0) {
					set(cx, y, cz, stairs(PURPUR_STAIRS, alongX ? dir(0, Integer.signum(side)) : dir(Integer.signum(side), 0)));
				}
			}
		}
		for (int[] d : DIRECTIONS) {
			for (int a : new int[] {-3, 3}) {
				set(fx(0, d[0], d[1], in, a), y + 3, fz(0, d[0], d[1], in, a), banner("magenta", dir(-d[0], -d[1])));
			}
		}
		set(in, y, in, LAMP);
		set(-in, y, -in, LAMP);
		set(in, y, -in, "minecraft:barrel[facing=up]");
		set(-in, y, in, "minecraft:barrel[facing=up]");
	}

	/** Laboratorio de alquimia: suportes de pocao, caldeiroes, cristais, bau e uma sentinela. */
	private void alchemy(int floorY) {
		int y = floorY + 1;
		int in = keepHalf(4) - 1;
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				set(sx * in, y, sz * in, s("minecraft:water_cauldron", "level=3"));
				set(sx * in, y, sz * (in - 1), VOID_PLANKS);
				set(sx * in, y + 1, sz * (in - 1), "minecraft:brewing_stand");
				set(sx * (in - 1), y, sz * in, CRYSTAL);
				set(sx * (in - 1), y + 1, sz * in, s(CLUSTER, "facing=up"));
			}
		}
		set(3, y, 0, "minecraft:cauldron");
		chest(-in, y, 2, E);
		set(-in, y, -2, "minecraft:barrel[facing=up]");
		spawn(CitadelPlan.Kind.SENTINEL, 2, y, 2);
	}

	/** Cofre: piso xadrez, pedestal com cabeca de dragao, dois baus, altar do Arauto Astral e dois shulkers. */
	private void vault(int floorY) {
		int y = floorY + 1;
		int in = keepHalf(VAULT_FLOOR) - 1;
		for (int dx = -in; dx <= in; dx++) {
			for (int dz = -in; dz <= in; dz++) {
				if ((dx != 0 || dz != 0) && Math.max(Math.abs(dx), Math.abs(dz)) < in) {
					set(dx, floorY, dz, (dx + dz & 1) == 0 ? VOID_BRICKS : PURPUR);
				}
			}
		}
		set(-2, y, -3, PILLAR);
		set(-2, y + 1, -3, s("minecraft:dragon_head", "rotation=0"));
		set(-3, y, -3, s(PURPUR_STAIRS, "facing=east"));
		set(-1, y, -3, s(PURPUR_STAIRS, "facing=west"));
		chest(3, y, -in, S);
		chest(-3, y, in, N);
		set(2, floorY, 2, VOID_BRICKS);
		set(2, y, 2, ALTAR);
		for (int k = 1; k <= 3; k++) {
			set(2, y + k, 2, VPlan.AIR);
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				set(sx * in, y + 4, sz * in, s(CLUSTER, "facing=down"));
			}
		}
		set(in, y, 0, CRYSTAL);
		set(in, y + 1, 0, s(CLUSTER, "facing=up"));
		set(-in, y, 0, CRYSTAL);
		set(-in, y + 1, 0, s(CLUSTER, "facing=up"));
		spawn(CitadelPlan.Kind.SHULKER, in, y, in);
		spawn(CitadelPlan.Kind.SHULKER, -in, y, -in);
	}

	/** Observatorio na cupula: atril, mesa de cartografia, "telescopio" de cristal; shulkers no terraco. */
	private void observatory(int floorY) {
		int y = floorY + 1;
		set(2, y, -2, s("minecraft:lectern", "facing=south"));
		set(-2, y, -2, "minecraft:cartography_table");
		set(2, y, 2, PILLAR);
		set(2, y + 1, 2, CRYSTAL);
		set(2, y + 2, 2, s(CLUSTER, "facing=up"));
		set(-2, y, 2, s(PURPUR_STAIRS, "facing=east"));
		spawn(CitadelPlan.Kind.SHULKER, 4, y, 4);
		spawn(CitadelPlan.Kind.SHULKER, -4, y, -4);
	}

	// ------------------------------------------------------------------ torres-satelite

	private void satellite(Sat sat, boolean tallest) {
		int cx = sat.x();
		int cz = sat.z();
		int n = sat.floors();
		int stops = sat.spire() ? n : n + 1;
		String name = "tower_" + dir(sat.dx(), sat.dz());
		p.tower(new CitadelPlan.Tower(name, cx, cz, TOWER_HALF, stops));
		for (int f = 0; f < n; f++) {
			storey(cx, cz, sat.half(f), f * FLOOR_HEIGHT, f == 0);
		}
		int topFloorY = (n - 1) * FLOOR_HEIGHT;
		// misulas do andar em balanco
		int hTop = sat.half(n - 1);
		for (int dx = -hTop; dx <= hTop; dx++) {
			for (int dz = -hTop; dz <= hTop; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				if (m == hTop) {
					int ox = Math.abs(dx) == m ? Integer.signum(dx) : 0;
					int oz = ox == 0 ? Integer.signum(dz) : 0;
					set(cx + dx, topFloorY - 1, cz + dz, stairsTop(PURPUR_STAIRS, dir(-ox, -oz)));
				}
			}
		}
		int roofY = n * FLOOR_HEIGHT;
		if (sat.spire()) {
			spireRoof(cx, cz, hTop, roofY);
		} else {
			crownRoof(cx, cz, hTop, roofY);
		}
		// banners na face de fora
		String out = dir(sat.dx(), sat.dz());
		for (int a : new int[] {-1, 1}) {
			int x = fx(cx, sat.dx(), sat.dz(), TOWER_HALF, a) + sat.dx();
			int z = fz(cz, sat.dx(), sat.dz(), TOWER_HALF, a) + sat.dz();
			set(x, 2 * FLOOR_HEIGHT - 1, z, banner(a < 0 ? "magenta" : "purple", out));
		}
		int bx = -sat.dx();
		int bz = -sat.dz();
		door(cx, cz, bx, bz, TOWER_HALF, 0, 3);
		door(cx, cz, bx, bz, sat.half(BRIDGE_FLOOR), BRIDGE_FLOOR * FLOOR_HEIGHT, 3);
		if (tallest) {
			door(cx, cz, bx, bz, sat.half(SKY_FLOOR), SKY_FLOOR * FLOOR_HEIGHT, 3);
		}
		for (int f = 0; f < stops; f++) {
			elevator(cx, f * FLOOR_HEIGHT, cz);
		}
		// salas
		List<Theme> themes = new ArrayList<>(List.of(Theme.ARMORY, Theme.QUARTERS, Theme.CONSERVATORY, Theme.STOREROOM));
		Collections.shuffle(themes, r);
		guardRoom(sat, 1);
		satNest(sat, FLOOR_HEIGHT + 1);
		for (int f = 2; f < n; f++) {
			Theme theme = f == n - 1 && n == 5 ? Theme.STAR_CHAMBER : themes.get((f - 2) % themes.size());
			satRoom(sat, f, theme, f == n - 1);
		}
		if (!sat.spire()) {
			spawn(CitadelPlan.Kind.SHULKER, cx + 3, roofY + 1, cz - 3);
			spawn(CitadelPlan.Kind.SHULKER, cx - 3, roofY + 1, cz + 3);
		}
	}

	/** Telhado em agulha: beiral de degraus e aneis alternando purpura e tijolo do End, com lucarnas e pinaculos. */
	private void spireRoof(int cx, int cz, int h, int roofY) {
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				set(cx + dx, roofY, cz + dz, m == h ? VOID_BRICKS : PURPUR);
			}
		}
		// beiral
		int eave = h + 1;
		for (int dx = -eave; dx <= eave; dx++) {
			for (int dz = -eave; dz <= eave; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) == eave) {
					set(cx + dx, roofY, cz + dz, stairs(PURPUR_STAIRS, inwardOf(dx, dz)));
				}
			}
		}
		int level = 0;
		for (int ring = h; ring >= 1; ring--) {
			for (int rep = 0; rep < 2; rep++) {
				int y = roofY + 1 + level;
				boolean brick = (ring & 1) == 0;
				for (int dx = -ring; dx <= ring; dx++) {
					for (int dz = -ring; dz <= ring; dz++) {
						int m = Math.max(Math.abs(dx), Math.abs(dz));
						if (m != ring) {
							if (m < ring) {
								set(cx + dx, y, cz + dz, rep == 1 && m == ring - 1 ? (brick ? BRICKS : PURPUR) : VPlan.AIR);
							}
							continue;
						}
						String block;
						if (rep == 1) {
							block = stairs(brick ? BRICK_STAIRS : PURPUR_STAIRS, inwardOf(dx, dz));
						} else if (ring == h - 1 && (dx == 0 || dz == 0)) {
							block = GLASS;
						} else {
							block = brick ? BRICKS : PURPUR;
						}
						set(cx + dx, y, cz + dz, block);
					}
				}
				level++;
			}
		}
		int tip = roofY + 1 + level;
		set(cx, tip, cz, PURPUR);
		set(cx, tip + 1, cz, PILLAR);
		set(cx, tip + 2, cz, PILLAR);
		set(cx, tip + 3, cz, LAMP);
		set(cx, tip + 4, cz, rod("up"));
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				int x = cx + sx * h;
				int z = cz + sz * h;
				set(x, roofY + 1, z, PILLAR);
				set(x, roofY + 2, z, PILLAR);
				set(x, roofY + 3, z, rod("up"));
			}
		}
	}

	private static String inwardOf(int dx, int dz) {
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		int ox = Math.abs(dx) == m ? Integer.signum(dx) : 0;
		int oz = ox == 0 ? Integer.signum(dz) : 0;
		return dir(-ox, -oz);
	}

	/** Telhado plano com coroa de ameias, pinaculos nos cantos e estandartes pendurados. */
	private void crownRoof(int cx, int cz, int h, int roofY) {
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				set(cx + dx, roofY, cz + dz, m == h ? VOID_BRICKS : (dx == 0 || dz == 0) ? BRICKS : PURPUR);
				if (m == h) {
					boolean corner = Math.abs(dx) == h && Math.abs(dz) == h;
					if (corner) {
						for (int k = 1; k <= 3; k++) {
							set(cx + dx, roofY + k, cz + dz, PILLAR);
						}
						set(cx + dx, roofY + 4, cz + dz, LAMP);
						set(cx + dx, roofY + 5, cz + dz, rod("up"));
					} else {
						set(cx + dx, roofY + 1, cz + dz, (dx + dz & 1) == 0 ? PURPUR : BRICK_WALL);
						if ((dx + dz & 1) == 0) {
							set(cx + dx, roofY + 2, cz + dz, s(PURPUR_SLAB, "type=bottom"));
						}
					}
				}
			}
		}
		for (int[] d : DIRECTIONS) {
			int x = fx(cx, d[0], d[1], h, 0) + d[0];
			int z = fz(cz, d[0], d[1], h, 0) + d[1];
			if (empty(x, roofY, z)) {
				set(x, roofY, z, banner("purple", dir(d[0], d[1])));
			}
		}
	}

	private void guardRoom(Sat sat, int y) {
		int cx = sat.x();
		int cz = sat.z();
		int in = TOWER_HALF - 1;
		int fxs = sat.dx() == 0 ? 1 : sat.dx();
		int fzs = sat.dz() == 0 ? 1 : sat.dz();
		// cantos do fundo: bancos e lampada
		set(cx + sat.dx() * in + Math.abs(sat.dz()) * in, y, cz + sat.dz() * in + Math.abs(sat.dx()) * in, LAMP);
		set(cx + sat.dx() * in - Math.abs(sat.dz()) * in, y, cz + sat.dz() * in - Math.abs(sat.dx()) * in, "minecraft:barrel[facing=up]");
		spawn(CitadelPlan.Kind.SENTINEL, cx + sat.dx() * 2 + Math.abs(sat.dz()) * 2 * fxs, y, cz + sat.dz() * 2 + Math.abs(sat.dx()) * 2 * fzs);
	}

	private void satNest(Sat sat, int y) {
		int cx = sat.x();
		int cz = sat.z();
		int in = TOWER_HALF - 1;
		int ax = Math.abs(sat.dz());
		int az = Math.abs(sat.dx());
		// fundo da sala (longe da porta)
		int bx = cx + sat.dx() * in;
		int bz = cz + sat.dz() * in;
		spawn(CitadelPlan.Kind.SHULKER, bx + ax * in, y, bz + az * in);
		if (chance(0.5)) {
			chest(bx - ax * in, y, bz - az * in, dir(-sat.dx(), -sat.dz()));
		} else {
			spawn(CitadelPlan.Kind.SHULKER, bx - ax * in, y, bz - az * in);
		}
		set(bx, y - 1, bz, MOSS);
		set(bx, y, bz, s(CLUSTER, "facing=up"));
	}

	/** Sala tematica de um andar alto da torre; o ultimo andar sempre tem bau. */
	private void satRoom(Sat sat, int floor, Theme theme, boolean top) {
		int cx = sat.x();
		int cz = sat.z();
		int floorY = floor * FLOOR_HEIGHT;
		int y = floorY + 1;
		int in = sat.half(floor) - 1;
		int ax = Math.abs(sat.dz());
		int az = Math.abs(sat.dx());
		int bx = cx + sat.dx() * in; // parede do fundo
		int bz = cz + sat.dz() * in;
		String toDoor = dir(-sat.dx(), -sat.dz());
		switch (theme) {
			case ARMORY -> {
				set(bx + ax * in, y, bz + az * in, s("minecraft:anvil", "facing=" + dir(ax, az)));
				set(bx - ax * in, y, bz - az * in, "minecraft:smithing_table");
				set(bx + ax * 2, y, bz + az * 2, s("minecraft:grindstone", "face=floor", "facing=" + toDoor));
				for (int side : new int[] {-2, 2}) {
					set(bx + ax * side, y + 2, bz + az * side, banner(side < 0 ? "magenta" : "black", toDoor));
				}
			}
			case QUARTERS -> {
				for (int side : new int[] {-1, 1}) {
					int hx = bx + ax * side * in;
					int hz = bz + az * side * in;
					set(hx, y, hz, s("minecraft:purple_bed", "facing=" + dir(sat.dx(), sat.dz()), "part=head"));
					set(hx - sat.dx(), y, hz - sat.dz(), s("minecraft:purple_bed", "facing=" + dir(sat.dx(), sat.dz()), "part=foot"));
				}
				set(bx, y, bz, "minecraft:barrel[facing=up]");
				set(bx, y + 1, bz, s("minecraft:purple_candle", "candles=2", "lit=true"));
			}
			case CONSERVATORY -> {
				for (int dx = -in; dx <= in; dx++) {
					for (int dz = -in; dz <= in; dz++) {
						int m = Math.max(Math.abs(dx), Math.abs(dz));
						boolean edge = m == in;
						boolean nearDoor = dx * -sat.dx() + dz * -sat.dz() >= in - 1 && Math.abs(dx * ax + dz * az) <= 1;
						if (edge && !nearDoor && (dx * ax + dz * az) != 0) {
							set(cx + dx, floorY, cz + dz, MOSS);
							set(cx + dx, y, cz + dz, noise01(cx + dx, cz + dz, 30) < 0.5 ? "mlinnotas:void_lily" : "mlinnotas:void_grass");
						}
					}
				}
				set(bx, y, bz, CRYSTAL);
				set(bx, y + 1, bz, s(CLUSTER, "facing=up"));
			}
			case STOREROOM -> {
				for (int side : new int[] {-1, 1}) {
					set(bx + ax * side * in, y, bz + az * side * in, "minecraft:barrel[facing=up]");
					set(bx + ax * side * in, y + 1, bz + az * side * in, "minecraft:barrel[facing=up]");
				}
				set(bx + ax, y, bz + az, "minecraft:decorated_pot");
			}
			case STAR_CHAMBER -> {
				set(bx, y, bz, PILLAR);
				set(bx, y + 1, bz, CRYSTAL);
				set(bx, y + 2, bz, s(CLUSTER, "facing=up"));
				for (int side : new int[] {-1, 1}) {
					set(bx + ax * side * in, y + 4, bz + az * side * in, s(CLUSTER, "facing=down"));
				}
				set(bx + ax * 2, y, bz + az * 2, s("minecraft:lectern", "facing=" + toDoor));
			}
		}
		if (top || theme == Theme.STOREROOM) {
			chest(bx - ax * 2, y, bz - az * 2, toDoor);
		}
		if (floor % 2 == 0 || top) {
			spawn(CitadelPlan.Kind.SHULKER, cx + sat.dx() * 1 - ax * 2, y, cz + sat.dz() * 1 - az * 2);
		}
	}

	// ------------------------------------------------------------------ pontes

	/**
	 * Ponte de 5 de largura no eixo (dx, dz) em {@code deckY}, de {@code from} a {@code to} (distancia do centro).
	 * Com {@code piers}, pilares e arcos ate o chao no meio; sem, arcos pendurados e lanterna.
	 */
	private void bridge(int dx, int dz, int deckY, int from, int to, boolean piers) {
		String along = dir(dx, dz);
		String back = dir(-dx, -dz);
		int middle = (from + to) / 2;
		for (int k = from; k <= to; k++) {
			for (int a = -2; a <= 2; a++) {
				int x = fx(0, dx, dz, k, a);
				int z = fz(0, dx, dz, k, a);
				boolean edge = Math.abs(a) == 2;
				set(x, deckY, z, edge ? VOID_BRICKS : a == 0 ? BRICKS : PURPUR);
				for (int y = deckY + 1; y <= deckY + 3; y++) {
					if (!edge || y > deckY + 1) {
						p.remove(x, y, z);
					}
				}
				if (edge) {
					boolean post = k == from || k == to || k == middle;
					if (post) {
						set(x, deckY + 1, z, PILLAR);
						set(x, deckY + 2, z, rod("up"));
					} else {
						set(x, deckY + 1, z, BRICK_WALL);
					}
					set(x, deckY - 1, z, stairsTop(PURPUR_STAIRS, alongDir(dx, dz, -Integer.signum(a))));
				}
			}
		}
		if (piers) {
			for (int a : new int[] {-2, 2}) {
				int x = fx(0, dx, dz, middle, a);
				int z = fz(0, dx, dz, middle, a);
				for (int y = 1; y < deckY - 1; y++) {
					set(x, y, z, y == 1 ? VOID_BRICKS : PILLAR);
				}
				set(x, deckY - 1, z, PURPUR);
				for (int side : new int[] {-1, 1}) {
					int ax = fx(0, dx, dz, middle + side, a);
					int az = fz(0, dx, dz, middle + side, a);
					set(ax, deckY - 1, az, stairsTop(PURPUR_STAIRS, side < 0 ? along : back));
				}
			}
		} else {
			int x = fx(0, dx, dz, middle, 0);
			int z = fz(0, dx, dz, middle, 0);
			set(x, deckY - 1, z, "minecraft:iron_chain");
			set(x, deckY - 2, z, "minecraft:iron_chain");
			set(x, deckY - 3, z, s("minecraft:soul_lantern", "hanging=true"));
			for (int a = -1; a <= 1; a++) {
				for (int k = from; k <= to; k++) {
					int ox = fx(0, dx, dz, k, a);
					int oz = fz(0, dx, dz, k, a);
					if (Math.abs(k - middle) >= 2 && a != 0) {
						set(ox, deckY - 1, oz, (k - middle) % 2 == 0 ? s(PURPUR_SLAB, "type=top") : PURPUR);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ cais e nave

	private void dockAndShip(int dx, int dz) {
		int cx = dx * DOCK_DIST;
		int cz = dz * DOCK_DIST;
		int h = 2;
		int floors = DECK_Y / FLOOR_HEIGHT;
		p.tower(new CitadelPlan.Tower("dock", cx, cz, h, floors + 1));
		for (int f = 0; f < floors; f++) {
			storey(cx, cz, h, f * FLOOR_HEIGHT, f == 0);
		}
		door(cx, cz, -dx, -dz, h, 0, 3);
		// plataforma de atracacao
		int ph = 4;
		for (int ox = -ph; ox <= ph; ox++) {
			for (int oz = -ph; oz <= ph; oz++) {
				int m = Math.max(Math.abs(ox), Math.abs(oz));
				set(cx + ox, DECK_Y, cz + oz, m == ph ? VOID_BRICKS : m <= h ? PURPUR : VOID_PLANKS);
				if (m == ph) {
					set(cx + ox, DECK_Y + 1, cz + oz, BRICK_WALL);
					set(cx + ox, DECK_Y - 1, cz + oz, stairsTop(PURPUR_STAIRS, inwardOf(ox, oz)));
				}
			}
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				int x = cx + sx * ph;
				int z = cz + sz * ph;
				set(x, DECK_Y + 1, z, PILLAR);
				set(x, DECK_Y + 2, z, LAMP);
				set(x, DECK_Y + 3, z, rod("up"));
			}
		}
		elevatorColumn(cx, cz, floors);
		spawn(CitadelPlan.Kind.SENTINEL, cx + Math.abs(dz) * 3, DECK_Y + 1, cz + Math.abs(dx) * 3);

		// passarela ate a nave
		int nearSide = SHIP_DIST - 5;
		for (int k = DOCK_DIST + ph; k < nearSide; k++) {
			for (int a = -2; a <= 2; a++) {
				int x = fx(0, dx, dz, k, a);
				int z = fz(0, dx, dz, k, a);
				boolean edge = Math.abs(a) == 2;
				set(x, DECK_Y, z, edge ? VOID_BRICKS : VOID_PLANKS);
				p.remove(x, DECK_Y + 1, z);
				if (edge) {
					set(x, DECK_Y + 1, z, (k % 3 == 0) ? PILLAR : "minecraft:iron_chain[axis=" + (dx != 0 ? "x" : "z") + "]");
					if (k % 3 == 0) {
						set(x, DECK_Y + 2, z, rod("up"));
					}
					set(x, DECK_Y - 1, z, stairsTop(PURPUR_STAIRS, alongDir(dx, dz, -Integer.signum(a))));
				}
			}
		}
		// abre a mureta da plataforma onde a passarela sai
		for (int a = -1; a <= 1; a++) {
			p.remove(fx(0, dx, dz, DOCK_DIST + ph, a), DECK_Y + 1, fz(0, dx, dz, DOCK_DIST + ph, a));
		}
		EndShip.build(p, dx, dz, SHIP_DIST, DECK_Y);
	}

	private void elevatorColumn(int cx, int cz, int floors) {
		for (int f = 0; f <= floors; f++) {
			elevator(cx, f * FLOOR_HEIGHT, cz);
		}
	}

	// ------------------------------------------------------------------ ilhotas, jardins e destrocos

	/** 3 ou 4 ilhotas ligadas ao terraco por pontes (longe da nave). */
	private void islets(int dockX, int dockZ) {
		double dockAngle = Math.atan2(dockZ, dockX);
		List<Double> offsets = new ArrayList<>(List.of(1.2, 2.0, Math.PI, -2.0, -1.2));
		Collections.shuffle(offsets, r);
		int count = 3 + r.nextInt(2);
		List<String> kinds = new ArrayList<>(List.of("shrine", "chorus", "gazebo", "grove"));
		Collections.shuffle(kinds, r);
		if (!kinds.subList(0, count).contains("gazebo")) {
			kinds.set(0, "gazebo"); // sempre ha um bau nas ilhotas
		}
		for (int i = 0; i < count; i++) {
			double angle = dockAngle + offsets.get(i) + (r.nextDouble() - 0.5) * 0.25;
			double dist = 42 + r.nextInt(5);
			double radius = 3.5 + r.nextDouble() * 1.5;
			int top = TERRACE_Y - 2 + r.nextInt(5);
			int ix = (int) Math.round(Math.cos(angle) * dist);
			int iz = (int) Math.round(Math.sin(angle) * dist);
			islet(ix, top, iz, radius, kinds.get(i));
			// ponte do terraco ate a ilhota
			double startR = edgeRadius(angle) - 2.0;
			double endR = dist - radius + 1.0;
			islandBridge(Math.cos(angle), Math.sin(angle), startR, endR, TERRACE_Y, top);
		}
	}

	private void islet(int cx, int top, int cz, double radius, String kind) {
		int ri = (int) Math.ceil(radius) + 1;
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz) + noise01(cx + dx, cz + dz, 40) * 0.8;
				if (d > radius) {
					continue;
				}
				double t = d / (radius + 0.5);
				int depth = 1 + (int) Math.round(Math.sqrt(1 - t * t) * radius * 1.8);
				for (int y = top; y >= top - depth; y--) {
					set(cx + dx, y, cz + dz, y == top ? MOSS : END_STONE);
				}
				if (noise01(cx + dx, cz + dz, 41) < 0.15) {
					set(cx + dx, top - depth - 1, cz + dz, s(CLUSTER, "facing=down"));
				}
			}
		}
		int y = top + 1;
		switch (kind) {
			case "shrine" -> {
				set(cx, top, cz, CRYSTAL);
				for (int k = 0; k < 3; k++) {
					set(cx, y + k, cz, k == 2 ? CRYSTAL : PILLAR);
				}
				set(cx, y + 3, cz, s(CLUSTER, "facing=up"));
				for (int[] d : DIRECTIONS) {
					set(cx + d[0] * 2, y, cz + d[1] * 2, s(CLUSTER, "facing=up"));
				}
			}
			case "chorus" -> {
				for (int[] c : new int[][] {{1, 1}, {-2, 0}, {0, -2}}) {
					chorus(cx + c[0], top, cz + c[1], 3 + r.nextInt(3));
				}
			}
			case "gazebo" -> {
				for (int sx : new int[] {-1, 1}) {
					for (int sz : new int[] {-1, 1}) {
						int height = (sx == 1 && sz == 1) ? 2 : 4;
						for (int k = 0; k < height; k++) {
							set(cx + sx * 2, y + k, cz + sz * 2, PILLAR);
						}
					}
				}
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (dx >= 1 && dz >= 0) {
							continue; // telhado quebrado
						}
						set(cx + dx, y + 4, cz + dz, Math.max(Math.abs(dx), Math.abs(dz)) == 2 ? s(PURPUR_SLAB, "type=bottom") : PURPUR);
					}
				}
				set(cx + 2, y, cz + 1, BRICK_SLAB);
				chest(cx, y, cz, S);
				set(cx - 1, y, cz, s("minecraft:purple_candle", "candles=4", "lit=true"));
				set(cx, y + 3, cz, s("minecraft:soul_lantern", "hanging=true"));
			}
			default -> {
				voidTree(cx, top, cz, 4);
				set(cx + 2, y, cz - 1, "mlinnotas:void_lily");
				set(cx - 2, y, cz + 1, "mlinnotas:end_mushroom");
			}
		}
	}

	/** Ponte rasterizada do terraco ate uma ilhota, de largura 3, subindo ou descendo no maximo 1 por bloco. */
	private void islandBridge(double ux, double uz, double startR, double endR, int startY, int endY) {
		double length = endR - startR;
		double px = -uz;
		double pz = ux;
		int steps = (int) Math.ceil(length * 4);
		java.util.Map<Long, Integer> deck = new java.util.LinkedHashMap<>();
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			double along = startR + length * t;
			int y = (int) Math.round(startY + (endY - startY) * t);
			for (double o = -1.0; o <= 1.0; o += 0.5) {
				int x = (int) Math.round(ux * along + px * o);
				int z = (int) Math.round(uz * along + pz * o);
				deck.putIfAbsent(CitadelPlan.key(x, 0, z), y);
			}
		}
		for (java.util.Map.Entry<Long, Integer> e : deck.entrySet()) {
			int x = CitadelPlan.keyX(e.getKey());
			int z = CitadelPlan.keyZ(e.getKey());
			int y = e.getValue();
			String here = p.get(x, y, z);
			if (here == null || VPlan.id(here).equals("end_moss") || VPlan.id(here).equals("air")) {
				set(x, y, z, here == null ? VOID_PLANKS : here);
			}
			if (p.get(x, y - 1, z) == null) {
				set(x, y - 1, z, PURPUR);
			}
			for (int k = 1; k <= 3; k++) {
				String above = p.get(x, y + k, z);
				if (above != null && !VPlan.id(above).equals("air") && CitadelPlan.obstacle(above)) {
					String id = VPlan.id(above);
					if (id.equals("end_stone") || id.equals("end_moss")) {
						p.remove(x, y + k, z);
					}
				}
			}
		}
		// guarda-corpos de correntes e postes nas laterais
		for (int i = 0; i <= steps; i += 6) {
			double t = i / (double) steps;
			double along = startR + length * t;
			int y = (int) Math.round(startY + (endY - startY) * t);
			for (double o : new double[] {-2.0, 2.0}) {
				int x = (int) Math.round(ux * along + px * o);
				int z = (int) Math.round(uz * along + pz * o);
				if (empty(x, y, z) && empty(x, y + 1, z) && !deck.containsKey(CitadelPlan.key(x, 0, z)) && touchesDeck(deck, x, y, z)) {
					set(x, y, z, VOID_BRICKS);
					set(x, y + 1, z, PILLAR);
					set(x, y + 2, z, rod("up"));
				}
			}
		}
	}

	private static boolean touchesDeck(java.util.Map<Long, Integer> deck, int x, int y, int z) {
		for (int[] d : DIRECTIONS) {
			Integer at = deck.get(CitadelPlan.key(x + d[0], 0, z + d[1]));
			if (at != null && at == y) {
				return true;
			}
		}
		return false;
	}

	private void chorus(int x, int ground, int z, int height) {
		set(x, ground, z, END_STONE);
		for (int k = 1; k <= height; k++) {
			boolean last = k == height;
			set(x, ground + k, z, last ? s("minecraft:chorus_flower", "age=5")
					: s("minecraft:chorus_plant", "down=true", "up=true", "north=false", "south=false", "east=false", "west=false"));
		}
	}

	private void voidTree(int x, int ground, int z, int height) {
		for (int k = 1; k <= height; k++) {
			set(x, ground + k, z, s(VOID_LOG, "axis=y"));
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					int y = ground + height + dy;
					if (dx * dx + dz * dz + dy * dy * 2 <= 5 && empty(x + dx, y, z + dz)) {
						set(x + dx, y, z + dz, VOID_LEAVES);
					}
				}
			}
		}
		set(x, ground + height + 2, z, VOID_LEAVES);
	}

	/** Jardins: grama e lirios do vazio, cogumelos, moitas de coro e arvores do vazio no musgo livre. */
	private void gardens() {
		for (int x = -RADIUS - 2; x <= RADIUS + 2; x++) {
			for (int z = -RADIUS - 2; z <= RADIUS + 2; z++) {
				double dist = Math.sqrt(x * x + z * z);
				int ground = dist <= PLATEAU ? 0 : TERRACE_Y;
				if (!"end_moss".equals(p.idAt(x, ground, z)) || !empty(x, ground + 1, z) || !empty(x, ground + 2, z)) {
					continue;
				}
				double n = noise01(x, z, 50);
				boolean terrace = ground == TERRACE_Y;
				if (terrace && n < 0.012 && clearAround(x, ground, z, 2)) {
					voidTree(x, ground, z, 3 + (int) (hash(x, z, 51) & 1));
				} else if (n < (terrace ? 0.05 : 0.02) && clearAround(x, ground, z, 2)) {
					chorus(x, ground, z, 2 + (int) (hash(x, z, 52) % 3 + 3) % 3);
				} else if (n < 0.13) {
					set(x, ground + 1, z, "mlinnotas:void_lily");
				} else if (n < 0.16) {
					set(x, ground + 1, z, "mlinnotas:end_mushroom");
				} else if (n < 0.40) {
					set(x, ground + 1, z, "mlinnotas:void_grass");
				}
			}
		}
	}

	/** Nada construido num raio {@code d} (pisos de caminho, paredes, pontes) em volta do ponto. */
	private boolean clearAround(int x, int ground, int z, int d) {
		for (int dx = -d; dx <= d; dx++) {
			for (int dz = -d; dz <= d; dz++) {
				String at = p.idAt(x + dx, ground, z + dz);
				if (at != null && !at.equals("end_moss") && !at.equals("void_bricks")) {
					return false;
				}
				for (int y = ground + 1; y <= ground + 6; y++) {
					if (!empty(x + dx, y, z + dz) && !isPlant(p.idAt(x + dx, y, z + dz))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean isPlant(String id) {
		return id != null && (id.equals("void_grass") || id.equals("void_lily") || id.equals("end_mushroom"));
	}

	/** Rochas soltas que flutuam em volta (algumas com um resto de alvenaria), longe de tudo. */
	private void debris() {
		int wanted = 4 + r.nextInt(3);
		int placed = 0;
		for (int attempt = 0; attempt < 80 && placed < wanted; attempt++) {
			double angle = r.nextDouble() * Math.PI * 2;
			double dist = 38 + r.nextDouble() * 16;
			int cx = (int) Math.round(Math.cos(angle) * dist);
			int cz = (int) Math.round(Math.sin(angle) * dist);
			int cy = -12 + r.nextInt(36);
			double radius = 2.3 + r.nextDouble() * 1.0;
			int ri = (int) Math.ceil(radius) + 3;
			boolean free = true;
			for (int dx = -ri; dx <= ri && free; dx++) {
				for (int dy = -ri; dy <= ri && free; dy++) {
					for (int dz = -ri; dz <= ri && free; dz++) {
						free = empty(cx + dx, cy + dy, cz + dz);
					}
				}
			}
			// nem em cima de ilhota/ilha (coluna inteira livre perto do centro)
			for (int y = cy - 40; y <= cy + 40 && free; y++) {
				free = empty(cx, y, cz);
			}
			if (!free) {
				continue;
			}
			boolean masonry = chance(0.5);
			for (int dx = -ri; dx <= ri; dx++) {
				for (int dy = -ri; dy <= ri; dy++) {
					for (int dz = -ri; dz <= ri; dz++) {
						double e = Math.sqrt(dx * dx + dy * dy * 2.2 + dz * dz) + noise01(cx + dx, cz + dz + dy * 31, 60) * 0.6;
						if (e > radius) {
							continue;
						}
						String block = END_STONE;
						if (masonry && dy >= 1) {
							block = dy == 1 ? BRICKS : PURPUR;
						} else if (dy > 0 && !masonry) {
							block = MOSS;
						}
						set(cx + dx, cy + dy, cz + dz, block);
					}
				}
			}
			set(cx, cy, cz, END_STONE);
			p.floatingBody(cx, cy, cz);
			placed++;
		}
	}
}
