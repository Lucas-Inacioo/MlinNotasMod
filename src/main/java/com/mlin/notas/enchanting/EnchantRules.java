package com.mlin.notas.enchanting;

import com.mlin.notas.tier.Tier;

/**
 * Regras puras do encantamento escolhido (sem classes do Minecraft).
 *
 * <ul>
 *   <li>Estantes liberam niveis: com 15 estantes qualquer encantamento chega ao nivel maximo;
 *       sem estantes so o nivel 1.</li>
 *   <li>O nivel de Mineracao do mundo (arvore de habilidades) limita o nivel de qualquer encantamento
 *       ({@link #miningCap}): Madeira a Ferro = I, Ouro e Cobalto = II, Diamante e Netherite = III,
 *       Infernita e Endita = IV, Astralita em diante = maximo. Vale na mesa e na bigorna.</li>
 *   <li>Custo em niveis de XP: para cada nivel novo {@code l}, paga {@code 2 * custoBase + 2 * l}, onde o custo base
 *       e o "anvil cost" do encantamento (1 comum, 2 incomum, 4 raro, 8 muito raro).</li>
 *   <li>Custo em lapis: 1 por nivel novo.</li>
 *   <li>Reagente tematico por encantamento, mais quanto maior o nivel ({@link EnchantReagents}).</li>
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

	/**
	 * Teto de nivel de encantamento pelo nivel de Mineracao do mundo ({@code miningLevel} = 1 Madeira .. 12 Celestio),
	 * sem contar o maximo do encantamento. {@link Integer#MAX_VALUE} = sem teto.
	 */
	public static int miningCap(int miningLevel) {
		if (miningLevel >= Tier.ASTRALITE.level()) {
			return Integer.MAX_VALUE;
		}
		if (miningLevel >= Tier.INFERNITE.level()) {
			return 4;
		}
		if (miningLevel >= Tier.DIAMOND.level()) {
			return 3;
		}
		if (miningLevel >= Tier.GOLD.level()) {
			return 2;
		}
		return 1;
	}

	/** Maior nivel de um encantamento com {@code maxLevel} que o mundo libera com Mineracao {@code miningLevel}. */
	public static int cappedLevel(int maxLevel, int miningLevel) {
		return Math.min(maxLevel, miningCap(miningLevel));
	}

	/** Primeiro nivel de Mineracao que libera {@code level} de encantamento (para dizer ao jogador o que falta). */
	public static Tier tierForLevel(int level) {
		for (Tier tier : Tier.values()) {
			if (miningCap(tier.level()) >= level) {
				return tier;
			}
		}
		return Tier.CELESTIUM;
	}

	/**
	 * A bigorna pode entregar um item com o encantamento no nivel {@code resultLevel}? Sim se cabe no teto do mundo ou
	 * se o item da esquerda ja tinha esse nivel (consertar/renomear um item achado nao e bloqueado).
	 */
	public static boolean anvilAllows(int leftLevel, int resultLevel, int maxLevel, int miningLevel) {
		return resultLevel <= Math.max(leftLevel, cappedLevel(maxLevel, miningLevel));
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
			cost += 2 * Math.max(1, anvilCost) + 2 * level;
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
		MINING_CAP,
		NEEDS_BOOKSHELVES,
		NOT_ENOUGH_XP,
		NOT_ENOUGH_LAPIS,
		NOT_ENOUGH_REAGENT
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
	 * @param miningLevel    nivel de Mineracao do mundo (1 = Madeira .. 12 = Celestio)
	 * @param playerLevels   niveis de XP do jogador
	 * @param lapis          lapis no slot
	 * @param reagentBase    quantidade do nivel I do reagente do encantamento ({@link EnchantReagents})
	 * @param reagentHave    reagente certo no slot de reagente (0 se o item do slot e outro)
	 * @param creative       jogador com materiais infinitos (nao paga nem respeita o teto do mundo)
	 */
	public static Check check(boolean hasItem, boolean applicable, boolean compatible, int currentLevel, int requestedLevel,
			int maxLevel, int anvilCost, int bookshelves, int miningLevel, int playerLevels, int lapis, int reagentBase, int reagentHave, boolean creative) {
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
		if (!creative && requestedLevel > cappedLevel(maxLevel, miningLevel)) {
			return Check.MINING_CAP;
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
		if (reagentHave < EnchantReagents.cost(reagentBase, currentLevel, requestedLevel)) {
			return Check.NOT_ENOUGH_REAGENT;
		}
		return Check.OK;
	}
}
