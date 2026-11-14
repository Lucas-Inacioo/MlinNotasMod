package com.mlin.notas.end;

import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.BlockTraits;
import com.mlin.notas.vstructures.VPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Resultado do {@link CitadelPlanner}: estado de bloco por posicao relativa (texto no formato do {@code /setblock},
 * ex. {@code minecraft:purpur_stairs[facing=north,half=top]}; origem = centro da ilha, y=0 = topo do patio), baus com
 * loot, criaturas/entidades, torres e corpos flutuantes intencionais. Logica pura: a peca ({@code CitadelPiece}) traduz
 * os textos em blocos.
 *
 * <p>Tudo que nao esta no plano e vazio (a cidadela fica sobre o vazio do End).
 */
public final class CitadelPlan {
	/** Entidade a criar. {@code dirX/dirZ}: para onde ela olha (moldura). */
	public record Spawn(Kind kind, int x, int y, int z, int dirX, int dirZ) {
	}

	public enum Kind {
		SHULKER,
		SENTINEL,
		ELYTRA_FRAME
	}

	/** Uma torre com coluna de elevadores: nome, centro, meia largura (no terreo) e andares. */
	public record Tower(String name, int x, int z, int half, int floors) {
		public int floorY(int floor) {
			return floor * CitadelPlanner.FLOOR_HEIGHT;
		}

		/** Y da laje que cobre o ultimo andar. */
		public int roofY() {
			return floors * CitadelPlanner.FLOOR_HEIGHT;
		}
	}

	public static final String CHEST_LOOT = "minecraft:chests/end_city_treasure";

	private final Map<Long, String> blocks = new HashMap<>();
	private final Map<Long, String> loot = new LinkedHashMap<>();
	private final List<Spawn> spawns = new ArrayList<>();
	private final List<Tower> towers = new ArrayList<>();
	private final List<Pos> floatingBodies = new ArrayList<>();
	private final Map<String, Pos> markers = new LinkedHashMap<>();
	private final int radius;

	CitadelPlan(int radius) {
		this.radius = radius;
	}

	public static long key(int x, int y, int z) {
		return VPlan.key(x, y, z);
	}

	public static int keyX(long key) {
		return VPlan.keyX(key);
	}

	public static int keyY(long key) {
		return VPlan.keyY(key);
	}

	public static int keyZ(long key) {
		return VPlan.keyZ(key);
	}

	// --- construcao (so o planner) ---

	void set(int x, int y, int z, String state) {
		long k = key(x, y, z);
		blocks.put(k, state);
		loot.remove(k);
	}

	void remove(int x, int y, int z) {
		long k = key(x, y, z);
		blocks.remove(k);
		loot.remove(k);
	}

	void chest(int x, int y, int z, String state) {
		set(x, y, z, state);
		loot.put(key(x, y, z), CHEST_LOOT);
	}

	void spawn(Spawn spawn) {
		spawns.add(spawn);
	}

	void tower(Tower tower) {
		towers.add(tower);
	}

	void floatingBody(int x, int y, int z) {
		floatingBodies.add(new Pos(x, y, z));
	}

	void mark(String name, int x, int y, int z) {
		markers.put(name, new Pos(x, y, z));
	}

	// --- consulta ---

