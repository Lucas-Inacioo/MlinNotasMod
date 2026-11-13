package com.mlin.notas.dragon;

import com.mlin.notas.boss.AttackScheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Regras da luta reformulada contra o Dragao do Ender (logica pura): vida escalada pelos jogadores na arena, 3 fases,
 * ataques especiais sorteados pelo {@link AttackScheduler}, tempos de aviso e alcance de cada ataque.
 *
 * <ul>
 * <li>Fase 1 (100% a 50%): <b>chuva de meteoros do vazio</b> (alvos marcados no chao 2 s antes) e <b>acaros
 * cristalinos</b> que brotam do chao.</li>
 * <li>Fase 2 (abaixo de 50%): ao entrar, <b>dois cristais renascem</b> nos pilares mais baixos; ganha o <b>mergulho</b>
 * (marca um circulo no chao, desce em carga e solta uma onda de choque; pular desvia) e <b>sentinelas de purpur</b>.</li>
 * <li>Fase 3 (abaixo de 20%): <b>furia</b> - <b>rajada de sopro</b> (3 bolas de fogo do dragao de uma vez), nuvens de
 * sopro 50% maiores e mais meteoros por jogador.</li>
 * </ul>
 */
public final class DragonRules {
	/** Vida do dragao com ate {@link #BASE_PLAYERS} jogadores (o vanilla tem 200 para qualquer numero). */
	public static final double BASE_HEALTH = 1000.0;
	public static final int BASE_PLAYERS = 2;
	/** Cada jogador a mais soma esta fracao da vida base (igual aos outros chefes). */
	public static final double EXTRA_PLAYER_BONUS = 0.5;
	/** Jogadores contados: ate esta distancia horizontal do portal de saida (a arena inteira). */
	public static final int PLAYER_SCAN_RADIUS = 150;
	/** Camas e ancoras de renascimento nao explodem a ate esta distancia horizontal do portal (a ilha da luta). */
	public static final int EXPLOSION_GUARD_RADIUS = 150;
	public static final double PHASE_TWO_FRACTION = 0.5;
	public static final double PHASE_THREE_FRACTION = 0.2;

	/** Pausa global entre ataques especiais (ticks, fase 1; o scheduler usa 2/3 disso nas fases 2 e 3). */
	public static final int ATTACK_GAP = 120;

	public static final String METEORS = "void_meteors";
	public static final String MITES = "crystal_mites";
	public static final String DIVE = "dive";
	public static final String SENTINELS = "sentinels";
	public static final String BREATH_VOLLEY = "breath_volley";

	/** Aviso no chao antes de o meteoro aparecer no ceu (ticks). */
	public static final int METEOR_WARNING = 40;
	/** Altura acima do alvo de onde o meteoro cai. */
	public static final int METEOR_HEIGHT = 16;
	public static final double METEOR_RADIUS = 3.0;
	/** Distancia maxima do meteoro ao jogador marcado. */
	public static final double METEOR_SPREAD = 5.0;

	/** O mergulho desiste se nao chegar ao alvo neste tempo e explode onde estiver. */
	public static final int DIVE_TIMEOUT = 100;
	/** Antes de partir para o mergulho o dragao ruge e marca o alvo (ticks). */
	public static final int DIVE_WINDUP = 30;
	/** Distancia ao alvo em que o mergulho termina. */
	public static final double DIVE_ARRIVE = 8.0;
	public static final double SHOCKWAVE_RADIUS = 11.0;
	public static final int SHOCKWAVE_TICKS = 18;
	public static final double SHOCKWAVE_WIDTH = 1.8;
	/** Quem esta a mais que isto acima do chao (pulando) nao e atingido pela onda. */
	public static final double SHOCKWAVE_HEIGHT = 1.2;

	/** Maximo de adds vivos perto do portal. */
	public static final int MAX_MITES = 6;
	public static final int MAX_SENTINELS = 3;
	/** Cristais que renascem ao entrar na fase 2. */
	public static final int REGROWN_CRYSTALS = 2;
	/** Multiplicador do raio e da duracao das nuvens de sopro na fase 3. */
	public static final float RAGE_BREATH_SCALE = 1.5F;

	private DragonRules() {
	}

	/** Explosoes de cama/ancora sao bloqueadas em ({@code x}, {@code z}) do End: perto do portal de saida (0, 0). */
	public static boolean guardsExplosionsAt(int x, int z) {
		return (long) x * x + (long) z * z <= (long) EXPLOSION_GUARD_RADIUS * EXPLOSION_GUARD_RADIUS;
	}

