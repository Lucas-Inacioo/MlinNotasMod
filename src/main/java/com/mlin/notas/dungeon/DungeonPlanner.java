package com.mlin.notas.dungeon;

import com.mlin.notas.dungeon.DungeonPlan.Room;
import com.mlin.notas.dungeon.DungeonPlan.RoomType;
import com.mlin.notas.ruins.RuinPlan.Dir;
import com.mlin.notas.util.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Planeja uma masmorra media (logica pura, testada em JUnit): predio na superficie, escadaria longa ate o primeiro
 * andar e 2-3 andares subterraneos de salas numa grade de celulas de 15 blocos ligadas por corredores. Cada andar e
 * uma arvore aleatoria de salas a partir da sala onde a escada chega (todas as salas alcancaveis), mais um atalho
 * opcional (porta de ferro que so abre pela alavanca do lado mais fundo). O ultimo andar tem a sala do mini-chefe,
 * a mais funda da arvore, com o portao selado da camara final (cofre com premio por jogador + bau final), que abre
 * com a Chave da Masmorra que o mini-chefe solta.
 *
 * <p>Salas especiais: entrada, escada, armadilhas (fileiras de placas com ejetores de flechas visiveis nas paredes e
 * ladrilho de aviso), enigma (placa de madeira ligada por redstone a porta do nicho com bau; qualquer coisa em cima
 * da placa abre: um jogador segura enquanto o outro pega, ou joga-se um item), geradores, biblioteca, arsenal,
 * deposito e a sala do tema (tumulos com nichos de ossos na cripta, forja com lava atras de grades na fortaleza ana,
 * piscina com coral no templo afogado, sarcofagos e vasos na catacumba de areia).</p>
 *
 * <p>Referencias de desenho (so forma e ideia; tudo escrito do zero): "King's Crypt - City of the Dead" e
 * "Underground Tomb/Cathedral" (Planet Minecraft) para o mausoleu e os nichos nas paredes; "The Crypts" (Planet
 * Minecraft, servidor Newhaven), masmorra que pede dois jogadores para abrir a porta, para o enigma cooperativo; o mapa
 * "Crypt Crawler" (Minecraft Maps) para o ritmo sala-corredor-armadilha; YUNG's Better Dungeons (catacumbas em
 * labirinto com salas escondidas) e When Dungeons Arise (complexos anoes de mineracao) para a escala media e os temas;
 * as Camaras de Provacao vanilla para o cofre com chave e premio por jogador; o templo do deserto e o monumento
 * oceanico vanilla para as paletas de arenito/terracota e prismarinho.</p>
 */
public final class DungeonPlanner {
	/** Tamanho da celula da grade (sala + metade do corredor de cada lado). */
	public static final int CELL = 15;
	/** Chao do primeiro andar (relativo ao chao da superficie em y=-1). */
	public static final int FLOOR0 = -16;
	/** Distancia vertical entre andares. */
	public static final int SPACING = 9;
	public static final int GRID_W = 4;
	public static final int GRID_H = 3;
	/** z do centro da celula da sala de entrada (a escadaria desce para o sul a partir do predio). */
	public static final int ENTRY_Z = 22;

	private DungeonPlanner() {
	}

	public static int floorY(int floor) {
		return FLOOR0 - SPACING * floor;
	}

	public static DungeonPlan plan(DungeonTheme theme, long seed) {
		Random rnd = new Random(seed * 0x9E3779B97F4A7C15L + theme.ordinal() * 7919L);
		DungeonPlan plan = new DungeonPlan(theme);
		new Builder(theme, rnd, plan).build();
		return plan;
	}

	// ------------------------------------------------------------------------------------------------------------
	// layout (grade de celulas por andar)
	// ------------------------------------------------------------------------------------------------------------

	enum EdgeKind {
		TREE, SHORTCUT, GATE
	}

	/** Ligacao entre duas celulas vizinhas de um andar. */
	record Edge(int ai, int aj, int bi, int bj, EdgeKind kind) {
		boolean touches(int i, int j) {
			return ai == i && aj == j || bi == i && bj == j;
		}
	}

	static final class FloorLayout {
		final int floor;
		final int[][] depth = new int[GRID_W][GRID_H];
		final List<int[]> order = new ArrayList<>();
		final List<Edge> edges = new ArrayList<>();
		int arrival;
		int stair = -1;
		int[] boss;
		int[] vault;

		FloorLayout(int floor) {
			this.floor = floor;
			for (int[] row : depth) {
				java.util.Arrays.fill(row, -1);
			}
		}

		boolean used(int i, int j) {
			return i >= 0 && j >= 0 && i < GRID_W && j < GRID_H && depth[i][j] >= 0;
		}

		boolean connected(int ai, int aj, int bi, int bj) {
			for (Edge e : edges) {
				if (e.touches(ai, aj) && e.touches(bi, bj)) {
					return true;
				}
			}
			return false;
		}

		void add(int i, int j, int d, int pi, int pj) {
			depth[i][j] = d;
			order.add(new int[] {i, j});
			if (pi >= 0) {
				edges.add(new Edge(pi, pj, i, j, EdgeKind.TREE));
			}
		}
	}

	private static final class Builder {
		private final DungeonTheme theme;
		private final Random rnd;
		private final DungeonPlan plan;
		private final int entryI;
		private final List<FloorLayout> layouts = new ArrayList<>();
		/** Sala por andar e celula. */
		private final Map<Long, Integer> roomAt = new HashMap<>();
		/** Celulas reservadas dentro das salas (frente das portas, caminhos): decoracao nao entra. */
		private final Set<Long> reserved = new HashSet<>();

		Builder(DungeonTheme theme, Random rnd, DungeonPlan plan) {
			this.theme = theme;
			this.rnd = rnd;
			this.plan = plan;
			this.entryI = 1 + rnd.nextInt(GRID_W - 2);
		}

		static long cellKey(int floor, int i, int j) {
			return ((long) floor << 16) | ((long) i << 8) | j;
		}

		int cellX(int i) {
			return (i - entryI) * CELL;
		}

		static int cellZ(int j) {
			return ENTRY_Z + j * CELL;
		}

		void build() {
			int floors = 2 + rnd.nextInt(2);
			plan.floors(floors);
			layout(floors);
			rooms();
			for (Room room : plan.rooms()) {
				shell(room);
			}
			for (Room room : plan.rooms()) {
				carve(room);
			}
			entrance();
			for (FloorLayout layout : layouts) {
				for (Edge edge : layout.edges) {
					corridor(layout.floor, edge);
				}
			}
			for (int f = 0; f + 1 < floors; f++) {
				Room upper = room(f, layouts.get(f).stair, 0);
				stairwell(upper);
			}
			for (Room room : plan.rooms()) {
				decorate(room);
			}
			surface();
			enclose();
		}

		// --- grade ---

		private void layout(int floors) {
			int arrival = entryI;
			for (int f = 0; f < floors; f++) {
				boolean last = f == floors - 1;
				int target = floors == 2 ? 6 + rnd.nextInt(2) : 5 + rnd.nextInt(2);
				FloorLayout best = null;
				for (int attempt = 0; attempt < 40 && best == null; attempt++) {
					best = tryFloor(f, arrival, target, last);
				}
				if (best == null) {
					throw new IllegalStateException("masmorra sem layout valido no andar " + f);
				}
				layouts.add(best);
				arrival = best.stair;
			}
		}

		private FloorLayout tryFloor(int f, int arrival, int target, boolean last) {
			FloorLayout l = new FloorLayout(f);
			l.arrival = arrival;
			l.add(arrival, 0, 0, -1, -1);
			if (!last) {
				// escada para baixo na fileira de cima (sem vizinho ao norte, onde fica a rampa)
				List<Integer> options = new ArrayList<>();
				for (int i = 0; i < GRID_W; i++) {
					int dist = Math.abs(i - arrival);
					if (dist == 1 || dist == 2) {
						options.add(i);
					}
				}
				int stair = options.get(rnd.nextInt(options.size()));
				int step = stair > arrival ? 1 : -1;
				for (int i = arrival + step; ; i += step) {
					l.add(i, 0, l.depth[i - step][0] + 1, i - step, 0);
					if (i == stair) {
						break;
					}
				}
				l.stair = stair;
			}
			int grow = last ? target - 1 : target;
			while (l.order.size() < grow) {
				List<int[]> frontier = new ArrayList<>();
				for (int[] c : l.order) {
					for (Dir d : Dir.values()) {
						int ni = c[0] + d.dx;
						int nj = c[1] + d.dz;
						if (ni >= 0 && nj >= 0 && ni < GRID_W && nj < GRID_H && !l.used(ni, nj)) {
							frontier.add(new int[] {c[0], c[1], ni, nj});
						}
					}
				}
				if (frontier.isEmpty()) {
					return null;
				}
				int[] pick = frontier.get(rnd.nextInt(frontier.size()));
				l.add(pick[2], pick[3], l.depth[pick[0]][pick[1]] + 1, pick[0], pick[1]);
			}
			if (last) {
				// mini-chefe na sala mais funda que ainda tenha uma celula livre ao lado para a camara final
				int bestDepth = -1;
				List<int[]> candidates = new ArrayList<>();
				for (int[] c : l.order) {
					if (c[0] == arrival && c[1] == 0) {
						continue;
					}
					List<int[]> free = new ArrayList<>();
					for (Dir d : Dir.values()) {
						int ni = c[0] + d.dx;
						int nj = c[1] + d.dz;
						if (ni >= 0 && nj >= 0 && ni < GRID_W && nj < GRID_H && !l.used(ni, nj)) {
							free.add(new int[] {ni, nj});
						}
					}
					if (free.isEmpty()) {
						continue;
					}
					int d = l.depth[c[0]][c[1]];
					if (d > bestDepth) {
						bestDepth = d;
						candidates.clear();
					}
					if (d == bestDepth) {
						int[] v = free.get(rnd.nextInt(free.size()));
						candidates.add(new int[] {c[0], c[1], v[0], v[1]});
					}
				}
				if (candidates.isEmpty() || bestDepth < 2) {
					return null;
				}
				int[] pick = candidates.get(rnd.nextInt(candidates.size()));
				l.boss = new int[] {pick[0], pick[1]};
				l.vault = new int[] {pick[2], pick[3]};
				l.depth[pick[2]][pick[3]] = bestDepth + 1;
				l.order.add(l.vault);
				l.edges.add(new Edge(pick[0], pick[1], pick[2], pick[3], EdgeKind.GATE));
			}
			// atalho: o par de salas vizinhas nao ligadas mais distante pela arvore
			if (rnd.nextInt(10) < 7) {
				int[] best = null;
				int bestGap = 2;
				for (int[] a : l.order) {
					for (Dir d : new Dir[] {Dir.EAST, Dir.SOUTH}) {
						int bi = a[0] + d.dx;
						int bj = a[1] + d.dz;
						if (!l.used(bi, bj) || l.connected(a[0], a[1], bi, bj) || isCell(l.vault, a) || isCell(l.vault, bi, bj)) {
							continue;
						}
						int gap = treeDistance(l, a[0], a[1], bi, bj);
						if (gap > bestGap) {
							bestGap = gap;
							best = new int[] {a[0], a[1], bi, bj};
						}
					}
				}
				if (best != null) {
					l.edges.add(new Edge(best[0], best[1], best[2], best[3], EdgeKind.SHORTCUT));
				}
			}
			return l;
		}

