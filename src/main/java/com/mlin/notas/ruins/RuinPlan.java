package com.mlin.notas.ruins;

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
 * Resultado do {@link RuinPlanner}: blocos por posicao relativa (origem = ponto de entrada; y=0 = altura dos pes no
 * chao, y=-1 = camada de alicerce), direcao dos blocos orientados e marcadores nomeados (bau, pedra de teleporte,
 * lugares reservados a chefes). Logica pura.
 *
 * <p>Fora do plano o terreno e considerado plano: y &lt; 0 e chao, y &ge; 0 e ar.
 */
public final class RuinPlan {
	/** Direcao horizontal (dx, dz), sem depender do Minecraft. */
	public enum Dir {
		NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

		public final int dx;
		public final int dz;

		Dir(int dx, int dz) {
			this.dx = dx;
			this.dz = dz;
		}

		/** Gira 90 graus no sentido horario {@code times} vezes. */
		public Dir rotate(int times) {
			return values()[Math.floorMod(ordinal() + times, 4)];
		}
	}

	public static final String MARKER_CHEST = "chest";
	/** Ponto do lado de fora, no chao, de onde se chega a pe. */
	public static final String MARKER_ENTRANCE = "entrance";
	public static final String MARKER_WAYSTONE = "waystone";
	/** Atril do altar da capela: onde vai o item de invocar um chefe (futuro). */
	public static final String MARKER_BOSS_SUMMON = "boss_summon";
	/** Bloco provisorio da sala do Golem de Cobalto (futuro) na mina. */
	public static final String MARKER_BOSS_ALTAR = "boss_altar";

	private final RuinKind kind;
	private final Map<Long, RuinPart> blocks = new LinkedHashMap<>();
	private final Map<Long, Dir> facing = new LinkedHashMap<>();
	private final Map<String, Pos> markers = new LinkedHashMap<>();
	/** Entulho solto (tem que estar apoiado em algo embaixo, nao so encostado). */
	private final Set<Long> loose = new HashSet<>();
	private boolean ironVein;

	RuinPlan(RuinKind kind) {
		this.kind = kind;
	}

	public static long key(int x, int y, int z) {
		return ((long) (x + 512) << 32) | ((long) (y + 512) << 16) | (z + 512);
	}

	public static int keyX(long key) {
		return (int) (key >>> 32) - 512;
	}

	public static int keyY(long key) {
		return (int) ((key >>> 16) & 0xFFFF) - 512;
	}

	public static int keyZ(long key) {
		return (int) (key & 0xFFFF) - 512;
	}

	// --- construcao (planner) ---

	void set(int x, int y, int z, RuinPart part) {
		blocks.put(key(x, y, z), part);
	}

	void set(int x, int y, int z, RuinPart part, Dir dir) {
		set(x, y, z, part);
		facing.put(key(x, y, z), dir);
	}

	void setIfAbsent(int x, int y, int z, RuinPart part) {
		blocks.putIfAbsent(key(x, y, z), part);
	}

	void remove(int x, int y, int z) {
		blocks.remove(key(x, y, z));
		facing.remove(key(x, y, z));
		loose.remove(key(x, y, z));
	}

	void markLoose(int x, int y, int z) {
		loose.add(key(x, y, z));
	}

	void mark(String name, int x, int y, int z) {
		markers.put(name, new Pos(x, y, z));
	}

	void ironVein(boolean iron) {
		this.ironVein = iron;
	}

	/** Copia girada 90 graus no sentido horario {@code times} vezes em torno da origem. */
	RuinPlan rotated(int times) {
		int t = Math.floorMod(times, 4);
		RuinPlan out = new RuinPlan(kind);
		out.ironVein = ironVein;
		for (Map.Entry<Long, RuinPart> entry : blocks.entrySet()) {
			long k = entry.getKey();
			int[] r = rotate(keyX(k), keyZ(k), t);
			out.blocks.put(key(r[0], keyY(k), r[1]), entry.getValue());
			Dir dir = facing.get(k);
			if (dir != null) {
				out.facing.put(key(r[0], keyY(k), r[1]), dir.rotate(t));
			}
		}
		for (Map.Entry<String, Pos> marker : markers.entrySet()) {
			Pos p = marker.getValue();
			int[] r = rotate(p.x(), p.z(), t);
			out.markers.put(marker.getKey(), new Pos(r[0], p.y(), r[1]));
		}
		for (long k : loose) {
			int[] r = rotate(keyX(k), keyZ(k), t);
			out.loose.add(key(r[0], keyY(k), r[1]));
		}
		return out;
	}

