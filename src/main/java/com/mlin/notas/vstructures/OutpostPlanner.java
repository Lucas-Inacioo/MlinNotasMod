package com.mlin.notas.vstructures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Posto avancado de saqueadores refeito: acampamento de guerra murado (paliçada de troncos de 47x47 sobre base de
 * pedra, portao com duas torres e passarela, 4 torres de vigia nos cantos) em volta da torre vanilla aumentada (11x11,
 * 5 andares: entrada, arsenal, alojamento, sala do capitao e deposito; terraco com o bau vanilla, 8 estandartes de
 * saqueador, fogueira de sinal e telhado de quatro aguas). Dentro do muro: jaula do golem de ferro (golem de neve na
 * neve), jaula das allays, barracas de la, alvos de treino, pilha de toras, carroca de suprimentos e roda de fogueira,
 * sorteados em lugares fixos. Saqueadores, 2 vindicadores e o capitao com estandarte ja nascem com a estrutura. Paleta
 * pelo bioma ({@link VariantRules}). Portao na frente (-z). Logica pura.
 *
 * <p>Referencias (so ideia de forma, camadas e paleta; nada copiado): Toni's Better Pillager Outposts (paliçada,
 * torres octogonais, portao, a torre como fortaleza), Improved Pillager Outpost (torre de varios andares, fogueira de
 * sinal), Better Pillager Outpost (capitao no topo, vindicadores) e as variantes por bioma do Repurposed Structures.
 */
public final class OutpostPlanner extends PlanBuilder {
	static final String TOP_CHEST = "minecraft:chests/pillager_outpost";
	static final String ARMORY = "mlinnotas:chests/outpost_armory";
	static final String SUPPLIES = "mlinnotas:chests/outpost_supplies";
	public static final String MARKER_TOP_CHEST = "top_chest";
	public static final String MARKER_GOLEM_CAGE = "golem_cage";
	public static final int HALF = 23;
	static final int DECK = 20;

	/** Paleta da variante. */
	private record Palette(String post, String stripped, String wall, String floor, String stairs, String slab, String fence, String trapdoor,
			String base, String baseAlt, String soil, String plant, boolean snow) {
	}

	private final Palette pal;
	private final int variant;

	private OutpostPlanner(Random random, int variant) {
		super(new VPlan(false), random);
		this.variant = variant;
		this.pal = palette(variant);
	}

	private static Palette palette(int variant) {
		return switch (variant) {
			case VariantRules.OUTPOST_TAIGA -> new Palette("minecraft:spruce_log", "minecraft:stripped_spruce_log", "minecraft:spruce_planks",
					"minecraft:dark_oak_planks", "minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:spruce_fence", "minecraft:spruce_trapdoor",
					"minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:podzol", "minecraft:fern", false);
			case VariantRules.OUTPOST_DESERT -> new Palette("minecraft:dark_oak_log", "minecraft:stripped_dark_oak_log", "minecraft:birch_planks",
					"minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:dark_oak_fence",
					"minecraft:dark_oak_trapdoor", "minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:sand", "minecraft:dead_bush", false);
			case VariantRules.OUTPOST_SNOWY -> new Palette("minecraft:spruce_log", "minecraft:stripped_spruce_log", "minecraft:birch_planks",
					"minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:spruce_fence", "minecraft:spruce_trapdoor",
					"minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:snow_block", "minecraft:snow", true);
			case VariantRules.OUTPOST_SAVANNA -> new Palette("minecraft:acacia_log", "minecraft:stripped_acacia_log", "minecraft:birch_planks",
					"minecraft:acacia_planks", "minecraft:acacia_stairs", "minecraft:acacia_slab", "minecraft:acacia_fence", "minecraft:acacia_trapdoor",
					"minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:grass_block", "minecraft:short_grass", false);
			case VariantRules.OUTPOST_CHERRY -> new Palette("minecraft:cherry_log", "minecraft:stripped_cherry_log", "minecraft:birch_planks",
					"minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:cherry_fence", "minecraft:cherry_trapdoor",
					"minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:grass_block", "minecraft:pink_petals", false);
			default -> new Palette("minecraft:dark_oak_log", "minecraft:stripped_dark_oak_log", "minecraft:birch_planks", "minecraft:dark_oak_planks",
					"minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:dark_oak_fence", "minecraft:dark_oak_trapdoor",
					"minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:grass_block", "minecraft:short_grass", false);
		};
	}