	public String get(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	/** Id do bloco (sem namespace nem propriedades) em (x, y, z), ou {@code null}. */
	public String idAt(int x, int y, int z) {
		String s = get(x, y, z);
		return s == null ? null : VPlan.id(s);
	}

	public Map<Long, String> blocks() {
		return Collections.unmodifiableMap(blocks);
	}

	public Map<Long, String> lootTables() {
		return Collections.unmodifiableMap(loot);
	}

	public List<Spawn> spawns() {
		return Collections.unmodifiableList(spawns);
	}

	/** A primeira e a fortaleza central; depois as torres-satelite e o mastro do cais. */
	public List<Tower> towers() {
		return Collections.unmodifiableList(towers);
	}

	public Tower tower(String name) {
		return towers.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
	}

	/** Um ponto de cada rocha que flutua de proposito, solta do resto (destrocos). */
	public List<Pos> floatingBodies() {
		return Collections.unmodifiableList(floatingBodies);
	}

	public Map<String, Pos> markers() {
		return Collections.unmodifiableMap(markers);
	}

	public Pos marker(String name) {
		return markers.get(name);
	}

	/** Raio da ilha principal. */
	public int radius() {
		return radius;
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

	public int minY() {
		return bounds()[1];
	}

	public int maxY() {
		return bounds()[4];
	}

	public long count(Kind kind) {
		return spawns.stream().filter(s -> s.kind() == kind).count();
	}

	public long count(String blockId) {
		return blocks.values().stream().filter(s -> VPlan.id(s).equals(blockId)).count();
	}

	public List<Pos> positionsOf(String blockId) {
		List<Pos> out = new ArrayList<>();
		for (Map.Entry<Long, String> e : blocks.entrySet()) {
			if (VPlan.id(e.getValue()).equals(blockId)) {
				long k = e.getKey();
				out.add(new Pos(keyX(k), keyY(k), keyZ(k)));
			}
		}
		return out;
	}

	// --- tracos dos blocos (BlockTraits + os blocos do mod) ---

	private static final Set<String> MOD_PLANTS = Set.of("void_grass", "void_lily", "end_mushroom");

	public static boolean isAir(String state) {
		return state == null || BlockTraits.isAir(state);
	}

	public static boolean obstacle(String state) {
		if (state == null) {
			return false;
		}
		return !MOD_PLANTS.contains(VPlan.id(state)) && BlockTraits.obstacle(state);
	}

	public static boolean standable(String state) {
		if (state == null || MOD_PLANTS.contains(VPlan.id(state))) {
			return false;
		}
		String id = VPlan.id(state);
		// cabeca de dragao, suporte de pocao, cristal e coro nao servem de piso
		if (id.endsWith("_head") || id.equals("brewing_stand") || id.endsWith("crystal_cluster") || id.equals("chorus_plant")
				|| id.equals("chorus_flower")) {
			return false;
		}
		return BlockTraits.standable(state);
	}

	public boolean solidAt(int x, int y, int z) {
		return obstacle(get(x, y, z));
	}

	public boolean validFeet(int x, int y, int z) {
		if (solidAt(x, y, z) || solidAt(x, y + 1, z)) {
			return false;
		}
		String here = get(x, y, z);
		return (here != null && BlockTraits.climbable(here)) || standable(get(x, y - 1, z));
	}

	private boolean elevator(int x, int y, int z) {
		return "purpur_elevator".equals(idAt(x, y, z));
	}

	/**
	 * Pes alcancaveis a pe a partir de {@code (sx, sy, sz)}: anda, sobe 1 pulando, cai ate 3, sobe/desce escadas de mao
	 * e usa os elevadores de purpur ({@link ElevatorLogic}).
	 */
	public Set<Long> reachableFrom(int sx, int sy, int sz) {
		Set<Long> seen = new HashSet<>();
		Deque<int[]> queue = new ArrayDeque<>();
		int[] b = bounds();
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
				if (nx < b[0] - 2 || nx > b[3] + 2 || nz < b[2] - 2 || nz > b[5] + 2) {
					continue;
				}
				if (!solidAt(nx, y, nz) && !solidAt(nx, y + 1, nz)) {
					for (int k = 0; k <= 3; k++) {
						if (k > 0 && solidAt(nx, y - k, nz)) {
							break;
						}
						if (validFeet(nx, y - k, nz)) {
							visit(seen, queue, nx, y - k, nz);
							break;
						}
					}
				} else if (!solidAt(x, y + 2, z) && validFeet(nx, y + 1, nz)) {
					visit(seen, queue, nx, y + 1, nz);
				}
			}
			String here = get(x, y, z);
			if (here != null && BlockTraits.climbable(here)) {
				if (validFeet(x, y + 1, z)) {
					visit(seen, queue, x, y + 1, z);
				}
				if (validFeet(x, y - 1, z)) {
					visit(seen, queue, x, y - 1, z);
				}
			}
			if (elevator(x, y - 1, z)) {
				for (boolean up : new boolean[] {true, false}) {
					OptionalInt target = ElevatorLogic.target(y - 1, up, ty -> elevator(x, ty, z), ty -> !solidAt(x, ty, z), CitadelPlanner.ELEVATOR_RANGE);
					if (target.isPresent()) {
						visit(seen, queue, x, target.getAsInt() + 1, z);
					}
				}
			}
		}
		return seen;
	}

