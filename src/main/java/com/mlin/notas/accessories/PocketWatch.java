package com.mlin.notas.accessories;

/** Conta do Relogio de Bolso: relogio do Overworld (ticks) -> dia, hora e minuto (tick 0 = 06:00). Logica pura. */
public final class PocketWatch {
	public static final int TICKS_PER_DAY = 24000;

	private PocketWatch() {
	}

	/** Dia de jogo, comecando em 1. */
	public static long day(long clockTicks) {
		return Math.floorDiv(clockTicks, TICKS_PER_DAY) + 1;
	}

	/** Minutos desde a meia-noite (0..1439). */
	public static int minuteOfDay(long clockTicks) {
		long inDay = Math.floorMod(clockTicks + 6000L, TICKS_PER_DAY);
		return (int) (inDay * 1440L / TICKS_PER_DAY);
	}

	/** "06:00", "18:30". */
	public static String time(long clockTicks) {
		int minutes = minuteOfDay(clockTicks);
		return String.format("%02d:%02d", minutes / 60, minutes % 60);
	}
}
