package com.mlin.notas.ruins;

import com.mlin.notas.ruins.RuinPlan.Dir;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.mlin.notas.ruins.RuinPart.*;

/**
 * Monta o plano de cada ruina a partir de uma semente (mesma semente = mesma ruina). Tudo e construido virado para
 * o sul (entrada em +z), depois espelhado ou nao e girado por um angulo sorteado. Logica pura.
 *
 * <p>Referencias de construcoes da comunidade (so inspiracao de forma, camadas e paleta; nada copiado): torres em
 * ruinas da GrabCraft ("Medieval Tower 1 Ruins", "Detailed Medieval Watchtower 1": base alargada, misulas e ameias,
 * mistura de tijolos rachados/musgosos e muita trepadeira), "Medieval Church Ruins" (GrabCraft) e "Grimspire Gate"
 * (Planet Minecraft) para a capela (nave com contrafortes, vitrais quebrados, arvore crescendo dentro), pocos
 * medievais com telhado de duas aguas (GrabCraft "Medieval Simple Well") e entradas de mina recuadas com moldura de
 * troncos, patio com trilhos, carrinho e caixotes (guia de entradas de caverna do minecraft.how, "Medieval Mine" do
 * mcbuild.org, ideias de entradas de mina da Gamer Empire).
 */
public final class RuinPlanner {
	private RuinPlanner() {
	}

	public static RuinPlan plan(RuinKind kind, long seed) {
		Random random = new Random(seed * 31L + kind.ordinal());
		RuinPlan plan = new RuinPlan(kind);
		switch (kind) {
			case WATCHTOWER -> watchtower(plan, random);
			case CAMP -> camp(plan, random);
			case WELL -> well(plan, random);
			case CHAPEL -> chapel(plan, random);
			case MINE -> mine(plan, random);
		}
		plan.removeFloating();
		// ponto de chegada a pe: 2 blocos ao sul do que ha na superficie
		int front = Integer.MIN_VALUE;
		for (Map.Entry<Long, RuinPart> entry : plan.blocks().entrySet()) {
			if (RuinPlan.keyY(entry.getKey()) >= -1 && entry.getValue() != AIR) {
				front = Math.max(front, RuinPlan.keyZ(entry.getKey()));
			}
		}
		plan.mark(RuinPlan.MARKER_ENTRANCE, 0, 0, front + 2);
		RuinPlan out = random.nextBoolean() ? plan.mirrored() : plan;
		return out.rotated(random.nextInt(4));
	}

	// --- ferramentas comuns ---

	/**
	 * Pedra de parede com desgaste: mistura por peso de pedra principal, secundaria, musgosa e rachada. Mais musgo
	 * perto do chao (umidade) e mais rachaduras perto do topo quebrado.
	 */
	static RuinPart stone(Random r, int y, int top) {
		int roll = r.nextInt(100);
		int moss = y <= 1 ? 30 : y <= 4 ? 18 : 8;
		int crack = y >= top - 2 ? 24 : 10;
		if (roll < moss) {
			return WALL_MOSSY;
		}
		roll -= moss;
		if (roll < crack) {
			return WALL_CRACKED;
		}
		return roll - crack < 18 ? WALL_ALT : WALL;
	}

	/** Rocha bruta (revestimento e chao da mina). */
	private static RuinPart rock(Random r) {
		int roll = r.nextInt(100);
		return roll < 45 ? FOUNDATION : roll < 80 ? WALL_ALT : WALL_MOSSY;
	}

	private static boolean free(RuinPlan plan, int x, int y, int z) {
		RuinPart p = plan.get(x, y, z);
		return p == null || p == AIR;
	}

	/** Algo pode ficar apoiado em cima de (x, y, z) (o terreno fora do plano e chao abaixo de y=0). */
	private static boolean supports(RuinPlan plan, int x, int y, int z) {
		RuinPart p = plan.get(x, y, z);
		return p != null ? p.obstacle() : y < 0;
	}

	private static boolean wallish(RuinPart p) {
		return p == WALL || p == WALL_ALT || p == WALL_MOSSY || p == WALL_CRACKED || p == ACCENT || p == FOUNDATION;
	}

	/** Garante chao do plano embaixo de algo posto fora do alicerce (a camada y=-1 desce ate o terreno). */
	private static void ground(RuinPlan plan, int x, int z) {
		if (plan.get(x, -1, z) == null) {
			plan.set(x, -1, z, SOIL);
		}
	}

