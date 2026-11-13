package com.mlin.notas.enchanting;

/**
 * Regras puras do encantamento escolhido (sem classes do Minecraft).
 *
 * <ul>
 *   <li>Estantes liberam niveis: com 15 estantes qualquer encantamento chega ao nivel maximo;
 *       sem estantes so o nivel 1.</li>
 *   <li>Custo em niveis de XP: para cada nivel novo {@code l}, paga {@code custoBase + l}, onde o custo base
 *       e o "anvil cost" do encantamento (1 comum, 2 incomum, 4 raro, 8 muito raro).</li>
 *   <li>Custo em lapis: 1 por nivel novo.</li>
 * </ul>
 */
public final class EnchantRules {
	/** Numero de estantes que libera o nivel maximo (igual ao maximo util da mesa vanilla). */
	public static final int MAX_BOOKSHELVES = 15;

	private EnchantRules() {
	}

	/** Maior nivel permitido para um encantamento com {@code maxLevel}, dadas as estantes ao redor. */
	public static int maxAllowedLevel(int maxLevel, int bookshelves) {
		if (maxLevel <= 0) {
			return 0;
		}
		int shelves = Math.clamp(bookshelves, 0, MAX_BOOKSHELVES);
		// linear: 0 estantes = nivel 1, 15 estantes = nivel maximo
		int allowed = 1 + (maxLevel - 1) * shelves / MAX_BOOKSHELVES;
		return Math.clamp(allowed, 1, maxLevel);
	}

	/** Estantes necessarias para liberar {@code level} de um encantamento com {@code maxLevel}. */
	public static int bookshelvesNeeded(int maxLevel, int level) {
		for (int shelves = 0; shelves <= MAX_BOOKSHELVES; shelves++) {
			if (maxAllowedLevel(maxLevel, shelves) >= level) {
				return shelves;
			}
		}
		return MAX_BOOKSHELVES;
	}

	/** Niveis de XP para subir de {@code fromLevel} (0 = nao tem) ate {@code toLevel}. */
	public static int xpCost(int anvilCost, int fromLevel, int toLevel) {
		int cost = 0;
		for (int level = Math.max(1, fromLevel + 1); level <= toLevel; level++) {
			cost += Math.max(1, anvilCost) + level;
		}
		return cost;
	}

	/** Lapis necessario para subir de {@code fromLevel} ate {@code toLevel}. */
	public static int lapisCost(int fromLevel, int toLevel) {
		return Math.max(0, toLevel - Math.max(0, fromLevel));
	}

	/** Motivo de recusa, ou {@link Check#OK}. A ordem dos testes define a mensagem mostrada ao jogador. */
	public enum Check {
		OK,
		NO_ITEM,
		NOT_APPLICABLE,
		INCOMPATIBLE,
		ALREADY_AT_LEVEL,
		NEEDS_BOOKSHELVES,
		NOT_ENOUGH_XP,
		NOT_ENOUGH_LAPIS
	}

	/**
	 * Valida um pedido de encantamento.
	 *
	 * @param applicable     o encantamento serve para o item
	 * @param compatible     nao conflita com os encantamentos que o item ja tem (ignorando ele mesmo)
	 * @param currentLevel   nivel que o item ja tem (0 se nao tem)
	 * @param requestedLevel nivel pedido
	 * @param maxLevel       nivel maximo do encantamento
	 * @param anvilCost      custo base do encantamento
	 * @param bookshelves    estantes ao redor da mesa
	 * @param playerLevels   niveis de XP do jogador
	 * @param lapis          lapis no slot
	 * @param creative       jogador com materiais infinitos (nao paga)
	 */
	public static Check check(boolean hasItem, boolean applicable, boolean compatible, int currentLevel, int requestedLevel,
			int maxLevel, int anvilCost, int bookshelves, int playerLevels, int lapis, boolean creative) {
		if (!hasItem) {
			return Check.NO_ITEM;
		}
		if (!applicable || requestedLevel < 1 || requestedLevel > maxLevel) {
			return Check.NOT_APPLICABLE;
		}
		if (!compatible) {
			return Check.INCOMPATIBLE;
		}
		if (requestedLevel <= currentLevel) {
			return Check.ALREADY_AT_LEVEL;
		}
		if (requestedLevel > maxAllowedLevel(maxLevel, bookshelves)) {
			return Check.NEEDS_BOOKSHELVES;
		}
		if (creative) {
			return Check.OK;
		}
		if (playerLevels < xpCost(anvilCost, currentLevel, requestedLevel)) {
			return Check.NOT_ENOUGH_XP;
		}
		if (lapis < lapisCost(currentLevel, requestedLevel)) {
			return Check.NOT_ENOUGH_LAPIS;
		}
		return Check.OK;
	}
}
