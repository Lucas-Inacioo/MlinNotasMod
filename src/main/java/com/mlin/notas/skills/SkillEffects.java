package com.mlin.notas.skills;

/** Bonus de cada nivel dos ramos laterais (logica pura). */
public final class SkillEffects {
	public static final double MELEE_PER_LEVEL = 0.5;
	public static final double HEALTH_PER_LEVEL = 2.0;
	public static final double SPEED_PER_LEVEL = 0.04;
	public static final double FALL_REDUCTION_PER_LEVEL = 0.10;
	public static final double PROSPECTING_PER_LEVEL = 0.20;

	private SkillEffects() {
	}

	/** Dano corpo a corpo somado (Combate). */
	public static double meleeBonus(int level) {
		return MELEE_PER_LEVEL * level;
	}

	/** Vida maxima somada (Vigor). */
	public static double healthBonus(int level) {
		return HEALTH_PER_LEVEL * level;
	}

	/** Fracao de velocidade somada a base (Agilidade). */
	public static double speedBonus(int level) {
		return SPEED_PER_LEVEL * level;
	}

	/** Fracao do dano de queda removida (Agilidade): 0,1 por nivel, ate 0,5. */
	public static double fallReduction(int level) {
		return FALL_REDUCTION_PER_LEVEL * level;
	}

	/** Chance de um minerio bruto/gema extra (Prospeccao): 0,2 por nivel, ate 1,0. */
	public static double prospectingChance(int level) {
		return PROSPECTING_PER_LEVEL * level;
	}
}
