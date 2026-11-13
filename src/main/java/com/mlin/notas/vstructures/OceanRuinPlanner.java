package com.mlin.notas.vstructures;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ruinas oceanicas refeitas: um conjunto no fundo do mar com um templo grande (podio com degraus, peristilo de colunas
 * caneladas, algumas quebradas e outras caidas no chao, cela com porta, parte do frontao e do telhado ainda de pe,
 * altar com o bau {@code underwater_ruin_big} e chao de areia/cascalho com blocos suspeitos de arqueologia) e de 2 a 5
 * ruinas pequenas em volta (arco partido, casinha desabada com o bau {@code underwater_ruin_small}, coluna caida,
 * pedestal com estatua quebrada). Quente: arenito, arenito cortado/cinzelado e liso, com coral, picles do mar e capim
 * marinho. Frio: tijolos de pedra (com musgo e rachados), algas, cascalho e blocos de magma que fazem colunas de bolhas.
 * Afogados nascem com o templo. Logica pura.
 *
 * <p>Referencias (so forma e paleta; nada copiado): "Hyperborea Atlantis" do Vaxor (templos gregos claros cercados de
 * coral), as ruinas oceanicas vanilla (grupo de ruinas pequenas em volta de uma grande, bolhas de magma) e os templos
 * em colunata de builds de Atlantida do Planet Minecraft.
 */
public final class OceanRuinPlanner extends PlanBuilder {
	public static final String BIG = "minecraft:chests/underwater_ruin_big";
	public static final String SMALL = "minecraft:chests/underwater_ruin_small";
	public static final String MARKER_ALTAR = "altar";

	private final boolean warm;
	private final String dig;
	private final String digBlock;
	private final String floorSoft;

	private OceanRuinPlanner(Random random, boolean warm) {
		super(new VPlan(false), random);
		this.warm = warm;
		this.dig = warm ? "minecraft:archaeology/ocean_ruin_warm" : "minecraft:archaeology/ocean_ruin_cold";
		this.digBlock = warm ? "minecraft:suspicious_sand" : "minecraft:suspicious_gravel";
		this.floorSoft = warm ? "minecraft:sand" : "minecraft:gravel";
	}

	public static VPlan plan(long seed, int variant, boolean warm) {
		OceanRuinPlanner b = new OceanRuinPlanner(new Random(seed * 2741L + (warm ? 1 : 2)), warm);
		b.build();
		b.p.setWaterLevel(40);
		return b.p;
	}

	private String wall() {
		double d = r.nextDouble();
		if (warm) {
			return d < 0.5 ? "minecraft:sandstone" : d < 0.75 ? "minecraft:cut_sandstone" : d < 0.9 ? "minecraft:smooth_sandstone" : "minecraft:sand";
		}
		return d < 0.45 ? "minecraft:stone_bricks" : d < 0.75 ? "minecraft:mossy_stone_bricks" : d < 0.92 ? "minecraft:cracked_stone_bricks"
				: "minecraft:cobblestone";
	}

	private String fine() {
		return warm ? "minecraft:smooth_sandstone" : "minecraft:stone_bricks";
	}

	private String chiseled() {
		return warm ? "minecraft:chiseled_sandstone" : "minecraft:chiseled_stone_bricks";
	}

	private String stairs() {
		return warm ? "minecraft:sandstone_stairs" : (chance(0.5) ? "minecraft:mossy_stone_brick_stairs" : "minecraft:stone_brick_stairs");
	}

	private String slab() {
		return warm ? "minecraft:sandstone_slab" : "minecraft:stone_brick_slab";
	}

	private String columnBlock() {
		return warm ? "minecraft:cut_sandstone" : (chance(0.3) ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks");
	}

	private void build() {
		temple();
		List<int[]> spots = new ArrayList<>();
		int count = between(2, 5);
		double a0 = r.nextDouble() * Math.PI * 2;
		for (int i = 0; i < count; i++) {
			double a = a0 + i * (Math.PI * 2 / count) + (r.nextDouble() - 0.5) * 0.6;
			int dist = between(17, 24);
			spots.add(new int[] {(int) Math.round(Math.cos(a) * dist), (int) Math.round(Math.sin(a) * dist)});
		}
		int smallChests = 0;
		for (int i = 0; i < spots.size(); i++) {
			int[] s = spots.get(i);
			int kind = i == 0 ? 1 : r.nextInt(4);
			switch (kind) {
				case 0 -> arch(s[0], s[1]);
				case 1 -> {
					hut(s[0], s[1], smallChests < 2);
					smallChests++;
				}
				case 2 -> fallenColumn(s[0], s[1]);
				default -> statue(s[0], s[1]);
			}
		}
		garden();
		for (int x = -9; x <= 9; x++) {
			for (int z = -12; z <= 12; z++) {
				for (int y = 0; y <= 12; y++) {
					p.setIfAbsent(x, y, z, VPlan.AIR);
				}
			}
		}
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 1, -12);
	}

