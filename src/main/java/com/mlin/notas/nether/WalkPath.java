package com.mlin.notas.nether;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Caminhada a pe numa {@link WalkGrid} (busca em largura, sem nada do Minecraft). Passos nas 4 direcoes; sobe no
 * maximo 1 bloco (com 3 de espaco livre na origem para pular) e desce no maximo {@link #MAX_DROP} (com espaco livre
 * na coluna vizinha para passar por cima da borda). Descer e ida sem volta, entao "da para ir de A ate B" e
 * direcional: {@link #mutual} confere os dois sentidos.
 */
public final class WalkPath {
	/** Queda maxima por passo (3 blocos ainda nao machucam). */
	public static final int MAX_DROP = 3;

	private final WalkGrid grid;
	private final int[] offsets;
	private final int nodes;

	public WalkPath(WalkGrid grid) {
		this.grid = grid;
		this.offsets = new int[grid.width() * grid.depth() + 1];
		for (int z = 0; z < grid.depth(); z++) {
			for (int x = 0; x < grid.width(); x++) {
				int column = x + z * grid.width();
				offsets[column + 1] = offsets[column] + grid.levels(x, z).length;
			}
		}
		this.nodes = offsets[offsets.length - 1];
	}

	/** Uma posicao em pe: coluna e indice da altura dentro da coluna. */
	public record Node(int x, int z, int level) {
	}

	public int nodeCount() {
		return nodes;
	}

	private int id(int x, int z, int level) {
		return offsets[x + z * grid.width()] + level;
	}

	/** Posicao em pe da coluna com os pes na altura {@code feetY}, ou null. */
	public Node at(int x, int z, int feetY) {
		int[] levels = grid.levels(x, z);
		for (int i = 0; i < levels.length; i++) {
			if (levels[i] == feetY) {
				return new Node(x, z, i);
			}
		}
		return null;
	}

	/** O chao (posicao mais baixa em pe) da coluna, ou null. */
	public Node floor(int x, int z) {
		return grid.levels(x, z).length == 0 ? null : new Node(x, z, 0);
	}

	/** Posicao em pe da coluna com os pes mais perto de {@code feetY} (no maximo {@code tolerance} blocos), ou null. */
	public Node closestTo(int x, int z, int feetY, int tolerance) {
		int[] levels = grid.levels(x, z);
		Node best = null;
		int bestDistance = tolerance + 1;
		for (int i = 0; i < levels.length; i++) {
			int distance = Math.abs(levels[i] - feetY);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = new Node(x, z, i);
			}
		}
		return best;
	}

	/** Chao mais perto de (x, z) num raio (para achar um ponto de partida perto de um canto). */
	public Node nearestFloor(int x, int z, int radius) {
		for (int r = 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int cx = x + dx;
					int cz = z + dz;
					if (cx >= 0 && cz >= 0 && cx < grid.width() && cz < grid.depth() && grid.levels(cx, cz).length > 0) {
						return new Node(cx, cz, 0);
					}
				}
			}
		}
		return null;
	}

	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** Da para dar um passo da altura {@code fromY} (com {@code fromClear} de espaco livre) para {@code toY}? */
	static boolean canStep(int fromY, int fromClear, int toY, int toClear) {
		int rise = toY - fromY;
		if (rise > 1 || rise < -MAX_DROP) {
			return false;
		}
		if (rise == 1) {
			return fromClear >= 3;
		}
		// descendo: a coluna vizinha precisa estar livre na altura do corpo (pes e cabeca) antes da queda
		return toClear >= 2 - rise;
	}

	/** Tudo que da para alcancar a pe partindo de {@code start}. */
	public boolean[] reachableFrom(Node start) {
		boolean[] seen = new boolean[nodes];
		int[] qx = new int[nodes];
		int[] qz = new int[nodes];
		int[] ql = new int[nodes];
		int head = 0;
		int tail = 0;
		int first = id(start.x(), start.z(), start.level());
		seen[first] = true;
		qx[tail] = start.x();
		qz[tail] = start.z();
		ql[tail] = start.level();
		tail++;
		while (head < tail) {
			int x = qx[head];
			int z = qz[head];
			int level = ql[head];
			head++;
			int fromY = grid.levels(x, z)[level];
			int fromClear = grid.clearance(x, z, level);
			for (int[] d : DIRECTIONS) {
				int nx = x + d[0];
				int nz = z + d[1];
				if (nx < 0 || nz < 0 || nx >= grid.width() || nz >= grid.depth()) {
					continue;
				}
				int[] levels = grid.levels(nx, nz);
				for (int i = 0; i < levels.length; i++) {
					if (levels[i] > fromY + 1) {
						break;
					}
					int next = id(nx, nz, i);
					if (!seen[next] && canStep(fromY, fromClear, levels[i], grid.clearance(nx, nz, i))) {
						seen[next] = true;
						qx[tail] = nx;
						qz[tail] = nz;
						ql[tail] = i;
						tail++;
					}
				}
			}
		}
		return seen;
	}

	public boolean reaches(boolean[] reachable, Node target) {
		return reachable[id(target.x(), target.z(), target.level())];
	}

	/** Da para ir de {@code a} ate {@code b} e voltar a pe. */
	public boolean mutual(Node a, Node b) {
		return reaches(reachableFrom(a), b) && reaches(reachableFrom(b), a);
	}

	/** Quantas posicoes em pe sao alcancaveis a partir de {@code start}. */
	public int count(boolean[] reachable) {
		int total = 0;
		for (boolean r : reachable) {
			if (r) {
				total++;
			}
		}
		return total;
	}

	/**
	 * Sorteia {@code samples} chaos e mede a fracao dos pares ordenados (a, b), a != b, em que da para ir de a ate b.
	 * Devolve 1.0 se houver menos de 2 chaos.
	 */
	public double connectedPairFraction(int samples, long seed) {
		List<Node> floors = new ArrayList<>();
		Random random = new Random(seed);
		int attempts = 0;
		while (floors.size() < samples && attempts < samples * 200) {
			attempts++;
			Node node = floor(random.nextInt(grid.width()), random.nextInt(grid.depth()));
			if (node != null) {
				floors.add(node);
			}
		}
		return connectedPairFraction(floors);
	}

	/** Fracao dos pares ordenados (a, b), a != b, da lista em que da para ir de a ate b (1.0 com menos de 2 pontos). */
	public double connectedPairFraction(List<Node> floors) {
		if (floors.size() < 2) {
			return 1.0;
		}
		long ok = 0;
		long total = 0;
		for (Node from : floors) {
			boolean[] reach = reachableFrom(from);
			for (Node to : floors) {
				if (to != from) {
					total++;
					if (reaches(reach, to)) {
						ok++;
					}
				}
			}
		}
		return (double) ok / total;
	}
}
