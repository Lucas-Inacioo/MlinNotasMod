package com.mlin.notas.wand;

import com.mlin.notas.util.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Logica pura da varinha de construcao: a partir do bloco clicado e da face clicada, encontra os blocos
 * iguais no mesmo plano e devolve as posicoes (uma camada a frente da face) onde novos blocos devem ser colocados.
 */
public final class WandPlanner {
	private WandPlanner() {
	}

	/**
	 * @param clicked       bloco clicado
	 * @param normal        vetor unitario da face clicada (ex.: (0,1,0) para a face de cima)
	 * @param isSameBlock   true se a posicao contem o mesmo bloco do clicado
	 * @param isReplaceable true se a posicao pode receber o novo bloco (ar, grama alta, agua...)
	 * @param limit         numero maximo de blocos a colocar
	 * @return posicoes alvo, em ordem de distancia ao ponto clicado
	 */
	public static List<Pos> plan(Pos clicked, Pos normal, Predicate<Pos> isSameBlock, Predicate<Pos> isReplaceable, int limit) {
		List<Pos> targets = new ArrayList<>();
		if (limit <= 0 || !isSameBlock.test(clicked) || !isReplaceable.test(clicked.plus(normal))) {
			return targets;
		}
		List<Pos> planeDirs = planeDirections(normal);
		Set<Pos> visited = new HashSet<>();
		Deque<Pos> queue = new ArrayDeque<>();
		visited.add(clicked);
		queue.add(clicked);
		while (!queue.isEmpty() && targets.size() < limit) {
			Pos current = queue.poll();
			targets.add(current.plus(normal));
			for (Pos dir : planeDirs) {
				Pos next = current.plus(dir);
				if (visited.add(next) && isSameBlock.test(next) && isReplaceable.test(next.plus(normal))) {
					queue.add(next);
				}
			}
		}
		return targets;
	}

	static List<Pos> planeDirections(Pos normal) {
		List<Pos> dirs = new ArrayList<>(4);
		if (normal.x() == 0) {
			dirs.add(new Pos(1, 0, 0));
			dirs.add(new Pos(-1, 0, 0));
		}
		if (normal.y() == 0) {
			dirs.add(new Pos(0, 1, 0));
			dirs.add(new Pos(0, -1, 0));
		}
		if (normal.z() == 0) {
			dirs.add(new Pos(0, 0, 1));
			dirs.add(new Pos(0, 0, -1));
		}
		return dirs;
	}
}
