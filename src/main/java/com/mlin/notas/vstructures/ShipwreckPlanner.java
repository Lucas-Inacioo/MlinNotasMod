package com.mlin.notas.vstructures;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Naufragio refeito: tres tipos de navio sorteados pela semente (chalupa de 23 blocos e 1 mastro, brigue de 31 e 2
 * mastros, galeao de 39 e 3 mastros com castelo de popa de 2 andares e canhoes), casco arredondado com quilha enterrada
 * na areia, cinta de toras na linha d'agua, amurada de cerca, gurupes, mastros com vergas e velas rasgadas, cesto da
 * gavea. Estado de naufragio: rombos no casco com tabuas espalhadas no fundo, buracos no convés, um mastro quebrado
 * caido ao lado do navio com a vela no chao, as vezes partido em dois e a ancora no fundo pela corrente. Cabine do
 * capitao na popa com o bau do mapa ({@code shipwreck_map}, que traz o mapa do tesouro) e o do tesouro
 * ({@code shipwreck_treasure}); porao com barris, escada de mao ate a escotilha e o bau de suprimentos
 * ({@code shipwreck_supply}) na proa. Encalhado: afundado mais na areia, com montes de areia em volta e dentro, e um
 * rombo ao nivel do chao para entrar a pe. Popa em -z, proa em +z. Logica pura.
 *
 * <p>Referencias (so forma e proporcao; nada copiado): Toni's Better Shipwrecks (tipos de navio, galeao com castelo de
 * popa e cobertas de canhao), o guia "How to build a Minecraft shipwreck" do minecraft.how (quilha e cavernas, casco
 * assimetrico, mastro caido, madeiras misturadas) e o schematic "Shipwreck upside down" do Minecraft Maps (casco de
 * perfil arredondado).
 */
public final class ShipwreckPlanner extends PlanBuilder {
	public static final String MAP = "minecraft:chests/shipwreck_map";
	public static final String SUPPLY = "minecraft:chests/shipwreck_supply";
	public static final String TREASURE = "minecraft:chests/shipwreck_treasure";
	public static final String MARKER_CABIN = "cabin";

	private record Wood(String planks, String log, String stripped, String stairs, String slab, String fence, String trapdoor) {
	}

	private static final Wood[] WOODS = {
			wood("oak"), wood("spruce"), wood("dark_oak"), wood("birch"), wood("jungle")};

	private static Wood wood(String n) {
		return new Wood("minecraft:" + n + "_planks", "minecraft:" + n + "_log", "minecraft:stripped_" + n + "_log", "minecraft:" + n + "_stairs",
				"minecraft:" + n + "_slab", "minecraft:" + n + "_fence", "minecraft:" + n + "_trapdoor");
	}

	private final boolean beached;
	private final int type;
	private final Wood w;
	private final Wood trim;
	private final int len;
	private final int beam;
	private final int deck;
	private final int keel;
	private final int zs;
	private final int ze;
	private final int castle;
	private final List<Integer> masts = new ArrayList<>();

	private ShipwreckPlanner(Random random, boolean beached) {
		super(new VPlan(false), random);
		this.beached = beached;
		this.type = r.nextInt(3);
		this.w = WOODS[r.nextInt(3)];
		this.trim = WOODS[r.nextInt(WOODS.length)];
		this.len = type == 0 ? 23 : type == 1 ? 31 : 39;
		this.beam = type == 0 ? 3 : type == 1 ? 4 : 5;
		this.deck = type == 0 ? 3 : 4;
		this.keel = beached ? -3 : -2;
		this.zs = -len / 2;
		this.ze = zs + len - 1;
		this.castle = type == 0 ? 5 : type == 1 ? 7 : 9;
	}

	public static VPlan plan(long seed, int variant, boolean beached) {
		ShipwreckPlanner b = new ShipwreckPlanner(new Random(seed * 4421L + (beached ? 11 : 5)), beached);
		b.build();
		if (!beached) {
			b.p.setWaterLevel(40);
		}
		return b.p;
	}

