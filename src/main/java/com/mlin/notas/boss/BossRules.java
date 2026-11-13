package com.mlin.notas.boss;

/**
 * Regras comuns dos chefes (logica pura): fase, vida escalada pelo numero de jogadores, despertar, animacoes e a
 * quantidade de escamas do dragao.
 */
public final class BossRules {
	/** Abaixo desta fracao da vida o chefe entra na fase 2. */
	public static final double PHASE_TWO_FRACTION = 0.5;
	/** Jogadores contados para a vida e para a barra: ate esta distancia. */
	public static final int PLAYER_SCAN_RADIUS = 32;
	/** A vida base ja conta com este numero de jogadores. */
	public static final int BASE_PLAYERS = 2;
	/** Cada jogador a mais soma esta fracao da vida base. */
	public static final double EXTRA_PLAYER_BONUS = 0.5;
	/** Duracao da animacao de surgimento (invulneravel, sem atacar). */
	public static final int SPAWN_TICKS = 40;
	/** Raio sem outro chefe do mesmo tipo para o altar aceitar invocar. */
	public static final int ALTAR_BUSY_RADIUS = 48;
	/** Teto da vida maxima (a vanilla limita em 1024; os chefes passam disso com 3+ jogadores). */
	public static final double MAX_HEALTH_CAP = 16384.0;
	/** Vida do Wither com ate 2 jogadores (a vanilla tem 300). */
	public static final double WITHER_HEALTH = 900.0;

	// dano das habilidades especiais (tipo {@code mlinnotas:boss_ability}: armadura vale, Protecao nao); o golpe corpo a
	// corpo esta em BossKind#melee
	public static final float GOLEM_CHARGE = 16.0F;
	public static final float GOLEM_SLAM = 9.0F;
	public static final float GOLEM_SLAM_PHASE_TWO = 12.0F;
	public static final float GOLEM_ROCK = 10.0F;
	public static final float GUARDIAN_TENDRIL = 5.0F;
	/** Pulso sonico (tipo vanilla sonic_boom: ignora armadura e encantamentos). */
	public static final float GUARDIAN_PULSE = 10.0F;
	public static final float GUARDIAN_PULSE_PHASE_TWO = 12.0F;
	public static final float COLOSSUS_WAVE = 8.0F;
	public static final float COLOSSUS_WAVE_PHASE_TWO = 10.0F;
	public static final float COLOSSUS_METEOR = 9.0F;
	public static final float COLOSSUS_METEOR_SPLASH = 4.0F;
	public static final float HERALD_STAR = 11.0F;

	private BossRules() {
	}

	/** 1 ou 2; fase 2 quando a vida fica abaixo de 50%. */
	public static int phase(double health, double maxHealth) {
		return maxHealth > 0 && health < maxHealth * PHASE_TWO_FRACTION ? 2 : 1;
	}

	/** Multiplicador da vida: 1 para ate 2 jogadores, +50% por jogador a mais. */
	public static double healthMultiplier(int players) {
		return 1.0 + EXTRA_PLAYER_BONUS * Math.max(0, players - BASE_PLAYERS);
	}

	public static double scaledHealth(double baseHealth, int players) {
		return baseHealth * healthMultiplier(players);
	}

	/**
	 * Escamas soltas pelo dragao: 2 a 4 na primeira morte do mundo, 1 a 2 quando ele renasce ({@code roll} em 0..2;
	 * na reinvocacao so 0..1 conta).
	 */
	public static int dragonScales(int roll, boolean firstKill) {
		return firstKill ? 2 + Math.max(0, Math.min(2, roll)) : 1 + Math.max(0, Math.min(1, roll));
	}

	/** Raio da onda de fogo no tick {@code tick} (0..duration): cresce linear ate {@code maxRadius}. */
	public static double waveRadius(int tick, int duration, double maxRadius) {
		if (duration <= 0) {
			return maxRadius;
		}
		return maxRadius * Math.max(0, Math.min(duration, tick)) / duration;
	}

	/** O alvo a {@code distance} esta na faixa da onda de raio {@code radius} e largura {@code width}. */
	public static boolean inRing(double distance, double radius, double width) {
		return Math.abs(distance - radius) <= width / 2.0;
	}

	/** Forca do empurrao do golpe no chao: maxima no centro, zero na borda do raio. */
	public static double slamKnockback(double distance, double radius, double strength) {
		if (distance >= radius) {
			return 0.0;
		}
		return strength * (1.0 - distance / radius * 0.5);
	}
}
