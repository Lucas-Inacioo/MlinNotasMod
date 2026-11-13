package com.mlin.notas.vstructures;

import java.util.Random;

/**
 * Portal em ruinas refeito, um por variante vanilla ({@link VKind} RUINED_PORTAL*): santuario de pedra (estrado com
 * degraus, piso rachado, obeliscos quebrados dos lados com lanterna) em volta da moldura de obsidiana partida (blocos
 * faltando, obsidiana chorona, pedacos caidos no chao; as vezes a moldura gigante, as vezes caida deitada). O chao em
 * volta vira netherrack com magma e uma poca de lava (menos nas variantes frias, no oceano e na selva), ha blocos de
 * ouro no entulho e o bau {@code ruined_portal}. Variantes: comum e montanha (tijolos de pedra, fria com neve e gelo),
 * deserto (meio enterrado na areia, sem musgo), selva (coberta de folhas e trepadeiras, muito musgo), pantano
 * (trepadeiras, musgo), oceano (no fundo do mar, muito musgo, magma que faz bolhas) e Nether (pedra-negra no lugar da
 * pedra). Moldura no plano x-y, frente em -z. Logica pura.
 *
 * <p>Referencias (so forma; nada copiado): os schematics "Ruined Portal 1..8" do Minecraft Maps (moldura partida,
 * netherrack espalhado, ouro no entulho) e santuarios de portal de builds do Planet Minecraft (estrado e obeliscos).
 */
public final class RuinedPortalPlanner extends PlanBuilder {
	public static final String CHEST = "minecraft:chests/ruined_portal";
	public static final String MARKER_FRAME = "frame";

	private final VKind kind;
	private final boolean cold;
	private final boolean nether;
	private final boolean ocean;
	private final double moss;
	private final boolean vines;
	private final boolean overgrown;
	private final int sink;

	private RuinedPortalPlanner(Random random, int variant, VKind kind) {
		super(new VPlan(false), random);
		this.kind = kind;
		this.nether = kind == VKind.RUINED_PORTAL_NETHER;
		this.ocean = kind == VKind.RUINED_PORTAL_OCEAN;
		this.cold = variant == VariantRules.PORTAL_COLD && !nether;
		this.moss = switch (kind) {
			case RUINED_PORTAL_JUNGLE, RUINED_PORTAL_OCEAN -> 0.8;
			case RUINED_PORTAL_SWAMP -> 0.5;
			case RUINED_PORTAL_DESERT, RUINED_PORTAL_NETHER -> 0;
			default -> 0.2;
		};
		this.vines = kind == VKind.RUINED_PORTAL_JUNGLE || kind == VKind.RUINED_PORTAL_SWAMP;
		this.overgrown = kind == VKind.RUINED_PORTAL_JUNGLE;
		this.sink = kind == VKind.RUINED_PORTAL_DESERT ? 1 : 0;
	}

	public static VPlan plan(long seed, int variant, VKind kind) {
		RuinedPortalPlanner b = new RuinedPortalPlanner(new Random(seed * 1777L + kind.ordinal()), variant, kind);
		b.build();
		if (b.ocean) {
			b.p.setWaterLevel(40);
		}
		return b.p;
	}

	private String stone() {
		if (nether) {
			double d = r.nextDouble();
			return d < 0.45 ? "minecraft:polished_blackstone_bricks" : d < 0.75 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:blackstone";
		}
		if (chance(moss)) {
			return chance(0.6) ? "minecraft:mossy_stone_bricks" : "minecraft:mossy_cobblestone";
		}
		return chance(0.7) ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks";
	}

	private String stairs() {
		return nether ? "minecraft:polished_blackstone_brick_stairs" : chance(moss) ? "minecraft:mossy_stone_brick_stairs" : "minecraft:stone_brick_stairs";
	}

	private String slab() {
		return nether ? "minecraft:polished_blackstone_brick_slab" : chance(moss) ? "minecraft:mossy_stone_brick_slab" : "minecraft:stone_brick_slab";
	}

	private String chiseled() {
		return nether ? "minecraft:chiseled_polished_blackstone" : "minecraft:chiseled_stone_bricks";
	}

	private String obsidian() {
		return chance(0.2) ? "minecraft:crying_obsidian" : "minecraft:obsidian";
	}

