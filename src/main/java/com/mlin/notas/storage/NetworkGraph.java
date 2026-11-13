package com.mlin.notas.storage;

import com.mlin.notas.util.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Descobre uma rede de armazenamento por busca em largura a partir de um bloco da rede. Cabos, unidades e terminais
 * conduzem (a busca continua por eles); baus ligados sao folhas (entram na rede mas nao a estendem, entao dois baus
 * encostados nao viram um atalho). Logica pura.
 */
public final class NetworkGraph {
	/** Limite de blocos de uma rede (conduitores + baus). */
	public static final int MAX_NODES = 1024;

	private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	/** O que ha numa posicao, do ponto de vista da rede. */
	public enum NodeKind {
		NONE,
		CABLE,
		UNIT,
		TERMINAL,
		CONTAINER;

		public boolean conducts() {
			return this == CABLE || this == UNIT || this == TERMINAL;
		}
	}

	/**
	 * Resultado: unidades e baus na ordem em que foram achados (mais perto primeiro), quantos blocos a rede tem e se
	 * a busca parou no limite.
	 */
	public record Result(List<Pos> units, List<Pos> containers, List<Pos> terminals, int cables, boolean truncated) {
		public int size() {
			return units.size() + containers.size() + terminals.size() + cables;
		}
	}

	private NetworkGraph() {
	}

	public static Result discover(Pos start, Function<Pos, NodeKind> kindAt) {
		return discover(start, kindAt, MAX_NODES);
	}

	public static Result discover(Pos start, Function<Pos, NodeKind> kindAt, int maxNodes) {
		List<Pos> units = new ArrayList<>();
		List<Pos> containers = new ArrayList<>();
		List<Pos> terminals = new ArrayList<>();
		int cables = 0;
		boolean truncated = false;
		NodeKind startKind = kindAt.apply(start);
		if (!startKind.conducts()) {
			return new Result(List.of(), List.of(), List.of(), 0, false);
		}
		Set<Pos> seen = new HashSet<>();
		Deque<Pos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		int nodes = 0;
		while (!queue.isEmpty()) {
			Pos pos = queue.poll();
			NodeKind kind = kindAt.apply(pos);
			if (nodes >= maxNodes) {
				truncated = true;
				break;
			}
			nodes++;
			switch (kind) {
				case CABLE -> cables++;
				case UNIT -> units.add(pos);
				case TERMINAL -> terminals.add(pos);
				case CONTAINER -> containers.add(pos);
				case NONE -> {
					nodes--;
					continue;
				}
			}
			if (!kind.conducts()) {
				continue;
			}
			for (int[] d : NEIGHBORS) {
				Pos next = pos.offset(d[0], d[1], d[2]);
				if (seen.add(next) && kindAt.apply(next) != NodeKind.NONE) {
					queue.add(next);
				}
			}
		}
		return new Result(List.copyOf(units), List.copyOf(containers), List.copyOf(terminals), cables, truncated);
	}
}
