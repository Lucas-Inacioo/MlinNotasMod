package com.mlin.notas.util;

/**
 * Conta de experiencia do vanilla (logica pura): pontos por nivel e conversao entre niveis e pontos. Usada para
 * cobrar XP em pontos (deposito da arvore de habilidades), assim pagar aos poucos custa o mesmo que pagar de uma vez.
 */
public final class XpMath {
	private XpMath() {
	}

	/** Pontos para passar do nivel {@code level} ao seguinte (igual a {@code Player.getXpNeededForNextLevel}). */
	public static int pointsToNextLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	/** Pontos para ir do nivel 0 ao nivel {@code levels}. */
	public static int pointsForLevels(int levels) {
		int total = 0;
		for (int level = 0; level < levels; level++) {
			total += pointsToNextLevel(level);
		}
		return total;
	}

	/** Nivel inteiro alcancado com {@code points} pontos a partir do zero. */
	public static int levelForPoints(int points) {
		int level = 0;
		int left = Math.max(0, points);
		while (left >= pointsToNextLevel(level)) {
			left -= pointsToNextLevel(level);
			level++;
		}
		return level;
	}

	/** Menor nivel (a partir do zero) que vale pelo menos {@code points} pontos: o "~N niveis" mostrado ao jogador. */
	public static int levelsCovering(int points) {
		int level = levelForPoints(points);
		return pointsForLevels(level) >= points ? level : level + 1;
	}

	/** Pontos que um jogador tem: os niveis inteiros + a fracao da barra ({@code progress} em 0..1). */
	public static int totalPoints(int level, float progress) {
		return pointsForLevels(Math.max(0, level)) + (int) Math.floor(Math.max(0F, Math.min(1F, progress)) * pointsToNextLevel(Math.max(0, level)));
	}
}
