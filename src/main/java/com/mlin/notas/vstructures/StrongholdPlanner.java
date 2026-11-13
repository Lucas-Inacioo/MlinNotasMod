package com.mlin.notas.vstructures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Fortaleza (stronghold) refeita: labirinto de dois andares numa grade de 5x5 celulas de 15 blocos por andar. Cada
 * celula e uma sala ou um cruzamento de corredores; as ligacoes sao uma arvore aleatoria (Kruskal) com alguns ciclos a
 * mais, e becos de corredor sao podados (viram rocha) ou terminam num nicho com bau. Corredores 3x3 de tijolos de pedra
 * misturados (musgosos, rachados e infestados), com tochas raras, teias, nichos com bau ({@code stronghold_corridor}) e
 * armadilhas de fio com despensers de flechas. Portas: arco aberto, porta de carvalho, grade de ferro ou porta de ferro
 * com botao dos dois lados.
 *
 * <p>Salas: escada em espiral de entrada (no centro, onde o olho do fim aponta), biblioteca grande de dois andares com
 * galeria, escadas de mao e lustres ({@code stronghold_library}), escadaria monumental descendo 14 blocos para o andar de
 * baixo, sala do portal do End (estrado com poca de lava, 12 molduras viradas para dentro, gerador de traças na
 * escada), prisoes com celas de grade e porta de ferro, sala da fonte, depositos com barris, refeitorio, arsenal,
 * cripta com tumulos e gerador de esqueletos, estudo com estantes e cruzamentos com pilares
 * ({@code stronghold_crossing}). Logica pura; subterraneo (fora do plano e rocha).
 *
 * <p>Referencias (so ideia de forma, salas e paleta; nada copiado): YUNG's Better Strongholds (grande biblioteca de dois
 * andares, arsenal, refeitorio, armadilhas e passagens), Integrated Stronghold e TONI's Better Strongholds (bloco de
 * prisao com celas de grade, salas de fonte), a proposta "Stronghold Remaster" do feedback da Mojang (salao central
 * ligando varias camaras) e a sala do portal vanilla (lava sob o portal, escada com gerador de traças).
 */
public final class StrongholdPlanner extends PlanBuilder {
	public static final String LIBRARY = "minecraft:chests/stronghold_library";
	public static final String CORRIDOR = "minecraft:chests/stronghold_corridor";
	public static final String CROSSING = "minecraft:chests/stronghold_crossing";
	public static final String TRAP = "mlinnotas:chests/stronghold_trap";
	public static final String MARKER_PORTAL = "portal";
	public static final String MARKER_PORTAL_SPAWNER = "portal_spawner";
	public static final String MARKER_LIBRARY = "library";
	public static final String MARKER_STAIRS = "grand_stairs";
	public static final String MARKER_STAIRS_TOP = "grand_stairs_top";
	public static final String MARKER_CRYPT = "crypt";
	public static final String MARKER_PRISON = "prison";
	public static final String MARKER_FOUNTAIN = "fountain";
	public static final String MARKER_TRAP = "trap";
	public static final int CELL = 15;
	public static final int UPPER = 0;
	public static final int LOWER = -14;
	private static final int G = 2;

	enum Kind {
		START, STAIRS, LIBRARY, PORTAL, PRISON, FOUNTAIN, STOREROOM, MESS, CRYPT, ARMORY, STUDY, CROSSING, CORRIDOR
	}

	/** Sala: interior [x1..x2] x [y..y+h-1] x [z1..z2]. */
	private static final class Room {
		final Kind kind;
		final int level;
		int x1;
		int z1;
		int x2;
		int z2;
		int y;
		int h;
		final List<int[]> doors = new ArrayList<>(); // {x, z, dirIndex} na parede
		final Set<Long> keep = new HashSet<>();

		Room(Kind kind, int level) {
			this.kind = kind;
			this.level = level;
		}

		int cx() {
			return (x1 + x2) / 2;
		}

		int cz() {
			return (z1 + z2) / 2;
		}

		boolean walled() {
			return kind != Kind.CORRIDOR;
		}
	}

	private record Edge(int a, int b, String dir) {
	}

	private static final String[] DIRS = {N, E, S, W};

	private final Room[] slots = new Room[50];
	private final boolean[] connectable = new boolean[50];
	private final List<Room> rooms = new ArrayList<>();
	private final List<Edge> edges = new ArrayList<>();
	private String stairDir;
	private int stairA;

	private StrongholdPlanner(Random random) {
		super(new VPlan(true), random);
	}

	public static VPlan plan(long seed, int variant) {
		StrongholdPlanner b = new StrongholdPlanner(new Random(seed * 3121L + 991));
		b.build();
		return b.p;
	}

	// --- grade ---

	private static int slot(int level, int i, int j) {
		return level * 25 + (i + G) * 5 + (j + G);
	}

	private static int si(int slot) {
		return (slot % 25) / 5 - G;
	}

	private static int sj(int slot) {
		return slot % 5 - G;
	}

	private static int sl(int slot) {
		return slot / 25;
	}

	private static boolean inGrid(int i, int j) {
		return Math.abs(i) <= G && Math.abs(j) <= G;
	}

	private static int floorY(int level) {
		return level == 0 ? UPPER : LOWER;
	}

	private static int dirIndex(String d) {
		return switch (d) {
			case N -> 0;
			case E -> 1;
			case S -> 2;
			default -> 3;
		};
	}