	public static VPlan plan(long seed, int variant) {
		OutpostPlanner b = new OutpostPlanner(new Random(seed * 6151L + 17), variant);
		b.build();
		return b.p;
	}

	private String base() {
		return chance(0.3) ? pal.baseAlt : pal.base;
	}

	private String log(String axis) {
		return axis(pal.post, axis);
	}

	private void build() {
		ground();
		palisade();
		gate();
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				cornerTower(19 * sx, 19 * sz);
			}
		}
		keep();
		features();
		paths();
		mobs();
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, -HALF - 4);
	}

	/** Chao plano dentro do muro (corta morro, aterra buraco) com algumas plantas. */
	private void ground() {
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				String soil = pal.soil;
				p.set(x, -1, z, soil, VPlan.FILL_SOIL);
				for (int y = 0; y <= 12; y++) {
					p.setIfAbsent(x, y, z, VPlan.AIR);
				}
			}
		}
	}

	private void plants() {
		for (int x = -HALF + 1; x < HALF; x++) {
			for (int z = -HALF + 1; z < HALF; z++) {
				if (!isAirAt(x, 0, z) || !VPlan.id(p.get(x, -1, z)).equals(VPlan.id(pal.soil)) || !chance(pal.snow ? 0.35 : 0.08)) {
					continue;
				}
				set(x, 0, z, pal.snow ? s("snow", "layers=1") : pal.plant);
			}
		}
	}

	/** Paliçada: troncos em pe sobre base de pedra, ponta de cerca em troncos alternados. */
	private void palisade() {
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				if (Math.abs(x) != HALF && Math.abs(z) != HALF) {
					continue;
				}
				p.set(x, -1, z, pal.base, VPlan.FILL_DOWN);
				set(x, 0, z, base());
				int h = chance(0.08) ? 2 : 4;
				for (int y = 1; y <= h; y++) {
					set(x, y, z, log("y"));
				}
				if (h == 4 && (x + z) % 2 == 0) {
					set(x, 5, z, pal.fence);
				}
				if (h == 4 && (x + z) % 6 == 0 && Math.abs(x) != Math.abs(z)) {
					// estandarte para fora
					String out = Math.abs(z) == HALF ? (z < 0 ? N : S) : (x < 0 ? W : E);
					int ox = x + dx(out);
					int oz = z + dz(out);
					if (p.get(ox, 3, oz) == null) {
						set(ox, 3, oz, s("white_wall_banner", "facing=" + out));
						p.tag(ox, 3, oz, "ominous");
					}
				}
			}
		}
		// passarela de tabuas por dentro, apoiada em escoras
		for (int x = -HALF + 1; x <= HALF - 1; x++) {
			for (int z = -HALF + 1; z <= HALF - 1; z++) {
				boolean ring = Math.abs(x) == HALF - 1 || Math.abs(z) == HALF - 1;
				if (!ring || (Math.abs(x) >= 17 && Math.abs(z) >= 17) || (z < 0 && Math.abs(x) <= 8)) {
					continue;
				}
				set(x, 3, z, slabTop(pal.slab));
				if ((x + z) % 5 == 0) {
					set(x, 2, z, pal.fence);
					set(x, 1, z, pal.fence);
					set(x, 0, z, pal.fence);
				}
			}
		}
	}

	/** Portao com duas torrezinhas e passarela por cima. */
	private void gate() {
		for (int sx = -1; sx <= 1; sx += 2) {
			int cx = 5 * sx;
			for (int x = cx - 2; x <= cx + 2; x++) {
				for (int z = -HALF - 2; z <= -HALF + 2; z++) {
					p.set(x, -1, z, pal.base, VPlan.FILL_DOWN);
					boolean corner = Math.abs(x - cx) == 2 && Math.abs(z + HALF) == 2;
					for (int y = 0; y <= 6; y++) {
						set(x, y, z, y <= 1 ? base() : corner ? log("y") : pal.wall);
					}
					set(x, 7, z, pal.floor);
					boolean edge = Math.abs(x - cx) == 2 || Math.abs(z + HALF) == 2;
					if (edge) {
						set(x, 8, z, corner ? log("y") : pal.fence);
					}
					if (corner) {
						set(x, 9, z, log("y"));
					}
				}
			}
			for (int x = cx - 2; x <= cx + 2; x++) {
				for (int z = -HALF - 2; z <= -HALF + 2; z++) {
					set(x, 10, z, pal.slab);
				}
			}
			fill(cx - 1, 10, -HALF - 1, cx + 1, 10, -HALF + 1, pal.floor);
			set(cx, 11, -HALF, pal.slab);
			// seteiras e estandarte de saqueador na frente
			set(cx, 4, -HALF - 2, VPlan.AIR);
			set(cx, 5, -HALF - 3, s("white_wall_banner", "facing=north"));
			p.tag(cx, 5, -HALF - 3, "ominous");
			// escada de mao por dentro do acampamento
			for (int y = 0; y <= 7; y++) {
				set(cx, y, -HALF + 3, ladder(S));
			}
			set(cx, 8, -HALF + 2, VPlan.AIR);
		}
		air(-2, 0, -HALF - 1, 2, 4, -HALF + 1);
		for (int x = -2; x <= 2; x++) {
			for (int z = -HALF - 1; z <= -HALF + 1; z++) {
				set(x, 5, z, pal.floor);
			}
			set(x, 6, -HALF - 1, pal.fence);
			set(x, 6, -HALF + 1, pal.fence);
			set(x, -1, -HALF, "minecraft:dirt_path");
		}
		set(-2, 4, -HALF - 1, stairsTop(pal.stairs, E));
		set(2, 4, -HALF - 1, stairsTop(pal.stairs, W));
		for (int x = -4; x <= 4; x++) {
			for (int z = -HALF - 6; z <= -HALF - 3; z++) {
				for (int y = 0; y <= 4; y++) {
					p.setIfAbsent(x, y, z, VPlan.AIR);
				}
			}
		}
	}

	/** Torre de vigia de canto: 5x5 em toras, plataforma com parapeito e telhadinho. */
	private void cornerTower(int cx, int cz) {
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
				boolean edge = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
				if (corner) {
					p.set(x, -1, z, pal.base, VPlan.FILL_DOWN);
					set(x, 0, z, base());
					for (int y = 1; y <= 10; y++) {
						set(x, y, z, log("y"));
					}
				}
				set(x, 6, z, pal.floor);
				if (edge && !corner) {
					set(x, 7, z, pal.fence);
					set(x, 5, z, stairsTop(pal.stairs, Math.abs(x - cx) == 2 ? (x > cx ? W : E) : (z > cz ? N : S)));
				}
				set(x, 11, z, pal.slab);
			}
		}
		fill(cx - 1, 11, cz - 1, cx + 1, 11, cz + 1, pal.floor);
		set(cx, 12, cz, pal.slab);
		// escora central e escada de mao virada para o meio do acampamento
		int ix = cx > 0 ? -1 : 1;
		for (int y = 0; y <= 5; y++) {
			set(cx, y, cz, log("y"));
		}
		p.set(cx, -1, cz, pal.base, VPlan.FILL_DOWN);
		for (int y = 0; y <= 6; y++) {
			set(cx + ix, y, cz, ladder(ix > 0 ? E : W));
		}
		set(cx + ix, 6, cz, ladder(ix > 0 ? E : W));
		if (chance(0.5)) {
			p.loot(cx - ix, 7, cz + Integer.signum(cz), s("barrel", "facing=up"), SUPPLIES);
		}
		set(cx, 7, cz - Integer.signum(cz), s("campfire", "lit=false"));
	}

	/** A torre: 11x11, 4 andares + terraco com o bau vanilla e telhado de quatro aguas. */
	private void keep() {
		int h = 5;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				p.set(x, -1, z, pal.base, VPlan.FILL_DOWN);
				boolean edge = Math.abs(x) == h || Math.abs(z) == h;
				boolean corner = Math.abs(x) == h && Math.abs(z) == h;
				boolean mid = x == 0 || z == 0;
				for (int y = 0; y < DECK; y++) {
					if (!edge) {
						set(x, y, z, VPlan.AIR);
						continue;
					}
					String block;
					if (y <= 2) {
						block = corner ? pal.base : base();
					} else if (corner || mid) {
						block = log("y");
					} else if (y % 4 == 0) {
						block = axis(pal.stripped, Math.abs(z) == h ? "x" : "z");
					} else {
						block = pal.wall;
					}
					set(x, y, z, block);
				}
				if (!edge) {
					set(x, -1, z, pal.base);
					for (int f = 4; f < DECK; f += 4) {
						set(x, f, z, pal.floor);
					}
				}
			}
		}
		// janelas com venezianas
		for (int f = 0; f < DECK; f += 4) {
			int wy = f + 2;
			if (wy <= 2) {
				continue;
			}
			for (int off : new int[] {-3, 3}) {
				for (String side : new String[] {N, S, E, W}) {
					int x = dx(side) != 0 ? dx(side) * h : off;
					int z = dz(side) != 0 ? dz(side) * h : off;
					set(x, wy, z, VPlan.AIR);
					if (chance(0.6)) {
						int ox = x + dx(side);
						int oz = z + dz(side);
						set(ox, wy, oz, s(VPlan.id(pal.trapdoor), "facing=" + side, "open=true", "half=top"));
					}
				}
			}
		}
		// porta da frente e degrau
		air(0, 0, -h, 0, 1, -h);
		set(0, -1, -h, pal.floor);
		// escada de mao (parede oeste) atravessando os andares
		for (int y = 0; y <= DECK; y++) {
			set(-4, y, 4, ladder(E));
		}
		// terraco saliente, parapeito, estandartes e telhado
		for (int x = -h - 1; x <= h + 1; x++) {
			for (int z = -h - 1; z <= h + 1; z++) {
				if (x == -4 && z == 4) {
					continue;
				}
				set(x, DECK, z, pal.floor);
				int ring = Math.max(Math.abs(x), Math.abs(z));
				if (ring == h + 1) {
					boolean corner = Math.abs(x) == h + 1 && Math.abs(z) == h + 1;
					set(x, DECK + 1, z, corner ? log("y") : pal.fence);
					if (corner) {
						set(x, DECK + 2, z, log("y"));
						set(x, DECK + 3, z, log("y"));
					}
					String inward = Math.abs(z) >= Math.abs(x) ? (z < 0 ? S : N) : (x < 0 ? E : W);
					if (!corner) {
						set(x, DECK - 1, z, stairsTop(pal.stairs, inward));
					}
				}
			}
		}
		for (int i = 0; i <= 6; i++) {
			int rad = h + 1 - i;
			int y = DECK + 4 + i;
			for (int x = -rad; x <= rad; x++) {
				for (int z = -rad; z <= rad; z++) {
					if (Math.max(Math.abs(x), Math.abs(z)) == rad) {
						String inward = Math.abs(z) >= Math.abs(x) ? (z < 0 ? S : N) : (x < 0 ? E : W);
						set(x, y, z, rad == 0 ? pal.slab : stairs(pal.stairs, inward));
					} else {
						set(x, y, z, pal.floor);
					}
				}
			}
		}
		for (String side : new String[] {N, S, E, W}) {
			for (int off : new int[] {-2, 2}) {
				int x = dx(side) != 0 ? dx(side) * (h + 1) : off;
				int z = dz(side) != 0 ? dz(side) * (h + 1) : off;
				set(x, DECK - 2, z, s("white_wall_banner", "facing=" + side));
				p.tag(x, DECK - 2, z, "ominous");
			}
		}
		p.loot(3, DECK + 1, 3, s("chest", "facing=west"), TOP_CHEST);
		p.mark(MARKER_TOP_CHEST, 3, DECK + 1, 3);
		set(-3, DECK + 1, -3, s("campfire", "lit=" + chance(0.5)));
		set(3, DECK + 1, -3, s("barrel", "facing=up"));
		set(0, DECK + 3, 0, s("lantern", "hanging=true"));
		interior(h);
	}

	private void interior(int h) {
		// terreo: entrada, bancada, barris
		set(3, 0, -3, "minecraft:crafting_table");
		p.loot(4, 0, 2, s("barrel", "facing=west"), SUPPLIES);
		set(4, 0, 3, s("barrel", "facing=up"));
		set(4, 1, 3, s("barrel", "facing=up"));
		set(-3, 0, -3, "minecraft:pumpkin");
		set(0, 3, 0, s("lantern", "hanging=true"));
		// 1o andar: arsenal
		set(4, 5, -4, "minecraft:fletching_table");
		set(4, 5, -3, "minecraft:smithing_table");
		set(4, 5, 0, s("anvil", "facing=north"));
		set(-4, 5, -4, s("grindstone", "face=floor", "facing=north"));
		p.loot(4, 5, 4, s("chest", "facing=west"), ARMORY);
		set(3, 5, 4, s("barrel", "facing=up"));
		set(0, 7, 0, s("lantern", "hanging=true"));
		// 2o andar: alojamento
		for (int x : new int[] {-2, 0, 2}) {
			set(x, 9, 4, s("red_bed", "facing=south", "part=head"));
			set(x, 9, 3, s("red_bed", "facing=south", "part=foot"));
		}
		set(4, 9, -4, "minecraft:crafting_table");
		set(-4, 9, -3, s("barrel", "facing=up"));
		set(0, 11, 0, s("lantern", "hanging=true"));
		// 3o andar: sala do capitao
		set(0, 13, 2, "minecraft:cartography_table");
		set(1, 13, 2, s("lectern", "facing=north"));
		set(-1, 13, 2, "minecraft:loom");
		p.loot(4, 13, 4, s("chest", "facing=west"), ARMORY);
		set(4, 13, -4, s("white_banner", "rotation=0"));
		p.tag(4, 13, -4, "ominous");
		set(-2, 13, -2, "minecraft:red_carpet");
		set(-1, 13, -2, "minecraft:red_carpet");
		set(0, 15, 0, s("lantern", "hanging=true"));
		// 4o andar: deposito
		set(4, 17, 4, "minecraft:pumpkin");
		set(3, 17, 4, "minecraft:hay_block");
		set(4, 18, 4, "minecraft:hay_block");
		set(4, 17, 3, s("barrel", "facing=up"));
		p.loot(4, 17, -4, s("barrel", "facing=up"), SUPPLIES);
		set(3, 17, -4, s("barrel", "facing=up"));
		set(-3, 17, -4, s("target"));
		set(0, 19, 0, s("lantern", "hanging=true"));
	}

	// --- acampamento ---

	private void features() {
		List<int[]> slots = new ArrayList<>(List.of(new int[] {-13, -12}, new int[] {13, -12}, new int[] {-13, 9}, new int[] {13, 9},
				new int[] {-4, 15}, new int[] {9, 16}, new int[] {-15, -1}, new int[] {15, -1}));
		Collections.shuffle(slots, r);
		int i = 0;
		cage(slots.get(i++), true);
		if (chance(0.7)) {
			cage(slots.get(i++), false);
		}
		tent(slots.get(i++));
		tent(slots.get(i++));
		targets(slots.get(i++));
		logPile(slots.get(i++));
		if (chance(0.7)) {
			wagon(slots.get(i++));
		}
		if (i < slots.size()) {
			campfire(slots.get(i));
		}
		plants();
	}

	/** Jaula de cerca 5x5 com teto: golem de ferro (ou de neve) ou duas allays. */
	private void cage(int[] at, boolean golem) {
		int cx = at[0];
		int cz = at[1];
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				boolean edge = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
				boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
				set(x, -1, z, edge ? pal.base : golem ? "minecraft:coarse_dirt" : "minecraft:hay_block");
				for (int y = 0; y <= 2; y++) {
					set(x, y, z, edge ? (corner ? log("y") : pal.fence) : VPlan.AIR);
				}
				set(x, 3, z, edge ? pal.floor : pal.slab);
			}
		}
		if (golem) {
			p.entity(pal.snow ? "minecraft:snow_golem" : "minecraft:iron_golem", cx + 0.5, 0, cz + 0.5, "persistent");
			p.mark(MARKER_GOLEM_CAGE, cx, 0, cz);
		} else {
			p.entity("minecraft:allay", cx + 0.5, 0.5, cz + 0.5, "persistent");
			p.entity("minecraft:allay", cx + 0.5, 1.2, cz + 0.5, "persistent");
		}
	}

	/** Barraca de la branca em A, com cumeeira de tora, bancada e abobora. */
	private void tent(int[] at) {
		int cx = at[0];
		int cz = at[1];
		for (int z = cz - 2; z <= cz + 2; z++) {
			for (int y = 0; y <= 3; y++) {
				int w = 3 - y;
				for (int x = cx - w; x <= cx + w; x++) {
					boolean shell = Math.abs(x - cx) == w;
					set(x, y, z, shell ? (z == cz && y == 0 ? pal.fence : "minecraft:white_wool") : VPlan.AIR);
				}
			}
			set(cx, 3, z, log("z"));
			set(cx, -1, z, pal.floor);
		}
		// fundo fechado, frente aberta com estacas
		for (int y = 0; y <= 2; y++) {
			for (int x = cx - (2 - y); x <= cx + (2 - y); x++) {
				set(x, y, cz + 2, "minecraft:white_wool");
			}
		}
		set(cx - 3, 0, cz - 3, pal.fence);
		set(cx + 3, 0, cz - 3, pal.fence);
		set(cx - 1, 0, cz + 1, "minecraft:crafting_table");
		set(cx + 1, 0, cz + 1, chance(0.5) ? "minecraft:pumpkin" : s("barrel", "facing=up"));
		set(cx, 0, cz, "minecraft:white_carpet");
		set(cx, 0, cz - 1, "minecraft:white_carpet");
	}

	/** Alvos de treino: estaca de cerca, fardo de feno e abobora entalhada (os vanilla) e blocos-alvo. */
	private void targets(int[] at) {
		int cx = at[0];
		int cz = at[1];
		for (int i = -1; i <= 1; i++) {
			int x = cx + 2 * i;
			set(x, 0, cz, pal.fence);
			set(x, 1, cz, s("hay_block", "axis=y"));
			set(x, 2, cz, s("carved_pumpkin", "facing=north"));
		}
		set(cx - 3, 0, cz + 2, "minecraft:target");
		set(cx + 3, 0, cz + 2, "minecraft:target");
		set(cx + 3, 1, cz + 2, "minecraft:target");
	}

	private void logPile(int[] at) {
		int cx = at[0];
		int cz = at[1];
		for (int x = cx - 2; x <= cx + 2; x++) {
			set(x, 0, cz - 1, log("x"));
			set(x, 0, cz, log("x"));
			set(x, 0, cz + 1, log("x"));
			set(x, 1, cz - 1, x == cx + 2 ? VPlan.AIR : log("x"));
			set(x, 1, cz, log("x"));
			set(x, 2, cz, x < cx - 1 ? VPlan.AIR : log("x"));
		}
		set(cx + 3, 0, cz + 1, axis(pal.stripped, "y"));
		set(cx + 3, 0, cz - 1, s(pal.slab));
	}

	/** Carroca de suprimentos: estrado de tabuas, rodas de alcapao, barris e caixotes. */
	private void wagon(int[] at) {
		int cx = at[0];
		int cz = at[1];
		for (int x = cx - 1; x <= cx + 1; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				set(x, 0, z, slabTop(pal.slab));
			}
		}
		for (int z : new int[] {cz - 1, cz + 1}) {
			set(cx - 2, 0, z, s(VPlan.id(pal.trapdoor), "facing=west", "open=true"));
			set(cx + 2, 0, z, s(VPlan.id(pal.trapdoor), "facing=east", "open=true"));
		}
		p.loot(cx, 1, cz - 1, s("barrel", "facing=up"), SUPPLIES);
		set(cx - 1, 1, cz, s("barrel", "facing=up"));
		set(cx + 1, 1, cz + 1, "minecraft:hay_block");
		set(cx, 1, cz + 2, "minecraft:pumpkin");
		set(cx, 0, cz - 3, pal.fence);
	}

	private void campfire(int[] at) {
		int cx = at[0];
		int cz = at[1];
		set(cx, 0, cz, s("campfire", "lit=true"));
		set(cx - 2, 0, cz, log("z"));
		set(cx + 2, 0, cz, log("z"));
		set(cx, 0, cz + 2, log("x"));
		set(cx + 1, 0, cz - 2, "minecraft:crafting_table");
	}

	private void paths() {
		for (int z = -HALF + 1; z <= -6; z++) {
			for (int x = -1; x <= 1; x++) {
				if (isAirAt(x, 0, z) && chance(0.85)) {
					set(x, -1, z, "minecraft:dirt_path");
				}
			}
		}
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) >= 6 && isAirAt(x, 0, z) && chance(0.7)) {
					set(x, -1, z, chance(0.3) ? "minecraft:gravel" : "minecraft:dirt_path");
				}
			}
		}
	}

	private void mobs() {
		p.entity("minecraft:pillager", 0.5, DECK + 1, 0.5, "captain");
		p.entity("minecraft:pillager", -2.5, DECK + 1, 3.5, "persistent");
		p.entity("minecraft:pillager", 2.5, DECK + 1, -1.5, "persistent");
		p.entity("minecraft:vindicator", -1.5, 9, 0.5, "persistent");
		p.entity("minecraft:vindicator", 1.5, 9, -1.5, "persistent");
		int count = between(3, 5);
		for (int i = 0; i < 40 && count > 0; i++) {
			int x = between(-18, 18);
			int z = between(-18, 18);
			if (Math.max(Math.abs(x), Math.abs(z)) <= 7 || !isAirAt(x, 0, z) || !isAirAt(x, 1, z)) {
				continue;
			}
			p.entity("minecraft:pillager", x + 0.5, 0, z + 0.5, "persistent");
			count--;
		}
	}
}