	/** Copia espelhada (x vira -x): leste e oeste trocam. */
	RuinPlan mirrored() {
		RuinPlan out = new RuinPlan(kind);
		out.ironVein = ironVein;
		for (Map.Entry<Long, RuinPart> entry : blocks.entrySet()) {
			long k = entry.getKey();
			long m = key(-keyX(k), keyY(k), keyZ(k));
			out.blocks.put(m, entry.getValue());
			Dir dir = facing.get(k);
			if (dir != null) {
				out.facing.put(m, dir == Dir.EAST ? Dir.WEST : dir == Dir.WEST ? Dir.EAST : dir);
			}
		}
		for (Map.Entry<String, Pos> marker : markers.entrySet()) {
			Pos p = marker.getValue();
			out.markers.put(marker.getKey(), new Pos(-p.x(), p.y(), p.z()));
		}
		for (long k : loose) {
			out.loose.add(key(-keyX(k), keyY(k), keyZ(k)));
		}
		return out;
	}

	private static int[] rotate(int x, int z, int times) {
		int rx = x;
		int rz = z;
		for (int i = 0; i < times; i++) {
			int nx = -rz;
			rz = rx;
			rx = nx;
		}
		return new int[] {rx, rz};
	}

	// --- consulta ---

	public RuinKind kind() {
		return kind;
	}