		private static boolean isCell(int[] c, int[] other) {
			return c != null && c[0] == other[0] && c[1] == other[1];
		}

		private static boolean isCell(int[] c, int i, int j) {
			return c != null && c[0] == i && c[1] == j;
		}

		private static int treeDistance(FloorLayout l, int ai, int aj, int bi, int bj) {
			Map<Long, Integer> dist = new HashMap<>();
			Deque<int[]> queue = new ArrayDeque<>();
			dist.put((long) ai * 64 + aj, 0);
			queue.add(new int[] {ai, aj});
			while (!queue.isEmpty()) {
				int[] c = queue.poll();
				int d = dist.get((long) c[0] * 64 + c[1]);
				if (c[0] == bi && c[1] == bj) {
					return d;
				}
				for (Edge e : l.edges) {
					if (e.kind() != EdgeKind.TREE || !e.touches(c[0], c[1])) {
						continue;
					}
					int ni = e.ai() == c[0] && e.aj() == c[1] ? e.bi() : e.ai();
					int nj = e.ai() == c[0] && e.aj() == c[1] ? e.bj() : e.aj();
					if (dist.putIfAbsent((long) ni * 64 + nj, d + 1) == null) {
						queue.add(new int[] {ni, nj});
					}
				}
			}
			return 0;
		}

		// --- salas ---

		private Set<Dir> sides(FloorLayout l, int i, int j) {
			Set<Dir> out = EnumSet.noneOf(Dir.class);
			for (Edge e : l.edges) {
				if (e.touches(i, j)) {
					int oi = e.ai() == i && e.aj() == j ? e.bi() : e.ai();
					int oj = e.ai() == i && e.aj() == j ? e.bj() : e.aj();
					for (Dir d : Dir.values()) {
						if (i + d.dx == oi && j + d.dz == oj) {
							out.add(d);
						}
					}
				}
			}
			return out;
		}

		private void rooms() {
			// tipos: especiais fixos, os outros saem de um baralho (os primeiros 6 sempre aparecem)
			List<RoomType> must = new ArrayList<>(List.of(RoomType.PUZZLE, RoomType.SPAWNER, RoomType.TRAP, RoomType.SHRINE,
					RoomType.LIBRARY, RoomType.ARMORY));
			Collections.shuffle(must, rnd);
			List<RoomType> extra = new ArrayList<>(List.of(RoomType.SPAWNER, RoomType.STORE, RoomType.HALL, RoomType.SHRINE,
					RoomType.HALL, RoomType.STORE, RoomType.LIBRARY, RoomType.SPAWNER, RoomType.HALL, RoomType.ARMORY));
			Collections.shuffle(extra, rnd);
			List<RoomType> deck = new ArrayList<>(must);
			deck.addAll(extra);

			// salas comuns em ordem aleatoria, para o enigma e as armadilhas cairem em qualquer andar
			List<int[]> free = new ArrayList<>();
			Map<Long, RoomType> types = new HashMap<>();
			for (FloorLayout l : layouts) {
				for (int[] c : l.order) {
					RoomType fixed = null;
					if (l.floor == 0 && c[0] == l.arrival && c[1] == 0) {
						fixed = RoomType.ENTRY;
					} else if (c[0] == l.arrival && c[1] == 0) {
						fixed = RoomType.LANDING;
					} else if (c[0] == l.stair && c[1] == 0) {
						fixed = RoomType.STAIRS_DOWN;
					} else if (isCell(l.boss, c)) {
						fixed = RoomType.BOSS;
					} else if (isCell(l.vault, c)) {
						fixed = RoomType.VAULT;
					}
					if (fixed != null) {
						types.put(cellKey(l.floor, c[0], c[1]), fixed);
					} else {
						free.add(new int[] {l.floor, c[0], c[1]});
					}
				}
			}
			Collections.shuffle(free, rnd);
			// o enigma precisa de um lado sem corredor (o nicho); escolhe a primeira sala que serve
			int puzzleIndex = -1;
			for (int k = 0; k < free.size(); k++) {
				int[] c = free.get(k);
				if (sides(layouts.get(c[0]), c[1], c[2]).size() < 4) {
					puzzleIndex = k;
					break;
				}
			}
			int d = 0;
			for (int k = 0; k < free.size(); k++) {
				int[] c = free.get(k);
				RoomType type;
				if (k == puzzleIndex) {
					type = RoomType.PUZZLE;
				} else {
					do {
						type = deck.get(d++ % deck.size());
					} while (type == RoomType.PUZZLE);
				}
				types.put(cellKey(c[0], c[1], c[2]), type);
			}

			int base = 0;
			for (FloorLayout l : layouts) {
				int fy = floorY(l.floor);
				for (int[] c : l.order) {
					RoomType type = types.get(cellKey(l.floor, c[0], c[1]));
					Set<Dir> sides = sides(l, c[0], c[1]);
					int w;
					int dz;
					switch (type) {
						case ENTRY -> {
							w = 9;
							dz = 9;
						}
						case STAIRS_DOWN, LANDING, VAULT -> {
							w = 11;
							dz = 11;
						}
						case BOSS -> {
							w = 13;
							dz = 13;
						}
						case PUZZLE -> {
							Dir alcove = alcoveSide(sides);
							boolean alongX = alcove.dx != 0;
							w = alongX ? 7 : 9;
							dz = alongX ? 9 : 7;
						}
						case TRAP -> {
							w = 9;
							dz = 11;
						}
						default -> {
							w = 7 + 2 * rnd.nextInt(3);
							dz = 7 + 2 * rnd.nextInt(3);
						}
					}
					int h = switch (type) {
						case BOSS -> 7;
						case VAULT -> 6;
						default -> 5;
					};
					int cx = cellX(c[0]);
					int cz = cellZ(c[1]);
					int id = plan.rooms().size();
					plan.addRoom(new Room(id, type, l.floor, c[0], c[1], cx - (w - 1) / 2, cz - (dz - 1) / 2, cx + (w - 1) / 2,
							cz + (dz - 1) / 2, fy, h, base + l.depth[c[0]][c[1]]));
					roomAt.put(cellKey(l.floor, c[0], c[1]), id);
				}
				int maxDepth = 0;
				for (int[] c : l.order) {
					maxDepth = Math.max(maxDepth, l.depth[c[0]][c[1]]);
				}
				base += l.stair >= 0 ? l.depth[l.stair][0] + 1 : maxDepth + 1;
			}
		}

		private Dir alcoveSide(Set<Dir> sides) {
			for (Dir d : new Dir[] {Dir.SOUTH, Dir.EAST, Dir.WEST, Dir.NORTH}) {
				if (!sides.contains(d)) {
					return d;
				}
			}
			return Dir.SOUTH;
		}

		private Room room(int floor, int i, int j) {
			Integer id = roomAt.get(cellKey(floor, i, j));
			return id == null ? null : plan.rooms().get(id);
		}

		private Set<Dir> sides(Room r) {
			return sides(layouts.get(r.floor()), r.ci(), r.cj());
		}

		// --- casca e interior ---

		private void shell(Room r) {
			int top = r.fy() + r.h() + 1;
			for (int x = r.x0() - 1; x <= r.x1() + 1; x++) {
				for (int z = r.z0() - 1; z <= r.z1() + 1; z++) {
					boolean edge = x < r.x0() || x > r.x1() || z < r.z0() || z > r.z1();
					for (int y = r.fy(); y <= top; y++) {
						if (edge || y == top) {
							plan.set(x, y, z, DungeonPart.WALL);
						} else if (y == r.fy()) {
							plan.set(x, y, z, floorTile(r, x, z));
						}
					}
				}
			}
		}

		private DungeonPart floorTile(Room r, int x, int z) {
			boolean border = x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1();
			if (border) {
				return DungeonPart.FLOOR_TRIM;
			}
			return switch (r.type()) {
				case BOSS, VAULT, ENTRY -> (Math.abs(x - r.cx()) + Math.abs(z - r.cz())) % 4 == 0 ? DungeonPart.FLOOR_ALT : DungeonPart.FLOOR;
				case LIBRARY, SHRINE -> (x + z) % 2 == 0 ? DungeonPart.FLOOR_ALT : DungeonPart.FLOOR;
				default -> (x - r.x0()) % 4 == 2 && (z - r.z0()) % 4 == 2 ? DungeonPart.FLOOR_ALT : DungeonPart.FLOOR;
			};
		}

		private void carve(Room r) {
			for (int x = r.x0(); x <= r.x1(); x++) {
				for (int z = r.z0(); z <= r.z1(); z++) {
					for (int y = r.fy() + 1; y <= r.fy() + r.h(); y++) {
						plan.set(x, y, z, DungeonPart.AIR);
					}
				}
			}
		}

