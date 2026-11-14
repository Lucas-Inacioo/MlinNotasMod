package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Praca central por nivel (logica pura). O poco vem da obra paga "Praca do Poco"; aqui a praca ganha, a cada nivel:
 * calcamento ate a borda com bancos e canteiros, chafariz, coreto, estatua (do ultimo chefe derrotado, ou generica) e o
 * monumento com torre do relogio. Cada peca ocupa um quadrante da praca (nordeste = chafariz, sudoeste = coreto,
 * noroeste = estatua, sudeste = monumento), centrada em (+-4, +-4) ou, se o chao la nao esta na altura da praca ou algo
 * atrapalha, em (+-3, +-3); os eixos (as entradas das avenidas) ficam livres.
 *
 * <p>Referencias de forma: chafarizes de praca de vila e coretos vitorianos (Planet Minecraft "village fountain",
 * "bandstand"), torre do relogio de praca de cidade pequena; escrito do zero.
 */
public final class PlazaWorks {
	private PlazaWorks() {
	}

	/** Quadrantes: nordeste, sudoeste, noroeste, sudeste ({x, z}). */
	static final int[][] QUADRANTS = {{1, -1}, {-1, 1}, {-1, -1}, {1, 1}};

	public static Blueprint plan(WorkContext ctx, int stage) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		int base = plan.plazaY();
		if (!ctx.ready(0, 1) && !ctx.ready(1, 0) && !ctx.ready(0, -1) && !ctx.ready(-1, 0)) {
			// a praca fica no anel 1; sem ele pronto nada acontece
			return bp;
		}
		bp.needRing(1);
		paving(ctx, bp, base, stage);
		List<int[]> used = new ArrayList<>();
		if (stage >= 2) {
			place(ctx, bp, used, QUADRANTS[0], 1, (c, sx, sz) -> fountain(ctx, bp, c[0], c[1], base));
		}
		if (stage >= 3) {
			place(ctx, bp, used, QUADRANTS[1], 1, (c, sx, sz) -> bandstand(ctx, bp, c[0], c[1], base));
		}
		if (stage >= 4) {
			place(ctx, bp, used, QUADRANTS[2], 1, (c, sx, sz) -> statue(ctx, bp, c[0], c[1], base, ctx.lastBoss()));
		}
		if (stage >= 5) {
			place(ctx, bp, used, QUADRANTS[3], 1, (c, sx, sz) -> monument(ctx, bp, c[0], c[1], base, sx, sz));
		}
		benchesAndBeds(ctx, bp, base, used);
		return bp;
	}

	interface Feature {
		void build(int[] center, int sx, int sz);
	}

	/** Acha o centro do quadrante (4,4) ou (3,3) onde a peca de raio {@code r} cabe e a constroi. */
	static void place(WorkContext ctx, Blueprint bp, List<int[]> used, int[] q, int r, Feature feature) {
		for (int d : new int[] {4, 3}) {
			int cx = q[0] * d;
			int cz = q[1] * d;
			boolean ok = true;
			for (int x = cx - r; x <= cx + r && ok; x++) {
				for (int z = cz - r; z <= cz + r && ok; z++) {
					ok = ctx.h(x, z) == ctx.plan().plazaY() && ctx.free(x, z) && ctx.plan().code(x, z) != MasterPlan.OUT
							&& ctx.plan().code(x, z) != MasterPlan.LAKE;
				}
			}
			if (ok) {
				used.add(new int[] {cx, cz, r});
				feature.build(new int[] {cx, cz}, q[0], q[1]);
				return;
			}
		}
	}

	/** Calcamento ate a borda da praca (a praca e circular: celulas da propria praca). */
	static void paving(WorkContext ctx, Blueprint bp, int base, int stage) {
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (!plan.plaza(x, z) || (x == 0 && z == 0) || ctx.h(x, z) != base) {
					continue;
				}
				int d2 = x * x + z * z;
				String block;
				if (stage >= 3) {
					// pedra desenhada: anel central claro, quadriculado
					block = d2 <= 6 ? m.accent() : Math.floorMod(x + z, 2) == 0 ? m.bricks() : m.smooth();
				} else {
					int pattern = Math.floorMod(x * 5 + z * 3, 9);
					block = d2 <= 2 ? m.bricks() : pattern == 0 ? "minecraft:mossy_stone_bricks" : pattern < 3 ? m.cobble() : m.bricks();
				}
				bp.surface(x, base, z, block);
			}
		}
	}

	/** Bancos virados para o centro nas 8 bocas das entradas e canteiros de flores nos cantos que sobram. */
	static void benchesAndBeds(WorkContext ctx, Blueprint bp, int base, List<int[]> used) {
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		int[][] benches = {{5, 2, 0}, {5, -2, 0}, {-5, 2, 1}, {-5, -2, 1}, {2, 5, 2}, {-2, 5, 2}, {2, -5, 3}, {-2, -5, 3}};
		String[] faces = {"east", "west", "south", "north"};
		for (int[] b : benches) {
			if (plan.plaza(b[0], b[1]) && ctx.h(b[0], b[1]) == base && ctx.free(b[0], b[1]) && !inside(used, b[0], b[1], 1)) {
				bp.above(b[0], base + 1, b[1], m.stairs(faces[b[2]], false));
			}
		}
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				int d2 = x * x + z * z;
				if (!plan.plaza(x, z) || ctx.h(x, z) != base || Math.abs(x) <= 2 || Math.abs(z) <= 2 || d2 < 31 || !ctx.free(x, z) || inside(used, x, z, 1)) {
					continue;
				}
				bp.surface(x, base, z, m.lawn());
				bp.above(x, base + 1, z, ctx.roll(x, z, 11, 3) == 0 ? "minecraft:azure_bluet" : ctx.roll(x, z, 12, 2) == 0 ? "minecraft:cornflower"
						: "minecraft:poppy");
			}
		}
	}

	static boolean inside(List<int[]> used, int x, int z, int grow) {
		for (int[] u : used) {
			if (Math.abs(x - u[0]) <= u[2] + grow && Math.abs(z - u[1]) <= u[2] + grow) {
				return true;
			}
		}
		return false;
	}

	/** Chafariz: bacia 3x3 de tijolo com agua e coluna central com lanterna. */
	static void fountain(WorkContext ctx, Blueprint bp, int cx, int cz, int base) {
		Mats m = ctx.mats();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (dx == 0 && dz == 0) {
					bp.surface(x, base, z, "minecraft:water");
					bp.above(x, base + 1, z, m.wall());
					bp.above(x, base + 2, z, m.wall());
					bp.above(x, base + 3, z, Mats.lantern(false));
				} else {
					bp.surface(x, base, z, m.bricks());
					bp.above(x, base + 1, z, m.brickSlab());
				}
			}
		}
	}

	/** Coreto: piso de tabuas 3x3, quatro colunas de tronco, telhado de lajes 5x5 e lanterna pendurada. */
	static void bandstand(WorkContext ctx, Blueprint bp, int cx, int cz, int base) {
		Mats m = ctx.mats();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				bp.surface(cx + dx, base, cz + dz, m.planks());
			}
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				for (int y = 1; y <= 3; y++) {
					bp.above(cx + sx, base + y, cz + sz, m.log());
				}
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				bp.above(cx + dx, base + 4, cz + dz, edge ? m.slabBottom() : m.planks());
			}
		}
		bp.above(cx, base + 5, cz, m.slabBottom());
		bp.above(cx, base + 3, cz, Mats.lantern(true));
	}

	/** Estatua sobre pedestal: corpo, bracos e cabeca na paleta do chefe (ou de pedra). */
	static void statue(WorkContext ctx, Blueprint bp, int cx, int cz, int base, String boss) {
		Mats m = ctx.mats();
		String[] pal = statuePalette(boss);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				bp.surface(cx + dx, base, cz + dz, m.bricks());
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (Math.abs(dx) + Math.abs(dz) <= 1) {
					bp.above(cx + dx, base + 1, cz + dz, m.bricks());
				}
			}
		}
		bp.above(cx, base + 1, cz, m.accent());
		bp.above(cx, base + 2, cz, pal[0]);
		bp.above(cx, base + 3, cz, pal[0]);
		bp.above(cx, base + 4, cz, pal[0]);
		bp.above(cx - 1, base + 4, cz, pal[1]);
		bp.above(cx + 1, base + 4, cz, pal[1]);
		bp.above(cx - 1, base + 5, cz, pal[2]);
		bp.above(cx, base + 5, cz, pal[3]);
		bp.above(cx, base + 6, cz, pal[3]);
		bp.above(cx, base + 7, cz, pal[4]);
	}

	/** Corpo, bracos, ombros/arma, cabeca e topo da estatua por chefe. */
	static String[] statuePalette(String boss) {
		return switch (boss == null ? "" : boss) {
			case "cobalt_golem" -> new String[] {"mlinnotas:cobalt_block", "mlinnotas:cobalt_block", "minecraft:iron_bars", "mlinnotas:cobalt_block",
					"minecraft:lightning_rod"};
			case "abyssal_guardian" -> new String[] {"minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:prismarine_wall",
					"minecraft:sea_lantern", "minecraft:lightning_rod"};
			case "igneous_colossus" -> new String[] {"minecraft:blackstone", "minecraft:gilded_blackstone", "minecraft:blackstone_wall", "minecraft:magma_block",
					"minecraft:lightning_rod"};
			case "astral_herald" -> new String[] {"minecraft:purpur_block", "minecraft:purpur_pillar", "minecraft:end_rod", "minecraft:amethyst_block",
					"minecraft:end_rod"};
			case "ender_dragon" -> new String[] {"minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:purpur_pillar", "minecraft:obsidian",
					"minecraft:end_rod"};
			case "wither" -> new String[] {"minecraft:soul_sand", "minecraft:soul_soil", "minecraft:blackstone_wall", "minecraft:coal_block",
					"minecraft:lightning_rod"};
			default -> new String[] {"minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:iron_bars", "minecraft:chiseled_stone_bricks",
					"minecraft:lightning_rod"};
		};
	}

	/** Monumento: base escalonada e torre de 3x3 com relogio nos quatro lados e telhado em piramide. */
	static void monument(WorkContext ctx, Blueprint bp, int cx, int cz, int base, int sx, int sz) {
		Mats m = ctx.mats();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				bp.surface(cx + dx, base, cz + dz, m.bricks());
			}
		}
		// fuste: tijolo com colunas de tronco nos cantos; no 6o ao 8o andar o anel de fora vira o mostrador claro do relogio
		for (int y = 1; y <= 9; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean ring = dx != 0 || dz != 0;
					boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
					boolean face = ring && y >= 6 && y <= 8;
					String block;
					if (face) {
						// ponteiros: o meio do mostrador e o ponto de cima (12h) em escuro
						boolean middle = !corner && y != 7;
						block = !corner && y == 7 ? "minecraft:gold_block" : middle ? "minecraft:black_concrete" : "minecraft:smooth_quartz";
					} else {
						block = corner ? m.log() : y >= 9 ? m.smooth() : m.bricks();
					}
					bp.above(cx + dx, base + y, cz + dz, block);
				}
			}
		}
		// telhado: duas camadas de degraus e agulha
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					String face = Math.abs(dx) == 2 ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
					if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
						bp.above(cx + dx, base + 10, cz + dz, m.brickSlab());
					} else {
						bp.above(cx + dx, base + 10, cz + dz, m.stoneStairs(face, false));
					}
				} else {
					bp.above(cx + dx, base + 10, cz + dz, m.bricks());
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
					String face = Math.abs(dx) == 1 ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
					bp.above(cx + dx, base + 11, cz + dz, m.stoneStairs(face, false));
				} else {
					bp.above(cx + dx, base + 11, cz + dz, m.bricks());
				}
			}
		}
		bp.above(cx, base + 12, cz, m.bricks());
		bp.above(cx, base + 13, cz, "minecraft:lightning_rod");
	}
}
