package com.mlin.notas.boss;

import java.util.Random;

/**
 * Planeja os covis de chefe (logica pura, mesma semente = mesmo covil). Referencias de forma (sem copiar schematics):
 * <ul>
 *   <li>Santuario de Cobalto: circulos de pedras em pe e monolitos em ruina ("Monoliths Remnants", minecraftmaps.com),
 *       a estrutura de invocacao com pilares e arena limpa do datapack "Sandstone Golem Boss" (planetminecraft.com) e o
 *       altar 9x9 com quatro suportes do Golem de Pedra-negra do AdventureZ. Estrado em dois degraus, 8 pedras em pe
 *       de alturas variadas com veio de cobalto, uma caida, caminhos cardeais e postes com lanterna.</li>
 *   <li>Santuario Abissal: o centro das cidades antigas (schematics "Ancient City Center" em minecraftmaps.com) e o
 *       Templo Antigo do Deeper and Darker (salao central, moldura de deepslate reforcado, trono). Salao 23x23 de
 *       deepslate com colunas, sculk no chao e no teto, estrado com moldura e uma escadaria que sobe para as cavernas.</li>
 *   <li>Forja do Colosso: o Ninho do Devorador de Lava (Legendary Monsters: fosso de 19 blocos, colunas de basalto,
 *       magma e lava rente ao chao, caveira e fogueira de alma) e a arena do Nether Gauntlet (Bosses of Mass
 *       Destruction: colunas de basalto como cobertura em volta do centro). Arena redonda com anel de lava cruzado por
 *       quatro pontes, mureta com quatro entradas, colunas de basalto e o trono onde o colosso dorme.</li>
 * </ul>
 */
public final class LairPlanner {
	public static final int SHRINE_RADIUS = 13;
	public static final int SANCTUM_HALF = 11;
	public static final int SANCTUM_HEIGHT = 10;
	public static final int FORGE_RADIUS = 15;

	private LairPlanner() {
	}

	public static LairPlan plan(LairKind kind, long seed) {
		LairPlan plan = new LairPlan(kind);
		switch (kind) {
			case COBALT_SHRINE -> shrine(plan, seed);
			case ABYSSAL_SANCTUM -> sanctum(plan, seed);
			case COLOSSUS_FORGE -> forge(plan, seed);
		}
		return plan;
	}