	private static void visit(Set<Long> seen, Deque<int[]> queue, int x, int y, int z) {
		if (seen.add(key(x, y, z))) {
			queue.add(new int[] {x, y, z});
		}
	}

	/** Da para abrir/usar o bloco em {@code (x, y, z)} de alguma posicao alcancavel ao lado, em cima ou embaixo. */
	public static boolean usableFrom(Set<Long> reach, int x, int y, int z) {
		return VPlan.usableFrom(reach, x, y, z);
	}

	// --- apoio ---

	/**
	 * Componentes ligados (pelos 6 vizinhos) dos blocos que nao sao ar. O primeiro e o que contem o centro da ilha
	 * (0, -1, 0); os outros devem ser os {@link #floatingBodies() corpos flutuantes} intencionais.
	 */
	public List<Set<Long>> components() {
		Set<Long> left = new HashSet<>();
		for (Map.Entry<Long, String> e : blocks.entrySet()) {
			if (!isAir(e.getValue())) {
				left.add(e.getKey());
			}
		}
		List<Set<Long>> out = new ArrayList<>();
		long start = key(0, -1, 0);
		if (left.contains(start)) {
			out.add(flood(left, start));
		}
		while (!left.isEmpty()) {
			out.add(flood(left, left.iterator().next()));
		}
		return out;
	}

	private static Set<Long> flood(Set<Long> left, long start) {
		Set<Long> part = new HashSet<>();
		Deque<Long> queue = new ArrayDeque<>();
		left.remove(start);
		part.add(start);
		queue.add(start);
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			long k = queue.poll();
			for (int[] d : dirs) {
				long n = key(keyX(k) + d[0], keyY(k) + d[1], keyZ(k) + d[2]);
				if (left.remove(n)) {
					part.add(n);
					queue.add(n);
				}
			}
		}
		return part;
	}

	/** Blocos que precisam de apoio (estandarte, escada de mao, planta, corrente...) e nao tem. */
	public List<String> unsupported() {
		List<String> out = new ArrayList<>();
		for (Map.Entry<Long, String> e : blocks.entrySet()) {
			String s = e.getValue();
			long k = e.getKey();
			int x = keyX(k);
			int y = keyY(k);
			int z = keyZ(k);
			String id = VPlan.id(s);
			int[] need;
			if (MOD_PLANTS.contains(id) || id.equals("chorus_plant")) {
				need = new int[] {0, -1, 0};
			} else if (id.equals("chorus_flower") || id.equals("dragon_wall_head")) {
				need = null;
			} else {
				need = BlockTraits.supportOffset(s);
			}
			if (need == null) {
				continue;
			}
			String support = get(x + need[0], y + need[1], z + need[2]);
			String supportId = support == null ? "" : VPlan.id(support);
			boolean ok;
			if (MOD_PLANTS.contains(id)) {
				ok = supportId.equals("end_moss") || supportId.equals("end_stone");
			} else if (id.equals("chorus_plant")) {
				ok = supportId.equals("end_stone") || supportId.equals("chorus_plant");
			} else {
				ok = support != null && !isAir(support) && (BlockTraits.sturdy(support) || hangs(supportId, need)
						|| (need[1] == -1 && (id.endsWith("carpet") || "top".equals(VPlan.prop(support, "type"))
								|| "top".equals(VPlan.prop(support, "half")))));
			}
			if (!ok) {
				out.add(s + " em " + x + "," + y + "," + z + " (apoio: " + support + ")");
			}
		}
		return out;
	}

	private static boolean hangs(String supportId, int[] need) {
		if (need[1] != 1) {
			return false;
		}
		return supportId.endsWith("chain") || supportId.endsWith("_wall") || supportId.endsWith("_slab") || supportId.endsWith("_stairs")
				|| supportId.equals("end_rod");
	}
}
