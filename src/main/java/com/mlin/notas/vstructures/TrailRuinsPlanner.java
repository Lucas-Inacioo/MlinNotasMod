package com.mlin.notas.vstructures;

import java.util.Random;

/**
 * Ruinas da trilha refeitas: um povoado antigo enterrado (como o vanilla, para escavar com o pincel). No meio a torre
 * de tijolos de barro com faixas de terracota colorida, vitrais e o topo quebrado saindo do chao; em volta de 4 a 6
 * casas enterradas (paredes de barro/terracota/tijolos, piso de terracota, entulho de cascalho, terra e lama dentro)
 * ligadas por ruas de barro compactado a 8 blocos de profundidade. Cascalho suspeito com a loot vanilla
 * ({@code archaeology/trail_ruins_common}, e {@code _rare} nas casas e no fundo da torre) fica no entulho e nas ruas;
 * potes decorados nos cantos. Para achar o lugar: pontas de parede e o toco da torre aparecem na superficie e ha um
 * poco de escavacao aberto dentro da torre (escada de mao ate o primeiro andar) com um acampamento de arqueologo ao
 * lado. Logica pura.
 *
 * <p>Referencias (so forma e paleta; nada copiado): as ruinas da trilha vanilla (terracota colorida, vitrais, ruas
 * enterradas) e sitios arqueologicos de builds do Planet Minecraft (poco de escavacao com escada e acampamento).
 */
public final class TrailRuinsPlanner extends PlanBuilder {
	public static final String COMMON = "minecraft:archaeology/trail_ruins_common";
	public static final String RARE = "minecraft:archaeology/trail_ruins_rare";
	public static final String MARKER_PIT = "pit";
	static final int ROAD = -8;
	static final int BASE = -13;

	private static final String[] TERRACOTTA = {"minecraft:terracotta", "minecraft:light_blue_terracotta", "minecraft:yellow_terracotta",
			"minecraft:red_terracotta", "minecraft:cyan_terracotta", "minecraft:brown_terracotta", "minecraft:light_gray_terracotta"};

	private TrailRuinsPlanner(Random random) {
		super(new VPlan(false), random);
	}

	public static VPlan plan(long seed, int variant) {
		TrailRuinsPlanner b = new TrailRuinsPlanner(new Random(seed * 5003L + 29));
		b.build();
		return b.p;
	}

	private String wall() {
		double d = r.nextDouble();
		return d < 0.45 ? "minecraft:mud_bricks" : d < 0.6 ? "minecraft:packed_mud" : d < 0.75 ? "minecraft:bricks" : pick(TERRACOTTA);
	}

	private String fillSoil() {
		double d = r.nextDouble();
		return d < 0.4 ? "minecraft:gravel" : d < 0.75 ? "minecraft:dirt" : d < 0.9 ? "minecraft:coarse_dirt" : "minecraft:mud";
	}