	private void build() {
		layout();
		connect();
		for (Edge e : edges) {
			carve(e);
		}
		for (Room r : rooms) {
			shell(r);
		}
		for (Edge e : edges) {
			registerDoor(e, e.a, e.dir);
			registerDoor(e, e.b, opposite(e.dir));
		}
		for (Room r : rooms) {
			decorate(r);
		}
		for (Room r : rooms) {
			for (int[] d : r.doors) {
				doorway(r, d);
			}
		}
		for (Edge e : edges) {
			corridorDetails(e);
		}
		for (int s = 0; s < 50; s++) {
			Room r = slots[s];
			if (r != null && r.kind == Kind.CORRIDOR) {
				junction(r, s);
			}
		}
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 3, UPPER, 3);
	}

	/** Escolhe a escadaria, a biblioteca, a sala do portal e distribui o resto das celas. */
	private void layout() {
		Room start = new Room(Kind.START, 0);
		box(start, 0, 0, 3, UPPER, 14);
		claim(start, slot(0, 0, 0), true);

		// escadaria monumental: celula A em cima, B (vizinha) embaixo
		while (true) {
			int i = between(-G, G);
			int j = between(-G, G);
			String d = pick(N, E, S, W);
			int bi = i + dx(d);
			int bj = j + dz(d);
			if (!inGrid(bi, bj) || (i == 0 && j == 0) || (bi == 0 && bj == 0)) {
				continue;
			}
			stairDir = d;
			stairA = slot(0, i, j);
			Room stairs = new Room(Kind.STAIRS, 0);
			stairsBox(stairs, i * CELL, j * CELL, d);
			claim(stairs, slot(0, i, j), true);
			claim(stairs, slot(0, bi, bj), false);
			claim(stairs, slot(1, i, j), false);
			claim(stairs, slot(1, bi, bj), true);
			break;
		}
		// biblioteca grande no andar de cima
		List<int[]> anchors = freeAnchors(0);
		int[] lib = anchors.get(r.nextInt(anchors.size()));
		bigRoom(new Room(Kind.LIBRARY, 0), 0, lib[0], lib[1], 11);
		// sala do portal no andar de baixo, longe do pe da escadaria
		int bottom = stairA + 25 + 5 * dx(stairDir) + dz(stairDir);
		List<int[]> lower = freeAnchors(1);
		lower.sort((a, b) -> Integer.compare(dist(b, bottom), dist(a, bottom)));
		int[] portal = lower.get(r.nextInt(Math.min(3, lower.size())));
		bigRoom(new Room(Kind.PORTAL, 1), 1, portal[0], portal[1], 10);

		fillLevel(0, new ArrayList<>(List.of(Kind.PRISON, Kind.FOUNTAIN, Kind.STOREROOM, Kind.MESS, Kind.ARMORY, Kind.CROSSING, Kind.CROSSING,
				Kind.CROSSING)));
		fillLevel(1, new ArrayList<>(List.of(Kind.CRYPT, Kind.PRISON, Kind.STOREROOM, Kind.STUDY, Kind.FOUNTAIN, Kind.CROSSING, Kind.CROSSING,
				Kind.CROSSING)));
	}

	private static int dist(int[] anchor, int slot) {
		return Math.abs(2 * anchor[0] + 1 - 2 * si(slot)) + Math.abs(2 * anchor[1] + 1 - 2 * sj(slot));
	}

	private List<int[]> freeAnchors(int level) {
		List<int[]> out = new ArrayList<>();
		for (int i = -G; i < G; i++) {
			for (int j = -G; j < G; j++) {
				if (slots[slot(level, i, j)] == null && slots[slot(level, i + 1, j)] == null && slots[slot(level, i, j + 1)] == null
						&& slots[slot(level, i + 1, j + 1)] == null) {
					out.add(new int[] {i, j});
				}
			}
		}
		return out;
	}

	private void bigRoom(Room room, int level, int i, int j, int h) {
		room.x1 = i * CELL - 4;
		room.x2 = i * CELL + 18;
		room.z1 = j * CELL - 4;
		room.z2 = j * CELL + 18;
		room.y = floorY(level);
		room.h = h;
		for (int a = 0; a <= 1; a++) {
			for (int c = 0; c <= 1; c++) {
				claim(room, slot(level, i + a, j + c), true);
			}
		}
	}

	private void fillLevel(int level, List<Kind> kinds) {
		List<Integer> free = new ArrayList<>();
		for (int i = -G; i <= G; i++) {
			for (int j = -G; j <= G; j++) {
				if (slots[slot(level, i, j)] == null) {
					free.add(slot(level, i, j));
				}
			}
		}
		Collections.shuffle(free, r);
		for (int k = 0; k < free.size(); k++) {
			int s = free.get(k);
			Kind kind = k < kinds.size() ? kinds.get(k) : Kind.CORRIDOR;
			Room room = new Room(kind, level);
			int half = switch (kind) {
				case FOUNTAIN, CRYPT -> 5;
				case CROSSING -> 3;
				case CORRIDOR -> 1;
				case MESS -> pick(4, 5);
				default -> 4;
			};
			int h = switch (kind) {
				case FOUNTAIN -> 6;
				case CRYPT, CROSSING -> 5;
				case CORRIDOR -> 3;
				default -> 4;
			};
			box(room, si(s) * CELL, sj(s) * CELL, half, floorY(level), h);
			claim(room, s, true);
		}
	}

	private static void box(Room room, int cx, int cz, int half, int y, int h) {
		room.x1 = cx - half;
		room.x2 = cx + half;
		room.z1 = cz - half;
		room.z2 = cz + half;
		room.y = y;
		room.h = h;
	}

	private void stairsBox(Room room, int ax, int az, String d) {
		int[] a = local(ax, az, d, -5, -3);
		int[] b = local(ax, az, d, 20, 3);
		room.x1 = Math.min(a[0], b[0]);
		room.x2 = Math.max(a[0], b[0]);
		room.z1 = Math.min(a[1], b[1]);
		room.z2 = Math.max(a[1], b[1]);
		room.y = LOWER;
		room.h = UPPER + 7 - LOWER;
	}

	/** Ponto a {@code u} blocos na direcao {@code d} e {@code v} para a direita dela. */
	private static int[] local(int ox, int oz, String d, int u, int v) {
		String side = cw(d);
		return new int[] {ox + dx(d) * u + dx(side) * v, oz + dz(d) * u + dz(side) * v};
	}

	private void claim(Room room, int slot, boolean canConnect) {
		slots[slot] = room;
		connectable[slot] = canConnect;
		if (!rooms.contains(room)) {
			rooms.add(room);
		}
	}

	// --- ligacoes ---

	private void connect() {
		List<Edge> candidates = new ArrayList<>();
		for (int level = 0; level <= 1; level++) {
			for (int i = -G; i <= G; i++) {
				for (int j = -G; j <= G; j++) {
					int a = slot(level, i, j);
					if (i < G) {
						candidate(candidates, a, slot(level, i + 1, j), E);
					}
					if (j < G) {
						candidate(candidates, a, slot(level, i, j + 1), S);
					}
				}
			}
		}
		Collections.shuffle(candidates, r);
		Map<Room, Room> parent = new HashMap<>();
		for (Room room : rooms) {
			parent.put(room, room);
		}
		List<Edge> extra = new ArrayList<>();
		for (Edge e : candidates) {
			Room ra = find(parent, slots[e.a]);
			Room rb = find(parent, slots[e.b]);
			if (ra != rb) {
				parent.put(ra, rb);
				edges.add(e);
			} else {
				extra.add(e);
			}
		}
		for (Edge e : extra) {
			if (chance(0.16)) {
				edges.add(e);
			}
		}
		// poda becos de corredor (viram rocha)
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int s = 0; s < 50; s++) {
				Room room = slots[s];
				if (room == null || room.kind != Kind.CORRIDOR) {
					continue;
				}
				List<Edge> touching = new ArrayList<>();
				for (Edge e : edges) {
					if (e.a == s || e.b == s) {
						touching.add(e);
					}
				}
				if (touching.size() <= 1 && (touching.isEmpty() || chance(0.7))) {
					edges.removeAll(touching);
					slots[s] = null;
					rooms.remove(room);
					changed = true;
				}
			}
		}
	}

	private void candidate(List<Edge> out, int a, int b, String dir) {
		Room ra = slots[a];
		Room rb = slots[b];
		if (ra != null && rb != null && ra != rb && connectable[a] && connectable[b]) {
			out.add(new Edge(a, b, dir));
		}
	}

	private static Room find(Map<Room, Room> parent, Room room) {
		Room root = room;
		while (parent.get(root) != root) {
			root = parent.get(root);
		}
		return root;
	}

	// --- blocos ---

	private String brick(int level) {
		double v = r.nextDouble();
		double moss = level == 1 ? 0.24 : 0.16;
		if (v < 0.5 - moss / 2) {
			return "minecraft:stone_bricks";
		}
		if (v < 0.5 + moss / 2) {
			return "minecraft:mossy_stone_bricks";
		}
		if (v < 0.84) {
			return "minecraft:cracked_stone_bricks";
		}
		if (v < 0.9) {
			return "minecraft:infested_stone_bricks";
		}
		if (v < 0.95) {
			return "minecraft:infested_mossy_stone_bricks";
		}
		return "minecraft:infested_cracked_stone_bricks";
	}

	/** Corredor 3x3 de centro a centro das duas celas. */
	private void carve(Edge e) {
		int level = sl(e.a);
		int y = floorY(level);
		int ax = si(e.a) * CELL;
		int az = sj(e.a) * CELL;
		String side = cw(e.dir);
		for (int u = 0; u <= CELL; u++) {
			for (int v = -2; v <= 2; v++) {
				int x = ax + dx(e.dir) * u + dx(side) * v;
				int z = az + dz(e.dir) * u + dz(side) * v;
				for (int dy = -1; dy <= 3; dy++) {
					if (Math.abs(v) == 2 || dy == -1 || dy == 3) {
						if (p.get(x, y + dy, z) == null) {
							set(x, y + dy, z, brick(level));
						}
					} else {
						set(x, y + dy, z, VPlan.CAVE_AIR);
					}
				}
			}
		}
	}

	/** Casca e interior vazio de cada sala (a casca nao fecha corredores ja abertos: viram as portas). */
	private void shell(Room room) {
		if (room.kind == Kind.CORRIDOR) {
			return;
		}
		for (int x = room.x1 - 1; x <= room.x2 + 1; x++) {
			for (int y = room.y - 1; y <= room.y + room.h; y++) {
				for (int z = room.z1 - 1; z <= room.z2 + 1; z++) {
					String here = p.get(x, y, z);
					boolean inside = x >= room.x1 && x <= room.x2 && z >= room.z1 && z <= room.z2 && y >= room.y && y < room.y + room.h;
					if (inside) {
						set(x, y, z, VPlan.CAVE_AIR);
					} else if (here == null || !BlockTraits.isAir(here) || y == room.y - 1 || y == room.y + room.h) {
						set(x, y, z, brick(room.level));
					}
				}
			}
		}
	}

	private void registerDoor(Edge e, int slot, String dir) {
		Room room = slots[slot];
		if (!room.walled()) {
			return;
		}
		int cx = si(slot) * CELL;
		int cz = sj(slot) * CELL;
		int x = switch (dir) {
			case E -> room.x2 + 1;
			case W -> room.x1 - 1;
			default -> cx;
		};
		int z = switch (dir) {
			case S -> room.z2 + 1;
			case N -> room.z1 - 1;
			default -> cz;
		};
		int y = floorY(sl(slot));
		room.doors.add(new int[] {x, z, dirIndex(dir), y});
		// a sala abre a passagem: reabre o vao (a casca de outra sala pode ter fechado)
		String side = cw(dir);
		for (int v = -1; v <= 1; v++) {
			for (int dy = 0; dy <= 2; dy++) {
				set(x + dx(side) * v, y + dy, z + dz(side) * v, VPlan.CAVE_AIR);
			}
		}
		int depth = room.kind == Kind.START || room.kind == Kind.CROSSING ? 1 : 3;
		for (int k = 1; k <= depth; k++) {
			for (int v = -1; v <= 1; v++) {
				for (int dy = 0; dy <= 2; dy++) {
					room.keep.add(VPlan.key(x - dx(dir) * k + dx(side) * v, y + dy, z - dz(dir) * k + dz(side) * v));
				}
			}
		}
	}

	/** Decoracao que respeita a frente das portas. */
	private boolean put(Room room, int x, int y, int z, String state) {
		if (room.keep.contains(VPlan.key(x, y, z))) {
			return false;
		}
		set(x, y, z, state);
		return true;
	}

	private boolean lootAt(Room room, int x, int y, int z, String state, String table) {
		if (room.keep.contains(VPlan.key(x, y, z))) {
			return false;
		}
		p.loot(x, y, z, state, table);
		return true;
	}

	private boolean hasDoor(Room room, String dir) {
		for (int[] d : room.doors) {
			if (d[2] == dirIndex(dir)) {
				return true;
			}
		}
		return false;
	}

	private void decorate(Room room) {
		switch (room.kind) {
			case START -> start(room);
			case STAIRS -> grandStairs(room);
			case LIBRARY -> library(room);
			case PORTAL -> portal(room);
			case PRISON -> prison(room);
			case FOUNTAIN -> fountain(room);
			case STOREROOM -> storeroom(room);
			case MESS -> mess(room);
			case CRYPT -> crypt(room);
			case ARMORY -> armory(room);
			case STUDY -> study(room);
			case CROSSING -> crossing(room);
			default -> {
			}
		}
	}

	// --- salas ---

	/** Escada em espiral em volta de um pilar, subindo ate o teto: o comeco da fortaleza (o olho do fim aponta aqui). */
	private void start(Room room) {
		int y = room.y;
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int k = 0; k < room.h; k++) {
					set(x, y + k, z, (x == 0 && z == 0) || k % 4 == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
				}
			}
		}
		List<int[]> ring = new ArrayList<>();
		for (int i = -2; i < 2; i++) {
			ring.add(new int[] {i, -2});
		}
		for (int i = -2; i < 2; i++) {
			ring.add(new int[] {2, i});
		}
		for (int i = 2; i > -2; i--) {
			ring.add(new int[] {i, 2});
		}
		for (int i = 2; i > -2; i--) {
			ring.add(new int[] {-2, i});
		}
		int first = r.nextInt(16);
		for (int k = 1; k <= 12; k++) {
			int[] at = ring.get((first + k) % 16);
			for (int dy = 0; dy < k; dy++) {
				set(at[0], y + dy, at[1], dy == k - 1 ? "minecraft:smooth_stone" : brick(0));
			}
		}
		for (int k = 13; k <= 16; k++) {
			int[] at = ring.get((first + k) % 16);
			if (k == 14) {
				set(at[0], y, at[1], s("candle", "candles=3", "lit=false"));
			}
		}
		int[] top = ring.get((first + 12) % 16);
		set(top[0], y + 12, top[1], s("candle", "candles=2", "lit=false"));
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				put(room, 3 * sx, y + 2, 3 * sz, "minecraft:cobweb");
			}
		}
	}

	/** Escadaria monumental: patamar em cima, 14 degraus largos com balaustrada e lustres, patamar embaixo. */
	private void grandStairs(Room room) {
		int ax = si(stairA) * CELL;
		int az = sj(stairA) * CELL;
		String d = stairDir;
		String back = opposite(d);
		// patamar de cima macico
		for (int u = -5; u <= -1; u++) {
			for (int v = -3; v <= 3; v++) {
				int[] w = local(ax, az, d, u, v);
				for (int y = LOWER; y < UPPER; y++) {
					set(w[0], y, w[1], y == UPPER - 1 ? (u == -1 || Math.abs(v) == 3 ? "minecraft:chiseled_stone_bricks" : "minecraft:polished_andesite")
							: brick(1));
				}
			}
		}
		for (int k = 1; k <= 14; k++) {
			int u = k - 1;
			for (int v = -3; v <= 3; v++) {
				int[] w = local(ax, az, d, u, v);
				int top = UPPER - 1 - k;
				for (int y = LOWER - 1; y < top; y++) {
					set(w[0], y, w[1], brick(1));
				}
				set(w[0], top, w[1], k == 14 ? "minecraft:polished_andesite" : stairs("minecraft:stone_brick_stairs", back));
				if (Math.abs(v) == 3 && k >= 2 && k < 14) {
					set(w[0], top + 1, w[1], k % 4 == 2 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_brick_wall");
					if (k % 4 == 2) {
						set(w[0], top + 2, w[1], s("lantern", "hanging=false"));
					}
				}
			}
		}
		// patamar de baixo: piso com faixa
		for (int u = 14; u <= 20; u++) {
			for (int v = -3; v <= 3; v++) {
				int[] w = local(ax, az, d, u, v);
				set(w[0], LOWER - 1, w[1], v == 0 || u == 17 ? "minecraft:chiseled_stone_bricks" : "minecraft:polished_andesite");
			}
		}
		// lustres pendurados no teto
		int ceiling = room.y + room.h - 1;
		for (int u : new int[] {4, 10, 17}) {
			int[] w = local(ax, az, d, u, 0);
			int bottom = u == 17 ? LOWER + 6 : UPPER - 1 - u + 4;
			for (int y = ceiling; y > bottom; y--) {
				set(w[0], y, w[1], s("iron_chain", "axis=y"));
			}
			set(w[0], bottom, w[1], s("lantern", "hanging=true"));
		}
		// pilastras nas paredes laterais
		for (int u = -4; u <= 20; u += 4) {
			for (int v : new int[] {-4, 4}) {
				int[] w = local(ax, az, d, u, v);
				for (int y = LOWER; y <= ceiling; y++) {
					if (p.get(w[0], y, w[1]) != null && !BlockTraits.isAir(p.get(w[0], y, w[1]))) {
						set(w[0], y, w[1], y % 5 == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
					}
				}
			}
		}
		for (int v : new int[] {-3, 3}) {
			int[] w = local(ax, az, d, 19, v);
			set(w[0], LOWER, w[1], "minecraft:cobweb");
			int[] t = local(ax, az, d, -4, v);
			set(t[0], UPPER + 2, t[1], wallTorchAway(t[0], t[1], v < 0 ? opposite(cw(d)) : cw(d)));
		}
		int[] m = local(ax, az, d, 17, 0);
		p.mark(MARKER_STAIRS, m[0], LOWER, m[1]);
		int[] top = local(ax, az, d, -3, 0);
		p.mark(MARKER_STAIRS_TOP, top[0], UPPER, top[1]);
	}

	private String wallTorchAway(int x, int z, String wallSide) {
		return wallTorch(opposite(wallSide));
	}

	/** Biblioteca de dois andares: estantes do chao ao teto, galeria com guarda-corpo, escadas de mao, ilhas e lustres. */
	private void library(Room room) {
		int y = room.y;
		int cx = room.x1 + 11;
		int cz = room.z1 + 11;
		for (int x = room.x1; x <= room.x2; x++) {
			for (int z = room.z1; z <= room.z2; z++) {
				int d = Math.min(Math.min(x - room.x1, room.x2 - x), Math.min(z - room.z1, room.z2 - z));
				set(x, y - 1, z, d == 3 || (x == cx || z == cz) && d > 5 ? "minecraft:polished_andesite" : "minecraft:oak_planks");
				if (d == 0) {
					for (int k = 0; k < room.h; k++) {
						put(room, x, y + k, z, k == 5 ? "minecraft:stripped_oak_log" : "minecraft:bookshelf");
					}
				} else if (d <= 2) {
					set(x, y + 5, z, "minecraft:spruce_planks");
				} else if (d == 3) {
					boolean post = (x - room.x1) % 5 == 3 && (z - room.z1) % 5 == 3;
					if (post) {
						for (int k = 0; k <= 5; k++) {
							put(room, x, y + k, z, "minecraft:stripped_spruce_log");
						}
					} else {
						set(x, y + 5, z, slabTop("minecraft:spruce_slab"));
					}
					set(x, y + 6, z, "minecraft:spruce_fence");
				}
			}
		}
		// escadas de mao para a galeria (encostadas nas estantes)
		int[][] ladders = {{room.x1 + 1, cz + 2, 3}, {room.x2 - 1, cz - 2, 1}};
		for (int[] l : ladders) {
			String facing = l[2] == 3 ? E : W;
			for (int k = 0; k <= 6; k++) {
				set(l[0], y + k, l[1], ladder(facing));
			}
		}
		// ilhas de estantes no terreo
		for (int row = -1; row <= 1; row += 2) {
			for (int side = -1; side <= 1; side += 2) {
				int z = cz + row * 5;
				for (int x = cx + side * 3; Math.abs(x - cx) <= 6; x += side) {
					put(room, x, y, z, "minecraft:bookshelf");
					put(room, x, y + 1, z, Math.abs(x - cx) == 6 ? "minecraft:chiseled_bookshelf" : "minecraft:bookshelf");
				}
			}
		}
		// mesa de leitura no centro
		for (int x = cx - 1; x <= cx + 1; x++) {
			set(x, y, cz, slabTop("minecraft:dark_oak_slab"));
			set(x, y, cz - 1, stairs("minecraft:spruce_stairs", S));
			set(x, y, cz + 1, stairs("minecraft:spruce_stairs", N));
		}
		set(cx, y + 1, cz, s("candle", "candles=2", "lit=false"));
		set(cx - 3, y, cz, s("lectern", "facing=east"));
		set(cx + 3, y, cz, s("lectern", "facing=west"));
		for (int[] c : new int[][] {{cx, cz}, {cx - 5, cz}, {cx + 5, cz}}) {
			for (int k = room.h - 1; k >= 6; k--) {
				set(c[0], y + k, c[1], s("iron_chain", "axis=y"));
			}
			set(c[0], y + 5, c[1], s("lantern", "hanging=true"));
		}
		// baus: dois no terreo, dois na galeria
		lootAt(room, cx - 6, y, cz - 4, s("chest", "facing=south"), LIBRARY);
		lootAt(room, cx + 6, y, cz + 4, s("chest", "facing=north"), LIBRARY);
		p.loot(room.x1 + 1, y + 6, room.z1 + 4, s("chest", "facing=east"), LIBRARY);
		p.loot(room.x2 - 1, y + 6, room.z2 - 4, s("chest", "facing=west"), LIBRARY);
		for (int i = 0; i < 10; i++) {
			int x = between(room.x1 + 1, room.x2 - 1);
			int z = between(room.z1 + 1, room.z2 - 1);
			int k = pick(4, room.h - 1, room.h - 2);
			if (isAirAt(x, y + k, z)) {
				set(x, y + k, z, "minecraft:cobweb");
			}
		}
		p.mark(MARKER_LIBRARY, cx, y, cz);
	}

	/** Sala do portal: estrado de dois degraus com poca de lava, 12 molduras viradas para dentro, gerador de traças. */
	private void portal(Room room) {
		int y = room.y;
		int cx = room.x1 + 11;
		int cz = room.z1 + 11;
		// piso em aneis
		for (int x = room.x1; x <= room.x2; x++) {
			for (int z = room.z1; z <= room.z2; z++) {
				int d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
				set(x, y - 1, z, d == 6 || d == 9 ? "minecraft:chiseled_stone_bricks" : d % 2 == 0 ? "minecraft:polished_andesite" : "minecraft:stone_bricks");
			}
		}
		for (int x = cx - 4; x <= cx + 4; x++) {
			for (int z = cz - 4; z <= cz + 4; z++) {
				int d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
				set(x, y, z, d == 4 ? "minecraft:stone_bricks" : "minecraft:polished_andesite");
				set(x, y + 1, z, d <= 1 ? "minecraft:lava" : d == 4 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
			}
		}
		// molduras: viradas para o centro
		for (int k = -1; k <= 1; k++) {
			frame(cx + k, y + 2, cz - 2, S);
			frame(cx + k, y + 2, cz + 2, N);
			frame(cx - 2, y + 2, cz + k, E);
			frame(cx + 2, y + 2, cz + k, W);
		}
		p.mark(MARKER_PORTAL, cx, y + 2, cz);
		// degraus na frente (lado da primeira porta) e atras
		String front = room.doors.isEmpty() ? N : DIRS[room.doors.get(0)[2]];
		for (String side : new String[] {front, opposite(front)}) {
			String lat = cw(side);
			for (int v = -1; v <= 1; v++) {
				int x = cx + dx(side) * 5 + dx(lat) * v;
				int z = cz + dz(side) * 5 + dz(lat) * v;
				set(x, y, z, stairs("minecraft:stone_brick_stairs", opposite(side)));
			}
		}
		// gerador de traças no alto da escada da frente, ao lado
		String lat = cw(front);
		int sx = cx + dx(front) * 4 + dx(lat) * 3;
		int sz = cz + dz(front) * 4 + dz(lat) * 3;
		p.spawner(sx, y + 2, sz, "minecraft:silverfish");
		p.mark(MARKER_PORTAL_SPAWNER, sx, y + 2, sz);
		// pilares com lanternas de alma e janelas de grade no alto das paredes
		for (int ox = -1; ox <= 1; ox += 2) {
			for (int oz = -1; oz <= 1; oz += 2) {
				int px = cx + 8 * ox;
				int pz = cz + 8 * oz;
				for (int k = 0; k < room.h; k++) {
					put(room, px, y + k, pz, k == 0 || k == room.h - 1 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
				}
				put(room, px - ox, y + 4, pz, wallTorchFace(ox < 0 ? E : W, true));
			}
		}
		for (int x = room.x1 + 2; x <= room.x2 - 2; x += 4) {
			for (int z : new int[] {room.z1 - 1, room.z2 + 1}) {
				set(x, y + 6, z, "minecraft:iron_bars");
				set(x, y + 7, z, "minecraft:iron_bars");
			}
		}
		for (int z = room.z1 + 2; z <= room.z2 - 2; z += 4) {
			for (int x : new int[] {room.x1 - 1, room.x2 + 1}) {
				set(x, y + 6, z, "minecraft:iron_bars");
				set(x, y + 7, z, "minecraft:iron_bars");
			}
		}
		for (int k = room.h - 1; k >= room.h - 3; k--) {
			set(cx, y + k, cz + 6, s("iron_chain", "axis=y"));
			set(cx, y + k, cz - 6, s("iron_chain", "axis=y"));
		}
		set(cx, y + room.h - 4, cz + 6, s("soul_lantern", "hanging=true"));
		set(cx, y + room.h - 4, cz - 6, s("soul_lantern", "hanging=true"));
		for (int i = 0; i < 8; i++) {
			int x = between(room.x1, room.x2);
			int z = between(room.z1, room.z2);
			if (isAirAt(x, y + room.h - 1, z)) {
				set(x, y + room.h - 1, z, "minecraft:cobweb");
			}
		}
	}

	private String wallTorchFace(String facing, boolean soul) {
		return s(soul ? "soul_wall_torch" : "wall_torch", "facing=" + facing);
	}

	private void frame(int x, int y, int z, String facing) {
		set(x, y, z, s("end_portal_frame", "facing=" + facing, "eye=" + (chance(0.1) ? "true" : "false")));
	}

	/** Prisao: corredor no meio, celas de grade dos lados (so nos lados sem porta), porta de ferro com botao do lado de fora. */
	private void prison(Room room) {
		int y = room.y;
		int cx = room.cx();
		int cz = room.cz();
		boolean alongX = hasDoor(room, E) || hasDoor(room, W) || !(hasDoor(room, N) || hasDoor(room, S));
		String axis = alongX ? E : S;
		String lat = cw(axis);
		boolean chestPlaced = false;
		for (int side = -1; side <= 1; side += 2) {
			String sideDir = side > 0 ? lat : opposite(lat);
			if (hasDoor(room, sideDir)) {
				continue;
			}
			String out = opposite(sideDir);
			for (int u = -4; u <= 4; u++) {
				for (int depth = 2; depth <= 4; depth++) {
					int x = cx + dx(axis) * u + dx(lat) * side * depth;
					int z = cz + dz(axis) * u + dz(lat) * side * depth;
					if (u == 0) {
						for (int k = 0; k < room.h; k++) {
							put(room, x, y + k, z, "minecraft:stone_bricks");
						}
					} else if (depth == 2) {
						for (int k = 0; k < room.h; k++) {
							put(room, x, y + k, z, k == room.h - 1 ? "minecraft:stone_bricks" : "minecraft:iron_bars");
						}
					}
				}
			}
			// uma porta de ferro por cela, no primeiro lugar livre da frente
			for (int sg = -1; sg <= 1; sg += 2) {
				for (int u : new int[] {2 * sg, 3 * sg, sg, 4 * sg}) {
					int x = cx + dx(axis) * u + dx(lat) * side * 2;
					int z = cz + dz(axis) * u + dz(lat) * side * 2;
					int bx = x - dx(lat) * side;
					int bz = z - dz(lat) * side;
					boolean free = true;
					for (int k = 0; k <= 2; k++) {
						free &= !room.keep.contains(VPlan.key(x, y + k, z)) && !room.keep.contains(VPlan.key(bx, y + k, bz));
					}
					if (!free) {
						continue;
					}
					set(x, y, z, s("iron_door", "facing=" + out, "half=lower", "hinge=left", "open=false", "powered=false"));
					set(x, y + 1, z, s("iron_door", "facing=" + out, "half=upper", "hinge=left", "open=false", "powered=false"));
					set(x, y + 2, z, "minecraft:stone_bricks");
					// botao no tijolo em cima da porta, do lado do corredor (o tijolo energizado abre a porta)
					set(bx, y + 2, bz, s("stone_button", "face=wall", "facing=" + out));
					break;
				}
			}
			for (int u : new int[] {-3, 3}) {
				int x = cx + dx(axis) * u + dx(lat) * side * 4;
				int z = cz + dz(axis) * u + dz(lat) * side * 4;
				if (!chestPlaced && chance(0.6)) {
					chestPlaced = lootAt(room, x, y, z, s("chest", "facing=" + out), CORRIDOR);
				} else {
					put(room, x, y, z, pick("minecraft:skeleton_skull", "minecraft:cobweb", s("candle", "lit=false"), "minecraft:bone_block"));
				}
				int x2 = cx + dx(axis) * (u > 0 ? 1 : -1) + dx(lat) * side * 4;
				int z2 = cz + dz(axis) * (u > 0 ? 1 : -1) + dz(lat) * side * 4;
				put(room, x2, y + room.h - 1, z2, s("iron_chain", "axis=y"));
				put(room, x2, y + room.h - 2, z2, s("iron_chain", "axis=y"));
			}
		}
		put(room, cx + dx(axis) * 3, y + room.h - 1, cz + dz(axis) * 3, s("lantern", "hanging=true"));
		if (!p.markers().containsKey(MARKER_PRISON)) {
			p.mark(MARKER_PRISON, cx, y, cz);
		}
	}

	/** Fonte: bacia com borda de lajes, pilar com lanterna no meio, bancos em volta. */
	private void fountain(Room room) {
		int y = room.y;
		int cx = room.cx();
		int cz = room.cz();
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				int d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
				if (d == 2) {
					set(x, y, z, "minecraft:stone_brick_slab");
				} else if (d == 1) {
					set(x, y - 1, z, "minecraft:mossy_stone_bricks");
					set(x, y, z, "minecraft:water");
				}
			}
		}
		for (int k = 0; k <= 2; k++) {
			set(cx, y + k, cz, k == 2 ? "minecraft:chiseled_stone_bricks" : "minecraft:mossy_stone_bricks");
		}
		set(cx, y + 3, cz, s("lantern", "hanging=false"));
		for (String d : DIRS) {
			String lat = cw(d);
			for (int v = -1; v <= 1; v++) {
				int x = cx + dx(d) * 4 + dx(lat) * v;
				int z = cz + dz(d) * 4 + dz(lat) * v;
				put(room, x, y, z, stairs("minecraft:stone_brick_stairs", d));
			}
		}
		for (int i = 0; i < 6; i++) {
			int x = between(room.x1, room.x2);
			int z = between(room.z1, room.z2);
			if (isAirAt(x, y, z) && Math.max(Math.abs(x - cx), Math.abs(z - cz)) >= 3) {
				put(room, x, y, z, "minecraft:moss_carpet");
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				put(room, cx + 5 * sx, y + 3, cz + 5 * sz, "minecraft:cobweb");
			}
		}
		if (!p.markers().containsKey(MARKER_FOUNTAIN)) {
			p.mark(MARKER_FOUNTAIN, cx, y, cz);
		}
	}

	/** Deposito: barris empilhados, caixotes, dois baus de corredor. */
	private void storeroom(Room room) {
		int y = room.y;
		int chests = 0;
		for (int x = room.x1; x <= room.x2; x++) {
			for (int z = room.z1; z <= room.z2; z++) {
				boolean edge = x == room.x1 || x == room.x2 || z == room.z1 || z == room.z2;
				if (!edge || !chance(0.6)) {
					continue;
				}
				double v = r.nextDouble();
				if (v < 0.12 && chests < 2) {
					String facing = x == room.x1 ? E : x == room.x2 ? W : z == room.z1 ? S : N;
					if (lootAt(room, x, y, z, s("chest", "facing=" + facing), CORRIDOR)) {
						chests++;
					}
				} else if (v < 0.55) {
					if (put(room, x, y, z, s("barrel", "facing=up")) && chance(0.4)) {
						put(room, x, y + 1, z, s("barrel", "facing=up"));
					}
				} else if (v < 0.8) {
					put(room, x, y, z, "minecraft:stripped_spruce_wood");
				} else {
					put(room, x, y, z, "minecraft:hay_block");
				}
			}
		}
		if (chests == 0) {
			int x = room.cx();
			int z = room.cz();
			p.loot(x, y, z, s("chest", "facing=north"), CORRIDOR);
		}
		put(room, room.cx() + 1, y + room.h - 1, room.cz(), "minecraft:cobweb");
		put(room, room.cx(), y + room.h - 1, room.cz() - 1, s("lantern", "hanging=true"));
	}

	/** Refeitorio: mesa comprida com bancos, beliches nas paredes, caldeirao e barris. */
	private void mess(Room room) {
		int y = room.y;
		int cx = room.cx();
		int cz = room.cz();
		boolean alongX = chance(0.5);
		String axis = alongX ? E : S;
		String lat = cw(axis);
		int len = (room.x2 - room.x1) / 2 - 1;
		for (int u = -len + 1; u <= len - 1; u++) {
			int x = cx + dx(axis) * u;
			int z = cz + dz(axis) * u;
			set(x, y, z, slabTop("minecraft:spruce_slab"));
			put(room, x + dx(lat), y, z + dz(lat), stairs("minecraft:oak_stairs", lat));
			put(room, x - dx(lat), y, z - dz(lat), stairs("minecraft:oak_stairs", opposite(lat)));
			if (u % 2 == 0) {
				set(x, y + 1, z, u == 0 ? s("candle", "candles=4", "lit=false") : "minecraft:flower_pot");
			}
		}
		set(cx, y + room.h - 1, cz, s("lantern", "hanging=true"));
		int half = (room.x2 - room.x1) / 2;
		put(room, cx + dx(lat) * half + dx(axis) * half, y, cz + dz(lat) * half + dz(axis) * half, "minecraft:cauldron");
		put(room, cx - dx(lat) * half + dx(axis) * half, y, cz - dz(lat) * half + dz(axis) * half, s("barrel", "facing=up"));
		lootAt(room, cx - dx(lat) * half - dx(axis) * half, y, cz - dz(lat) * half - dz(axis) * half, s("chest", "facing=" + lat), CROSSING);
		put(room, cx + dx(lat) * half - dx(axis) * half, y, cz + dz(lat) * half - dz(axis) * half, "minecraft:smoker");
	}

	/** Cripta: tumulos em fileiras com cranios e velas, gerador de esqueletos e um bau no fundo. */
	private void crypt(Room room) {
		int y = room.y;
		int cx = room.cx();
		int cz = room.cz();
		for (int x = room.x1; x <= room.x2; x++) {
			for (int z = room.z1; z <= room.z2; z++) {
				set(x, y - 1, z, (x + z) % 2 == 0 ? "minecraft:polished_andesite" : "minecraft:stone_bricks");
			}
		}
		for (int tx : new int[] {-3, 3}) {
			for (int tz : new int[] {-3, 0, 3}) {
				boolean ok = true;
				for (int dz = 0; dz <= 1; dz++) {
					ok &= !room.keep.contains(VPlan.key(cx + tx, y, cz + tz + dz)) && !room.keep.contains(VPlan.key(cx + tx, y + 1, cz + tz + dz));
				}
				if (!ok) {
					continue;
				}
				set(cx + tx, y, cz + tz, "minecraft:polished_andesite");
				set(cx + tx, y, cz + tz + 1, "minecraft:polished_andesite");
				set(cx + tx, y + 1, cz + tz, "minecraft:stone_brick_slab");
				set(cx + tx, y + 1, cz + tz + 1, chance(0.5) ? "minecraft:skeleton_skull" : s("candle", "candles=" + between(1, 4), "lit=false"));
			}
		}
		for (int k = room.h - 1; k >= room.h - 2; k--) {
			set(cx, y + k, cz, s("iron_chain", "axis=y"));
		}
		set(cx, y + room.h - 3, cz, s("soul_lantern", "hanging=true"));
		int[][] corners = {{room.x1, room.z1}, {room.x2, room.z2}, {room.x1, room.z2}, {room.x2, room.z1}};
		boolean spawner = false;
		boolean chest = false;
		for (int[] c : corners) {
			if (room.keep.contains(VPlan.key(c[0], y, c[1]))) {
				continue;
			}
			if (!spawner) {
				p.spawner(c[0], y, c[1], "minecraft:skeleton");
				spawner = true;
			} else if (!chest) {
				p.loot(c[0], y, c[1], s("chest", "facing=" + (c[0] == room.x1 ? E : W)), CROSSING);
				chest = true;
			} else {
				set(c[0], y, c[1], "minecraft:cobweb");
			}
		}
		if (!p.markers().containsKey(MARKER_CRYPT)) {
			p.mark(MARKER_CRYPT, cx, y, cz);
		}
	}

	/** Arsenal: bigorna, rebolo, mesa de ferraria, suportes de armadura, grades na parede e um bau. */
	private void armory(Room room) {
		int y = room.y;
		int cx = room.cx();
		int cz = room.cz();
		put(room, room.x1, y, room.z1, s("anvil", "facing=north"));
		put(room, room.x2, y, room.z1, "minecraft:smithing_table");
		put(room, room.x1, y, room.z2, s("grindstone", "face=floor", "facing=north"));
		lootAt(room, room.x2, y, room.z2, s("chest", "facing=west"), CORRIDOR);
		for (int x = room.x1 + 2; x <= room.x2 - 2; x += 2) {
			if (put(room, x, y, room.z1, s("barrel", "facing=up"))) {
				put(room, x, y + 2, room.z1, "minecraft:iron_bars");
			}
		}
		for (int x = room.x1 + 2; x <= room.x2 - 2; x += 3) {
			if (!room.keep.contains(VPlan.key(x, y, room.z2)) && !room.keep.contains(VPlan.key(x, y + 1, room.z2))) {
				p.entity("minecraft:armor_stand", x + 0.5, y, room.z2 + 0.5, "");
			}
		}
		set(cx, y + room.h - 1, cz, s("lantern", "hanging=true"));
	}

	/** Estudo: estantes nas paredes, atril, mesa e um bau de biblioteca. */
	private void study(Room room) {
		int y = room.y;
		for (int x = room.x1; x <= room.x2; x++) {
			for (int z = room.z1; z <= room.z2; z++) {
				boolean edge = x == room.x1 || x == room.x2 || z == room.z1 || z == room.z2;
				if (edge) {
					for (int k = 0; k < room.h; k++) {
						put(room, x, y + k, z, "minecraft:bookshelf");
					}
				}
			}
		}
		int cx = room.cx();
		int cz = room.cz();
		set(cx, y, cz, slabTop("minecraft:spruce_slab"));
		set(cx, y + 1, cz, s("candle", "candles=1", "lit=false"));
		set(cx + 1, y, cz, s("lectern", "facing=west"));
		if (room.keep.contains(VPlan.key(cx - 2, y, cz - 2))) {
			p.loot(cx + 2, y, cz + 2, s("chest", "facing=north"), LIBRARY);
		} else {
			p.loot(cx - 2, y, cz - 2, s("chest", "facing=south"), LIBRARY);
		}
		set(cx, y + room.h - 1, cz, s("lantern", "hanging=true"));
	}

	/** Cruzamento: 4 pilares, pocinho ou lustre no meio, as vezes um bau num canto. */
	private void crossing(Room room) {
		int y = room.y;
		int cx = room.cx();
		int cz = room.cz();
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int k = 0; k < room.h; k++) {
					set(cx + 2 * sx, y + k, cz + 2 * sz, k == 0 || k == room.h - 1 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
				}
			}
		}
		set(cx + 2, y + 2, cz + 1, wallTorch(N));
		if (chance(0.4)) {
			set(cx, y - 1, cz, "minecraft:water");
			set(cx, y - 2, cz, "minecraft:mossy_stone_bricks");
		} else {
			set(cx, y + room.h - 1, cz, s("iron_chain", "axis=y"));
			set(cx, y + room.h - 2, cz, s("lantern", "hanging=true"));
		}
		if (chance(0.7)) {
			int sx = pick(-1, 1);
			int sz = pick(-1, 1);
			p.loot(cx + 3 * sx, y, cz + 3 * sz, s("chest", "facing=" + (sz < 0 ? S : N)), CROSSING);
		}
	}

	// --- portas e corredores ---

	private void doorway(Room room, int[] door) {
		int x = door[0];
		int z = door[1];
		String dir = DIRS[door[2]];
		int y = door[3];
		String lat = cw(dir);
		String type = switch (room.kind) {
			case START, CROSSING, STAIRS, PORTAL -> "arch";
			case PRISON -> "iron";
			case LIBRARY, STUDY, STOREROOM, MESS -> pick("wood", "wood", "arch");
			case CRYPT -> pick("iron", "bars");
			default -> pick("arch", "wood", "bars", "iron");
		};
		if (type.equals("arch")) {
			for (int v = -1; v <= 1; v += 2) {
				set(x + dx(lat) * v, y + 2, z + dz(lat) * v, stairsTop("minecraft:stone_brick_stairs", v < 0 ? opposite(lat) : lat));
			}
			return;
		}
		for (int v = -1; v <= 1; v++) {
			for (int dy = 0; dy <= 2; dy++) {
				int px = x + dx(lat) * v;
				int pz = z + dz(lat) * v;
				if (v == 0 && dy < 2) {
					continue;
				}
				set(px, y + dy, pz, type.equals("bars") && dy < 2 ? "minecraft:iron_bars" : "minecraft:stone_bricks");
			}
		}
		if (type.equals("bars")) {
			return;
		}
		String block = type.equals("iron") ? "iron_door" : "oak_door";
		set(x, y, z, s(block, "facing=" + dir, "half=lower", "hinge=left", "open=false", "powered=false"));
		set(x, y + 1, z, s(block, "facing=" + dir, "half=upper", "hinge=left", "open=false", "powered=false"));
		if (type.equals("iron")) {
			// botao dos dois lados, no tijolo ao lado da porta (o tijolo energizado abre a porta)
			set(x + dx(lat) + dx(dir), y + 1, z + dz(lat) + dz(dir), s("stone_button", "face=wall", "facing=" + dir));
			set(x - dx(lat) - dx(dir), y + 1, z - dz(lat) - dz(dir), s("stone_button", "face=wall", "facing=" + opposite(dir)));
		}
	}

	/** Detalhes no meio do corredor: armadilha de fio, nicho com bau, tochas e teias. */
	private void corridorDetails(Edge e) {
		int level = sl(e.a);
		int y = floorY(level);
		int ax = si(e.a) * CELL;
		int az = sj(e.a) * CELL;
		String d = e.dir;
		String side = cw(d);
		double roll = r.nextDouble();
		if (roll < 0.22 && clearCorridor(ax, az, d, side, y, 7)) {
			int[] c = local(ax, az, d, 7, 0);
			int[] h1 = local(ax, az, d, 7, -1);
			int[] h2 = local(ax, az, d, 7, 1);
			int[] w1 = local(ax, az, d, 7, -2);
			int[] w2 = local(ax, az, d, 7, 2);
			String in1 = side;
			String in2 = opposite(side);
			set(w1[0], y, w1[1], "minecraft:stone_bricks");
			set(w2[0], y, w2[1], "minecraft:stone_bricks");
			set(h1[0], y, h1[1], s("tripwire_hook", "facing=" + in1, "attached=true"));
			set(h2[0], y, h2[1], s("tripwire_hook", "facing=" + in2, "attached=true"));
			boolean xAxis = dx(side) != 0;
			set(c[0], y, c[1], s("tripwire", "attached=true", xAxis ? "east=true" : "north=true", xAxis ? "west=true" : "south=true"));
			p.loot(w1[0], y + 1, w1[1], s("dispenser", "facing=" + in1), TRAP);
			p.loot(w2[0], y + 1, w2[1], s("dispenser", "facing=" + in2), TRAP);
			if (!p.markers().containsKey(MARKER_TRAP)) {
				p.mark(MARKER_TRAP, c[0], y, c[1]);
			}
		} else if (roll < 0.38 && clearCorridor(ax, az, d, side, y, 8)) {
			int v = pick(-1, 1);
			int[] w = local(ax, az, d, 8, 2 * v);
			p.loot(w[0], y, w[1], s("chest", "facing=" + (v > 0 ? opposite(side) : side)), CORRIDOR);
			int[] web = local(ax, az, d, 8, v);
			set(web[0], y + 2, web[1], "minecraft:cobweb");
		}
		if (chance(0.35)) {
			int u = pick(4, 11);
			int v = pick(-1, 1);
			int[] t = local(ax, az, d, u, v);
			int[] wall = local(ax, az, d, u, 2 * v);
			String here = p.get(t[0], y + 2, t[1]);
			String back = p.get(wall[0], y + 2, wall[1]);
			if (here != null && BlockTraits.isAir(here) && back != null && BlockTraits.sturdy(back)) {
				set(t[0], y + 2, t[1], wallTorch(v > 0 ? opposite(side) : side));
			}
		}
		if (chance(0.3)) {
			int[] t = local(ax, az, d, between(3, 12), pick(-1, 1));
			if (isAirAt(t[0], y + 2, t[1])) {
				set(t[0], y + 2, t[1], "minecraft:cobweb");
			}
		}
	}

	/** O trecho {@code u} do corredor e so corredor (ar no meio, parede dos lados). */
	private boolean clearCorridor(int ax, int az, String d, String side, int y, int u) {
		for (int du = -1; du <= 1; du++) {
			for (int v = -1; v <= 1; v++) {
				int[] c = local(ax, az, d, u + du, v);
				if (!isAirAt(c[0], y, c[1]) || !isAirAt(c[0], y + 2, c[1])) {
					return false;
				}
			}
			for (int v : new int[] {-2, 2}) {
				int[] w = local(ax, az, d, u + du, v);
				for (int dy = 0; dy <= 2; dy++) {
					String s = p.get(w[0], y + dy, w[1]);
					if (s == null || !BlockTraits.sturdy(s) || VPlan.id(s).equals("chest")) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Cruzamento de corredores: beco ganha nicho com bau; os outros, tocha ou teia. */
	private void junction(Room room, int s) {
		int cx = si(s) * CELL;
		int cz = sj(s) * CELL;
		int y = room.y;
		List<String> open = new ArrayList<>();
		for (Edge e : edges) {
			if (e.a == s) {
				open.add(e.dir);
			} else if (e.b == s) {
				open.add(opposite(e.dir));
			}
		}
		if (open.size() == 1) {
			String back = opposite(open.get(0));
			p.loot(cx + dx(back), y, cz + dz(back), s("chest", "facing=" + open.get(0)), CORRIDOR);
			set(cx, y + 2, cz, "minecraft:cobweb");
		} else {
			for (String d : DIRS) {
				if (!open.contains(d)) {
					set(cx + dx(d), y + 2, cz + dz(d), wallTorch(opposite(d)));
					break;
				}
			}
		}
	}
}
