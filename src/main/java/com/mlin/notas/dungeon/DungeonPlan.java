package com.mlin.notas.dungeon;

import com.mlin.notas.ruins.RuinPlan;
import com.mlin.notas.ruins.RuinPlan.Dir;
import com.mlin.notas.util.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resultado do {@link DungeonPlanner}: blocos por posicao relativa (origem = centro do predio da entrada; y=0 = pes
 * no chao da superficie, y=-1 = chao), direcoes, salas, baus com o tipo de loot, geradores, atalhos, enigma e
 * armadilhas. Logica pura.
 *
 * <p>Fora do plano o terreno e considerado firme: y &lt; 0 e rocha, y &ge; 0 e ar.</p>
 */
public final class DungeonPlan {
	public enum RoomType {
		/** Primeira sala, no fim da escadaria que desce do predio da superficie. */
		ENTRY,
		/** Sala com a escada que desce para o andar de baixo. */
		STAIRS_DOWN,
		/** Sala onde a escada chega no andar de baixo. */
		LANDING,
		HALL,
		LIBRARY,
		ARMORY,
		/** Sala do tema: tumulos (cripta), forja (anoes), santuario alagado (afogados), sarcofagos (areia). */
		SHRINE,
		SPAWNER,
		/** Nicho com bau atras de porta de ferro que abre com a placa de madeira ligada por redstone. */
		PUZZLE,
		/** Duas fileiras de placas com ejetores de flechas nas paredes. */
		TRAP,
		STORE,
		/** Sala do mini-chefe, com o portao selado da camara final. */
		BOSS,
		/** Camara final: cofre (um premio por jogador) e bau final. */
		VAULT
	}

	/**
	 * Sala: interior de {@code x0..x1} por {@code z0..z1}, chao em {@code fy} (ar de fy+1 a fy+h). {@code depth} =
	 * quantas portas da entrada ate ela pelo caminho normal (sem atalhos).
	 */
	public record Room(int id, RoomType type, int floor, int ci, int cj, int x0, int z0, int x1, int z1, int fy, int h, int depth) {
		public int cx() {
			return (x0 + x1) / 2;
		}

		public int cz() {
			return (z0 + z1) / 2;
		}

		public boolean contains(int x, int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}

		/** Ponto no chao no meio da sala (altura dos pes). */
		public Pos center() {
			return new Pos(cx(), fy + 1, cz());
		}
	}

	/** Atalho: porta de ferro entre duas salas que so abre pela alavanca do lado de dentro (sala mais funda). */
	public record Shortcut(Pos door, Pos lever, int innerRoom, int outerRoom) {
	}

	/** Enigma: placa de madeira ligada por redstone a porta de ferro do nicho com bau; botao do lado de dentro. */
	public record Puzzle(Pos plate, Pos door, Pos chest, Pos button) {
	}

	/** Armadilha: placas de pressao e os ejetores que elas disparam. */
	public record Trap(List<Pos> plates, List<Pos> dispensers) {
	}

	public static final String MARKER_ENTRANCE = "entrance";
	public static final String MARKER_BOSS = "boss";
	public static final String MARKER_VAULT = "vault";
	public static final String MARKER_GATE = "gate";
	public static final String MARKER_FINAL_CHEST = "final_chest";

	public static final String LOOT_COMMON = "dungeon_common";
	public static final String LOOT_LIBRARY = "dungeon_library";
	public static final String LOOT_ARMORY = "dungeon_armory";
	public static final String LOOT_SECRET = "dungeon_secret";
	public static final String LOOT_FINAL = "dungeon_final";
	/** Tabela do cofre (sorteada por jogador). */
	public static final String LOOT_VAULT = "dungeon_vault";

	private final DungeonTheme theme;
	private final Map<Long, DungeonPart> blocks = new LinkedHashMap<>();
	private final Map<Long, Dir> facing = new LinkedHashMap<>();
	private final Map<String, Pos> markers = new LinkedHashMap<>();
	private final Map<Long, String> chests = new LinkedHashMap<>();
	private final Map<Long, String> spawners = new LinkedHashMap<>();
	private final List<Room> rooms = new ArrayList<>();
	private final List<Shortcut> shortcuts = new ArrayList<>();
	private final List<Trap> traps = new ArrayList<>();
	private Puzzle puzzle;
	private int floors;

	DungeonPlan(DungeonTheme theme) {
		this.theme = theme;
	}

	public static long key(int x, int y, int z) {
		return RuinPlan.key(x, y, z);
	}

