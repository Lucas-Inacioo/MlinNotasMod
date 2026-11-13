package com.mlin.notas.sleep;

/**
 * Contagem de quem esta dormindo (logica pura, mesma conta do {@code SleepStatus} vanilla).
 */
public final class SleepRules {
	/** Padrao do vanilla para a gamerule {@code players_sleeping_percentage}. */
	public static final int VANILLA_DEFAULT_PERCENT = 100;
	/** Padrao do mod: metade dos jogadores. */
	public static final int MOD_DEFAULT_PERCENT = 50;

	private SleepRules() {
	}

	/** Estado da votacao do sono: {@code sleeping} de {@code needed} necessarios. */
	public record Status(int active, int sleeping, int needed) {
		public int missing() {
			return Math.max(0, needed - sleeping);
		}

		public boolean enough() {
			return sleeping >= needed;
		}
	}

	/** Dormindo necessarios: pelo menos 1, arredondando para cima (igual ao vanilla). */
	public static int sleepersNeeded(int activePlayers, int percent) {
		return Math.max(1, (int) Math.ceil(activePlayers * percent / 100.0F));
	}

	public static Status status(int activePlayers, int sleepingPlayers, int percent) {
		return new Status(activePlayers, sleepingPlayers, sleepersNeeded(activePlayers, percent));
	}

	/** Na partida do servidor: troca o padrao vanilla pelo do mod, uma vez por mundo (quem mudar depois fica com o seu). */
	public static boolean shouldApplyModDefault(int currentPercent, boolean alreadyApplied) {
		return !alreadyApplied && currentPercent == VANILLA_DEFAULT_PERCENT;
	}
}
