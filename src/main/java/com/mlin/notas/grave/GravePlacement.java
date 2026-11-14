package com.mlin.notas.grave;

/**
 * Onde a lapide vai (logica pura): o lugar seguro mais perto da morte. Seguro = celula livre (ar ou planta
 * substituivel, sem fluido e sem block entity), chao firme embaixo e sem lava em cima. Nunca escolhe celula que nao
 * seja livre, entao nunca substitui bloco do jogador nem container.
 *
 * <p>Ordem de busca:</p>
 * <ol>
 *   <li>caixa de {@link #RADIUS} blocos para os lados e {@link #VERTICAL} para cima/baixo em volta da morte (descer
 *   custa o dobro de subir; vizinho de lava custa mais);</li>
 *   <li>a coluna da morte: primeiro subindo, depois descendo;</li>
 *   <li>em volta do ultimo chao onde o jogador pisou;</li>
 *   <li>caixa maior ({@link #FAR_RADIUS});</li>
 *   <li>sem lugar seguro: primeira celula livre na coluna acima da morte (a lapide flutua; ela nao cai), depois
 *   qualquer celula substituivel (ate agua/lava). Sem nada disso: {@code null} (os itens caem como no vanilla).</li>
 * </ol>
 * Morte no vazio (abaixo do mundo) inverte as duas primeiras: o ultimo chao vem antes, e a coluna e varrida de cima
 * para baixo (o lugar seguro mais alto).
 */
public final class GravePlacement {
	public static final int RADIUS = 8;
	public static final int VERTICAL = 10;
	public static final int FAR_RADIUS = 24;
	public static final int FAR_VERTICAL = 16;
	/** Custo extra de um lugar com lava encostada (em distancia ao quadrado). */
	static final int LAVA_PENALTY = 60;

	/** O que ha numa celula. */
	public enum Cell {
		/** Ar ou bloco substituivel sem fluido e sem block entity: a lapide pode ir aqui. */
		OPEN,
		/** Agua (ou outro fluido nao perigoso). */
		WATER,
		/** Lava, fogo, magma: perigo. */
		LAVA,
		/** Bloco com face de cima firme: serve de chao. */
		SOLID,
		/** Qualquer outra coisa (fora do mundo, chunk nao carregado, cerca, container...). */
		BLOCKED
	}

	@FunctionalInterface
	public interface World {
		Cell at(int x, int y, int z);
	}

	public record Pos(int x, int y, int z) {
	}

	/** Resultado: posicao e se e um lugar seguro (com chao) ou so o ultimo recurso. */
	public record Result(Pos pos, boolean safe) {
	}

	private GravePlacement() {
	}

	/**
	 * @param minY primeiro Y valido do mundo
	 * @param maxY ultimo Y valido (use a altura logica em dimensoes com teto, para nao ir parar em cima da bedrock)
	 * @param lastGround ultimo lugar com chao onde o jogador esteve (ou null)
	 */
	public static Result find(World world, int x, int y, int z, int minY, int maxY, Pos lastGround) {
		World w = bounded(world, minY, maxY);
		boolean void_ = y < minY;
		int cy = Math.max(minY + 1, Math.min(maxY - 1, y));
		Pos found;
		if (void_) {
			found = lastGround != null ? nearest(w, lastGround.x(), lastGround.y(), lastGround.z(), RADIUS, VERTICAL) : null;
			if (found == null) {
				found = columnDown(w, x, z, maxY - 1, minY + 1);
			}
		} else {
			found = nearest(w, x, cy, z, RADIUS, VERTICAL);
			if (found == null) {
				found = columnUp(w, x, z, cy, maxY - 1);
			}
			if (found == null) {
				found = columnDown(w, x, z, cy, minY + 1);
			}
			if (found == null && lastGround != null) {
				found = nearest(w, lastGround.x(), lastGround.y(), lastGround.z(), RADIUS, VERTICAL);
			}
		}
		if (found == null) {
			found = nearest(w, x, cy, z, FAR_RADIUS, FAR_VERTICAL);
		}
		if (found != null) {
			return new Result(found, true);
		}
		Pos fallback = fallback(w, x, cy, z, maxY);
		return fallback == null ? null : new Result(fallback, false);
	}

	/** Celula onde a lapide fica segura: livre, chao firme embaixo e sem lava em cima. */
	public static boolean safe(World w, int x, int y, int z) {
		return w.at(x, y, z) == Cell.OPEN && w.at(x, y - 1, z) == Cell.SOLID && w.at(x, y + 1, z) != Cell.LAVA;
	}

	static Pos nearest(World w, int cx, int cy, int cz, int radius, int vertical) {
		Pos best = null;
		long bestScore = Long.MAX_VALUE;
		for (int dy = -vertical; dy <= vertical; dy++) {
			long dyCost = (long) dy * dy * (dy < 0 ? 2 : 1);
			if (dyCost >= bestScore) {
				continue;
			}
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					long score = dyCost + (long) dx * dx + (long) dz * dz;
					if (score >= bestScore) {
						continue;
					}
					int x = cx + dx;
					int y = cy + dy;
					int z = cz + dz;
					if (!safe(w, x, y, z)) {
						continue;
					}
					if (nearLava(w, x, y, z)) {
						score += LAVA_PENALTY;
						if (score >= bestScore) {
							continue;
						}
					}
					best = new Pos(x, y, z);
					bestScore = score;
				}
			}
		}
		return best;
	}

	private static boolean nearLava(World w, int x, int y, int z) {
		return w.at(x + 1, y, z) == Cell.LAVA || w.at(x - 1, y, z) == Cell.LAVA || w.at(x, y, z + 1) == Cell.LAVA
				|| w.at(x, y, z - 1) == Cell.LAVA;
	}

	static Pos columnUp(World w, int x, int z, int from, int to) {
		for (int y = from; y <= to; y++) {
			if (safe(w, x, y, z)) {
				return new Pos(x, y, z);
			}
		}
		return null;
	}

	static Pos columnDown(World w, int x, int z, int from, int to) {
		for (int y = from; y >= to; y--) {
			if (safe(w, x, y, z)) {
				return new Pos(x, y, z);
			}
		}
		return null;
	}

	private static Pos fallback(World w, int x, int cy, int z, int maxY) {
		for (int y = cy; y < maxY; y++) {
			if (w.at(x, y, z) == Cell.OPEN) {
				return new Pos(x, y, z);
			}
		}
		for (int y = cy; y < maxY; y++) {
			Cell cell = w.at(x, y, z);
			if (cell == Cell.WATER || cell == Cell.LAVA) {
				return new Pos(x, y, z);
			}
		}
		return null;
	}

	private static World bounded(World world, int minY, int maxY) {
		return (x, y, z) -> y < minY || y > maxY ? Cell.BLOCKED : world.at(x, y, z);
	}
}