		/** Toda parte do plano abaixo da superficie fica cercada: vizinho fora do plano vira parede (cavernas, agua). */
		private void enclose() {
			List<Long> keys = new ArrayList<>(plan.blocks().keySet());
			for (long k : keys) {
				DungeonPart part = plan.blocks().get(k);
				if (part == DungeonPart.WALL || part == DungeonPart.ORE || part == DungeonPart.GLOW) {
					continue;
				}
				int x = DungeonPlan.keyX(k);
				int y = DungeonPlan.keyY(k);
				int z = DungeonPlan.keyZ(k);
				if (y > -1) {
					continue;
				}
				int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
				for (int[] a : around) {
					int ny = y + a[1];
					if (ny <= -2) {
						plan.setIfAbsent(x + a[0], ny, z + a[2], DungeonPart.WALL);
					}
				}
			}
		}

		// --- escadaria da entrada ---

		private void entrance() {
			Room entry = plan.room(RoomType.ENTRY);
			int fy = entry.fy();
			int steps = -1 - fy - 1; // 14 degraus de -2 ate fy+1
			for (int k = 1; k <= steps + 1; k++) {
				int z = 1 + k;
				int block = -1 - k;
				boolean landing = block <= fy;
				for (int x = -1; x <= 1; x++) {
					if (landing) {
						plan.set(x, fy, z, x == 0 ? DungeonPart.FLOOR_ALT : DungeonPart.FLOOR_TRIM);
					} else {
						plan.set(x, block, z, DungeonPart.STAIRS, Dir.NORTH);
					}
					int stand = landing ? fy + 1 : block + 1;
					for (int y = stand; y <= stand + 3; y++) {
						plan.set(x, y, z, DungeonPart.AIR);
					}
					int ceiling = stand + 4;
					if (ceiling <= -2) {
						plan.set(x, ceiling, z, x == 0 && k % 3 == 0 ? DungeonPart.ACCENT : DungeonPart.WALL);
					}
				}
				int stand = landing ? fy + 1 : block + 1;
				for (int side : new int[] {-2, 2}) {
					for (int y = stand - 1; y <= stand + 4; y++) {
						if (y <= -2) {
							plan.set(side, y, z, k % 4 == 0 && y >= stand && y <= stand + 2 ? DungeonPart.PILLAR : DungeonPart.WALL);
						}
					}
					if (k % 4 == 2 && stand + 2 <= -2) {
						plan.set(side - Integer.signum(side), stand + 2, z, DungeonPart.WALL_TORCH, side > 0 ? Dir.WEST : Dir.EAST);
					}
				}
			}
			// porta da sala de entrada (parede norte)
			doorway(entry.z0() - 1, fy, 0, true, entry.h());
			reserveFront(entry, Dir.NORTH);
		}

		// --- corredores ---

		private void corridor(int floor, Edge e) {
			Room a = room(floor, e.ai(), e.aj());
			Room b = room(floor, e.bi(), e.bj());
			// a = oeste/norte, b = leste/sul
			if (a.ci() > b.ci() || a.cj() > b.cj()) {
				Room t = a;
				a = b;
				b = t;
			}
			boolean alongX = a.cj() == b.cj();
			int start = alongX ? a.x1() + 1 : a.z1() + 1;
			int end = alongX ? b.x0() - 1 : b.z0() - 1;
			int c = alongX ? a.cz() : a.cx();
			int fy = a.fy();
			Dir toB = alongX ? Dir.EAST : Dir.SOUTH;
			for (int u = start; u <= end; u++) {
				boolean wallColumn = u == start || u == end;
				for (int v = c - 2; v <= c + 2; v++) {
					int inner = Math.abs(v - c);
					if (!wallColumn) {
						set(alongX, u, fy, v, inner == 2 ? DungeonPart.WALL : inner == 0 ? DungeonPart.FLOOR : DungeonPart.FLOOR_TRIM);
						set(alongX, u, fy + 4, v, inner == 2 ? DungeonPart.WALL : (u - start) % 4 == 2 ? DungeonPart.ACCENT : DungeonPart.WALL);
					}
					for (int y = fy + 1; y <= fy + 3; y++) {
						if (inner == 2) {
							if (!wallColumn) {
								set(alongX, u, y, v, (u - start) % 4 == 2 ? DungeonPart.PILLAR : DungeonPart.WALL);
							}
						} else {
							set(alongX, u, y, v, DungeonPart.AIR);
						}
					}
				}
				if (!wallColumn && (u - start) % 8 == 6 && end - start > 4) {
					// tocha num pilar do corredor
					boolean left = (u / 8) % 2 == 0;
					int v = left ? c - 1 : c + 1;
					set(alongX, u, fy + 2, v, DungeonPart.WALL_TORCH, alongX ? (left ? Dir.SOUTH : Dir.NORTH) : (left ? Dir.EAST : Dir.WEST));
				}
				if (!wallColumn && rnd.nextInt(6) == 0) {
					set(alongX, u, fy + 3, rnd.nextBoolean() ? c - 1 : c + 1, DungeonPart.COBWEB);
				}
			}
			doorway(start, fy, c, alongX ? false : true, a.h(), alongX);
			doorway(end, fy, c, alongX ? false : true, b.h(), alongX);
			reserveFront(a, alongX ? Dir.EAST : Dir.SOUTH);
			reserveFront(b, alongX ? Dir.WEST : Dir.NORTH);

			switch (e.kind()) {
				case SHORTCUT -> {
					Room inner = a.depth() >= b.depth() ? a : b;
					Room outer = inner == a ? b : a;
					int u = inner == a ? start : end;
					for (int v = c - 1; v <= c + 1; v++) {
						for (int y = fy + 1; y <= fy + 3; y++) {
							if (v != c || y == fy + 3) {
								set(alongX, u, y, v, DungeonPart.WALL);
							}
						}
					}
					Dir intoInner = inner == b ? toB : opposite(toB);
					set(alongX, u, fy + 1, c, DungeonPart.IRON_DOOR, intoInner);
					set(alongX, u, fy + 2, c, DungeonPart.IRON_DOOR_TOP, intoInner);
					int lu = u + (inner == b ? 1 : -1);
					set(alongX, lu, fy + 2, c + 1, DungeonPart.LEVER, intoInner);
					plan.addShortcut(new DungeonPlan.Shortcut(pos(alongX, u, fy + 1, c), pos(alongX, lu, fy + 2, c + 1), inner.id(), outer.id()));
				}
				case GATE -> {
					Room boss = a.type() == RoomType.BOSS ? a : b;
					int u = boss == a ? start : end;
					for (int v = c - 1; v <= c + 1; v++) {
						for (int y = fy + 1; y <= fy + 3; y++) {
							set(alongX, u, y, v, DungeonPart.GATE);
						}
					}
					Pos gate = pos(alongX, u, fy + 1, c);
					plan.mark(DungeonPlan.MARKER_GATE, gate.x(), gate.y(), gate.z());
				}
				case TREE -> {
					int length = end - start - 1;
					if (length >= 3 && rnd.nextInt(10) < 3 && a.type() != RoomType.BOSS && b.type() != RoomType.BOSS) {
						corridorTrap(alongX, (start + end) / 2, fy, c);
					}
				}
			}
		}

		private void corridorTrap(boolean alongX, int u, int fy, int c) {
			List<Pos> plates = new ArrayList<>();
			List<Pos> dispensers = new ArrayList<>();
			for (int v = c - 1; v <= c + 1; v++) {
				set(alongX, u, fy, v, DungeonPart.TRAP_FLOOR);
				set(alongX, u, fy + 1, v, DungeonPart.TRAP_PLATE);
				plates.add(pos(alongX, u, fy + 1, v));
			}
			// ejetores nas duas paredes, um bloco acima do chao: atiram atravessando as placas
			set(alongX, u, fy + 1, c - 2, DungeonPart.DISPENSER, alongX ? Dir.SOUTH : Dir.EAST);
			set(alongX, u, fy + 1, c + 2, DungeonPart.DISPENSER, alongX ? Dir.NORTH : Dir.WEST);
			dispensers.add(pos(alongX, u, fy + 1, c - 2));
			dispensers.add(pos(alongX, u, fy + 1, c + 2));
			// aviso: cranio e teias antes da armadilha nao, ossos no chao do lado
			plan.addTrap(new DungeonPlan.Trap(plates, dispensers));
		}

		private static Dir opposite(Dir d) {
			return d.rotate(2);
		}

		/** Ponto (u ao longo do corredor, v atravessado) em coordenadas x/z. */
		private static Pos pos(boolean alongX, int u, int y, int v) {
			return alongX ? new Pos(u, y, v) : new Pos(v, y, u);
		}

		private void set(boolean alongX, int u, int y, int v, DungeonPart part) {
			Pos p = pos(alongX, u, y, v);
			plan.set(p.x(), p.y(), p.z(), part);
		}

		private void set(boolean alongX, int u, int y, int v, DungeonPart part, Dir dir) {
			Pos p = pos(alongX, u, y, v);
			plan.set(p.x(), p.y(), p.z(), part, dir);
		}

		/** Abertura 3x3 numa parede de sala, com batentes de pilar e verga de destaque. */
		private void doorway(int u, int fy, int c, boolean alongZ, int h) {
			doorway(u, fy, c, alongZ, h, !alongZ);
		}

		private void doorway(int u, int fy, int c, boolean unused, int h, boolean alongX) {
			for (int v = c - 2; v <= c + 2; v++) {
				for (int y = fy + 1; y <= fy + 4; y++) {
					int inner = Math.abs(v - c);
					if (y == fy + 4) {
						set(alongX, u, y, v, DungeonPart.ACCENT);
					} else if (inner == 2) {
						set(alongX, u, y, v, DungeonPart.PILLAR);
					} else {
						set(alongX, u, y, v, y == fy + 3 && inner == 1 ? DungeonPart.STAIRS_TOP : DungeonPart.AIR,
								y == fy + 3 && inner == 1 ? archDir(alongX, v - c) : null);
					}
				}
			}
		}

		/** Degrau de ponta-cabeca nos cantos de cima da porta (arco): a parte alta encosta no batente. */
		private static Dir archDir(boolean alongX, int side) {
			if (alongX) {
				return side < 0 ? Dir.NORTH : Dir.SOUTH;
			}
			return side < 0 ? Dir.WEST : Dir.EAST;
		}

