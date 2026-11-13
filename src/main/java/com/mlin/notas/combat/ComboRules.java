package com.mlin.notas.combat;

/**
 * Combo de golpes por tempo (estado guardado no servidor por jogador). Logica pura.
 *
 * <p>Um golpe com carga cheia ({@link #FULL_CHARGE}) que vem ate {@link #GRACE_TICKS} depois de a arma recarregar
 * continua o combo; o terceiro e o golpe final, e o seguinte recomeca. Golpe fraco, demora ou troca de arma zera.</p>
 */
public final class ComboRules {
	/** Carga minima para o golpe contar no combo (a mesma do "golpe forte" vanilla). */
	public static final float FULL_CHARGE = 0.9F;
	/** Folga, alem da recarga, para emendar o proximo golpe. */
	public static final int GRACE_TICKS = 16;

	/**
	 * Estado depois de um golpe.
	 *
	 * @param weapon id da arma (troca de arma zera o combo)
	 * @param step   indice deste golpe no combo (0..COMBO_LENGTH-1), ou -1 se foi fraco
	 * @param tick   tick do golpe
	 */
	public record State(String weapon, int step, long tick) {
		public boolean finisher() {
			return step == WeaponClass.COMBO_LENGTH - 1;
		}
	}

	private ComboRules() {
	}

	/** O combo ainda esta vivo no tick {@code now} (para o HUD). */
	public static boolean alive(State state, long now, int cooldownTicks) {
		return state != null && state.step >= 0 && now - state.tick <= cooldownTicks + GRACE_TICKS;
	}

	public static State next(State previous, String weapon, long now, int cooldownTicks, float charge) {
		if (charge < FULL_CHARGE) {
			return new State(weapon, -1, now);
		}
		boolean continues = previous != null && previous.weapon.equals(weapon) && previous.step >= 0
				&& now - previous.tick <= cooldownTicks + GRACE_TICKS;
		int step = continues ? (previous.step + 1) % WeaponClass.COMBO_LENGTH : 0;
		return new State(weapon, step, now);
	}
}