	/** Templo: podio 13x19, peristilo, cela, altar e telhado meio caido. Frente em -z. */
	private void temple() {
		int hx = 6;
		int hz = 9;
		// podio com degraus em volta
		for (int x = -hx - 1; x <= hx + 1; x++) {
			for (int z = -hz - 1; z <= hz + 1; z++) {
				boolean step = Math.abs(x) == hx + 1 || Math.abs(z) == hz + 1;
				if (step) {
					if (chance(0.85)) {
						String face = Math.abs(z) == hz + 1 ? (z < 0 ? S : N) : (x < 0 ? E : W);
						set(x, 0, z, s(stairs(), "facing=" + face));
					}
				} else {
					set(x, 0, z, wall());
				}
			}
		}
		// piso do templo: lajes finas, com faixa de areia/cascalho e blocos suspeitos
		for (int x = -hx; x <= hx; x++) {
			for (int z = -hz; z <= hz; z++) {
				set(x, 1, z, (Math.abs(x) + Math.abs(z)) % 4 == 0 ? chiseled() : fine());
				if (Math.abs(x) <= 3 && z >= -5 && z <= 5 && chance(0.3)) {
					set(x, 1, z, floorSoft);
				}
			}
		}
		int digs = 0;
		while (digs < 4) {
			int x = between(-3, 3);
			int z = between(-5, 5);
			if (!(x == 0 && z == 4) && !p.archaeologyMap().containsKey(VPlan.key(x, 1, z))) {
				p.suspicious(x, 1, z, digBlock, dig);
				digs++;
			}
		}
		// colunas do peristilo (cada 3 blocos na borda)
		for (int x = -hx; x <= hx; x += 3) {
			for (int z = -hz; z <= hz; z += 3) {
				if (Math.abs(x) != hx && Math.abs(z) != hz) {
					continue;
				}
				column(x, 2, z, chance(0.3) ? between(1, 4) : 7);
			}
		}
		// arquitrave onde as colunas ficaram inteiras
		for (int x = -hx; x <= hx; x++) {
			for (int z = -hz; z <= hz; z++) {
				if ((Math.abs(x) == hx || Math.abs(z) == hz) && hasFullColumnNear(x, z)) {
					set(x, 9, z, fine());
				}
			}
		}
		// cela (paredes internas) com porta na frente
		int cx = 3;
		int cz1 = -4;
		int cz2 = 7;
		for (int x = -cx; x <= cx; x++) {
			for (int z = cz1; z <= cz2; z++) {
				if (Math.abs(x) != cx && z != cz1 && z != cz2) {
					continue;
				}
				int top = 2 + between(1, 6);
				if (Math.abs(x) <= 1 && z == cz1) {
					top = 1; // porta
				}
				for (int y = 2; y <= top; y++) {
					set(x, y, z, wall());
				}
			}
		}
		set(-2, 4, cz1, chiseled());
		set(2, 4, cz1, chiseled());
		// altar com o bau grande
		set(0, 2, 4, chiseled());
		set(-1, 2, 4, s(stairs(), "facing=east"));
		set(1, 2, 4, s(stairs(), "facing=west"));
		p.loot(0, 3, 4, s("chest", "facing=north"), BIG);
		p.mark(MARKER_ALTAR, 0, 3, 4);
		// frontao meio de pe na frente
		for (int x = -hx; x <= hx; x++) {
			int h = hx - Math.abs(x);
			for (int y = 10; y <= 10 + h / 2; y++) {
				if (p.get(x, 9, -hz) != null && chance(0.85)) {
					set(x, y, -hz, y == 10 + h / 2 ? s(slab()) : wall());
				}
			}
		}
		// telhado parcial sobre a cela
		for (int x = -cx; x <= cx; x++) {
			for (int z = cz1; z <= cz2; z++) {
				if (p.get(x, 8, z) != null && !BlockTraits.isAir(p.get(x, 8, z)) && chance(0.6)) {
					set(x, 9, z, s(slab()));
				}
			}
		}
		// entulho em volta
		for (int i = 0; i < 14; i++) {
			int x = between(-hx - 5, hx + 5);
			int z = between(-hz - 5, hz + 5);
			if (Math.abs(x) > hx + 1 || Math.abs(z) > hz + 1) {
				if (p.get(x, 0, z) == null) {
					p.set(x, 0, z, chance(0.5) ? wall() : s(slab()), VPlan.LOOSE);
				}
			}
		}
		p.entity("minecraft:drowned", 0.5, 2, 0.5, "");
		p.entity("minecraft:drowned", -4.5, 2, -7.5, "");
		if (!warm) {
			set(-hx - 3, 0, 2, "minecraft:magma_block");
			set(hx + 3, 0, -3, "minecraft:magma_block");
		}
	}