	/** Meia boca do casco na linha {@code z} e altura {@code y}; -1 fora do navio. */
	int width(int z, int y) {
		if (z < zs || z > ze || y < keel || y > deck) {
			return -1;
		}
		double t = (z - zs) / (double) (len - 1);
		double shape;
		if (t < 0.1) {
			shape = 0.75 + 0.25 * t / 0.1;
		} else if (t > 0.6) {
			double u = (t - 0.6) / 0.4;
			shape = Math.sqrt(Math.max(0, 1 - u * u));
		} else {
			shape = 1;
		}
		double f = (y - keel) / (double) (deck - keel);
		double belly = 0.35 + 0.65 * Math.min(1, f * 1.7);
		return (int) Math.round(beam * shape * belly);
	}

	private boolean shell(int x, int y, int z) {
		int wd = width(z, y);
		int ax = Math.abs(x);
		if (wd < 0 || ax > wd) {
			return false;
		}
		return ax == wd || y == keel || ax > width(z, y - 1) || ax > width(z - 1, y) || ax > width(z + 1, y);
	}

	private void build() {
		hull();
		sternCastle();
		masts();
		hold();
		damage();
		surroundings();
		for (int x = -beam - 3; x <= beam + 3; x++) {
			for (int z = zs - 3; z <= ze + 6; z++) {
				for (int y = 0; y <= deck + 17; y++) {
					p.setIfAbsent(x, y, z, VPlan.AIR);
				}
			}
		}
		p.removeFloating();
		int entranceZ = zs - 4;
		set(0, 0, entranceZ, VPlan.AIR);
		set(0, 1, entranceZ, VPlan.AIR);
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, entranceZ);
	}

	private void hull() {
		for (int z = zs; z <= ze; z++) {
			for (int y = keel; y <= deck; y++) {
				int wd = width(z, y);
				for (int x = -wd; x <= wd; x++) {
					if (y == deck) {
						set(x, y, z, Math.abs(x) == wd ? w.stripped + "[axis=z]" : w.planks);
					} else if (shell(x, y, z)) {
						String b = x == 0 && y == keel ? axis(w.log, "z") : y == deck - 1 ? axis(trim.stripped, "z") : w.planks;
						set(x, y, z, b);
					} else if (y == keel + 1) {
						set(x, y, z, w.planks); // piso do porao
					} else {
						set(x, y, z, VPlan.AIR);
					}
				}
			}
			// amurada
			int wd = width(z, deck);
			for (int x = -wd; x <= wd; x++) {
				boolean rim = Math.abs(x) == wd || z == zs || z == ze || Math.abs(x) > width(z + 1, deck);
				if (rim) {
					set(x, deck + 1, z, z >= ze - 2 ? w.planks : w.fence);
				}
			}
		}
		// proa levantada e gurupes
		for (int z = ze - 3; z <= ze; z++) {
			int wd = width(z, deck);
			for (int x = -wd; x <= wd; x++) {
				if (p.get(x, deck + 1, z) == null) {
					set(x, deck + 1, z, w.planks);
				}
			}
		}
		for (int z = ze + 1; z <= ze + 4; z++) {
			set(0, deck + 1, z, axis(w.log, "z"));
		}
		set(0, deck + 2, ze + 4, w.fence);
		if (type == 2) {
			// canhoes nas portinholas
			for (int z = zs + castle + 2; z < ze - 5; z += 4) {
				for (int side : new int[] {-1, 1}) {
					int wd = width(z, deck);
					set(side * wd, deck + 1, z, VPlan.AIR);
					set(side * (wd - 1), deck + 1, z, s("lightning_rod", "facing=" + (side < 0 ? W : E)));
				}
			}
		}
	}

	private void sternCastle() {
		int front = zs + castle - 1;
		int levels = type == 2 ? 2 : 1;
		for (int lv = 0; lv < levels; lv++) {
			int base = deck + lv * 4;
			for (int z = zs; z <= front; z++) {
				int wd = width(z, deck);
				for (int x = -wd; x <= wd; x++) {
					boolean wall = Math.abs(x) == wd || z == zs || z == front;
					for (int y = base + 1; y <= base + 3; y++) {
						set(x, y, z, wall ? w.planks : VPlan.AIR);
					}
					set(x, base + 4, z, wall ? axis(trim.stripped, "z") : w.planks);
				}
			}
			// janelas da popa e dos lados
			for (int x = -2; x <= 2; x += 2) {
				set(x, base + 2, zs, s("glass_pane"));
			}
			for (int side : new int[] {-1, 1}) {
				set(side * width(zs + castle / 2, deck), base + 2, zs + castle / 2, s("glass_pane"));
			}
		}
		// porta da cabine
		set(0, deck + 1, front, VPlan.AIR);
		set(0, deck + 2, front, VPlan.AIR);
		set(-1, deck + 3, front, s(trim.stairs, "facing=north", "half=top"));
		set(1, deck + 3, front, s(trim.stairs, "facing=north", "half=top"));
		// gradil do tombadilho
		int top = deck + levels * 4;
		for (int z = zs; z <= front; z++) {
			int wd = width(z, deck);
			for (int x = -wd; x <= wd; x++) {
				if (Math.abs(x) == wd || z == zs || z == front) {
					set(x, top + 1, z, w.fence);
				}
			}
		}
		set(0, top + 1, zs + 1, s("lantern"));
		// moveis da cabine
		int y = deck + 1;
		p.loot(1, y, zs + 1, s("chest", "facing=south"), MAP);
		p.loot(-1, y, zs + 1, s("chest", "facing=south"), TREASURE);
		p.mark(MARKER_CABIN, 0, y, zs + 1);
		set(0, y, zs + 1, s("barrel", "facing=up"));
		int tz = zs + castle / 2;
		set(0, y, tz, w.fence);
		set(0, y + 1, tz, s("brown_carpet"));
		set(1, y, tz, s(w.stairs, "facing=west"));
		set(-1, y + 2, tz - 1, s("lantern", "hanging=true"));
		set(-1, y + 3, tz - 1, s("iron_chain", "axis=y"));
		set(0, y + 3, tz - 1, w.planks);
		int wd = width(tz, deck);
		set(-(wd - 1), y, tz + 1, "minecraft:bookshelf");
		if (type == 2) {
			// escada de mao ate o andar de cima (sala de navegacao)
			for (int ly = y; ly <= y + 3; ly++) {
				set(wd - 1, ly, front - 1, s("ladder", "facing=north"));
			}
			set(wd - 1, y, front, w.planks);
			set(wd - 1, y + 1, front, w.planks);
			set(wd - 1, y + 2, front, w.planks);
			set(wd - 1, y + 3, front, w.planks);
			set(0, y + 4, zs + 1, s("cartography_table"));
			set(1, y + 4, zs + 1, s("lectern", "facing=north"));
		}
	}

	private void masts() {
		double[] at = type == 0 ? new double[] {0.5} : type == 1 ? new double[] {0.36, 0.66} : new double[] {0.32, 0.56, 0.8};
		int fallen = chance(0.75) ? r.nextInt(at.length) : -1;
		for (int i = 0; i < at.length; i++) {
			int mz = zs + (int) Math.round(at[i] * (len - 1));
			masts.add(mz);
			int height = (type == 0 ? 11 : type == 1 ? 13 : 15) - (i == at.length - 1 && at.length > 1 ? 2 : 0);
			int top = i == fallen ? deck + 2 + r.nextInt(2) : deck + height;
			for (int y = keel + 2; y <= top; y++) {
				set(0, y, mz, axis(w.log, "y"));
			}
			if (i == fallen) {
				fallenMast(mz, height);
				continue;
			}
			int yardW = width(mz, deck) + 1;
			for (int yy : new int[] {deck + 5, deck + height - 3}) {
				for (int x = -yardW; x <= yardW; x++) {
					if (x != 0) {
						set(x, yy, mz, axis(w.stripped, "x"));
					}
				}
			}
			// vela rasgada entre as vergas
			for (int x = -yardW + 1; x <= yardW - 1; x++) {
				for (int y = deck + 6; y < deck + height - 3; y++) {
					if (x != 0 && chance(y > deck + 8 ? 0.75 : 0.45)) {
						set(x, y, mz, "minecraft:white_wool");
					}
				}
			}
			// cesto da gavea
			if (i == at.length / 2 && type > 0) {
				int ny = deck + height - 1;
				for (int x = -1; x <= 1; x++) {
					for (int z = mz - 1; z <= mz + 1; z++) {
						if (x != 0 || z != mz) {
							set(x, ny, z, s(w.slab, "type=top"));
							set(x, ny + 1, z, w.fence);
						}
					}
				}
				set(0, ny + 1, mz - 1, VPlan.AIR);
			}
		}
	}

	/** Mastro quebrado caido no chao ao lado do navio, com verga e pedacos de vela. */
	private void fallenMast(int mz, int height) {
		int side = chance(0.5) ? 1 : -1;
		int start = width(mz, 0) + 2;
		int length = height - 3;
		int dz = between(-2, 2);
		for (int i = 0; i < length; i++) {
			int x = side * (start + i);
			int z = mz + (dz * i) / length;
			set(x, 0, z, axis(w.log, "x"));
			if (i == length / 2) {
				for (int k = -3; k <= 3; k++) {
					if (k != 0 && p.get(x, 0, z + k) == null) {
						set(x, 0, z + k, axis(w.stripped, "z"));
					}
				}
			}
			if (chance(0.5) && p.get(x, 0, z + 1) == null) {
				set(x, 0, z + (chance(0.5) ? 1 : -1), s("white_carpet"));
			}
		}
	}

	private void hold() {
		int floor = keel + 2;
		// escotilha com escada de mao no mastro de re
		int mz = masts.get(0);
		for (int y = floor; y <= deck; y++) {
			set(0, y, mz - 1, s("ladder", "facing=north"));
		}
		set(1, deck, mz - 1, VPlan.AIR);
		set(1, deck, mz - 2, w.trapdoor + "[facing=south,half=top,open=true]");
		// barris e caixotes
		for (int z = zs + castle; z < ze - 3; z++) {
			int wd = width(z, floor);
			for (int side : new int[] {-1, 1}) {
				int x = side * (wd - 1);
				if (wd >= 2 && p.get(x, floor, z) != null && isAirAt(x, floor, z) && chance(0.35) && Math.abs(z - mz) > 1) {
					set(x, floor, z, chance(0.6) ? s("barrel", "facing=up") : axis(w.log, "y"));
					if (chance(0.3) && isAirAt(x, floor + 1, z)) {
						set(x, floor + 1, z, s("barrel", "facing=" + (side < 0 ? E : W)));
					}
				}
			}
		}
		// bau de suprimentos na proa
		int sz = ze - 5;
		p.loot(0, floor, sz, s("chest", "facing=south"), SUPPLY);
		set(0, floor + 2, sz - 1, s("lantern", "hanging=true"));
		if (type == 2) {
			int gz = (zs + ze) / 2 + 2;
			p.loot(-1, floor, gz, s("chest", "facing=east"), SUPPLY);
		}
	}

	private void damage() {
		int holes = between(3, 6) + type * 2;
		int sideBias = chance(0.5) ? 1 : -1;
		for (int h = 0; h < holes; h++) {
			int z = between(zs + castle, ze - 2);
			int side = chance(0.75) ? sideBias : -sideBias;
			int y = between(keel + 1, deck);
			int rad = between(1, 2);
			breach(side, y, z, rad);
		}
		if (beached) {
			// rombo ao nivel do chao para entrar a pe no porao
			int z = (zs + ze) / 2 + 1;
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y <= 2; y++) {
					int wd = width(z + dz, y);
					set(sideBias * wd, y, z + dz, VPlan.AIR);
				}
			}
		}
		// buracos no convés
		for (int i = 0; i < 3 + type * 2; i++) {
			int z = between(zs + castle + 1, ze - 4);
			int x = between(-2, 2);
			if (x != 0 && Math.abs(z - masts.get(0)) > 2) {
				set(x, deck, z, VPlan.AIR);
			}
		}
		if (type > 0 && chance(0.35)) {
			// partido em dois: abre um vao no meio do casco
			int gz = (zs + ze) / 2 + between(3, 5);
			for (int z = gz; z <= gz + 1; z++) {
				for (int y = keel + 2; y <= deck + 1; y++) {
					for (int x = -beam - 1; x <= beam + 1; x++) {
						String here = p.get(x, y, z);
						if (here != null && !VPlan.id(here).equals("chest") && !VPlan.id(here).equals("ladder")) {
							set(x, y, z, VPlan.AIR);
						}
					}
				}
			}
		}
	}

	private void breach(int side, int cy, int cz, int rad) {
		for (int z = cz - rad; z <= cz + rad; z++) {
			for (int y = cy - rad; y <= cy + rad; y++) {
				int wd = width(z, y);
				if (wd < 0 || y <= keel) {
					continue;
				}
				int x = side * wd;
				String here = p.get(x, y, z);
				if (here != null && (z - cz) * (z - cz) + (y - cy) * (y - cy) <= rad * rad + 1 && !VPlan.id(here).equals("chest")) {
					set(x, y, z, VPlan.AIR);
					if (chance(0.5)) {
						int gx = side * (wd + between(1, 4));
						int gz = z + between(-2, 2);
						if (p.get(gx, 0, gz) == null) {
							p.set(gx, 0, gz, chance(0.5) ? w.planks : s(w.slab), VPlan.LOOSE);
						}
					}
				}
			}
		}
	}

	private void surroundings() {
		int mz = masts.get(masts.size() - 1);
		// ancora no fundo, presa pela corrente na proa
		int ax = chance(0.5) ? 2 : -2;
		int az = ze + 3;
		for (int y = 0; y <= deck; y++) {
			set(ax, y, az, s("iron_chain", "axis=y"));
		}
		set(ax, deck + 1, az, w.planks);
		set(ax, deck + 1, az - 1, w.planks);
		set(ax, deck + 1, az - 2, w.planks);
		set(ax, 0, az, s("anvil", "facing=north"));
		for (int x = -beam - 6; x <= beam + 6; x++) {
			for (int z = zs - 6; z <= ze + 8; z++) {
				if (p.get(x, 0, z) != null || p.get(x, -1, z) != null) {
					continue;
				}
				double roll = r.nextDouble();
				if (beached) {
					boolean near = Math.abs(x) <= beam + 2;
					if (near && roll < 0.55) {
						p.set(x, 0, z, "minecraft:sand", VPlan.FILL_DOWN);
						if (roll < 0.2 && Math.abs(x) <= beam + 1) {
							p.set(x, 1, z, "minecraft:sand");
						}
					} else if (roll < 0.03) {
						set(x, 0, z, "minecraft:dead_bush");
					}
				} else if (roll < 0.04 && Math.abs(z - mz) > 1) {
					int h = between(3, 10);
					for (int y = 0; y < h; y++) {
						set(x, y, z, y == h - 1 ? s("kelp", "age=" + between(15, 25)) : "minecraft:kelp_plant");
					}
				} else if (roll < 0.22) {
					set(x, 0, z, "minecraft:seagrass");
				}
			}
		}
		if (beached) {
			// areia acumulada no porao
			int floor = keel + 2;
			for (int z = zs + castle; z <= ze; z++) {
				int wd = width(z, floor);
				for (int x = -wd; x <= wd; x++) {
					if (isAirAt(x, floor, z) && chance(0.25) && !VPlan.id(p.get(x, floor, z)).equals("ladder")) {
						set(x, floor, z, "minecraft:sand");
					}
				}
			}
		}
	}
}