	private void build() {
		boolean giant = chance(0.12);
		boolean fallen = !giant && chance(0.2);
		int wi = giant ? 4 : between(2, 3);
		int hi = giant ? 6 : between(3, 4);
		int w = wi + 2;
		int h = hi + 2;
		int x0 = -w / 2;
		int half = w / 2 + 3;
		spread(half + 4);
		dais(x0, w, half);
		if (fallen) {
			fallenFrame(x0, w, h);
		} else {
			frame(x0, w, h);
		}
		obelisks(half);
		rubble(half, x0, w);
		int chestX = half - 1;
		p.loot(chestX, 0, -2, s("chest", "facing=" + (chance(0.5) ? N : W)), CHEST);
		if (chance(0.5)) {
			set(chestX, 0, -3, s(slab()));
		}
		if (vines || overgrown) {
			greenery(half);
		}
		if (cold) {
			snow(half + 3);
		}
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, -half - 4);
	}

	/** Chao em volta vira netherrack, com magma e (se nao for frio/oceano/selva) uma poca de lava. */
	private void spread(int rad) {
		for (int x = -rad; x <= rad; x++) {
			for (int z = -rad; z <= rad; z++) {
				double d = Math.sqrt(x * x + z * z * 1.6) / rad;
				if (d > 1 || r.nextDouble() > 1.1 - d) {
					continue;
				}
				String b = "minecraft:netherrack";
				if (!cold && chance(0.12)) {
					b = "minecraft:magma_block";
				}
				if (kind == VKind.RUINED_PORTAL_DESERT && chance(0.4)) {
					b = "minecraft:sand";
				}
				set(x, -1, z, b);
			}
		}
		if (!cold && !ocean && !overgrown && !vines) {
			int lx = pick(-1, 1) * (rad - 3);
			int lz = between(1, 3);
			for (int dx = 0; dx <= 1; dx++) {
				set(lx + dx, -1, lz, "minecraft:lava");
				set(lx + dx, -2, lz, "minecraft:netherrack");
				set(lx + dx, -1, lz + 1, "minecraft:magma_block");
			}
		}
	}

	private void dais(int x0, int w, int half) {
		for (int x = -half; x <= half; x++) {
			for (int z = -2; z <= 2; z++) {
				if (chance(0.85)) {
					set(x, -1, z, stone());
				}
			}
		}
		for (int x = x0 - 1; x <= x0 + w; x++) {
			for (int z = -1; z <= 1; z++) {
				boolean edge = z != 0 || x == x0 - 1 || x == x0 + w;
				if (sink > 0) {
					continue;
				}
				if (edge && chance(0.2)) {
					continue;
				}
				set(x, 0, z, edge ? (z != 0 ? s(stairs(), "facing=" + (z < 0 ? S : N)) : s(slab())) : stone());
			}
		}
	}

	private void frame(int x0, int w, int h) {
		int yb = (sink > 0 ? -sink : 1);
		for (int dx = 0; dx < w; dx++) {
			for (int dy = 0; dy < h; dy++) {
				boolean border = dx == 0 || dx == w - 1 || dy == 0 || dy == h - 1;
				int y = yb + dy;
				if (!border) {
					if (y < 0) {
						set(x0 + dx, y, 0, "minecraft:sand");
					}
					continue;
				}
				boolean corner = (dx == 0 || dx == w - 1) && dy == h - 1;
				double missing = corner ? 0.4 : dy >= h - 2 ? 0.15 : 0.05;
				if (dy > 0 && chance(missing)) {
					continue;
				}
				set(x0 + dx, y, 0, obsidian());
			}
		}
		p.mark(MARKER_FRAME, x0, yb, 0);
		if (sink > 0) {
			// areia acumulada dos lados
			for (int x = x0 - 2; x <= x0 + w + 1; x++) {
				for (int z = -2; z <= 2; z++) {
					if (p.get(x, 0, z) == null && chance(Math.abs(z) <= 1 ? 0.6 : 0.3)) {
						set(x, 0, z, "minecraft:sand");
					}
				}
			}
		}
	}

	/** Moldura caida deitada no chao (para tras). */
	private void fallenFrame(int x0, int w, int h) {
		for (int dx = 0; dx < w; dx++) {
			for (int dy = 0; dy < h; dy++) {
				boolean border = dx == 0 || dx == w - 1 || dy == 0 || dy == h - 1;
				if (border && !chance(0.15)) {
					set(x0 + dx, 0, 2 + dy, obsidian());
				}
			}
		}
		p.mark(MARKER_FRAME, x0, 0, 2);
		// a base continua de pe
		for (int dx = 0; dx < w; dx++) {
			if (chance(0.7)) {
				set(x0 + dx, 1, 0, obsidian());
			}
		}
	}

	private void obelisks(int half) {
		for (int side : new int[] {-1, 1}) {
			int x = side * half;
			int h = chance(0.6) ? 5 : between(2, 3);
			set(x, 0, 0, chiseled());
			for (int y = 1; y < h; y++) {
				set(x, y, 0, stone());
			}
			if (h == 5) {
				set(x, 5, 0, chiseled());
				if (!ocean) {
					set(x, 6, 0, s(nether ? "soul_lantern" : "lantern"));
				}
			} else {
				// pedacos caidos
				for (int i = 1; i <= 5 - h; i++) {
					p.set(x - side * i, 0, 1 + i % 2, stone(), VPlan.LOOSE);
				}
			}
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				if (chance(0.7) && p.get(x + d[0], 0, d[1]) == null) {
					String face = d[0] == 1 ? W : d[0] == -1 ? E : d[1] == 1 ? N : S;
					set(x + d[0], 0, d[1], s(stairs(), "facing=" + face));
				}
			}
		}
	}

	private void rubble(int half, int x0, int w) {
		int golds = between(1, 2);
		for (int i = 0; i < 12 + golds; i++) {
			int x = between(-half - 2, half + 2);
			int z = between(-4, 4);
			if (p.get(x, 0, z) != null || (Math.abs(x) <= 1 && z < -1)) {
				continue;
			}
			String b = i < golds ? "minecraft:gold_block" : chance(0.5) ? obsidian() : chance(0.5) ? stone() : s(slab());
			p.set(x, 0, z, b, VPlan.LOOSE);
		}
		if (p.count("gold_block") == 0) {
			set(x0 - 1, 0, 2, "minecraft:gold_block");
		}
		if (!cold && !ocean && !overgrown && !vines && chance(0.5)) {
			set(x0 + w, 1, 1, s("iron_bars"));
			set(x0 + w, 0, 1, stone());
		}
	}

	private void greenery(int half) {
		for (int x = -half - 2; x <= half + 2; x++) {
			for (int y = 1; y <= 9; y++) {
				for (int z = -3; z <= 3; z++) {
					String here = p.get(x, y, z);
					if (here != null) {
						continue;
					}
					String below = p.get(x, y - 1, z);
					if (overgrown && below != null && BlockTraits.sturdy(below) && !VPlan.id(below).endsWith("leaves") && chance(0.45)) {
						set(x, y, z, s("jungle_leaves", "persistent=true"));
					}
				}
			}
		}
		// trepadeiras penduradas na moldura e nos obeliscos
		for (long k : new java.util.ArrayList<>(p.blocks().keySet())) {
			String s = p.blocks().get(k);
			if (s == null || !BlockTraits.sturdy(s)) {
				continue;
			}
			int x = VPlan.keyX(k);
			int y = VPlan.keyY(k);
			int z = VPlan.keyZ(k);
			if (y >= 2 && p.get(x, y, z - 1) == null && chance(0.3)) {
				int len = between(1, 3);
				for (int i = 0; i < len && p.get(x, y - i, z - 1) == null && y - i >= 1; i++) {
					set(x, y - i, z - 1, s("vine", "south=true"));
				}
			}
		}
		for (int i = 0; i < 6; i++) {
			int x = between(-half - 3, half + 3);
			int z = between(-5, 5);
			if (p.get(x, 0, z) == null) {
				set(x, 0, z, overgrown ? "minecraft:fern" : "minecraft:short_grass");
			}
		}
	}

	private void snow(int rad) {
		for (int x = -rad; x <= rad; x++) {
			for (int z = -rad; z <= rad; z++) {
				for (int y = 0; y <= 8; y++) {
					String here = p.get(x, y, z);
					String below = p.get(x, y - 1, z);
					boolean ground = y == 0 && (below == null || !BlockTraits.isAir(below));
					if (here == null && (ground || (below != null && BlockTraits.sturdy(below))) && chance(0.6)) {
						set(x, y, z, s("snow", "layers=1"));
						break;
					}
				}
			}
		}
		set(-rad + 2, -1, 0, "minecraft:packed_ice");
	}
}