	private void build() {
		String band = pick(TERRACOTTA);
		tower(band);
		int houses = between(4, 6);
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int i = 0; i < houses; i++) {
			double a = a0 + i * Math.PI * 2 / houses;
			int dist = between(13, 17);
			int hx = (int) Math.round(Math.cos(a) * dist);
			int hz = (int) Math.round(Math.sin(a) * dist);
			road(hx, hz);
			house(hx, hz, between(2, 3), i == 0);
		}
		camp();
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, -8);
	}

	private void tower(String band) {
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
				set(x, BASE, z, "minecraft:terracotta");
				int top = 4 - (Math.abs(x + z) % 3 == 0 ? between(0, 2) : between(1, 5));
				for (int y = BASE + 1; y <= 4; y++) {
					if (edge) {
						if (y > top) {
							continue;
						}
						boolean stripe = (y - BASE) % 5 == 0;
						set(x, y, z, stripe ? band : wall());
					} else if (y < ROAD) {
						set(x, y, z, fillSoil());
					} else if (y == ROAD) {
						set(x, y, z, "minecraft:packed_mud");
					} else {
						set(x, y, z, VPlan.AIR); // poco de escavacao aberto
					}
				}
			}
		}
		// vitrais
		for (int y : new int[] {-5, -1, 2}) {
			for (int[] d : new int[][] {{0, -3}, {0, 3}, {-3, 0}, {3, 0}}) {
				if (p.get(d[0], y, d[1]) != null) {
					set(d[0], y, d[1], pick("minecraft:light_blue_stained_glass_pane", "minecraft:yellow_stained_glass_pane",
							"minecraft:magenta_stained_glass_pane"));
				}
			}
		}
		// escada de mao do fundo do poco ate a superficie, passarela de tabuas e a brecha por onde se entra
		for (int y = ROAD + 1; y <= 0; y++) {
			set(2, y, 2, s("ladder", "facing=north"));
			set(2, y, 3, wall());
		}
		for (int[] c : new int[][] {{0, -2}, {1, -2}, {2, -2}, {2, -1}, {2, 0}, {2, 1}}) {
			set(c[0], -1, c[1], "minecraft:spruce_planks");
		}
		for (int y = 0; y <= 2; y++) {
			set(0, y, -3, VPlan.AIR);
		}
		set(0, -1, -3, "minecraft:mud_bricks");
		// cascalho suspeito no fundo do poco e no entulho embaixo
		for (int i = 0; i < 6; i++) {
			int x = between(-2, 2);
			int z = between(-2, 2);
			if (x == 2 && z == 2) {
				continue;
			}
			p.suspicious(x, ROAD, z, "minecraft:suspicious_gravel", COMMON);
		}
		p.suspicious(between(-2, 2), BASE + 2, between(-2, 2), "minecraft:suspicious_gravel", RARE);
		p.mark(MARKER_PIT, 0, ROAD + 1, 0);
		set(-2, ROAD + 1, 2, s("decorated_pot", "facing=south"));
	}

	private void road(int hx, int hz) {
		int steps = Math.max(Math.abs(hx), Math.abs(hz));
		for (int i = 4; i <= steps; i++) {
			int x = Math.round(hx * i / (float) steps);
			int z = Math.round(hz * i / (float) steps);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (p.get(x + dx, ROAD, z + dz) == null) {
						if (chance(0.06)) {
							p.suspicious(x + dx, ROAD, z + dz, "minecraft:suspicious_gravel", COMMON);
						} else {
							set(x + dx, ROAD, z + dz, chance(0.7) ? "minecraft:packed_mud" : pick(TERRACOTTA));
						}
					}
				}
			}
		}
	}

	private void house(int cx, int cz, int half, boolean rare) {
		int floor = ROAD + 1;
		int top = chance(0.4) ? between(0, 1) : -2;
		for (int x = cx - half - 1; x <= cx + half + 1; x++) {
			for (int z = cz - half - 1; z <= cz + half + 1; z++) {
				boolean edge = Math.abs(x - cx) == half + 1 || Math.abs(z - cz) == half + 1;
				set(x, floor - 1, z, edge ? wall() : pick(TERRACOTTA));
				for (int y = floor; y <= -1; y++) {
					if (edge) {
						int wallTop = top - (((x + z) & 3) == 0 ? between(0, 2) : 0);
						if (y <= wallTop) {
							set(x, y, z, wall());
						}
					} else {
						set(x, y, z, fillSoil());
					}
				}
				if (edge && top >= 0) {
					for (int y = 0; y <= top; y++) {
						if (p.get(x, y - 1, z) != null && chance(0.7)) {
							set(x, y, z, wall());
						}
					}
				}
			}
		}
		int digs = 0;
		while (digs < 4) {
			int x = cx + between(-half, half);
			int z = cz + between(-half, half);
			int y = floor + between(0, 2);
			p.suspicious(x, y, z, "minecraft:suspicious_gravel", digs == 0 && rare ? RARE : COMMON);
			digs++;
		}
		set(cx - half, floor, cz - half, s("decorated_pot", "facing=" + pick(N, S, E, W)));
		// marcas na superficie
		for (int x = cx - half; x <= cx + half; x++) {
			for (int z = cz - half; z <= cz + half; z++) {
				if (chance(0.3) && p.get(x, -1, z) != null && top < 0) {
					set(x, -1, z, chance(0.5) ? "minecraft:coarse_dirt" : "minecraft:gravel");
				}
			}
		}
	}

	private void camp() {
		int x = 6;
		int z = -5;
		set(x, 0, z, s("barrel", "facing=up"));
		set(x + 1, 0, z, s("decorated_pot", "facing=north"));
		set(x, 0, z + 1, s("campfire", "lit=false"));
		for (int dz = -1; dz <= 1; dz++) {
			set(x + 2, 0, z + dz, s("spruce_fence"));
			set(x + 2, 1, z + dz, s("spruce_fence"));
			set(x + 2, 2, z + dz, s("white_wool"));
			set(x + 1, 2, z + dz, s("white_wool"));
		}
		set(x + 1, 0, z + 1, s("scaffolding", "distance=0"));
		set(x - 1, 0, z - 1, s("oak_slab"));
	}
}