		private void reserveFront(Room r, Dir side) {
			int c = side.dx != 0 ? r.cz() : r.cx();
			for (int depth = 0; depth < 3; depth++) {
				for (int v = c - 1; v <= c + 1; v++) {
					int x;
					int z;
					if (side.dx != 0) {
						x = side.dx > 0 ? r.x1() - depth : r.x0() + depth;
						z = v;
					} else {
						z = side.dz > 0 ? r.z1() - depth : r.z0() + depth;
						x = v;
					}
					reserved.add(DungeonPlan.key(x, r.fy() + 1, z));
				}
			}
		}

		private boolean free(Room r, int x, int z) {
			return r.contains(x, z) && !reserved.contains(DungeonPlan.key(x, r.fy() + 1, z)) && plan.get(x, r.fy() + 1, z) == DungeonPart.AIR;
		}

		private void reserve(Room r, int x, int z) {
			reserved.add(DungeonPlan.key(x, r.fy() + 1, z));
		}

		// --- escada entre andares ---

		/** Rampa de 8 degraus ao longo da parede norte (dois de largura), do andar de cima ao de baixo. */
		private void stairwell(Room upper) {
			Room lower = room(upper.floor() + 1, upper.ci(), upper.cj());
			int fy = upper.fy();
			int x0 = upper.x0();
			int z0 = upper.z0();
			for (int z = z0; z <= z0 + 1; z++) {
				for (int k = 1; k <= 8; k++) {
					int x = x0 + k;
					plan.set(x, fy - k, z, DungeonPart.STAIRS, Dir.WEST);
					for (int y = fy - k + 1; y <= fy - k + 3; y++) {
						plan.set(x, y, z, DungeonPart.AIR);
					}
					for (int y = lower.fy() + 1; y <= fy - k - 1; y++) {
						plan.set(x, y, z, k % 3 == 1 && y > lower.fy() + 1 ? DungeonPart.PILLAR : DungeonPart.WALL);
					}
				}
			}
			// parapeito em volta do buraco no andar de cima
			for (int x = x0 + 1; x <= x0 + 3; x++) {
				plan.set(x, fy + 1, z0 + 2, DungeonPart.STONE_WALL);
			}
			plan.set(x0 + 4, fy + 1, z0 + 2, DungeonPart.STONE_WALL);
			for (int x = x0; x <= x0 + 9; x++) {
				for (int z = z0; z <= z0 + 2; z++) {
					reserve(upper, x, z);
					reserve(lower, x, z);
				}
			}
			for (int z = z0; z <= z0 + 3; z++) {
				reserve(upper, x0, z);
				reserve(lower, x0 + 9, z);
				reserve(lower, x0 + 10, z);
			}
			// luz embutida na parede norte, acima da rampa
			plan.set(x0 + 2, fy, z0 - 1, DungeonPart.GLOW);
			plan.set(x0 + 6, fy - 4, z0 - 1, DungeonPart.GLOW);
		}

		// --- decoracao ---

		private void decorate(Room r) {
			architecture(r);
			lights(r);
			switch (r.type()) {
				case ENTRY -> entryRoom(r);
				case STAIRS_DOWN, LANDING -> stairRoom(r);
				case HALL -> hall(r);
				case LIBRARY -> library(r);
				case ARMORY -> armory(r);
				case SHRINE -> shrine(r);
				case SPAWNER -> spawnerRoom(r);
				case PUZZLE -> puzzleRoom(r);
				case TRAP -> trapRoom(r);
				case STORE -> store(r);
				case BOSS -> bossRoom(r);
				case VAULT -> vaultRoom(r);
			}
			cobwebs(r);
			if (theme == DungeonTheme.DWARF) {
				oreVeins(r);
			}
		}

		/**
		 * Detalhe de arquitetura comum a todas as salas: pilastras nas paredes a cada 4 blocos (longe das portas),
		 * sanca de degraus de ponta-cabeca no alto das paredes e vigas de madeira no teto.
		 */
		private void architecture(Room r) {
			int top = r.fy() + r.h();
			boolean stairRoom = r.type() == RoomType.STAIRS_DOWN || r.type() == RoomType.LANDING;
			for (Dir side : Dir.values()) {
				List<int[]> line = wallLine(r, side);
				for (int k = 0; k < line.size(); k++) {
					int[] s = line.get(k);
					int wx = s[0] + side.dx;
					int wz = s[1] + side.dz;
					// pilastra: a coluna inteira da parede e os vizinhos de baixo ainda sao parede lisa
					if (k % 4 == 2 && k < line.size() - 1 && solidWall(wx, r.fy() + 1, top, wz)
							&& solidWall(wx - side.dz, r.fy() + 1, r.fy() + 3, wz - side.dx) && solidWall(wx + side.dz, r.fy() + 1, r.fy() + 3, wz + side.dx)) {
						for (int y = r.fy() + 1; y <= top; y++) {
							plan.set(wx, y, wz, y == top ? DungeonPart.ACCENT : DungeonPart.PILLAR);
						}
					}
					// sanca: degrau de ponta-cabeca encostado na parede, no alto
					if (!(stairRoom && side == Dir.NORTH) && plan.get(s[0], top, s[1]) == DungeonPart.AIR) {
						plan.set(s[0], top, s[1], DungeonPart.STAIRS_TOP, side);
					}
				}
			}
			// vigas no teto, atravessando o lado menor, a cada 3 blocos
			boolean alongZ = r.x1() - r.x0() >= r.z1() - r.z0();
			int ceiling = top + 1;
			if (alongZ) {
				for (int x = r.x0() + 1; x < r.x1(); x += 3) {
					for (int z = r.z0() - 1; z <= r.z1() + 1; z++) {
						if (plan.get(x, ceiling, z) == DungeonPart.WALL) {
							plan.set(x, ceiling, z, DungeonPart.BEAM, Dir.SOUTH);
						}
					}
				}
			} else {
				for (int z = r.z0() + 1; z < r.z1(); z += 3) {
					for (int x = r.x0() - 1; x <= r.x1() + 1; x++) {
						if (plan.get(x, ceiling, z) == DungeonPart.WALL) {
							plan.set(x, ceiling, z, DungeonPart.BEAM, Dir.EAST);
						}
					}
				}
			}
		}

		private boolean solidWall(int x, int y0, int y1, int z) {
			for (int y = y0; y <= y1; y++) {
				if (plan.get(x, y, z) != DungeonPart.WALL) {
					return false;
				}
			}
			return true;
		}

		/** Tochas nas paredes (ou luz embutida no templo afogado) e lanternas penduradas nas salas grandes. */
		private void lights(Room r) {
			int y = r.fy() + 3;
			for (Dir side : Dir.values()) {
				List<int[]> spots = wallLine(r, side);
				for (int k = 0; k < spots.size(); k++) {
					int[] s = spots.get(k);
					int along = side.dx != 0 ? s[1] : s[0];
					int center = side.dx != 0 ? r.cz() : r.cx();
					int off = Math.abs(along - center);
					if (off != 3 && off != 6) {
						continue;
					}
					if (theme == DungeonTheme.DROWNED) {
						if (plan.get(s[0] + side.dx, y, s[1] + side.dz) == DungeonPart.WALL) {
							plan.set(s[0] + side.dx, y, s[1] + side.dz, DungeonPart.GLOW);
						}
					} else if (plan.get(s[0], y, s[1]) == DungeonPart.AIR && plan.get(s[0] + side.dx, y, s[1] + side.dz) == DungeonPart.WALL) {
						plan.set(s[0], y, s[1], DungeonPart.WALL_TORCH, opposite(side));
					}
				}
			}
			if (r.x1() - r.x0() >= 8 && r.z1() - r.z0() >= 8 && r.type() != RoomType.STAIRS_DOWN && r.type() != RoomType.LANDING
					&& r.type() != RoomType.VAULT) {
				hang(r, r.cx(), r.cz(), r.h() >= 6 ? 3 : 1);
			}
		}

		/** Corrente presa no teto com lanterna na ponta. */
		private void hang(Room r, int x, int z, int chain) {
			int top = r.fy() + r.h();
			for (int k = 0; k < chain; k++) {
				plan.set(x, top - k, z, DungeonPart.CHAIN);
			}
			plan.set(x, top - chain, z, DungeonPart.LANTERN_HANGING);
		}

		/** Celulas encostadas na parede de um lado (de dentro), em ordem. */
		private List<int[]> wallLine(Room r, Dir side) {
			List<int[]> out = new ArrayList<>();
			if (side.dx != 0) {
				int x = side.dx > 0 ? r.x1() : r.x0();
				for (int z = r.z0(); z <= r.z1(); z++) {
					out.add(new int[] {x, z});
				}
			} else {
				int z = side.dz > 0 ? r.z1() : r.z0();
				for (int x = r.x0(); x <= r.x1(); x++) {
					out.add(new int[] {x, z});
				}
			}
			return out;
		}

		/** Lugares livres encostados nas paredes (sem cantos), com a direcao para dentro da sala. */
		private List<int[]> wallSpots(Room r) {
			List<int[]> out = new ArrayList<>();
			for (Dir side : Dir.values()) {
				for (int[] s : wallLine(r, side)) {
					boolean corner = (s[0] == r.x0() || s[0] == r.x1()) && (s[1] == r.z0() || s[1] == r.z1());
					if (!corner && free(r, s[0], s[1])) {
						out.add(new int[] {s[0], s[1], opposite(side).ordinal()});
					}
				}
			}
			Collections.shuffle(out, rnd);
			return out;
		}

		private void cornerPillars(Room r) {
			int top = r.fy() + r.h();
			for (int x : new int[] {r.x0(), r.x1()}) {
				for (int z : new int[] {r.z0(), r.z1()}) {
					if (!free(r, x, z)) {
						continue;
					}
					for (int y = r.fy() + 1; y <= top; y++) {
						plan.set(x, y, z, y == top ? DungeonPart.ACCENT : DungeonPart.PILLAR);
					}
					reserve(r, x, z);
				}
			}
		}

		/** Pilares soltos a 2 blocos das paredes (salas de 9+), com misulas no topo. */
		private void innerPillars(Room r) {
			if (r.x1() - r.x0() < 8 || r.z1() - r.z0() < 8) {
				return;
			}
			int top = r.fy() + r.h();
			for (int x : new int[] {r.x0() + 2, r.x1() - 2}) {
				for (int z : new int[] {r.z0() + 2, r.z1() - 2}) {
					if (!free(r, x, z)) {
						continue;
					}
					for (int y = r.fy() + 1; y <= top; y++) {
						plan.set(x, y, z, DungeonPart.PILLAR);
					}
					for (Dir d : Dir.values()) {
						if (plan.get(x + d.dx, top, z + d.dz) == DungeonPart.AIR) {
							plan.set(x + d.dx, top, z + d.dz, DungeonPart.STAIRS_TOP, opposite(d));
						}
					}
					reserve(r, x, z);
				}
			}
		}

