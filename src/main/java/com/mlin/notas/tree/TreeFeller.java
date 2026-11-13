package com.mlin.notas.tree;

import com.mlin.notas.util.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Logica pura do "quebrar arvore inteira": a partir do tronco quebrado, percorre os troncos conectados
 * (vizinhanca 3x3x3) e devolve a ordem em que devem ser quebrados (do mais baixo para o mais alto).
 */
public final class TreeFeller {
	/** Limite padrao de troncos por arvore, evita derrubar florestas conectadas. */
	public static final int DEFAULT_MAX_LOGS = 256;
	/** Raio horizontal maximo a partir do tronco inicial. Troncos mais longe nao pertencem a mesma arvore. */
	public static final int MAX_HORIZONTAL_RADIUS = 8;

	private TreeFeller() {
	}

	/**
	 * @param logs      troncos conectados, ordenados por altura (nao inclui o bloco inicial)
	 * @param hasLeaves se existe alguma folha encostada em algum tronco (distingue arvore de casa de madeira)
	 */
	public record Result(List<Pos> logs, boolean hasLeaves) {
		public boolean isTree() {
			return hasLeaves && !logs.isEmpty();
		}
	}

	public static Result fell(Pos start, Predicate<Pos> isLog, Predicate<Pos> isLeaves, int maxLogs) {
		Set<Pos> visited = new HashSet<>();
		Deque<Pos> queue = new ArrayDeque<>();
		List<Pos> found = new ArrayList<>();
		boolean leaves = false;
		visited.add(start);
		queue.add(start);
		while (!queue.isEmpty() && found.size() < maxLogs) {
			Pos current = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						Pos next = current.offset(dx, dy, dz);
						if (!visited.add(next)) {
							continue;
						}
						if (Math.abs(next.x() - start.x()) > MAX_HORIZONTAL_RADIUS
								|| Math.abs(next.z() - start.z()) > MAX_HORIZONTAL_RADIUS) {
							continue;
						}
						if (isLog.test(next)) {
							if (found.size() >= maxLogs) {
								break;
							}
							found.add(next);
							queue.add(next);
						} else if (!leaves && isLeaves.test(next)) {
							leaves = true;
						}
					}
				}
			}
		}
		found.sort((a, b) -> {
			int byHeight = Integer.compare(a.y(), b.y());
			if (byHeight != 0) {
				return byHeight;
			}
			return Integer.compare(a.distanceChebyshev(start), b.distanceChebyshev(start));
		});
		return new Result(List.copyOf(found), leaves);
	}
}
