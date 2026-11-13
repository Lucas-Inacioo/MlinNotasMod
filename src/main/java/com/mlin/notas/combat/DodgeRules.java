package com.mlin.notas.combat;

/**
 * Esquiva: um impulso curto na direcao do movimento (para tras se parado), com alguns ticks de invulnerabilidade,
 * recarga e custo de fome. Logica pura; o cliente usa as mesmas regras para nao pedir esquiva que o servidor recusaria.
 */
public final class DodgeRules {
	/** Ticks sem tomar dano depois de esquivar. */
	public static final int INVULNERABLE_TICKS = 6;
	/** Ticks entre duas esquivas. */
	public static final int COOLDOWN_TICKS = 30;
	/** Fome minima (como correr: precisa de mais de 6). */
	public static final int MIN_FOOD = 7;
	/** Exaustao gasta por esquiva (4 = um ponto de fome). */
	public static final float EXHAUSTION = 1.5F;
	/** Velocidade horizontal do impulso (blocos/tick). */
	public static final double HORIZONTAL_SPEED = 0.85;
	/** Velocidade vertical do impulso: um saltinho tira o atrito do chao. */
	public static final double VERTICAL_SPEED = 0.22;

	/** Motivos de recusa (ou {@link #OK}). */
	public enum Check {
		OK,
		COOLDOWN,
		HUNGRY,
		AIRBORNE
	}

	private DodgeRules() {
	}

	public static Check check(long now, long lastDodge, int food, boolean onGround, boolean creative) {
		if (now - lastDodge < COOLDOWN_TICKS) {
			return Check.COOLDOWN;
		}
		if (!creative && food < MIN_FOOD) {
			return Check.HUNGRY;
		}
		if (!onGround) {
			return Check.AIRBORNE;
		}
		return Check.OK;
	}

	/** O jogador esta nos ticks de invulnerabilidade da esquiva. */
	public static boolean invulnerable(long now, long lastDodge) {
		return now >= lastDodge && now - lastDodge < INVULNERABLE_TICKS;
	}

	/** Fracao da recarga ja cumprida (0..1), para o HUD. */
	public static float cooldownProgress(long now, long lastDodge) {
		return Math.clamp((now - lastDodge) / (float) COOLDOWN_TICKS, 0.0F, 1.0F);
	}

	/**
	 * Direcao do impulso no mundo (x, z, normalizada) a partir da entrada de movimento ({@code strafe} positivo =
	 * esquerda, {@code forward} positivo = frente, como no Minecraft) e do yaw. Sem entrada: para tras.
	 */
	public static double[] direction(float strafe, float forward, float yawDegrees) {
		double s = strafe;
		double f = forward;
		double length = Math.sqrt(s * s + f * f);
		if (length < 1.0E-3) {
			s = 0.0;
			f = -1.0;
			length = 1.0;
		}
		s /= length;
		f /= length;
		double radians = Math.toRadians(yawDegrees);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		// mesmo calculo do Entity.getInputVector
		return new double[] {s * cos - f * sin, f * cos + s * sin};
	}
}
