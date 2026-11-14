package com.mlin.notas.village;

import java.util.List;
import java.util.Map;

/**
 * Regras das construcoes de oficios e servicos (Vilas 2.0, fase 6), logica pura: pedreira, curtume, alfaiataria,
 * flecharia, encantador, taverna (boatos), igreja, feira semanal. Todos os numeros ficam aqui para o balanceamento.
 */
public final class CraftsRules {
	/** Um dia de jogo em ticks. */
	public static final long DAY = 24000L;
	/** Curtume: a tina leva meio dia de jogo para curar. */
	public static final int TANNING_TICKS = 12000;
	public static final int VAT_SLOTS = 8;

	private CraftsRules() {
	}

	// ---------------------------------------------------------------- curtume

	/** Couro que sai de {@code flesh} carnes podres: 1 a cada 2 no nivel I (arredonda para cima), 1 a 1 no II. */
	public static int tanned(int flesh, int level) {
		if (flesh <= 0) {
			return 0;
		}
		return level >= 2 ? flesh : (flesh + 1) / 2;
	}

	// ---------------------------------------------------------------- pedreira

	/** Itens que o bau da pedreira recebe por dia de jogo. */
	public static int quarryPerDay(int level) {
		return switch (Math.max(1, Math.min(3, level))) {
			case 1 -> 60;
			case 2 -> 120;
			default -> 240;
		};
	}

	/** Ticks entre dois itens. */
	public static int quarryPeriod(int level) {
		return (int) (DAY / quarryPerDay(level));
	}

	/** O que a pedreira solta para um sorteio de 0 a 99: pedra, pedregulho e tijolos; o nivel III traz tambem ardosia. */
	public static String quarryDrop(int level, int roll) {
		int r = Math.floorMod(roll, 100);
		if (level >= 3 && r >= 90) {
			return "minecraft:cobbled_deepslate";
		}
		if (r < 40) {
			return "minecraft:cobblestone";
		}
		if (r < 75) {
			return "minecraft:stone";
		}
		return "minecraft:stone_bricks";
	}

	/** Quantos itens a pedreira solta entre dois instantes (conta os multiplos do periodo que passaram). */
	public static int quarryDue(int level, long from, long to) {
		long period = quarryPeriod(level);
		return (int) Math.max(0, to / period - from / period);
	}

	/** Blocos de aterro que o estoque da pedreira cobre no preco da terraplenagem (credito por obra funcionando). */
	public static int quarryFillCredit(int level) {
		return switch (Math.max(0, Math.min(3, level))) {
			case 0 -> 0;
			case 1 -> 60;
			case 2 -> 160;
			default -> 320;
		};
	}

	// ---------------------------------------------------------------- preco das ofertas com construcao

	/** Preco de uma oferta do mercado com os descontos das construcoes (alfaiate, flecheiro). Nunca menos de 1. */
	public static int price(String offerId, int base, Map<String, Integer> levels) {
		int tailor = levels.getOrDefault(CraftsBuildings.TAILOR, 0);
		int fletcher = levels.getOrDefault(CraftsBuildings.FLETCHER, 0);
		return switch (offerId) {
			case "backpack" -> Math.max(1, base - 5 * Math.max(0, tailor - 1));
			case "tipped_poison", "tipped_slowness", "tipped_harming" -> Math.max(3, base - Math.max(0, fletcher - 1));
			default -> base;
		};
	}

	// ---------------------------------------------------------------- encantador

	/**
	 * Nivel de Mineracao "efetivo" para o teto de encantamento: o do mundo, limitado pelo nivel do Encantador
	 * (I = ferro, II = ouro, III = diamante, IV = infernita, V = sem limite). O teto do encantamento fica
	 * {@code min(teto da Mineracao, nivel da casa)}.
	 */
	public static int effectiveMining(int miningLevel, int enchanterLevel) {
		if (enchanterLevel <= 0) {
			return miningLevel;
		}
		int cap = switch (Math.min(5, enchanterLevel)) {
			case 1 -> com.mlin.notas.tier.Tier.IRON.level();
			case 2 -> com.mlin.notas.tier.Tier.GOLD.level();
			case 3 -> com.mlin.notas.tier.Tier.DIAMOND.level();
			case 4 -> com.mlin.notas.tier.Tier.INFERNITE.level();
			default -> Integer.MAX_VALUE;
		};
		return Math.min(miningLevel, cap);
	}

	/** Teto de nivel do encantamento: o menor entre a Mineracao do mundo e o nivel do Encantador (I..V; V sem limite). */
	public static int enchantCap(int miningCap, int enchanterLevel) {
		int house = enchanterLevel >= 5 ? Integer.MAX_VALUE : Math.max(1, enchanterLevel);
		return Math.min(miningCap, house);
	}

	// ---------------------------------------------------------------- igreja

	/** O sino toca ao anoitecer: a partir do por do sol ate um pouco depois. */
	public static boolean duskBell(long dayTime) {
		long t = Math.floorMod(dayTime, DAY);
		return t >= 12000 && t < 12200;
	}

	/** Os aldeoes caminham ate a igreja durante a janela do sino. */
	public static boolean gathering(long dayTime) {
		long t = Math.floorMod(dayTime, DAY);
		return t >= 12000 && t < 13000;
	}

	/** Bencao do altar: Regeneracao I por 15 s, uma vez por dia de jogo por jogador. */
	public static final int BLESSING_TICKS = 300;

	public static boolean canBless(long lastDay, long today) {
		return today > lastDay;
	}

	/** O cemiterio (nivel III da igreja) existe a partir do nivel III. */
	public static boolean hasGraveyard(int churchLevel) {
		return churchLevel >= 3;
	}

	// ---------------------------------------------------------------- taverna

	/** Boatos por dia por jogador: 1 no nivel I, 2 no II, 3 no III. */
	public static int rumorsPerDay(int tavernLevel) {
		return Math.max(1, Math.min(3, tavernLevel));
	}

	/** Missoes extras do dia que a taverna acrescenta: 1 a partir do nivel I (2 no III). */
	public static int extraBounties(int tavernLevel) {
		return tavernLevel >= 3 ? 2 : tavernLevel >= 1 ? 1 : 0;
	}

	// ---------------------------------------------------------------- feira semanal

	/** Dias de jogo por semana (a feira abre no ultimo). */
	public static final int WEEK = 7;

	/** A feira abre no ultimo dia de cada semana de jogo (dias 6, 13, 20...), com o Mercado no nivel II. */
	public static boolean fairDay(long day, int marketLevel) {
		return marketLevel >= 2 && Math.floorMod(day, WEEK) == WEEK - 1;
	}

	/** Mercadores itinerantes que chegam: 1 com o Mercado II, 2 com o III. */
	public static int fairMerchants(int marketLevel) {
		return marketLevel >= 3 ? 2 : 1;
	}

	/** Fachadas de pedra nas casas do centro a partir da Cidade; casas geminadas na Capital; arcadas na Metropole. */
	public enum Leftover {
		FACADES, TOWNHOUSES, ARCADES
	}

	public static List<Leftover> leftoversFor(VillageLevel level) {
		return switch (level) {
			case TOWN -> List.of(Leftover.FACADES);
			case CAPITAL -> List.of(Leftover.FACADES, Leftover.TOWNHOUSES);
			case METROPOLIS, CROWN_CITY -> List.of(Leftover.FACADES, Leftover.TOWNHOUSES, Leftover.ARCADES);
			default -> List.of();
		};
	}
}
