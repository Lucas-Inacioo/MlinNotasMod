package com.mlin.notas.vein;

import com.mlin.notas.util.Pos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Mineracao em veio (logica pura): a partir do minerio quebrado, acha os minerios do mesmo grupo conectados
 * (vizinhanca 3x3x3, para pegar veios em diagonal) e devolve a ordem de quebra, do mais perto para o mais longe.
 *
 * <p>Grupo = id do bloco sem o prefixo {@code deepslate_} ({@link #group}), entao minerio de ferro e de ferro de
 * ardosia saem juntos. O tamanho maximo cresce com o nivel de Mineracao da arvore de habilidades
 * ({@link #maxBlocks}).</p>
 */
public final class VeinPlanner {
	/** Raio maximo (Chebyshev) a partir do bloco inicial. */
	public static final int MAX_RADIUS = 8;
	/** Teto absoluto de blocos extras por quebra. */
	public static final int HARD_CAP = 32;

	private VeinPlanner() {
	}

	/** Blocos extras que um veio pode levar com Mineracao {@code miningLevel} (1 = Madeira .. 12 = Celestio). */
	public static int maxBlocks(int miningLevel) {
		int level = Math.max(1, miningLevel);
		return Math.min(HARD_CAP, 8 + 2 * (level - 1));
	}

	/** Grupo do minerio: {@code minecraft:deepslate_iron_ore} e {@code minecraft:iron_ore} -> {@code minecraft:iron_ore}. */
	public static String group(String blockId) {
		int colon = blockId.indexOf(':');
		String ns = colon < 0 ? "minecraft" : blockId.substring(0, colon);
		String path = colon < 0 ? blockId : blockId.substring(colon + 1);
		if (path.startsWith("deepslate_")) {
			path = path.substring("deepslate_".length());
		}
		return ns + ":" + path;
	}

	/**
	 * @param start   bloco ja quebrado (nao entra no resultado)
	 * @param group   grupo do minerio quebrado ({@link #group})
	 * @param groupAt grupo do bloco em cada posicao (null = nao e minerio)
	 * @param max     maximo de blocos extras
	 */
	public static List<Pos> plan(Pos start, String group, Function<Pos, String> groupAt, int max) {
		List<Pos> found = new ArrayList<>();
		if (max <= 0) {
			return found;
		}
		Set<Pos> visited = new HashSet<>();
		Deque<Pos> queue = new ArrayDeque<>();
		visited.add(start);
		queue.add(start);
		while (!queue.isEmpty() && found.size() < max) {
			Pos current = queue.poll();
			for (int dx = -1; dx <= 1 && found.size() < max; dx++) {
				for (int dy = -1; dy <= 1 && found.size() < max; dy++) {
					for (int dz = -1; dz <= 1 && found.size() < max; dz++) {
						Pos next = current.offset(dx, dy, dz);
						if (!visited.add(next) || next.distanceChebyshev(start) > MAX_RADIUS) {
							continue;
						}
						if (group.equals(groupAt.apply(next))) {
							found.add(next);
							queue.add(next);
						}
					}
				}
			}
		}
		found.sort(Comparator.comparingInt(p -> distanceSq(p, start)));
		return found;
	}

	private static int distanceSq(Pos a, Pos b) {
		int dx = a.x() - b.x();
		int dy = a.y() - b.y();
		int dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