	/** 1, 2 ou 3 pela fracao de vida. */
	public static int phase(double health, double maxHealth) {
		if (maxHealth <= 0) {
			return 1;
		}
		double f = health / maxHealth;
		if (f < PHASE_THREE_FRACTION) {
			return 3;
		}
		return f < PHASE_TWO_FRACTION ? 2 : 1;
	}

	/** Multiplicador da vida: 1 para ate 2 jogadores, +50% por jogador a mais. */
	public static double healthMultiplier(int players) {
		return 1.0 + EXTRA_PLAYER_BONUS * Math.max(0, players - BASE_PLAYERS);
	}

	public static double maxHealth(int players) {
		return BASE_HEALTH * healthMultiplier(players);
	}

	/**
	 * Vida nova ao reescalar de {@code oldMax} para {@code newMax}, mantendo a fracao (um dragao ferido continua
	 * ferido). Nunca abaixo de 1 se estava vivo.
	 */
	public static double rescaledHealth(double health, double oldMax, double newMax) {
		if (oldMax <= 0) {
			return newMax;
		}
		double scaled = health / oldMax * newMax;
		return health > 0 ? Math.max(1.0, Math.min(newMax, scaled)) : 0.0;
	}

	/** So reescala para cima: jogadores que saem da arena nao deixam o dragao mais fraco no meio da luta. */
	public static boolean shouldRescale(int scaledFor, int players) {
		return scaledFor <= 0 || Math.max(BASE_PLAYERS, players) > Math.max(BASE_PLAYERS, scaledFor);
	}

	/** Meteoros por jogador em cada fase. */
	public static int meteorsPerPlayer(int phase) {
		return switch (phase) {
			case 1 -> 3;
			case 2 -> 4;
			default -> 5;
		};
	}

	public static float meteorDamage(int phase) {
		return phase >= 3 ? 10.0F : 8.0F;
	}

	public static float shockwaveDamage(int phase) {
		return phase >= 3 ? 12.0F : 10.0F;
	}

	/** Dano do meteoro a {@code distance} do impacto: cheio no centro, metade na borda, zero fora. */
	public static float meteorFalloff(double distance, float damage) {
		if (distance > METEOR_RADIUS) {
			return 0.0F;
		}
		return (float) (damage * (1.0 - 0.5 * distance / METEOR_RADIUS));
	}

	/**
	 * A onda de choque atinge quem esta na faixa do anel e com os pes perto do chao ({@code heightAboveGround} ate
	 * {@link #SHOCKWAVE_HEIGHT}); quem pula por cima escapa.
	 */
	public static boolean shockwaveHits(double distance, double radius, double heightAboveGround) {
		return heightAboveGround <= SHOCKWAVE_HEIGHT && Math.abs(distance - radius) <= SHOCKWAVE_WIDTH / 2.0;
	}

	/** Adds a invocar agora: um por jogador, sem passar do limite. */
	public static int addsToSpawn(int players, int alive, int max) {
		return Math.max(0, Math.min(Math.max(1, players), max - alive));
	}

	/** Uma coluna de obsidiana do End (centro, raio, altura do topo, se tem gaiola, se ainda tem cristal). */
	public record Pillar(int x, int z, int radius, int height, boolean guarded, boolean hasCrystal) {
	}

	/**
	 * Pilares que recebem cristal novo na fase 2: os mais baixos sem gaiola e sem cristal (faceis de acertar com
	 * flecha), depois os engaiolados; no maximo {@code count}.
	 */
	public static List<Pillar> regrowTargets(List<Pillar> pillars, int count) {
		List<Pillar> empty = new ArrayList<>();
		for (Pillar p : pillars) {
			if (!p.hasCrystal()) {
				empty.add(p);
			}
		}
		empty.sort(Comparator.comparing(Pillar::guarded).thenComparingInt(Pillar::height).thenComparingInt(Pillar::x).thenComparingInt(Pillar::z));
		return List.copyOf(empty.subList(0, Math.min(count, empty.size())));
	}

	/** Ataques especiais do dragao. Distancia = do dragao ao jogador mais perto. */
	public static AttackScheduler scheduler() {
		return new AttackScheduler(ATTACK_GAP, List.of(
				new AttackScheduler.Attack(METEORS, 220, 1, 0, 200, 3),
				new AttackScheduler.Attack(MITES, 500, 1, 0, 200, 2),
				new AttackScheduler.Attack(DIVE, 300, 2, 12, 120, 3),
				new AttackScheduler.Attack(SENTINELS, 600, 2, 0, 200, 2),
				new AttackScheduler.Attack(BREATH_VOLLEY, 160, 3, 8, 90, 3)));
	}
}
