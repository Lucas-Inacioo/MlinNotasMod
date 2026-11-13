package com.mlin.notas.vstructures;

import com.mlin.notas.util.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plano de uma estrutura vanilla refeita: estado de bloco por posicao relativa (texto no formato do comando
 * {@code /setblock}, ex. {@code minecraft:oak_stairs[facing=north,half=top]}), bandeiras de colocacao, baus/despensers
 * com loot, geradores de monstros, areia suspeita, entidades e marcadores. Logica pura: a peca ({@code VanillaRemakePiece})
 * traduz os textos em blocos, gira/espelha e coloca.
 *
 * <p>Modelo do terreno para os testes: na superficie ({@link #underground()} falso) o que nao esta no plano e chao
 * abaixo de y=0 e ar de y=0 para cima; no subterraneo tudo fora do plano e rocha.
 */
public final class VPlan {
	/** Na colocacao, preenche para baixo com o mesmo bloco ate achar chao firme (alicerces, pilares). */
	public static final int FILL_DOWN = 1;
	/** So coloca se o lugar estiver vazio (ar, liquido, planta): pontes de tabua, chao sobre cavernas. */
	public static final int IF_AIR = 2;
	/** Bloco que nao precisa ligar a estrutura (enfeite solto, entulho): e conferido so por ter apoio embaixo. */
	public static final int LOOSE = 4;
	/** So coloca se o lugar tiver um bloco solido (veios de minerio nas paredes da mina). */
	public static final int IF_SOLID = 8;
	/** Como {@link #FILL_DOWN}, mas preenche com terra (arenito sob areia): chao de acampamento sobre terreno irregular. */
	public static final int FILL_SOIL = 16;
	/** Como {@link #FILL_DOWN}, mas desce ate {@code VanillaRemakePiece.DEEP_FILL_DEPTH} (pilares de ponte sobre o mar de lava). */
	public static final int FILL_DEEP = 32;

	public static final String AIR = "minecraft:air";
	public static final String CAVE_AIR = "minecraft:cave_air";

	public static final String MARKER_ENTRANCE = "entrance";

	/** Entidade criada junto com a estrutura. {@code tag}: detalhe tratado pela peca (ex. {@code captain}, loot do carrinho). */
	public record Spawn(String type, double x, double y, double z, String tag) {
	}

	private final boolean underground;
	private final Map<Long, String> blocks = new LinkedHashMap<>();
	private final Map<Long, Integer> flags = new HashMap<>();
	private final Map<Long, String> loot = new LinkedHashMap<>();
	private final Map<Long, String> spawners = new LinkedHashMap<>();
	private final Map<Long, String> archaeology = new LinkedHashMap<>();
	private final Map<Long, Integer> groups = new HashMap<>();
	private final Map<Long, String> tags = new HashMap<>();
	private final List<Spawn> entities = new ArrayList<>();
	private final Map<String, Pos> markers = new LinkedHashMap<>();
	private int group;

	public VPlan(boolean underground) {
		this.underground = underground;
	}

	public static long key(int x, int y, int z) {
		return ((long) (x + 2048) << 36) | ((long) (y + 2048) << 18) | (z + 2048);
	}

	public static int keyX(long key) {
		return (int) (key >>> 36) - 2048;
	}

	public static int keyY(long key) {
		return (int) ((key >>> 18) & 0x3FFFF) - 2048;
	}

	public static int keyZ(long key) {
		return (int) (key & 0x3FFFF) - 2048;
	}

	/** Texto de estado: {@code s("oak_stairs", "facing=north")} -> {@code minecraft:oak_stairs[facing=north]}. */
	public static String s(String id, String... props) {
		String full = id.contains(":") ? id : "minecraft:" + id;
		return props.length == 0 ? full : full + "[" + String.join(",", props) + "]";
	}

	/** Id do bloco (sem namespace nem propriedades). */
	public static String id(String state) {
		int colon = state.indexOf(':');
		int bracket = state.indexOf('[');
		return state.substring(colon + 1, bracket < 0 ? state.length() : bracket);
	}

	/** Valor de uma propriedade do texto de estado, ou {@code null}. */
	public static String prop(String state, String name) {
		int bracket = state.indexOf('[');
		if (bracket < 0) {
			return null;
		}
		for (String pair : state.substring(bracket + 1, state.length() - 1).split(",")) {
			int eq = pair.indexOf('=');
			if (pair.substring(0, eq).equals(name)) {
				return pair.substring(eq + 1);
			}
		}
		return null;
	}

	// --- construcao ---

	public boolean underground() {
		return underground;
	}

	public void set(int x, int y, int z, String state) {
		long k = key(x, y, z);
		blocks.put(k, state);
		flags.remove(k);
		if (group != 0) {
			groups.put(k, group);
		}
	}

	public void set(int x, int y, int z, String state, int flag) {
		set(x, y, z, state);
		if (flag != 0) {
			flags.put(key(x, y, z), flag);
		}
	}

	public void setIfAbsent(int x, int y, int z, String state) {
		if (!blocks.containsKey(key(x, y, z))) {
			set(x, y, z, state);
		}
	}

	public void remove(int x, int y, int z) {
		long k = key(x, y, z);
		blocks.remove(k);
		flags.remove(k);
		loot.remove(k);
		spawners.remove(k);
		archaeology.remove(k);
		groups.remove(k);
	}

	public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, state);
				}
			}
		}
	}

	public void air(int x1, int y1, int z1, int x2, int y2, int z2) {
		fill(x1, y1, z1, x2, y2, z2, underground ? CAVE_AIR : AIR);
	}

	public void flag(int x, int y, int z, int flag) {
		flags.merge(key(x, y, z), flag, (a, b) -> a | b);
	}

	/** Bau, barril ou despenser com loot (id da loot table). */
	public void loot(int x, int y, int z, String state, String table) {
		set(x, y, z, state);
		loot.put(key(x, y, z), table);
	}

	public void spawner(int x, int y, int z, String entity) {
		set(x, y, z, "minecraft:spawner");
		spawners.put(key(x, y, z), entity);
	}

	/** Areia/cascalho suspeito com loot de arqueologia. */
	public void suspicious(int x, int y, int z, String state, String table) {
		set(x, y, z, state);
		archaeology.put(key(x, y, z), table);
	}

	/** Detalhe extra do bloco tratado pela peca (ex. {@code ominous} = estandarte de saqueador). */
	public void tag(int x, int y, int z, String tag) {
		tags.put(key(x, y, z), tag);
	}

	public Map<Long, String> tagMap() {
		return Collections.unmodifiableMap(tags);
	}

	public void entity(String type, double x, double y, double z, String tag) {
		entities.add(new Spawn(type, x, y, z, tag));
	}

	public void mark(String name, int x, int y, int z) {
		markers.put(name, new Pos(x, y, z));
	}

	/** Os blocos colocados daqui em diante pertencem a um trecho novo (a peca pula trechos que caem em liquido). */
	public int newGroup() {
		return ++group;
	}

	public void endGroup() {
		group = 0;
	}

	// --- consulta ---

	public String get(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	public int flags(int x, int y, int z) {
		return flags.getOrDefault(key(x, y, z), 0);
	}

	public Map<Long, String> blocks() {
		return Collections.unmodifiableMap(blocks);
	}

	public Map<Long, Integer> flagMap() {
		return Collections.unmodifiableMap(flags);
	}

	public Map<Long, String> lootTables() {
		return Collections.unmodifiableMap(loot);
	}

	public Map<Long, String> spawnerMap() {
		return Collections.unmodifiableMap(spawners);
	}

	public Map<Long, String> archaeologyMap() {
		return Collections.unmodifiableMap(archaeology);
	}

	public Map<Long, Integer> groupMap() {
		return Collections.unmodifiableMap(groups);
	}

	public List<Spawn> entities() {
		return Collections.unmodifiableList(entities);
	}

	public Map<String, Pos> markers() {
		return Collections.unmodifiableMap(markers);
	}

	public Pos marker(String name) {
		return markers.get(name);
	}

	public List<Pos> positionsOf(String blockId) {
		List<Pos> out = new ArrayList<>();
		for (Map.Entry<Long, String> e : blocks.entrySet()) {
			if (id(e.getValue()).equals(blockId)) {
				long k = e.getKey();
				out.add(new Pos(keyX(k), keyY(k), keyZ(k)));
			}
		}
		return out;
	}

	public long count(String blockId) {
		return blocks.values().stream().filter(s -> id(s).equals(blockId)).count();
	}

	/** {min x, min y, min z, max x, max y, max z}. */
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

	/** {min x, min z, max x, max z} do que fica na superficie (y &ge; -1, sem ar). */
	public int[] surfaceBounds() {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (Map.Entry<Long, String> e : blocks.entrySet()) {
			long k = e.getKey();
			if (keyY(k) >= -1 && !BlockTraits.isAir(e.getValue())) {
				b[0] = Math.min(b[0], keyX(k));
				b[1] = Math.min(b[1], keyZ(k));
				b[2] = Math.max(b[2], keyX(k));
				b[3] = Math.max(b[3], keyZ(k));
			}
		}
		return b;
	}

	// --- andar a pe ---

	/** Ocupa o espaco (o jogador nao atravessa). */
	public boolean solid(int x, int y, int z) {
		String s = get(x, y, z);
		if (s == null) {
			return underground || y < 0;
		}
		return BlockTraits.obstacle(s);
	}

	private boolean supports(int x, int y, int z) {
		String s = get(x, y, z);
		if (s == null) {
			return underground || y < 0;
		}
		return BlockTraits.standable(s);
	}

	/** Pisar aqui dispara uma armadilha (placa de pressao, fio de armadilha). */
	public boolean trap(int x, int y, int z) {
		String s = get(x, y, z);
		return s != null && BlockTraits.trigger(s);
	}

	public boolean validFeet(int x, int y, int z) {
		if (solid(x, y, z) || solid(x, y + 1, z)) {
			return false;
		}
		String here = get(x, y, z);
		return (here != null && BlockTraits.climbable(here)) || supports(x, y - 1, z);
	}

	/**
	 * Pes alcancaveis a pe a partir de {@code (sx, sy, sz)}: anda, sobe 1 pulando, cai ate 3, usa escadas de mao e
	 * trepadeiras. Com {@code avoidTraps} nao pisa em placas de pressao nem em fios de armadilha.
	 */
	public Set<Long> reachableFrom(int sx, int sy, int sz, boolean avoidTraps) {
		Set<Long> seen = new HashSet<>();
		Deque<int[]> queue = new ArrayDeque<>();
		int[] b = bounds();
		int minX = Math.min(b[0], sx) - 3;
		int maxX = Math.max(b[3], sx) + 3;
		int minZ = Math.min(b[2], sz) - 3;
		int maxZ = Math.max(b[5], sz) + 3;
		if (validFeet(sx, sy, sz)) {
			seen.add(key(sx, sy, sz));
			queue.add(new int[] {sx, sy, sz});
		}
		int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			int x = p[0];
			int y = p[1];
			int z = p[2];
			for (int[] d : dirs) {
				int nx = x + d[0];
				int nz = z + d[1];
				if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
					continue;
				}
				if (!solid(nx, y, nz) && !solid(nx, y + 1, nz)) {
					for (int k = 0; k <= 3; k++) {
						if (k > 0 && solid(nx, y - k, nz)) {
							break;
						}
						if (validFeet(nx, y - k, nz)) {
							visit(seen, queue, nx, y - k, nz, avoidTraps);
							break;
						}
					}
				} else if (!solid(x, y + 2, z) && validFeet(nx, y + 1, nz)) {
					visit(seen, queue, nx, y + 1, nz, avoidTraps);
				}
			}
			String here = get(x, y, z);
			if (here != null && BlockTraits.climbable(here)) {
				if (validFeet(x, y + 1, z)) {
					visit(seen, queue, x, y + 1, z, avoidTraps);
				}
				if (validFeet(x, y - 1, z)) {
					visit(seen, queue, x, y - 1, z, avoidTraps);
				}
			}
		}
		return seen;
	}

	private void visit(Set<Long> seen, Deque<int[]> queue, int x, int y, int z, boolean avoidTraps) {
		if (avoidTraps && trap(x, y, z)) {
			return;
		}
		if (seen.add(key(x, y, z))) {
			queue.add(new int[] {x, y, z});
		}
	}

	/** Da para abrir/usar o bloco em {@code (x, y, z)} de alguma posicao alcancavel ao lado, em cima ou embaixo. */
	public static boolean usableFrom(Set<Long> reach, int x, int y, int z) {
		int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		for (int[] d : dirs) {
			for (int dy = -2; dy <= 1; dy++) {
				if (reach.contains(key(x + d[0], y + dy, z + d[1]))) {
					return true;
				}
			}
		}
		return reach.contains(key(x, y + 1, z)) || reach.contains(key(x, y - 2, z));
	}

	// --- apoio ---

	private boolean anchored(int x, int y, int z) {
		String s = get(x, y, z);
		if (s == null) {
			return underground || y < 0;
		}
		return !BlockTraits.isAir(s);
	}

	/**
	 * Blocos que ficariam soltos: nao ligados (pelos 6 vizinhos) ao terreno nem a algo que desce ate o chao
	 * ({@link #FILL_DOWN}/{@link #IF_AIR}). Ar nao conta.
	 */
	public Set<Long> floating() {
		Set<Long> supported = new HashSet<>();
		Deque<Long> queue = new ArrayDeque<>();
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (Map.Entry<Long, String> e : blocks.entrySet()) {
			if (BlockTraits.isAir(e.getValue())) {
				continue;
			}
			long k = e.getKey();
			int x = keyX(k);
			int y = keyY(k);
			int z = keyZ(k);
			boolean grounded = (flags.getOrDefault(k, 0) & (FILL_DOWN | FILL_SOIL | FILL_DEEP)) != 0;
			for (int[] d : dirs) {
				if (!grounded && get(x + d[0], y + d[1], z + d[2]) == null && anchored(x + d[0], y + d[1], z + d[2])) {
					grounded = true;
				}
			}
			if (grounded && supported.add(k)) {
				queue.add(k);
			}
		}
		while (!queue.isEmpty()) {
			long k = queue.poll();
			for (int[] d : dirs) {
				long n = key(keyX(k) + d[0], keyY(k) + d[1], keyZ(k) + d[2]);
				String s = blocks.get(n);
				if (s != null && !BlockTraits.isAir(s) && supported.add(n)) {
					queue.add(n);
				}
			}
		}
		Set<Long> out = new HashSet<>();
		for (Map.Entry<Long, String> e : blocks.entrySet()) {
			if (!BlockTraits.isAir(e.getValue()) && !supported.contains(e.getKey())) {
				out.add(e.getKey());
			}
		}
		return out;
	}

	/** Blocos que precisam de apoio (tocha, trilho, placa, escada de mao, alavanca, planta...) e nao tem. */
	public List<String> unsupported() {
		List<String> out = new ArrayList<>();
		for (Map.Entry<Long, String> e : blocks.entrySet()) {
			String s = e.getValue();
			long k = e.getKey();
			int x = keyX(k);
			int y = keyY(k);
			int z = keyZ(k);
			int[] need = BlockTraits.supportOffset(s);
			if (need == null) {
				continue;
			}
			int sx = x + need[0];
			int sy = y + need[1];
			int sz = z + need[2];
			String support = get(sx, sy, sz);
			boolean ok = support == null ? (underground || sy < 0) : BlockTraits.sturdy(support) || BlockTraits.acceptsHanging(support, need)
					|| (need[1] == -1 && BlockTraits.topFace(support, s));
			if (!ok) {
				out.add(s + " em " + x + "," + y + "," + z + " (apoio: " + support + ")");
			}
		}
		return out;
	}

	/** Troca por ar o que ficaria solto no ar (entulho sem apoio). */
	public void removeFloating() {
		for (long k : floating()) {
			blocks.put(k, underground ? CAVE_AIR : AIR);
			flags.remove(k);
			loot.remove(k);
			spawners.remove(k);
			archaeology.remove(k);
		}
	}
}