	private static void airIfEmpty(RuinPlan plan, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					plan.setIfAbsent(x, y, z, AIR);
				}
			}
		}
	}

	private static void fill(RuinPlan plan, int x0, int y0, int z0, int x1, int y1, int z1, RuinPart part) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					plan.set(x, y, z, part);
				}
			}
		}
	}

	/** Pedra de teleporte (2 de altura) com alicerce embaixo. */
	private static void waystone(RuinPlan plan, int x, int z) {
		plan.set(x, -1, z, FOUNDATION);
		plan.set(x, 0, z, WAYSTONE);
		plan.set(x, 1, z, WAYSTONE_TOP);
		plan.mark(RuinPlan.MARKER_WAYSTONE, x, 0, z);
	}

	private static void chest(RuinPlan plan, int x, int y, int z, Dir facing) {
		plan.set(x, y, z, CHEST, facing);
		if (plan.marker(RuinPlan.MARKER_CHEST) == null) {
			plan.mark(RuinPlan.MARKER_CHEST, x, y, z);
		}
	}

	/**
	 * Monte de entulho solto de ate {@code height} blocos na coluna (x, z), no primeiro espaco livre que tenha apoio
	 * embaixo. O de cima as vezes e laje ou degrau caido.
	 */
	private static void rubble(RuinPlan plan, Random r, int x, int z, int height) {
		rubble(plan, r, x, 0, z, height);
	}

	/** Entulho a partir da altura {@code y0} (sobe no maximo 1 bloco para achar espaco, nunca para cima de muros). */
	private static void rubble(RuinPlan plan, Random r, int x, int y0, int z, int height) {
		int y = y0;
		while (!free(plan, x, y, z)) {
			if (++y > y0 + 1) {
				return;
			}
		}
		if (!supports(plan, x, y - 1, z)) {
			return;
		}
		if (y == 0) {
			ground(plan, x, z);
		}
		for (int i = 0; i < height; i++) {
			if (!free(plan, x, y + i, z)) {
				return;
			}
			boolean last = i == height - 1;
			int roll = r.nextInt(6);
			if (last && roll == 0) {
				plan.set(x, y + i, z, SLAB);
			} else if (last && roll == 1) {
				plan.set(x, y + i, z, STAIRS, Dir.values()[r.nextInt(4)]);
			} else {
				plan.set(x, y + i, z, stone(r, 0, 0));
			}
			plan.markLoose(x, y + i, z);
		}
	}

	/** Trepadeiras penduradas nas faces livres das paredes (de y=2 para cima), descendo ate {@code maxLen} blocos. */
	private static void vines(RuinPlan plan, Random r, double chance, int maxLen) {
		List<Long> walls = new ArrayList<>();
		for (Map.Entry<Long, RuinPart> entry : plan.blocks().entrySet()) {
			if (wallish(entry.getValue()) && RuinPlan.keyY(entry.getKey()) >= 2) {
				walls.add(entry.getKey());
			}
		}
		for (long k : walls) {
			int x = RuinPlan.keyX(k);
			int y = RuinPlan.keyY(k);
			int z = RuinPlan.keyZ(k);
			for (Dir d : Dir.values()) {
				if (r.nextDouble() >= chance) {
					continue;
				}
				int nx = x + d.dx;
				int nz = z + d.dz;
				int len = 1 + r.nextInt(maxLen);
				for (int i = 0; i < len; i++) {
					int yy = y - i;
					if (yy < 0 || !free(plan, nx, yy, nz) || !wallish(plan.get(x, yy, z))) {
						break;
					}
					plan.set(nx, yy, nz, VINE, d.rotate(2));
				}
			}
		}
	}

	/** Moitas de folhas por cima de muros quebrados (abaixo de {@code maxY}), as vezes caindo para o lado. */
	private static void wallTopLeaves(RuinPlan plan, Random r, double chance, int maxY) {
		List<Long> tops = new ArrayList<>();
		for (Map.Entry<Long, RuinPart> entry : plan.blocks().entrySet()) {
			long k = entry.getKey();
			int y = RuinPlan.keyY(k);
			if (wallish(entry.getValue()) && y >= 0 && y < maxY && free(plan, RuinPlan.keyX(k), y + 1, RuinPlan.keyZ(k))) {
				tops.add(k);
			}
		}
		for (long k : tops) {
			if (r.nextDouble() >= chance) {
				continue;
			}
			int x = RuinPlan.keyX(k);
			int y = RuinPlan.keyY(k) + 1;
			int z = RuinPlan.keyZ(k);
			plan.set(x, y, z, LEAVES);
			if (r.nextInt(3) == 0) {
				Dir d = Dir.values()[r.nextInt(4)];
				if (free(plan, x + d.dx, y, z + d.dz)) {
					plan.set(x + d.dx, y, z + d.dz, LEAVES);
				}
			}
		}
	}

	/** Grama e flores no chao de terra (ou fora da ruina) dentro do retangulo. */
	private static void plants(RuinPlan plan, Random r, int x0, int z0, int x1, int z1, double chance) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				RuinPart below = plan.get(x, -1, z);
				if (!free(plan, x, 0, z) || (below != null && below != SOIL) || r.nextDouble() >= chance) {
					continue;
				}
				ground(plan, x, z);
				plan.set(x, 0, z, r.nextInt(4) == 0 ? FLOWER : PLANT);
			}
		}
	}

	/** Tapete de musgo em pisos livres na altura {@code y}. */
	private static void moss(RuinPlan plan, Random r, int y, int x0, int z0, int x1, int z1, double chance) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				RuinPart below = plan.get(x, y - 1, z);
				if (free(plan, x, y, z) && below != null && below.standable() && r.nextDouble() < chance) {
					plan.set(x, y, z, MOSS_CARPET);
				}
			}
		}
	}

	private static void setIfFree(RuinPlan plan, int x, int y, int z, RuinPart part) {
		if (free(plan, x, y, z)) {
			plan.set(x, y, z, part);
		}
	}

	private static void setIfFree(RuinPlan plan, int x, int y, int z, RuinPart part, Dir dir) {
		if (free(plan, x, y, z)) {
			plan.set(x, y, z, part, dir);
		}
	}

	// --- torre de vigia caida ---

	/** Raio da torre: paredes em |x| ou |z| = 4 (base 9x9). */
	static final int TOWER_R = 4;
	/** Altura minima do deque do topo (a torre fica com 14 a 16 de deque, ~18 com ameias). */
	static final int TOWER_MIN_TOP = 14;

	private static void watchtower(RuinPlan plan, Random r) {
		int rr = TOWER_R;
		int top = TOWER_MIN_TOP + r.nextInt(3);
		// plataforma de alicerce com borda irregular
		for (int x = -rr - 3; x <= rr + 3; x++) {
			for (int z = -rr - 3; z <= rr + 3; z++) {
				int d = Math.max(Math.abs(x), Math.abs(z));
				if (d <= rr + 1) {
					plan.set(x, -1, z, FOUNDATION);
				} else if (d == rr + 2 && r.nextInt(3) > 0) {
					plan.set(x, -1, z, r.nextBoolean() ? FOUNDATION : SOIL);
				}
			}
		}
		// brecha do desabamento: parede oeste, metade sul (o espelho sorteado leva para o leste)
		int bx = -rr;
		int bz = 1 + r.nextInt(2);
		int low = 1 + r.nextInt(2);
		int[][] tops = new int[2 * rr + 1][2 * rr + 1];
		for (int x = -rr; x <= rr; x++) {
			for (int z = -rr; z <= rr; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) != rr) {
					continue;
				}
				int d = Math.abs(x - bx) + Math.abs(z - bz);
				int h = Math.min(top, low + d * 2 + r.nextInt(2));
				tops[x + rr][z + rr] = h;
				boolean corner = Math.abs(x) == rr && Math.abs(z) == rr;
				for (int y = 0; y <= h; y++) {
					plan.set(x, y, z, corner ? (y % 2 == 0 ? ACCENT : WALL_ALT) : stone(r, y, h));
				}
			}
		}
		// porta ao sul com arco e janelas de seteira
		for (int y = 0; y <= 2; y++) {
			plan.set(0, y, rr, AIR);
		}
		if (tops[rr][2 * rr] >= 3) {
			plan.set(0, 3, rr, ACCENT);
		}
		int[][] slits = {{0, -rr, 2}, {rr, 0, 2}, {0, -rr, 7}, {rr, 0, 7}, {-rr, 0, 7}, {0, rr, 7}, {rr, 0, 12}, {0, -rr, 12}};
		for (int[] s : slits) {
			if (tops[s[0] + rr][s[1] + rr] > s[2] + 2) {
				plan.set(s[0], s[2], s[1], AIR);
				plan.set(s[0], s[2] + 1, s[1], AIR);
			}
		}
		// base alargada (degraus encostados) e misulas + parapeito com ameias onde a parede chega ao topo
		for (int x = -rr - 1; x <= rr + 1; x++) {
			for (int z = -rr - 1; z <= rr + 1; z++) {
				boolean ex = Math.abs(x) == rr + 1;
				boolean ez = Math.abs(z) == rr + 1;
				if (!ex && !ez) {
					continue;
				}
				Dir in = ex ? (x > 0 ? Dir.WEST : Dir.EAST) : (z > 0 ? Dir.NORTH : Dir.SOUTH);
				if (ex && ez) {
					boolean intact = tops[x > 0 ? 2 * rr : 0][z > 0 ? 2 * rr : 0] >= top;
					if (intact) {
						plan.set(x, top, z, stone(r, top, top));
						plan.set(x, top + 1, z, stone(r, top, top));
					}
					continue;
				}
				int wallTop = tops[Math.max(-rr, Math.min(rr, x)) + rr][Math.max(-rr, Math.min(rr, z)) + rr];
				if (wallTop >= 3 && !(z == rr + 1 && Math.abs(x) <= 1)) {
					plan.set(x, 0, z, STAIRS, in);
				}
				if (wallTop >= top) {
					plan.set(x, top - 1, z, STAIRS_TOP, in);
					plan.set(x, top, z, stone(r, top, top));
					if (((x + z) & 1) == 0) {
						plan.set(x, top + 1, z, stone(r, top, top));
					}
				}
			}
		}
		// escada em espiral junto as paredes internas: degrau i na altura i
		List<int[]> path = new ArrayList<>();
		for (int z = 2; z >= -3; z--) {
			path.add(new int[] {-3, z});
		}
		for (int x = -2; x <= 3; x++) {
			path.add(new int[] {x, -3});
		}
		for (int z = -2; z <= 3; z++) {
			path.add(new int[] {3, z});
		}
		int[][] stairAt = new int[7][7];
		for (int[] row : stairAt) {
			java.util.Arrays.fill(row, Integer.MIN_VALUE);
		}
		for (int i = 0; i < top; i++) {
			int[] c = path.get(i);
			int[] n = path.get(i + 1);
			stairAt[c[0] + 3][c[1] + 3] = i;
			if (i == 5 || i == 11) {
				plan.set(c[0], i, c[1], PLANKS); // patamar no canto
			} else {
				plan.set(c[0], i, c[1], WOOD_STAIRS, dirOf(n[0] - c[0], n[1] - c[1]));
			}
			if (i > 0) {
				plan.set(c[0], i - 1, c[1], PLANKS); // banzo: cada degrau apoiado e ligado ao anterior
			}
		}
		// andares (5 e 10) e deque do topo, com buraco em cima da escada e onde o desabamento levou o assoalho
		int[] levels = {5, 10, top};
		double[] holes = {2.0, 3.0, 2.5};
		for (int li = 0; li < levels.length; li++) {
			int level = levels[li];
			double hole = holes[li] + r.nextDouble();
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					int h = stairAt[x + 3][z + 3];
					if (h >= level - 3 && h <= level) {
						continue;
					}
					if (Math.hypot(x - bx, z - bz) < hole) {
						continue;
					}
					plan.set(x, level, z, FLOOR);
				}
			}
			if (level < top) {
				for (int x = -2; x <= 2; x++) {
					plan.set(x, level - 1, 0, BEAM, Dir.EAST); // viga sob o assoalho
				}
			}
		}
		// telheiro quebrado no topo, ao norte: dois postes, viga e tabuas soltas
		for (int y = top + 1; y <= top + 2; y++) {
			plan.set(-rr, y, -rr, BEAM);
			plan.set(rr, y, -rr, BEAM);
		}
		for (int x = -rr; x <= rr; x++) {
			plan.set(x, top + 3, -rr, BEAM, Dir.EAST);
		}
		for (int x = -3; x <= 3; x++) {
			if (x == 0 || r.nextInt(3) > 0) {
				plan.set(x, top + 3, -3, WOOD_SLAB);
			}
		}
		plan.set(0, top + 2, -3, LANTERN_HANGING);
		chest(plan, 0, top + 1, -2, Dir.SOUTH);
		// terreo: deposito
		plan.set(2, 0, -2, BARREL);
		plan.set(2, 0, -1, CRATE);
		plan.set(2, 1, -1, CRATE, Dir.NORTH);
		plan.set(1, 0, -2, HAY);
		plan.set(2, 2, -1, FLOWER_POT);
		plan.set(-1, 0, -2, ANVIL, Dir.EAST);
		plan.set(2, 4, 2, COBWEB);
		plan.set(-2, 4, -2, COBWEB);
		// 1o andar: alojamento
		if (plan.get(0, 5, -1) == FLOOR) {
			plan.set(0, 6, -1, FENCE);
			plan.set(0, 7, -1, LANTERN);
		}
		if (plan.get(1, 5, -1) == FLOOR) {
			plan.set(1, 6, -1, WOOD_STAIRS, Dir.EAST);
		}
		for (int z = 1; z <= 2; z++) {
			if (plan.get(1, 5, z) == FLOOR) {
				plan.set(1, 6, z, CARPET);
			}
		}
		if (plan.get(2, 5, -2) == FLOOR) {
			plan.set(2, 6, -2, CRATE);
		}
		// 2o andar: arsenal abandonado
		if (plan.get(1, 10, 1) == FLOOR) {
			plan.set(1, 11, 1, CRATE);
			plan.set(1, 12, 1, CANDLE);
		}
		if (plan.get(2, 10, 0) == FLOOR) {
			plan.set(2, 11, 0, DECORATED_POT);
		}
		plan.set(2, 13, 2, COBWEB);
		airIfEmpty(plan, -rr + 1, 0, -rr + 1, rr - 1, top + 2, rr - 1);
		// entulho dentro e no campo do desabamento, mais denso perto da brecha; um pedaco de parede tombado
		rubble(plan, r, -2, 1, 2);
		rubble(plan, r, -2, 2, 1);
		rubble(plan, r, -1, 1, 1);
		for (int x = -rr - 8; x <= -rr - 1; x++) {
			for (int z = bz - 6; z <= bz + 6; z++) {
				double d = Math.hypot(x - bx, z - bz);
				if (d <= 7 && r.nextDouble() < 0.6 - d * 0.075) {
					rubble(plan, r, x, z, d < 3.5 ? 1 + r.nextInt(2) : 1);
				}
			}
		}
		for (int i = 2; i <= 5; i++) {
			rubble(plan, r, bx - i - 1, bz + (i > 3 ? 1 : 0), 1);
		}
		// pedra de teleporte numa pracinha a frente, com caminho ate a porta
		waystone(plan, 3, rr + 4);
		for (int x = 2; x <= 4; x++) {
			for (int z = rr + 3; z <= rr + 5; z++) {
				plan.setIfAbsent(x, -1, z, PAVING);
			}
		}
		for (int z = rr + 2; z <= rr + 5; z++) {
			plan.setIfAbsent(0, -1, z, GROUND);
			plan.setIfAbsent(1, -1, z, r.nextBoolean() ? GROUND : GRAVEL);
		}
		// vegetacao
		vines(plan, r, 0.10, 5);
		wallTopLeaves(plan, r, 0.35, top - 1);
		moss(plan, r, 6, -3, -3, 3, 3, 0.10);
		moss(plan, r, 1, -3, -3, 3, 3, 0.08);
		plants(plan, r, -rr - 9, -rr - 6, rr + 6, rr + 7, 0.30);
	}

	private static Dir dirOf(int dx, int dz) {
		if (dx > 0) {
			return Dir.EAST;
		}
		if (dx < 0) {
			return Dir.WEST;
		}
		return dz > 0 ? Dir.SOUTH : Dir.NORTH;
	}

	// --- acampamento abandonado ---

	private static void camp(RuinPlan plan, Random r) {
		for (int x = -11; x <= 11; x++) {
			for (int z = -11; z <= 10; z++) {
				double d = (x / 10.5) * (x / 10.5) + (z / 9.0) * (z / 9.0) + (r.nextDouble() - 0.5) * 0.25;
				if (d > 1.0) {
					continue;
				}
				int roll = r.nextInt(100);
				// grama so na borda: no meio, terra batida e cascalho (arvores do worldgen nao nascem neles)
				RuinPart g = d < 0.7 ? (roll < 75 ? GROUND : GRAVEL) : roll < 60 ? SOIL : roll < 85 ? GROUND : GRAVEL;
				plan.set(x, -1, z, g);
			}
		}
		// fogueira com pedras em volta e espeto (duas cercas e uma corrente)
		fill(plan, -1, -1, -1, 1, -1, 1, FOUNDATION);
		plan.set(0, 0, 0, CAMPFIRE);
		for (int x = -1; x <= 1; x += 2) {
			plan.set(x, 0, 0, FENCE);
			plan.set(x, 1, 0, FENCE);
		}
		plan.set(0, 1, 0, CHAIN, Dir.EAST);
		plan.set(0, 0, -1, BUTTON, Dir.NORTH);
		plan.set(0, 0, 1, BUTTON, Dir.SOUTH);
		plan.set(-1, 0, 1, SLAB);
		plan.set(1, 0, -1, SLAB);
		// bancos de tronco e um toco
		for (int z = -1; z <= 1; z++) {
			plan.set(-3, 0, z, BEAM, Dir.NORTH);
		}
		plan.set(1, 0, 3, BEAM, Dir.EAST);
		plan.set(2, 0, 3, BEAM, Dir.EAST);
		plan.set(3, 0, -1, BEAM);
		// barracas: a grande ao norte (aberta para a fogueira), uma a oeste e as vezes uma a leste (as vezes caida)
		tent(plan, r, 0, -10, Dir.SOUTH, 3, 6, false, 0);
		tent(plan, r, -10, 1, Dir.EAST, 2, 4, false, 1);
		if (r.nextInt(10) < 7) {
			tent(plan, r, 10, -1, Dir.WEST, 2, 4, r.nextInt(10) < 4, 2);
		}
		// carroca quebrada: caixote e feno na carroceria, rodas de alcapao, varais de cerca
		ground(plan, -7, 6);
		ground(plan, -6, 6);
		plan.set(-7, 0, 6, CRATE);
		plan.set(-6, 0, 6, HAY, Dir.EAST);
		plan.set(-6, 1, 6, WOOD_SLAB);
		plan.set(-7, 0, 5, TRAPDOOR, Dir.NORTH);
		plan.set(-6, 0, 5, TRAPDOOR, Dir.NORTH);
		if (r.nextBoolean()) {
			plan.set(-7, 0, 7, TRAPDOOR, Dir.SOUTH);
		}
		plan.set(-5, 0, 6, FENCE);
		ground(plan, -5, 6);
		ground(plan, -8, 7);
		plan.set(-8, 0, 7, DECORATED_POT);
		// curral com cerca quebrada, feno e cocho
		for (int x = 5; x <= 10; x++) {
			for (int z = 4; z <= 9; z++) {
				boolean edge = x == 5 || x == 10 || z == 4 || z == 9;
				ground(plan, x, z);
				if (edge && !(z == 4 && (x == 7 || x == 8)) && r.nextInt(4) > 0) {
					plan.set(x, 0, z, FENCE);
				}
			}
		}
		plan.set(8, 0, 7, HAY);
		plan.set(8, 1, 7, HAY);
		plan.set(9, 0, 7, HAY, Dir.NORTH);
		plan.set(6, 0, 8, CAULDRON);
		// lenha empilhada e toco de rachar lenha
		for (int x = 4; x <= 6; x++) {
			ground(plan, x, -7);
			plan.set(x, 0, -7, BEAM, Dir.NORTH);
		}
		plan.set(4, 1, -7, BEAM, Dir.NORTH);
		plan.set(5, 1, -7, BEAM, Dir.NORTH);
		plan.markLoose(4, 1, -7);
		plan.markLoose(5, 1, -7);
		plan.set(4, 0, -5, BEAM);
		// muro baixo de pedra seca (quebra-vento) a noroeste, meio desmoronado
		int[][] windbreak = {{-10, -4}, {-9, -5}, {-8, -6}, {-7, -6}, {-6, -7}, {-5, -7}};
		for (int[] c : windbreak) {
			plan.set(c[0], -1, c[1], FOUNDATION);
			int h = 1 + r.nextInt(2);
			for (int y = 0; y < h; y++) {
				plan.set(c[0], y, c[1], stone(r, y, h));
			}
			if (r.nextInt(3) == 0) {
				plan.set(c[0], h, c[1], SLAB);
			}
		}
		for (int[] c : new int[][] {{-9, -4}, {-8, -5}, {-7, -7}}) {
			ground(plan, c[0], c[1]);
		}
		plan.set(-9, 0, -4, WALL_CRACKED);
		plan.set(-8, 0, -5, WALL_MOSSY);
		plan.set(-7, 0, -7, WALL_ALT);
		plan.set(-6, 1, -7, WALL);
		rubble(plan, r, -7, -5, 1);
		rubble(plan, r, -4, -8, 1);
		// poste com lanterna, caixotes e vaso perto da barraca grande
		plan.set(2, 0, -4, FENCE);
		plan.set(2, 1, -4, LANTERN);
		plan.set(-2, 0, -4, CRATE);
		plan.set(-2, 1, -4, CANDLE);
		plan.set(-3, 0, -4, CRATE, Dir.NORTH);
		for (int x = -11; x <= 11; x++) {
			for (int z = -11; z <= 10; z++) {
				if (plan.get(x, -1, z) != null) {
					airIfEmpty(plan, x, 0, z, x, 14, z);
				}
			}
		}
		plants(plan, r, -12, -12, 12, 11, 0.22);
	}

	/**
	 * Barraca em "A" de la listrada. {@code (bx, bz)} e o fundo, {@code open} o lado aberto, {@code hw} a meia
	 * largura (a cumeeira fica na altura hw) e {@code len} o comprimento. Caida = metade da lona no chao.
	 * {@code which}: 0 = grande (bau, colchonetes, lanterna, poste da frente), 1 = barril com loot, 2 = caixote.
	 */
	private static void tent(RuinPlan plan, Random r, int bx, int bz, Dir open, int hw, int len, boolean fallen, int which) {
		Dir side = open.rotate(1);
		for (int a = -1; a <= len; a++) {
			for (int b = -hw; b <= hw; b++) {
				int x = bx + open.dx * a + side.dx * b;
				int z = bz + open.dz * a + side.dz * b;
				if (a < 0 || a == len) {
					continue;
				}
				ground(plan, x, z);
				int roof = hw - Math.abs(b);
				RuinPart cloth = a % 2 == 0 ? TENT : TENT_ALT;
				if (fallen && a >= len / 2) {
					plan.set(x, 0, z, b == 0 ? (a == len / 2 ? BEAM : CARPET) : CARPET, open);
					continue;
				}
				if (b == 0) {
					plan.set(x, hw, z, BEAM, open);
				} else {
					plan.set(x, roof, z, cloth);
				}
				for (int y = 0; y < roof; y++) {
					boolean closed = a == 0 || (a == len - 1 && b <= -2);
					plan.set(x, y, z, closed ? cloth : AIR);
				}
			}
		}
		// postes: atras sempre; na frente so na grande (nas pequenas tamparia a entrada)
		int px = bx - open.dx;
		int pz = bz - open.dz;
		ground(plan, px, pz);
		for (int y = 0; y < hw; y++) {
			plan.set(px, y, pz, FENCE);
		}
		plan.set(px, hw, pz, BEAM, open);
		if (which == 0 && !fallen) {
			int fx = bx + open.dx * len;
			int fz = bz + open.dz * len;
			ground(plan, fx, fz);
			for (int y = 0; y < hw; y++) {
				plan.set(fx, y, fz, FENCE);
			}
			plan.set(fx, hw, fz, BEAM, open);
		}
		if (fallen) {
			if (which == 2) {
				int cx = bx + open.dx;
				int cz = bz + open.dz;
				plan.set(cx, 0, cz, CRATE);
			}
			return;
		}
		int ix = bx + open.dx;
		int iz = bz + open.dz;
		Dir back = open;
		switch (which) {
			case 0 -> {
				chest(plan, ix, 0, iz, back);
				for (int a = 2; a <= 3; a++) {
					for (int b = -2; b <= 2; b += 4) {
						plan.set(bx + open.dx * a + side.dx * b, 0, bz + open.dz * a + side.dz * b, CARPET);
					}
				}
				plan.set(bx + open.dx * (len - 2), hw - 1, bz + open.dz * (len - 2), LANTERN_HANGING);
				plan.set(ix + side.dx * 2, 0, iz + side.dz * 2, CRATE);
				plan.set(ix - side.dx * 2, 0, iz - side.dz * 2, COBWEB);
			}
			case 1 -> {
				plan.set(ix, 0, iz, BARREL);
				plan.set(bx + open.dx * 2 + side.dx, 0, bz + open.dz * 2 + side.dz, CARPET);
			}
			default -> plan.set(ix, 0, iz, CRATE);
		}
	}

	// --- poco antigo ---

	/** Pes no chao da cisterna embaixo do poco. */
	static final int WELL_ROOM_FLOOR = -12;

	private static void well(RuinPlan plan, Random r) {
		int f = WELL_ROOM_FLOOR;
		// praca de pedra irregular
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				double d = Math.hypot(x, z) + r.nextDouble() * 1.2;
				if (d <= 4.6) {
					plan.set(x, -1, z, r.nextInt(10) < 7 ? PAVING : FOUNDATION);
				} else if (d <= 6.2 && r.nextInt(10) < 6) {
					plan.set(x, -1, z, r.nextBoolean() ? GROUND : SOIL);
				}
			}
		}
		// borda 5x5 em volta da boca 3x3; lajes perto dos cantos, meio dos lados livre para subir
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (Math.abs(x) != 2 && Math.abs(z) != 2) {
					continue;
				}
				boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
				plan.set(x, 0, z, corner ? ACCENT : stone(r, 0, 1));
				if (!corner && (Math.abs(x) == 1 || Math.abs(z) == 1)) {
					plan.set(x, 1, z, SLAB);
				}
			}
		}
		plan.set(-2, 1, 2, FLOWER_POT);
		// postes, sarilho e telhado de duas aguas com oitoes de tabua
		for (int y = 1; y <= 3; y++) {
			plan.set(-2, y, 0, BEAM);
			plan.set(2, y, 0, BEAM);
		}
		for (int x = -2; x <= 2; x++) {
			plan.set(x, 4, 0, BEAM, Dir.EAST);
		}
		for (int x = -2; x <= 2; x += 4) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 4; y < 7 - Math.abs(z); y++) {
					if (plan.get(x, y, z) == null) {
						plan.set(x, y, z, PLANKS);
					}
				}
			}
		}
		boolean brokenSouth = r.nextBoolean();
		for (int k = 0; k <= 3; k++) {
			int y = 4 + k;
			int zz = 3 - k;
			for (int x = -3; x <= 3; x++) {
				if (zz == 0) {
					plan.set(x, y, 0, WOOD_SLAB);
					continue;
				}
				boolean southHole = brokenSouth && r.nextInt(4) == 0 && Math.abs(x) != 2;
				boolean northHole = !brokenSouth && r.nextInt(4) == 0 && Math.abs(x) != 2;
				if (!southHole) {
					plan.set(x, y, zz, WOOD_STAIRS, Dir.NORTH);
				}
				if (!northHole) {
					plan.set(x, y, -zz, WOOD_STAIRS, Dir.SOUTH);
				}
			}
		}
		plan.set(0, 3, 0, CHAIN);
		plan.set(0, 2, 0, CAULDRON); // balde pendurado no sarilho
		// poco: revestido ate o teto da cisterna, mais musgo quanto mais fundo
		for (int y = -1; y >= f + 4; y--) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) == 2 || Math.abs(z) == 2) {
						plan.set(x, y, z, stone(r, y < f + 9 ? 0 : 6, 99));
					} else {
						plan.set(x, y, z, AIR);
					}
				}
			}
		}
		airIfEmpty(plan, -1, 0, -1, 1, 3, 1);
		for (int y = f; y <= 0; y++) {
			plan.set(0, y, -1, LADDER, Dir.SOUTH);
		}
		// cisterna: sala 9x9 com abobada de arcos, agua embaixo da boca do poco
		List<long[]> carved = new ArrayList<>();
		carve(plan, carved, -4, f, -1, 4, f + 3, 7);
		for (int x = -4; x <= 4; x++) {
			for (int z = -1; z <= 7; z++) {
				plan.set(x, f - 1, z, r.nextInt(10) < 6 ? PAVING : stone(r, 0, 99));
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (x == 0 && z == -1) {
					continue; // pe da escada de mao
				}
				plan.set(x, f - 1, z, WATER);
				plan.set(x, f - 2, z, PAVING);
				carved.add(new long[] {x, f - 1, z}); // reveste em volta da agua tambem
			}
		}
		for (int z : new int[] {2, 6}) {
			for (int x = -3; x <= 3; x += 6) {
				for (int y = f; y <= f + 3; y++) {
					plan.set(x, y, z, y % 2 == 0 ? ACCENT : WALL_ALT);
				}
			}
			plan.set(-2, f + 3, z, STAIRS_TOP, Dir.WEST);
			plan.set(2, f + 3, z, STAIRS_TOP, Dir.EAST);
			for (int x = -1; x <= 1; x++) {
				plan.set(x, f + 3, z, SLAB_TOP);
			}
		}
		chest(plan, 4, f, 7, Dir.WEST);
		plan.set(-4, f, 7, BARREL);
		plan.set(-4, f + 1, 7, FLOWER_POT);
		plan.set(-4, f, 6, CRATE);
		plan.set(-4, f + 1, 6, CRATE, Dir.EAST);
		plan.set(-4, f + 2, 6, CANDLE);
		plan.set(4, f, 0, DECORATED_POT);
		plan.set(3, f, -1, DECORATED_POT);
		plan.set(4, f, 4, CRATE);
		plan.set(4, f + 1, 4, LANTERN);
		plan.set(0, f + 3, 4, CHAIN);
		plan.set(0, f + 2, 4, LANTERN_HANGING);
		plan.set(4, f + 3, 7, COBWEB);
		plan.set(-4, f + 3, -1, COBWEB);
		plan.set(4, f + 3, -1, COBWEB);
		plan.set(-4, f + 3, 3, COBWEB);
		moss(plan, r, f, -3, -1, 3, 4, 0.35);
		line(plan, r, carved, false);
		// restos de um muro de patio e coisas largadas em volta
		for (int i = 0; i < 4; i++) {
			int h = 1 + r.nextInt(3);
			int[][] cells = {{-6, -6 + i}, {-6 + i, -6}};
			for (int[] c : cells) {
				ground(plan, c[0], c[1]);
				if (plan.get(c[0], -1, c[1]) == SOIL) {
					plan.set(c[0], -1, c[1], FOUNDATION);
				}
				for (int y = 0; y < h; y++) {
					plan.set(c[0], y, c[1], stone(r, y, h));
				}
				if (r.nextBoolean()) {
					plan.set(c[0], h, c[1], STONE_WALL);
				}
			}
		}
		plan.set(3, 0, 4, WOOD_STAIRS, Dir.SOUTH);
		plan.set(4, 0, 4, WOOD_STAIRS, Dir.SOUTH);
		ground(plan, 3, 4);
		ground(plan, 4, 4);
		plan.set(-3, 0, 3, FENCE);
		plan.set(-3, 1, 3, LANTERN);
		ground(plan, -3, 3);
		plan.set(4, 0, -3, HAY);
		ground(plan, 4, -3);
		plan.set(-4, 0, 1, CAULDRON);
		ground(plan, -4, 1);
		plan.set(-3, 0, -2, DECORATED_POT);
		rubble(plan, r, 5, -1, 1);
		rubble(plan, r, -5, 4, 1);
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (plan.get(x, -1, z) != null) {
					airIfEmpty(plan, x, 0, z, x, 8, z);
				}
			}
		}
		vines(plan, r, 0.15, 3);
		plants(plan, r, -9, -9, 9, 9, 0.25);
	}

	// --- capela em ruinas ---

	static final int CHAPEL_EAVE = 7;

	private static void chapel(RuinPlan plan, Random r) {
		int hx = 5;
		int hz = 8;
		int eave = CHAPEL_EAVE;
		// plataforma de pedra com borda irregular (inclui a base da torre do sino)
		for (int x = -7; x <= 7; x++) {
			for (int z = -10; z <= 12; z++) {
				boolean core = Math.abs(x) <= 6 && Math.abs(z) <= 9 || (x >= -6 && x <= -1 && z >= 8 && z <= 12);
				if (core) {
					int roll = r.nextInt(100);
					plan.set(x, -1, z, roll < 60 ? PAVING : roll < 85 ? FOUNDATION : WALL_MOSSY);
				} else if (Math.abs(x) <= 7 && z <= 10 && r.nextBoolean()) {
					plan.set(x, -1, z, r.nextBoolean() ? FOUNDATION : SOIL);
				}
			}
		}
		// paredes: a lateral leste desaba para a frente; oitao alto nos fundos; fachada quebrada a direita
		int[] eastTop = new int[2 * hz + 1];
		for (int z = -hz; z <= hz; z++) {
			int h;
			if (z <= -1) {
				h = eave;
			} else {
				h = Math.max(1, eave - (z + 1) + r.nextInt(2));
			}
			eastTop[z + hz] = h;
			wallColumn(plan, r, hx, z, h, z == -hz || z == hz);
			int w = r.nextInt(7) == 0 ? eave - 1 - r.nextInt(2) : eave;
			wallColumn(plan, r, -hx, z, w, z == -hz || z == hz);
		}
		for (int x = -hx + 1; x <= hx - 1; x++) {
			wallColumn(plan, r, x, -hz, 12 - Math.abs(x) - (r.nextInt(5) == 0 ? 1 : 0), false);
			int front = x <= 0 ? 12 - Math.abs(x) - 1 - r.nextInt(3) : Math.max(0, 4 - x + r.nextInt(2));
			wallColumn(plan, r, x, hz, front, false);
		}
		// porta em arco
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 2; y++) {
				plan.set(x, y, hz, AIR);
			}
		}
		if (plan.get(-1, 3, hz) != null) {
			plan.set(-1, 3, hz, STAIRS_TOP, Dir.WEST);
		}
		if (plan.get(1, 3, hz) != null) {
			plan.set(1, 3, hz, STAIRS_TOP, Dir.EAST);
		}
		if (plan.get(0, 3, hz) != null) {
			plan.set(0, 3, hz, AIR);
			if (plan.get(0, 4, hz) != null) {
				plan.set(0, 4, hz, ACCENT);
			}
		}
		// vitrais quebrados nas laterais e rosacea no oitao
		for (int z : new int[] {-5, -1, 3}) {
			for (int x = -hx; x <= hx; x += 2 * hx) {
				int h = x > 0 ? eastTop[z + hz] : eave;
				for (int y = 2; y <= 4 && y < h; y++) {
					plan.set(x, y, z, glass(r), Dir.NORTH);
				}
				if (h >= 5) {
					plan.set(x, 5, z, ACCENT);
				}
			}
		}
		int[][] rose = {{0, 8}, {-1, 8}, {1, 8}, {0, 7}, {0, 9}};
		for (int[] c : rose) {
			plan.set(c[0], c[1], -hz, glass(r), Dir.EAST);
		}
		for (int[] c : new int[][] {{-1, 7}, {1, 7}, {-1, 9}, {1, 9}, {0, 10}, {0, 6}, {-2, 8}, {2, 8}}) {
			plan.set(c[0], c[1], -hz, ACCENT);
		}
		// contrafortes com degrau no topo
		for (int z : new int[] {-6, -2, 2, 6}) {
			for (int x = -hx - 1; x <= hx + 1; x += 2 * hx + 2) {
				int wall = x > 0 ? eastTop[z + hz] : eave;
				int bh = wall >= 5 ? 3 : Math.max(0, wall - 2);
				if (bh == 0) {
					continue;
				}
				for (int y = 0; y < bh; y++) {
					plan.set(x, y, z, stone(r, y, bh));
				}
				plan.set(x, bh, z, STAIRS, x > 0 ? Dir.WEST : Dir.EAST);
			}
		}
		// torre do sino (toco) na frente, a esquerda: sino caido la dentro
		int tower = 9 + r.nextInt(3);
		for (int x = -5; x <= -2; x++) {
			for (int z = hz + 1; z <= hz + 3; z++) {
				boolean ring = x == -5 || x == -2 || z == hz + 3;
				if (!ring) {
					continue;
				}
				boolean corner = (x == -5 || x == -2) && z == hz + 3;
				int h = tower - r.nextInt(3);
				for (int y = 0; y <= h; y++) {
					plan.set(x, y, z, corner ? (y % 2 == 0 ? ACCENT : WALL_ALT) : stone(r, y, h));
				}
			}
			int h = Math.max(plan.get(x, 0, hz) == null ? 0 : 1, tower - 1 - r.nextInt(2));
			for (int y = 0; y <= h; y++) {
				if (x != -2 || y > 0) {
					plan.setIfAbsent(x, y, hz, stone(r, y, h));
				}
			}
		}
		for (int y = 0; y <= 1; y++) {
			plan.set(-3, y, hz, AIR); // passagem da nave para a torre
		}
		for (int[] s : new int[][] {{-5, hz + 2}, {-2, hz + 2}, {-4, hz + 3}}) {
			plan.set(s[0], 5, s[1], AIR);
			plan.set(s[0], 6, s[1], AIR);
		}
		airIfEmpty(plan, -4, 0, hz + 1, -3, tower + 1, hz + 2);
		plan.set(-4, 0, hz + 2, BELL, Dir.EAST);
		plan.set(-3, 0, hz + 1, CRATE);
		plan.set(-4, tower - 3, hz + 1, BEAM, Dir.EAST);
		plan.set(-3, tower - 3, hz + 1, BEAM, Dir.EAST);
		plan.set(-3, 4, hz + 2, COBWEB);
		// interior
		airIfEmpty(plan, -hx + 1, 0, -hz + 1, hx - 1, 13, hz - 1);
		// presbiterio: degrau, estrado, altar com atril (lugar do item de invocar o chefe), velas
		for (int x = -3; x <= 3; x++) {
			plan.set(x, 0, -5, STAIRS, Dir.NORTH);
			plan.set(x, 0, -6, PAVING);
			plan.set(x, 0, -7, PAVING);
		}
		for (int x = -1; x <= 1; x++) {
			plan.set(x, 1, -7, ALTAR);
		}
		plan.set(0, 2, -7, LECTERN, Dir.SOUTH);
		plan.mark(RuinPlan.MARKER_BOSS_SUMMON, 0, 2, -7);
		plan.set(-1, 2, -7, CANDLE);
		plan.set(1, 2, -7, CANDLE);
		plan.set(-3, 1, -7, CANDLE);
		plan.set(3, 1, -7, FLOWER_POT);
		plan.set(-3, 1, -6, FLOWER_POT);
		chest(plan, 4, 0, -7, Dir.WEST);
		plan.set(-4, 0, -7, CRATE);
		plan.set(-4, 1, -7, CANDLE);
		// arvore crescendo no canto desabado, com terra em volta da raiz
		int tx = 3;
		int tz = 4;
		for (int x = tx - 1; x <= tx + 1; x++) {
			for (int z = tz - 1; z <= tz + 1; z++) {
				plan.set(x, -1, z, SOIL);
			}
		}
		for (int y = 0; y <= 4; y++) {
			plan.set(tx, y, tz, BEAM);
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int y = 3; y <= 6; y++) {
					int dy = y - 5;
					if (dx * dx + dz * dz + dy * dy * 2 <= 6 && r.nextInt(10) < 8) {
						setIfFree(plan, tx + dx, y, tz + dz, LEAVES);
					}
				}
			}
		}
		setIfFree(plan, tx, 5, tz, LEAVES);
		// corredor com tapete e bancos dos dois lados (alguns quebrados ou sumidos)
		for (int z = -4; z <= hz - 1; z++) {
			if (r.nextInt(5) > 0) {
				setIfFree(plan, 0, 0, z, RUNNER);
			}
		}
		for (int z = -3; z <= 5; z += 2) {
			for (int x : new int[] {-4, -3, -2, 2, 3, 4}) {
				boolean collapsed = x > 0 && z >= 1;
				int roll = r.nextInt(10);
				if (plan.get(x, -1, z) == SOIL || (collapsed && roll < 5) || roll == 0) {
					continue;
				}
				setIfFree(plan, x, 0, z, roll == 1 ? WOOD_SLAB : WOOD_STAIRS, Dir.SOUTH);
			}
		}
		// tesouras do telhado, lanternas penduradas e o resto do telhado nos fundos
		for (int z : new int[] {-6, -2}) {
			for (int x = -hx + 1; x <= hx - 1; x++) {
				plan.set(x, eave, z, BEAM, Dir.EAST);
			}
			plan.set(0, eave - 1, z, CHAIN);
			plan.set(0, eave - 2, z, LANTERN_HANGING);
		}
		for (int x = -hx + 1; x <= -1; x++) {
			plan.set(x, eave, 2, BEAM, Dir.EAST);
		}
		for (int y = eave + 1; y <= 12; y++) {
			plan.set(0, y, -6, BEAM);
		}
		int westEnd = -1 + r.nextInt(3);
		int eastEnd = -4;
		for (int side = -1; side <= 1; side += 2) {
			int end = side < 0 ? westEnd : eastEnd;
			for (int z = -hz - 1; z <= end; z++) {
				for (int k = 0; k <= 5; k++) {
					int x = side * (6 - k);
					boolean ragged = z > end - 2 && r.nextInt(10) < 4;
					if (z != -hz && (ragged || r.nextInt(100) < 12)) {
						continue;
					}
					plan.set(x, eave + k, z, WOOD_STAIRS, side > 0 ? Dir.WEST : Dir.EAST);
				}
			}
		}
		for (int z = -hz - 1; z <= Math.min(westEnd, eastEnd); z++) {
			plan.set(0, 13, z, WOOD_SLAB);
		}
		// entulho do desabamento: dentro (longe do corredor) e fora, a leste
		for (int x = 2; x <= 4; x++) {
			for (int z = 0; z <= hz - 1; z++) {
				if (r.nextInt(10) < 3 && !(x == tx && z == tz)) {
					rubble(plan, r, x, z, 1 + r.nextInt(2));
				}
			}
		}
		for (int x = hx + 1; x <= hx + 6; x++) {
			for (int z = -1; z <= hz + 3; z++) {
				double d = x - hx + Math.max(0, -z) * 0.5;
				if (r.nextDouble() < 0.5 - d * 0.08) {
					rubble(plan, r, x, z, d < 2 ? 1 + r.nextInt(2) : 1);
				}
			}
		}
		// cemiterio a oeste: lapides (muros ou degraus tombados) com flores, cerca quebrada
		for (int z = -5; z <= 4; z += 3) {
			ground(plan, -9, z);
			ground(plan, -8, z);
			int roll = r.nextInt(4);
			if (roll == 0) {
				plan.set(-9, 0, z, STAIRS, Dir.WEST);
			} else if (roll == 1) {
				plan.set(-9, 0, z, ACCENT);
			} else {
				plan.set(-9, 0, z, STONE_WALL);
			}
			plan.set(-8, -1, z, GROUND);
			if (r.nextBoolean()) {
				plan.set(-8, -1, z + 1, SOIL);
				plan.set(-8, 0, z + 1, FLOWER);
			}
		}
		for (int z = -7; z <= 6; z++) {
			if (r.nextInt(3) > 0) {
				ground(plan, -11, z);
				plan.set(-11, 0, z, FENCE);
			}
		}
		// pedra de teleporte a frente, a direita da porta; caminho de terra ate a porta
		waystone(plan, 4, hz + 4);
		for (int x = 3; x <= 5; x++) {
			for (int z = hz + 3; z <= hz + 5; z++) {
				plan.setIfAbsent(x, -1, z, PAVING);
			}
		}
		for (int z = hz + 1; z <= hz + 5; z++) {
			for (int x = -1; x <= 1; x++) {
				plan.setIfAbsent(x, -1, z, r.nextInt(4) == 0 ? GRAVEL : GROUND);
			}
		}
		plan.set(4, 5, -7, COBWEB);
		plan.set(-4, 6, -7, COBWEB);
		plan.set(-4, 3, 7, COBWEB);
		// clareira: tira troncos e copas da floresta que ficariam por cima da capela
		airIfEmpty(plan, -7, 0, -10, 7, 18, 12);
		vines(plan, r, 0.22, 6);
		wallTopLeaves(plan, r, 0.35, 12);
		moss(plan, r, 0, -4, -4, 4, 7, 0.10);
		plants(plan, r, -12, -11, 12, 14, 0.30);
	}

	private static void wallColumn(RuinPlan plan, Random r, int x, int z, int h, boolean corner) {
		for (int y = 0; y <= h; y++) {
			plan.set(x, y, z, corner ? (y % 2 == 0 ? ACCENT : WALL_ALT) : stone(r, y, h));
		}
	}

	/** Vitral quebrado: vidro colorido, grade ou buraco. */
	private static RuinPart glass(Random r) {
		int roll = r.nextInt(100);
		return roll < 45 ? GLASS_PANE : roll < 60 ? IRON_BARS : AIR;
	}

	// --- mina desativada ---

	static final int MINE_STEPS = 9;
	/** Pes no fundo da mina (salao do veio e sala do chefe). */
	static final int MINE_BOTTOM = -MINE_STEPS + 1;

	private static void mine(RuinPlan plan, Random r) {
		plan.ironVein(r.nextBoolean());
		List<long[]> carved = new ArrayList<>();
		int bottom = MINE_BOTTOM;
		// patio de cascalho na frente
		for (int x = -8; x <= 8; x++) {
			for (int z = 1; z <= 10; z++) {
				double d = (x / 7.5) * (x / 7.5) + ((z - 4.5) / 5.5) * ((z - 4.5) / 5.5) + (r.nextDouble() - 0.5) * 0.25;
				if (d <= 1.0 || z == 1 && Math.abs(x) <= 6) {
					int roll = r.nextInt(100);
					plan.set(x, -1, z, roll < 35 ? GRAVEL : roll < 70 ? GROUND : roll < 90 ? SOIL : FOUNDATION);
				}
			}
		}
		// rampa descendo para o norte: degrau i em z=-i, pes em y=-i; escoras de tronco a cada 3
		for (int i = 0; i < MINE_STEPS; i++) {
			int z = -i;
			int feet = -i;
			carve(plan, carved, -2, feet, z, 2, feet + 3, z);
			for (int x = -2; x <= 2; x++) {
				plan.set(x, feet - 1, z, rock(r));
			}
			plan.set(0, feet, z, i == 0 ? RAIL : RAIL_UP, i == 0 ? Dir.NORTH : Dir.SOUTH);
			if (i % 3 == 1) {
				timberSet(plan, feet, z);
				plan.set(-1, feet + 2, z, LANTERN_HANGING);
			}
		}
		// fachada: muro de arrimo em degraus com grama em cima e boca escorada por troncos
		for (int x = -6; x <= 6; x++) {
			int h = Math.abs(x) <= 2 ? 5 : Math.max(1, 5 - (Math.abs(x) - 2) + r.nextInt(2));
			plan.set(x, -1, 0, FOUNDATION);
			for (int y = 0; y < h; y++) {
				if (Math.abs(x) > 2 || y >= 4) {
					plan.set(x, y, 0, stone(r, y, h));
				}
			}
			plan.set(x, h, 0, SOIL);
			if (r.nextInt(3) == 0) {
				plan.set(x, h + 1, 0, r.nextInt(3) == 0 ? LEAVES : PLANT);
			}
			if (Math.abs(x) > 2) {
				plan.set(x, 0, 1, STAIRS, Dir.NORTH); // pe do muro em rampa
			}
		}
		for (int y = 0; y <= 3; y++) {
			plan.set(-2, y, 0, BEAM);
			plan.set(2, y, 0, BEAM);
		}
		for (int x = -2; x <= 2; x++) {
			plan.set(x, 4, 0, BEAM, Dir.EAST);
		}
		plan.set(-1, 3, 0, WOOD_STAIRS_TOP, Dir.WEST);
		plan.set(1, 3, 0, WOOD_STAIRS_TOP, Dir.EAST);
		plan.set(0, 3, 0, AIR);
		plan.set(0, 4, 1, BEAM, Dir.SOUTH);
		plan.set(0, 3, 1, LANTERN_HANGING);
		// trecho plano ate o salao
		carve(plan, carved, -2, bottom, -10, 2, bottom + 3, -9);
		for (int z = -10; z <= -9; z++) {
			for (int x = -2; x <= 2; x++) {
				plan.set(x, bottom - 1, z, rock(r));
			}
			plan.set(0, bottom, z, RAIL, Dir.NORTH);
		}
		// salao do veio: irregular, com nucleo retangular garantido
		for (int x = -8; x <= 8; x++) {
			for (int z = -22; z <= -10; z++) {
				double d = (x / 6.5) * (x / 6.5) + ((z + 15) / 5.5) * ((z + 15) / 5.5) + (r.nextDouble() - 0.5) * 0.25;
				boolean core = Math.abs(x) <= 4 && z >= -19 && z <= -11;
				if (d > 1.0 && !core) {
					continue;
				}
				carve(plan, carved, x, bottom, z, x, bottom + (d < 0.45 ? 4 : 3), z);
				plan.set(x, bottom - 1, z, rock(r));
			}
		}
		for (int z : new int[] {-12, -18}) {
			for (int y = bottom; y <= bottom + 2; y++) {
				plan.set(-3, y, z, BEAM);
				plan.set(3, y, z, BEAM);
			}
			for (int x = -3; x <= 3; x++) {
				plan.set(x, bottom + 3, z, BEAM, Dir.EAST);
			}
		}
		plan.set(1, bottom + 2, -12, LANTERN_HANGING);
		plan.set(-1, bottom + 2, -18, LANTERN_HANGING);
		for (int z = -17; z <= -11; z++) {
			plan.set(0, bottom, z, z == -16 ? MINECART : RAIL, Dir.NORTH);
		}
		chest(plan, -4, bottom, -19, Dir.EAST);
		plan.set(4, bottom, -19, BARREL);
		plan.set(4, bottom, -18, CRATE);
		plan.set(4, bottom + 1, -18, CRATE, Dir.WEST);
		plan.set(4, bottom + 2, -18, LANTERN);
		plan.set(-4, bottom, -11, CRAFTING_TABLE);
		plan.set(-3, bottom, -11, ANVIL, Dir.NORTH);
		plan.set(-2, bottom, -19, DECORATED_POT);
		// monte de minerio solto
		for (int[] c : new int[][] {{3, -11}, {4, -11}, {4, -12}}) {
			plan.set(c[0], bottom, c[1], ORE);
			plan.markLoose(c[0], bottom, c[1]);
		}
		plan.set(4, bottom + 1, -11, ORE);
		plan.markLoose(4, bottom + 1, -11);
		plan.set(4, bottom + 3, -19, COBWEB);
		plan.set(-4, bottom + 3, -11, COBWEB);
		// corredor para oeste ate a sala do altar do chefe (Golem de Cobalto, futuro)
		carve(plan, carved, -11, bottom, -16, -5, bottom + 3, -14);
		for (int x = -11; x <= -5; x++) {
			for (int z = -16; z <= -14; z++) {
				plan.set(x, bottom - 1, z, rock(r));
			}
		}
		for (int x : new int[] {-7, -10}) {
			for (int y = bottom; y <= bottom + 2; y++) {
				plan.set(x, y, -16, BEAM);
				plan.set(x, y, -14, BEAM);
			}
			for (int z = -16; z <= -14; z++) {
				plan.set(x, bottom + 3, z, BEAM, Dir.SOUTH);
			}
		}
		rubble(plan, r, -9, bottom, -14, 2);
		rubble(plan, r, -8, bottom, -16, 1);
		plan.set(-6, bottom + 3, -15, COBWEB);
		// sala do altar: 7x7, piso de pedra, pilares, grades, correntes penduradas e o altar no meio
		carve(plan, carved, -12, bottom, -15, -12, bottom + 2, -15);
		carve(plan, carved, -19, bottom, -18, -13, bottom + 4, -12);
		for (int x = -19; x <= -13; x++) {
			for (int z = -18; z <= -12; z++) {
				plan.set(x, bottom - 1, z, r.nextInt(10) < 7 ? PAVING : WALL_CRACKED);
			}
		}
		for (int y = bottom; y <= bottom + 2; y++) {
			plan.set(-12, y, -16, IRON_BARS, Dir.NORTH);
			plan.set(-12, y, -14, IRON_BARS, Dir.NORTH);
		}
		for (int[] c : new int[][] {{-19, -18}, {-13, -18}, {-19, -12}, {-13, -12}}) {
			for (int y = bottom; y <= bottom + 4; y++) {
				plan.set(c[0], y, c[1], y % 2 == 0 ? ACCENT : WALL_ALT);
			}
		}
		int ax = -16;
		int az = -15;
		plan.set(ax, bottom, az, BOSS_ALTAR);
		plan.mark(RuinPlan.MARKER_BOSS_ALTAR, ax, bottom, az);
		plan.set(ax, bottom, az + 1, STAIRS, Dir.NORTH);
		plan.set(ax, bottom, az - 1, STAIRS, Dir.SOUTH);
		plan.set(ax + 1, bottom, az, STAIRS, Dir.WEST);
		plan.set(ax - 1, bottom, az, STAIRS, Dir.EAST);
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				plan.set(ax + dx, bottom, az + dz, SLAB);
				plan.set(ax + dx, bottom + 1, az + dz, CANDLE);
				for (int y = bottom + 2; y <= bottom + 4; y++) {
					plan.set(ax + dx * 2, y, az + dz * 2, CHAIN);
				}
			}
		}
		for (int y = bottom + 1; y <= bottom + 2; y++) {
			plan.set(-20, y, -15, IRON_BARS, Dir.NORTH);
			plan.set(-16, y, -19, IRON_BARS, Dir.EAST);
			plan.set(-16, y, -11, IRON_BARS, Dir.EAST);
		}
		plan.set(-18, bottom, -13, TORCH);
		plan.set(-14, bottom, -17, TORCH);
		plan.set(-18, bottom, -17, LANTERN);
		plan.set(-19, bottom + 4, -15, COBWEB);
		plan.set(-13, bottom + 4, -17, COBWEB);
		// paredes da sala do altar de tijolo (antes do revestimento de rocha bruta)
		for (int x = -20; x <= -12; x++) {
			for (int z = -19; z <= -11; z++) {
				for (int y = bottom - 1; y <= bottom + 5; y++) {
					boolean shell = x == -20 || x == -12 || z == -19 || z == -11 || y == bottom + 5;
					if (shell) {
						plan.setIfAbsent(x, y, z, stone(r, y - bottom, 6));
					}
				}
			}
		}
		// patio: trilhos saindo da mina, caixotes, monte de entulho, guindaste com lanterna
		for (int z = 1; z <= 4; z++) {
			plan.set(0, 0, z, RAIL, Dir.NORTH);
		}
		ground(plan, 3, 2);
		ground(plan, 4, 2);
		plan.set(3, 0, 2, CRATE);
		plan.set(4, 0, 2, CRATE, Dir.NORTH);
		plan.set(3, 1, 2, CRATE);
		plan.set(-3, 0, 6, ORE);
		plan.markLoose(-3, 0, 6);
		for (int[] c : new int[][] {{-4, 2}, {-5, 3}, {-4, 3}, {-5, 4}, {-6, 2}}) {
			rubble(plan, r, c[0], c[1], 1 + r.nextInt(2));
		}
		ground(plan, 5, 4);
		for (int y = 0; y <= 4; y++) {
			plan.set(5, y, 4, BEAM);
		}
		plan.set(4, 4, 4, BEAM, Dir.EAST);
		plan.set(3, 4, 4, BEAM, Dir.EAST);
		plan.set(3, 3, 4, CHAIN);
		plan.set(3, 2, 4, LANTERN_HANGING);
		line(plan, r, carved, true);
		for (int x = -8; x <= 8; x++) {
			for (int z = 1; z <= 10; z++) {
				if (plan.get(x, -1, z) != null) {
					airIfEmpty(plan, x, 0, z, x, 6, z);
				}
			}
		}
		plants(plan, r, -9, 1, 9, 11, 0.25);
		vines(plan, r, 0.08, 3);
	}

	/** Escora de mina: dois troncos em pe e um deitado no teto (ao longo de x). */
	private static void timberSet(RuinPlan plan, int feet, int z) {
		for (int y = feet; y <= feet + 2; y++) {
			plan.set(-2, y, z, BEAM);
			plan.set(2, y, z, BEAM);
		}
		for (int x = -2; x <= 2; x++) {
			plan.set(x, feet + 3, z, BEAM, Dir.EAST);
		}
	}

	private static void carve(RuinPlan plan, List<long[]> carved, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					plan.setIfAbsent(x, y, z, AIR);
					carved.add(new long[] {x, y, z});
				}
			}
		}
	}

	/**
	 * Reveste tudo o que foi escavado (cavernas por perto nao abrem buracos). Na mina: rocha bruta com veio de minerio
	 * em volta do salao, e grama por cima do que ficou acima do chao (a frente, z &ge; 1, fica aberta).
	 */
	private static void line(RuinPlan plan, Random r, List<long[]> carved, boolean mine) {
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (long[] c : carved) {
			for (int[] d : dirs) {
				int x = (int) c[0] + d[0];
				int y = (int) c[1] + d[1];
				int z = (int) c[2] + d[2];
				if (plan.get(x, y, z) != null) {
					continue;
				}
				if (!mine) {
					plan.set(x, y, z, stone(r, 0, 99));
					continue;
				}
				if (z >= 1) {
					continue;
				}
				RuinPart part;
				if (y >= 0 && d[1] == 1) {
					part = SOIL;
				} else if (z <= -10 && z >= -22 && Math.abs(x) <= 9 && r.nextInt(10) < 3) {
					part = ORE;
				} else {
					part = rock(r);
				}
				plan.set(x, y, z, part);
			}
		}
	}
}
