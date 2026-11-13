package com.mlin.notas.ruins;

import com.mlin.notas.ruins.RuinPlan.Dir;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Monta o plano de cada ruina a partir de uma semente (mesma semente = mesma ruina). Tudo e construido virado para
 * o sul (entrada em +z) e no fim girado por um angulo sorteado. Logica pura.
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
		for (long k : plan.blocks().keySet()) {
			if (RuinPlan.keyY(k) >= -1) {
				front = Math.max(front, RuinPlan.keyZ(k));
			}
		}
		plan.mark(RuinPlan.MARKER_ENTRANCE, 0, 0, front + 2);
		return plan.rotated(random.nextInt(4));
	}

	/** Parede com desgaste: musgo e rachaduras sorteados. */
	private static RuinPart weathered(Random random) {
		int roll = random.nextInt(10);
		return roll < 3 ? RuinPart.WALL_MOSSY : roll < 4 ? RuinPart.WALL_CRACKED : RuinPart.WALL;
	}

	private static void airIfEmpty(RuinPlan plan, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					plan.setIfAbsent(x, y, z, RuinPart.AIR);
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
		plan.set(x, -1, z, RuinPart.FOUNDATION);
		plan.set(x, 0, z, RuinPart.WAYSTONE);
		plan.set(x, 1, z, RuinPart.WAYSTONE_TOP);
		plan.mark(RuinPlan.MARKER_WAYSTONE, x, 0, z);
	}

	private static void chest(RuinPlan plan, int x, int y, int z, Dir facing) {
		plan.set(x, y, z, RuinPart.CHEST, facing);
		if (plan.marker(RuinPlan.MARKER_CHEST) == null) {
			plan.mark(RuinPlan.MARKER_CHEST, x, y, z);
		}
	}

	// --- torre de vigia caida ---

	static final int TOWER_HALF = 3;
	static final int TOWER_TOP = 8;

	private static void watchtower(RuinPlan plan, Random random) {
		int h = TOWER_HALF;
		int sx = random.nextBoolean() ? 1 : -1; // lado que desabou (sempre na metade sul)
		fill(plan, -h - 1, -1, -h - 1, h + 1, -1, h + 1, RuinPart.FOUNDATION);
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				boolean ring = Math.abs(x) == h || Math.abs(z) == h;
				boolean collapsed = x * sx >= 1 && z >= 1;
				if (!ring) {
					continue;
				}
				int top = collapsed ? 1 + random.nextInt(3) : TOWER_TOP;
				for (int y = 0; y <= top; y++) {
					plan.set(x, y, z, Math.abs(x) == h && Math.abs(z) == h ? RuinPart.BEAM : weathered(random));
				}
				// ameias no topo (so onde nao desabou)
				if (!collapsed && (x + z) % 2 == 0) {
					plan.set(x, TOWER_TOP + 1, z, weathered(random));
				}
			}
		}
		// porta ao sul
		plan.set(0, 0, h, RuinPart.AIR);
		plan.set(0, 1, h, RuinPart.AIR);
		// janelas estreitas
		plan.set(-h, 5, 0, RuinPart.AIR);
		plan.set(h, 5, 0, RuinPart.AIR);
		// andares: o do meio com buracos, o de cima so na metade norte (a sul caiu)
		for (int x = -h + 1; x <= h - 1; x++) {
			for (int z = -h + 1; z <= h - 1; z++) {
				boolean collapsed = x * sx >= 1 && z >= 1;
				if (!collapsed && !(z >= 1 && random.nextInt(4) == 0)) {
					plan.set(x, 4, z, RuinPart.FLOOR);
				}
				if (z <= 0 && !(x * sx >= 1 && z == 0)) {
					plan.set(x, TOWER_TOP, z, RuinPart.FLOOR);
				}
			}
		}
		// escada de mao na parede norte, do chao ate o topo
		for (int y = 0; y <= TOWER_TOP; y++) {
			plan.set(0, y, -h + 1, RuinPart.LADDER, Dir.SOUTH);
		}
		// bau no topo, longe do lado que desabou
		chest(plan, -2 * sx, TOWER_TOP + 1, -h + 1, Dir.SOUTH);
		airIfEmpty(plan, -h + 1, 0, -h + 1, h - 1, TOWER_TOP + 2, h - 1);
		// entulho do desabamento no chao, do lado de fora
		for (int x = h + 1; x <= h + 4; x++) {
			for (int z = 1; z <= h + 3; z++) {
				if (random.nextInt(10) < 4) {
					plan.set(x * sx, -1, z, RuinPart.FOUNDATION);
					plan.set(x * sx, 0, z, random.nextBoolean() ? RuinPart.SLAB : weathered(random));
				}
			}
		}
		waystone(plan, -3 * sx, h + 3);
	}

	// --- acampamento abandonado ---

	private static void camp(RuinPlan plan, Random random) {
		for (int x = -8; x <= 8; x++) {
			for (int z = -5; z <= 5; z++) {
				double d = (x / 8.5) * (x / 8.5) + (z / 5.5) * (z / 5.5);
				if (d <= 1.0) {
					plan.set(x, -1, z, random.nextInt(5) == 0 ? RuinPart.FOUNDATION : RuinPart.GROUND);
				}
			}
		}
		plan.set(0, 0, 0, RuinPart.CAMPFIRE);
		plan.set(-2, 0, 0, RuinPart.BEAM);
		plan.set(2, 0, 1, RuinPart.BEAM);
		tent(plan, -5, false);
		tent(plan, 5, random.nextInt(3) == 0);
		chest(plan, -2, 0, -4, Dir.SOUTH);
		plan.set(2, 0, -4, RuinPart.BARREL, Dir.SOUTH);
		plan.set(3, 0, -4, RuinPart.WOOD_SLAB);
		for (int x = -8; x <= 8; x++) {
			for (int z = -5; z <= 5; z++) {
				if (plan.get(x, -1, z) != null) {
					airIfEmpty(plan, x, 0, z, x, 5, z);
				}
			}
		}
	}

	/** Barraca em "A" de la ao longo de z, aberta ao sul; caida = so a la no chao. */
	private static void tent(RuinPlan plan, int cx, boolean fallen) {
		for (int z = -3; z <= 2; z++) {
			if (fallen) {
				if (z != 0) {
					plan.set(cx, 0, z, RuinPart.TENT);
				}
				continue;
			}
			plan.set(cx - 1, 0, z, RuinPart.TENT);
			plan.set(cx + 1, 0, z, RuinPart.TENT);
			plan.set(cx, 1, z, RuinPart.TENT);
			plan.set(cx, 0, z, z == -3 ? RuinPart.TENT : RuinPart.AIR);
		}
	}

	// --- poco antigo ---

	static final int WELL_ROOM_FLOOR = -10;

	private static void well(RuinPlan plan, Random random) {
		fill(plan, -2, -1, -2, 2, -1, 2, RuinPart.FOUNDATION);
		// borda de 3x3 em volta do poco 1x1
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (x != 0 || z != 0) {
					plan.set(x, 0, z, weathered(random));
				}
			}
		}
		for (int x = -1; x <= 1; x += 2) {
			for (int z = -1; z <= 1; z += 2) {
				plan.set(x, 1, z, RuinPart.FENCE);
				plan.set(x, 2, z, RuinPart.FENCE);
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (random.nextInt(5) > 0 || (Math.abs(x) == 1 && Math.abs(z) == 1)) {
					plan.set(x, 3, z, RuinPart.WOOD_SLAB);
				}
			}
		}
		// sala la embaixo: x -2..2, z 0..4 (a parede norte continua a do poco), pes de -9 a -7
		int floor = WELL_ROOM_FLOOR;
		fill(plan, -3, floor, -1, 3, floor + 4, 5, RuinPart.WALL);
		for (int x = -3; x <= 3; x++) {
			for (int z = -1; z <= 5; z++) {
				if (random.nextInt(6) == 0) {
					plan.set(x, floor + 1 + random.nextInt(3), z, RuinPart.WALL_MOSSY);
				}
			}
		}
		fill(plan, -2, floor, 0, 2, floor, 4, RuinPart.PAVING);
		fill(plan, -2, floor + 1, 0, 2, floor + 3, 4, RuinPart.AIR);
		// poco: de y=0 ate o teto da sala, revestido
		for (int y = floor + 4; y <= -1; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					if (x != 0 || z != 0) {
						plan.set(x, y, z, weathered(random));
					}
				}
			}
			plan.set(0, y, 0, RuinPart.AIR);
		}
		for (int y = floor + 1; y <= 0; y++) {
			plan.set(0, y, 0, RuinPart.LADDER, Dir.SOUTH);
		}
		plan.set(0, 1, 0, RuinPart.AIR);
		plan.set(0, 2, 0, RuinPart.AIR);
		chest(plan, 2, floor + 1, 4, Dir.WEST);
		plan.set(-2, floor + 1, 4, RuinPart.BARREL, Dir.EAST);
		plan.set(-2, floor + 3, 0, RuinPart.COBWEB);
		plan.set(2, floor + 3, 1, RuinPart.COBWEB);
		plan.set(1, floor + 1, 0, RuinPart.TORCH);
	}

	// --- capela em ruinas ---

	private static void chapel(RuinPlan plan, Random random) {
		int hx = 4;
		int hz = 6;
		fill(plan, -hx, -1, -hz, hx, -1, hz, RuinPart.PAVING);
		for (int x = -hx; x <= hx; x++) {
			for (int z = -hz; z <= hz; z++) {
				if (Math.abs(x) != hx && Math.abs(z) != hz) {
					continue;
				}
				boolean corner = Math.abs(x) == hx && Math.abs(z) == hz;
				int top = corner ? 5 : 2 + random.nextInt(4);
				if (z == -hz) {
					top = Math.max(top, 4); // a parede do altar fica de pe
				}
				for (int y = 0; y <= top; y++) {
					plan.set(x, y, z, corner ? RuinPart.BEAM : weathered(random));
				}
			}
		}
		// porta e janelas
		for (int y = 0; y <= 2; y++) {
			plan.set(0, y, hz, RuinPart.AIR);
		}
		for (int z = -3; z <= 3; z += 3) {
			plan.set(-hx, 2, z, RuinPart.AIR);
			plan.set(hx, 2, z, RuinPart.AIR);
		}
		// altar: estrado de 3 blocos e o atril em cima (lugar do item de invocar o chefe)
		for (int x = -1; x <= 1; x++) {
			plan.set(x, 0, -hz + 1, RuinPart.ALTAR);
		}
		plan.set(0, 1, -hz + 1, RuinPart.LECTERN, Dir.SOUTH);
		plan.mark(RuinPlan.MARKER_BOSS_SUMMON, 0, 1, -hz + 1);
		chest(plan, hx - 1, 0, -hz + 1, Dir.WEST);
		// bancos dos dois lados do corredor
		for (int z = -2; z <= 2; z += 2) {
			for (int x : new int[] {-3, -2, 2, 3}) {
				if (random.nextInt(5) > 0) {
					plan.set(x, 0, z, RuinPart.WOOD_SLAB);
				}
			}
		}
		// vigas do telhado (algumas cairam) e restos de telhado
		for (int z = -hz + 2; z <= hz - 2; z += 4) {
			if (random.nextInt(3) == 0) {
				continue;
			}
			for (int x = -hx; x <= hx; x++) {
				plan.set(x, 6, z, RuinPart.BEAM);
			}
		}
		for (int x = -hx; x <= hx; x++) {
			for (int z = -hz; z <= hz; z++) {
				if (plan.get(x, 6, z) == null && random.nextInt(4) == 0) {
					plan.set(x, 6, z, RuinPart.SLAB);
				}
			}
		}
		airIfEmpty(plan, -hx + 1, 0, -hz + 1, hx - 1, 5, hz - 1);
		waystone(plan, 3, hz + 3);
		plan.set(-3, 0, hz + 2, RuinPart.SLAB);
		plan.set(-3, -1, hz + 2, RuinPart.FOUNDATION);
	}

	// --- mina desativada ---

	static final int MINE_STEPS = 9;
	/** Pes no fundo da mina (sala do veio e sala do chefe). */
	static final int MINE_BOTTOM = -MINE_STEPS + 1;

	private static void mine(RuinPlan plan, Random random) {
		plan.ironVein(random.nextBoolean());
		List<long[]> carved = new ArrayList<>();
		// boca da mina: alicerce na frente e moldura de troncos
		fill(plan, -2, -1, 1, 2, -1, 3, RuinPart.FOUNDATION);
		for (int y = 0; y <= 2; y++) {
			plan.set(-2, y, 0, RuinPart.BEAM);
			plan.set(2, y, 0, RuinPart.BEAM);
		}
		fill(plan, -2, 3, 0, 2, 3, 0, RuinPart.PLANKS);
		plan.set(-2, 0, 1, RuinPart.FENCE);
		plan.set(2, 0, 1, RuinPart.FENCE);
		// rampa descendo para o norte: degrau i em z=-i, pes em y=-i
		for (int i = 0; i < MINE_STEPS; i++) {
			int z = -i;
			int feet = -i;
			plan.set(-1, feet - 1, z, RuinPart.PLANKS);
			plan.set(0, feet - 1, z, RuinPart.PLANKS);
			plan.set(1, feet - 1, z, RuinPart.PLANKS);
			carve(plan, carved, -1, feet, z, 1, feet + 2, z);
			if (i > 0 && i % 3 == 0) {
				// escora: duas cercas e uma viga no teto
				plan.set(-1, feet, z, RuinPart.FENCE);
				plan.set(-1, feet + 1, z, RuinPart.FENCE);
				plan.set(1, feet, z, RuinPart.FENCE);
				plan.set(1, feet + 1, z, RuinPart.FENCE);
				for (int x = -2; x <= 2; x++) {
					plan.set(x, feet + 3, z, RuinPart.PLANKS);
				}
			}
		}
		int bottom = MINE_BOTTOM;
		// sala do veio: x -3..3, z -15..-9
		carve(plan, carved, -3, bottom, -15, 3, bottom + 2, -9);
		fill(plan, -3, bottom - 1, -15, 3, bottom - 1, -9, RuinPart.PLANKS);
		for (int[] c : new int[][] {{-3, -9}, {3, -9}, {-3, -15}, {3, -15}}) {
			for (int y = bottom; y <= bottom + 2; y++) {
				plan.set(c[0], y, c[1], RuinPart.FENCE);
			}
		}
		for (int x = -4; x <= 4; x++) {
			plan.set(x, bottom + 3, -12, RuinPart.PLANKS);
		}
		// veio: parede norte e um pedaco da leste
		for (int x = -2; x <= 2; x++) {
			for (int y = bottom; y <= bottom + 2; y++) {
				if (random.nextInt(10) < 6) {
					plan.set(x, y, -16, RuinPart.ORE);
				}
			}
		}
		for (int z = -14; z <= -12; z++) {
			for (int y = bottom; y <= bottom + 1; y++) {
				plan.set(4, y, z, RuinPart.ORE);
			}
		}
		chest(plan, -2, bottom, -15, Dir.SOUTH);
		plan.set(2, bottom, -10, RuinPart.TORCH);
		plan.set(-2, bottom + 2, -10, RuinPart.COBWEB);
		// corredor para oeste ate a sala do altar do chefe (Golem de Cobalto, futuro)
		carve(plan, carved, -7, bottom, -12, -4, bottom + 2, -12);
		carve(plan, carved, -13, bottom, -15, -8, bottom + 3, -9);
		fill(plan, -13, bottom - 1, -15, -8, bottom - 1, -9, RuinPart.PAVING);
		plan.set(-10, bottom, -12, RuinPart.BOSS_ALTAR);
		plan.mark(RuinPlan.MARKER_BOSS_ALTAR, -10, bottom, -12);
		plan.set(-8, bottom, -9, RuinPart.TORCH);
		plan.set(-13, bottom, -15, RuinPart.TORCH);
		// revestimento de tudo o que foi escavado (cavernas por perto nao abrem buracos na mina)
		for (long[] c : carved) {
			int x = (int) c[0];
			int y = (int) c[1];
			int z = (int) c[2];
			for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
				int nz = z + d[2];
				if (nz >= 1) {
					continue; // a frente da mina fica aberta
				}
				plan.setIfAbsent(x + d[0], y + d[1], nz, weathered(random));
			}
		}
	}

	private static void carve(RuinPlan plan, List<long[]> carved, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					plan.setIfAbsent(x, y, z, RuinPart.AIR);
					carved.add(new long[] {x, y, z});
				}
			}
		}
	}
}