	private boolean hasFullColumnNear(int x, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				String s = p.get(x + dx, 8, z + dz);
				if (s != null && !BlockTraits.isAir(s)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Coluna canelada: base, fuste e capitel; altura menor = quebrada. */
	private void column(int x, int y0, int z, int h) {
		for (int y = y0; y < y0 + h; y++) {
			boolean base = y == y0;
			boolean cap = y == y0 + h - 1 && h == 7;
			set(x, y, z, base || cap ? chiseled() : (warm ? "minecraft:cut_sandstone" : columnBlock()));
		}
	}

	private void arch(int x, int z) {
		boolean alongX = chance(0.5);
		for (int side : new int[] {-2, 2}) {
			int px = alongX ? x + side : x;
			int pz = alongX ? z : z + side;
			int h = side < 0 || chance(0.6) ? 5 : between(2, 3);
			for (int y = 0; y < h; y++) {
				set(px, y, pz, y == 0 ? chiseled() : columnBlock());
			}
		}
		if (p.get(alongX ? x + 2 : x, 4, alongX ? z : z + 2) != null) {
			for (int d = -2; d <= 2; d++) {
				set(alongX ? x + d : x, 5, alongX ? z : z + d, d == 0 ? chiseled() : wall());
			}
		} else {
			// meio arco: a outra metade caida no chao
			for (int d = -2; d <= 0; d++) {
				set(alongX ? x + d : x, 5, alongX ? z : z + d, wall());
			}
			for (int d = 2; d <= 4; d++) {
				p.set(alongX ? x + d : x + 1, 0, alongX ? z + 1 : z + d, wall(), VPlan.LOOSE);
			}
		}
	}

	private void hut(int x, int z, boolean chest) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
				set(x + dx, 0, z + dz, edge ? wall() : (chance(0.6) ? floorSoft : wall()));
				if (edge) {
					int h = chance(0.25) ? 0 : between(1, 4);
					if (dz == -3 && Math.abs(dx) <= 0) {
						h = 0;
					}
					for (int y = 1; y <= h; y++) {
						set(x + dx, y, z + dz, wall());
					}
				}
			}
		}
		if (chest) {
			p.loot(x + 1, 1, z + 1, s("chest", "facing=north"), SMALL);
		}
		if (chance(0.6)) {
			p.suspicious(x - 1, 0, z, digBlock, dig);
		}
	}

	private void fallenColumn(int x, int z) {
		boolean alongX = chance(0.5);
		set(x, 0, z, chiseled());
		set(x, 1, z, columnBlock());
		for (int d = 1; d <= between(4, 6); d++) {
			set(alongX ? x + d : x, 0, alongX ? z : z + d, warm ? "minecraft:cut_sandstone" : axisOrBricks());
		}
	}

	private String axisOrBricks() {
		return chance(0.5) ? "minecraft:stone_bricks" : "minecraft:mossy_stone_bricks";
	}

	private void statue(int x, int z) {
		set(x, 0, z, chiseled());
		set(x, 1, z, fine());
		set(x, 2, z, warm ? "minecraft:chiseled_red_sandstone" : "minecraft:chiseled_stone_bricks");
		if (chance(0.5)) {
			set(x, 3, z, s("skeleton_skull", "rotation=" + r.nextInt(16)));
		} else {
			p.set(x + 1, 0, z, s("skeleton_skull", "rotation=" + r.nextInt(16)), VPlan.LOOSE);
		}
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			if (p.get(x + d[0], 0, z + d[1]) == null) {
				set(x + d[0], 0, z + d[1], s(stairs(), "facing=" + (d[0] == 1 ? W : d[0] == -1 ? E : d[1] == 1 ? N : S)));
			}
		}
	}

	private void garden() {
		String[] corals = {"tube", "brain", "bubble", "fire", "horn"};
		for (int x = -30; x <= 30; x++) {
			for (int z = -30; z <= 30; z++) {
				if (p.get(x, 0, z) != null || x * x + z * z > 900) {
					continue;
				}
				double roll = r.nextDouble();
				if (warm) {
					if (roll < 0.04) {
						String c = pick(corals);
						set(x, 0, z, s(c + "_coral_block"));
						set(x, 1, z, chance(0.5) ? s(c + "_coral_fan") : chance(0.5) ? s(c + "_coral") : s("sea_pickle", "pickles=" + between(1, 4)));
					} else if (roll < 0.08) {
						set(x, 0, z, s(pick(corals) + "_coral_fan"));
					} else if (roll < 0.25) {
						set(x, 0, z, chance(0.8) ? "minecraft:seagrass" : s("sea_pickle", "pickles=" + between(1, 3)));
					}
				} else {
					if (roll < 0.035) {
						int h = between(3, 12);
						for (int y = 0; y < h; y++) {
							set(x, y, z, y == h - 1 ? s("kelp", "age=" + between(15, 25)) : "minecraft:kelp_plant");
						}
					} else if (roll < 0.2) {
						set(x, 0, z, "minecraft:seagrass");
					}
				}
			}
		}
	}
}