		private void chestAt(Room r, int[] spot, String loot) {
			plan.chest(spot[0], r.fy() + 1, spot[1], Dir.values()[spot[2]], loot);
			reserve(r, spot[0], spot[1]);
		}

		private int[] takeSpot(Room r) {
			List<int[]> spots = wallSpots(r);
			return spots.isEmpty() ? null : spots.getFirst();
		}

		private void place(Room r, int[] spot, DungeonPart part) {
			plan.set(spot[0], r.fy() + 1, spot[1], part, Dir.values()[spot[2]]);
			reserve(r, spot[0], spot[1]);
		}

		private void scatter(Room r, int count, DungeonPart... parts) {
			List<int[]> spots = wallSpots(r);
			for (int k = 0; k < Math.min(count, spots.size()); k++) {
				place(r, spots.get(k), parts[rnd.nextInt(parts.length)]);
			}
		}

		private void cobwebs(Room r) {
			int top = r.fy() + r.h() - 1;
			int[][] spots = {{r.x0() + 1, r.z0()}, {r.x1() - 1, r.z1()}, {r.x0(), r.z1() - 1}, {r.x1(), r.z0() + 1}, {r.x0() + 1, r.z1()}};
			for (int[] s : spots) {
				if (rnd.nextInt(3) > 0 && plan.get(s[0], top, s[1]) == DungeonPart.AIR) {
					plan.set(s[0], top, s[1], DungeonPart.COBWEB);
				}
			}
		}

		private void oreVeins(Room r) {
			int veins = 4 + rnd.nextInt(5);
			for (int k = 0; k < veins; k++) {
				Dir side = Dir.values()[rnd.nextInt(4)];
				List<int[]> line = wallLine(r, side);
				int[] s = line.get(rnd.nextInt(line.size()));
				int y = r.fy() + 1 + rnd.nextInt(r.h());
				int wx = s[0] + side.dx;
				int wz = s[1] + side.dz;
				for (int n = 0; n < 3; n++) {
					int ox = side.dx != 0 ? 0 : rnd.nextInt(3) - 1;
					int oz = side.dx != 0 ? rnd.nextInt(3) - 1 : 0;
					int oy = rnd.nextInt(3) - 1;
					if (plan.get(wx + ox, y + oy, wz + oz) == DungeonPart.WALL) {
						plan.set(wx + ox, y + oy, wz + oz, DungeonPart.ORE);
					}
				}
			}
		}

		private void carpetRunner(Room r, boolean alongX) {
			if (alongX) {
				for (int x = r.x0() + 1; x <= r.x1() - 1; x++) {
					if (plan.get(x, r.fy() + 1, r.cz()) == DungeonPart.AIR) {
						plan.set(x, r.fy() + 1, r.cz(), DungeonPart.CARPET);
					}
				}
			} else {
				for (int z = r.z0() + 1; z <= r.z1() - 1; z++) {
					if (plan.get(r.cx(), r.fy() + 1, z) == DungeonPart.AIR) {
						plan.set(r.cx(), r.fy() + 1, z, DungeonPart.CARPET);
					}
				}
			}
		}

		private void entryRoom(Room r) {
			cornerPillars(r);
			innerPillars(r);
			scatter(r, 2 + rnd.nextInt(2), DungeonPart.BARREL, DungeonPart.DECORATED_POT, DungeonPart.SKULL, DungeonPart.CANDLE);
			if (rnd.nextBoolean()) {
				int[] spot = takeSpot(r);
				if (spot != null) {
					chestAt(r, spot, DungeonPlan.LOOT_COMMON);
				}
			}
			carpetRunner(r, false);
		}

		private void stairRoom(Room r) {
			cornerPillars(r);
			scatter(r, 2, DungeonPart.BARREL, DungeonPart.SKULL, DungeonPart.CANDLE, DungeonPart.DECORATED_POT);
		}

		private void hall(Room r) {
			cornerPillars(r);
			innerPillars(r);
			int[] spot = takeSpot(r);
			if (spot != null) {
				chestAt(r, spot, DungeonPlan.LOOT_COMMON);
			}
			scatter(r, 3, DungeonPart.SKULL, DungeonPart.CANDLE, DungeonPart.DECORATED_POT, DungeonPart.BARREL);
			carpetRunner(r, rnd.nextBoolean());
		}

		private void library(Room r) {
			// estantes de 2 de altura em toda parede livre, velas em cima, atril no meio
			for (int[] s : wallSpots(r)) {
				int y0 = r.fy() + 1;
				if (plan.get(s[0], y0 + 2, s[1]) == DungeonPart.WALL_TORCH) {
					plan.set(s[0], y0 + 2, s[1], DungeonPart.AIR);
				}
				plan.set(s[0], y0, s[1], DungeonPart.BOOKSHELF);
				plan.set(s[0], y0 + 1, s[1], DungeonPart.BOOKSHELF);
				if (rnd.nextInt(4) == 0) {
					plan.set(s[0], y0 + 2, s[1], DungeonPart.CANDLE);
				}
				reserve(r, s[0], s[1]);
			}
			cornerPillars(r);
			int cx = r.cx();
			int cz = r.cz();
			if (free(r, cx, cz)) {
				plan.set(cx, r.fy() + 1, cz, DungeonPart.LECTERN, Dir.SOUTH);
				reserve(r, cx, cz);
			}
			// bau no lugar de uma estante
			for (Dir side : Dir.values()) {
				for (int[] s : wallLine(r, side)) {
					if (plan.get(s[0], r.fy() + 1, s[1]) == DungeonPart.BOOKSHELF && plan.get(s[0], r.fy() + 2, s[1]) == DungeonPart.BOOKSHELF) {
						plan.chest(s[0], r.fy() + 1, s[1], opposite(side), DungeonPlan.LOOT_LIBRARY);
						plan.set(s[0], r.fy() + 2, s[1], DungeonPart.CANDLE);
						if (plan.get(s[0], r.fy() + 3, s[1]) == DungeonPart.CANDLE) {
							plan.set(s[0], r.fy() + 3, s[1], DungeonPart.AIR);
						}
						return;
					}
				}
			}
		}

		private void armory(Room r) {
			cornerPillars(r);
			int[] spot = takeSpot(r);
			if (spot != null) {
				chestAt(r, spot, DungeonPlan.LOOT_ARMORY);
			}
			List<int[]> spots = wallSpots(r);
			int k = 0;
			for (DungeonPart part : new DungeonPart[] {DungeonPart.ANVIL, DungeonPart.WORKSTATION, DungeonPart.BARREL, DungeonPart.BARREL}) {
				if (k < spots.size()) {
					place(r, spots.get(k++), part);
				}
			}
			// suportes de armas: muros de pedra com grades em cima
			for (int n = 0; n < 2 && k < spots.size(); n++, k++) {
				int[] s = spots.get(k);
				plan.set(s[0], r.fy() + 1, s[1], DungeonPart.STONE_WALL);
				plan.set(s[0], r.fy() + 2, s[1], DungeonPart.IRON_BARS);
				reserve(r, s[0], s[1]);
			}
			innerPillars(r);
		}

		private void store(Room r) {
			cornerPillars(r);
			List<int[]> spots = wallSpots(r);
			int chests = 1 + rnd.nextInt(2);
			for (int k = 0; k < spots.size() && k < 7; k++) {
				int[] s = spots.get(k);
				if (k < chests) {
					chestAt(r, s, DungeonPlan.LOOT_COMMON);
				} else {
					boolean pot = rnd.nextInt(3) == 0;
					place(r, s, pot ? DungeonPart.DECORATED_POT : DungeonPart.BARREL);
					if (!pot && rnd.nextBoolean() && plan.get(s[0], r.fy() + 2, s[1]) == DungeonPart.AIR) {
						plan.set(s[0], r.fy() + 2, s[1], DungeonPart.BARREL);
					}
				}
			}
		}

		private void spawnerRoom(Room r) {
			cornerPillars(r);
			int cx = r.cx();
			int cz = r.cz();
			boolean two = r.x1() - r.x0() >= 8 && rnd.nextBoolean() && free(r, cx - 2, cz) && free(r, cx + 2, cz)
					&& free(r, cx - 3, cz) && free(r, cx + 3, cz);
			int[][] points = two ? new int[][] {{cx - 2, cz}, {cx + 2, cz}} : new int[][] {{cx, cz}};
			for (int k = 0; k < points.length; k++) {
				int x = points[k][0];
				int z = points[k][1];
				if (!free(r, x, z)) {
					continue;
				}
				// pedestal: degraus em volta, gerador em cima
				plan.set(x, r.fy() + 1, z, DungeonPart.ACCENT);
				plan.spawner(x, r.fy() + 2, z, theme.spawnerMob(k == 1));
				for (Dir d : Dir.values()) {
					if (free(r, x + d.dx, z + d.dz)) {
						plan.set(x + d.dx, r.fy() + 1, z + d.dz, DungeonPart.STAIRS, opposite(d));
						reserve(r, x + d.dx, z + d.dz);
					}
				}
				reserve(r, x, z);
			}
			int[] spot = takeSpot(r);
			if (spot != null) {
				chestAt(r, spot, DungeonPlan.LOOT_COMMON);
			}
			scatter(r, 3, DungeonPart.SKULL, DungeonPart.CANDLE, DungeonPart.COBWEB);
		}