	public static int keyX(long key) {
		return RuinPlan.keyX(key);
	}

	public static int keyY(long key) {
		return RuinPlan.keyY(key);
	}

	public static int keyZ(long key) {
		return RuinPlan.keyZ(key);
	}

	// --- construcao (planner) ---

	void set(int x, int y, int z, DungeonPart part) {
		long k = key(x, y, z);
		blocks.put(k, part);
		facing.remove(k);
	}

	void set(int x, int y, int z, DungeonPart part, Dir dir) {
		long k = key(x, y, z);
		blocks.put(k, part);
		if (dir == null) {
			facing.remove(k);
		} else {
			facing.put(k, dir);
		}
	}

	void setIfAbsent(int x, int y, int z, DungeonPart part) {
		blocks.putIfAbsent(key(x, y, z), part);
	}

	void mark(String name, int x, int y, int z) {
		markers.put(name, new Pos(x, y, z));
	}

	void chest(int x, int y, int z, Dir dir, String loot) {
		set(x, y, z, DungeonPart.CHEST, dir);
		chests.put(key(x, y, z), loot);
	}

	void spawner(int x, int y, int z, String mob) {
		set(x, y, z, DungeonPart.SPAWNER);
		spawners.put(key(x, y, z), mob);
	}

	void addRoom(Room room) {
		rooms.add(room);
	}

	void replaceRoom(int index, Room room) {
		rooms.set(index, room);
	}

	void addShortcut(Shortcut shortcut) {
		shortcuts.add(shortcut);
	}

	void addTrap(Trap trap) {
		traps.add(trap);
	}

	void puzzle(Puzzle puzzle) {
		this.puzzle = puzzle;
	}

	void floors(int floors) {
		this.floors = floors;
	}

	// --- leitura ---

	public DungeonTheme theme() {
		return theme;
	}

	public Map<Long, DungeonPart> blocks() {
		return Collections.unmodifiableMap(blocks);
	}