	public RuinPart get(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	public Dir facing(int x, int y, int z) {
		return facing.get(key(x, y, z));
	}

	public Map<Long, RuinPart> blocks() {
		return Collections.unmodifiableMap(blocks);
	}

	public Map<String, Pos> markers() {
		return Collections.unmodifiableMap(markers);
	}

	public Pos marker(String name) {
		return markers.get(name);
	}

	/** Blocos de entulho solto (cada um precisa de apoio logo abaixo). */
	public Set<Long> loose() {
		return Collections.unmodifiableSet(loose);
	}

	/** O veio da mina e de ferro (senao, cobre). */
	public boolean ironVein() {
		return ironVein;
	}

	public long count(RuinPart part) {
		return blocks.values().stream().filter(p -> p == part).count();
	}

	public List<Pos> positionsOf(RuinPart part) {
		List<Pos> out = new ArrayList<>();
		for (Map.Entry<Long, RuinPart> entry : blocks.entrySet()) {
			if (entry.getValue() == part) {
				long k = entry.getKey();
				out.add(new Pos(keyX(k), keyY(k), keyZ(k)));
			}
		}
		return out;
	}

	/** {min x, min y, min z, max x, max y, max z} de todos os blocos do plano. */
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

	/** {min x, min z, max x, max z} do que fica na superficie (y &ge; -1): area onde o terreno e amostrado. */
	public int[] surfaceBounds() {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (Map.Entry<Long, RuinPart> entry : blocks.entrySet()) {
			long k = entry.getKey();
			if (keyY(k) >= -1 && entry.getValue() != RuinPart.AIR) {
				b[0] = Math.min(b[0], keyX(k));
				b[1] = Math.min(b[1], keyZ(k));
				b[2] = Math.max(b[2], keyX(k));
				b[3] = Math.max(b[3], keyZ(k));
			}
		}
		return b;
	}

	/** Ocupado (o terreno fora do plano e chao abaixo de y=0). */
	public boolean solid(int x, int y, int z) {
		RuinPart part = get(x, y, z);
		return part != null ? part.obstacle() : y < 0;
	}

	private boolean supportsStanding(int x, int y, int z) {
		RuinPart part = get(x, y, z);
		return part != null ? part.standable() : y < 0;
	}

	/** Posicao dos pes valida: corpo livre (2 blocos) e chao firme embaixo ou escada de mao. */
	public boolean validFeet(int x, int y, int z) {
		if (solid(x, y, z) || solid(x, y + 1, z)) {
			return false;
		}
		return get(x, y, z) == RuinPart.LADDER || supportsStanding(x, y - 1, z);
	}

	/**
	 * Posicoes dos pes alcancaveis a pe partindo de {@code (x, y, z)}: anda, sobe 1 bloco pulando (com espaco para a
	 * cabeca), cai ate 3 blocos e sobe/desce escadas de mao. Nao constroi nada.
	 */
	public Set<Long> reachableFrom(int sx, int sy, int sz) {
		Set<Long> seen = new HashSet<>();
		Deque<int[]> queue = new ArrayDeque<>();
		// o chao em volta e infinito: anda so ate 3 blocos alem da ruina
		int[] b = bounds();
		int minX = Math.min(b[0], sx) - 3;
		int maxX = Math.max(b[3], sx) + 3;
		int minZ = Math.min(b[2], sz) - 3;
		int maxZ = Math.max(b[5], sz) + 3;
		if (validFeet(sx, sy, sz)) {
			seen.add(key(sx, sy, sz));
			queue.add(new int[] {sx, sy, sz});
		}
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			int x = p[0];
			int y = p[1];
			int z = p[2];
			for (Dir d : Dir.values()) {
				int nx = x + d.dx;
				int nz = z + d.dz;
				if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
					continue;
				}
				if (!solid(nx, y, nz) && !solid(nx, y + 1, nz)) {
					// entra na coluna e cai ate achar chao ou escada
					for (int k = 0; k <= 3; k++) {
						if (k > 0 && solid(nx, y - k, nz)) {
							break;
						}
						if (validFeet(nx, y - k, nz)) {
							visit(seen, queue, nx, y - k, nz);
							break;
						}
					}
				} else if (!solid(x, y + 2, z) && validFeet(nx, y + 1, nz)) {
					visit(seen, queue, nx, y + 1, nz);
				}
			}
			if (get(x, y, z) == RuinPart.LADDER) {
				if (validFeet(x, y + 1, z)) {
					visit(seen, queue, x, y + 1, z);
				}
				if (validFeet(x, y - 1, z)) {
					visit(seen, queue, x, y - 1, z);
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

	/** Da para usar o bloco em {@code (x, y, z)} (abrir bau, ler atril) de alguma posicao alcancavel ao lado. */
	public static boolean usableFrom(Set<Long> reach, int x, int y, int z) {
		for (Dir d : Dir.values()) {
			for (int dy = -1; dy <= 1; dy++) {
				if (reach.contains(key(x + d.dx, y + dy, z + d.dz))) {
					return true;
				}
			}
		}
		return reach.contains(key(x, y + 1, z));
	}

	/**
	 * Blocos que ficariam soltos no ar: nao ligados (pelos 6 vizinhos) a nada enterrado (y &lt; 0) nem apoiados no
	 * chao em y=0.
	 */
	public Set<Long> floating() {
		Set<Long> supported = new HashSet<>();
		Deque<Long> queue = new ArrayDeque<>();
		for (Map.Entry<Long, RuinPart> entry : blocks.entrySet()) {
			if (entry.getValue() == RuinPart.AIR) {
				continue;
			}
			long k = entry.getKey();
			int y = keyY(k);
			boolean grounded = y < 0 || (y == 0 && get(keyX(k), -1, keyZ(k)) == null);
			if (grounded && supported.add(k)) {
				queue.add(k);
			}
		}
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			long k = queue.poll();
			for (int[] d : dirs) {
				long n = key(keyX(k) + d[0], keyY(k) + d[1], keyZ(k) + d[2]);
				RuinPart part = blocks.get(n);
				if (part != null && part != RuinPart.AIR && supported.add(n)) {
					queue.add(n);
				}
			}
		}
		Set<Long> out = new HashSet<>();
		for (Map.Entry<Long, RuinPart> entry : blocks.entrySet()) {
			if (entry.getValue() != RuinPart.AIR && !supported.contains(entry.getKey())) {
				out.add(entry.getKey());
			}
		}
		return out;
	}

	/** Troca por ar tudo o que ficaria solto (entulho de desabamento sem apoio). */
	void removeFloating() {
		for (long k : floating()) {
			blocks.put(k, RuinPart.AIR);
			facing.remove(k);
			loose.remove(k);
		}
	}
}
