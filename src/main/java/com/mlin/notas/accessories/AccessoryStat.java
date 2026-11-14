package com.mlin.notas.accessories;

/**
 * Numero somado de todos os acessorios equipados. Os quatro primeiros tem teto por estagio do mundo
 * ({@link AccessoryStage}); os demais tem teto fixo. Valores percentuais sao fracoes (0.05 = 5%). Logica pura.
 */
public enum AccessoryStat {
	/** Dano corpo a corpo (multiplica o total). */
	DAMAGE("damage", true, Double.NaN),
	/** Reducao de todo dano recebido. */
	DAMAGE_REDUCTION("damage_reduction", true, Double.NaN),
	/** Velocidade de movimento. */
	SPEED("speed", true, Double.NaN),
	/** Vida maxima (pontos; 2 = 1 coracao). */
	MAX_HEALTH("max_health", false, Double.NaN),
	KNOCKBACK_RESISTANCE("knockback_resistance", true, 0.30),
	/** Reducao de dano de queda. */
	FALL_REDUCTION("fall_reduction", true, 0.50),
	MINING_SPEED("mining_speed", true, 0.25),
	ATTACK_SPEED("attack_speed", true, 0.10),
	LUCK("luck", false, 2.0),
	/** Fracao do dano causado que volta como vida (no maximo 1 vez por segundo). */
	LIFESTEAL("lifesteal", true, 0.03),
	/** Reducao de dano de fogo e do tempo queimando (nunca imunidade). */
	FIRE_REDUCTION("fire_reduction", true, 0.30),
	/** Reducao do pulso sonico (dano que ignora armadura do Guardiao, sonic boom). */
	SONIC_REDUCTION("sonic_reduction", true, 0.20),
	/** Reducao de dano magico (sopro do dragao, raios estelares, pocoes). */
	MAGIC_REDUCTION("magic_reduction", true, 0.20),
	/** Reducao de explosoes e do dano de murchar. */
	BLAST_REDUCTION("blast_reduction", true, 0.25),
	/** Altura de degrau extra (blocos). */
	STEP_HEIGHT("step_height", false, 0.5),
	/** Folego extra embaixo d'agua (como Respiracao). */
	OXYGEN("oxygen", false, 2.0),
	/** Eficiencia de movimento na agua (1 = Passos Profundos III). */
	SWIM_SPEED("swim_speed", true, 0.33);

	private final String id;
	private final boolean percent;
	private final double fixedCap;

	AccessoryStat(String id, boolean percent, double fixedCap) {
		this.id = id;
		this.percent = percent;
		this.fixedCap = fixedCap;
	}

	public String id() {
		return id;
	}

	/** Mostrado como porcentagem (0.05 -> "5%"). */
	public boolean percent() {
		return percent;
	}

	/** O teto depende do estagio do mundo. */
	public boolean staged() {
		return Double.isNaN(fixedCap);
	}

	/** Valor com sinal para o jogador: {@code +5%}, {@code -3%}, {@code +2}, {@code +1.5}. */
	public String format(double value) {
		double shown = percent ? value * 100.0 : value;
		double rounded = Math.round(shown * 10.0) / 10.0;
		String number = rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
		return (value >= 0 ? "+" : "") + number + (percent ? "%" : "");
	}

	/** Teto da soma no estagio dado. */
	public double cap(AccessoryStage stage) {
		return staged() ? stage.cap(this) : fixedCap;
	}
}