	public DungeonPart get(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	public Dir facing(int x, int y, int z) {
		return facing.get(key(x, y, z));
	}

	public Map<String, Pos> markers() {
		return Collections.unmodifiableMap(markers);
	}

	public Pos marker(String name) {
		return markers.get(name);
	}

	/** Baus: posicao -> tabela de loot ({@code mlinnotas:chests/<valor>}). */
	public Map<Long, String> chests() {
		return Collections.unmodifiableMap(chests);
	}

	/** Geradores: posicao -> id do mob. */
	public Map<Long, String> spawners() {
		return Collections.unmodifiableMap(spawners);
	}

	public List<Room> rooms() {
		return Collections.unmodifiableList(rooms);
	}

	public Room room(RoomType type) {
		return rooms.stream().filter(r -> r.type() == type).findFirst().orElse(null);
	}

	public List<Shortcut> shortcuts() {
		return Collections.unmodifiableList(shortcuts);
	}

	public List<Trap> traps() {
		return Collections.unmodifiableList(traps);
	}

	public Puzzle puzzle() {
		return puzzle;
	}

	public int floors() {
		return floors;
	}

	public List<Pos> positionsOf(DungeonPart part) {
		List<Pos> out = new ArrayList<>();
		for (Map.Entry<Long, DungeonPart> e : blocks.entrySet()) {
			if (e.getValue() == part) {
				long k = e.getKey();
				out.add(new Pos(keyX(k), keyY(k), keyZ(k)));
			}
		}
		return out;
	}

	public int count(DungeonPart part) {
		int n = 0;
		for (DungeonPart p : blocks.values()) {
			if (p == part) {
				n++;
			}
		}
		return n;
	}

	/** {minX, minY, minZ, maxX, maxY, maxZ} de todos os blocos do plano. */
	public int[] bounds() {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (long k : blocks.keySet()) {
			b[0] = Math.min(b[0], keyX(k));
			b[1] = Math.min(b[1], keyY(k));
			b[2] = Math.min(b[2], keyZ(k));
			b[3] = Math.max(b[3], keyX(k));
			b[4] = Math.max(b[4], keyY(k));
			b[5] = Math.max(b[5], keyZ(k));
		}
		return b;
	}

	// --- caminhabilidade (para os testes e para escolher lugares) ---

	/** Portas do caminho: o que conta como aberto na busca a pe. */
	public record Doors(boolean gateOpen, boolean ironDoorsOpen) {
		public static final Doors CLOSED = new Doors(false, false);
		public static final Doors KEY = new Doors(true, false);
		public static final Doors ALL = new Doors(true, true);
	}

	public boolean passable(int x, int y, int z, Doors doors) {
		DungeonPart part = get(x, y, z);
		if (part == null) {
			return y >= 0;
		}
		if (part == DungeonPart.GATE) {
			return doors.gateOpen();
		}
		if (part == DungeonPart.IRON_DOOR || part == DungeonPart.IRON_DOOR_TOP) {
			return doors.ironDoorsOpen();
		}
		return part.passable();
	}

	public boolean supports(int x, int y, int z) {
		DungeonPart part = get(x, y, z);
		if (part == null) {
			return y < 0;
		}
		return part.solid() && part != DungeonPart.IRON_DOOR_TOP;
	}

	public boolean standable(int x, int y, int z, Doors doors) {
		return passable(x, y, z, doors) && passable(x, y + 1, z, doors) && supports(x, y - 1, z)
				&& get(x, y - 1, z) != DungeonPart.LAVA && get(x, y, z) != DungeonPart.LAVA;
	}

	/**
	 * Posicoes (pes) alcancaveis a pe a partir de {@code start}: anda para os lados, sobe 1 (com espaco para o pulo)
	 * e desce ate 3. A busca fica dentro da caixa do plano (+2).
	 */
	public Set<Long> reachableFrom(Pos start, Doors doors) {
		int[] b = bounds();
		Set<Long> seen = new HashSet<>();
		Deque<long[]> queue = new ArrayDeque<>();
		if (!standable(start.x(), start.y(), start.z(), doors)) {
			return seen;
		}
		seen.add(key(start.x(), start.y(), start.z()));
		queue.add(new long[] {start.x(), start.y(), start.z()});
		while (!queue.isEmpty()) {
			long[] p = queue.poll();
			int x = (int) p[0];
			int y = (int) p[1];
			int z = (int) p[2];
			for (Dir d : Dir.values()) {
				int nx = x + d.dx;
				int nz = z + d.dz;
				if (nx < b[0] - 2 || nx > b[3] + 2 || nz < b[2] - 2 || nz > b[5] + 2) {
					continue;
				}
				// subir 1: precisa de espaco para o pulo na origem
				if (passable(x, y + 2, z, doors) && standable(nx, y + 1, nz, doors)) {
					visit(seen, queue, nx, y + 1, nz);
				}
				if (!passable(nx, y, nz, doors) || !passable(nx, y + 1, nz, doors)) {
					continue;
				}
				for (int dy = 0; dy >= -3; dy--) {
					if (dy < 0 && !passable(nx, y + dy, nz, doors)) {
						break;
					}
					if (standable(nx, y + dy, nz, doors)) {
						visit(seen, queue, nx, y + dy, nz);
						break;
					}
				}
			}
		}
		return seen;
	}

	private static void visit(Set<Long> seen, Deque<long[]> queue, int x, int y, int z) {
		if (seen.add(key(x, y, z))) {
			queue.add(new long[] {x, y, z});
		}
	}

	/** Da para usar o bloco em (x, y, z) (bau, cofre, alavanca) de alguma posicao alcancada ao lado ou em cima. */
	public static boolean usableFrom(Set<Long> reach, int x, int y, int z) {
		for (int dy = -2; dy <= 1; dy++) {
			for (Dir d : Dir.values()) {
				if (reach.contains(key(x + d.dx, y + dy, z + d.dz))) {
					return true;
				}
			}
			if (dy != 0 && reach.contains(key(x, y + dy, z))) {
				return true;
			}
		}
		return false;
	}

	/** Partes que precisam de apoio (chao, teto ou parede atras) e nao tem. */
	public List<Pos> floating() {
		List<Pos> out = new ArrayList<>();
		for (Map.Entry<Long, DungeonPart> e : blocks.entrySet()) {
			long k = e.getKey();
			int x = keyX(k);
			int y = keyY(k);
			int z = keyZ(k);
			DungeonPart part = e.getValue();
			boolean ok = switch (part.support()) {
				case NONE -> true;
				case FLOOR -> supports(x, y - 1, z);
				case CEILING -> {
					DungeonPart above = get(x, y + 1, z);
					yield above == null ? y + 1 < 0 : above.solid() || above == DungeonPart.CHAIN;
				}
				case WALL -> {
					Dir dir = facing(x, y, z);
					if (dir == null) {
						yield false;
					}
					DungeonPart behind = get(x - dir.dx, y, z - dir.dz);
					yield behind == null ? y < 0 : behind.solid();
				}
			};
			if (!ok) {
				out.add(new Pos(x, y, z));
			}
		}
		return out;
	}
}
