package com.mlin.notas.vstructures;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Mina abandonada refeita: rede de tuneis 3x3 a partir de um salao central (camp de mineiros com poco do guincho
 * subindo para a superficie, corrente com barril, bancadas, carrinhos), com escoras de cerca e viga a cada 4 blocos,
 * trilhos com falhas, tochas, teias, pontes de tabua sobre cavernas (com toras ate o chao), carrinhos com bau
 * (loot vanilla), corredores de aranha das cavernas com gerador, cruzamentos com pilares, rampas e pocos de escada de
 * mao para outros niveis, e salas no fim dos tuneis: oficina, toca de aranhas, acampamento de mineiros, pedreira com
 * veios de minerio, deposito; becos sem saida terminam em veio de minerio ou desmoronamento de cascalho. Variantes
 * pelo bioma: carvalho, ardosia (funda), gelo, exuberante, espeleotemas, selva, deserto e mesa (carvalho escuro, ouro).
 * Logica pura; cada trecho e um grupo ({@link VPlan#newGroup}) que a peca pula se encostar em liquido.
 *
 * <p>Referencias (so ideia de forma, camadas e paleta; nada copiado): YUNG's Better Mineshafts (oficinas, adegas,
 * variantes por bioma, depositos de minerio, aberturas na superficie), Dungeons and Taverns Mineshaft Overhaul
 * (tuneis principais e secundarios, desmoronamentos), Moog's Mineshafts Reimagined (salas tematicas) e o
 * Repurposed Structures (geradores por bioma).
 */
public final class MineshaftPlanner extends PlanBuilder {
	static final String LOOT = "minecraft:chests/abandoned_mineshaft";
	static final String WORKSHOP = "mlinnotas:chests/mineshaft_workshop";
	public static final String MARKER_HUB = "hub";
	static final int MAX_CORRIDORS = 28;
	static final int LIMIT = 84;

	private record Palette(String planks, String log, String fence, String slab, String floor, String wall, String[] ores, String spider) {
	}

	private record Exit(int x, int y, int z, String dir, int depth, int parent) {
	}

	private final boolean mesa;
	private final int variant;
	private final Palette pal;
	private final List<int[]> boxes = new ArrayList<>();
	private final Deque<Exit> queue = new ArrayDeque<>();
	private int corridors;

	private MineshaftPlanner(Random random, int variant, boolean mesa) {
		super(new VPlan(true), random);
		this.mesa = mesa;
		this.variant = variant;
		this.pal = palette(variant, mesa);
	}

	private static Palette palette(int variant, boolean mesa) {
		if (mesa) {
			return new Palette("minecraft:dark_oak_planks", "minecraft:dark_oak_log", "minecraft:dark_oak_fence", "minecraft:dark_oak_slab",
					"minecraft:red_sand", "minecraft:terracotta", new String[] {"minecraft:gold_ore", "minecraft:gold_ore", "minecraft:iron_ore",
							"minecraft:redstone_ore"}, "minecraft:cave_spider");
		}
		return switch (variant) {
			case VariantRules.SHAFT_DEEPSLATE -> new Palette("minecraft:spruce_planks", "minecraft:stripped_spruce_log", "minecraft:spruce_fence",
					"minecraft:spruce_slab", "minecraft:cobbled_deepslate", "minecraft:deepslate_bricks", new String[] {"minecraft:deepslate_iron_ore",
							"minecraft:deepslate_redstone_ore", "minecraft:deepslate_lapis_ore", "minecraft:deepslate_gold_ore", "minecraft:deepslate_diamond_ore"},
					"minecraft:cave_spider");
			case VariantRules.SHAFT_ICE -> new Palette("minecraft:spruce_planks", "minecraft:spruce_log", "minecraft:spruce_fence", "minecraft:spruce_slab",
					"minecraft:packed_ice", "minecraft:packed_ice", new String[] {"minecraft:coal_ore", "minecraft:iron_ore", "minecraft:emerald_ore"},
					"minecraft:stray");
			case VariantRules.SHAFT_LUSH -> new Palette("minecraft:oak_planks", "minecraft:oak_log", "minecraft:oak_fence", "minecraft:oak_slab",
					"minecraft:moss_block", "minecraft:mossy_cobblestone", new String[] {"minecraft:copper_ore", "minecraft:clay", "minecraft:iron_ore"},
					"minecraft:cave_spider");
			case VariantRules.SHAFT_DRIPSTONE -> new Palette("minecraft:oak_planks", "minecraft:stripped_oak_log", "minecraft:oak_fence",
					"minecraft:oak_slab", "minecraft:dripstone_block", "minecraft:dripstone_block", new String[] {"minecraft:copper_ore", "minecraft:iron_ore",
							"minecraft:raw_copper_block"}, "minecraft:cave_spider");
			case VariantRules.SHAFT_JUNGLE -> new Palette("minecraft:jungle_planks", "minecraft:jungle_log", "minecraft:jungle_fence",
					"minecraft:jungle_slab", "minecraft:mossy_cobblestone", "minecraft:mossy_cobblestone", new String[] {"minecraft:iron_ore",
							"minecraft:lapis_ore", "minecraft:coal_ore"}, "minecraft:cave_spider");
			case VariantRules.SHAFT_DESERT -> new Palette("minecraft:birch_planks", "minecraft:stripped_birch_log", "minecraft:birch_fence",
					"minecraft:birch_slab", "minecraft:sand", "minecraft:smooth_sandstone", new String[] {"minecraft:gold_ore", "minecraft:coal_ore",
							"minecraft:iron_ore"}, "minecraft:husk");
			default -> new Palette("minecraft:oak_planks", "minecraft:oak_log", "minecraft:oak_fence", "minecraft:oak_slab", "minecraft:gravel",
					"minecraft:cobblestone", new String[] {"minecraft:iron_ore", "minecraft:coal_ore", "minecraft:copper_ore", "minecraft:iron_ore"},
					"minecraft:cave_spider");
		};
	}

	public static VPlan plan(long seed, int variant, boolean mesa) {
		MineshaftPlanner best = null;
		for (int attempt = 0; attempt < 6; attempt++) {
			MineshaftPlanner b = new MineshaftPlanner(new Random(seed * 2654435761L + (mesa ? 7 : 1) + attempt * 977L), variant, mesa);
			b.build();
			if (best == null || b.corridors > best.corridors) {
				best = b;
			}
			if (b.corridors >= 12) {
				break;
			}
		}
		return best.p;
	}

	private String floorBlock() {
		return pal.floor;
	}

	private void build() {
		hub();
		for (String d : new String[] {N, E, S, W}) {
			if (chance(0.9)) {
				queue.add(new Exit(8 * dx(d), 0, 8 * dz(d), d, 0, 0));
			}
		}
		while (!queue.isEmpty() && corridors < MAX_CORRIDORS) {
			Exit e = queue.poll();
			corridor(e);
		}
		p.endGroup();
		decorate();
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, 0);
	}

	// --- ocupacao ---

	private boolean free(int ignore, int x1, int y1, int z1, int x2, int y2, int z2) {
		int ax = Math.min(x1, x2);
		int bx = Math.max(x1, x2);
		int ay = Math.min(y1, y2);
		int by = Math.max(y1, y2);
		int az = Math.min(z1, z2);
		int bz = Math.max(z1, z2);
		if (Math.abs(ax) > LIMIT || Math.abs(bx) > LIMIT || Math.abs(az) > LIMIT || Math.abs(bz) > LIMIT || ay < -24 || by > 26) {
			return false;
		}
		for (int i = 0; i < boxes.size(); i++) {
			int[] b = boxes.get(i);
			if (i != ignore && ax <= b[3] + 1 && bx >= b[0] - 1 && ay <= b[4] + 1 && by >= b[1] - 1 && az <= b[5] + 1 && bz >= b[2] - 1) {
				return false;
			}
		}
		return true;
	}

	private int claim(int x1, int y1, int z1, int x2, int y2, int z2) {
		boxes.add(new int[] {Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)});
		return boxes.size() - 1;
	}

	/** Caixa de um trecho que comeca em (x, z), anda {@code len} na direcao {@code d} com largura +-{@code half}. */
	private int[] span(int x, int z, String d, int len, int half) {
		int ex = x + dx(d) * (len - 1);
		int ez = z + dz(d) * (len - 1);
		int px = dz(d) != 0 ? half : 0;
		int pz = dx(d) != 0 ? half : 0;
		return new int[] {Math.min(x, ex) - px, Math.min(z, ez) - pz, Math.max(x, ex) + px, Math.max(z, ez) + pz};
	}

	// --- salao central ---

	/** Topo do poco do guincho: na mesa a mina fica rasa e o poco abre na superficie; nas outras para 2 blocos abaixo dela. */
	private int shaftTop() {
		return mesa ? 14 : 22;
	}

	private void hub() {
		p.newGroup();
		int h = 7;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z));
				int top = ring >= 7 ? 3 : ring >= 6 ? 4 : 5;
				air(x, 0, z, x, top, z);
				set(x, -1, z, chance(0.7) ? "minecraft:coarse_dirt" : chance(0.5) ? "minecraft:gravel" : "minecraft:dirt");
			}
		}
		claim(-h, -1, -h, h, shaftTop(), h);
		// pilares e vigas do madeiramento
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int y = 0; y <= 4; y++) {
					set(5 * sx, y, 5 * sz, axis(pal.log, "y"));
				}
				set(5 * sx, 3, 4 * sz, wallTorch(sz > 0 ? N : S));
			}
			for (int i = -4; i <= 4; i++) {
				set(5 * sx, 5, i, axis(pal.log, "z"));
				set(i, 5, 5 * sx, axis(pal.log, "x"));
			}
		}
		// poco do guincho subindo para a superficie, com escada de mao e corrente com barril
		air(-1, 6, -1, 1, shaftTop(), 1);
		for (int y = 0; y <= 5; y++) {
			set(0, y, 2, axis(pal.log, "y"));
		}
		for (int y = 0; y <= shaftTop(); y++) {
			set(0, y, 1, ladder(N));
		}
		for (int y = 4; y <= shaftTop(); y++) {
			set(-1, y, -1, s("iron_chain", "axis=y"));
		}
		set(-1, 3, -1, s("barrel", "facing=up"));
		// trilhos cruzando o salao
		for (int i = -7; i <= 7; i++) {
			if (Math.abs(i) > 2) {
				set(i, 0, 0, s("rail", "shape=east_west"));
				set(0, 0, i, s("rail", "shape=north_south"));
			}
		}
		// bancadas, fornalha, bigorna, barris
		set(-6, 0, -6, "minecraft:crafting_table");
		set(-5, 0, -6, s("furnace", "facing=south"));
		set(-6, 0, -5, s("anvil", "facing=east"));
		p.loot(6, 0, -6, s("barrel", "facing=up"), WORKSHOP);
		set(6, 0, -5, s("barrel", "facing=up"));
		set(6, 1, -6, s("barrel", "facing=up"));
		set(-6, 0, 6, s("hay_block", "axis=x"));
		set(6, 0, 6, "minecraft:composter");
		set(-3, 4, -3, s("lantern", "hanging=true"));
		set(-3, 5, -3, axis(pal.log, "x"));
		set(3, 4, 3, s("lantern", "hanging=true"));
		set(3, 5, 3, axis(pal.log, "x"));
		set(4, 0, -2, s("rail", "shape=east_west"));
		p.entity("minecraft:minecart", 4.5, 0.06, -1.5, "");
		set(4, 0, -1, s("rail", "shape=east_west"));
		set(-4, 0, 2, s("rail", "shape=north_south"));
		p.entity("minecraft:chest_minecart", -3.5, 0.06, 2.5, "loot:" + LOOT);
		p.mark(MARKER_HUB, 0, 0, 0);
	}

	// --- tuneis ---

	private void corridor(Exit e) {
		int len = between(10, 26);
		int[] sp = span(e.x(), e.z(), e.dir(), len, 1);
		while (len >= 6 && !free(e.parent(), sp[0], e.y() - 1, sp[1], sp[2], e.y() + 3, sp[3])) {
			len -= 4;
			sp = span(e.x(), e.z(), e.dir(), len, 1);
		}
		if (len < 6) {
			return;
		}
		corridors++;
		p.newGroup();
		int box = claim(sp[0], e.y() - 1, sp[1], sp[2], e.y() + 3, sp[3]);
		String d = e.dir();
		int px = dz(d) != 0 ? 1 : 0;
		int pz = dx(d) != 0 ? 1 : 0;
		String railShape = dx(d) != 0 ? "east_west" : "north_south";
		boolean spiders = chance(0.14);
		int spiderAt = between(3, len - 3);
		spiderAt -= spiderAt % 4; // longe das escoras (o gerador fica no meio e o jogador passa pelos lados)
		if (spiderAt < 4) {
			spiderAt = 4;
		}
		int cartAt = chance(0.45) ? between(1, len - 4) : -1;
		if (cartAt == spiderAt) {
			cartAt = -1;
		}
		int y = e.y();
		for (int i = 0; i < len; i++) {
			int cx = e.x() + dx(d) * i;
			int cz = e.z() + dz(d) * i;
			for (int k = -1; k <= 1; k++) {
				int x = cx + px * k;
				int z = cz + pz * k;
				air(x, y, z, x, y + 2, z);
				p.set(x, y - 1, z, pal.planks, VPlan.IF_AIR);
			}
			boolean support = i % 4 == 2;
			if (support) {
				for (int k : new int[] {-1, 1}) {
					int x = cx + px * k;
					int z = cz + pz * k;
					set(x, y, z, pal.fence);
					set(x, y + 1, z, pal.fence);
					p.set(x, y - 2, z, axis(pal.log, "y"), VPlan.IF_AIR | VPlan.FILL_DOWN);
				}
				for (int k = -1; k <= 1; k++) {
					set(cx + px * k, y + 2, cz + pz * k, pal.planks);
				}
				if (chance(0.3) && i + 1 < len) {
					set(cx + dx(d), y + 2, cz + dz(d), wallTorch(d));
				}
			} else if (chance(0.06)) {
				int k = pick(-1, 1);
				set(cx + px * k, y + 2, cz + pz * k, "minecraft:cobweb");
			}
			if (!support && (i == cartAt || chance(0.72))) {
				set(cx, y, cz, s("rail", "shape=" + railShape));
			}
			if (i == cartAt && !support) {
				p.entity("minecraft:chest_minecart", cx + 0.5, y + 0.06, cz + 0.5, "loot:" + LOOT);
			}
			if (spiders && Math.abs(i - spiderAt) <= 3) {
				for (int k = -1; k <= 1; k++) {
					for (int dy = 0; dy <= 2; dy++) {
						int x = cx + px * k;
						int z = cz + pz * k;
						if (isAirAt(x, y + dy, z) && (k != 0 || dy == 2) && chance(0.55)) {
							set(x, y + dy, z, "minecraft:cobweb");
						}
					}
				}
			}
		}
		if (spiders) {
			int sx = e.x() + dx(d) * spiderAt;
			int sz = e.z() + dz(d) * spiderAt;
			p.spawner(sx, y, sz, pal.spider);
			set(sx, y + 1, sz, "minecraft:cobweb");
		}
		int endX = e.x() + dx(d) * len;
		int endZ = e.z() + dz(d) * len;
		ending(new Exit(endX, y, endZ, d, e.depth() + 1, box));
	}

	/** O que vem no fim de um tunel. */
	private void ending(Exit e) {
		if (e.depth() > 5 || corridors >= MAX_CORRIDORS) {
			deadEnd(e);
			return;
		}
		int roll = r.nextInt(100);
		boolean ok;
		if (roll < 34) {
			ok = crossing(e);
		} else if (roll < 48) {
			ok = ramp(e);
		} else if (roll < 58) {
			ok = shaft(e);
		} else if (roll < 84) {
			ok = room(e);
		} else {
			ok = false;
		}
		if (!ok) {
			deadEnd(e);
		}
	}

	/** Cruzamento 5x5 (alto, com pilares de tora), de onde saem ate 3 tuneis. */
	private boolean crossing(Exit e) {
		String d = e.dir();
		int cx = e.x() + dx(d) * 2;
		int cz = e.z() + dz(d) * 2;
		boolean tall = chance(0.5);
		int top = tall ? 5 : 3;
		if (!free(e.parent(), cx - 2, e.y() - 1, cz - 2, cx + 2, e.y() + top + 1, cz + 2)) {
			return false;
		}
		p.newGroup();
		int box = claim(cx - 2, e.y() - 1, cz - 2, cx + 2, e.y() + top + 1, cz + 2);
		int y = e.y();
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				air(x, y, z, x, y + top - 1, z);
				p.set(x, y - 1, z, pal.planks, VPlan.IF_AIR);
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int yy = y; yy < y + top; yy++) {
					set(cx + 2 * sx, yy, cz + 2 * sz, axis(pal.log, "y"));
				}
				p.set(cx + 2 * sx, y - 2, cz + 2 * sz, axis(pal.log, "y"), VPlan.IF_AIR | VPlan.FILL_DOWN);
			}
		}
		if (tall) {
			// mezanino de tabuas no meio do cruzamento alto
			for (int x = cx - 1; x <= cx + 1; x++) {
				set(x, y + 4, cz - 2, slabTop(pal.slab));
				set(x, y + 4, cz + 2, slabTop(pal.slab));
			}
			set(cx, y + 4, cz, s("lantern", "hanging=true"));
			set(cx, y + 5, cz, axis(pal.log, "x"));
			set(cx - 1, y + 5, cz, axis(pal.log, "x"));
			set(cx + 1, y + 5, cz, axis(pal.log, "x"));
		}
		String left = cw(cw(cw(d)));
		String right = cw(d);
		String[] outs = {d, left, right};
		for (String o : outs) {
			if (chance(o.equals(d) ? 0.75 : 0.6)) {
				queue.add(new Exit(cx + dx(o) * 3, y, cz + dz(o) * 3, o, e.depth(), box));
			}
		}
		return true;
	}

	/** Rampa (sem degraus, como a vanilla): sobe ou desce 5 blocos em 6. */
	private boolean ramp(Exit e) {
		String d = e.dir();
		int sign = e.y() > 6 ? -1 : e.y() < -10 ? 1 : pick(-1, 1);
		int len = 6;
		int[] sp = span(e.x(), e.z(), d, len, 1);
		int y0 = e.y();
		int y1 = e.y() + sign * (len - 1);
		if (!free(e.parent(), sp[0], Math.min(y0, y1) - 1, sp[1], sp[2], Math.max(y0, y1) + 3, sp[3])) {
			return false;
		}
		p.newGroup();
		int box = claim(sp[0], Math.min(y0, y1) - 1, sp[1], sp[2], Math.max(y0, y1) + 3, sp[3]);
		int px = dz(d) != 0 ? 1 : 0;
		int pz = dx(d) != 0 ? 1 : 0;
		for (int i = 0; i < len; i++) {
			int cx = e.x() + dx(d) * i;
			int cz = e.z() + dz(d) * i;
			int yy = y0 + sign * i;
			for (int k = -1; k <= 1; k++) {
				air(cx + px * k, yy, cz + pz * k, cx + px * k, yy + 3, cz + pz * k);
				p.set(cx + px * k, yy - 1, cz + pz * k, pal.planks, VPlan.IF_AIR);
			}
			if (i % 3 == 1) {
				set(cx + px, yy + 2, cz + pz, "minecraft:cobweb");
			}
		}
		queue.add(new Exit(e.x() + dx(d) * len, y1, e.z() + dz(d) * len, d, e.depth(), box));
		return true;
	}

	/** Poco de escada de mao para um nivel 8 blocos abaixo, de onde sai um tunel para o lado. */
	private boolean shaft(Exit e) {
		String d = e.dir();
		int lx = e.x();
		int lz = e.z();
		int bottom = e.y() - 8;
		String side = chance(0.5) ? cw(d) : cw(cw(cw(d)));
		if (!free(e.parent(), lx - 1, bottom - 1, lz - 1, lx + 1, e.y() + 2, lz + 1) || bottom < -20) {
			return false;
		}
		p.newGroup();
		int box = claim(lx, bottom - 1, lz, lx, e.y() + 2, lz);
		for (int y = bottom; y <= e.y(); y++) {
			set(lx, y, lz, ladder(opposite(d)));
		}
		air(lx, e.y() + 1, lz, lx, e.y() + 2, lz);
		set(lx, e.y() + 2, lz, s("lantern", "hanging=true"));
		// passagem curta para o lado no fundo
		int gx = lx + dx(side);
		int gz = lz + dz(side);
		air(gx, bottom, gz, gx, bottom + 2, gz);
		queue.add(new Exit(lx + dx(side) * 2, bottom, lz + dz(side) * 2, side, e.depth(), box));
		return true;
	}

	/** Sala no fim do tunel (oficina, toca de aranha, acampamento, pedreira ou deposito). */
	private boolean room(Exit e) {
		String d = e.dir();
		int w = 3;
		int depth = 8;
		int h = 4;
		int px = dz(d) != 0 ? 1 : 0;
		int pz = dx(d) != 0 ? 1 : 0;
		int[] sp = span(e.x(), e.z(), d, depth, w);
		int y = e.y();
		if (!free(e.parent(), sp[0], y - 1, sp[1], sp[2], y + h, sp[3])) {
			return false;
		}
		p.newGroup();
		int box = claim(sp[0], y - 1, sp[1], sp[2], y + h, sp[3]);
		for (int i = 0; i < depth; i++) {
			for (int k = -w; k <= w; k++) {
				int x = e.x() + dx(d) * i + px * k;
				int z = e.z() + dz(d) * i + pz * k;
				int top = (Math.abs(k) == w || i == 0 || i == depth - 1) ? h - 2 : h - 1;
				air(x, y, z, x, y + top, z);
				p.set(x, y - 1, z, i % 2 == 0 ? pal.planks : floorBlock(), VPlan.IF_AIR);
			}
		}
		// escoras nos cantos
		for (int i : new int[] {1, depth - 2}) {
			for (int k : new int[] {-w + 1, w - 1}) {
				int x = e.x() + dx(d) * i + px * k;
				int z = e.z() + dz(d) * i + pz * k;
				for (int yy = y; yy < y + h; yy++) {
					set(x, yy, z, axis(pal.log, "y"));
				}
			}
		}
		int mx = e.x() + dx(d) * (depth / 2);
		int mz = e.z() + dz(d) * (depth / 2);
		int back = depth - 1;
		int bx = e.x() + dx(d) * back;
		int bz = e.z() + dz(d) * back;
		String toEntrance = opposite(d);
		int kind = r.nextInt(5);
		switch (kind) {
			case 0 -> { // oficina
				set(bx + px * -2, y, bz + pz * -2, "minecraft:crafting_table");
				set(bx + px * -1, y, bz + pz * -1, s("furnace", "facing=" + toEntrance));
				set(bx + px, y, bz + pz, s("anvil", "facing=" + cw(d)));
				p.loot(bx + px * 2, y, bz + pz * 2, s("barrel", "facing=" + toEntrance), WORKSHOP);
				set(bx, y, bz, s("smithing_table"));
				set(mx, y + 3, mz, s("lantern", "hanging=true"));
				set(mx, y + 4, mz, axis(pal.log, "y"));
			}
			case 1 -> { // toca de aranha
				p.spawner(mx, y, mz, pal.spider);
				for (int i = 1; i < depth - 1; i++) {
					for (int k = -w + 1; k <= w - 1; k++) {
						for (int dy = 0; dy <= 2; dy++) {
							int x = e.x() + dx(d) * i + px * k;
							int z = e.z() + dz(d) * i + pz * k;
							if (isAirAt(x, y + dy, z) && chance(dy == 0 ? 0.25 : 0.5)) {
								set(x, y + dy, z, "minecraft:cobweb");
							}
						}
					}
				}
				p.loot(bx + px * 2, y, bz + pz * 2, s("chest", "facing=" + toEntrance), LOOT);
				set(bx + px * 2, y + 1, bz + pz * 2, "minecraft:cobweb");
			}
			case 2 -> { // acampamento de mineiros
				String head = d;
				set(bx + px * -2, y, bz + pz * -2, s("brown_bed", "facing=" + head, "part=head"));
				set(bx + px * -2 - dx(d), y, bz + pz * -2 - dz(d), s("brown_bed", "facing=" + head, "part=foot"));
				set(bx + px * 2, y, bz + pz * 2, s("brown_bed", "facing=" + head, "part=head"));
				set(bx + px * 2 - dx(d), y, bz + pz * 2 - dz(d), s("brown_bed", "facing=" + head, "part=foot"));
				set(mx, y, mz, s("campfire", "lit=false"));
				p.loot(bx, y, bz, s("chest", "facing=" + toEntrance), WORKSHOP);
				set(mx + px * 2, y, mz + pz * 2, s("barrel", "facing=up"));
				set(mx - px * 2, y, mz - pz * 2, "minecraft:crafting_table");
				set(mx, y + 3, mz, s("lantern", "hanging=true"));
				set(mx, y + 4, mz, axis(pal.log, "y"));
			}
			case 3 -> { // pedreira: veios nas paredes e carrinho cheio
				for (int i = 0; i < depth; i++) {
					for (int k : new int[] {-w - 1, w + 1}) {
						for (int dy = 0; dy <= 2; dy++) {
							int ox = e.x() + dx(d) * i + px * k;
							int oz = e.z() + dz(d) * i + pz * k;
							if (chance(0.45) && p.get(ox, y + dy, oz) == null) {
								p.set(ox, y + dy, oz, pick(pal.ores), VPlan.IF_SOLID);
							}
						}
					}
				}
				for (int i = 1; i < depth - 1; i++) {
					set(e.x() + dx(d) * i, y, e.z() + dz(d) * i, s("rail", "shape=" + (dx(d) != 0 ? "east_west" : "north_south")));
				}
				p.entity("minecraft:chest_minecart", mx + 0.5, y + 0.06, mz + 0.5, "loot:" + LOOT);
				set(bx + px * 2, y, bz + pz * 2, "minecraft:gravel");
				set(bx + px * 2, y + 1, bz + pz * 2, "minecraft:gravel");
				set(bx + px, y, bz + pz, "minecraft:gravel");
				set(bx - px * 2, y, bz - pz * 2, s("pointed_dripstone", "vertical_direction=up", "thickness=tip"));
			}
			default -> { // deposito
				for (int k = -w + 1; k <= w - 1; k++) {
					if (k != 0) {
						set(bx + px * k, y, bz + pz * k, s("barrel", "facing=up"));
						if (chance(0.5)) {
							set(bx + px * k, y + 1, bz + pz * k, s("barrel", "facing=up"));
						}
					}
				}
				p.loot(bx, y, bz, s("barrel", "facing=" + toEntrance), WORKSHOP);
				set(mx + px * -2, y, mz + pz * -2, s("hay_block", "axis=y"));
				set(mx + px * 2, y, mz + pz * 2, "minecraft:tnt");
				set(mx, y + 3, mz, s("lantern", "hanging=true"));
				set(mx, y + 4, mz, axis(pal.log, "y"));
			}
		}
		// tunel lateral saindo da sala as vezes
		if (chance(0.5)) {
			String side = chance(0.5) ? cw(d) : cw(cw(cw(d)));
			queue.add(new Exit(mx + dx(side) * (w + 1), y, mz + dz(side) * (w + 1), side, e.depth(), box));
		}
		return true;
	}

	/** Beco: veio de minerio na parede do fundo ou desmoronamento de cascalho. */
	private void deadEnd(Exit e) {
		String d = e.dir();
		int px = dz(d) != 0 ? 1 : 0;
		int pz = dx(d) != 0 ? 1 : 0;
		p.newGroup();
		if (chance(0.5)) {
			for (int k = -2; k <= 2; k++) {
				for (int dy = -1; dy <= 3; dy++) {
					for (int i = 0; i <= 1; i++) {
						int ox = e.x() + dx(d) * i + px * k;
						int oz = e.z() + dz(d) * i + pz * k;
						if (chance(0.5) && p.get(ox, e.y() + dy, oz) == null) {
							p.set(ox, e.y() + dy, oz, pick(pal.ores), VPlan.IF_SOLID);
						}
					}
				}
			}
		} else {
			// desmoronamento: rampa de cascalho entrando no fim do tunel
			for (int i = 1; i <= 3; i++) {
				int cx = e.x() - dx(d) * i;
				int cz = e.z() - dz(d) * i;
				for (int k = -1; k <= 1; k++) {
					int hgt = 3 - i + (chance(0.5) ? 1 : 0);
					for (int dy = 0; dy < Math.min(hgt, 3); dy++) {
						set(cx + px * k, e.y() + dy, cz + pz * k, chance(0.8) ? "minecraft:gravel" : "minecraft:cobblestone");
					}
				}
			}
		}
	}

	// --- variantes ---

	/** Enfeites pelo bioma nas superficies dos tuneis (musgo, neve, espeleotemas, trepadeiras, areia...). */
	private void decorate() {
		List<long[]> cells = new ArrayList<>();
		for (var entry : p.blocks().entrySet()) {
			if (BlockTraits.isAir(entry.getValue())) {
				long k = entry.getKey();
				cells.add(new long[] {VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k)});
			}
		}
		for (long[] c : cells) {
			int x = (int) c[0];
			int y = (int) c[1];
			int z = (int) c[2];
			if (!isAirAt(x, y, z)) {
				continue;
			}
			boolean floor = p.get(x, y - 1, z) == null || (BlockTraits.sturdy(p.get(x, y - 1, z)) && !BlockTraits.isAir(p.get(x, y - 1, z)));
			boolean ceiling = p.get(x, y + 1, z) == null;
			if (ceiling) {
				if (chance(0.12)) {
					p.set(x, y + 1, z, pal.wall, VPlan.IF_SOLID);
				}
			}
			switch (mesa ? -1 : variant) {
				case VariantRules.SHAFT_LUSH -> {
					if (floor && p.get(x, y - 1, z) == null && chance(0.25)) {
						set(x, y, z, "minecraft:moss_carpet");
					} else if (ceiling && chance(0.06)) {
						set(x, y, z, s("cave_vines", "berries=" + chance(0.5), "age=25"));
					}
				}
				case VariantRules.SHAFT_ICE -> {
					if (floor && p.get(x, y - 1, z) == null && chance(0.3)) {
						set(x, y, z, s("snow", "layers=" + between(1, 2)));
					}
				}
				case VariantRules.SHAFT_DRIPSTONE -> {
					if (ceiling && chance(0.07)) {
						set(x, y, z, s("pointed_dripstone", "vertical_direction=down", "thickness=tip"));
					}
				}
				case VariantRules.SHAFT_JUNGLE -> {
					if (ceiling && chance(0.05)) {
						set(x, y, z, s("glow_lichen", "up=true"));
					}
				}
				case VariantRules.SHAFT_DESERT -> {
					if (floor && p.get(x, y - 1, z) == null && chance(0.15)) {
						p.set(x, y - 1, z, "minecraft:sand", VPlan.IF_SOLID);
					}
				}
				default -> {
				}
			}
		}
	}
}