		private void shrine(Room r) {
			cornerPillars(r);
			switch (theme) {
				case CRYPT -> {
					// nichos de ossos nas paredes (loculi) e tumulos no meio
					for (Dir side : Dir.values()) {
						for (int[] s : wallLine(r, side)) {
							int along = side.dx != 0 ? s[1] : s[0];
							int y = r.fy() + 2;
							int wx = s[0] + side.dx;
							int wz = s[1] + side.dz;
							if (along % 2 == 0 && free(r, s[0], s[1]) && plan.get(wx, y, wz) == DungeonPart.WALL
									&& plan.get(s[0], y, s[1]) == DungeonPart.AIR) {
								plan.set(wx, y, wz, rnd.nextInt(3) == 0 ? DungeonPart.SKULL : DungeonPart.AIR, opposite(side));
								if (plan.get(wx, y, wz) == DungeonPart.AIR && rnd.nextBoolean()) {
									plan.set(wx, y, wz, DungeonPart.CANDLE);
								}
							}
						}
					}
					tombs(r);
				}
				case DWARF -> forge(r);
				case DROWNED -> pool(r);
				case SAND -> {
					tombs(r);
					List<int[]> spots = wallSpots(r);
					for (int k = 0; k < Math.min(5, spots.size()); k++) {
						place(r, spots.get(k), k % 2 == 0 ? DungeonPart.DECORATED_POT : DungeonPart.SKULL);
					}
					// areia suspeita no chao (arqueologia)
					for (int n = 0; n < 4; n++) {
						int x = r.x0() + 1 + rnd.nextInt(r.x1() - r.x0() - 1);
						int z = r.z0() + 1 + rnd.nextInt(r.z1() - r.z0() - 1);
						if (plan.get(x, r.fy() + 1, z) == DungeonPart.AIR) {
							plan.set(x, r.fy(), z, rnd.nextBoolean() ? DungeonPart.SUSPICIOUS : DungeonPart.SAND);
						}
					}
				}
			}
			int[] spot = takeSpot(r);
			if (spot != null) {
				chestAt(r, spot, DungeonPlan.LOOT_COMMON);
			}
		}

		/** Tumulos de 1x2 no miolo da sala (deixa a volta livre). */
		private void tombs(Room r) {
			int cx = r.cx();
			for (int z = r.z0() + 2; z + 1 <= r.z1() - 2; z += 3) {
				for (int x : new int[] {cx - 2, cx + 2}) {
					if (!free(r, x, z) || !free(r, x, z + 1) || x <= r.x0() || x >= r.x1()) {
						continue;
					}
					plan.set(x, r.fy() + 1, z, DungeonPart.STAIRS, Dir.SOUTH);
					plan.set(x, r.fy() + 1, z + 1, DungeonPart.STAIRS, Dir.NORTH);
					plan.set(x, r.fy() + 2, z, DungeonPart.CANDLE);
					reserve(r, x, z);
					reserve(r, x, z + 1);
				}
			}
		}

		/** Forja ana: canal de lava atras de grades numa parede, bigornas e alto-fornos. */
		private void forge(Room r) {
			Set<Dir> open = EnumSet.noneOf(Dir.class);
			Set<Dir> sides = sides(r);
			for (Dir d : Dir.values()) {
				if (!sides.contains(d)) {
					open.add(d);
				}
			}
			Dir wall = open.isEmpty() ? null : open.iterator().next();
			if (wall != null) {
				for (int[] s : wallLine(r, wall)) {
					boolean corner = (s[0] == r.x0() || s[0] == r.x1()) && (s[1] == r.z0() || s[1] == r.z1());
					if (corner || !free(r, s[0], s[1])) {
						continue;
					}
					plan.set(s[0], r.fy(), s[1], DungeonPart.LAVA);
					plan.set(s[0], r.fy() + 1, s[1], DungeonPart.IRON_BARS);
					plan.set(s[0], r.fy() + 2, s[1], DungeonPart.IRON_BARS);
					plan.set(s[0] + wall.dx, r.fy() + 3, s[1] + wall.dz, DungeonPart.GLOW);
					reserve(r, s[0], s[1]);
				}
			}
			List<int[]> spots = wallSpots(r);
			DungeonPart[] stuff = {DungeonPart.ANVIL, DungeonPart.WORKSTATION, DungeonPart.ANVIL, DungeonPart.BARREL, DungeonPart.TREASURE};
			for (int k = 0; k < Math.min(stuff.length, spots.size()); k++) {
				place(r, spots.get(k), stuff[k]);
			}
		}

		/** Santuario alagado: piscina rasa no meio com borda de coral e lanterna do mar no fundo. */
		private void pool(Room r) {
			int cx = r.cx();
			int cz = r.cz();
			int rx = Math.min(2, (r.x1() - r.x0()) / 2 - 2);
			int rz = Math.min(2, (r.z1() - r.z0()) / 2 - 2);
			for (int x = cx - rx; x <= cx + rx; x++) {
				for (int z = cz - rz; z <= cz + rz; z++) {
					if (!free(r, x, z)) {
						continue;
					}
					boolean edge = x == cx - rx || x == cx + rx || z == cz - rz || z == cz + rz;
					if (edge) {
						plan.set(x, r.fy() + 1, z, rnd.nextInt(3) == 0 ? DungeonPart.DECOR_BLOCK : DungeonPart.SLAB);
					} else {
						plan.set(x, r.fy(), z, DungeonPart.WATER);
						plan.set(x, r.fy() - 1, z, x == cx && z == cz ? DungeonPart.GLOW : DungeonPart.SAND);
					}
					reserve(r, x, z);
				}
			}
			scatter(r, 4, DungeonPart.PLANT, DungeonPart.DECOR_BLOCK, DungeonPart.DECORATED_POT);
		}

		private void puzzleRoom(Room r) {
			cornerPillars(r);
			Dir side = alcoveSide(sides(r));
			int fy = r.fy();
			boolean alongX = side.dx != 0; // nicho a leste/oeste
			// u = eixo do nicho (atravessa a parede), v = ao longo da parede
			int wallU = alongX ? (side.dx > 0 ? r.x1() + 1 : r.x0() - 1) : (side.dz > 0 ? r.z1() + 1 : r.z0() - 1);
			int s = alongX ? side.dx : side.dz;
			int c = alongX ? r.cz() : r.cx();
			// nicho 3x3x3 atras da parede
			for (int depth = 1; depth <= 4; depth++) {
				int u = wallU + s * depth;
				for (int v = c - 2; v <= c + 2; v++) {
					for (int y = fy; y <= fy + 4; y++) {
						boolean inside = depth <= 3 && Math.abs(v - c) <= 1 && y >= fy + 1 && y <= fy + 3;
						set(alongX, u, y, v, inside ? DungeonPart.AIR : y == fy ? DungeonPart.FLOOR_TRIM : DungeonPart.WALL);
					}
				}
			}
			Dir out = opposite(side);
			set(alongX, wallU, fy + 1, c, DungeonPart.IRON_DOOR, out);
			set(alongX, wallU, fy + 2, c, DungeonPart.IRON_DOOR_TOP, out);
			for (int v = c - 1; v <= c + 1; v += 2) {
				for (int y = fy + 1; y <= fy + 2; y++) {
					set(alongX, wallU, y, v, DungeonPart.ACCENT);
				}
			}
			// bau no fundo, botao do lado de dentro para sair, tocha
			Pos chest = pos(alongX, wallU + 3 * s, fy + 1, c);
			plan.chest(chest.x(), chest.y(), chest.z(), out, DungeonPlan.LOOT_SECRET);
			Pos button = pos(alongX, wallU + s, fy + 2, c + 1);
			plan.set(button.x(), button.y(), button.z(), DungeonPart.BUTTON, side);
			Pos torch = pos(alongX, wallU + 2 * s, fy + 3, c - 1);
			plan.set(torch.x(), torch.y(), torch.z(), DungeonPart.WALL_TORCH, alongX ? Dir.SOUTH : Dir.EAST);
			Pos door = pos(alongX, wallU, fy + 1, c);

			// placa num canto do lado oposto, redstone ao longo da parede do fundo e depois reto ate a porta
			int farU = alongX ? (side.dx > 0 ? r.x0() : r.x1()) : (side.dz > 0 ? r.z0() : r.z1());
			int frontU = wallU - s;
			int cornerV = rnd.nextBoolean() ? (alongX ? r.z0() : r.x0()) : (alongX ? r.z1() : r.x1());
			int vs = Integer.signum(c - cornerV);
			Pos plate = pos(alongX, farU, fy + 1, cornerV);
			for (int v = cornerV; v != c + vs; v += vs) {
				Pos p = pos(alongX, farU, fy + 1, v);
				plan.set(p.x(), p.y(), p.z(), v == cornerV ? DungeonPart.PUZZLE_PLATE : DungeonPart.REDSTONE);
				reserve(r, p.x(), p.z());
			}
			for (int u = farU + s; u != frontU + s; u += s) {
				Pos p = pos(alongX, u, fy + 1, c);
				plan.set(p.x(), p.y(), p.z(), DungeonPart.REDSTONE);
				reserve(r, p.x(), p.z());
			}
			// a placa fica num canto: tira o pilar dali se tiver
			for (int y = fy + 2; y <= fy + r.h(); y++) {
				if (plan.get(plate.x(), y, plate.z()) == DungeonPart.PILLAR || plan.get(plate.x(), y, plate.z()) == DungeonPart.ACCENT) {
					plan.set(plate.x(), y, plate.z(), DungeonPart.AIR);
				}
			}
			plan.puzzle(new DungeonPlan.Puzzle(plate, door, chest, button));
			scatter(r, 2, DungeonPart.SKULL, DungeonPart.CANDLE);
		}

		private void trapRoom(Room r) {
			cornerPillars(r);
			int fy = r.fy();
			for (int z : new int[] {r.z0() + 3, r.z1() - 3}) {
				List<Pos> plates = new ArrayList<>();
				List<Pos> dispensers = new ArrayList<>();
				for (int x = r.x0(); x <= r.x1(); x++) {
					plan.set(x, fy, z, DungeonPart.TRAP_FLOOR);
					plan.set(x, fy + 1, z, DungeonPart.TRAP_PLATE);
					plates.add(new Pos(x, fy + 1, z));
				}
				plan.set(r.x0() - 1, fy + 1, z, DungeonPart.DISPENSER, Dir.EAST);
				plan.set(r.x1() + 1, fy + 1, z, DungeonPart.DISPENSER, Dir.WEST);
				dispensers.add(new Pos(r.x0() - 1, fy + 1, z));
				dispensers.add(new Pos(r.x1() + 1, fy + 1, z));
				// aviso: cranios no chao antes das placas
				plan.addTrap(new DungeonPlan.Trap(plates, dispensers));
				for (int x = r.x0(); x <= r.x1(); x++) {
					reserve(r, x, z);
				}
			}
			// entre as fileiras, ossadas das vitimas anteriores (aviso)
			for (int n = 0; n < 2; n++) {
				int x = n == 0 ? r.x0() : r.x1();
				int z = r.cz() + (rnd.nextBoolean() ? 1 : -1) * 2;
				if (free(r, x, z)) {
					plan.set(x, fy + 1, z, DungeonPart.SKULL, n == 0 ? Dir.EAST : Dir.WEST);
					reserve(r, x, z);
				}
			}
			int[] spot = takeSpot(r);
			if (spot != null) {
				chestAt(r, spot, DungeonPlan.LOOT_COMMON);
			}
		}