	static long hash(long seed, int x, int y, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (y * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return h;
	}

	/** Numero em [0, 1) fixo por posicao. */
	static double noise(long seed, int x, int y, int z) {
		return (hash(seed, x, y, z) >>> 11) * 0x1.0p-53;
	}

	// ------------------------------------------------------------------------------------------------ santuario

	private static void shrine(LairPlan plan, long seed) {
		Random random = new Random(seed);
		int r = SHRINE_RADIUS;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > r + 0.4) {
					continue;
				}
				plan.set(x, -1, z, LairPart.FOUNDATION);
				double n = noise(seed, x, 0, z);
				LairPart floor;
				if ((Math.abs(x) <= 1 || Math.abs(z) <= 1) && d > 5.5) {
					floor = LairPart.PATH;
				} else if (d > r - 1.5 && n < 0.45) {
					floor = LairPart.DECOR; // borda tomada pelo musgo
				} else {
					floor = n < 0.6 ? LairPart.FLOOR : n < 0.85 ? LairPart.FLOOR_ACCENT : LairPart.FLOOR_CRACKED;
				}
				plan.set(x, 0, z, floor);
				for (int y = 1; y <= 10; y++) {
					plan.set(x, y, z, LairPart.AIR);
				}
			}
		}
		// estrado: degrau largo (11x11, borda de laje) e degrau de cima (7x7, borda de laje)
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				int m = Math.max(Math.abs(x), Math.abs(z));
				plan.set(x, 1, z, m == 5 ? LairPart.SLAB : LairPart.DAIS);
				if (m <= 3) {
					plan.set(x, 2, z, m == 3 ? LairPart.SLAB : LairPart.DAIS);
				}
			}
		}
		plan.set(0, 3, 0, LairPart.ALTAR);
		plan.mark(LairPlan.MARKER_ALTAR, 0, 3, 0);
		plan.mark(LairPlan.MARKER_BOSS, 0, 4, 0);
		// quatro suportes nos cantos do estrado (mureta + lanterna)
		for (int[] c : new int[][] {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
			plan.set(c[0], 2, c[1], LairPart.WALL_LOW);
			plan.set(c[0], 3, c[1], LairPart.LIGHT);
		}
		plan.set(0, 2, -4, LairPart.CHEST);
		plan.mark(LairPlan.MARKER_CHEST, 0, 2, -4);

		// pedras em pe (2x2), uma delas quebrada com o resto caido no chao
		int broken = random.nextInt(8);
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 8 + i * Math.PI / 4;
			int sx = (int) Math.round(Math.cos(a) * 10.0);
			int sz = (int) Math.round(Math.sin(a) * 10.0);
			int height = i == broken ? 2 : 4 + random.nextInt(4);
			int vein = 1 + random.nextInt(Math.max(1, height - 1));
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					for (int y = 1; y <= height; y++) {
						LairPart part = y == height && i != broken ? LairPart.PILLAR_CAP
								: y == vein && dx == 0 && dz == 0 ? LairPart.WALL_ACCENT : LairPart.PILLAR;
						plan.set(sx + dx, y, sz + dz, part);
					}
				}
			}
			if (i == broken) {
				// pedaco caido para fora do circulo, deitado
				int ox = (int) Math.signum(Math.round(Math.cos(a) * 10.0));
				int oz = (int) Math.signum(Math.round(Math.sin(a) * 10.0));
				for (int k = 2; k <= 3; k++) {
					int fx = sx + ox * k;
					int fz = sz + oz * k;
					if (plan.get(fx, 0, fz) != null) {
						plan.set(fx, 1, fz, LairPart.RUBBLE);
					}
				}
			}
		}
		plan.mark(LairPlan.MARKER_ENTRANCE, 0, 1, r);
	}

	// ------------------------------------------------------------------------------------------------ abissal

	private static void sanctum(LairPlan plan, long seed) {
		int h = SANCTUM_HALF;
		int top = SANCTUM_HEIGHT + 1;
		for (int x = -h - 1; x <= h + 1; x++) {
			for (int z = -h - 1; z <= h + 1; z++) {
				boolean wall = Math.abs(x) == h + 1 || Math.abs(z) == h + 1;
				plan.set(x, -1, z, LairPart.FOUNDATION);
				for (int y = 0; y <= top; y++) {
					LairPart part;
					double n = noise(seed, x, y, z);
					if (wall) {
						part = y == 4 || y == 8 ? LairPart.WALL_ACCENT : n < 0.18 ? LairPart.WALL_CRACKED : LairPart.WALL;
					} else if (y == 0) {
						if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
							part = LairPart.FLOOR_ACCENT;
						} else {
							double patch = noise(seed, x / 3, 99, z / 3);
							part = patch < 0.35 && n < 0.8 ? LairPart.DECOR : n < 0.12 ? LairPart.FLOOR_CRACKED : LairPart.FLOOR;
						}
					} else if (y == top) {
						part = noise(seed, x / 2, 77, z / 2) < 0.3 ? LairPart.DECOR : LairPart.CEILING;
					} else {
						part = LairPart.AIR;
					}
					plan.set(x, y, z, part);
				}
			}
		}
		// colunas com base e capitel, lanterna de alma na base pelo lado de dentro
		for (int px : new int[] {-7, 7}) {
			for (int pz : new int[] {-6, -1, 4, 9}) {
				for (int y = 1; y <= SANCTUM_HEIGHT; y++) {
					plan.set(px, y, pz, y == 1 || y == SANCTUM_HEIGHT ? LairPart.PILLAR_CAP : LairPart.PILLAR);
				}
				plan.set(px - Integer.signum(px), 1, pz, LairPart.LIGHT);
			}
		}
		// lanternas penduradas por correntes no meio do salao
		for (int[] c : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}, {0, 7}}) {
			plan.set(c[0], SANCTUM_HEIGHT, c[1], LairPart.CHAIN);
			plan.set(c[0], SANCTUM_HEIGHT - 1, c[1], LairPart.CHAIN);
			plan.set(c[0], SANCTUM_HEIGHT - 2, c[1], LairPart.HANGING_LIGHT);
		}
		// estrado ao norte em dois degraus, com moldura de deepslate reforcado na parede
		for (int x = -4; x <= 4; x++) {
			for (int z = -h; z <= -6; z++) {
				plan.set(x, 1, z, z == -6 ? LairPart.SLAB : LairPart.DAIS);
			}
		}
		for (int x = -2; x <= 2; x++) {
			for (int z = -h; z <= -8; z++) {
				plan.set(x, 2, z, z == -8 ? LairPart.SLAB : LairPart.DAIS);
			}
		}
		for (int y = 2; y <= 9; y++) {
			for (int x = -4; x <= 4; x++) {
				boolean edge = Math.abs(x) == 4 || y == 9;
				if (y == 2 && Math.abs(x) <= 2) {
					continue; // degrau de cima
				}
				if (edge) {
					plan.set(x, y, -h, LairPart.FRAME);
				} else if (y >= 3) {
					plan.set(x, y, -h - 1, LairPart.DECOR); // sculk no fundo da moldura
				}
			}
		}
		plan.set(0, 3, -10, LairPart.ALTAR);
		plan.mark(LairPlan.MARKER_ALTAR, 0, 3, -10);
		plan.mark(LairPlan.MARKER_BOSS, 0, 4, -10);
		plan.set(2, 3, -10, LairPart.CANDLE);
		plan.set(-2, 3, -10, LairPart.CANDLE);
		plan.set(3, 2, -9, LairPart.CHEST);
		plan.set(-3, 2, -9, LairPart.CHEST);
		plan.mark(LairPlan.MARKER_CHEST, 3, 2, -9);
		// sensores e gritadores (sem invocar warden) pelo salao
		plan.set(5, 1, 3, LairPart.SENSOR);
		plan.set(-5, 1, 6, LairPart.SENSOR);
		plan.set(-4, 1, -2, LairPart.SENSOR);
		plan.set(9, 1, -9, LairPart.SHRIEKER);
		plan.set(-9, 1, -9, LairPart.SHRIEKER);

		// porta ao sul e escadaria subindo ate as cavernas
		for (int x = -1; x <= 1; x++) {
			for (int y = 1; y <= 4; y++) {
				plan.set(x, y, h + 1, LairPart.AIR);
			}
		}
		int steps = 12;
		for (int i = 0; i < steps; i++) {
			int z = h + 2 + i;
			int fy = i;
			for (int x = -2; x <= 2; x++) {
				for (int y = fy - 1; y <= fy + 5; y++) {
					LairPart part;
					if (Math.abs(x) == 2 || y == fy + 5) {
						part = noise(seed, x, y, z) < 0.2 ? LairPart.WALL_CRACKED : LairPart.WALL;
					} else if (y <= fy) {
						part = y == fy ? LairPart.FLOOR : LairPart.FOUNDATION;
					} else {
						part = LairPart.AIR;
					}
					plan.set(x, y, z, part);
				}
			}
		}
		// saida: a escada acaba aberta (a rocha em volta e cavada no mundo ate a caverna mais proxima)
		plan.mark(LairPlan.MARKER_ENTRANCE, 0, steps, h + 1 + steps);
	}

	// ------------------------------------------------------------------------------------------------ forja

	private static void forge(LairPlan plan, long seed) {
		Random random = new Random(seed);
		int r = FORGE_RADIUS;
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > r + 0.5) {
					continue;
				}
				boolean cardinal = Math.abs(x) <= 1 || Math.abs(z) <= 1;
				double n = noise(seed, x, 0, z);
				plan.set(x, -1, z, LairPart.FOUNDATION);
				LairPart floor;
				if (d >= 8.5 && d <= 9.5 && !cardinal) {
					floor = LairPart.LIQUID;
				} else if (d >= 8.0 && d <= 10.0 && cardinal) {
					floor = LairPart.FLOOR_ACCENT; // ponte sobre o anel de lava
				} else {
					floor = n < 0.55 ? LairPart.FLOOR : n < 0.75 ? LairPart.FLOOR_ACCENT : n < 0.92 ? LairPart.FLOOR_CRACKED : LairPart.DECOR;
				}
				plan.set(x, 0, z, floor);
				boolean rim = d > r - 1.0;
				for (int y = 1; y <= 14; y++) {
					LairPart part = LairPart.AIR;
					if (rim && !cardinal) {
						if (y <= 3) {
							part = noise(seed, x, y, z) < 0.12 ? LairPart.WALL_ACCENT : LairPart.WALL;
						} else if (y == 4) {
							part = LairPart.WALL_LOW;
						}
					}
					plan.set(x, y, z, part);
				}
			}
		}
		// colunas de basalto fora das entradas, cada uma com uma coluna baixa ao lado, topo de magma
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 8 + i * Math.PI / 4;
			int cx = (int) Math.round(Math.cos(a) * 12.0);
			int cz = (int) Math.round(Math.sin(a) * 12.0);
			int height = 6 + random.nextInt(5);
			for (int y = 1; y <= height; y++) {
				plan.set(cx, y, cz, y == height ? LairPart.PILLAR_CAP : LairPart.PILLAR);
			}
			int ox = cx + (int) Math.signum(Math.round(Math.cos(a) * 3));
			int oz = cz + (int) Math.signum(Math.round(Math.sin(a) * 3));
			int low = 2 + random.nextInt(3);
			for (int y = 1; y <= low; y++) {
				plan.set(ox, y, oz, LairPart.RUBBLE);
			}
		}
		// trono: degrau largo (raio 4,5 com borda de laje) e degrau de cima (raio 2,5), fogueiras de alma
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d <= 4.5) {
					plan.set(x, 1, z, LairPart.DAIS);
				} else if (d <= 5.5) {
					plan.set(x, 1, z, LairPart.SLAB);
				}
				if (d <= 2.5) {
					plan.set(x, 2, z, LairPart.DAIS);
				}
			}
		}
		for (int[] c : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
			plan.set(c[0], 2, c[1], LairPart.CAMPFIRE);
		}
		plan.mark(LairPlan.MARKER_BOSS, 0, 3, 0);
		plan.set(8, 1, -8, LairPart.CHEST);
		plan.set(-8, 1, 8, LairPart.CHEST);
		plan.mark(LairPlan.MARKER_CHEST, 8, 1, -8);
		plan.set(6, 1, 11, LairPart.SKULL);
		plan.set(-10, 1, -2, LairPart.SKULL);
		plan.mark(LairPlan.MARKER_ENTRANCE, 0, 1, r);
	}
}
