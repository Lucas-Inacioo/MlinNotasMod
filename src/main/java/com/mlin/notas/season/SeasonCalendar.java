package com.mlin.notas.season;

/**
 * Calendario das estacoes. O "dia absoluto" e o dia do relogio do Overworld ({@code tempo / 24000}) somado ao
 * deslocamento do mundo (que o {@code /season set} muda). Um ano tem {@code 4 * duracao} dias. Logica pura.
 */
public final class SeasonCalendar {
	public static final int TICKS_PER_DAY = 24000;
	public static final int DEFAULT_LENGTH = 8;

	private SeasonCalendar() {
	}

	/** Dia do relogio (pode ser negativo se alguem voltar o tempo para tras de 0). */
	public static long clockDay(long clockTicks) {
		return Math.floorDiv(clockTicks, TICKS_PER_DAY);
	}

	public static SeasonDate date(long absoluteDay, int lengthDays) {
		int length = Math.max(1, lengthDays);
		long year = 4L * length;
		int dayOfYear = (int) Math.floorMod(absoluteDay, year);
		Season season = Season.values()[dayOfYear / length];
		int day = dayOfYear % length;
		return new SeasonDate(season, phase(day, length), day, length);
	}

	/** Sub-fase do dia {@code day} (0..length-1): tercos da estacao. */
	public static Season.Phase phase(int day, int length) {
		int third = Math.min(2, Math.max(0, day) * 3 / Math.max(1, length));
		return Season.Phase.values()[third];
	}

	/** Primeiro dia (0..length-1) da fase na estacao; se a estacao for curta demais para ter a fase, o mais proximo. */
	public static int firstDayOf(Season.Phase phase, int length) {
		int len = Math.max(1, length);
		for (int day = 0; day < len; day++) {
			if (phase(day, len) == phase) {
				return day;
			}
		}
		return phase == Season.Phase.EARLY ? 0 : len - 1;
	}

	/** Dia do ano (0..4*length-1) do comeco da fase. */
	public static int dayOfYear(Season season, Season.Phase phase, int length) {
		return season.ordinal() * Math.max(1, length) + firstDayOf(phase, length);
	}

	/**
	 * Deslocamento que faz o dia de relogio {@code clockDay} cair no comeco de {@code season}/{@code phase}. Fica
	 * entre 0 e um ano.
	 */
	public static long offsetFor(long clockDay, Season season, Season.Phase phase, int length) {
		long year = 4L * Math.max(1, length);
		return Math.floorMod(dayOfYear(season, phase, length) - clockDay, year);
	}

	/**
	 * Novo deslocamento que mantem a data atual quando a duracao muda de {@code oldLength} para {@code newLength}
	 * (a mesma estacao e a mesma fracao dela).
	 */
	public static long offsetKeepingDate(long clockDay, long offset, int oldLength, int newLength) {
		SeasonDate now = date(clockDay + offset, oldLength);
		int newLen = Math.max(1, newLength);
		int day = Math.min(newLen - 1, (int) ((long) now.dayOfSeason() * newLen / Math.max(1, oldLength)));
		long target = (long) now.season().ordinal() * newLen + day;
		return Math.floorMod(target - clockDay, 4L * newLen);
	}
}