		private void bossRoom(Room r) {
			cornerPillars(r);
			innerPillars(r);
			int fy = r.fy();
			// anel de lanternas penduradas e luz alta
			hang(r, r.x0() + 2, r.cz(), 2);
			hang(r, r.x1() - 2, r.cz(), 2);
			hang(r, r.cx(), r.z0() + 2, 2);
			hang(r, r.cx(), r.z1() - 2, 2);
			// trono no lado oposto ao portao
			Set<Dir> sides = sides(r);
			Dir gateSide = null;
			Room vault = plan.room(RoomType.VAULT);
			for (Dir d : sides) {
				if (r.ci() + d.dx == vault.ci() && r.cj() + d.dz == vault.cj()) {
					gateSide = d;
				}
			}
			Dir throneSide = gateSide == null ? Dir.NORTH : opposite(gateSide);
			if (sides.contains(throneSide)) {
				for (Dir d : Dir.values()) {
					if (!sides.contains(d)) {
						throneSide = d;
						break;
					}
				}
			}
			int tx = r.cx() + throneSide.dx * ((r.x1() - r.x0()) / 2);
			int tz = r.cz() + throneSide.dz * ((r.z1() - r.z0()) / 2);
			if (!sides.contains(throneSide)) {
				Dir in = opposite(throneSide);
				for (int k = -1; k <= 1; k++) {
					int x = tx + (throneSide.dx == 0 ? k : 0);
					int z = tz + (throneSide.dz == 0 ? k : 0);
					plan.set(x, fy + 1, z, k == 0 ? DungeonPart.ACCENT : DungeonPart.SLAB);
					reserve(r, x, z);
				}
				plan.set(tx, fy + 2, tz, DungeonPart.STAIRS, throneSide);
				plan.set(tx - (throneSide.dz != 0 ? 1 : 0), fy + 2, tz - (throneSide.dx != 0 ? 1 : 0), DungeonPart.CANDLE);
				plan.set(tx + (throneSide.dz != 0 ? 1 : 0), fy + 2, tz + (throneSide.dx != 0 ? 1 : 0), DungeonPart.CANDLE);
				plan.set(tx + throneSide.dx, fy + 3, tz + throneSide.dz, DungeonPart.ACCENT);
				plan.set(tx + throneSide.dx, fy + 4, tz + throneSide.dz, DungeonPart.GLOW);
				// passadeira do trono ao meio
				for (int k = 1; k < (Math.abs(throneSide.dx) * (r.x1() - r.x0()) + Math.abs(throneSide.dz) * (r.z1() - r.z0())) / 2; k++) {
					int x = tx + in.dx * k;
					int z = tz + in.dz * k;
					if (plan.get(x, fy + 1, z) == DungeonPart.AIR) {
						plan.set(x, fy + 1, z, DungeonPart.CARPET);
					}
				}
			}
			scatter(r, 6, DungeonPart.SKULL, DungeonPart.CANDLE, DungeonPart.SKULL);
			plan.mark(DungeonPlan.MARKER_BOSS, r.cx(), fy + 1, r.cz() + (gateSide == Dir.SOUTH ? -1 : 1));
		}

		private void vaultRoom(Room r) {
			cornerPillars(r);
			innerPillars(r);
			int fy = r.fy();
			int cx = r.cx();
			int cz = r.cz();
			// estrado em degraus com o cofre no topo
			for (int x = cx - 1; x <= cx + 1; x++) {
				for (int z = cz - 1; z <= cz + 1; z++) {
					if (x == cx && z == cz) {
						plan.set(x, fy + 1, z, DungeonPart.ACCENT);
					} else {
						Dir d = x < cx ? Dir.WEST : x > cx ? Dir.EAST : z < cz ? Dir.NORTH : Dir.SOUTH;
						plan.set(x, fy + 1, z, x != cx && z != cz ? DungeonPart.SLAB : DungeonPart.STAIRS, opposite(d));
					}
					reserve(r, x, z);
				}
			}
			Room boss = plan.room(RoomType.BOSS);
			Dir toBoss = Dir.SOUTH;
			for (Dir d : Dir.values()) {
				if (r.ci() + d.dx == boss.ci() && r.cj() + d.dz == boss.cj()) {
					toBoss = d;
				}
			}
			plan.set(cx, fy + 2, cz, DungeonPart.VAULT, toBoss);
			plan.mark(DungeonPlan.MARKER_VAULT, cx, fy + 2, cz);
			// bau final na parede do fundo, ladeado de tesouros
			Dir back = opposite(toBoss);
			int bx = cx + back.dx * ((r.x1() - r.x0()) / 2);
			int bz = cz + back.dz * ((r.z1() - r.z0()) / 2);
			plan.chest(bx, fy + 1, bz, toBoss, DungeonPlan.LOOT_FINAL);
			plan.mark(DungeonPlan.MARKER_FINAL_CHEST, bx, fy + 1, bz);
			reserve(r, bx, bz);
			for (int k : new int[] {-2, 2}) {
				int x = bx + (back.dz != 0 ? k : 0);
				int z = bz + (back.dx != 0 ? k : 0);
				if (free(r, x, z)) {
					plan.set(x, fy + 1, z, DungeonPart.TREASURE);
					plan.set(x, fy + 2, z, DungeonPart.CANDLE);
					reserve(r, x, z);
				}
			}
			for (int k : new int[] {-1, 1}) {
				int x = bx + (back.dz != 0 ? k : 0);
				int z = bz + (back.dx != 0 ? k : 0);
				if (free(r, x, z)) {
					plan.set(x, fy + 1, z, DungeonPart.LANTERN);
					reserve(r, x, z);
				}
			}
			scatter(r, 4, DungeonPart.DECORATED_POT, DungeonPart.TREASURE, DungeonPart.CANDLE);
			// luz no teto sobre o cofre
			plan.set(cx, fy + r.h() + 1, cz, DungeonPart.GLOW);
		}

		// --- predio da superficie ---

		private void surface() {
			// limpa o terreno em volta (ate 10 acima) e assenta o patio
			for (int x = -7; x <= 7; x++) {
				for (int z = -9; z <= 10; z++) {
					for (int y = 0; y <= 11; y++) {
						plan.setIfAbsent(x, y, z, DungeonPart.AIR);
					}
				}
			}
			// paredes x -4..4, z -4..7; porta ao norte
			int wallTop = theme == DungeonTheme.SAND ? 3 : 4;
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 7; z++) {
					boolean edge = x == -4 || x == 4 || z == -4 || z == 7;
					boolean corner = (x == -4 || x == 4) && (z == -4 || z == 7);
					if (plan.get(x, -1, z) != DungeonPart.AIR) {
						plan.set(x, -1, z, edge ? DungeonPart.ACCENT : (x + z) % 2 == 0 ? DungeonPart.FLOOR_ALT : DungeonPart.FLOOR);
					}
					for (int y = 0; y <= wallTop; y++) {
						if (edge) {
							plan.set(x, y, z, corner || y == 0 ? DungeonPart.PILLAR : DungeonPart.WALL);
						} else if (plan.get(x, y, z) == DungeonPart.AIR || plan.get(x, y, z) == null) {
							plan.set(x, y, z, DungeonPart.AIR);
						}
					}
				}
			}
			// porta 3x3 com arco
			for (int x = -1; x <= 1; x++) {
				for (int y = 0; y <= 2; y++) {
					plan.set(x, y, -4, y == 2 && x != 0 ? DungeonPart.STAIRS_TOP : DungeonPart.AIR, y == 2 && x != 0 ? (x < 0 ? Dir.WEST : Dir.EAST) : null);
				}
			}
			for (int y = 0; y <= 3; y++) {
				plan.set(-2, y, -4, DungeonPart.PILLAR);
				plan.set(2, y, -4, DungeonPart.PILLAR);
			}
			for (int x = -2; x <= 2; x++) {
				plan.set(x, 3, -4, DungeonPart.ACCENT);
			}
			// degraus de entrada e caminho
			for (int x = -1; x <= 1; x++) {
				for (int z = -9; z <= -5; z++) {
					plan.set(x, -1, z, x == 0 ? DungeonPart.FLOOR_ALT : DungeonPart.FLOOR_TRIM);
				}
			}
			// parapeito do buraco da escada
			for (int z = 2; z <= 5; z++) {
				plan.set(-2, 0, z, DungeonPart.STONE_WALL);
				plan.set(2, 0, z, DungeonPart.STONE_WALL);
			}
			plan.set(-2, 1, 2, DungeonPart.CANDLE);
			plan.set(2, 1, 5, DungeonPart.CANDLE);
			plan.mark(DungeonPlan.MARKER_ENTRANCE, 0, 0, -8);

			switch (theme) {
				case CRYPT -> {
					gableRoof(wallTop);
					windows(wallTop);
					buttresses(wallTop);
					graveyard();
					// vasos com velas ladeando a porta
					for (int x : new int[] {-2, 2}) {
						plan.set(x, 0, -6, DungeonPart.DECORATED_POT);
						ground(x, -6);
					}
					plan.set(0, wallTop, 0, DungeonPart.CHAIN);
					plan.set(0, wallTop - 1, 0, DungeonPart.LANTERN_HANGING);
					plan.set(-3, 0, 6, DungeonPart.SKULL, Dir.EAST);
					plan.set(3, 0, -3, DungeonPart.CANDLE);
				}
				case DWARF -> {
					flatRoof(wallTop);
					// colunas altas do portao com lanternas
					for (int y = 4; y <= 6; y++) {
						plan.set(-2, y, -5, DungeonPart.PILLAR);
						plan.set(2, y, -5, DungeonPart.PILLAR);
					}
					for (int y = 0; y <= 3; y++) {
						plan.set(-2, y, -5, DungeonPart.PILLAR);
						plan.set(2, y, -5, DungeonPart.PILLAR);
					}
					for (int x = -2; x <= 2; x++) {
						plan.set(x, 7, -5, DungeonPart.ACCENT);
					}
					plan.set(-2, 8, -5, DungeonPart.LANTERN);
					plan.set(2, 8, -5, DungeonPart.LANTERN);
					plan.set(0, 8, -5, DungeonPart.TREASURE);
					for (int x = -1; x <= 1; x++) {
						plan.set(x, 3, -5, DungeonPart.STAIRS_TOP, Dir.NORTH);
					}
					plan.set(0, wallTop, 0, DungeonPart.CHAIN);
					plan.set(0, wallTop - 1, 0, DungeonPart.LANTERN_HANGING);
					plan.set(-3, 0, -3, DungeonPart.BARREL, Dir.EAST);
					plan.set(3, 0, -3, DungeonPart.ANVIL, Dir.NORTH);
					// carrinhos de minerio: blocos de minerio no patio
					plan.set(-5, 0, -7, DungeonPart.ORE);
					plan.set(5, 0, -6, DungeonPart.ORE);
					plan.set(5, 1, -6, DungeonPart.LANTERN);
					for (int[] g : new int[][] {{-5, -7}, {5, -6}, {-2, -5}, {2, -5}}) {
						ground(g[0], g[1]);
					}
				}
				case DROWNED -> {
					domeRoof(wallTop);
					// colunata em ruinas: abre as paredes laterais
					for (int z = -3; z <= 6; z++) {
						for (int y = 1; y <= 3; y++) {
							if (z % 3 != 0) {
								plan.set(-4, y, z, y == 1 ? DungeonPart.SLAB : DungeonPart.AIR);
								plan.set(4, y, z, y == 1 ? DungeonPart.SLAB : DungeonPart.AIR);
							}
						}
					}
					for (int x = -6; x <= 6; x++) {
						for (int z = -9; z <= 10; z++) {
							if (Math.abs(x) >= 5 || z <= -5 || z >= 8) {
								if (plan.get(x, -1, z) == null && rnd.nextInt(3) > 0) {
									plan.set(x, -1, z, DungeonPart.SAND);
								}
							}
						}
					}
					plan.set(-5, 0, 2, DungeonPart.DECOR_BLOCK);
					plan.set(5, 0, -1, DungeonPart.DECOR_BLOCK);
					plan.set(-5, 0, -6, DungeonPart.DECOR_BLOCK);
					for (int[] g : new int[][] {{-5, 2}, {5, -1}, {-5, -6}}) {
						ground(g[0], g[1]);
					}
					plan.set(-3, 0, -3, DungeonPart.PLANT);
					plan.set(3, 0, 6, DungeonPart.PLANT);
					plan.set(0, wallTop, 0, DungeonPart.GLOW);
				}
				case SAND -> {
					pyramidRoof(wallTop);
					// obeliscos na frente
					for (int x : new int[] {-4, 4}) {
						for (int y = 0; y <= 3; y++) {
							plan.set(x, y, -7, y == 3 ? DungeonPart.ACCENT : DungeonPart.PILLAR);
						}
						plan.set(x, 4, -7, DungeonPart.SLAB);
						ground(x, -7);
					}
					plan.set(-3, 0, -3, DungeonPart.DECORATED_POT);
					plan.set(3, 0, -3, DungeonPart.DECORATED_POT);
					plan.set(-3, 0, 6, DungeonPart.SKULL, Dir.EAST);
				}
			}
		}

		private void gableRoof(int wallTop) {
			int base = wallTop + 1;
			for (int t = 0; t <= 4; t++) {
				int y = base + t;
				for (int z = -5; z <= 8; z++) {
					plan.set(-5 + t, y, z, DungeonPart.STAIRS, Dir.EAST);
					plan.set(5 - t, y, z, DungeonPart.STAIRS, Dir.WEST);
					for (int x = -4 + t; x <= 4 - t; x++) {
						if (z == -5 || z == 8 || x == -5 + t || x == 5 - t) {
							continue;
						}
						plan.set(x, y, z, z == -4 || z == 7 || t > 0 ? DungeonPart.WALL : DungeonPart.PLANKS);
					}
				}
			}
			for (int z = -5; z <= 8; z++) {
				plan.set(0, base + 5, z, DungeonPart.SLAB);
			}
			// beirais de degrau nas pontas e a cruz/sino da fachada
			plan.set(0, base + 3, -5, DungeonPart.ACCENT);
			plan.set(0, base + 4, -5, DungeonPart.ACCENT);
			plan.set(0, base + 6, -4, DungeonPart.STONE_WALL);
		}

		private void windows(int wallTop) {
			for (int z : new int[] {0, 4}) {
				for (int y = 1; y <= 2; y++) {
					plan.set(-4, y, z, DungeonPart.IRON_BARS);
					plan.set(4, y, z, DungeonPart.IRON_BARS);
				}
			}
		}

		/** Contrafortes nas paredes laterais, entre as janelas. */
		private void buttresses(int wallTop) {
			for (int z : new int[] {-2, 2, 6}) {
				for (int side : new int[] {-1, 1}) {
					int x = side * 5;
					for (int y = 0; y < wallTop - 1; y++) {
						plan.set(x, y, z, y == 0 ? DungeonPart.ACCENT : DungeonPart.PILLAR);
					}
					plan.set(x, wallTop - 1, z, DungeonPart.STAIRS, side < 0 ? Dir.EAST : Dir.WEST);
					ground(x, z);
				}
			}
		}

		private void graveyard() {
			int[][] stones = {{-6, -7}, {-6, -3}, {-6, 1}, {-6, 5}, {6, -7}, {6, -3}, {6, 1}, {6, 5}, {-3, 9}, {0, 9}, {3, 9}};
			for (int[] s : stones) {
				if (rnd.nextInt(4) == 0) {
					continue;
				}
				plan.set(s[0], 0, s[1], DungeonPart.STONE_WALL);
				ground(s[0], s[1]);
				if (rnd.nextBoolean()) {
					plan.set(s[0], 1, s[1], rnd.nextBoolean() ? DungeonPart.CANDLE : DungeonPart.SKULL, Dir.NORTH);
				}
				int dz = s[1] >= 9 ? -1 : 0;
				int dx = s[1] >= 9 ? 0 : -Integer.signum(s[0]);
				if (plan.get(s[0] + dx, -1, s[1] + dz) == null) {
					plan.set(s[0] + dx, -1, s[1] + dz, DungeonPart.SAND);
				}
			}
		}

		/** Chao firme (ladrilho de borda) sob um enfeite do patio. */
		private void ground(int x, int z) {
			DungeonPart below = plan.get(x, -1, z);
			if (below == null || below == DungeonPart.AIR) {
				plan.set(x, -1, z, DungeonPart.FLOOR_TRIM);
			}
		}

		private void flatRoof(int wallTop) {
			int y = wallTop + 1;
			for (int x = -5; x <= 5; x++) {
				for (int z = -5; z <= 8; z++) {
					boolean edge = x == -5 || x == 5 || z == -5 || z == 8;
					plan.set(x, y, z, edge ? DungeonPart.ACCENT : DungeonPart.WALL);
					if (edge && (x + z) % 2 == 0) {
						plan.set(x, y + 1, z, DungeonPart.WALL);
					} else if (edge) {
						plan.set(x, y + 1, z, DungeonPart.STONE_WALL);
					}
				}
			}
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 7; z++) {
					boolean edge = x == -4 || x == 4 || z == -4 || z == 7;
					boolean corner = (x == -4 || x == 4) && (z == -4 || z == 7);
					if (edge && !corner) {
						plan.set(x, wallTop, z, DungeonPart.ACCENT);
					}
				}
			}
			// a linha de cima da parede vira cornija; o vao da porta continua aberto
			for (int x = -1; x <= 1; x++) {
				plan.set(x, wallTop, -4, DungeonPart.WALL);
			}
		}

		private void domeRoof(int wallTop) {
			int base = wallTop + 1;
			for (int t = 0; t <= 3; t++) {
				int y = base + t;
				int rx = 5 - t;
				int zs = -5 + t;
				int ze = 8 - t;
				for (int x = -rx; x <= rx; x++) {
					for (int z = zs; z <= ze; z++) {
						boolean edge = x == -rx || x == rx || z == zs || z == ze;
						if (!edge && t < 3) {
							continue;
						}
						if (rnd.nextInt(12) == 0 && t > 0) {
							continue; // telhado quebrado
						}
						plan.set(x, y, z, edge ? (t == 0 ? DungeonPart.SLAB : DungeonPart.WALL) : DungeonPart.ACCENT);
					}
				}
			}
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 7; z++) {
					if (plan.get(x, base, z) == null || plan.get(x, base, z) == DungeonPart.AIR) {
						plan.set(x, base, z, DungeonPart.WALL);
					}
				}
			}
			plan.set(0, base + 4, 1, DungeonPart.GLOW);
		}

		private void pyramidRoof(int wallTop) {
			for (int t = 0; t <= 4; t++) {
				int y = wallTop + 1 + t;
				int rx = 5 - t;
				int zs = -5 + t;
				int ze = 8 - t;
				for (int x = -rx; x <= rx; x++) {
					for (int z = zs; z <= ze; z++) {
						boolean edge = x == -rx || x == rx || z == zs || z == ze;
						plan.set(x, y, z, edge && t % 2 == 0 ? DungeonPart.FLOOR_TRIM : DungeonPart.WALL);
					}
				}
			}
			plan.set(0, wallTop + 6, 1, DungeonPart.ACCENT);
			plan.set(0, wallTop + 6, 2, DungeonPart.ACCENT);
			// a parede (3 de altura) ganha uma faixa de terracota
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 7; z++) {
					boolean edge = x == -4 || x == 4 || z == -4 || z == 7;
					if (edge && plan.get(x, 2, z) == DungeonPart.WALL) {
						plan.set(x, 2, z, DungeonPart.FLOOR_TRIM);
					}
				}
			}
		}
	}
}
